package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.viewmodels.AuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    navController: NavController,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val verificationCode by viewModel.verificationCode.collectAsState()
    val isCodeSent by viewModel.isCodeSent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val showAccountInUseError by viewModel.showAccountInUseError.collectAsState()

    // Состояние для анимации появления кнопки "Попробовать снова"
    var showRetryButton by remember { mutableStateOf(false) }

    // Показываем кнопку "Попробовать снова" через 1 секунду после ошибки 409
    LaunchedEffect(showAccountInUseError) {
        if (showAccountInUseError) {
            delay(1000)
            showRetryButton = true
        } else {
            showRetryButton = false
        }
    }

    // Если уже авторизован, вызываем onLoginSuccess
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Вход в приложение",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Иконка приложения
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Сметы",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!isCodeSent) {
                // ===== ЭКРАН ВВОДА НОМЕРА ТЕЛЕФОНА =====
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { viewModel.updatePhoneNumber(it) },
                    label = { Text(text = "Номер телефона") },
                    placeholder = { Text(text = "+7 (XXX) XXX-XX-XX") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null && !showAccountInUseError
                )

                if (errorMessage != null && !showAccountInUseError) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.sendCode() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneNumber.isNotBlank() && !isLoading && !showAccountInUseError
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text(text = "Получить код")
                    }
                }
            } else {
                // ===== ЭКРАН ВВОДА КОДА =====

                // Информация о номере телефона
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Код отправлен на номер",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = phoneNumber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.updatePhoneNumber("")
                                viewModel.updateVerificationCode("")
                                viewModel.resetToPhoneInput()
                                viewModel.clearAccountInUseError()
                            }
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Изменить номер",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Поле ввода кода
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = {
                        viewModel.updateVerificationCode(it)
                        // Очищаем ошибку при вводе
                        if (showAccountInUseError) {
                            viewModel.clearAccountInUseError()
                        }
                    },
                    label = { Text(text = "Код подтверждения") },
                    placeholder = { Text(text = "123456") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null,
                    enabled = !showAccountInUseError
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ===== ОБРАБОТКА ОШИБОК =====
                when {
                    showAccountInUseError -> {
                        // Специальная карточка для ошибки 409
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = errorMessage ?: "Аккаунт уже используется на другом устройстве",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Вы не можете войти, так как аккаунт уже активен на другом устройстве. Если это были вы, пожалуйста, выйдите из аккаунта на другом устройстве.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (showRetryButton) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.retryWithSamePhone()
                                                showRetryButton = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Попробовать снова")
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.resetToPhoneInputWithClear()
                                            showRetryButton = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Ввести другой номер")
                                    }
                                }
                            }
                        }
                    }

                    errorMessage != null -> {
                        // Обычная ошибка (неверный код и т.д.)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ===== КНОПКА ВХОДА =====
                Button(
                    onClick = {
                        viewModel.verifyCode()
                        // Сбрасываем показ кнопки повторной попытки
                        showRetryButton = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = verificationCode.length >= 4 && !isLoading && !showAccountInUseError,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showAccountInUseError)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text(text = "Войти")
                    }
                }

                // ===== КНОПКА "ИЗМЕНИТЬ НОМЕР" (только если нет ошибки 409) =====
                if (!showAccountInUseError) {
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            viewModel.updatePhoneNumber("")
                            viewModel.updateVerificationCode("")
                            viewModel.resetToPhoneInput()
                            viewModel.clearAccountInUseError()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Изменить номер телефона", fontSize = 12.sp)
                    }
                }
            }

            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    onLoginSuccess()
                }
            }
        }
    }
}

// ===== ДОПОЛНИТЕЛЬНЫЙ КОМПОНЕНТ ДЛЯ ОШИБКИ (опционально) =====
@Composable
fun AccountInUseDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onChangePhone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Аккаунт уже используется")
            }
        },
        text = {
            Column {
                Text(
                    "Вы не можете войти, так как аккаунт уже активен на другом устройстве.",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Если это были вы, пожалуйста, выйдите из аккаунта на другом устройстве.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("Попробовать снова")
            }
        },
        dismissButton = {
            TextButton(onClick = onChangePhone) {
                Text("Ввести другой номер")
            }
        }
    )
}