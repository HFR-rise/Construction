package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.viewmodels.CalculatorViewModel
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = hiltViewModel()
) {
    val unitPrice by viewModel.unitPrice.collectAsState()
    val quantity by viewModel.quantity.collectAsState()
    val laborHours by viewModel.laborHours.collectAsState()
    val hourlyRate by viewModel.hourlyRate.collectAsState()

    val materialTotal = viewModel.calculateMaterialTotal()
    val laborTotal = viewModel.calculateLaborTotal()
    val grandTotal = viewModel.calculateGrandTotal()

    val formatter = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Калькулятор",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        MaterialCard(
            title = "Материалы",
            fields = listOf(
                "Цена за единицу" to unitPrice to { viewModel.updateUnitPrice(it) },
                "Количество" to quantity to { viewModel.updateQuantity(it) }
            ),
            totalLabel = "Итого материалы:",
            totalValue = formatter.format(materialTotal)
        )

        MaterialCard(
            title = "Работы",
            fields = listOf(
                "Часы работы" to laborHours to { viewModel.updateLaborHours(it) },
                "Ставка в час" to hourlyRate to { viewModel.updateHourlyRate(it) }
            ),
            totalLabel = "Итого работы:",
            totalValue = formatter.format(laborTotal)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Общая стоимость",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatter.format(grandTotal),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialCard(
    title: String,
    fields: List<Pair<Pair<String, String>, (String) -> Unit>>,
    totalLabel: String,
    totalValue: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            fields.forEach { (fieldData, onValueChange) ->
                val (label, value) = fieldData
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = totalValue,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}