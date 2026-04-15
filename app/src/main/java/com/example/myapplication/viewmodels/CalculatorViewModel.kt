package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {
    private val _unitPrice = MutableStateFlow("")
    val unitPrice: StateFlow<String> = _unitPrice

    private val _quantity = MutableStateFlow("")
    val quantity: StateFlow<String> = _quantity

    private val _laborHours = MutableStateFlow("")
    val laborHours: StateFlow<String> = _laborHours

    private val _hourlyRate = MutableStateFlow("")
    val hourlyRate: StateFlow<String> = _hourlyRate

    fun updateUnitPrice(value: String) { _unitPrice.value = value }
    fun updateQuantity(value: String) { _quantity.value = value }
    fun updateLaborHours(value: String) { _laborHours.value = value }
    fun updateHourlyRate(value: String) { _hourlyRate.value = value }

    fun calculateMaterialTotal(): Double = (_unitPrice.value.toDoubleOrNull() ?: 0.0) * (_quantity.value.toDoubleOrNull() ?: 0.0)
    fun calculateLaborTotal(): Double = (_laborHours.value.toDoubleOrNull() ?: 0.0) * (_hourlyRate.value.toDoubleOrNull() ?: 0.0)
    fun calculateGrandTotal(): Double = calculateMaterialTotal() + calculateLaborTotal()
}