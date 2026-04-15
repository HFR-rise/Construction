package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = ObjectModel::class,
            parentColumns = ["id"],
            childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Project(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val objectId: String? = null,
    val customerContactId: String? = null,
    val foremanContactId: String? = null,
    val managerContactId: String? = null,
    val includeForeman: Boolean = false,
    val includeManager: Boolean = false,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0
)

enum class ProjectStatus {
    ACTIVE, COMPLETED, ARCHIVED
}