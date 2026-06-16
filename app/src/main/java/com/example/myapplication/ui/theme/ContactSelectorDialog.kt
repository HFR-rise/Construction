package com.example.myapplication.ui.theme

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
import com.example.myapplication.viewmodels.SearchFilter

// Импортируем функции из ContactsScreen
// Если они там не экспортированы, нужно сделать их public или продублировать с другим именем

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectorDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelect: (Contact) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val filteredContacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val duplicatesVersion by viewModel.duplicatesVersion.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedContactForInfo by remember { mutableStateOf<Contact?>(null) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var selectedDuplicateContact by remember { mutableStateOf<Contact?>(null) }

    var contactMethods by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }
    var duplicateMethods by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }

    LaunchedEffect(selectedContactForInfo) {
        if (selectedContactForInfo != null) {
            viewModel.getContactMethods(selectedContactForInfo!!.id).collect { methods ->
                contactMethods = methods
            }
        }
    }

    LaunchedEffect(selectedDuplicateContact) {
        if (selectedDuplicateContact != null) {
            viewModel.getContactMethods(selectedDuplicateContact!!.id).collect { methods ->
                duplicateMethods = methods
            }
        }
    }

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
                                    placeholder = { Text(getFilterDisplayNameForSelector(currentFilter)) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(
                                        getFilterIconForSelector(currentFilter),
                                        contentDescription = "Фильтр"
                                    )
                                }
                            }
                        } else {
                            Text(title, fontWeight = FontWeight.Bold)
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
                    items(
                        items = filteredContacts,
                        key = { contact -> "${contact.id}_$duplicatesVersion" }
                    ) { contact ->
                        SelectableContactCard(
                            contact = contact,
                            onSelect = { onSelect(contact) },
                            onEdit = { editingContact = contact },
                            onInfo = { selectedContactForInfo = contact },
                            onDuplicateClick = {
                                val duplicateIds = viewModel.getDuplicateContacts(contact.id)
                                duplicateIds.firstOrNull()?.let { duplicateId ->
                                    val duplicateContact = viewModel.contacts.value.find { it.id == duplicateId }
                                    duplicateContact?.let { selectedDuplicateContact = it }
                                }
                            },
                            hasDuplicate = viewModel.hasDuplicates(contact.id)
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
                                Text("Ничего не найдено", color = Color.Gray)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Отмена")
                }
            }
        }
    }

    if (showFilterMenu) {
        AlertDialog(
            onDismissRequest = { showFilterMenu = false },
            title = { Text("Выберите тип поиска") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchFilter.values().forEach { filter ->
                        val isSelected = currentFilter == filter
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSearchFilter(filter)
                                    showFilterMenu = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    getFilterIconForSelector(filter),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = getFilterDisplayNameForSelector(filter),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterMenu = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (selectedContactForInfo != null) {
        ContactDetailsDialog(
            contact = selectedContactForInfo!!,
            methods = contactMethods,
            onDismiss = {
                selectedContactForInfo = null
                contactMethods = emptyList()
            }
        )
    }

    if (selectedDuplicateContact != null) {
        ContactDetailsDialog(
            contact = selectedDuplicateContact!!,
            methods = duplicateMethods,
            onDismiss = {
                selectedDuplicateContact = null
                duplicateMethods = emptyList()
            }
        )
    }

    if (editingContact != null) {
        val contactToEdit = editingContact
        var editMethods by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }

        LaunchedEffect(contactToEdit) {
            if (contactToEdit != null) {
                viewModel.getContactMethods(contactToEdit.id).collect { methods ->
                    editMethods = methods
                }
            }
        }

        ContactEditDialog(
            contact = contactToEdit,
            methods = editMethods,
            onDismiss = { editingContact = null },
            onSave = { name, description, addedMethods, updatedMethods, deletedMethods ->
                val updatedContact = contactToEdit!!.copy(
                    name = name,
                    description = description
                )
                viewModel.updateContact(updatedContact)

                addedMethods.forEach { method ->
                    viewModel.addContactMethod(contactToEdit.id, method.methodType, method.value)
                }
                updatedMethods.forEach { method ->
                    viewModel.updateContactMethod(method)
                }
                deletedMethods.forEach { method ->
                    viewModel.deleteContactMethod(method)
                }
                editingContact = null
            }
        )
    }
}

@Composable
fun SelectableContactCard(
    contact: Contact,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onDuplicateClick: () -> Unit,
    hasDuplicate: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Row {
                if (hasDuplicate) {
                    IconButton(onClick = onDuplicateClick) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Открыть дубликат",
                            tint = Color.Red
                        )
                    }
                }
                IconButton(onClick = onInfo) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Информация",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ==================== ЛОКАЛЬНЫЕ ВЕРСИИ ФУНКЦИЙ (с уникальными именами) ====================

@Composable
fun getFilterIconForSelector(filter: SearchFilter): androidx.compose.ui.graphics.vector.ImageVector {
    return when (filter) {
        SearchFilter.BY_NAME -> Icons.Default.Person
        SearchFilter.BY_DESCRIPTION -> Icons.Default.Description
        SearchFilter.BY_PHONE -> Icons.Default.Phone
        SearchFilter.BY_TELEGRAM -> Icons.Default.Send
        SearchFilter.BY_VK -> Icons.Default.People
        SearchFilter.BY_EMAIL -> Icons.Default.Email
        SearchFilter.BY_OTHER -> Icons.Default.Link
    }
}

fun getFilterDisplayNameForSelector(filter: SearchFilter): String {
    return when (filter) {
        SearchFilter.BY_NAME -> "По имени"
        SearchFilter.BY_DESCRIPTION -> "По описанию"
        SearchFilter.BY_PHONE -> "Телефон"
        SearchFilter.BY_TELEGRAM -> "Telegram"
        SearchFilter.BY_VK -> "VK"
        SearchFilter.BY_EMAIL -> "Email"
        SearchFilter.BY_OTHER -> "Другой способ"
    }
}
