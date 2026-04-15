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
}