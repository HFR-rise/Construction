package com.example.myapplication.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.myapplication.data.database.*
import com.example.myapplication.data.models.*
import com.example.myapplication.network.ApiService
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val apiService: ApiService,
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val objectDao: ObjectDao,
    private val syncOperationDao: SyncOperationDao,
    private val userPreferences: UserPreferences,
    private val context: Context
) {
    private val TAG = "SyncManager"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isSyncing = false

    var onForceLogout: (() -> Unit)? = null
        set(value) {
            field = value
            Log.d(TAG, "onForceLogout callback registered: ${value != null}")
        }

    init {
        startPeriodicSync()
    }

    private fun startPeriodicSync() {
        scope.launch {
            while (true) {
                if (hasInternetConnection()) {
                    val userId = userPreferences.getUserId()
                    if (userId != null) {
                        syncIfQueueIsEmpty(userId)
                    }
                }
                delay(60000)
            }
        }
    }

    suspend fun syncIfQueueIsEmpty(userId: String) {
        val pendingCount = syncOperationDao.getOperationsCountForUser(userId)

        if (pendingCount == 0) {
            Log.d(TAG, "Queue is empty, loading data from server...")
            syncDataFromServer(userId)
        } else {
            Log.d(TAG, "Queue has $pendingCount operations, will sync them first")
            val allSent = syncPendingOperationsToServer(userId, retryUntilSuccess = true)

            if (allSent) {
                Log.d(TAG, "All operations sent successfully, now loading from server...")
                syncDataFromServer(userId)
            } else {
                Log.w(TAG, "Some operations failed to send, will retry later")
            }
        }
    }

    suspend fun handleOnlineRecovery() {
        if (isSyncing) return
        isSyncing = true

        try {
            val userId = userPreferences.getUserId() ?: return
            Log.d(TAG, "📡 Starting online recovery...")

            val allOperationsSent = syncPendingOperationsToServer(userId, retryUntilSuccess = true)

            if (allOperationsSent) {
                Log.d(TAG, "Queue is empty, safe to load from server")
                syncDataFromServer(userId)
                syncMissingDataToServer(userId)
            } else {
                Log.w(TAG, "Cannot load from server - queue not empty, will retry later")
            }

            Log.d(TAG, "✅ Online recovery completed")
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down during online recovery: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error during online recovery: ${e.message}")
        } finally {
            isSyncing = false
        }
    }


    suspend fun checkCurrentSession(): Boolean {
        val userId = userPreferences.getUserId() ?: return false
        val deviceId = userPreferences.getDeviceId() ?: return false

        if (!hasInternetConnection()) {
            Log.d(TAG, "No internet connection, skipping session check (returning true)")
            return true
        }

        return try {
            val response = apiService.checkSessionWithDevice(userId, deviceId)
            val isValid = response.isSuccessful && response.body()?.isValid == true
            Log.d(TAG, "Session check for user $userId: ${if (isValid) "valid" else "INVALID"}")
            isValid
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down during session check: ${e.message}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session: ${e.message}")
            true
        }
    }

    private suspend fun checkSessionValidity(userId: String): Boolean {
        if (!hasInternetConnection()) {
            Log.d(TAG, "No internet connection, skipping session validity check (returning true)")
            return true
        }

        return try {
            val response = apiService.checkSession(userId)
            val isValid = response.isSuccessful && response.body()?.isValid == true
            Log.d(TAG, "Session validity check for user $userId: ${if (isValid) "valid" else "INVALID"}")
            isValid
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down during session validity check: ${e.message}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session validity: ${e.message}")
            true
        }
    }


    private suspend fun syncPendingOperationsToServer(userId: String, retryUntilSuccess: Boolean = false): Boolean {
        var allSuccess = false

        while (!allSuccess) {
            val operations = syncOperationDao.getOperationsForUser(userId)
            if (operations.isEmpty()) {
                Log.d(TAG, "✅ Queue is empty")
                return true
            }

            Log.d(TAG, "📤 Syncing ${operations.size} pending operations...")

            val successIds = mutableListOf<String>()
            var hasFailures = false

            for (op in operations) {
                try {
                    val success = when (op.type) {
                        "CREATE" -> handleCreateOperation(op, userId)
                        "UPDATE" -> handleUpdateOperation(op, userId)
                        "DELETE" -> handleDeleteOperation(op, userId)
                        else -> false
                    }

                    if (success) {
                        successIds.add(op.id)
                        Log.d(TAG, "✅ Synced: ${op.type} ${op.entityId}")
                    } else {
                        hasFailures = true
                        Log.w(TAG, "⚠️ Failed: ${op.type} ${op.entityId}")
                    }
                } catch (e: ConnectException) {
                    Log.e(TAG, "Server is down, keeping operation in queue: ${e.message}")
                    hasFailures = true
                } catch (e: SocketTimeoutException) {
                    Log.e(TAG, "Server timeout, keeping operation in queue: ${e.message}")
                    hasFailures = true
                } catch (e: Exception) {
                    hasFailures = true
                    Log.e(TAG, "Error: ${e.message}")
                }
            }

            for (id in successIds) {
                syncOperationDao.deleteById(id)
            }

            allSuccess = !hasFailures

            if (!allSuccess && retryUntilSuccess) {
                Log.d(TAG, "Some operations failed, retrying in 5 seconds...")
                delay(5000)
            } else if (!allSuccess && !retryUntilSuccess) {
                Log.w(TAG, "Some operations failed, will retry later")
                return false
            }
        }

        return true
    }

    private suspend fun handleCreateOperation(op: SyncOperationEntity, userId: String): Boolean {
        return try {
            when (op.entityType) {
                "PROJECT" -> {
                    val project = gson.fromJson(op.data, Project::class.java)
                    val response = apiService.createProject(project, userId)
                    if (response.isSuccessful) {
                        response.body()?.let { projectDao.updateProject(it) }
                        true
                    } else false
                }
                "OBJECT" -> {
                    val obj = gson.fromJson(op.data, ObjectModel::class.java)
                    val response = apiService.createObject(obj, userId)
                    if (response.isSuccessful) {
                        response.body()?.let { objectDao.insertOrUpdateObject(it) }
                        true
                    } else false
                }
                "CONTACT" -> {
                    val contact = gson.fromJson(op.data, Contact::class.java)
                    val response = apiService.createContact(contact, userId)
                    response.isSuccessful
                }
                "MATERIAL" -> {
                    val material = gson.fromJson(op.data, Material::class.java)
                    val response = apiService.addMaterial(material, userId)
                    response.isSuccessful
                }
                "WORK_ITEM" -> {
                    val workItem = gson.fromJson(op.data, WorkItem::class.java)
                    val response = apiService.addWorkItem(workItem, userId)
                    response.isSuccessful
                }
                "CONTACT_METHOD" -> {
                    val method = gson.fromJson(op.data, ContactMethod::class.java)
                    val response = apiService.addContactMethod(method, userId)
                    response.isSuccessful
                }
                else -> false
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, operation queued: ${e.message}")
            false
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Server timeout, operation queued: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleCreateOperation: ${e.message}")
            false
        }
    }

    private suspend fun handleUpdateOperation(op: SyncOperationEntity, userId: String): Boolean {
        return try {
            when (op.entityType) {
                "PROJECT" -> {
                    val project = gson.fromJson(op.data, Project::class.java)
                    val response = apiService.updateProject(op.entityId, project, userId)
                    response.isSuccessful
                }
                "OBJECT" -> {
                    val obj = gson.fromJson(op.data, ObjectModel::class.java)
                    val response = apiService.updateObject(op.entityId, obj, userId)
                    response.isSuccessful
                }
                "CONTACT" -> {
                    val contact = gson.fromJson(op.data, Contact::class.java)
                    val response = apiService.updateContact(op.entityId, contact, userId)
                    response.isSuccessful
                }
                else -> false
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, operation queued: ${e.message}")
            false
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Server timeout, operation queued: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleUpdateOperation: ${e.message}")
            false
        }
    }

    private suspend fun handleDeleteOperation(op: SyncOperationEntity, userId: String): Boolean {
        return try {
            when (op.entityType) {
                "PROJECT" -> {
                    val response = apiService.deleteProject(op.entityId, userId)
                    response.isSuccessful
                }
                "OBJECT" -> {
                    val response = apiService.deleteObject(op.entityId, userId)
                    response.isSuccessful
                }
                "CONTACT" -> {
                    val response = apiService.deleteContact(op.entityId, userId)
                    response.isSuccessful
                }
                "MATERIAL" -> {
                    val response = apiService.deleteMaterial(op.entityId, userId)
                    response.isSuccessful
                }
                "WORK_ITEM" -> {
                    val response = apiService.deleteWorkItem(op.entityId, userId)
                    response.isSuccessful
                }
                else -> false
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, operation queued: ${e.message}")
            false
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Server timeout, operation queued: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleDeleteOperation: ${e.message}")
            false
        }
    }


    private suspend fun syncMissingDataToServer(userId: String) {
        Log.d(TAG, "🔍 Checking for missing data to sync...")
        syncMissingProjectsToServer(userId)
        syncMissingObjectsToServer(userId)
        syncMissingContactsToServer(userId)
        Log.d(TAG, "✅ Missing data sync completed")
    }

    private suspend fun syncMissingProjectsToServer(userId: String) {
        val localProjects = projectDao.getProjectsForUserSync(userId)
        for (project in localProjects) {
            if (!isProjectOnServer(project.id, userId)) {
                Log.d(TAG, "Found missing project on device: ${project.name}")
                syncProjectToServer(project, userId)
            }
        }
    }

    private suspend fun syncMissingObjectsToServer(userId: String) {
        val localObjects = objectDao.getAllObjectsSync()
        for (obj in localObjects) {
            if (!isObjectOnServer(obj.id, userId)) {
                Log.d(TAG, "Found missing object on device: ${obj.name}")
                syncObjectToServer(obj, userId)
            }
        }
    }


    suspend fun shareProject(
        projectId: String,
        phoneNumber: String,
        userId: String
    ): Result<Unit> {
        return try {
            val response = apiService.shareProject(projectId, mapOf("phoneNumber" to phoneNumber), userId)

            if (response.isSuccessful) {
                Log.d(TAG, "✅ Project $projectId shared with $phoneNumber")
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = when (response.code()) {
                    404 -> "Пользователь с таким номером не найден"
                    400 -> "Неверный номер телефона"
                    403 -> "Нет прав для расшаривания этой сметы"
                    else -> "Ошибка: ${response.code()}"
                }
                Log.e(TAG, "❌ Share failed: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server unreachable: ${e.message}")
            Result.failure(Exception("Нет соединения с сервером"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing project: ${e.message}")
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }

    private suspend fun syncMissingContactsToServer(userId: String) {
        val localContacts = contactDao.getAllContactsSync()
        for (contact in localContacts) {
            if (!isContactOnServer(contact.id, userId)) {
                Log.d(TAG, "Found missing contact on device: ${contact.name}")
                syncContactToServer(contact, userId)
            }
        }
    }

    private suspend fun isProjectOnServer(projectId: String, userId: String): Boolean {
        return try {
            val response = apiService.getProject(projectId, userId)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun isObjectOnServer(objectId: String, userId: String): Boolean {
        return try {
            val response = apiService.getObject(objectId, userId)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun isContactOnServer(contactId: String, userId: String): Boolean {
        return try {
            val response = apiService.getContact(contactId, userId)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }



    suspend fun syncDataFromServer(userId: String) {
        Log.d(TAG, "📥 Loading data from server...")

        val pendingCount = syncOperationDao.getOperationsCountForUser(userId)
        if (pendingCount > 0) {
            Log.w(TAG, "⚠️ Cannot load from server - queue has $pendingCount pending operations!")
            return
        }

        if (!hasInternetConnection()) {
            Log.w(TAG, "No internet connection, cannot load data from server")
            return
        }

        try {
            val projectsResponse = apiService.getProjectsForUser(userId)
            if (projectsResponse.isSuccessful) {
                val serverProjects = projectsResponse.body() ?: emptyList()
                for (serverProject in serverProjects) {
                    val existing = projectDao.getProjectById(serverProject.id)
                    if (existing == null) {
                        projectDao.insertProject(serverProject)
                    } else {
                        projectDao.updateProject(serverProject)
                    }
                }
                Log.d(TAG, "Loaded ${serverProjects.size} projects")
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, cannot load projects: ${e.message}")
            return
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Server timeout, cannot load projects: ${e.message}")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error loading projects: ${e.message}")
        }

        try {
            val objectsResponse = apiService.getRootObjects(userId)
            if (objectsResponse.isSuccessful) {
                val serverObjects = objectsResponse.body() ?: emptyList()
                for (serverObject in serverObjects) {
                    val existing = objectDao.getObjectById(serverObject.id)
                    if (existing == null) {
                        objectDao.insertObject(serverObject)
                    } else {
                        objectDao.updateObject(serverObject)
                    }
                    syncChildObjectsFromServer(serverObject.id, userId)
                }
                Log.d(TAG, "Loaded ${serverObjects.size} objects")
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, cannot load objects: ${e.message}")
            return
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Server timeout, cannot load objects: ${e.message}")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error loading objects: ${e.message}")
        }

        try {
            val contactsResponse = apiService.getAllContacts(userId)
            if (contactsResponse.isSuccessful) {
                val serverContacts = contactsResponse.body() ?: emptyList()
                for (serverContact in serverContacts) {
                    val existing = contactDao.getContactById(serverContact.id)
                    if (existing == null) {
                        contactDao.insertContact(serverContact)
                    } else {
                        contactDao.updateContact(serverContact)
                    }
                }
                Log.d(TAG, "Loaded ${serverContacts.size} contacts")
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, cannot load contacts: ${e.message}")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Server timeout, cannot load contacts: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contacts: ${e.message}")
        }
    }

    private suspend fun syncChildObjectsFromServer(parentId: String, userId: String) {
        try {
            val response = apiService.getChildObjects(parentId, userId)
            if (response.isSuccessful) {
                val children = response.body() ?: emptyList()
                for (child in children) {
                    val existing = objectDao.getObjectById(child.id)
                    if (existing == null) {
                        objectDao.insertObject(child)
                    } else {
                        objectDao.updateObject(child)
                    }
                }
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, cannot sync child objects: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing child objects: ${e.message}")
        }
    }


    suspend fun queueOperation(type: String, entityType: String, entityId: String, data: Any) {
        val userId = userPreferences.getUserId() ?: return
        val operation = SyncOperationEntity(
            id = UUID.randomUUID().toString(),
            type = type,
            entityType = entityType,
            entityId = entityId,
            data = gson.toJson(data),
            timestamp = System.currentTimeMillis(),
            userId = userId
        )
        syncOperationDao.insert(operation)
        Log.d(TAG, "📝 Queued: $type $entityType")

        if (hasInternetConnection()) {
            scope.launch {
                syncPendingOperationsToServer(userId, retryUntilSuccess = false)
            }
        }
    }

    suspend fun syncProjectToServer(project: Project, userId: String) {
        try {
            val response = apiService.createProject(project, userId)
            if (response.isSuccessful) {
                response.body()?.let { projectDao.updateProject(it) }
                Log.d(TAG, "✅ Project synced: ${project.name}")
            } else {
                queueOperation("CREATE", "PROJECT", project.id, project)
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, project queued: ${e.message}")
            queueOperation("CREATE", "PROJECT", project.id, project)
        } catch (e: Exception) {
            queueOperation("CREATE", "PROJECT", project.id, project)
        }
    }

    suspend fun syncObjectToServer(obj: ObjectModel, userId: String) {
        try {
            val response = apiService.createObject(obj, userId)
            if (response.isSuccessful) {
                response.body()?.let { objectDao.updateObject(it) }
                Log.d(TAG, "✅ Object synced: ${obj.name}")
            } else {
                queueOperation("CREATE", "OBJECT", obj.id, obj)
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, object queued: ${e.message}")
            queueOperation("CREATE", "OBJECT", obj.id, obj)
        } catch (e: Exception) {
            queueOperation("CREATE", "OBJECT", obj.id, obj)
        }
    }

    suspend fun syncContactToServer(contact: Contact, userId: String) {
        try {
            val response = apiService.createContact(contact, userId)
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Contact synced: ${contact.name}")
            } else {
                queueOperation("CREATE", "CONTACT", contact.id, contact)
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, contact queued: ${e.message}")
            queueOperation("CREATE", "CONTACT", contact.id, contact)
        } catch (e: Exception) {
            queueOperation("CREATE", "CONTACT", contact.id, contact)
        }
    }

    suspend fun syncProjectDeletion(projectId: String, userId: String) {
        try {
            val response = apiService.deleteProject(projectId, userId)
            if (!response.isSuccessful) {
                queueOperation("DELETE", "PROJECT", projectId, mapOf("id" to projectId))
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Server is down, deletion queued: ${e.message}")
            queueOperation("DELETE", "PROJECT", projectId, mapOf("id" to projectId))
        } catch (e: Exception) {
            queueOperation("DELETE", "PROJECT", projectId, mapOf("id" to projectId))
        }
    }


    suspend fun clearAllLocalData() {
        Log.d(TAG, "🗑️ Clearing all local data (logout)")
        projectDao.deleteAll()
        objectDao.deleteAll()
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
        materialDao.deleteAll()
        workItemDao.deleteAll()
        syncOperationDao.clearAll()
        Log.d(TAG, "✅ All local data cleared")
    }

    suspend fun clearAllOperations() {
        syncOperationDao.clearAll()
        Log.d(TAG, "📝 All operations cleared from queue")
    }

    suspend fun clearAllOperationsForUser(userId: String) {
        syncOperationDao.clearForUser(userId)
        Log.d(TAG, "📝 All operations cleared for user: $userId")
    }


    fun hasInternetConnection(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getPendingOperationsCount(): Int {
        val userId = userPreferences.getUserId() ?: return 0
        return syncOperationDao.getOperationsCountForUser(userId)
    }
}
