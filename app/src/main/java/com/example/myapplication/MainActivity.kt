package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.viewmodels.ObjectsViewModel
import com.example.myapplication.ui.screens.*
import com.example.myapplication.utils.UserPreferences
import com.example.myapplication.viewmodels.AuthViewModel
import com.example.myapplication.services.WebSocketService
import com.example.myapplication.services.SyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var webSocketService: WebSocketService

    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        userPreferences = userPreferences,
                        webSocketService = webSocketService,
                        syncManager = syncManager
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    userPreferences: UserPreferences,
    webSocketService: WebSocketService,
    syncManager: SyncManager,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleScope = remember { lifecycleOwner.lifecycleScope }

    var lastSelectedRoute by remember { mutableStateOf("objects_root") }

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val startDestination = if (isLoggedIn) "objects_root" else "auth"

    LaunchedEffect(Unit) {
        authViewModel.navigationEvent.collect { event ->
            when (event) {
                is AuthViewModel.NavigationEvent.NavigateToAuth -> {
                    Log.e("MainScreen", "🎯 Navigate to AUTH")
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthViewModel.NavigationEvent.NavigateToMain -> {
                    Log.e("MainScreen", "🎯 Navigate to MAIN")
                    navController.navigate("objects_root") {
                        popUpTo("auth") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }


    LaunchedEffect(isLoggedIn) {
        Log.e("MainScreen", "🔥 isLoggedIn changed to: $isLoggedIn (navigation handled by events)")
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            Log.e("MainScreen", "WebSocket connected: ${webSocketService.isConnected()}, screen: ${currentDestination?.route}")
        }
    }

    LaunchedEffect(Unit) {
        syncManager.onForceLogout = {
            Log.w("MainScreen", "Force logout triggered by SyncManager on ${currentDestination?.route}")
            lifecycleScope.launch {
                Toast.makeText(context, "Сессия истекла", Toast.LENGTH_LONG).show()
                authViewModel.forceLogout()
            }
        }
    }

    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    DisposableEffect(Unit) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("MainScreen", "Network available, checking session...")
                lifecycleScope.launch {
                    val isValid = syncManager.checkCurrentSession()
                    Log.d("MainScreen", "Session check after network available: $isValid")
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    LaunchedEffect(currentDestination) {
        val route = currentDestination?.route
        if (route != null && isLoggedIn) {
            when {
                route == "objects_root" || route.startsWith("objects/") -> {
                    lastSelectedRoute = "objects_root"
                }
                route == "contacts" -> {
                    lastSelectedRoute = "contacts"
                }
                route == "materials_storage" -> {
                    lastSelectedRoute = "materials_storage"
                }
                route == "profile" -> {
                    lastSelectedRoute = "profile"
                }
                else -> { }
            }
        }
    }

    Scaffold(
        bottomBar = {
            val currentRoute = currentDestination?.route

            val shouldShowBottomBar = isLoggedIn &&
                    currentRoute != null &&
                    currentRoute != "auth" &&
                    !currentRoute.startsWith("move_project/") &&
                    !currentRoute.startsWith("create_project/") &&
                    !currentRoute.startsWith("edit_project/") &&
                    !currentRoute.startsWith("view_project/") &&
                    !currentRoute.startsWith("project_detail/")

            if (shouldShowBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50),
                                    Color(0xFF2196F3),
                                    Color(0xFF9C27B0)
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
                            BottomNavItem("Контакты", Icons.Default.Contacts, "contacts", Icons.Default.Contacts),
                            BottomNavItem("Общение", Icons.Default.Chat, "materials_storage", Icons.Default.Chat),
                            BottomNavItem("Профиль", Icons.Default.Person, "profile", Icons.Default.Person)
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
                                            popUpTo("objects_root") { inclusive = false }
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
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("auth") {
                AuthScreen(
                    navController = navController,
                    onLoginSuccess = {
                        authViewModel.refreshLoginState()
                    }
                )
            }

            composable("main_menu") {
                MainMenuScreen(navController)
            }

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

            // Экран перемещения сметы
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

            // Общение (Pending Shares)
            composable("materials_storage") {
                PendingSharesScreen(navController)
            }

            // Профиль
            composable("profile") {
                ProfileScreen(
                    navController = navController,
                    userPreferences = userPreferences,
                    onLogout = {
                        // ТОЛЬКО вызываем logout, навигация через событие
                        authViewModel.logout()
                    }
                )
            }

            // Создание проекта (корневой)
            composable("create_project_root") {
                CreateProjectScreen(
                    navController = navController,
                    mode = ProjectScreenMode.CREATE,
                    objectId = null,
                    projectId = null
                )
            }

            // Просмотр сметы
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

data class BottomNavItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

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

    var navigationStack by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var currentParentId by remember { mutableStateOf<String?>(null) }

    val onObjectSelected: (String, String) -> Unit = { objectId, objectName ->
        val finalObjectId = if (objectId == "root") {
            val rootObjects = viewModel.objects.value
            val rootObject = rootObjects.find { it.name == "Без объекта" }
            rootObject?.id ?: run {
                viewModel.createRootObjectIfNeeded()
                viewModel.objects.value.find { it.name == "Без объекта" }?.id ?: ""
            }
        } else {
            objectId
        }

        selectedObjectId = finalObjectId
        selectedObjectName = if (objectId == "root") "Корневой объект" else objectName
        showConfirmDialog = true
    }

    val onObjectOpen: (String, String) -> Unit = { objectId, objectName ->
        if (currentParentId != null) {
            val currentName = navigationStack.lastOrNull()?.second ?:
            viewModel.objects.value.find { it.id == currentParentId }?.name ?: "Объекты"
            navigationStack = navigationStack + (currentParentId!! to currentName)
        }
        currentParentId = objectId
    }

    val onNavigateBack: () -> Unit = {
        if (navigationStack.isNotEmpty()) {
            val last = navigationStack.last()
            currentParentId = last.first
            navigationStack = navigationStack.dropLast(1)
        } else {
            currentParentId = null
        }
    }

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

    if (showConfirmDialog && selectedObjectId != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Переместить смету") },
            text = {
                Text(if (selectedObjectId == "root") "Вы уверены, что хотите переместить смету на главный экран?" else "Вы уверены, что хотите переместить смету в \"${selectedObjectName ?: "выбранный"}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.moveProject(projectId, selectedObjectId!!)
                        showConfirmDialog = false
                        navController.navigateUp()
                    }
                ) {
                    Text("Переместить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}