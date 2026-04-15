package com.example.myapplication.viewmodels

import com.example.myapplication.utils.FuzzySearch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaterialsStorageViewModel @Inject constructor(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _allMaterials = MutableStateFlow<List<Material>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredMaterials: StateFlow<List<Material>> = combine(
        _allMaterials,
        _searchQuery
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

    init {
        loadAllMaterials()
    }

    private fun loadAllMaterials() {
        viewModelScope.launch {
            repository.getAllProjects().collect { projects ->
                val allMaterials = mutableListOf<Material>()
                for (project in projects) {
                    repository.getMaterials(project.id).collect { materials ->
                        allMaterials.addAll(materials)
                        _allMaterials.value = allMaterials
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}