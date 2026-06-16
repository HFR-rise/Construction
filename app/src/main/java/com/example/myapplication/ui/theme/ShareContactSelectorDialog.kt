// app/src/main/java/com/example/myapplication/ui/components/ShareContactSelectorDialog.kt

package com.example.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.viewmodels.ContactsViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContactSelectorDialog(
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit,
    onNoPhoneError: (Contact) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val filteredContacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val scope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    isSearchActive = false
                                    viewModel.clearSearch()
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Поиск по имени...") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                        } else {
                            Text("Выберите контакт", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    navigationIcon = {
                        if (!isSearchActive) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        }
                    },
                    actions = {
                        if (!isSearchActive) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Поиск")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredContacts) { contact ->
                        ShareContactCard(
                            contact = contact,
                            onClick = {
                                scope.launch {
                                    // Получаем способы связи контакта
                                    val methods = viewModel.getContactMethods(contact.id).first()
                                    val hasPhone = methods.any { method ->
                                        method.methodType.contains("телефон", ignoreCase = true) ||
                                                method.methodType.contains("phone", ignoreCase = true)
                                    }

                                    if (!hasPhone) {
                                        onNoPhoneError(contact)
                                    } else {
                                        onContactSelected(contact)
                                    }
                                }
                            }
                        )
                    }

                    if (filteredContacts.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Ничего не найдено", color = Color.Gray)
                                    Text(
                                        text = "По запросу \"$searchQuery\"",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    if (filteredContacts.isEmpty() && searchQuery.isBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.People,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Нет контактов", color = Color.Gray)
                                    Text(
                                        text = "Добавьте контакты в разделе Контакты",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShareContactCard(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (contact.description.isNotBlank()) {
                    Text(
                        text = contact.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Выбрать",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}