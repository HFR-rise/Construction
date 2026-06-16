package com.example.myapplication.ui.screens

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
import com.example.myapplication.viewmodels.PendingSharesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingSharesScreen(
    navController: NavController,
    viewModel: PendingSharesViewModel = hiltViewModel()
) {
    val pendingProjects by viewModel.pendingProjects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadPendingProjects()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Общение",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
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
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (pendingProjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет приглашений",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Когда вам отправят смету, она появится здесь",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingProjects) { project ->
                        PendingProjectCard(
                            project = project,
                            onAccept = {
                                viewModel.acceptShare(project.id)
                            },
                            onDecline = {
                                viewModel.declineShare(project.id)
                            },
                            onViewDetails = {
                                navController.navigate("view_project/${project.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendingProjectCard(
    project: Project,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Вам предложили смету",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Просмотреть")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Принять")
                }

                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Отклонить")
                }
            }
        }
    }
}