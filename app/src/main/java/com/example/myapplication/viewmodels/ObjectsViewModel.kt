package com.example.myapplication.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.repository.ObjectRepository
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.utils.FuzzySearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Используем один источник parentId
    private val _showDeleteProjectConfirmation = MutableStateFlow(false)
    val showDeleteProjectConfirmation: StateFlow<Boolean> = _showDeleteProjectConfirmation

    private val _projectToDelete = MutableStateFlow<Project?>(null)
    val projectToDelete: StateFlow<Project?> = _projectToDelete

    private var currentParentId: String? = savedStateHandle.get<String>("parentId")

    private val _objects = MutableStateFlow<List<ObjectModel>>(emptyList())
    val objects: StateFlow<List<ObjectModel>> = _objects

    private val _projectsInObject = MutableStateFlow<List<Project>>(emptyList())
    val projectsInObject: StateFlow<List<Project>> = _projectsInObject

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    private val _showCreateTypeDialog = MutableStateFlow(false)
    val showCreateTypeDialog: StateFlow<Boolean> = _showCreateTypeDialog

    private val _currentObjectName = MutableStateFlow<String?>(null)
    val currentObjectName: StateFlow<String?> = _currentObjectName

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation

    private val _objectToDelete = MutableStateFlow<ObjectModel?>(null)
    val objectToDelete: StateFlow<ObjectModel?> = _objectToDelete

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog

    private val _infoObject = MutableStateFlow<ObjectModel?>(null)
    val infoObject: StateFlow<ObjectModel?> = _infoObject

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _currentFilter = MutableStateFlow(ObjectFilterType.BY_NAME)
    val currentFilter: StateFlow<ObjectFilterType> = _currentFilter.asStateFlow()

    private val _editingObject = MutableStateFlow<ObjectModel?>(null)
    val editingObject: StateFlow<ObjectModel?> = _editingObject

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Для MoveProjectScreen(TO DO: удалить)
    private val _objectsForMove = MutableStateFlow<List<ObjectModel>>(emptyList())
    val objectsForMove: StateFlow<List<ObjectModel>> = _objectsForMove

    private val _projectsInObjectForMove = MutableStateFlow<List<Project>>(emptyList())
    val projectsInObjectForMove: StateFlow<List<Project>> = _projectsInObjectForMove

    private val _isLoadingForMove = MutableStateFlow(false)
    val isLoadingForMove: StateFlow<Boolean> = _isLoadingForMove

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

    init {
        loadData()
        loadCurrentObjectName()
        loadContacts()
        ensureRootObjectExists()
    }

    private fun ensureRootObjectExists() {
        viewModelScope.launch {
            val rootObjects = objectRepository.getRootObjects().first()
            if (rootObjects.none { it.name == "Без объекта" }) {
                val rootObject = ObjectModel(
                    name = "Без объекта",
                    description = "Корневые сметы"
                )
                objectRepository.insertObject(rootObject)
                Log.d("ObjectsViewModel", "Created root object for unassigned projects")
            }
        }
    }

    fun showDeleteProjectConfirmation(project: Project) {
        _projectToDelete.value = project
        _showDeleteProjectConfirmation.value = true
    }

    fun hideDeleteProjectConfirmation() {
        _showDeleteProjectConfirmation.value = false
        _projectToDelete.value = null
    }

    fun confirmDeleteProject() {
        viewModelScope.launch {
            _projectToDelete.value?.let { project ->
                projectRepository.deleteProject(project)
                refreshProjects()
            }
            hideDeleteProjectConfirmation()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            Log.d("ObjectsViewModel", "loadData called, currentParentId: $currentParentId")
            if (currentParentId != null) {
                // Загружаем дочерние объекты
                objectRepository.getChildObjects(currentParentId!!).collect { list ->
                    _objects.value = list
                    Log.d("ObjectsViewModel", "Loaded ${list.size} child objects")
                }
                // Загружаем сметы для этого объекта
                projectRepository.getProjectsByObjectId(currentParentId!!).collect { projects ->
                    _projectsInObject.value = projects
                    Log.d(
                        "ObjectsViewModel",
                        "Loaded ${projects.size} projects for object $currentParentId"
                    )
                }
            } else {
                objectRepository.getRootObjects().collect { list ->
                    _objects.value = list
                    Log.d("ObjectsViewModel", "Loaded ${list.size} root objects")
                }
                _projectsInObject.value = emptyList()
            }
        }
    }

    private fun loadCurrentObjectName() {
        viewModelScope.launch {
            if (currentParentId != null) {
                val obj = objectRepository.getObjectById(currentParentId!!)
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

    // Обновляем parentId и перезагружаем данные
    fun updateParentId(newParentId: String?) {
        Log.d("ObjectsViewModel", "updateParentId: $currentParentId -> $newParentId")
        currentParentId = newParentId
        loadData()
        loadCurrentObjectName()
    }

    fun createObject(
        name: String,
        street: String,
        house: String,
        building: String,
        description: String
    ) {
        viewModelScope.launch {
            val obj = ObjectModel(
                name = name,
                street = street,
                house = house,
                building = building,
                description = description,
                parentObjectId = currentParentId
            )
            objectRepository.insertObject(obj)
            _showCreateDialog.value = false
            loadData()
        }
    }

    fun createProjectAndAttachToObject(name: String, description: String, objectId: String) {
        viewModelScope.launch {
            val project = Project(
                name = name,
                description = description,
                objectId = objectId
            )
            projectRepository.createProject(project)
            refreshProjects()
            _showCreateTypeDialog.value = false
        }
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

    fun startEditing(obj: ObjectModel) {
        _editingObject.value = obj
        _showEditDialog.value = true
    }

    fun hideEditDialog() {
        _showEditDialog.value = false
        _editingObject.value = null
    }

    fun updateObject(obj: ObjectModel) {
        viewModelScope.launch {
            objectRepository.updateObject(obj)
            hideEditDialog()
            loadData()
        }
    }

    fun deleteObject(obj: ObjectModel) {
        viewModelScope.launch {
            objectRepository.deleteObject(obj)
            loadData()
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            if (currentParentId != null) {
                projectRepository.getProjectsByObjectId(currentParentId!!).collect { projects ->
                    _projectsInObject.value = projects
                    Log.d("ObjectsViewModel", "Refreshed ${projects.size} projects")
                }
            } else {
                _projectsInObject.value = emptyList()
            }
        }
    }

    fun moveProject(projectId: String, newObjectId: String) {
        viewModelScope.launch {
            try {
                Log.d("ObjectsViewModel", "Moving project $projectId to object $newObjectId")
                projectRepository.updateProjectObjectId(projectId, newObjectId)
                refreshProjects()
            } catch (e: Exception) {
                Log.e("ObjectsViewModel", "Error moving project: ${e.message}")
            }
        }
    }

    // Методы для MoveProjectScreen (TO DO: не забыть удалить)
    fun loadRootObjectsForMove() {
        viewModelScope.launch {
            try {
                _isLoadingForMove.value = true
                val objects = objectRepository.getRootObjects().first()
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
                val childObjects = objectRepository.getChildObjects(parentId).first()
                _objectsForMove.value = childObjects.filter { it.name != "Без объекта" }
                val projects = projectRepository.getProjectsByObjectId(parentId).first()
                _projectsInObjectForMove.value = projects
                _isLoadingForMove.value = false
            } catch (e: Exception) {
                Log.e("MoveProject", "Error: ${e.message}")
                _isLoadingForMove.value = false
            }
        }
    }
}