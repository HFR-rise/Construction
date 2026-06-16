
package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.SendCodeRequest
import com.example.myapplication.data.repository.*
import com.example.myapplication.data.database.*
import com.example.myapplication.data.models.UserResponse
import com.example.myapplication.data.models.VerifyCodeRequest
import com.example.myapplication.network.ApiService
import com.example.myapplication.services.SyncManager
import com.example.myapplication.services.WebSocketService
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiService: ApiService,
    private val userPreferences: UserPreferences,
    private val webSocketService: WebSocketService,
    private val syncManager: SyncManager,
    private val projectRepository: ProjectRepository,
    private val objectRepository: ObjectRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {


    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber

    private val _verificationCode = MutableStateFlow("")
    val verificationCode: StateFlow<String> = _verificationCode

    private val _isCodeSent = MutableStateFlow(false)
    val isCodeSent: StateFlow<Boolean> = _isCodeSent

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _errorMessageResId = MutableStateFlow<Int?>(null)
    val errorMessageResId: StateFlow<Int?> = _errorMessageResId

    private val _currentUser = MutableStateFlow<UserResponse?>(null)
    val currentUser: StateFlow<UserResponse?> = _currentUser

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _showAccountInUseError = MutableStateFlow(false)
    val showAccountInUseError: StateFlow<Boolean> = _showAccountInUseError


    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    sealed class NavigationEvent {
        object NavigateToAuth : NavigationEvent()
        object NavigateToMain : NavigationEvent()
    }


    init {
        syncManager.onForceLogout = {
            Log.w("AuthViewModel", "Force logout triggered by sync manager")
            forceLogout()
        }

        webSocketService.onForceLogout = {
            Log.w("AuthViewModel", "FORCE_LOGOUT received from server")
            forceLogout()
        }

        val savedUserId = userPreferences.getUserId()
        if (savedUserId != null && userPreferences.isLoggedIn()) {
            _isLoggedIn.value = true
            webSocketService.connect(savedUserId)
            viewModelScope.launch {
                syncManager.syncIfQueueIsEmpty(savedUserId)
            }
        }
    }


    fun updatePhoneNumber(value: String) {
        _phoneNumber.value = value
        _errorMessage.value = null
        _errorMessageResId.value = null
        _showAccountInUseError.value = false
    }

    fun updateVerificationCode(value: String) {
        _verificationCode.value = value
        _errorMessage.value = null
        _errorMessageResId.value = null
        _showAccountInUseError.value = false
    }

    fun sendCode() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _errorMessageResId.value = null
            _showAccountInUseError.value = false

            try {
                val request = SendCodeRequest(_phoneNumber.value)
                val response = apiService.sendCode(request)

                if (response.isSuccessful) {
                    _isCodeSent.value = true
                    Log.d("AuthViewModel", "Code sent successfully to ${_phoneNumber.value}")
                } else {
                    when (response.code()) {
                        400 -> {
                            _errorMessage.value = "Неверный формат номера телефона"
                            _errorMessageResId.value = com.example.myapplication.R.string.invalid_phone_format
                        }
                        429 -> {
                            _errorMessage.value = "Слишком много попыток. Попробуйте позже."
                            _errorMessageResId.value = com.example.myapplication.R.string.too_many_attempts
                        }
                        else -> {
                            _errorMessage.value = "Ошибка отправки кода (${response.code()})"
                        }
                    }
                    Log.e("AuthViewModel", "Send code failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка сети: ${e.message}"
                Log.e("AuthViewModel", "Error sending code: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetToPhoneInput() {
        _isCodeSent.value = false
        _verificationCode.value = ""
        _errorMessage.value = null
        _errorMessageResId.value = null
        _showAccountInUseError.value = false
    }

    fun verifyCode() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _errorMessageResId.value = null
            _showAccountInUseError.value = false

            try {
                val deviceId = getOrCreateDeviceId()

                val request = VerifyCodeRequest(
                    phoneNumber = _phoneNumber.value,
                    code = _verificationCode.value,
                    deviceId = deviceId
                )
                val response = apiService.verifyCode(request)

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    _currentUser.value = user

                    userPreferences.saveUserId(user.id)
                    userPreferences.savePhoneNumber(user.phoneNumber)
                    userPreferences.setLoggedIn(true)
                    userPreferences.saveDeviceId(deviceId)

                    clearAllLocalData()

                    webSocketService.connect(user.id)

                    delay(500)

                    syncManager.syncDataFromServer(user.id)

                    _isLoggedIn.value = true
                    _showAccountInUseError.value = false

                    _navigationEvent.emit(NavigationEvent.NavigateToMain)

                    Log.d("AuthViewModel", "✅ User verified successfully: ${user.id}")

                } else if (response.code() == 409) {
                    handleConflictError(response)

                } else if (response.code() == 400) {
                    _errorMessage.value = "Неверный или просроченный код подтверждения"
                    _errorMessageResId.value = com.example.myapplication.R.string.invalid_verification_code
                    Log.w("AuthViewModel", "Invalid verification code")

                } else if (response.code() == 404) {
                    _errorMessage.value = "Пользователь не найден"
                    Log.w("AuthViewModel", "User not found")

                } else {
                    _errorMessage.value = "Ошибка: ${response.code()} ${response.message()}"
                    Log.e("AuthViewModel", "Verification failed: ${response.code()}")
                }

            } catch (e: Exception) {
                _errorMessage.value = "Ошибка сети: ${e.message}"
                Log.e("AuthViewModel", "Error verifying code: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun handleConflictError(response: retrofit2.Response<UserResponse>) {
        var errorMessage = "Аккаунт уже используется на другом устройстве"

        try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                val json = JSONObject(errorBody)
                val serverMessage = json.optString("error", "")
                if (serverMessage.isNotEmpty()) {
                    errorMessage = serverMessage
                }
            }
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error parsing error response: ${e.message}")
        }

        _errorMessage.value = errorMessage
        _showAccountInUseError.value = true
        _verificationCode.value = ""

        Log.w("AuthViewModel", "❌ Login rejected - account already in use on another device")
    }

    fun clearAccountInUseError() {
        _showAccountInUseError.value = false
        _errorMessage.value = null
        _errorMessageResId.value = null
    }

    fun retryWithSamePhone() {
        _verificationCode.value = ""
        _errorMessage.value = null
        _errorMessageResId.value = null
        _showAccountInUseError.value = false
        Log.d("AuthViewModel", "Retry with same phone: ${_phoneNumber.value}")
    }

    fun resetToPhoneInputWithClear() {
        resetToPhoneInput()
        _showAccountInUseError.value = false
        _phoneNumber.value = ""
        Log.d("AuthViewModel", "Reset to phone input with clear")
    }

    private suspend fun clearAllLocalData() {
        try {
            Log.d("AuthViewModel", "Clearing all local data before new login")
            projectRepository.deleteAllProjects()
            objectRepository.deleteAllObjects()
            contactRepository.deleteAllContacts()
            syncManager.clearAllOperations()
            Log.d("AuthViewModel", "All local data cleared successfully")
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error clearing local data: ${e.message}")
        }
    }


    fun forceLogout() {
        viewModelScope.launch {
            try {
                Log.e("AuthViewModel", "🚨 FORCE LOGOUT EXECUTING 🚨")

                val currentUserId = userPreferences.getUserId()
                if (currentUserId == null) {
                    Log.w("AuthViewModel", "No user ID found, skipping force logout")
                    return@launch
                }

                Log.e("AuthViewModel", "Current userId: $currentUserId")
                Log.e("AuthViewModel", "Current isLoggedIn before: ${_isLoggedIn.value}")

                webSocketService.disconnect()
                Log.d("AuthViewModel", "WebSocket disconnected")

                syncManager.clearAllLocalData()
                Log.d("AuthViewModel", "Local data cleared")

                userPreferences.clear()
                Log.d("AuthViewModel", "SharedPreferences cleared")

                _currentUser.value = null
                _isCodeSent.value = false
                _phoneNumber.value = ""
                _verificationCode.value = ""
                _showAccountInUseError.value = false
                _errorMessage.value = null

                _isLoggedIn.value = false
                Log.e("AuthViewModel", "✅ isLoggedIn set to FALSE")

                _navigationEvent.emit(NavigationEvent.NavigateToAuth)

                Log.d("AuthViewModel", "✅ Force logout completed for user: $currentUserId")

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error during force logout: ${e.message}", e)
            }
        }
    }


    fun logout() {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Executing normal logout")

                val currentUserId = userPreferences.getUserId()
                if (currentUserId == null) {
                    Log.w("AuthViewModel", "No user ID found, skipping logout")
                    return@launch
                }

                if (syncManager.hasInternetConnection()) {
                    try {
                        apiService.logout(currentUserId)
                        Log.d("AuthViewModel", "Logout request sent to server")
                    } catch (e: Exception) {
                        Log.e("AuthViewModel", "Error sending logout to server: ${e.message}")
                    }
                }

                webSocketService.disconnect()
                syncManager.clearAllLocalData()
                userPreferences.clear()

                _isLoggedIn.value = false
                _currentUser.value = null
                _isCodeSent.value = false
                _phoneNumber.value = ""
                _verificationCode.value = ""
                _showAccountInUseError.value = false

                _navigationEvent.emit(NavigationEvent.NavigateToAuth)

                Log.d("AuthViewModel", "✅ Normal logout completed for user: $currentUserId")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error during logout: ${e.message}")
            }
        }
    }

    fun refreshLoginState() {
        viewModelScope.launch {
            delay(100)
            _isLoggedIn.value = true
            Log.d("AuthViewModel", "Login state refreshed")
        }
    }

    private fun getOrCreateDeviceId(): String {
        var deviceId = userPreferences.getDeviceId()
        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            userPreferences.saveDeviceId(deviceId)
            Log.d("AuthViewModel", "Created new deviceId: $deviceId")
        }
        return deviceId
    }

    fun canAttemptLogin(): Boolean {
        return !_showAccountInUseError.value && !_isLoading.value
    }

    fun clearAllStates() {
        _phoneNumber.value = ""
        _verificationCode.value = ""
        _isCodeSent.value = false
        _isLoading.value = false
        _errorMessage.value = null
        _errorMessageResId.value = null
        _showAccountInUseError.value = false
        _currentUser.value = null
    }
}