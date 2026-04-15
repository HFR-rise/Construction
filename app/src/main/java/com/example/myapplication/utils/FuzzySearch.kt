package com.example.myapplication.utils

import android.util.Log
import org.apache.commons.text.similarity.LevenshteinDistance

object FuzzySearch {

    private val levenshtein = LevenshteinDistance()

    /**
     * Нормализация номера телефона
     * Преобразует любые форматы в единый цифровой формат
     * +7 (916) 123-45-67 -> 79161234567
     * 8 (916) 123-45-67 -> 79161234567
     * 9161234567 -> 79161234567
     * 899927949285 -> 79927949285
     * +79927949285 -> 79927949285
     */
    fun normalizePhone(phone: String): String {
        // Удаляем всё кроме цифр
        val digitsOnly = phone.replace(Regex("[^\\d]"), "")

        return when {
            // Если номер начинается с 8 и имеет 11 цифр -> заменяем 8 на 7
            digitsOnly.startsWith("8") && digitsOnly.length == 11 -> {
                "7" + digitsOnly.substring(1)
            }
            // Если номер начинается с 7 и имеет 11 цифр -> оставляем как есть
            digitsOnly.startsWith("7") && digitsOnly.length == 11 -> {
                digitsOnly
            }
            // Если номер имеет 10 цифр -> добавляем 7 в начало
            digitsOnly.length == 10 -> {
                "7" + digitsOnly
            }
            // Если номер имеет больше 11 цифр, берём последние 11
            digitsOnly.length > 11 -> {
                val last11 = digitsOnly.takeLast(11)
                if (last11.startsWith("7") || last11.startsWith("8")) {
                    normalizePhone(last11)
                } else {
                    "7" + last11
                }
            }
            // Если номер начинается с 8 но короче 11 цифр (частичный ввод)
            digitsOnly.startsWith("8") && digitsOnly.length < 11 -> {
                "7" + digitsOnly.substring(1)
            }
            else -> digitsOnly
        }
    }

    fun matches(text: String, query: String, maxDistance: Int = 2): Boolean {
        if (query.isBlank()) return true
        if (text.isBlank()) return false

        val textLower = text.lowercase()
        val queryLower = query.lowercase()

        // Прямое вхождение
        if (textLower.contains(queryLower)) return true

        // Специальная обработка для телефонов
        val textNormalized = normalizePhone(textLower)
        val queryNormalized = normalizePhone(queryLower)

        if (textNormalized.isNotEmpty() && queryNormalized.isNotEmpty()) {
            // Точное совпадение нормализованных номеров
            if (textNormalized == queryNormalized) {
                Log.d("FuzzySearch", "Phone exact match: $textNormalized == $queryNormalized")
                return true
            }
            // Частичное совпадение (например, ищем по последним цифрам)
            if (textNormalized.contains(queryNormalized)) {
                Log.d("FuzzySearch", "Phone contains match: $textNormalized contains $queryNormalized")
                return true
            }
            // Нечёткое совпадение для длинных номеров
            if (queryNormalized.length >= 5) {
                val distance = levenshtein.apply(textNormalized, queryNormalized)
                if (distance <= maxDistance) {
                    Log.d("FuzzySearch", "Phone fuzzy match: $textNormalized vs $queryNormalized, distance=$distance")
                    return true
                }
            }
        }

        // Для коротких запросов (меньше 3 символов) - только точное совпадение
        if (queryLower.length < 3) {
            return textLower == queryLower || textLower.contains(queryLower)
        }

        // Проверка по словам
        val words = textLower.split(" ")
        for (word in words) {
            if (word.length >= queryLower.length - maxDistance) {
                val distance = levenshtein.apply(word, queryLower)
                if (distance <= maxDistance) return true
            }
        }

        // Проверка всего текста
        if (queryLower.length >= 3) {
            val totalDistance = levenshtein.apply(textLower, queryLower)
            if (totalDistance <= maxDistance) return true
        }

        return false
    }

    fun <T> filter(
        items: List<T>,
        query: String,
        textExtractor: (T) -> String,
        maxDistance: Int = 2
    ): List<T> {
        if (query.isBlank()) return items
        return items.filter { item ->
            matches(textExtractor(item), query, maxDistance)
        }
    }
}
