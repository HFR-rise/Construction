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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.viewmodels.ProjectDetailViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    navController: NavController,
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsState()
    val filteredMaterials by viewModel.filteredMaterials.collectAsState()
    val filteredWorkItems by viewModel.filteredWorkItems.collectAsState()
    val materialSearchQuery by viewModel.materialSearchQuery.collectAsState()
    val workSearchQuery by viewModel.workSearchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    val showMaterialDialog by viewModel.showMaterialDialog.collectAsState()
    val showWorkDialog by viewModel.showWorkDialog.collectAsState()
    val editingMaterial by viewModel.editingMaterial.collectAsState()
    val editingWorkItem by viewModel.editingWorkItem.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Детали проекта") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showDeleteConfirmation() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить смету")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.showMaterialDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить материал")
                }
                FloatingActionButton(
                    onClick = { viewModel.showWorkDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Добавить работу")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Бюджет проекта", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Итого: ${String.format(Locale.US, "%.2f", project?.totalBudget ?: 0.0)} ₽",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Материалы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                OutlinedTextField(
                    value = materialSearchQuery,
                    onValueChange = { viewModel.updateMaterialSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск материалов...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            if (filteredMaterials.isEmpty() && materialSearchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Материалы не найдены",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            items(filteredMaterials) { material ->
                MaterialItem(
                    material = material,
                    onEdit = { viewModel.startEditMaterial(material) },
                    onDelete = { viewModel.deleteMaterial(material) }
                )
            }

            item {
                Text(
                    text = "Работы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = workSearchQuery,
                    onValueChange = { viewModel.updateWorkSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск работ...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            if (filteredWorkItems.isEmpty() && workSearchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Работы не найдены",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            items(filteredWorkItems) { work ->
                WorkItemRow(
                    work = work,
                    onEdit = { viewModel.startEditWorkItem(work) },
                    onDelete = { viewModel.deleteWorkItem(work) }
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            title = { Text("Удалить смету") },
            text = { Text("Вы уверены, что хотите удалить эту смету? Все материалы и работы будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject()
                        navController.navigateUp()
                    }
                ) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmation() }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showMaterialDialog || editingMaterial != null) {
        val material = editingMaterial
        MaterialDialog(
            material = material,
            onDismiss = {
                viewModel.hideMaterialDialog()
                viewModel.clearEditMaterial()
            },
            onSave = { name, quantity, unit, price ->
                if (material == null) {
                    viewModel.addMaterial(name, quantity, unit, price)
                } else {
                    viewModel.updateMaterial(material.copy(
                        name = name,
                        quantity = quantity,
                        unit = unit,
                        unitPrice = price
                    ))
                }
            }
        )
    }

    if (showWorkDialog || editingWorkItem != null) {
        val work = editingWorkItem
        WorkDialog(
            workItem = work,
            onDismiss = {
                viewModel.hideWorkDialog()
                viewModel.clearEditWorkItem()
            },
            onSave = { name, hours, rate, materialCost ->
                if (work == null) {
                    viewModel.addWorkItem(name, hours, rate, materialCost)
                } else {
                    viewModel.updateWorkItem(work.copy(
                        name = name,
                        laborHours = hours,
                        hourlyRate = rate,
                        materialCost = materialCost
                    ))
                }
            }
        )
    }
}

@Composable
fun MaterialItem(
    material: Material,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
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
                Text(material.name, fontWeight = FontWeight.Medium)
                Text(
                    text = "${material.quantity} ${material.unit} × ${String.format(Locale.US, "%.2f", material.unitPrice)} ₽ = ${String.format(Locale.US, "%.2f", material.totalPrice)} ₽",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun WorkItemRow(
    work: WorkItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
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
                Text(work.name, fontWeight = FontWeight.Medium)
                Text(
                    text = "Труд: ${work.laborHours} ч × ${String.format(Locale.US, "%.2f", work.hourlyRate)} ₽ = ${String.format(Locale.US, "%.2f", work.laborCost)} ₽",
                    fontSize = 12.sp
                )
                Text(
                    text = "Материалы: ${String.format(Locale.US, "%.2f", work.materialCost)} ₽",
                    fontSize = 12.sp
                )
                Text(
                    text = "Итого: ${String.format(Locale.US, "%.2f", work.totalCost)} ₽",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDialog(
    material: Material?,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, Double) -> Unit
) {
    var name by remember { mutableStateOf(material?.name ?: "") }
    var quantity by remember { mutableStateOf(material?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(material?.unit ?: "шт") }
    var price by remember { mutableStateOf(material?.unitPrice?.toString() ?: "") }

    val isEditMode = material != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Редактировать материал" else "Добавить материал") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название*") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = name.isBlank()
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Количество*") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Единица измерения") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("шт") }
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Цена за ед.*") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = quantity.toDoubleOrNull() ?: 0.0
                    val p = price.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && q > 0 && p > 0) {
                        onSave(name, q, unit.ifBlank { "шт" }, p)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDialog(
    workItem: WorkItem?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf(workItem?.name ?: "") }
    var hours by remember { mutableStateOf(workItem?.laborHours?.toString() ?: "") }
    var rate by remember { mutableStateOf(workItem?.hourlyRate?.toString() ?: "") }
    var materialCost by remember { mutableStateOf(workItem?.materialCost?.toString() ?: "") }

    val isEditMode = workItem != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Редактировать работу" else "Добавить работу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название работы*") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = name.isBlank()
                )
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Часы работы") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Ставка в час (₽)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = materialCost,
                    onValueChange = { materialCost = it },
                    label = { Text("Стоимость материалов (₽)") },
                    modifier = Modifier.fillMaxWidth()
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
                        onSave(name, h, r, m)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
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