package com.example.myapplication.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {

    // Вставка операции
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: SyncOperationEntity)

    // Удаление операции по ID
    @Delete
    suspend fun delete(operation: SyncOperationEntity)

    // Удаление операции по ID
    @Query("DELETE FROM sync_operations WHERE id = :operationId")
    suspend fun deleteById(operationId: String)

    // Получение всех операций для пользователя (сортировка по времени)
    @Query("SELECT * FROM sync_operations WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getOperationsForUser(userId: String): List<SyncOperationEntity>

    // Получение всех операций для пользователя как Flow (для наблюдения)
    @Query("SELECT * FROM sync_operations WHERE userId = :userId ORDER BY timestamp ASC")
    fun observeOperationsForUser(userId: String): Flow<List<SyncOperationEntity>>

    // Получение количества операций для пользователя
    @Query("SELECT COUNT(*) FROM sync_operations WHERE userId = :userId")
    suspend fun getOperationsCountForUser(userId: String): Int

    // Очистка всех операций для пользователя
    @Query("DELETE FROM sync_operations WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    // Очистка всех операций (для выхода из аккаунта)
    @Query("DELETE FROM sync_operations")
    suspend fun clearAll()

    // Получение операций по типу
    @Query("SELECT * FROM sync_operations WHERE userId = :userId AND type = :type ORDER BY timestamp ASC")
    suspend fun getOperationsByType(userId: String, type: String): List<SyncOperationEntity>

    // Получение операций по entityType
    @Query("SELECT * FROM sync_operations WHERE userId = :userId AND entityType = :entityType ORDER BY timestamp ASC")
    suspend fun getOperationsByEntityType(userId: String, entityType: String): List<SyncOperationEntity>

    // Удаление операций старше определённой даты
    @Query("DELETE FROM sync_operations WHERE userId = :userId AND timestamp < :timestamp")
    suspend fun deleteOldOperations(userId: String, timestamp: Long)

    // Получение последней операции для конкретной сущности
    @Query("SELECT * FROM sync_operations WHERE userId = :userId AND entityId = :entityId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOperationForEntity(userId: String, entityId: String): SyncOperationEntity?
}