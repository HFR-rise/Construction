package com.example.myapplication.data.database

import androidx.room.*
import com.example.myapplication.data.models.ContactMethod
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactMethodDao {

    @Query("SELECT * FROM contact_methods WHERE contactId = :contactId")
    fun getMethodsForContact(contactId: String): Flow<List<ContactMethod>>

    @Insert
    suspend fun insertContactMethod(method: ContactMethod)

    @Update
    suspend fun updateContactMethod(method: ContactMethod)

    @Delete
    suspend fun deleteContactMethod(method: ContactMethod)

    @Query("DELETE FROM contact_methods")
    suspend fun deleteAll()

    @Query("DELETE FROM contact_methods WHERE id = :methodId")
    suspend fun deleteContactMethodById(methodId: String)

    @Query("SELECT * FROM contact_methods WHERE id = :id")
    suspend fun getContactMethodById(id: String): ContactMethod?



//    @Query("DELETE FROM contact_methods WHERE contactId IN (SELECT id FROM contacts WHERE userId = :userId)")
//    suspend fun deleteAllContactMethodsForUser(userId: String)

}