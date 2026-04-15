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
import com.example.myapplication.data.models.Project
import com.example.myapplication.viewmodels.ObjectsViewModel
import com.example.myapplication.viewmodels.ProjectFilterType
import com.example.myapplication.viewmodels.ProjectsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    navController: NavController,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val filteredProjects by viewModel.filteredProjects.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()

    val objectsViewModel: ObjectsViewModel = hiltViewModel()
    val showDeleteProjectConfirmation by objectsViewModel.showDeleteProjectConfirmation.collectAsState()
    val projectToDelete by objectsViewModel.projectToDelete.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate("create_project/none")
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Создать смету")
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
                                        ProjectFilterType.BY_NAME -> Icons.Default.Title
                                        ProjectFilterType.BY_DESCRIPTION -> Icons.Default.Description
                                        ProjectFilterType.BY_CUSTOMER -> Icons.Default.Person
                                        ProjectFilterType.BY_FOREMAN -> Icons.Default.Build
                                        ProjectFilterType.BY_MANAGER -> Icons.Default.SupervisorAccount
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
                                            ProjectFilterType.BY_NAME -> "Поиск по названию..."
                                            ProjectFilterType.BY_DESCRIPTION -> "Поиск по описанию..."
                                            ProjectFilterType.BY_CUSTOMER -> "Поиск по заказчику..."
                                            ProjectFilterType.BY_FOREMAN -> "Поиск по прорабу..."
                                            ProjectFilterType.BY_MANAGER -> "Поиск по менеджеру..."
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
                    } else {
                        Text(
                            "Сметы",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = { },
                actions = {
                    if (!isSearchActive) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GradientDivider()
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    filteredProjects.isEmpty() && searchQuery.isNotBlank() -> {
                        NoSearchResultsContent(searchQuery)
                    }

                    filteredProjects.isEmpty() -> {
                        EmptyProjectsContent()
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredProjects) { project ->
                                ProjectCard(
                                    project = project,
                                    onClick = {
                                        navController.navigate("view_project/${project.id}")
                                    },
                                    onEdit = {
                                        navController.navigate("edit_project/${project.id}")
                                    },
                                    onMove = {
                                        navController.navigate("move_project/${project.id}/none")
                                    },
                                    onDelete = {
                                        objectsViewModel.showDeleteProjectConfirmation(project)
                                    }
                                )
                            }
                        }
                    }
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
                    Text(
                        "Основные",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    FilterOptionCard(
                        title = "По названию",
                        isSelected = currentFilter == ProjectFilterType.BY_NAME,
                        onClick = {
                            viewModel.updateSearchFilter(ProjectFilterType.BY_NAME)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.Title
                    )

                    FilterOptionCard(
                        title = "По описанию",
                        isSelected = currentFilter == ProjectFilterType.BY_DESCRIPTION,
                        onClick = {
                            viewModel.updateSearchFilter(ProjectFilterType.BY_DESCRIPTION)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.Description
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Контакты",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    FilterOptionCard(
                        title = "По заказчику",
                        isSelected = currentFilter == ProjectFilterType.BY_CUSTOMER,
                        onClick = {
                            viewModel.updateSearchFilter(ProjectFilterType.BY_CUSTOMER)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.Person
                    )

                    FilterOptionCard(
                        title = "По прорабу",
                        isSelected = currentFilter == ProjectFilterType.BY_FOREMAN,
                        onClick = {
                            viewModel.updateSearchFilter(ProjectFilterType.BY_FOREMAN)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.Build
                    )

                    FilterOptionCard(
                        title = "По менеджеру",
                        isSelected = currentFilter == ProjectFilterType.BY_MANAGER,
                        onClick = {
                            viewModel.updateSearchFilter(ProjectFilterType.BY_MANAGER)
                            showFilterMenu = false
                        },
                        icon = Icons.Default.SupervisorAccount
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

    if (showDeleteProjectConfirmation && projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { objectsViewModel.hideDeleteProjectConfirmation() },
            title = { Text("Удалить смету") },
            text = {
                Text("Вы уверены, что хотите удалить смету \"${projectToDelete!!.name}\"? Все материалы и работы будут удалены безвозвратно.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        objectsViewModel.confirmDeleteProject()
                    }
                ) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { objectsViewModel.hideDeleteProjectConfirmation() }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun FilterOptionCard(
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
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                        text = project.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (project.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = project.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onMove) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Переместить",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Бюджет: ${String.format(Locale.US, "%.2f", project.totalBudget)} ₽",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = dateFormat.format(project.createdAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun NoSearchResultsContent(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ничего не найдено",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "По запросу \"$query\" ничего не найдено",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun EmptyProjectsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "У вас пока нет смет",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Нажмите + чтобы создать смету",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}