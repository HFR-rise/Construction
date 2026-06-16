package com.example.myapplication.ui.theme

import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.viewmodels.ObjectFilterType
import com.example.myapplication.viewmodels.ObjectsViewModel
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.util.Log
import java.util.Locale
import androidx.compose.foundation.shape.RoundedCornerShape

// Accompanist SwipeRefresh импорты
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

// Импорты для расшаривания
import com.example.myapplication.viewmodels.ShareViewModel
import com.example.myapplication.viewmodels.SharingState
import com.example.myapplication.viewmodels.ContactsViewModel
import com.example.myapplication.ui.components.ShareConfirmationDialog
import com.example.myapplication.ui.components.NoPhoneErrorDialog
import com.example.myapplication.ui.components.UserNotFoundErrorDialog
import com.example.myapplication.ui.components.ShareContactSelectorDialog
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectsScreen(
    navController: NavController,
    parentObjectId: String? = null,
    selectionMode: Boolean = false,
    onObjectSelected: ((String, String) -> Unit)? = null,
    onObjectOpen: ((String, String) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    navigationStack: List<Pair<String, String>> = emptyList(),
    viewModel: ObjectsViewModel = hiltViewModel()
) {
    val filteredObjects by viewModel.filteredObjects.collectAsState()
    val projectsInObject by viewModel.projectsInObject.collectAsState()
    val rootLevelProjects by viewModel.rootLevelProjects.collectAsState()
    val showRootProjects by viewModel.showRootProjects.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val showCreateTypeDialog by viewModel.showCreateTypeDialog.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentObjectName by viewModel.currentObjectName.collectAsState()

    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    val objectToDelete by viewModel.objectToDelete.collectAsState()
    val showInfoDialog by viewModel.showInfoDialog.collectAsState()
    val infoObject by viewModel.infoObject.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val editingObject by viewModel.editingObject.collectAsState()
    val showDeleteProjectConfirmation by viewModel.showDeleteProjectConfirmation.collectAsState()
    val projectToDelete by viewModel.projectToDelete.collectAsState()

    // ===== НОВЫЕ СОСТОЯНИЯ ДЛЯ РАСШАРИВАНИЯ =====
    val shareViewModel: ShareViewModel = hiltViewModel()
    val sharingState by shareViewModel.sharingState.collectAsState()
    val shareErrorMessage by shareViewModel.errorMessage.collectAsState()
    val contactsViewModel: ContactsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Состояния для сворачивания секций
    var showObjectsSection by remember { mutableStateOf(true) }

    // Состояния для расшаривания
    var showShareContactSelector by remember { mutableStateOf(false) }
    var selectedProjectForShare by remember { mutableStateOf<Project?>(null) }
    var selectedContactForShare by remember { mutableStateOf<Contact?>(null) }
    var showShareConfirmation by remember { mutableStateOf(false) }
    var showNoPhoneError by remember { mutableStateOf(false) }
    var showUserNotFoundError by remember { mutableStateOf(false) }
    var contactMethodsForShare by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }

    // Состояние для SwipeRefresh
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    // Обновляем parentId в ViewModel при изменении
    LaunchedEffect(parentObjectId) {
        Log.d("ObjectsScreen", "parentObjectId changed to: $parentObjectId")
        viewModel.updateParentId(parentObjectId)
        viewModel.refreshProjects()
    }

    // Обновляем данные при возврате на экран
    LaunchedEffect(Unit) {
        viewModel.loadDataFromLocalOnly()
    }

    // Загружаем методы для выбранного контакта
    LaunchedEffect(selectedContactForShare) {
        if (selectedContactForShare != null) {
            contactsViewModel.getContactMethods(selectedContactForShare!!.id).collect { methods ->
                contactMethodsForShare = methods
            }
        }
    }

    // Обработка результата расшаривания
    LaunchedEffect(sharingState) {
        when (sharingState) {
            is SharingState.Success -> {
                // Успешно поделились
                showShareConfirmation = false
                showShareContactSelector = false
                selectedProjectForShare = null
                selectedContactForShare = null

                Toast.makeText(
                    context,
                    "Смета отправлена!",
                    Toast.LENGTH_SHORT
                ).show()

                shareViewModel.resetState()
            }
            is SharingState.Error -> {
                val error = (sharingState as SharingState.Error).message
                when {
                    error.contains("не найден") -> {
                        showUserNotFoundError = true
                    }
                    error.contains("нет номера") -> {
                        showNoPhoneError = true
                    }
                    else -> {
                        Toast.makeText(
                            context,
                            error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                showShareConfirmation = false
                shareViewModel.resetState()
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSearch()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = {
                        viewModel.showCreateTypeDialog()
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Создать")
                }
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
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    when (currentFilter) {
                                        ObjectFilterType.BY_NAME -> Icons.Default.Folder
                                        ObjectFilterType.BY_DESCRIPTION -> Icons.Default.Description
                                        ObjectFilterType.BY_ADDRESS -> Icons.Default.LocationOn
                                    },
                                    contentDescription = "Фильтр",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        when (currentFilter) {
                                            ObjectFilterType.BY_NAME -> "Поиск по названию..."
                                            ObjectFilterType.BY_DESCRIPTION -> "Поиск по описанию..."
                                            ObjectFilterType.BY_ADDRESS -> "Поиск по адресу..."
                                        }
                                    )
                                },
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
                    } else if (selectionMode) {
                        val title = if (parentObjectId != null) {
                            val currentObject = filteredObjects.find { it.id == parentObjectId }
                            currentObject?.name ?: "Выберите объект для перемещения"
                        } else {
                            "Выберите объект для перемещения"
                        }
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (parentObjectId != null) currentObjectName ?: "Объекты" else "Объекты",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectionMode && parentObjectId != null) {
                        IconButton(onClick = {
                            viewModel.updateParentId(null)
                            onNavigateBack?.invoke()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    } else if (selectionMode) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                        }
                    } else if (parentObjectId != null && !isSearchActive) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (!isSearchActive && !selectionMode) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refreshAllData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                GradientDivider()

                if (isLoading && !isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // СЕКЦИЯ 1: ОБЪЕКТЫ
                        if (filteredObjects.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showObjectsSection = !showObjectsSection }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🏢 Объекты (${filteredObjects.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        if (showObjectsSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showObjectsSection) "Свернуть" else "Развернуть",
                                        tint = Color.Gray
                                    )
                                }
                            }

                            if (showObjectsSection) {
                                items(filteredObjects) { obj ->
                                    if (selectionMode) {
                                        SelectableObjectCard(
                                            obj = obj,
                                            onSelect = { onObjectSelected?.invoke(obj.id, obj.name) },
                                            onOpen = {
                                                viewModel.updateParentId(obj.id)
                                                onObjectOpen?.invoke(obj.id, obj.name)
                                                viewModel.refreshProjects()
                                            },
                                            onInfo = { viewModel.showInfoDialog(obj) }
                                        )
                                    } else {
                                        ObjectCard(
                                            obj = obj,
                                            onClick = { navController.navigate("objects/${obj.id}") },
                                            onEdit = { viewModel.startEditing(obj) },
                                            onDelete = { viewModel.showDeleteConfirmation(obj) },
                                            onInfo = { viewModel.showInfoDialog(obj) }
                                        )
                                    }
                                }
                            }
                        }

                        // РАЗДЕЛИТЕЛЬ
                        if (filteredObjects.isNotEmpty() && (projectsInObject.isNotEmpty() || rootLevelProjects.isNotEmpty())) {
                            item {
                                Divider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    thickness = 1.dp
                                )
                            }
                        }

                        // СЕКЦИЯ 2: СМЕТЫ ВНУТРИ ОБЪЕКТА
                        if (parentObjectId != null && projectsInObject.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📋 Сметы (${projectsInObject.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFFFF9800),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            items(projectsInObject) { project ->
                                if (selectionMode) {
                                    MoveProjectCardWithInfo(
                                        project = project,
                                        onInfoClick = { navController.navigate("view_project/${project.id}") }
                                    )
                                } else {
                                    // ===== ОБНОВЛЁННЫЙ ProjectCard с onShare =====
                                    ProjectCard(
                                        project = project,
                                        onClick = { navController.navigate("view_project/${project.id}") },
                                        onShare = {
                                            selectedProjectForShare = project
                                            showShareContactSelector = true
                                        },
                                        onEdit = { navController.navigate("edit_project/${project.id}") },
                                        onMove = { navController.navigate("move_project/${project.id}/${parentObjectId}") },
                                        onDelete = { viewModel.showDeleteProjectConfirmation(project) }
                                    )
                                }
                            }
                        }

                        // СЕКЦИЯ 3: СМЕТЫ НА КОРНЕВОМ УРОВНЕ
                        if (!selectionMode && parentObjectId == null && rootLevelProjects.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleRootProjectsSection() }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📋 Сметы (${rootLevelProjects.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color(0xFFFF9800)
                                    )
                                    Icon(
                                        if (showRootProjects) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showRootProjects) "Свернуть" else "Развернуть",
                                        tint = Color.Gray
                                    )
                                }
                            }

                            if (showRootProjects) {
                                items(rootLevelProjects) { project ->
                                    ProjectCard(
                                        project = project,
                                        onClick = { navController.navigate("view_project/${project.id}") },
                                        onShare = {
                                            // Открываем диалог выбора контакта для расшаривания
                                            selectedProjectForShare = project
                                            showShareContactSelector = true
                                        },
                                        onEdit = { navController.navigate("edit_project/${project.id}") },
                                        onMove = { navController.navigate("move_project/${project.id}/none") },
                                        onDelete = { viewModel.showDeleteProjectConfirmation(project) }
                                    )
                                }
                            }
                        }

                        // ПУСТОЕ СОСТОЯНИЕ
                        if (!selectionMode &&
                            filteredObjects.isEmpty() &&
                            projectsInObject.isEmpty() &&
                            rootLevelProjects.isEmpty() &&
                            searchQuery.isBlank()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Нет объектов и смет",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "чтобы создать объект или смету",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "нажмите +",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }

                        // СОСТОЯНИЕ ПОИСКА
                        if (searchQuery.isNotBlank() &&
                            filteredObjects.isEmpty() &&
                            projectsInObject.isEmpty() &&
                            rootLevelProjects.isEmpty()) {
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
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Ничего не найдено",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "По запросу \"$searchQuery\"",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // ===== КНОПКА "НЕ ДОБАВЛЯТЬ В ОБЪЕКТ" =====
                        if (selectionMode && parentObjectId == null) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        onObjectSelected?.invoke("root", "Главный экран")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF9800),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Home,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "🏠 Не добавлять в объект",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Смета будет отображаться на главном экране",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showShareContactSelector && selectedProjectForShare != null) {
        ShareContactSelectorDialog(
            onDismiss = {
                showShareContactSelector = false
                selectedProjectForShare = null
            },
            onContactSelected = { contact ->
                selectedContactForShare = contact
                showShareContactSelector = false
                showShareConfirmation = true
            },
            onNoPhoneError = { contact ->
                selectedContactForShare = contact
                showShareContactSelector = false
                showNoPhoneError = true
            }
        )
    }
    // ===== ДИАЛОГ ПОДТВЕРЖДЕНИЯ РАСШАРИВАНИЯ =====
    if (showShareConfirmation && selectedProjectForShare != null && selectedContactForShare != null) {
        ShareConfirmationDialog(
            contact = selectedContactForShare!!,
            projectName = selectedProjectForShare!!.name,
            onDismiss = {
                showShareConfirmation = false
                selectedContactForShare = null
                showShareContactSelector = true
            },
            onConfirm = {
                shareViewModel.shareProjectWithContact(
                    projectId = selectedProjectForShare!!.id,
                    projectName = selectedProjectForShare!!.name,
                    contact = selectedContactForShare!!,
                    contactMethods = contactMethodsForShare
                )
            }
        )
    }

    // ===== ОШИБКА "НЕТ ТЕЛЕФОНА" =====
    if (showNoPhoneError) {
        NoPhoneErrorDialog(
            contactName = selectedContactForShare?.name ?: "контакта",
            onDismiss = {
                showNoPhoneError = false
                selectedContactForShare = null
                showShareContactSelector = true
            }
        )
    }

    // ===== ОШИБКА "ПОЛЬЗОВАТЕЛЬ НЕ НАЙДЕН" =====
    if (showUserNotFoundError) {
        val phoneNumber = contactMethodsForShare
            .find { it.methodType.contains("телефон", ignoreCase = true) }
            ?.value ?: "неизвестный номер"

        UserNotFoundErrorDialog(
            phoneNumber = phoneNumber,
            onDismiss = {
                showUserNotFoundError = false
                selectedContactForShare = null
                showShareContactSelector = true
            }
        )
    }

    // ===== ДИАЛОГ ФИЛЬТРА =====
    if (showFilterMenu) {
        AlertDialog(
            onDismissRequest = { showFilterMenu = false },
            title = { Text("Выберите тип поиска") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterOptionObj(
                        title = "По названию",
                        isSelected = currentFilter == ObjectFilterType.BY_NAME,
                        onClick = {
                            viewModel.updateSearchFilter(ObjectFilterType.BY_NAME)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.Folder
                    )
                    FilterOptionObj(
                        title = "По описанию",
                        isSelected = currentFilter == ObjectFilterType.BY_DESCRIPTION,
                        onClick = {
                            viewModel.updateSearchFilter(ObjectFilterType.BY_DESCRIPTION)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.Description
                    )
                    FilterOptionObj(
                        title = "По адресу",
                        isSelected = currentFilter == ObjectFilterType.BY_ADDRESS,
                        onClick = {
                            viewModel.updateSearchFilter(ObjectFilterType.BY_ADDRESS)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.LocationOn
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterMenu = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // ===== ОСТАЛЬНЫЕ ДИАЛОГИ (без изменений) =====
    if (showCreateTypeDialog && !selectionMode) {
        CreateTypeDialog(
            onDismiss = { viewModel.hideCreateTypeDialog() },
            onCreateObject = {
                viewModel.hideCreateTypeDialog()
                viewModel.showCreateDialog()
            },
            onCreateProject = {
                viewModel.hideCreateTypeDialog()
                val objectId = parentObjectId ?: "none"
                navController.navigate("create_project/$objectId")
            },
            isInsideObject = parentObjectId != null
        )
    }

    if (showCreateDialog && !selectionMode) {
        CreateObjectDialog(
            onDismiss = { viewModel.hideCreateDialog() },
            onCreate = { name, street, house, building, description ->
                viewModel.createObject(name, street, house, building, description)
            }
        )
    }

    if (showDeleteConfirmation && objectToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            title = { Text("Удалить объект") },
            text = { Text("Вы уверены, что хотите удалить объект \"${objectToDelete?.name ?: ""}\"? Все дочерние объекты и сметы также будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        objectToDelete?.let { viewModel.deleteObject(it) }
                        viewModel.hideDeleteConfirmation()
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

    if (showEditDialog && editingObject != null && !selectionMode) {
        EditObjectDialog(
            obj = editingObject!!,
            onDismiss = { viewModel.hideEditDialog() },
            onSave = { name, street, house, building, description ->
                val updatedObj = editingObject!!.copy(
                    name = name,
                    street = street,
                    house = house,
                    building = building,
                    description = description
                )
                viewModel.updateObject(updatedObj)
            }
        )
    }

    if (showInfoDialog && infoObject != null) {
        val infoObj = infoObject!!
        val clipboardManager = LocalClipboardManager.current
        val formattedAddress = infoObj.getFormattedAddress()

        AlertDialog(
            onDismissRequest = { viewModel.hideInfoDialog() },
            title = { Text(infoObj.name) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (formattedAddress.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(formattedAddress))
                                }
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(formattedAddress)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Копировать",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                        }
                    }
                    if (infoObj.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(infoObj.description)
                        }
                    }
                    if (formattedAddress.isBlank() && infoObj.description.isBlank()) {
                        Text("Нет дополнительной информации")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideInfoDialog() }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showDeleteProjectConfirmation && projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteProjectConfirmation() },
            title = { Text("Удалить смету") },
            text = {
                Text("Вы уверены, что хотите удалить смету \"${projectToDelete!!.name}\"? Все материалы и работы будут удалены безвозвратно.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteProject() }
                ) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteProjectConfirmation() }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// НОВАЯ КАРТОЧКА ДЛЯ СМЕТ НА КОРНЕВОМ УРОВНЕ


// Остальные функции остаются без изменений
@Composable
fun FilterOptionObj(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SelectableObjectCard(
    obj: ObjectModel,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
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
                    text = obj.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                val formattedAddress = obj.getFormattedAddress()
                if (formattedAddress.isNotBlank()) {
                    Text(
                        text = formattedAddress,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 2
                    )
                }
            }
            Row {
                IconButton(onClick = onInfo) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Информация",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Button(onClick = onSelect) {
                    Text("Выбрать")
                }
            }
        }
    }
}

@Composable
fun CreateTypeDialog(
    onDismiss: () -> Unit,
    onCreateObject: () -> Unit,
    onCreateProject: () -> Unit,
    isInsideObject: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать") },
        text = { Text("Что вы хотите создать?") },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateObject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Объект")
                    }
                    Button(
                        onClick = onCreateProject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Смета")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена")
                }
            }
        },
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditObjectDialog(
    obj: ObjectModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(obj.name) }
    var street by remember { mutableStateOf(obj.street) }
    var house by remember { mutableStateOf(obj.house) }
    var building by remember { mutableStateOf(obj.building) }
    var description by remember { mutableStateOf(obj.description) }

    var streetError by remember { mutableStateOf<String?>(null) }
    var houseError by remember { mutableStateOf<String?>(null) }
    var buildingError by remember { mutableStateOf<String?>(null) }

    fun validateStreet(value: String): Boolean {
        return if (value.isNotBlank()) {
            if (value.any { it.isDigit() }) {
                streetError = "Улица не может содержать цифры"
                false
            } else {
                streetError = null
                true
            }
        } else {
            streetError = null
            true
        }
    }

    fun validateHouse(value: String): Boolean {
        return if (value.isNotBlank()) {
            if (!value.any { it.isDigit() }) {
                houseError = "Дом должен содержать цифры"
                false
            } else {
                val firstDigitIndex = value.indexOfFirst { it.isDigit() }
                val firstLetterIndex = value.indexOfFirst { it.isLetter() }

                if (firstLetterIndex != -1 && firstLetterIndex < firstDigitIndex) {
                    houseError = "Буквы не могут идти перед цифрами"
                    false
                }
                else {
                    val lettersAfterDigits = value.substring(firstDigitIndex).count { it.isLetter() }
                    if (lettersAfterDigits > 1) {
                        houseError = "После цифр может быть только одна буква"
                        false
                    }
                    else if (lettersAfterDigits == 1 && !value.last().isLetter()) {
                        houseError = "Буква должна быть в конце номера дома"
                        false
                    }
                    else {
                        houseError = null
                        true
                    }
                }
            }
        } else {
            houseError = null
            true
        }
    }

    fun validateBuilding(value: String): Boolean {
        return if (value.isNotBlank()) {
            if (!value.any { it.isDigit() }) {
                buildingError = "Корпус должен содержать цифры"
                false
            } else {
                val firstDigitIndex = value.indexOfFirst { it.isDigit() }
                val lastLetterIndex = value.indexOfLast { it.isLetter() }

                if (lastLetterIndex != -1 && lastLetterIndex > firstDigitIndex) {
                    buildingError = "Буквы могут быть только в начале"
                    false
                } else {
                    buildingError = null
                    true
                }
            }
        } else {
            buildingError = null
            true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать объект") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название объекта*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isBlank()
                )

                OutlinedTextField(
                    value = street,
                    onValueChange = {
                        street = it
                        validateStreet(it)
                    },
                    label = { Text("Улица") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = streetError != null,
                    supportingText = streetError?.let { { Text(it, color = Color.Red) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = house,
                        onValueChange = {
                            house = it
                            validateHouse(it)
                        },
                        label = { Text("Дом") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = houseError != null,
                        supportingText = houseError?.let { { Text(it, color = Color.Red) } }
                    )

                    OutlinedTextField(
                        value = building,
                        onValueChange = {
                            building = it
                            validateBuilding(it)
                        },
                        label = { Text("Корпус") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = buildingError != null,
                        supportingText = buildingError?.let { { Text(it, color = Color.Red) } }
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (street.isNotBlank() || house.isNotBlank() || building.isNotBlank()) {
                    val preview = buildString {
                        if (street.isNotBlank()) append("ул. $street")
                        if (house.isNotBlank()) {
                            if (isNotEmpty()) append(", ")
                            append("д. $house")
                        }
                        if (building.isNotBlank()) {
                            if (isNotEmpty()) append(", ")
                            append("к. $building")
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = preview,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val isStreetValid = if (street.isNotBlank()) validateStreet(street) else true
                    val isHouseValid = if (house.isNotBlank()) validateHouse(house) else true
                    val isBuildingValid = if (building.isNotBlank()) validateBuilding(building) else true

                    if (name.isNotBlank() && isStreetValid && isHouseValid && isBuildingValid) {
                        onSave(name, street, house, building, description)
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

@Composable
fun ObjectCard(
    obj: ObjectModel,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = obj.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    val formattedAddress = obj.getFormattedAddress()
                    if (formattedAddress.isNotBlank()) {
                        Text(
                            text = formattedAddress,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 2
                        )
                    }
                }
                Row {
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
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateObjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var house by remember { mutableStateOf("") }
    var building by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var streetError by remember { mutableStateOf<String?>(null) }
    var houseError by remember { mutableStateOf<String?>(null) }
    var buildingError by remember { mutableStateOf<String?>(null) }

    fun validateStreet(value: String): Boolean {
        return if (value.isNotBlank()) {
            if (value.any { it.isDigit() }) {
                streetError = "Улица не может содержать цифры"
                false
            } else {
                streetError = null
                true
            }
        } else {
            streetError = null
            true
        }
    }

    fun validateHouse(value: String): Boolean {
        return if (value.isNotBlank()) {
            if (!value.any { it.isDigit() }) {
                houseError = "Дом должен содержать цифры"
                false
            } else {
                val firstDigitIndex = value.indexOfFirst { it.isDigit() }
                val firstLetterIndex = value.indexOfFirst { it.isLetter() }

                if (firstLetterIndex != -1 && firstLetterIndex < firstDigitIndex) {
                    houseError = "Буквы не могут идти перед цифрами"
                    false
                }
                else {
                    val lettersAfterDigits = value.substring(firstDigitIndex).count { it.isLetter() }
                    if (lettersAfterDigits > 1) {
                        houseError = "После цифр может быть только одна буква"
                        false
                    }
                    else if (lettersAfterDigits == 1 && !value.last().isLetter()) {
                        houseError = "Буква должна быть в конце номера дома"
                        false
                    }
                    else {
                        houseError = null
                        true
                    }
                }
            }
        } else {
            houseError = null
            true
        }
    }

    fun validateBuilding(value: String): Boolean {
        return if (value.isNotBlank()) {
            if (!value.any { it.isDigit() }) {
                buildingError = "Корпус должен содержать цифры"
                false
            } else {
                val firstDigitIndex = value.indexOfFirst { it.isDigit() }
                val lastLetterIndex = value.indexOfLast { it.isLetter() }

                if (lastLetterIndex != -1 && lastLetterIndex > firstDigitIndex) {
                    buildingError = "Буквы могут быть только в начале"
                    false
                } else {
                    buildingError = null
                    true
                }
            }
        } else {
            buildingError = null
            true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать объект") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название объекта*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isBlank()
                )

                OutlinedTextField(
                    value = street,
                    onValueChange = {
                        street = it
                        validateStreet(it)
                    },
                    label = { Text("Улица") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = streetError != null,
                    supportingText = streetError?.let { { Text(it, color = Color.Red) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = house,
                        onValueChange = {
                            house = it
                            validateHouse(it)
                        },
                        label = { Text("Дом") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = houseError != null,
                        supportingText = houseError?.let { { Text(it, color = Color.Red) } }
                    )

                    OutlinedTextField(
                        value = building,
                        onValueChange = {
                            building = it
                            validateBuilding(it)
                        },
                        label = { Text("Корпус") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = buildingError != null,
                        supportingText = buildingError?.let { { Text(it, color = Color.Red) } }
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (street.isNotBlank() || house.isNotBlank() || building.isNotBlank()) {
                    val preview = buildString {
                        if (street.isNotBlank()) append("ул. $street")
                        if (house.isNotBlank()) {
                            if (isNotEmpty()) append(", ")
                            append("д. $house")
                        }
                        if (building.isNotBlank()) {
                            if (isNotEmpty()) append(", ")
                            append("к. $building")
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "Предпросмотр: $preview",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, street, house, building, description)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ContactSelectorRow(
    label: String,
    selectedContact: Contact?,
    onSelect: (Contact) -> Unit,
    onShowSelector: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedContact != null) {
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
            IconButton(onClick = { onSelect(selectedContact) }) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить", modifier = Modifier.size(20.dp))
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
            modifier = Modifier.width(100.dp)
        ) {
            Text(if (selectedContact != null) "Изменить" else "Выбрать")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectionDialog(
    title: String,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onSelect: (Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredContacts = if (searchQuery.isBlank()) {
        contacts
    } else {
        contacts.filter { contact ->
            contact.name.contains(searchQuery, ignoreCase = true) ||
                    contact.description.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск контакта") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredContacts) { contact ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(contact) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = contact.name,
                                    fontWeight = FontWeight.Medium
                                )
                                if (contact.description.isNotBlank()) {
                                    Text(
                                        text = contact.description,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun MoveProjectCardWithInfo(
    project: Project,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "Расходы: ${String.format(Locale.US, "%.2f", project.totalBudget)} ₽",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onInfoClick) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Информация о смете",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
//@Composable
//fun ProjectCard2(
//    project: Project,
//    onView: () -> Unit,
//    onEdit: () -> Unit,
//    onMove: () -> Unit,
//    onDelete: () -> Unit
////) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clickable { onView() },
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
//        ),
//        shape = RoundedCornerShape(8.dp)
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(12.dp),
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Column(modifier = Modifier.weight(1f)) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Icon(
//                        Icons.Default.Receipt,
//                        contentDescription = null,
//                        modifier = Modifier.size(18.dp),
//                        tint = Color(0xFFFF9800)
//                    )
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text(
//                        text = project.name,
//                        fontWeight = FontWeight.Medium,
//                        fontSize = 15.sp
//                    )
//                }
//                if (project.description.isNotBlank()) {
//                    Spacer(modifier = Modifier.height(4.dp))
//                    Text(
//                        text = project.description,
//                        fontSize = 12.sp,
//                        color = Color.Gray,
//                        maxLines = 1
//                    )
//                }
//                Spacer(modifier = Modifier.height(4.dp))
//                Text(
//                    text = "💰 Бюджет: ${String.format(Locale.US, "%.2f", project.totalBudget)} ₽",
//                    fontSize = 12.sp,
//                    color = MaterialTheme.colorScheme.primary,
//                    fontWeight = FontWeight.Medium
//                )
//            }
//            Row {
//                IconButton(onClick = onMove, modifier = Modifier.size(36.dp)) {
//                    Icon(
//                        Icons.Default.SwapHoriz,
//                        contentDescription = "Переместить",
//                        modifier = Modifier.size(20.dp),
//                        tint = MaterialTheme.colorScheme.primary
//                    )
//                }
//                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
//                    Icon(
//                        Icons.Default.Edit,
//                        contentDescription = "Редактировать",
//                        modifier = Modifier.size(20.dp),
//                        tint = MaterialTheme.colorScheme.primary
//                    )
//                }
//                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
//                    Icon(
//                        Icons.Default.Delete,
//                        contentDescription = "Удалить",
//                        modifier = Modifier.size(20.dp),
//                        tint = MaterialTheme.colorScheme.error
//                    )
//                }
//            }
//        }
//    }
//}