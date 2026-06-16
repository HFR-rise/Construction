package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "objects")  // ← УБРАТЬ foreignKeys
data class ObjectModel(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val street: String = "",
    val house: String = "",
    val building: String = "",
    val description: String = "",
    val parentObjectId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = ""
) {
    fun getFormattedAddress(): String {
        return buildString {
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
    }
}