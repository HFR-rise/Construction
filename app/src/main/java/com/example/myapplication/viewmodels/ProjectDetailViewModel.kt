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

    // Триггер для принудительного обновления
    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger

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

    private val _editingMaterial = MutableStateFlow<Material?>(null)
    val editingMaterial: StateFlow<Material?> = _editingMaterial

    private val _editingWorkItem = MutableStateFlow<WorkItem?>(null)
    val editingWorkItem: StateFlow<WorkItem?> = _editingWorkItem

    init {
        if (projectId.isNotEmpty()) {
            loadData()
        } else {
            Log.e("ProjectDetailViewModel", "projectId is empty!")
            _isLoading.value = false
        }
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
            _isLoading.value = true
            Log.d("ProjectDetailViewModel", "Loading data for projectId: $projectId")

            try {
                _project.value = repo.getProjectById(projectId)

                // Подписываемся на изменения материалов с refreshTrigger
                combine(
                    repo.getMaterials(projectId),
                    _refreshTrigger
                ) { materials, _ ->
                    materials
                }.collect { list ->
                    Log.d("ProjectDetailViewModel", "Materials loaded: ${list.size}")
                    _materials.value = list
                }

                // Подписываемся на изменения работ с refreshTrigger
                combine(
                    repo.getWorkItems(projectId),
                    _refreshTrigger
                ) { workItems, _ ->
                    workItems
                }.collect { list ->
                    Log.d("ProjectDetailViewModel", "Work items loaded: ${list.size}")
                    _workItems.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Error loading data: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    fun refreshData() {
        Log.d("ProjectDetailViewModel", "Refresh triggered")
        _refreshTrigger.value++
    }

    fun addMaterial(name: String, quantity: Double, unit: String, price: Double) {
        viewModelScope.launch {
            try {
                Log.d("ProjectDetailViewModel", "Adding material: $name")
                val material = Material(
                    projectId = projectId,
                    name = name,
                    quantity = quantity,
                    unit = unit.ifBlank { "шт" },
                    unitPrice = price
                )
                repo.addMaterial(material)
                _showMaterialDialog.value = false
                refreshData()
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Error adding material: ${e.message}")
            }
        }
    }

    fun updateMaterial(material: Material) {
        viewModelScope.launch {
            try {
                Log.d("ProjectDetailViewModel", "Updating material: ${material.name}")
                repo.updateMaterial(material)
                _editingMaterial.value = null
                refreshData()
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Error updating material: ${e.message}")
            }
        }
    }

    fun deleteMaterial(material: Material) {
        viewModelScope.launch {
            try {
                Log.d("ProjectDetailViewModel", "Deleting material: ${material.name}")
                repo.deleteMaterial(material)
                refreshData()
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Error deleting material: ${e.message}")
            }
        }
    }

    fun addWorkItem(name: String, hours: Double, rate: Double, materialCost: Double) {
        viewModelScope.launch {
            try {
                Log.d("ProjectDetailViewModel", "Adding work item: $name")
                val workItem = WorkItem(
                    projectId = projectId,
                    name = name,
                    laborHours = hours,
                    hourlyRate = rate,
                    materialCost = materialCost
                )
                repo.addWorkItem(workItem)
                _showWorkDialog.value = false
                refreshData()
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Error adding work item: ${e.message}")
            }
        }
    }

    fun updateWorkItem(workItem: WorkItem) {
        viewModelScope.launch {
            try {
                Log.d("ProjectDetailViewModel", "Updating work item: ${workItem.name}")
                repo.updateWorkItem(workItem)
                _editingWorkItem.value = null
                refreshData()
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Error updating work item: ${e.message}")
            }
        }
    }

    fun deleteWorkItem(workItem: WorkItem) {
        viewModelScope.launch {
            try {
                Log.d("ProjectDetailViewModel", "Deleting work item: ${workItem.name}")
                repo.deleteWorkItem(workItem)
                refreshData()
            } catch (e: Exception) {
                Log.e("ProjectDetailViewModel", "Error deleting work item: ${e.message}")
            }
        }
    }
}