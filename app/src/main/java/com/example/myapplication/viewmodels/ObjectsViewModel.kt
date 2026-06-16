package com.example.myapplication.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.repository.ObjectRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.network.ApiService
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.FuzzySearch
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import com.example.myapplication.data.repository.DeletionResult
import kotlinx.coroutines.flow.first

enum class ObjectFilterType {
    BY_NAME,
    BY_DESCRIPTION,
    BY_ADDRESS
}

@HiltViewModel
class ObjectsViewModel @Inject constructor(
    private val objectRepository: ObjectRepository,
    private val projectRepository: ProjectRepository,
    private val apiService: ApiService,
    private val userPreferences: UserPreferences,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ===== ПРИВАТНЫЕ ПОЛЯ =====
    private val _showDeleteProjectConfirmation = MutableStateFlow(false)
    private val _projectToDelete = MutableStateFlow<Project?>(null)
    private var _currentParentId: String? = savedStateHandle.get<String>("parentId")
    private val _objects = MutableStateFlow<List<ObjectModel>>(emptyList())
    private val _projectsInObject = MutableStateFlow<List<Project>>(emptyList())
    private val _rootLevelProjects = MutableStateFlow<List<Project>>(emptyList())
    private val _showRootProjects = MutableStateFlow(true)
    private val _showCreateDialog = MutableStateFlow(false)
    private val _showCreateTypeDialog = MutableStateFlow(false)
    private val _currentObjectName = MutableStateFlow<String?>(null)
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _showDeleteConfirmation = MutableStateFlow(false)
    private val _objectToDelete = MutableStateFlow<ObjectModel?>(null)
    private val _showInfoDialog = MutableStateFlow(false)
    private val _infoObject = MutableStateFlow<ObjectModel?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _currentFilter = MutableStateFlow(ObjectFilterType.BY_NAME)
    private val _editingObject = MutableStateFlow<ObjectModel?>(null)
    private val _showEditDialog = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _objectsForMove = MutableStateFlow<List<ObjectModel>>(emptyList())
    private val _projectsInObjectForMove = MutableStateFlow<List<Project>>(emptyList())
    private val _isLoadingForMove = MutableStateFlow(false)
    private val _deletionResult = MutableStateFlow<DeletionResult?>(null)
    private val _showDeletionResult = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    // ===== ПУБЛИЧНЫЕ ПОЛЯ (StateFlow) =====
    val showDeleteProjectConfirmation: StateFlow<Boolean> = _showDeleteProjectConfirmation
    val projectToDelete: StateFlow<Project?> = _projectToDelete
    val currentParentId: String? get() = _currentParentId
    val objects: StateFlow<List<ObjectModel>> = _objects
    val projectsInObject: StateFlow<List<Project>> = _projectsInObject
    val rootLevelProjects: StateFlow<List<Project>> = _rootLevelProjects
    val showRootProjects: StateFlow<Boolean> = _showRootProjects
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog
    val showCreateTypeDialog: StateFlow<Boolean> = _showCreateTypeDialog
    val currentObjectName: StateFlow<String?> = _currentObjectName
    val contacts: StateFlow<List<Contact>> = _contacts
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation
    val objectToDelete: StateFlow<ObjectModel?> = _objectToDelete
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog
    val infoObject: StateFlow<ObjectModel?> = _infoObject
    val searchQuery: StateFlow<String> = _searchQuery
    val currentFilter: StateFlow<ObjectFilterType> = _currentFilter.asStateFlow()
    val editingObject: StateFlow<ObjectModel?> = _editingObject
    val showEditDialog: StateFlow<Boolean> = _showEditDialog
    val isLoading: StateFlow<Boolean> = _isLoading
    val objectsForMove: StateFlow<List<ObjectModel>> = _objectsForMove
    val projectsInObjectForMove: StateFlow<List<Project>> = _projectsInObjectForMove
    val isLoadingForMove: StateFlow<Boolean> = _isLoadingForMove
    val deletionResult: StateFlow<DeletionResult?> = _deletionResult
    val showDeletionResult: StateFlow<Boolean> = _showDeletionResult
    val errorMessage: StateFlow<String?> = _errorMessage
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val filteredObjects: StateFlow<List<ObjectModel>> = combine(
        _objects,
        _searchQuery,
        _currentFilter
    ) { objects, query, filter ->
        val visibleObjects = objects.filter { it.name != "Без объекта" }

        if (query.isBlank()) {
            visibleObjects
        } else {
            when (filter) {
                ObjectFilterType.BY_NAME -> {
                    FuzzySearch.filter(
                        items = visibleObjects,
                        query = query,
                        textExtractor = { it.name },
                        maxDistance = 2
                    )
                }
                ObjectFilterType.BY_DESCRIPTION -> {
                    FuzzySearch.filter(
                        items = visibleObjects,
                        query = query,
                        textExtractor = { it.description },
                        maxDistance = 2
                    )
                }
                ObjectFilterType.BY_ADDRESS -> {
                    FuzzySearch.filter(
                        items = visibleObjects,
                        query = query,
                        textExtractor = { it.getFormattedAddress() },
                        maxDistance = 2
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ===== INIT =====
    init {
        loadDataFromLocalOnly()
        loadCurrentObjectName()
        loadContacts()
        ensureRootObjectExists()
    }

    // ===== ПРИВАТНЫЕ МЕТОДЫ =====
    private fun ensureRootObjectExists() {
        viewModelScope.launch {
            try {
                val rootObjects = objectRepository.getRootObjects().first()
                if (rootObjects.none { it.name == "Без объекта" }) {
                    val userId = userPreferences.getUserId()
                    val rootObject = ObjectModel(
                        name = "Без объекта",
                        description = "Корневые сметы (автоматически создан)",
                        userId = userId ?: ""
                    )
                    objectRepository.insertObject(rootObject)
                    Log.d("ObjectsViewModel", "Created root object for unassigned projects: ${rootObject.id}")
                }
            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error ensuring root object: ${e.message}")
            }
        }
    }

    private suspend fun loadDataFromLocal() {
        try {
            if (_currentParentId != null) {
                val list = objectRepository.getChildObjectsOnce(_currentParentId!!)
                _objects.value = list
                Log.d("ObjectsViewModel", "Loaded ${list.size} child objects from local DB")
            } else {
                val list = objectRepository.getRootObjectsOnce()
                val visibleObjects = list.filter { it.name != "Без объекта" }
                _objects.value = visibleObjects
                Log.d("ObjectsViewModel", "Loaded ${visibleObjects.size} root objects from local DB")
            }
        } catch (e: Exception) {
            Log.e("ObjectsViewModel", "Error loading data from local DB: ${e.message}", e)
            _objects.value = emptyList()
        }
    }

    private suspend fun loadProjectsForCurrentObject() {
        try {
            if (_currentParentId != null) {
                val projects = projectRepository.getProjectsByObjectIdOnce(_currentParentId!!)
                _projectsInObject.value = projects
                Log.d("ObjectsViewModel", "Loaded ${projects.size} projects for object $_currentParentId")
            } else {
                _projectsInObject.value = emptyList()
            }
        } catch (e: Exception) {
            Log.e("ObjectsViewModel", "Error loading projects: ${e.message}", e)
            _projectsInObject.value = emptyList()
        }
    }

    private suspend fun loadRootLevelProjects() {
        try {
            val userId = userPreferences.getUserId()
            if (userId == null) {
                _rootLevelProjects.value = emptyList()
                return
            }

            val allProjects = projectRepository.getProjectsForUserOnce(userId)
            val rootProjects = allProjects.filter { project ->
                project.objectId == null ||
                        project.objectId == "" ||
                        project.objectId == "null" ||
                        project.objectId == "none" ||
                        project.objectId == "root"
            }

            _rootLevelProjects.value = rootProjects
            Log.d("ObjectsViewModel", "Loaded ${rootProjects.size} root-level projects")

        } catch (e: Exception) {
            Log.e("ObjectsViewModel", "Error loading root-level projects: ${e.message}")
            _rootLevelProjects.value = emptyList()
        }
    }

    private suspend fun syncChildObjectsFromServer(parentId: String, userId: String) {
        Log.d("ObjectsViewModel", "Syncing child objects for parent: $parentId")

        try {
            val response = apiService.getChildObjects(parentId, userId)
            if (response.isSuccessful) {
                val objectsFromServer = response.body() ?: emptyList()
                Log.d("ObjectsViewModel", "Fetched ${objectsFromServer.size} child objects from server")

                for (obj in objectsFromServer) {
                    try {
                        val objWithUserId = if (obj.userId.isNullOrEmpty()) {
                            obj.copy(userId = userId)
                        } else {
                            obj
                        }
                        objectRepository.insertOrUpdateObject(objWithUserId)
                    } catch (e: Exception) {
                        Log.e("ObjectsViewModel", "Error saving object ${obj.id}: ${e.message}")
                    }
                }

                val updatedList = objectRepository.getChildObjectsOnce(parentId)
                _objects.value = updatedList
                Log.d("ObjectsViewModel", "UI updated with ${updatedList.size} objects")
            } else {
                Log.e("ObjectsViewModel", "Failed to sync child objects: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("ObjectsViewModel", "Error syncing child objects: ${e.message}")
        }
    }

    private suspend fun syncRootObjectsFromServer(userId: String) {
        Log.d("ObjectsViewModel", "Syncing root objects for user: $userId")

        try {
            val response = apiService.getRootObjects(userId)
            if (response.isSuccessful) {
                val objectsFromServer = response.body() ?: emptyList()
                Log.d("ObjectsViewModel", "Fetched ${objectsFromServer.size} root objects from server")

                for (obj in objectsFromServer) {
                    try {
                        val objWithUserId = if (obj.userId.isNullOrEmpty()) {
                            obj.copy(userId = userId)
                        } else {
                            obj
                        }
                        objectRepository.insertOrUpdateObject(objWithUserId)
                    } catch (e: Exception) {
                        Log.e("ObjectsViewModel", "Error saving root object ${obj.id}: ${e.message}")
                    }
                }

                val updatedList = objectRepository.getRootObjectsOnce()
                val visibleObjects = updatedList.filter { it.name != "Без объекта" }
                _objects.value = visibleObjects
                Log.d("ObjectsViewModel", "UI updated with ${visibleObjects.size} root objects")
            } else {
                Log.e("ObjectsViewModel", "Failed to sync root objects: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("ObjectsViewModel", "Error syncing root objects: ${e.message}")
        }
    }

    private fun loadCurrentObjectName() {
        viewModelScope.launch {
            if (_currentParentId != null) {
                val obj = objectRepository.getObjectById(_currentParentId!!)
                _currentObjectName.value = obj?.name
            }
        }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            projectRepository.getAllContacts().collect { list ->
                _contacts.value = list
            }
        }
    }

    private suspend fun getRootObjectId(): String? {
        return objectRepository.getRootObjectId()
    }

    // ===== ПУБЛИЧНЫЕ МЕТОДЫ =====
    fun loadDataFromLocalOnly() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadDataFromLocal()
                if (_currentParentId != null) {
                    loadProjectsForCurrentObject()
                } else {
                    loadRootLevelProjects()
                }
            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error loading data: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshAllData() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            Log.d("ObjectsViewModel", "Manual refresh started")

            try {
                val userId = userPreferences.getUserId()
                if (userId != null && syncManager.hasInternetConnection()) {
                    if (_currentParentId != null) {
                        syncChildObjectsFromServer(_currentParentId!!, userId)
                    } else {
                        syncRootObjectsFromServer(userId)
                    }
                    loadRootLevelProjects()
                    refreshProjects()
                }
                loadDataFromLocalOnly()
            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error during refresh: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateParentId(newParentId: String?) {
        if (_currentParentId == newParentId) return
        Log.d("ObjectsViewModel", "updateParentId: $_currentParentId -> $newParentId")
        _currentParentId = newParentId
        loadDataFromLocalOnly()
        loadCurrentObjectName()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchFilter(filter: ObjectFilterType) {
        _currentFilter.value = filter
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _currentFilter.value = ObjectFilterType.BY_NAME
    }

    fun toggleRootProjectsSection() {
        _showRootProjects.value = !_showRootProjects.value
    }

    fun showCreateDialog() {
        _showCreateDialog.value = true
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
    }

    fun showCreateTypeDialog() {
        _showCreateTypeDialog.value = true
    }

    fun hideCreateTypeDialog() {
        _showCreateTypeDialog.value = false
    }

    fun showDeleteConfirmation(obj: ObjectModel) {
        _objectToDelete.value = obj
        _showDeleteConfirmation.value = true
    }

    fun hideDeleteConfirmation() {
        _showDeleteConfirmation.value = false
        _objectToDelete.value = null
    }

    fun showInfoDialog(obj: ObjectModel) {
        _infoObject.value = obj
        _showInfoDialog.value = true
    }

    fun hideInfoDialog() {
        _showInfoDialog.value = false
        _infoObject.value = null
    }

    fun showDeleteProjectConfirmation(project: Project) {
        _projectToDelete.value = project
        _showDeleteProjectConfirmation.value = true
    }

    fun hideDeleteProjectConfirmation() {
        _showDeleteProjectConfirmation.value = false
        _projectToDelete.value = null
    }

    fun startEditing(obj: ObjectModel) {
        _editingObject.value = obj
        _showEditDialog.value = true
    }

    fun hideEditDialog() {
        _showEditDialog.value = false
        _editingObject.value = null
    }

    fun hideDeletionResult() {
        _showDeletionResult.value = false
        _deletionResult.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun createObject(
        name: String,
        street: String,
        house: String,
        building: String,
        description: String
    ) {
        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId == null) {
                    Log.e("ObjectsViewModel", "User not logged in")
                    _showCreateDialog.value = false
                    return@launch
                }

                val obj = ObjectModel(
                    name = name,
                    street = street,
                    house = house,
                    building = building,
                    description = description,
                    parentObjectId = _currentParentId,
                    userId = userId
                )

                Log.d("ObjectsViewModel", "Creating object: ${obj.name}, parentId: ${_currentParentId}")

                // 1. Сохраняем локально
                objectRepository.insertObject(obj)
                Log.d("ObjectsViewModel", "Object saved locally with id: ${obj.id}")

                // 2. ОБНОВЛЯЕМ UI - добавляем объект в текущий список (без перезагрузки)
                val currentList = _objects.value.toMutableList()
                currentList.add(obj)
                _objects.value = currentList
                Log.d("ObjectsViewModel", "UI updated immediately, new size: ${_objects.value.size}")

                // 3. Отправляем на сервер в фоне
                try {
                    val response = apiService.createObject(obj, userId)
                    if (response.isSuccessful) {
                        val serverObject = response.body()
                        if (serverObject != null) {
                            // Обновляем локальный объект с ID от сервера
                            objectRepository.insertOrUpdateObject(serverObject)

                            // Обновляем список, заменяя временный объект на серверный
                            val updatedList = _objects.value.toMutableList()
                            val index = updatedList.indexOfFirst { it.id == obj.id }
                            if (index != -1) {
                                updatedList[index] = serverObject
                                _objects.value = updatedList
                            }
                            Log.d("ObjectsViewModel", "Object created on server: ${serverObject.id}")
                        }
                    } else {
                        Log.e("ObjectsViewModel", "Server error: ${response.code()}")
                        syncManager.queueOperation("CREATE", "OBJECT", obj.id, obj)
                    }
                } catch (e: Exception) {
                    Log.e("ObjectsViewModel", "Network error: ${e.message}")
                    syncManager.queueOperation("CREATE", "OBJECT", obj.id, obj)
                }

                _showCreateDialog.value = false

            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error creating object: ${e.message}")
                _errorMessage.value = "Ошибка создания: ${e.message}"
                _showCreateDialog.value = false
            }
        }
    }

    fun createProjectAndAttachToObject(name: String, description: String, objectId: String) {
        viewModelScope.launch {
            val userId = userPreferences.getUserId()
            val project = Project(
                name = name,
                description = description,
                objectId = objectId,
                userId = userId ?: ""
            )
            projectRepository.createProject(project)
            refreshProjects()
            _showCreateTypeDialog.value = false
        }
    }

    fun deleteObject(obj: ObjectModel) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("ObjectsViewModel", "Deleting object with cascade: ${obj.name}")

                val userId = userPreferences.getUserId()
                if (userId != null && syncManager.hasInternetConnection()) {
                    try {
                        val response = apiService.deleteObject(obj.id, userId)
                        if (response.isSuccessful) {
                            Log.d("ObjectsViewModel", "Object deleted from server: ${obj.id}")
                        } else {
                            Log.e("ObjectsViewModel", "Server delete failed: ${response.code()}")
                            syncManager.queueOperation("DELETE", "OBJECT", obj.id, obj)
                        }
                    } catch (e: Exception) {
                        Log.e("ObjectsViewModel", "Error deleting from server: ${e.message}")
                        syncManager.queueOperation("DELETE", "OBJECT", obj.id, obj)
                    }
                }

                val result = objectRepository.deleteObjectWithCascade(obj)
                _deletionResult.value = result
                _showDeletionResult.value = true
                Log.d("ObjectsViewModel", "Delete result: ${result}")

                loadDataFromLocalOnly()
            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error deleting object: ${e.message}", e)
                _errorMessage.value = "Ошибка при удалении: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun confirmDeleteProject() {
        viewModelScope.launch {
            _projectToDelete.value?.let { project ->
                try {
                    val userId = userPreferences.getUserId()
                    if (userId != null && syncManager.hasInternetConnection()) {
                        try {
                            val response = apiService.deleteProject(project.id, userId)
                            if (!response.isSuccessful) {
                                syncManager.queueOperation("DELETE", "PROJECT", project.id, project)
                            }
                        } catch (e: Exception) {
                            Log.e("ObjectsViewModel", "Error deleting project from server: ${e.message}")
                            syncManager.queueOperation("DELETE", "PROJECT", project.id, project)
                        }
                    }

                    val result = objectRepository.deleteProjectWithAllData(project)
                    Log.d("ObjectsViewModel", "Deleted project: ${result.projectsDeleted} project")
                    refreshProjects()
                } catch (e: Exception) {
                    Log.e("ObjectsViewModel", "Error in confirmDeleteProject: ${e.message}")
                }
            }
            hideDeleteProjectConfirmation()
        }
    }

    fun updateObject(obj: ObjectModel) {
        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                val objWithUserId = if (obj.userId.isNullOrEmpty()) {
                    obj.copy(userId = userId ?: "")
                } else {
                    obj
                }
                objectRepository.updateObject(objWithUserId)

                if (userId != null && syncManager.hasInternetConnection()) {
                    try {
                        apiService.updateObject(objWithUserId.id, objWithUserId, userId)
                    } catch (e: Exception) {
                        Log.e("ObjectsViewModel", "Error updating object on server: ${e.message}")
                        syncManager.queueOperation("UPDATE", "OBJECT", objWithUserId.id, objWithUserId)
                    }
                }

                hideEditDialog()
                loadDataFromLocalOnly()
            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error updating object: ${e.message}")
                _errorMessage.value = "Ошибка обновления: ${e.message}"
            }
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            if (_currentParentId != null) {
                val projects = projectRepository.getProjectsByObjectIdOnce(_currentParentId!!)
                _projectsInObject.value = projects
                Log.d("ObjectsViewModel", "Refreshed ${projects.size} projects for object $_currentParentId")
            }
            loadRootLevelProjects()
        }
    }

    fun moveProject(projectId: String, newObjectId: String) {
        viewModelScope.launch {
            try {
                Log.d("ObjectsViewModel", "Moving project $projectId to object $newObjectId")

                val project = projectRepository.getProjectById(projectId)
                if (project == null) {
                    Log.e("ObjectsViewModel", "Project not found: $projectId")
                    return@launch
                }

                val finalObjectId = when {
                    newObjectId == "root" -> ""
                    newObjectId == "null" -> ""
                    newObjectId == "none" -> ""
                    else -> newObjectId
                }

                val updatedProject = project.copy(objectId = finalObjectId)
                projectRepository.updateProject(updatedProject)

                val userId = userPreferences.getUserId()
                if (userId != null && syncManager.hasInternetConnection()) {
                    try {
                        apiService.updateProject(projectId, updatedProject, userId)
                    } catch (e: Exception) {
                        Log.e("ObjectsViewModel", "Error syncing: ${e.message}")
                        syncManager.queueOperation("UPDATE", "PROJECT", projectId, updatedProject)
                    }
                }

                refreshProjects()
                loadRootLevelProjects()
                Log.d("ObjectsViewModel", "Project moved successfully")
            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error moving project: ${e.message}")
                _errorMessage.value = "Ошибка перемещения: ${e.message}"
            }
        }
    }

    fun loadRootObjectsForMove() {
        viewModelScope.launch {
            try {
                _isLoadingForMove.value = true
                val objects = objectRepository.getRootObjectsOnce()
                _objectsForMove.value = objects.filter { it.name != "Без объекта" }
                _isLoadingForMove.value = false
            } catch (e: Exception) {
                Log.e("MoveProject", "Error: ${e.message}")
                _isLoadingForMove.value = false
            }
        }
    }

    fun loadChildObjectsForMove(parentId: String) {
        viewModelScope.launch {
            try {
                _isLoadingForMove.value = true
                val childObjects = objectRepository.getChildObjectsOnce(parentId)
                _objectsForMove.value = childObjects.filter { it.name != "Без объекта" }
                val projects = projectRepository.getProjectsByObjectIdOnce(parentId)
                _projectsInObjectForMove.value = projects
                _isLoadingForMove.value = false
            } catch (e: Exception) {
                Log.e("MoveProject", "Error: ${e.message}")
                _isLoadingForMove.value = false
            }
        }
    }

    fun createRootObjectIfNeeded() {
        viewModelScope.launch {
            val rootObjects = objectRepository.getRootObjectsOnce()
            if (rootObjects.none { it.name == "Без объекта" }) {
                val userId = userPreferences.getUserId()
                val rootObject = ObjectModel(
                    name = "Без объекта",
                    description = "Корневые сметы (автоматически создан)",
                    userId = userId ?: ""
                )
                objectRepository.insertObject(rootObject)
                Log.d("ObjectsViewModel", "Created root object on demand: ${rootObject.id}")
            }
        }
    }

    fun refreshData() {
        Log.d("ObjectsViewModel", "Manual refresh requested")
        loadDataFromLocalOnly()
    }

    fun clearAndReload() {
        viewModelScope.launch {
            _objects.value = emptyList()
            _projectsInObject.value = emptyList()
            _rootLevelProjects.value = emptyList()
            loadDataFromLocalOnly()
        }
    }
}