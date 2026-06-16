// ShareViewModel.kt - новый файл

package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _sharingState = MutableStateFlow<SharingState>(SharingState.Idle)
    val sharingState: StateFlow<SharingState> = _sharingState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun shareProjectWithContact(
        projectId: String,
        projectName: String,
        contact: Contact,
        contactMethods: List<ContactMethod>
    ) {
        viewModelScope.launch {
            _sharingState.value = SharingState.Loading

            try {
                // 1. Находим номер телефона в способах связи
                val phoneMethod = contactMethods.find { method ->
                    method.methodType.contains("телефон", ignoreCase = true) ||
                            method.methodType.contains("phone", ignoreCase = true)
                }

                if (phoneMethod == null) {
                    Log.e("ShareViewModel", "Contact has no phone number: ${contact.name}")
                    _errorMessage.value = "NO_PHONE"
                    _sharingState.value = SharingState.Error("У контакта нет номера телефона")
                    return@launch
                }

                val phoneNumber = normalizePhoneNumber(phoneMethod.value)

                if (phoneNumber.isBlank()) {
                    Log.e("ShareViewModel", "Invalid phone number: ${phoneMethod.value}")
                    _errorMessage.value = "INVALID_PHONE"
                    _sharingState.value = SharingState.Error("Неверный формат номера телефона")
                    return@launch
                }

                Log.d("ShareViewModel", "Sharing project $projectId with phone: $phoneNumber")

                // 2. Отправляем запрос на сервер
                val userId = userPreferences.getUserId()
                if (userId == null) {
                    _sharingState.value = SharingState.Error("Пользователь не авторизован")
                    return@launch
                }

                val result = syncManager.shareProject(projectId, phoneNumber, userId)

                if (result.isSuccess) {
                    Log.d("ShareViewModel", "Project shared successfully")
                    _sharingState.value = SharingState.Success(contact)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    Log.e("ShareViewModel", "Share failed: $error")

                    when {
                        error.contains("не найден") -> {
                            _errorMessage.value = "USER_NOT_FOUND"
                            _sharingState.value = SharingState.Error("Пользователь не найден")
                        }
                        error.contains("Нет прав") -> {
                            _errorMessage.value = "NO_PERMISSION"
                            _sharingState.value = SharingState.Error("У вас нет прав для расшаривания этой сметы")
                        }
                        else -> {
                            _errorMessage.value = "GENERIC_ERROR"
                            _sharingState.value = SharingState.Error(error)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ShareViewModel", "Exception during share: ${e.message}")
                _sharingState.value = SharingState.Error("Ошибка: ${e.message}")
                _errorMessage.value = "EXCEPTION"
            }
        }
    }

    fun resetState() {
        _sharingState.value = SharingState.Idle
        _errorMessage.value = null
    }

    private fun normalizePhoneNumber(phone: String): String {
        val digitsOnly = phone.replace(Regex("[^\\d]"), "")

        return when {
            digitsOnly.startsWith("8") && digitsOnly.length == 11 -> "7" + digitsOnly.substring(1)
            digitsOnly.startsWith("7") && digitsOnly.length == 11 -> digitsOnly
            digitsOnly.length == 10 -> "7" + digitsOnly
            else -> digitsOnly
        }
    }
}

sealed class SharingState {
    object Idle : SharingState()
    object Loading : SharingState()
    data class Success(val contact: Contact) : SharingState()
    data class Error(val message: String) : SharingState()
}