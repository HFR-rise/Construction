package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.repository.ProjectRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendingSharesViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val userPreferences: UserPreferences,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _pendingProjects = MutableStateFlow<List<Project>>(emptyList())
    val pendingProjects: StateFlow<List<Project>> = _pendingProjects

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isAccepting = MutableStateFlow(false)
    val isAccepting: StateFlow<Boolean> = _isAccepting

    private val _isDeclining = MutableStateFlow(false)
    val isDeclining: StateFlow<Boolean> = _isDeclining

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun loadPendingProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val userId = userPreferences.getUserId()
                if (userId == null) {
                    Log.w("PendingShares", "User not logged in")
                    _pendingProjects.value = emptyList()
                    return@launch
                }

                val response = repository.getPendingProjects(userId)
                if (response.isSuccessful) {
                    _pendingProjects.value = response.body() ?: emptyList()
                    Log.d("PendingShares", "Loaded ${_pendingProjects.value.size} pending projects")
                } else {
                    _errorMessage.value = "Ошибка загрузки: ${response.code()}"
                    Log.e("PendingShares", "Failed to load pending projects: ${response.code()}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
                Log.e("PendingShares", "Error loading pending projects: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptShare(projectId: String) {
        viewModelScope.launch {
            if (_isAccepting.value) {
                Log.d("PendingShares", "Already accepting, skipping")
                return@launch
            }

            _isAccepting.value = true
            _errorMessage.value = null

            try {
                val userId = userPreferences.getUserId()
                if (userId == null) {
                    _errorMessage.value = "Пользователь не авторизован"
                    return@launch
                }

                val response = repository.acceptShare(projectId, userId)
                if (response.isSuccessful) {
                    _pendingProjects.value = _pendingProjects.value.filter { it.id != projectId }


                    syncManager.syncDataFromServer(userId)

                    Log.d("PendingShares", "Share accepted successfully for project: $projectId")
                } else {
                    when (response.code()) {
                        404 -> _errorMessage.value = "Приглашение не найдено"
                        403 -> _errorMessage.value = "Нет доступа к этому приглашению"
                        else -> _errorMessage.value = "Ошибка при принятии: ${response.code()}"
                    }
                    Log.e("PendingShares", "Accept share failed: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
                Log.e("PendingShares", "Error accepting share: ${e.message}")
            } finally {
                _isAccepting.value = false
            }
        }
    }

    fun declineShare(projectId: String) {
        viewModelScope.launch {
            if (_isDeclining.value) {
                Log.d("PendingShares", "Already declining, skipping")
                return@launch
            }

            _isDeclining.value = true
            _errorMessage.value = null

            try {
                val userId = userPreferences.getUserId()
                if (userId == null) {
                    _errorMessage.value = "Пользователь не авторизован"
                    return@launch
                }

                val response = repository.declineShare(projectId, userId)
                if (response.isSuccessful) {
                    _pendingProjects.value = _pendingProjects.value.filter { it.id != projectId }

                    Log.d("PendingShares", "Share declined successfully for project: $projectId")


                } else {
                    when (response.code()) {
                        404 -> _errorMessage.value = "Приглашение не найдено"
                        403 -> _errorMessage.value = "Нет доступа к этому приглашению"
                        else -> _errorMessage.value = "Ошибка при отклонении: ${response.code()}"
                    }
                    Log.e("PendingShares", "Decline share failed: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
                Log.e("PendingShares", "Error declining share: ${e.message}")
            } finally {
                _isDeclining.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun refresh() {
        loadPendingProjects()
    }
}