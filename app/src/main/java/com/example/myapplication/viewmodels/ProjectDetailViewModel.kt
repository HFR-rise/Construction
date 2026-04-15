package com.example.myapplication.viewmodels

import com.example.myapplication.utils.FuzzySearch
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val repo: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _project = MutableStateFlow<com.example.myapplication.data.models.Project?>(null)
    val project: StateFlow<com.example.myapplication.data.models.Project?> = _project

    private val _materials = MutableStateFlow<List<Material>>(emptyList())
    val materials: StateFlow<List<Material>> = _materials

    private val _workItems = MutableStateFlow<List<WorkItem>>(emptyList())
    val workItems: StateFlow<List<WorkItem>> = _workItems

    // Поиск по материалам
    private val _materialSearchQuery = MutableStateFlow("")
    val materialSearchQuery: StateFlow<String> = _materialSearchQuery

    val filteredMaterials: StateFlow<List<Material>> = combine(
        _materials,
        _materialSearchQuery
    ) { materials, query ->
        if (query.isBlank()) {
            materials
        } else {
            FuzzySearch.filter(
                items = materials,
                query = query,
                textExtractor = { it.name },
                maxDistance = 2
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Поиск по работам
    private val _workSearchQuery = MutableStateFlow("")
    val workSearchQuery: StateFlow<String> = _workSearchQuery

    val filteredWorkItems: StateFlow<List<WorkItem>> = combine(
        _workItems,
        _workSearchQuery
    ) { workItems, query ->
        if (query.isBlank()) {
            workItems
        } else {
            FuzzySearch.filter(
                items = workItems,
                query = query,
                textExtractor = { it.name },
                maxDistance = 2
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showMaterialDialog = MutableStateFlow(false)
    val showMaterialDialog: StateFlow<Boolean> = _showMaterialDialog

    private val _showWorkDialog = MutableStateFlow(false)
    val showWorkDialog: StateFlow<Boolean> = _showWorkDialog

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation

    private val _editingMaterial = MutableStateFlow<Material?>(null)
    val editingMaterial: StateFlow<Material?> = _editingMaterial

    private val _editingWorkItem = MutableStateFlow<WorkItem?>(null)
    val editingWorkItem: StateFlow<WorkItem?> = _editingWorkItem

    init {
        loadData()
    }

    fun updateMaterialSearchQuery(query: String) {
        _materialSearchQuery.value = query
    }

    fun updateWorkSearchQuery(query: String) {
        _workSearchQuery.value = query
    }

    fun showMaterialDialog() {
        _showMaterialDialog.value = true
    }

    fun hideMaterialDialog() {
        _showMaterialDialog.value = false
    }

    fun showWorkDialog() {
        _showWorkDialog.value = true
    }

    fun hideWorkDialog() {
        _showWorkDialog.value = false
    }

    fun showDeleteConfirmation() {
        _showDeleteConfirmation.value = true
    }

    fun hideDeleteConfirmation() {
        _showDeleteConfirmation.value = false
    }

    fun startEditMaterial(material: Material) {
        _editingMaterial.value = material
    }

    fun clearEditMaterial() {
        _editingMaterial.value = null
    }

    fun startEditWorkItem(workItem: WorkItem) {
        _editingWorkItem.value = workItem
    }

    fun clearEditWorkItem() {
        _editingWorkItem.value = null
    }

    private fun loadData() {
        viewModelScope.launch {
            _project.value = repo.getProjectById(projectId)

            repo.getMaterials(projectId).collect { list ->
                _materials.value = list
            }

            repo.getWorkItems(projectId).collect { list ->
                _workItems.value = list
                _isLoading.value = false
            }
        }
    }

    fun addMaterial(name: String, quantity: Double, unit: String, price: Double) {
        viewModelScope.launch {
            val material = Material(
                projectId = projectId,
                name = name,
                quantity = quantity,
                unit = unit.ifBlank { "шт" },
                unitPrice = price
            )
            repo.addMaterial(material)
            _showMaterialDialog.value = false
        }
    }

    fun updateMaterial(material: Material) {
        viewModelScope.launch {
            repo.updateMaterial(material)
            _editingMaterial.value = null
        }
    }

    fun deleteMaterial(material: Material) {
        viewModelScope.launch {
            repo.deleteMaterial(material)
        }
    }

    fun addWorkItem(name: String, hours: Double, rate: Double, materialCost: Double) {
        viewModelScope.launch {
            val workItem = WorkItem(
                projectId = projectId,
                name = name,
                laborHours = hours,
                hourlyRate = rate,
                materialCost = materialCost
            )
            repo.addWorkItem(workItem)
            _showWorkDialog.value = false
        }
    }

    fun updateWorkItem(workItem: WorkItem) {
        viewModelScope.launch {
            repo.updateWorkItem(workItem)
            _editingWorkItem.value = null
        }
    }

    fun deleteWorkItem(workItem: WorkItem) {
        viewModelScope.launch {
            repo.deleteWorkItem(workItem)
        }
    }

    fun deleteProject() {
        viewModelScope.launch {
            _project.value?.let { repo.deleteProject(it) }
            _showDeleteConfirmation.value = false
        }
    }
}