package com.example.myapplication.viewmodels

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.data.repository.ObjectRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.utils.FuzzySearch
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
    private val objectRepository: ObjectRepository
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

    fun loadProjects() {
        viewModelScope.launch {
            repository.getAllProjects().collect { list ->
                Log.d("ProjectsViewModel", "Loaded ${list.size} projects")
                _projects.value = list
                _isLoading.value = false
            }
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
        val rootObjects = objectRepository.getRootObjects().first()
        return rootObjects.find { it.name == "Без объекта" }?.id
            ?: throw IllegalStateException("Root object not found")
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

//    fun createProject(name: String, description: String) {
//        viewModelScope.launch {
//            repository.createProject(name, description)
//            showCreateDialog.value = false
//        }
//    }

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
            val createdProject = repository.createProject(project)
            showCreateDialog.value = false
        }
    }

    fun createProjectWithMaterialsAndWorks(
        name: String,
        description: String,
        objectId: String?,
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
                val finalObjectId = when (objectId) {
                    null, "null", "none", "" -> {
                        getRootObjectId()
                    }
                    else -> objectId
                }
                val project = Project(
                    name = name,
                    description = description,
                    objectId = finalObjectId,
                    customerContactId = customerContactId,
                    foremanContactId = foremanContactId,
                    managerContactId = managerContactId,
                    includeForeman = includeForeman,
                    includeManager = includeManager
                )
                val createdProject = repository.createProject(project)

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

                loadProjects()
                showCreateDialog.value = false

                Log.d("ProjectsViewModel", "Project created successfully: ${createdProject.name}")
            } catch (e: Exception) {
                Log.e("ProjectsViewModel", "Error creating project: ${e.message}")
            }
        }
    }

    // ==================== УДАЛЕНИЕ ПРОЕКТА ====================

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    // Получить смету по ID
    suspend fun getProjectById(projectId: String): Project? {
        isLoading.first { !it }
        return _projects.value.find { it.id == projectId }
    }

    // Получить материалы для сметы
    suspend fun getMaterialsForProject(projectId: String): List<Material> {
        return try {
            repository.getMaterials(projectId).first()
        } catch (e: Exception) {
            Log.e("ProjectsViewModel", "Error getting materials", e)
            emptyList()
        }
    }

    // Получить работы для сметы
    suspend fun getWorkItemsForProject(projectId: String): List<WorkItem> {
        return try {
            repository.getWorkItems(projectId).first()
        } catch (e: Exception) {
            Log.e("ProjectsViewModel", "Error getting work items", e)
            emptyList()
        }
    }

    // Обновить проект
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
                val oldProject = getProjectById(projectId) ?: return@launch

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
                repository.updateProject(updatedProject)

                val oldMaterials = getMaterialsForProject(projectId)
                oldMaterials.forEach { material ->
                    repository.deleteMaterial(material)
                }
                materials.forEach { material ->
                    repository.addMaterial(material.copy(projectId = projectId))
                }

                val oldWorkItems = getWorkItemsForProject(projectId)
                oldWorkItems.forEach { work ->
                    repository.deleteWorkItem(work)
                }
                workItems.forEach { work ->
                    repository.addWorkItem(work.copy(projectId = projectId))
                }

                val totalMaterialCost = materials.sumOf { it.quantity * it.unitPrice }
                val totalWorkCost = workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost }
                val totalBudget = totalMaterialCost + totalWorkCost

                val finalProject = updatedProject.copy(
                    totalBudget = totalBudget,
                    updatedAt = Date()
                )
                repository.updateProject(finalProject)

                loadProjects()

                Log.d("ProjectsViewModel", "Project updated successfully")
            } catch (e: Exception) {
                Log.e("ProjectsViewModel", "Error updating project", e)
            }
        }
    }
}