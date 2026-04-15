package com.example.myapplication.ui.theme


import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.viewmodels.ContactsViewModel
import java.util.regex.Pattern
import com.example.myapplication.viewmodels.SearchFilter

fun validateRussianPhone(phone: String): Boolean {
    val digitsOnly = phone.replace(Regex("\\D"), "")

    return when {
        digitsOnly.length == 11 && (digitsOnly.startsWith("7") || digitsOnly.startsWith("8")) -> true
        digitsOnly.length == 10 -> true
        else -> false
    }
}


fun formatRussianPhone(input: String): String {
    val digits = input.replace(Regex("\\D"), "")

    val limitedDigits = digits.take(11)

    return when {
        limitedDigits.startsWith("7") && limitedDigits.length > 1 -> {
            when (limitedDigits.length) {
                1 -> "+7"
                2 -> "+7 (${limitedDigits[1]}"
                3 -> "+7 (${limitedDigits.substring(1, 3)}"
                4 -> "+7 (${limitedDigits.substring(1, 4)}"
                5 -> "+7 (${limitedDigits.substring(1, 4)}) ${limitedDigits[4]}"
                6 -> "+7 (${limitedDigits.substring(1, 4)}) ${limitedDigits.substring(4, 6)}"
                7 -> "+7 (${limitedDigits.substring(1, 4)}) ${limitedDigits.substring(4, 7)}"
                8 -> "+7 (${limitedDigits.substring(1, 4)}) ${limitedDigits.substring(4, 7)}-${limitedDigits[7]}"
                9 -> "+7 (${limitedDigits.substring(1, 4)}) ${limitedDigits.substring(4, 7)}-${limitedDigits.substring(7, 9)}"
                10 -> "+7 (${limitedDigits.substring(1, 4)}) ${limitedDigits.substring(4, 7)}-${limitedDigits.substring(7, 10)}"
                11 -> "+7 (${limitedDigits.substring(1, 4)}) ${limitedDigits.substring(4, 7)}-${limitedDigits.substring(7, 11)}"
                else -> "+7" + limitedDigits.substring(1)
            }
        }
        limitedDigits.startsWith("8") && limitedDigits.length > 1 -> {
            val withSeven = "7" + limitedDigits.substring(1)
            formatRussianPhone(withSeven)
        }
        limitedDigits.length == 10 -> {
            formatRussianPhone("7$limitedDigits")
        }
        limitedDigits.isEmpty() -> ""
        else -> "+7$limitedDigits"
    }
}

enum class ContactMethodType(
    val displayName: String,
    val icon: Any,
    val needsAtSymbol: Boolean = false,
    val prefillAtSymbol: Boolean = false,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val validationRegex: String? = null,
    val placeholder: String = ""
) {
    PHONE(
        displayName = "Телефон",
        icon = Icons.Default.Phone,
        keyboardType = KeyboardType.Phone,
        placeholder = "только российские номера"
    ),
    TELEGRAM(
        displayName = "Telegram",
        icon = Icons.Default.Send,
        prefillAtSymbol = true,
        placeholder = "@username"
    ),
    VK(
        displayName = "VK",
        icon = "VK",
        prefillAtSymbol = true,
        placeholder = "@id... или @username"
    ),
    EMAIL(
        displayName = "Email",
        icon = Icons.Default.Email,
        needsAtSymbol = true,
        keyboardType = KeyboardType.Email,
        validationRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        placeholder = "example@mail.com"
    ),
    CUSTOM(
        displayName = "Иное",
        icon = Icons.Default.Edit,
        placeholder = "Введите произвольный текст"
    )
}


@Composable
fun getFilterIcon(filter: SearchFilter): androidx.compose.ui.graphics.vector.ImageVector {
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

fun getFilterDisplayName(filter: SearchFilter): String {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: NavController,
    viewModel: ContactsViewModel = hiltViewModel()
    ) {
    val filteredContacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val editingContact by viewModel.editingContact.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val duplicatesVersion by viewModel.duplicatesVersion.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedContactForDetails by remember { mutableStateOf<Contact?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    Scaffold(
    floatingActionButton = {
        FloatingActionButton(
            onClick = { viewModel.showAddDialog() },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить контакт")
        }
    },
    topBar = {
        TopAppBar(
            title = {
                if (isSearchActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showFilterMenu = true }
                        ) {
                            Icon(
                                getFilterIcon(currentFilter),
                                contentDescription = "Фильтр",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(getFilterDisplayName(currentFilter)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            )
                        )

                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                        }
                    }
                } else {
                    Text(
                        "Контакты",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            },
            navigationIcon = {},
            actions = {
                if (!isSearchActive) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )

    }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GradientDivider()

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredContacts,
                        key = { contact -> "${contact.id}_${duplicatesVersion}" }
                    ) { contact ->
                        val hasDuplicate = viewModel.hasDuplicates(contact.id)
                        val duplicateIds = viewModel.getDuplicateContacts(contact.id)

                        ContactCard(
                            contact = contact,
                            onContactClick = { selectedContactForDetails = contact },
                            onEdit = { viewModel.startEditing(contact) },
                            onDelete = {
                                contactToDelete = contact
                                showDeleteConfirmation = true
                            },
                            onDuplicateClick = {
                                duplicateIds.firstOrNull()?.let { duplicateId ->
                                    val duplicateContact = viewModel.contacts.value.find { it.id == duplicateId }
                                    duplicateContact?.let { selectedContactForDetails = it }
                                }
                            },
                            hasDuplicate = hasDuplicate
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
                                    Text(
                                        text = "Ничего не найдено",
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "По запросу \"$searchQuery\"",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                if (showDeleteConfirmation && contactToDelete != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showDeleteConfirmation = false
                            contactToDelete = null
                        },
                        title = { Text("Удалить контакт") },
                        text = {
                            Text("Вы уверены, что хотите удалить контакт \"${contactToDelete!!.name}\"?")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    contactToDelete?.let { contact ->
                                        viewModel.deleteContact(contact)
                                    }
                                    showDeleteConfirmation = false
                                    contactToDelete = null
                                }
                            ) {
                                Text("Удалить", color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showDeleteConfirmation = false
                                    contactToDelete = null
                                }
                            ) {
                                Text("Отмена")
                            }
                        }
                    )
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
                    Text("Основные", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color.Gray)

                    listOf(
                        SearchFilter.BY_NAME,
                        SearchFilter.BY_DESCRIPTION
                    ).forEach { filter ->
                        FilterOptionItem(
                            filter = filter,
                            isSelected = currentFilter == filter,
                            onClick = {
                                viewModel.updateSearchFilter(filter)
                                showFilterMenu = false
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Способы связи", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color.Gray)

                    listOf(
                        SearchFilter.BY_PHONE,
                        SearchFilter.BY_TELEGRAM,
                        SearchFilter.BY_VK,
                        SearchFilter.BY_EMAIL,
                        SearchFilter.BY_OTHER
                    ).forEach { filter ->
                        FilterOptionItem(
                            filter = filter,
                            isSelected = currentFilter == filter,
                            onClick = {
                                viewModel.updateSearchFilter(filter)
                                showFilterMenu = false
                            }
                        )
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

    if (selectedContactForDetails != null) {
        ContactDetailsDialog(
            contact = selectedContactForDetails!!,
            methods = viewModel.getMethodsForContact(selectedContactForDetails!!.id)
                .collectAsState(initial = emptyList()).value,
            onDismiss = { selectedContactForDetails = null }
        )
    }

    if (showAddDialog || editingContact != null) {
        val contactToEdit = editingContact

        ContactEditDialog(
            contact = contactToEdit,
            methods = if (contactToEdit != null) {
                viewModel.getMethodsForContact(contactToEdit.id)
                    .collectAsState(initial = emptyList()).value
            } else emptyList(),
            onDismiss = {
                viewModel.hideAddDialog()
                viewModel.clearEditing()
            },
            onSave = { name, description, addedMethods, updatedMethods, deletedMethods ->
                if (contactToEdit == null) {
                    viewModel.addContactWithMethods(name, description, addedMethods)
                } else {
                    val updatedContact = Contact(
                        id = contactToEdit.id,
                        name = name,
                        description = description,
                        createdAt = contactToEdit.createdAt
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
                }
            }
        )
    }
}


@Composable
fun FilterOptionItem(
    filter: SearchFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                getFilterIcon(filter),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = getFilterDisplayName(filter),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ContactCard(
    contact: Contact,
    onContactClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicateClick: () -> Unit,
    hasDuplicate: Boolean
) {
    Card(
    modifier = Modifier
    .fillMaxWidth()
    .clickable { onContactClick() },
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
            // предупреждение (если есть дубликаты)
            if (hasDuplicate) {
                IconButton(onClick = onDuplicateClick) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Есть дубликаты",
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Редактировать",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
}

@Composable
fun ContactDetailsDialog(
    contact: Contact,
    methods: List<ContactMethod>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

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
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = contact.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (contact.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = contact.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Способы связи",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (methods.isEmpty()) {
                    Text(
                        text = "Нет добавленных способов связи",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(methods) { method ->
                            MethodItem(
                                method = method,
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(method.value))
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
fun MethodItem(
    method: ContactMethod,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (val icon = getIconForMethod(method.methodType)) {
                    is String -> {
                        Text(
                            text = icon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Icon(
                            icon as ImageVector,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = method.methodType,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = method.value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Копировать",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun getIconForMethod(methodType: String): Any {
    return when (methodType.lowercase()) {
        "телефон", "phone" -> Icons.Default.Phone
        "telegram" -> Icons.Default.Send
        "vk" -> "VK"
        "email", "почта" -> Icons.Default.Email
        "иное" -> Icons.Default.Edit
        else -> Icons.Default.Link
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditDialog(
    contact: Contact?,
    methods: List<ContactMethod>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<ContactMethod>, List<ContactMethod>, List<ContactMethod>) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var description by remember { mutableStateOf(contact?.description ?: "") }

    val originalName = remember(contact) { contact?.name ?: "" }
    val originalDescription = remember(contact) { contact?.description ?: "" }
    val originalMethods = remember(methods) { methods.toList() }

    var localMethods by remember { mutableStateOf(methods.toList()) }

    LaunchedEffect(methods) {
        localMethods = methods.toList()
    }

    val addedMethods = remember { mutableStateListOf<ContactMethod>() }
    val updatedMethods = remember { mutableStateListOf<ContactMethod>() }
    val deletedMethods = remember { mutableStateListOf<ContactMethod>() }

    var showAddMethodDialog by remember { mutableStateOf(false) }
    var editingMethod by remember { mutableStateOf<ContactMethod?>(null) }

    fun addMethodLocally(methodType: String, value: String) {
        val newMethod = ContactMethod(
            contactId = contact?.id ?: "temp_${System.currentTimeMillis()}",
            methodType = methodType,
            value = value
        )
        localMethods = localMethods + newMethod
        addedMethods.add(newMethod)
    }

    fun updateMethodLocally(oldMethod: ContactMethod, newMethod: ContactMethod) {
        localMethods = localMethods.map {
            if (it.id == oldMethod.id) newMethod else it
        }
        if (addedMethods.contains(oldMethod)) {
            val index = addedMethods.indexOf(oldMethod)
            addedMethods[index] = newMethod
        } else if (!deletedMethods.contains(oldMethod)) {
            updatedMethods.removeAll { it.id == oldMethod.id }
            updatedMethods.add(newMethod)
        }
    }

    fun deleteMethodLocally(method: ContactMethod) {
        localMethods = localMethods.filter { it.id != method.id }
        if (addedMethods.contains(method)) {
            addedMethods.remove(method)
        } else {
            deletedMethods.add(method)
            updatedMethods.removeAll { it.id == method.id }
        }
    }

    fun cancelChanges() {
        name = originalName
        description = originalDescription
        localMethods = originalMethods.toList()
        addedMethods.clear()
        updatedMethods.clear()
        deletedMethods.clear()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::cancelChanges,
        title = {
            Text(if (contact == null) "Добавить контакт" else "Редактировать")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя*") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = name.isBlank()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth()
                )

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Способы связи",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    TextButton(
                        onClick = { showAddMethodDialog = true },
                        enabled = true
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Добавить", fontSize = 12.sp)
                    }
                }

                if (localMethods.isEmpty()) {
                    Text(
                        text = "Нет способов связи",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localMethods) { method ->
                            EditableMethodItem(
                                method = method,
                                onEdit = { editingMethod = method },
                                onDelete = { deleteMethodLocally(method) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, description, addedMethods.toList(), updatedMethods.toList(), deletedMethods.toList())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = ::cancelChanges) {
                Text("Отмена")
            }
        }
    )

    if (showAddMethodDialog) {
        MethodDialog(
            method = null,
            onDismiss = { showAddMethodDialog = false },
            onSave = { methodType, value ->
                addMethodLocally(methodType, value)
                showAddMethodDialog = false
            }
        )
    }

    if (editingMethod != null) {
        val oldMethod = editingMethod!!
        MethodDialog(
            method = oldMethod,
            onDismiss = { editingMethod = null },
            onSave = { methodType, value ->
                val updatedMethod = oldMethod.copy(
                    methodType = methodType,
                    value = value
                )
                updateMethodLocally(oldMethod, updatedMethod)
                editingMethod = null
            }
        )
    }
}

@Composable
fun EditableMethodItem(
    method: ContactMethod,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (val icon = getIconForMethod(method.methodType)) {
                    is String -> {
                        Text(
                            text = icon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Icon(
                            icon as ImageVector,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = method.methodType,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = method.value,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodDialog(
    method: ContactMethod?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val defaultType = when {
        method?.methodType?.contains("Телефон") == true || method?.methodType?.contains("Phone") == true -> ContactMethodType.PHONE
        method?.methodType?.contains("Telegram") == true -> ContactMethodType.TELEGRAM
        method?.methodType?.contains("VK") == true || method?.methodType?.contains("vk") == true -> ContactMethodType.VK
        method?.methodType?.contains("Email") == true || method?.methodType?.contains("Почта") == true -> ContactMethodType.EMAIL
        method?.methodType?.contains("Иное") == true -> ContactMethodType.CUSTOM
        else -> ContactMethodType.PHONE
    }

    var selectedType by remember { mutableStateOf(defaultType) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Отдельные состояния для каждого типа связи
    var phoneValue by remember { mutableStateOf(if (defaultType == ContactMethodType.PHONE) method?.value ?: "" else "") }
    var telegramValue by remember { mutableStateOf(if (defaultType == ContactMethodType.TELEGRAM) method?.value ?: "" else "") }
    var vkValue by remember { mutableStateOf(if (defaultType == ContactMethodType.VK) method?.value ?: "" else "") }
    var emailValue by remember { mutableStateOf(if (defaultType == ContactMethodType.EMAIL) method?.value ?: "" else "") }
    var customValue by remember { mutableStateOf(if (defaultType == ContactMethodType.CUSTOM) method?.value ?: "" else "") }
    var customTypeName by remember {
        mutableStateOf(
            if (defaultType == ContactMethodType.CUSTOM && method?.methodType != "Иное") {
                method?.methodType ?: ""
            } else {
                ""
            }
        )
    }
    // Для контроля позиции курсора
    var phoneTextFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(phoneValue)) }
    var telegramTextFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(telegramValue)) }
    var vkTextFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(vkValue)) }
    var emailTextFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(emailValue)) }
    var customTextFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(customValue)) }

    val currentTextFieldValue = when (selectedType) {
        ContactMethodType.PHONE -> phoneTextFieldValue
        ContactMethodType.TELEGRAM -> telegramTextFieldValue
        ContactMethodType.VK -> vkTextFieldValue
        ContactMethodType.EMAIL -> emailTextFieldValue
        ContactMethodType.CUSTOM -> customTextFieldValue
    }

    // Функция обновления значения
    fun updateCurrentValue(newValue: String, newTextFieldValue: androidx.compose.ui.text.input.TextFieldValue) {
        when (selectedType) {
            ContactMethodType.PHONE -> {
                phoneValue = newValue
                phoneTextFieldValue = newTextFieldValue
            }
            ContactMethodType.TELEGRAM -> {
                telegramValue = newValue
                telegramTextFieldValue = newTextFieldValue
            }
            ContactMethodType.VK -> {
                vkValue = newValue
                vkTextFieldValue = newTextFieldValue
            }
            ContactMethodType.EMAIL -> {
                emailValue = newValue
                emailTextFieldValue = newTextFieldValue
            }
            ContactMethodType.CUSTOM -> {
                customValue = newValue
                customTextFieldValue = newTextFieldValue
            }
        }
    }

    // Предзаполнение для типов с @
    LaunchedEffect(selectedType) {
        when (selectedType) {
            ContactMethodType.TELEGRAM -> {
                if (telegramValue.isEmpty()) {
                    updateCurrentValue("@", androidx.compose.ui.text.input.TextFieldValue("@"))
                } else if (!telegramValue.startsWith("@")) {
                    val newVal = "@$telegramValue"
                    updateCurrentValue(newVal, androidx.compose.ui.text.input.TextFieldValue(newVal))
                }
            }
            ContactMethodType.VK -> {
                if (vkValue.isEmpty()) {
                    updateCurrentValue("@", androidx.compose.ui.text.input.TextFieldValue("@"))
                } else if (!vkValue.startsWith("@")) {
                    val newVal = "@$vkValue"
                    updateCurrentValue(newVal, androidx.compose.ui.text.input.TextFieldValue(newVal))
                }
            }
            else -> {}
        }
    }

    fun validate(): Boolean {
        errorMessage = null

        val value = when (selectedType) {
            ContactMethodType.PHONE -> phoneValue
            ContactMethodType.TELEGRAM -> telegramValue
            ContactMethodType.VK -> vkValue
            ContactMethodType.EMAIL -> emailValue
            ContactMethodType.CUSTOM -> customValue
        }

        if (value.isBlank()) {
            errorMessage = "Поле не может быть пустым"
            return false
        }

        when (selectedType) {
            ContactMethodType.PHONE -> {
                if (!validateRussianPhone(value)) {
                    errorMessage = "Введите корректный российский номер телефона (10 или 11 цифр)"
                    return false
                }
            }
            ContactMethodType.EMAIL -> {
                if (!value.contains("@")) {
                    errorMessage = "Email должен содержать символ @"
                    return false
                }
                if (selectedType.validationRegex != null && !Pattern.matches(selectedType.validationRegex, value)) {
                    errorMessage = "Введите корректный email адрес"
                    return false
                }
            }
            ContactMethodType.TELEGRAM, ContactMethodType.VK -> {
                if (!value.startsWith("@")) {
                    errorMessage = "Никнейм должен начинаться с @"
                    return false
                }
                if (value.length < 2) {
                    errorMessage = "Введите никнейм после @"
                    return false
                }
            }
            ContactMethodType.CUSTOM -> {
                val finalCustomTypeName = customTypeName.ifBlank { "Иное" }
                if (finalCustomTypeName.isBlank()) {
                    errorMessage = "Введите название способа связи"
                    return false
                }
            }
        }
        return true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать способ связи") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Тип связи", fontWeight = FontWeight.Medium)

                // Иконки типов связи
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        ContactMethodType.PHONE,
                        ContactMethodType.TELEGRAM,
                        ContactMethodType.VK,
                        ContactMethodType.EMAIL
                    ).forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedType = type
                                    errorMessage = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (val icon = type.icon) {
                                is String -> {
                                    Text(
                                        text = icon,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                else -> {
                                    Icon(
                                        icon as ImageVector,
                                        contentDescription = type.displayName,
                                        modifier = Modifier.size(36.dp),
                                        tint = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // кнопка "Иное"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedType = ContactMethodType.CUSTOM
                            errorMessage = null
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedType == ContactMethodType.CUSTOM)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (selectedType == ContactMethodType.CUSTOM)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Иное (вольный ввод)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedType == ContactMethodType.CUSTOM)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Для "Иное" показываем дополнительное поле
                if (selectedType == ContactMethodType.CUSTOM) {
                    OutlinedTextField(
                        value = customTypeName,
                        onValueChange = { customTypeName = it },
                        label = { Text("Название способа связи") },
                        placeholder = { Text("Например: Discord, Skype, Signal...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Поле ввода значения
                OutlinedTextField(
                    value = currentTextFieldValue ?: androidx.compose.ui.text.input.TextFieldValue(""),
                    onValueChange = { newValue ->
                        val newText = newValue.text
                        val cursorPos = newValue.selection.start

                        when (selectedType) {
                            ContactMethodType.PHONE -> {
                                val digits = newText.filter { it.isDigit() }.take(11)
                                val formatted = formatPhoneNumber(digits)
                                val newCursorPos = (cursorPos + (formatted.length - newText.length)).coerceIn(0, formatted.length)
                                val newTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                    text = formatted,
                                    selection = androidx.compose.ui.text.TextRange(newCursorPos)
                                )
                                updateCurrentValue(formatted, newTextFieldValue)
                            }
                            ContactMethodType.TELEGRAM, ContactMethodType.VK -> {
                                val withoutAt = newText.replace("@", "")
                                val result = if (withoutAt.isNotEmpty()) "@$withoutAt" else "@"
                                val newCursorPos = if (result != newText) {
                                    (cursorPos + 1).coerceAtMost(result.length)
                                } else {
                                    cursorPos.coerceAtMost(result.length)
                                }
                                val newTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                    text = result,
                                    selection = androidx.compose.ui.text.TextRange(newCursorPos)
                                )
                                updateCurrentValue(result, newTextFieldValue)
                            }
                            else -> {
                                val newTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                    text = newText,
                                    selection = androidx.compose.ui.text.TextRange(cursorPos.coerceAtMost(newText.length))
                                )
                                updateCurrentValue(newText, newTextFieldValue)
                            }
                        }
                    },
                    label = {
                        Text(
                            when (selectedType) {
                                ContactMethodType.PHONE -> "Телефон"
                                ContactMethodType.TELEGRAM -> "Telegram"
                                ContactMethodType.VK -> "VK"
                                ContactMethodType.EMAIL -> "Email"
                                ContactMethodType.CUSTOM -> if (customTypeName != "Иное") customTypeName else "Значение"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                ContactMethodType.PHONE -> "+7 (XXX) XXX-XX-XX"
                                ContactMethodType.TELEGRAM -> "@username"
                                ContactMethodType.VK -> "@id123456789"
                                ContactMethodType.EMAIL -> "example@mail.com"
                                ContactMethodType.CUSTOM -> "Введите значение..."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = Color.Red) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (selectedType) {
                            ContactMethodType.PHONE -> KeyboardType.Phone
                            ContactMethodType.EMAIL -> KeyboardType.Email
                            else -> KeyboardType.Text
                        }
                    ),
                    singleLine = selectedType != ContactMethodType.CUSTOM
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validate()) {
                        val typeName = if (selectedType == ContactMethodType.CUSTOM) customTypeName.ifBlank { "Иное" } else selectedType.displayName
                        val value = when (selectedType) {
                            ContactMethodType.PHONE -> phoneValue
                            ContactMethodType.TELEGRAM -> telegramValue
                            ContactMethodType.VK -> vkValue
                            ContactMethodType.EMAIL -> emailValue
                            ContactMethodType.CUSTOM -> customValue
                        }
                        onSave(typeName, value)
                        onDismiss()
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

fun formatPhoneNumber(digits: String): String {
    if (digits.isEmpty()) return ""

    return when {
        digits.startsWith("7") -> {
            when (digits.length) {
                1 -> "+7"
                2 -> "+7 (${digits[1]}"
                3 -> "+7 (${digits.substring(1, 3)}"
                4 -> "+7 (${digits.substring(1, 4)}"
                5 -> "+7 (${digits.substring(1, 4)}) ${digits[4]}"
                6 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 6)}"
                7 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}"
                8 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits[7]}"
                9 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}"
                10 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 10)}"
                11 -> "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 11)}"
                else -> "+7$digits"
            }
        }
        digits.startsWith("8") -> {
            val withSeven = "7" + digits.substring(1)
            formatPhoneNumber(withSeven)
        }
        digits.length == 10 -> {
            formatPhoneNumber("7$digits")
        }
        else -> digits
    }
}
