package com.example.myapplication.viewmodels

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.network.ApiService
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.data.repository.ObjectRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.FuzzySearch
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Date

enum class ProjectFilterType {
    BY_NAME,
    BY_DESCRIPTION,
    BY_CUSTOMER,
    BY_FOREMAN,
    BY_MANAGER
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val objectRepository: ObjectRepository,
    val syncManager: SyncManager,           // ← ДОБАВЛЕНО
    private val userPreferences: UserPreferences,   // ← ДОБАВЛЕНО
    private val apiService: ApiService
) : ViewModel() {

    // ==================== ПЕРЕМЕННЫЕ СОСТОЯНИЯ ====================

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentFilter = MutableStateFlow(ProjectFilterType.BY_NAME)
    val currentFilter: StateFlow<ProjectFilterType> = _currentFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _duplicatesCache = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val duplicatesCache: StateFlow<Map<String, List<String>>> = _duplicatesCache

    private val _duplicatesVersion = MutableStateFlow(0)
    val duplicatesVersion: StateFlow<Int> = _duplicatesVersion

    var showCreateDialog = mutableStateOf(false)

    // ==================== ФИЛЬТРОВАННЫЕ СПИСКИ ====================

    val filteredProjects: StateFlow<List<Project>> = combine(
        _projects,
        _searchQuery,
        _currentFilter,
        _contacts
    ) { projects, query, filter, contacts ->
        if (query.isBlank()) {
            projects
        } else {
            when (filter) {
                ProjectFilterType.BY_NAME -> {
                    FuzzySearch.filter(
                        items = projects,
                        query = query,
                        textExtractor = { it.name },
                        maxDistance = 2
                    )
                }
                ProjectFilterType.BY_DESCRIPTION -> {
                    FuzzySearch.filter(
                        items = projects,
                        query = query,
                        textExtractor = { it.description },
                        maxDistance = 2
                    )
                }
                ProjectFilterType.BY_CUSTOMER -> {
                    projects.filter { project ->
                        project.customerContactId?.let { contactId ->
                            val contact = contacts.find { it.id == contactId }
                            contact?.let {
                                FuzzySearch.matches(it.name, query, maxDistance = 2) ||
                                        FuzzySearch.matches(it.description, query, maxDistance = 2)
                            } ?: false
                        } ?: false
                    }
                }
                ProjectFilterType.BY_FOREMAN -> {
                    projects.filter { project ->
                        project.foremanContactId?.let { contactId ->
                            val contact = contacts.find { it.id == contactId }
                            contact?.let {
                                FuzzySearch.matches(it.name, query, maxDistance = 2) ||
                                        FuzzySearch.matches(it.description, query, maxDistance = 2)
                            } ?: false
                        } ?: false
                    }
                }
                ProjectFilterType.BY_MANAGER -> {
                    projects.filter { project ->
                        project.managerContactId?.let { contactId ->
                            val contact = contacts.find { it.id == contactId }
                            contact?.let {
                                FuzzySearch.matches(it.name, query, maxDistance = 2) ||
                                        FuzzySearch.matches(it.description, query, maxDistance = 2)
                            } ?: false
                        } ?: false
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    init {
        loadProjects()
        loadContacts()
    }

    // ==================== ЗАГРУЗКА ДАННЫХ ====================
    // ИЗМЕНЕНО: теперь синхронизирует с сервером

    fun loadProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("ProjectsViewModel", "loadProjects started")

            val userId = userPreferences.getUserId()
            if (userId == null) {
                Log.e("ProjectsViewModel", "User not logged in")
                _isLoading.value = false
                return@launch
            }

            try {
                // Загружаем из Room (локально) с фильтрацией по userId
                repository.getProjectsForUser(userId).collect { projects ->
                    // !!! Дополнительная фильтрация для безопасности !!!
                    val filteredProjects = projects.filter { it.userId == userId || it.userId.isNullOrEmpty() }
                    _projects.value = filteredProjects
                    Log.d("ProjectsViewModel", "Loaded ${filteredProjects.size} projects from Room")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("ProjectsViewModel", "Error loading projects: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadProjectsFromRoom(userId: String) {
        repository.getProjectsForUser(userId).collect { projects ->
            _projects.value = projects
            Log.d("ProjectsViewModel", "Loaded ${projects.size} projects from Room")
        }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            repository.getAllContacts().collect { list ->
                _contacts.value = list
                updateDuplicatesCache()
            }
        }
    }

    suspend fun hasAnyContacts(): Boolean {
        return try {
            repository.getContactsCount() > 0
        } catch (e: Exception) {
            false
        }
    }

    // ==================== РАБОТА С ДУБЛИКАТАМИ ====================

    fun hasDuplicates(contactId: String): Boolean {
        return _duplicatesCache.value.containsKey(contactId)
    }

    private suspend fun getRootObjectId(): String {
        // Сначала пробуем получить из Room
        val rootObjects = objectRepository.getRootObjects().first()
        val rootObject = rootObjects.find { it.name == "Без объекта" }

        if (rootObject != null) {
            return rootObject.id
        }

        // Если в Room нет, пробуем получить с сервера
        val userId = userPreferences.getUserId()
        if (userId != null) {
            try {
                val response = apiService.getRootObjects(userId)
                if (response.isSuccessful) {
                    val serverObjects = response.body() ?: emptyList()
                    val serverRootObject = serverObjects.find { it.name == "Без объекта" }
                    if (serverRootObject != null) {
                        // Сохраняем в Room
                        objectRepository.insertObject(serverRootObject)
                        return serverRootObject.id
                    }
                }
            } catch (e: Exception) {
                Log.e("ProjectsViewModel", "Error getting root objects from server: ${e.message}")
            }
        }

        // Если всё равно нет - создаём новый
        val newRootObject = ObjectModel(
            name = "Без объекта",
            description = "Корневые сметы"
        )
        objectRepository.insertObject(newRootObject)
        return newRootObject.id
    }

    private fun updateDuplicatesCache() {
        val contactsList = _contacts.value
        val methodsMap = mutableMapOf<String, MutableList<String>>()

        viewModelScope.launch {
            for (contact in contactsList) {
                repository.getContactMethods(contact.id).collect { methods ->
                    methods.forEach { method ->
                        val normalizedValue = when {
                            method.methodType.contains("телефон") || method.methodType.contains("phone") -> {
                                method.value.replace(Regex("[^\\d]"), "")
                            }
                            else -> method.value.lowercase()
                        }
                        if (normalizedValue.isNotBlank()) {
                            methodsMap.getOrPut(normalizedValue) { mutableListOf() }.add(contact.id)
                        }
                    }
                    updateDuplicatesFromMap(methodsMap)
                }
            }
        }
    }

    private fun updateDuplicatesFromMap(map: Map<String, List<String>>) {
        val duplicates = mutableMapOf<String, MutableList<String>>()
        map.forEach { (_, contactIds) ->
            if (contactIds.size > 1) {
                contactIds.forEach { contactId ->
                    val otherContacts = contactIds.filter { it != contactId }.toMutableList()
                    if (otherContacts.isNotEmpty()) {
                        duplicates.getOrPut(contactId) { mutableListOf() }.addAll(otherContacts)
                    }
                }
            }
        }
        _duplicatesCache.value = duplicates
        _duplicatesVersion.value = _duplicatesVersion.value + 1
    }

    // ==================== ПОИСК ====================

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchFilter(filter: ProjectFilterType) {
        _currentFilter.value = filter
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _currentFilter.value = ProjectFilterType.BY_NAME
    }

    // ==================== СОЗДАНИЕ ПРОЕКТА ====================
    // ИЗМЕНЕНО: теперь синхронизирует с сервером

    fun createProjectAndAttachToObject(
        name: String,
        description: String,
        objectId: String,
        customerContactId: String?,
        foremanContactId: String?,
        managerContactId: String?,
        includeForeman: Boolean,
        includeManager: Boolean
    ) {
        viewModelScope.launch {
            val project = Project(
                name = name,
                description = description,
                objectId = objectId,
                customerContactId = customerContactId,
                foremanContactId = foremanContactId,
                managerContactId = managerContactId,
                includeForeman = includeForeman,
                includeManager = includeManager
            )

            // Сохраняем в Room
            val createdProject = repository.createProject(project)

            // Отправляем на сервер (с очередью при офлайне)
            val userId = userPreferences.getUserId()
            if (userId != null) {
                syncManager.syncProjectToServer(createdProject, userId)
            }

            showCreateDialog.value = false
            loadProjects()
        }
    }

// ProjectsViewModel.kt - исправленный метод

    fun createProjectWithMaterialsAndWorks(
        name: String,
        description: String,
        objectId: String?,  // ← теперь может быть null
        materials: List<Material>,
        workItems: List<WorkItem>,
        customerContactId: String?,
        foremanContactId: String?,
        managerContactId: String?,
        includeForeman: Boolean,
        includeManager: Boolean
    ) {
        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId == null) {
                    Log.e("ProjectsViewModel", "Cannot create project: userId is null")
                    showCreateDialog.value = false
                    return@launch
                }

                // ===== ИСПРАВЛЕНИЕ: обрабатываем null objectId =====
                val finalObjectId = when {
                    objectId == null -> ""
                    objectId == "null" -> ""
                    objectId == "none" -> ""
                    objectId == "root" -> ""
                    objectId.isEmpty() -> ""
                    else -> objectId
                }

                Log.d("ProjectsViewModel", "Creating project with objectId: $finalObjectId")

                val project = Project(
                    name = name,
                    description = description,
                    objectId = finalObjectId,  // ← null для корневых смет
                    customerContactId = customerContactId,
                    foremanContactId = foremanContactId,
                    managerContactId = managerContactId,
                    includeForeman = includeForeman,
                    includeManager = includeManager,
                    userId = userId
                )

                // Сохраняем в Room
                val createdProject = repository.createProject(project)
                Log.d("ProjectsViewModel", "Project saved to Room with objectId: ${createdProject.objectId}")

                // Сохраняем материалы и работы
                materials.forEach { material ->
                    repository.addMaterial(
                        Material(
                            projectId = createdProject.id,
                            name = material.name,
                            quantity = material.quantity,
                            unit = material.unit,
                            unitPrice = material.unitPrice
                        )
                    )
                }

                workItems.forEach { work ->
                    repository.addWorkItem(
                        WorkItem(
                            projectId = createdProject.id,
                            name = work.name,
                            laborHours = work.laborHours,
                            hourlyRate = work.hourlyRate,
                            materialCost = work.materialCost
                        )
                    )
                }

                // Отправляем на сервер
                syncManager.syncProjectToServer(createdProject, userId)

                loadProjects()
                showCreateDialog.value = false

                Log.d("ProjectsViewModel", "Project created successfully: ${createdProject.name}")
            } catch (e: Exception) {
                Log.e("ProjectsViewModel", "Error creating project: ${e.message}", e)
                showCreateDialog.value = false
            }
        }
    }

    // ==================== УДАЛЕНИЕ ПРОЕКТА ====================
    // ИЗМЕНЕНО: синхронизирует удаление с сервером

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            // Удаляем из Room
            repository.deleteProject(project)

            // Отправляем запрос на сервер
            val userId = userPreferences.getUserId()
            if (userId != null && syncManager.hasInternetConnection()) {
                try {
                    val apiService = (repository as? com.example.myapplication.network.ApiService) // Нужно добавить ApiService в конструктор
                    // или используем syncManager
                    syncManager.syncProjectDeletion(project.id, userId)
                } catch (e: Exception) {
                    Log.e("ProjectsViewModel", "Error deleting project from server: ${e.message}")
                    // Добавляем в очередь
                    syncManager.queueOperation(
                        type = "DELETE",
                        entityType = "PROJECT",
                        entityId = project.id,
                        data = project
                    )
                }
            }

            loadProjects()
        }
    }

    // ==================== ПОЛУЧЕНИЕ ДАННЫХ ====================

    suspend fun getProjectById(projectId: String): Project? {
        isLoading.first { !it }
        return _projects.value.find { it.id == projectId }
    }

    suspend fun getMaterialsForProject(projectId: String): List<Material> {
        return try {
            repository.getMaterials(projectId).first()
        } catch (e: Exception) {
            Log.e("ProjectsViewModel", "Error getting materials", e)
            emptyList()
        }
    }

    suspend fun getWorkItemsForProject(projectId: String): List<WorkItem> {
        return try {
            repository.getWorkItems(projectId).first()
        } catch (e: Exception) {
            Log.e("ProjectsViewModel", "Error getting work items", e)
            emptyList()
        }
    }

    // ==================== ОБНОВЛЕНИЕ ПРОЕКТА ====================
    // ИЗМЕНЕНО: синхронизирует обновление с сервером


    fun updateProject(
        projectId: String,
        name: String,
        description: String,
        materials: List<Material>,
        workItems: List<WorkItem>,
        customerContactId: String?,
        foremanContactId: String?,
        managerContactId: String?,
        includeForeman: Boolean,
        includeManager: Boolean
    ) {
        viewModelScope.launch {
            try {
                Log.d("ProjectsViewModel", "Updating project: $projectId")
                Log.d("ProjectsViewModel", "Materials to save: ${materials.size}")
                Log.d("ProjectsViewModel", "Work items to save: ${workItems.size}")

                val oldProject = getProjectById(projectId)
                if (oldProject == null) {
                    Log.e("ProjectsViewModel", "Project not found: $projectId")
                    return@launch
                }

                // 1. Обновляем основные данные проекта
                val updatedProject = oldProject.copy(
                    name = name,
                    description = description,
                    customerContactId = customerContactId,
                    foremanContactId = foremanContactId,
                    managerContactId = managerContactId,
                    includeForeman = includeForeman,
                    includeManager = includeManager,
                    updatedAt = Date()
                )

                // ✅ СОХРАНЯЕМ ПРОЕКТ В ROOM
                repository.updateProject(updatedProject)
                Log.d("ProjectsViewModel", "✅ Project updated in Room")

                // 2. Удаляем старые материалы
                val oldMaterials = repository.getMaterialsOnce(projectId)
                Log.d("ProjectsViewModel", "Old materials count: ${oldMaterials.size}")
                oldMaterials.forEach { material ->
                    repository.deleteMaterial(material)
                    Log.d("ProjectsViewModel", "Deleted material: ${material.name}")
                }

                // 3. Добавляем новые материалы
                materials.forEach { material ->
                    val newMaterial = material.copy(projectId = projectId)
                    repository.addMaterial(newMaterial)
                    Log.d("ProjectsViewModel", "Added material: ${newMaterial.name}")
                }

                // 4. Удаляем старые работы
                val oldWorkItems = repository.getWorkItemsOnce(projectId)
                Log.d("ProjectsViewModel", "Old work items count: ${oldWorkItems.size}")
                oldWorkItems.forEach { work ->
                    repository.deleteWorkItem(work)
                    Log.d("ProjectsViewModel", "Deleted work item: ${work.name}")
                }

                // 5. Добавляем новые работы
                workItems.forEach { work ->
                    val newWork = work.copy(projectId = projectId)
                    repository.addWorkItem(newWork)
                    Log.d("ProjectsViewModel", "Added work item: ${newWork.name}")
                }

                // 6. Пересчитываем бюджет
                val totalMaterialCost = materials.sumOf { it.quantity * it.unitPrice }
                val totalWorkCost = workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost }
                val totalBudget = totalMaterialCost + totalWorkCost
                Log.d("ProjectsViewModel", "Total budget: $totalBudget")

                val finalProject = updatedProject.copy(
                    totalBudget = totalBudget,
                    updatedAt = Date()
                )

                // ✅ СОХРАНЯЕМ ФИНАЛЬНЫЙ ПРОЕКТ С БЮДЖЕТОМ
                repository.updateProject(finalProject)
                Log.d("ProjectsViewModel", "✅ Final project saved with budget: $totalBudget")

                // 7. Проверяем, что данные сохранились
                val verifyMaterials = repository.getMaterialsOnce(projectId)
                val verifyWorkItems = repository.getWorkItemsOnce(projectId)
                Log.d("ProjectsViewModel", "✅ Verified: ${verifyMaterials.size} materials, ${verifyWorkItems.size} work items")

                // 8. Отправляем на сервер
                val userId = userPreferences.getUserId()
                if (userId != null) {
                    syncManager.syncProjectToServer(finalProject, userId)
                }

                // 9. Перезагружаем список проектов
                loadProjects()

                Log.d("ProjectsViewModel", "✅ Project update completed successfully")

            } catch (e: Exception) {
                Log.e("ProjectsViewModel", "Error updating project: ${e.message}", e)
            }
        }
    }

    // ==================== НОВЫЕ МЕТОДЫ ====================

    // Получить только свои сметы (с сервера)
    suspend fun loadProjectsFromServer() {
        val userId = userPreferences.getUserId() ?: return
        if (syncManager.hasInternetConnection()) {
            // Принудительно синхронизируем проекты с сервера
            syncManager.syncDataFromServer(userId)
            // Перезагружаем проекты из Room
            loadProjects()
        }
    }
}