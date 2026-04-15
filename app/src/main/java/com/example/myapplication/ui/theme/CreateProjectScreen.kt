package com.example.myapplication.ui.theme

import android.util.Log
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.viewmodels.ProjectsViewModel
import com.example.myapplication.viewmodels.ContactsViewModel
import java.util.Locale
import kotlinx.coroutines.flow.first

enum class ProjectScreenMode {
    CREATE,
    EDIT,
    VIEW
}

@Composable
fun ContactSelectorRowSimple(
    label: String,
    selectedContact: Contact?,
    onSelect: (Contact) -> Unit,
    onClear: () -> Unit,
    onShowSelector: () -> Unit
) {
    var showContactInfo by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedContact != null) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showContactInfo = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedContact.name,
                        fontWeight = FontWeight.Medium
                    )
                    if (selectedContact.description.isNotBlank()) {
                        Text(
                            text = selectedContact.description,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Очистить",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Text(
                text = "Не выбран",
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = onShowSelector,
            modifier = Modifier.width(120.dp)
        ) {
            Text(if (selectedContact != null) "Изменить" else "Выбрать")
        }
    }

    if (showContactInfo && selectedContact != null) {
        val contactsViewModel: ContactsViewModel = hiltViewModel()
        ContactDetailsDialog(
            contact = selectedContact,
            methods = contactsViewModel.getMethodsForContact(selectedContact.id)
                .collectAsState(initial = emptyList()).value,
            onDismiss = { showContactInfo = false }
        )
    }
}

@Composable
fun MaterialItemPreview(
    material: Material,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(material.name, fontWeight = FontWeight.Medium)
                Text(
                    text = "${material.quantity} ${material.unit} × ${String.format("%.2f", material.unitPrice)} ₽ = ${String.format("%.2f", material.totalPrice)} ₽",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun WorkItemPreview(
    workItem: WorkItem,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workItem.name, fontWeight = FontWeight.Medium)
                Text(
                    text = "Труд: ${workItem.laborHours} ч × ${String.format("%.2f", workItem.hourlyRate)} ₽ = ${String.format("%.2f", workItem.laborCost)} ₽",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Материалы: ${String.format("%.2f", workItem.materialCost)} ₽",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Итого: ${String.format("%.2f", workItem.totalCost)} ₽",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaterialDialogSimple(
    onDismiss: () -> Unit,
    onAdd: (String, Double, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("шт") }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить материал") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Количество*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Единица измерения") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("шт") }
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Цена за ед.*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = quantity.toDoubleOrNull() ?: 0.0
                    val p = price.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && q > 0 && p > 0) {
                        onAdd(name, q, unit.ifBlank { "шт" }, p)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkDialogSimple(
    onDismiss: () -> Unit,
    onAdd: (String, Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var materialCost by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить работу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название работы*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Часы работы") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Ставка в час") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = materialCost,
                    onValueChange = { materialCost = it },
                    label = { Text("Стоимость материалов") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hours.toDoubleOrNull() ?: 0.0
                    val r = rate.toDoubleOrNull() ?: 0.0
                    val m = materialCost.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        onAdd(name, h, r, m)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    navController: NavController,
    mode: ProjectScreenMode,
    objectId: String? = null,
    projectId: String? = null,
    viewModel: ProjectsViewModel = hiltViewModel()
) {

    val contactsViewModel: ContactsViewModel = hiltViewModel()

    var currentStep by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(mode != ProjectScreenMode.CREATE && projectId != null) }

    // Шаг 1: Основная информация
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var selectedCustomer by remember { mutableStateOf<Contact?>(null) }
    var selectedForeman by remember { mutableStateOf<Contact?>(null) }
    var selectedManager by remember { mutableStateOf<Contact?>(null) }

    var includeForeman by remember { mutableStateOf(false) }
    var includeManager by remember { mutableStateOf(false) }

    var showCustomerSelector by remember { mutableStateOf(false) }
    var showForemanSelector by remember { mutableStateOf(false) }
    var showManagerSelector by remember { mutableStateOf(false) }

    // Шаг 2: Калькулятор
    var materials by remember { mutableStateOf<List<Material>>(emptyList()) }
    var workItems by remember { mutableStateOf<List<WorkItem>>(emptyList()) }
    var showAddMaterialDialog by remember { mutableStateOf(false) }
    var showAddWorkDialog by remember { mutableStateOf(false) }

    val contacts by viewModel.contacts.collectAsState()

    val isReadOnly = mode == ProjectScreenMode.VIEW

    var showCustomerInfo by remember { mutableStateOf(false) }
    var showForemanInfo by remember { mutableStateOf(false) }
    var showManagerInfo by remember { mutableStateOf(false) }


    // Загружаем данные при редактировании или просмотре
    LaunchedEffect(projectId, mode) {
        if ((mode == ProjectScreenMode.EDIT || mode == ProjectScreenMode.VIEW) && projectId != null) {
            // Ждём пока контакты загрузятся
            if (viewModel.hasAnyContacts()) {
                // Контакты есть - ждём их загрузки
                Log.d("CreateProjectScreen", "Waiting for contacts to load...")
                viewModel.contacts.first { it.isNotEmpty() }
                Log.d("CreateProjectScreen", "Contacts loaded: ${viewModel.contacts.value.size}")
            } else {
                // Контактов нет - не ждём
                Log.d("CreateProjectScreen", "No contacts in database, skipping wait")
            }

            isLoading = true

            Log.d("CreateProjectScreen", "=== LOAD DATA ===")
            Log.d("CreateProjectScreen", "mode: $mode, projectId: $projectId")
            Log.d("CreateProjectScreen", "Contacts loaded: ${contacts.size}")

            val project = viewModel.getProjectById(projectId)
            Log.d("CreateProjectScreen", "Project received: ${project?.name}, id: ${project?.id}")

            if (project != null) {
                name = project.name
                description = project.description
                includeForeman = project.includeForeman
                includeManager = project.includeManager

                if (viewModel.contacts.value.isNotEmpty()){

                    if (project.customerContactId != null) {
                        selectedCustomer = contacts.find { it.id == project.customerContactId }
                        Log.d("CreateProjectScreen", "Customer found: ${selectedCustomer?.name}")
                    }
                    if (project.foremanContactId != null) {
                        selectedForeman = contacts.find { it.id == project.foremanContactId }
                        Log.d("CreateProjectScreen", "Foreman found: ${selectedForeman?.name}")
                    }
                    if (project.managerContactId != null) {
                        selectedManager = contacts.find { it.id == project.managerContactId }
                        Log.d("CreateProjectScreen", "Manager found: ${selectedManager?.name}")
                    }

                    materials = viewModel.getMaterialsForProject(projectId)
                    workItems = viewModel.getWorkItemsForProject(projectId)
                }
            }
            isLoading = false
        }
    }

    val totalMaterialCost = materials.sumOf { it.quantity * it.unitPrice }
    val totalWorkCost = workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost }
    val grandTotal = totalMaterialCost + totalWorkCost

    val title = when (mode) {
        ProjectScreenMode.CREATE -> if (currentStep == 1) "Создать смету (1/2)" else "Создать смету (2/2)"
        ProjectScreenMode.EDIT -> if (currentStep == 1) "Редактировать смету (1/2)" else "Редактировать смету (2/2)"
        ProjectScreenMode.VIEW -> if (currentStep == 1) "Просмотр сметы (1/2)" else "Просмотр сметы (2/2)"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    if (currentStep == 2) {
                        IconButton(onClick = { currentStep = 1 }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    } else {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (!isReadOnly && currentStep == 2) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showAddMaterialDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = "Добавить материал")
                    }
                    FloatingActionButton(
                        onClick = { showAddWorkDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Добавить работу")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GradientDivider()
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        if (currentStep == 1) {
                            // Шаг 1: Основная информация
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { if (!isReadOnly) name = it },
                                        label = { Text("Название сметы*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !isReadOnly
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = description,
                                        onValueChange = { if (!isReadOnly) description = it },
                                        label = { Text("Описание") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        enabled = !isReadOnly
                                    )
                                }

                                item { Divider() }

                                item { Text("Данные заказчика", fontWeight = FontWeight.Medium) }
                                item {
                                    if (isReadOnly) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = selectedCustomer != null) {
                                                    showCustomerInfo = true
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (selectedCustomer != null) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = selectedCustomer!!.name,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (selectedCustomer!!.description.isNotBlank()) {
                                                        Text(
                                                            text = selectedCustomer!!.description,
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text("Не выбран", color = Color.Gray)
                                            }
                                        }

                                        if (showCustomerInfo && selectedCustomer != null) {
                                            ContactDetailsDialog(
                                                contact = selectedCustomer!!,
                                                methods = contactsViewModel.getMethodsForContact(
                                                    selectedCustomer!!.id
                                                )
                                                    .collectAsState(initial = emptyList()).value,
                                                onDismiss = { showCustomerInfo = false }
                                            )
                                        }
                                    } else {
                                        ContactSelectorRowSimple(
                                            label = "Заказчик",
                                            selectedContact = selectedCustomer,
                                            onSelect = { selectedCustomer = it },
                                            onClear = { selectedCustomer = null },
                                            onShowSelector = { showCustomerSelector = true }
                                        )
                                    }
                                }

                                item { Divider() }

                                item { Text("Данные прораба", fontWeight = FontWeight.Medium) }
                                item {
                                    if (isReadOnly) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = selectedForeman != null) {
                                                    showForemanInfo = true
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (selectedForeman != null) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = selectedForeman!!.name,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (selectedForeman!!.description.isNotBlank()) {
                                                        Text(
                                                            text = selectedForeman!!.description,
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text("Не выбран", color = Color.Gray)
                                            }
                                        }

                                        if (showForemanInfo && selectedForeman != null) {
                                            ContactDetailsDialog(
                                                contact = selectedForeman!!,
                                                methods = contactsViewModel.getMethodsForContact(
                                                    selectedForeman!!.id
                                                )
                                                    .collectAsState(initial = emptyList()).value,
                                                onDismiss = { showForemanInfo = false }
                                            )
                                        }
                                    } else {
                                        ContactSelectorRowSimple(
                                            label = "Прораб",
                                            selectedContact = selectedForeman,
                                            onSelect = { selectedForeman = it },
                                            onClear = { selectedForeman = null },
                                            onShowSelector = { showForemanSelector = true }
                                        )
                                    }
                                }
                                item {
                                    if (isReadOnly) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = includeForeman,
                                                onCheckedChange = {},
                                                enabled = false
                                            )
                                            Text("Включить в проект", color = Color.Gray)
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = includeForeman,
                                                onCheckedChange = { includeForeman = it },
                                                enabled = selectedForeman != null
                                            )
                                            Text(
                                                "Включить в проект",
                                                color = if (selectedForeman != null) Color.Unspecified else Color.Gray
                                            )
                                        }
                                    }
                                }

                                item { Divider() }

                                item { Text("Данные менеджера", fontWeight = FontWeight.Medium) }
                                item {
                                    if (isReadOnly) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = selectedManager != null) {
                                                    showManagerInfo = true
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (selectedManager != null) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = selectedManager!!.name,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (selectedManager!!.description.isNotBlank()) {
                                                        Text(
                                                            text = selectedManager!!.description,
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text("Не выбран", color = Color.Gray)
                                            }
                                        }

                                        if (showManagerInfo && selectedManager != null) {
                                            ContactDetailsDialog(
                                                contact = selectedManager!!,
                                                methods = contactsViewModel.getMethodsForContact(
                                                    selectedManager!!.id
                                                )
                                                    .collectAsState(initial = emptyList()).value,
                                                onDismiss = { showManagerInfo = false }
                                            )
                                        }
                                    } else {
                                        ContactSelectorRowSimple(
                                            label = "Менеджер",
                                            selectedContact = selectedManager,
                                            onSelect = { selectedManager = it },
                                            onClear = { selectedManager = null },
                                            onShowSelector = { showManagerSelector = true }
                                        )
                                    }
                                }
                                item {
                                    if (isReadOnly) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = includeManager,
                                                onCheckedChange = {},
                                                enabled = false
                                            )
                                            Text("Включить в проект", color = Color.Gray)
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = includeManager,
                                                onCheckedChange = { includeManager = it },
                                                enabled = selectedManager != null
                                            )
                                            Text(
                                                "Включить в проект",
                                                color = if (selectedManager != null) Color.Unspecified else Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Шаг 2: Калькулятор
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Text(
                                        "Материалы",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }

                                if (materials.isEmpty()) {
                                    item { Text("Нет добавленных материалов", color = Color.Gray) }
                                } else {
                                    items(materials) { material ->
                                        if (isReadOnly) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                elevation = CardDefaults.cardElevation(
                                                    defaultElevation = 2.dp
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            material.name,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "${material.quantity} ${material.unit} × ${
                                                                String.format(
                                                                    "%.2f",
                                                                    material.unitPrice
                                                                )
                                                            } ₽ = ${
                                                                String.format(
                                                                    "%.2f",
                                                                    material.totalPrice
                                                                )
                                                            } ₽",
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            MaterialItemPreview(
                                                material = material,
                                                onDelete = {
                                                    materials =
                                                        materials.filter { it.id != material.id }
                                                }
                                            )
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        "Работы",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }

                                if (workItems.isEmpty()) {
                                    item { Text("Нет добавленных работ", color = Color.Gray) }
                                } else {
                                    items(workItems) { work ->
                                        if (isReadOnly) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                elevation = CardDefaults.cardElevation(
                                                    defaultElevation = 2.dp
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            work.name,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "Труд: ${work.laborHours} ч × ${
                                                                String.format(
                                                                    "%.2f",
                                                                    work.hourlyRate
                                                                )
                                                            } ₽ = ${
                                                                String.format(
                                                                    "%.2f",
                                                                    work.laborCost
                                                                )
                                                            } ₽",
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                        Text(
                                                            text = "Материалы: ${
                                                                String.format(
                                                                    "%.2f",
                                                                    work.materialCost
                                                                )
                                                            } ₽",
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                        Text(
                                                            text = "Итого: ${
                                                                String.format(
                                                                    "%.2f",
                                                                    work.totalCost
                                                                )
                                                            } ₽",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            WorkItemPreview(
                                                workItem = work,
                                                onDelete = {
                                                    workItems =
                                                        workItems.filter { it.id != work.id }
                                                }
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Расходы", fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${
                                                    String.format(
                                                        Locale.US,
                                                        "%.2f",
                                                        grandTotal
                                                    )
                                                } ₽",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 8.dp,
                        tonalElevation = 3.dp
                    ) {
                        if (currentStep == 1) {
                            Button(
                                onClick = { currentStep = 2 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                enabled = name.isNotBlank() || isReadOnly
                            ) {
                                Text("Далее")
                            }
                        } else {
                            Button(
                                onClick = {
                                    when (mode) {
                                        ProjectScreenMode.CREATE -> {
                                            if (name.isNotBlank()) {
                                                viewModel.createProjectWithMaterialsAndWorks(
                                                    name = name,
                                                    description = description,
                                                    objectId = objectId,
                                                    materials = materials,
                                                    workItems = workItems,
                                                    customerContactId = selectedCustomer?.id,
                                                    foremanContactId = selectedForeman?.id,
                                                    managerContactId = selectedManager?.id,
                                                    includeForeman = includeForeman,
                                                    includeManager = includeManager
                                                )
                                                viewModel.loadProjects()
                                                navController.navigateUp()
                                            }
                                        }

                                        ProjectScreenMode.EDIT -> {
                                            if (projectId != null && name.isNotBlank()) {
                                                viewModel.updateProject(
                                                    projectId = projectId,
                                                    name = name,
                                                    description = description,
                                                    materials = materials,
                                                    workItems = workItems,
                                                    customerContactId = selectedCustomer?.id,
                                                    foremanContactId = selectedForeman?.id,
                                                    managerContactId = selectedManager?.id,
                                                    includeForeman = includeForeman,
                                                    includeManager = includeManager
                                                )
                                                navController.navigateUp()
                                            }
                                        }

                                        ProjectScreenMode.VIEW -> {
                                            navController.navigateUp()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                enabled = if (mode == ProjectScreenMode.VIEW) true else name.isNotBlank()
                            ) {
                                Text(
                                    when (mode) {
                                        ProjectScreenMode.CREATE -> "Создать смету"
                                        ProjectScreenMode.EDIT -> "Сохранить изменения"
                                        ProjectScreenMode.VIEW -> "Закрыть"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (!isReadOnly && showCustomerSelector) {
        ContactSelectorDialog(
            title = "Выберите заказчика",
            onDismiss = { showCustomerSelector = false },
            onSelect = { contact ->
                selectedCustomer = contact
                showCustomerSelector = false
            }
        )
    }

    if (!isReadOnly && showForemanSelector) {
        ContactSelectorDialog(
            title = "Выберите прораба",
            onDismiss = { showForemanSelector = false },
            onSelect = { contact ->
                selectedForeman = contact
                showForemanSelector = false
            }
        )
    }

    if (!isReadOnly && showManagerSelector) {
        ContactSelectorDialog(
            title = "Выберите менеджера",
            onDismiss = { showManagerSelector = false },
            onSelect = { contact ->
                selectedManager = contact
                showManagerSelector = false
            }
        )
    }

    if (!isReadOnly && showAddMaterialDialog) {
        AddMaterialDialogSimple(
            onDismiss = { showAddMaterialDialog = false },
            onAdd = { name, quantity, unit, price ->
                val newMaterial = Material(
                    projectId = projectId ?: "",
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    unitPrice = price
                )
                materials = materials + newMaterial
                showAddMaterialDialog = false
            }
        )
    }

    if (!isReadOnly && showAddWorkDialog) {
        AddWorkDialogSimple(
            onDismiss = { showAddWorkDialog = false },
            onAdd = { name, hours, rate, materialCost ->
                val newWork = WorkItem(
                    projectId = projectId ?: "",
                    name = name,
                    laborHours = hours,
                    hourlyRate = rate,
                    materialCost = materialCost
                )
                workItems = workItems + newWork
                showAddWorkDialog = false
            }
        )
    }
}
