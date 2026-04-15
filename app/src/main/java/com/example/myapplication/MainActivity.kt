package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.theme.*
import com.example.myapplication.ui.theme.FinanceAppTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.viewmodels.ObjectsViewModel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var lastSelectedRoute by remember { mutableStateOf("objects_root") }

    // Следим за изменениями destination и обновляем lastSelectedRoute
    LaunchedEffect(currentDestination) {
        val route = currentDestination?.route
        if (route != null) {
            when {
                route == "objects_root" || route.startsWith("objects/") -> {
                    lastSelectedRoute = "objects_root"
                }
                route == "projects_list" || route.startsWith("project_detail/") -> {
                    lastSelectedRoute = "projects_list"
                }
                route == "contacts" -> {
                    lastSelectedRoute = "contacts"
                }
                route == "materials_storage" -> {
                    lastSelectedRoute = "materials_storage"
                }
                else -> { }
            }
        }
    }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF4CAF50), // Зелёный
                                Color(0xFF2196F3), // Синий
                                Color(0xFF9C27B0)  // Фиолетовый
                            ),
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val items = listOf(
                        BottomNavItem("Объекты", Icons.Default.Folder, "objects_root", Icons.Default.Folder),
                        BottomNavItem("Сметы", Icons.Default.Receipt, "projects_list", Icons.Default.Receipt),
                        BottomNavItem("Контакты", Icons.Default.Contacts, "contacts", Icons.Default.Contacts),
                        BottomNavItem("Общение", Icons.Default.Chat, "materials_storage", Icons.Default.Chat)
                    )

                    items.forEach { item ->
                        val isSelected = lastSelectedRoute == item.route
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    lastSelectedRoute = item.route
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isSelected) item.selectedIcon else item.icon,
                                contentDescription = item.title,
                                modifier = Modifier.size(24.dp),
                                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    item.title,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    )  { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "objects_root",
            modifier = Modifier.padding(paddingValues)
        ) {
            // Главное меню
            composable("main_menu") {
                MainMenuScreen(navController)
            }

            // Объекты - корневой уровень
            composable("objects_root") {
                ObjectsScreen(
                    navController = navController,
                    parentObjectId = null,
                    selectionMode = false,
                    onObjectSelected = null,
                    onObjectOpen = null,
                    onNavigateBack = null,
                    navigationStack = emptyList()
                )
            }


            // Объекты - с родительским ID
            composable(
                route = "objects/{parentId}",
                arguments = listOf(navArgument("parentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val parentId = backStackEntry.arguments?.getString("parentId")
                ObjectsScreen(
                    navController = navController,
                    parentObjectId = parentId,
                    selectionMode = false,
                    onObjectSelected = null,
                    onObjectOpen = null,
                    onNavigateBack = null,
                    navigationStack = emptyList()
                )
            }

            // Экран перемещения сметы (использует ObjectsScreen в режиме выбора)
            composable(
                route = "move_project/{projectId}/{currentObjectId}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType },
                    navArgument("currentObjectId") {
                        type = NavType.StringType
                        defaultValue = "none"
                    }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val currentObjectId = backStackEntry.arguments?.getString("currentObjectId")
                val actualCurrentObjectId = if (currentObjectId == "none") null else currentObjectId

                MoveProjectWrapper(
                    navController = navController,
                    projectId = projectId,
                    currentObjectId = actualCurrentObjectId
                )
            }

            // Список всех смет (только просмотр)
            composable("projects_list") {
                ProjectsScreen(navController)
            }

            // Детали проекта
            composable(
                route = "project_detail/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId")
                ProjectDetailScreen(navController)
            }

            // Контакты
            composable("contacts") {
                ContactsScreen(navController)
            }

            // Склад материалов (Общение)
            composable("materials_storage") {
                MaterialsStorageScreen(navController)
            }

//            // Создание проекта (без объекта)
//            composable("create_project") {
//                CreateProjectScreen(navController, null)
//            }

            // Создание проекта внутри объекта
//            composable(
//                route = "create_project/{objectId}",
//                arguments = listOf(navArgument("objectId") { type = NavType.StringType })
//            ) { backStackEntry ->
//                val objectId = backStackEntry.arguments?.getString("objectId")
//                CreateProjectScreen(
//                    navController = navController,
//                    mode = ProjectScreenMode.CREATE,
//                    objectId = objectId,
//                    projectId = null
//                )
//            }

//            // Создание проекта (без объекта)
//            composable("create_project") {
//                CreateProjectScreen(
//                    navController = navController,
//                    mode = ProjectScreenMode.CREATE,
//                    objectId = null,
//                    projectId = null
//                )
//            }


            composable("create_project_root") {
                CreateProjectScreen(
                    navController = navController,
                    mode = ProjectScreenMode.CREATE,
                    objectId = null,
                    projectId = null
                )
            }

            // Просмотр сметы (только чтение)
            composable(
                route = "view_project/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId")
                CreateProjectScreen(
                    navController = navController,
                    mode = ProjectScreenMode.VIEW,
                    objectId = null,
                    projectId = projectId
                )
            }
// Создание проекта внутри объекта
            composable(
                route = "create_project/{objectId}",
                arguments = listOf(navArgument("objectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val objectId = backStackEntry.arguments?.getString("objectId")
                CreateProjectScreen(
                    navController = navController,
                    mode = ProjectScreenMode.CREATE,
                    objectId = objectId,
                    projectId = null
                )
            }

// Редактирование проекта
            composable(
                route = "edit_project/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId")
                CreateProjectScreen(
                    navController = navController,
                    mode = ProjectScreenMode.EDIT,
                    objectId = null,
                    projectId = projectId
                )
            }
        }
    }
}

@Composable
fun MoveProjectWrapper(
    navController: androidx.navigation.NavController,
    projectId: String,
    currentObjectId: String?
) {
    var selectedObjectId by remember { mutableStateOf<String?>(null) }
    var selectedObjectName by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val viewModel: ObjectsViewModel = hiltViewModel()

    // Стек навигации: список объектов, которые мы открыли
    var navigationStack by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var currentParentId by remember { mutableStateOf<String?>(null) }

    Log.d("MoveProject", "=== MoveProjectWrapper RECOMPOSE ===")
    Log.d("MoveProject", "currentParentId: $currentParentId")
    Log.d("MoveProject", "navigationStack: $navigationStack")

    // Функция для обработки выбора объекта (перемещение)
    val onObjectSelected: (String, String) -> Unit = { objectId, objectName ->
        Log.d("MoveProject", "onObjectSelected CALLED: $objectName ($objectId)")
        selectedObjectId = objectId
        selectedObjectName = objectName
        showConfirmDialog = true
    }

    // Функция для открытия объекта (переход внутрь)
    val onObjectOpen: (String, String) -> Unit = { objectId, objectName ->
        Log.d("MoveProject", "=== onObjectOpen CALLED ===")
        Log.d("MoveProject", "Opening object: $objectName ($objectId)")
        Log.d("MoveProject", "Current parentId before: $currentParentId")

        // Добавляем текущий объект в стек, если он есть
        if (currentParentId != null) {
            val currentName = navigationStack.lastOrNull()?.second ?:
            viewModel.objects.value.find { it.id == currentParentId }?.name ?: "Объекты"
            Log.d("MoveProject", "Adding to stack: $currentParentId to $currentName")
            navigationStack = navigationStack + (currentParentId!! to currentName)
        }

        currentParentId = objectId
        Log.d("MoveProject", "Current parentId after: $currentParentId")
        Log.d("MoveProject", "Navigation stack size: ${navigationStack.size}")
    }

    // Функция для возврата назад
    val onNavigateBack: () -> Unit = {
        Log.d("MoveProject", "=== onNavigateBack CALLED ===")
        Log.d("MoveProject", "Current parentId before: $currentParentId")
        Log.d("MoveProject", "Navigation stack before: $navigationStack")

        if (navigationStack.isNotEmpty()) {
            val last = navigationStack.last()
            currentParentId = last.first
            navigationStack = navigationStack.dropLast(1)
            Log.d("MoveProject", "Popped from stack, new parentId: $currentParentId")
        } else {
            currentParentId = null
            Log.d("MoveProject", "Stack empty, setting parentId to null")
        }

        Log.d("MoveProject", "Current parentId after: $currentParentId")
    }

    // Отображаем ObjectsScreen с текущим parentId
    ObjectsScreen(
        navController = navController,
        parentObjectId = currentParentId,
        selectionMode = true,
        onObjectSelected = onObjectSelected,
        onObjectOpen = onObjectOpen,
        onNavigateBack = onNavigateBack,
        navigationStack = navigationStack,
        viewModel = viewModel
    )

    // Диалог подтверждения перемещения
    if (showConfirmDialog && selectedObjectId != null) {
        Log.d("MoveProject", "Showing confirm dialog for: $selectedObjectName ($selectedObjectId)")
        AlertDialog(
            onDismissRequest = {
                Log.d("MoveProject", "Confirm dialog dismissed")
                showConfirmDialog = false
            },
            title = { Text("Переместить смету") },
            text = { Text("Вы уверены, что хотите переместить смету в объект \"${selectedObjectName ?: "выбранный"}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Log.d("MoveProject", "Confirm button clicked, moving project $projectId to $selectedObjectId")
                        viewModel.moveProject(projectId, selectedObjectId!!)
                        showConfirmDialog = false
                        navController.navigateUp()
                    }
                ) {
                    Text("Переместить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        Log.d("MoveProject", "Cancel button clicked")
                        showConfirmDialog = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}


data class BottomNavItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)
