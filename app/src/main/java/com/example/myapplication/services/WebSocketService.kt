package com.example.myapplication.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.myapplication.data.database.*
import com.example.myapplication.data.models.*
import com.example.myapplication.utils.UserPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketService @Inject constructor(
    private val userPreferences: UserPreferences,
    private val projectDao: ProjectDao,
    private val materialDao: MaterialDao,
    private val workItemDao: WorkItemDao,
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val objectDao: ObjectDao,
    private val context: Context
) {
    private val tag = "WebSocketService"

    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .registerTypeAdapter(Date::class.java, JsonDeserializer { json, _, _ ->
            try {
                val dateStr = json.asString
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                format.parse(dateStr) ?: Date()
            } catch (e: Exception) {
                try {
                    val timestamp = json.asLong
                    Date(timestamp)
                } catch (e2: Exception) {
                    try {
                        val doubleValue = json.asDouble
                        val timestamp = doubleValue.toLong()
                        Date(timestamp)
                    } catch (e3: Exception) {
                        Log.e(tag, "Failed to parse date: ${json}")
                        Date()
                    }
                }
            }
        })
        .create()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 20

    private var reconnectJob: Job? = null
    private var isReconnecting = false
    private var currentUserId: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onForceLogout: (() -> Unit)? = null

    fun connect(userId: String) {
        Log.e(tag, "🔌🔌🔌 connect() CALLED for user: $userId 🔌🔌🔌")

        if (isConnected) {
            Log.d(tag, "Already connected, disconnecting first...")
            disconnect()
        }

        if (userId.isEmpty()) {
            Log.e(tag, "Cannot connect: userId is empty")
            return
        }

        val deviceId = userPreferences.getDeviceId()
        if (deviceId == null || deviceId.isEmpty()) {
            Log.e(tag, "Cannot connect: deviceId is null or empty")
            return
        }

        currentUserId = userId
        Log.d(tag, "Connecting WebSocket for user: $userId, device: $deviceId")

        val request = Request.Builder()
            .url("ws://192.168.43.150:8080/ws/estimates?userId=$userId&deviceId=$deviceId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.e(tag, "🎉🎉🎉 WEBSOCKET ON OPEN! User: $userId 🎉🎉🎉")
                isConnected = true
                reconnectAttempts = 0
                isReconnecting = false
                reconnectJob?.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "📩 Received message: $text")

                if (text == "ping") {
                    webSocket.send("pong")
                    Log.d(tag, "🏓 Received ping from server, sent pong")
                    return
                }

                handleSyncMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "💀💀💀 WEBSOCKET ON FAILURE: ${t.message} 💀💀💀")
                isConnected = false

                if (currentUserId != null) {
                    Log.d(tag, "🔌 Failure detected, starting reconnect for user: $currentUserId")
                    startReconnect(currentUserId!!)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.e(tag, "🔴🔴🔴 WebSocket closed: $reason (code: $code) 🔴🔴🔴")
                isConnected = false

                when (code) {
                    1000 -> {
                        Log.d(tag, "✅ Normal closure (code 1000), user initiated logout, not reconnecting")
                        reconnectAttempts = 0
                        isReconnecting = false
                    }
                    else -> {
                        Log.d(tag, "🔄 Non-normal closure (code $code), starting reconnect for user: $currentUserId")
                        if (currentUserId != null) {
                            startReconnect(currentUserId!!)
                        }
                    }
                }
            }
        })
    }

    private fun startReconnect(userId: String) {
        if (isReconnecting) {
            Log.d(tag, "Reconnect already in progress, skipping")
            return
        }

        isReconnecting = true
        reconnectJob?.cancel()

        reconnectJob = scope.launch {
            while (!isConnected && reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++

                val delayMs = minOf(2000L * (1 shl minOf(reconnectAttempts - 1, 5)), 60000L)
                Log.d(tag, "🔄 Reconnect attempt $reconnectAttempts/$maxReconnectAttempts in ${delayMs}ms")

                delay(delayMs)

                if (isConnected) {
                    Log.d(tag, "✅ Already connected, cancelling reconnect")
                    break
                }

                if (hasInternetConnection()) {
                    Log.d(tag, "🔌 Attempting reconnect...")
                    disconnect()
                    connect(userId)
                } else {
                    Log.d(tag, "📡 No internet connection, waiting...")
                }
            }

            if (reconnectAttempts >= maxReconnectAttempts && !isConnected) {
                Log.e(tag, "❌ Max reconnect attempts reached, continuing with longer intervals")
                reconnectAttempts = maxReconnectAttempts / 2
            }

            isReconnecting = false
        }
    }

    private fun hasInternetConnection(): Boolean {
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
            Log.e(tag, "Error checking internet connection: ${e.message}")
            false
        }
    }

    private fun handleSyncMessage(message: String) {
        try {
            val syncMessage = gson.fromJson(message, SyncMessage::class.java)
            Log.d(tag, "Processing message type: ${syncMessage.type}, entityType: ${syncMessage.entityType}")

            when (syncMessage.type) {
                "FORCE_LOGOUT" -> {
                    Log.w(tag, "⚠️ Received FORCE_LOGOUT from server")
                    handleForceLogout()
                }
                "CREATE", "UPDATE", "SHARE", "SHARE_ACCEPTED", "SHARE_DECLINED" -> {
                    handleDataMessage(syncMessage)
                }
                "DELETE" -> {
                    handleDeleteMessage(syncMessage)
                }
                else -> {
                    Log.w(tag, "Unknown message type: ${syncMessage.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing message: ${e.message}", e)
        }
    }

    private fun handleForceLogout() {
        scope.launch {
            try {
                Log.e(tag, "🚨🚨🚨 HANDLE FORCE LOGOUT STARTED 🚨🚨🚨")
                val userId = userPreferences.getUserId()
                Log.w(tag, "Processing FORCE_LOGOUT for user: $userId")

                clearAllLocalData()
                disconnect()

                Log.e(tag, "📢 About to invoke onForceLogout callback")
                withContext(Dispatchers.Main) {
                    Log.e(tag, "📢 Invoking onForceLogout on Main thread")
                    onForceLogout?.invoke()
                    Log.e(tag, "📢 onForceLogout callback completed")
                }

                Log.d(tag, "FORCE_LOGOUT processed successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error processing FORCE_LOGOUT: ${e.message}", e)
            }
        }
    }

    private suspend fun clearAllLocalData() {
        Log.d(tag, "🗑️ Clearing all local data (FORCE_LOGOUT)")
        projectDao.deleteAll()
        objectDao.deleteAll()
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
        materialDao.deleteAll()
        workItemDao.deleteAll()
        Log.d(tag, "✅ All local data cleared")
    }



    private fun handleDataMessage(syncMessage: SyncMessage) {
        val data = syncMessage.data
        if (data == null) {
            Log.w(tag, "Message data is null")
            return
        }

        val dataJson = gson.toJson(data)

        when (syncMessage.entityType) {
            "PROJECT" -> {
                try {
                    val project = gson.fromJson(dataJson, Project::class.java)
                    scope.launch {
                        val existing = projectDao.getProjectById(project.id)
                        if (existing == null) {
                            projectDao.insertProject(project)
                            Log.d(tag, "Inserted project: ${project.name} (${project.id})")
                        } else {
                            projectDao.updateProject(project)
                            Log.d(tag, "Updated project: ${project.name} (${project.id})")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing PROJECT message: ${e.message}")
                }
            }

            "OBJECT" -> {
                try {
                    val obj = gson.fromJson(dataJson, ObjectModel::class.java)
                    scope.launch {
                        val existing = objectDao.getObjectById(obj.id)
                        if (existing == null) {
                            objectDao.insertObject(obj)
                            Log.d(tag, "Inserted object: ${obj.name} (${obj.id})")
                        } else {
                            objectDao.updateObject(obj)
                            Log.d(tag, "Updated object: ${obj.name} (${obj.id})")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing OBJECT message: ${e.message}")
                }
            }

            "CONTACT" -> {
                try {
                    val contact = gson.fromJson(dataJson, Contact::class.java)
                    scope.launch {
                        val existing = contactDao.getContactById(contact.id)
                        if (existing == null) {
                            contactDao.insertContact(contact)
                            Log.d(tag, "Inserted contact: ${contact.name} (${contact.id})")
                        } else {
                            contactDao.updateContact(contact)
                            Log.d(tag, "Updated contact: ${contact.name} (${contact.id})")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing CONTACT message: ${e.message}")
                }
            }

            "CONTACT_METHOD" -> {
                try {
                    val method = gson.fromJson(dataJson, ContactMethod::class.java)
                    scope.launch {
                        val existing = contactMethodDao.getContactMethodById(method.id)
                        if (existing == null) {
                            contactMethodDao.insertContactMethod(method)
                            Log.d(tag, "Inserted contact method: ${method.methodType} (${method.id})")
                        } else {
                            contactMethodDao.updateContactMethod(method)
                            Log.d(tag, "Updated contact method: ${method.methodType} (${method.id})")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing CONTACT_METHOD message: ${e.message}")
                }
            }

            "MATERIAL" -> {
                try {
                    val material = gson.fromJson(dataJson, Material::class.java)
                    scope.launch {
                        val existing = materialDao.getMaterialById(material.id)
                        if (existing == null) {
                            materialDao.insertMaterial(material)
                            Log.d(tag, "Inserted material: ${material.name} (${material.id})")
                        } else {
                            materialDao.updateMaterial(material)
                            Log.d(tag, "Updated material: ${material.name} (${material.id})")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing MATERIAL message: ${e.message}")
                }
            }

            "WORK_ITEM" -> {
                try {
                    val workItem = gson.fromJson(dataJson, WorkItem::class.java)
                    scope.launch {
                        val existing = workItemDao.getWorkItemById(workItem.id)
                        if (existing == null) {
                            workItemDao.insertWorkItem(workItem)
                            Log.d(tag, "Inserted work item: ${workItem.name} (${workItem.id})")
                        } else {
                            workItemDao.updateWorkItem(workItem)
                            Log.d(tag, "Updated work item: ${workItem.name} (${workItem.id})")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing WORK_ITEM message: ${e.message}")
                }
            }

            else -> {
                Log.w(tag, "Unknown entity type: ${syncMessage.entityType}")
            }
        }
    }

    private fun handleDeleteMessage(syncMessage: SyncMessage) {
        val entityId = syncMessage.entityId
        if (entityId.isNullOrEmpty()) {
            Log.w(tag, "Delete message has no entityId")
            return
        }

        when (syncMessage.entityType) {
            "PROJECT" -> {
                scope.launch {
                    projectDao.deleteProjectById(entityId)
                    Log.d(tag, "Deleted project: $entityId")
                }
            }
            "MATERIAL" -> {
                scope.launch {
                    materialDao.deleteMaterialById(entityId)
                    Log.d(tag, "Deleted material: $entityId")
                }
            }
            "WORK_ITEM" -> {
                scope.launch {
                    workItemDao.deleteWorkItemById(entityId)
                    Log.d(tag, "Deleted work item: $entityId")
                }
            }
            "CONTACT" -> {
                scope.launch {
                    contactDao.deleteContactById(entityId)
                    Log.d(tag, "Deleted contact: $entityId")
                }
            }
            "CONTACT_METHOD" -> {
                scope.launch {
                    contactMethodDao.deleteContactMethodById(entityId)
                    Log.d(tag, "Deleted contact method: $entityId")
                }
            }
            "OBJECT" -> {
                scope.launch {
                    objectDao.deleteObjectById(entityId)
                    Log.d(tag, "Deleted object: $entityId")
                }
            }
            else -> {
                Log.w(tag, "Unknown entity type for delete: ${syncMessage.entityType}")
            }
        }
    }

    fun disconnect() {
        Log.d(tag, "Disconnecting WebSocket")
        reconnectJob?.cancel()
        isReconnecting = false
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        reconnectAttempts = 0
    }

    fun isConnected(): Boolean = isConnected
}