package com.example.myapplication.data.database

import androidx.room.*
import com.example.myapplication.data.models.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: String): Contact?

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsSync(): List<Contact>

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchContacts(query: String): Flow<List<Contact>>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactsCount(): Int

    @Insert
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

//    @Query("DELETE FROM contacts WHERE userId = :userId")
//    suspend fun deleteAllContactsForUser(userId: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()


    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContactById(contactId: String)
}