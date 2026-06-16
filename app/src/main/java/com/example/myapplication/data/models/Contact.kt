package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)