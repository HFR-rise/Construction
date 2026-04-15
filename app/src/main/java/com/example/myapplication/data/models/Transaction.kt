//package com.example.myapplication.data.models
//
//import androidx.room.Entity
//import androidx.room.Index
//import androidx.room.ForeignKey
//import androidx.room.PrimaryKey
//import java.util.Date
//import java.util.UUID
//
//@Entity(
//    tableName = "transactions",
//    indices = [Index(value = ["projectId"])],
//    foreignKeys = [
//        ForeignKey(
//            entity = Project::class,           // ← ДОБАВЬТЕ ЭТУ СТРОКУ
//            parentColumns = ["id"],
//            childColumns = ["projectId"],
//            onDelete = ForeignKey.CASCADE
//        )
//    ]
//)
//data class Transaction(
//    @PrimaryKey
//    val id: String = UUID.randomUUID().toString(),
//    val projectId: String,
//    val amount: Double,
//    val type: TransactionType,
//    val category: String = "",
//    val description: String = "",
//    val receiptPath: String? = null,
//    val date: Date = Date(),
//    val isConfirmed: Boolean = false
//)
//
//enum class TransactionType {
//    INCOME, EXPENSE
//}
