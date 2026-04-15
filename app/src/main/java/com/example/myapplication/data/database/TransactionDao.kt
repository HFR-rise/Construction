//package com.example.myapplication.data.database
//
//import androidx.room.*
//import com.example.myapplication.data.models.Transaction
//import kotlinx.coroutines.flow.Flow
//
//@Dao
//interface TransactionDao {
//
//    @Query("SELECT * FROM transactions WHERE projectId = :projectId ORDER BY date DESC")
//    fun getTransactionsForProject(projectId: String): Flow<List<Transaction>>
//
//    @Insert
//    suspend fun insertTransaction(transaction: Transaction)
//
//    @Update
//    suspend fun updateTransaction(transaction: Transaction)
//
//    @Delete
//    suspend fun deleteTransaction(transaction: Transaction)
//}
