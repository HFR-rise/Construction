package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.database.ContactDao
import com.example.myapplication.data.database.ContactMethodDao
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.network.ApiService
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactMethodDao: ContactMethodDao,
    private val apiService: ApiService,
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) {

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun addContact(contact: Contact): String {
        // ===== КРИТИЧЕСКОЕ ЛОГИРОВАНИЕ =====
        Log.e("ContactRepository", "!!! ADD CONTACT CALLED !!!")
        Log.e("ContactRepository", "Contact name: '${contact.name}'")
        Log.e("ContactRepository", "Contact id: '${contact.id}'")
        Log.e("ContactRepository", "Contact userId: '${contact.userId}'")
        // ===================================

        val contactWithId = if (contact.id.isEmpty()) {
            val newId = java.util.UUID.randomUUID().toString()
            Log.d("ContactRepository", "Generated new ID: $newId")
            contact.copy(id = newId)
        } else {
            contact
        }

        contactDao.insertContact(contactWithId)
        Log.d("ContactRepository", "✅ Contact saved locally: ${contactWithId.name} (${contactWithId.id})")

        val userId = userPreferences.getUserId()
        Log.d("ContactRepository", "Current userId from preferences: '$userId'")
        Log.d("ContactRepository", "Has internet connection: ${syncManager.hasInternetConnection()}")

        if (userId == null) {
            Log.e("ContactRepository", "❌ userId is NULL! Cannot sync to server!")
            return contactWithId.id
        }

        if (!syncManager.hasInternetConnection()) {
            Log.w("ContactRepository", "📱 No internet connection, contact queued for later sync")
            syncManager.queueOperation("CREATE", "CONTACT", contactWithId.id, contactWithId)
            return contactWithId.id
        }

        try {
            Log.d("ContactRepository", "📤 Sending contact to server...")
            Log.d("ContactRepository", "URL: /api/contacts")
            Log.d("ContactRepository", "Header X-User-Id: $userId")
            Log.d("ContactRepository", "Body: name=${contactWithId.name}, description=${contactWithId.description}")

            val response = apiService.createContact(contactWithId, userId)

            Log.d("ContactRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                val serverContact = response.body()
                if (serverContact != null) {
                    contactDao.updateContact(serverContact)
                    Log.e("ContactRepository", "✅✅✅ CONTACT SYNCED TO SERVER SUCCESSFULLY! ✅✅✅")
                    Log.d("ContactRepository", "Server returned id: ${serverContact.id}")
                } else {
                    Log.e("ContactRepository", "❌ Server returned null body")
                }
            } else {
                Log.e("ContactRepository", "❌ Failed to sync contact: ${response.code()} ${response.message()}")
                val errorBody = response.errorBody()?.string()
                Log.e("ContactRepository", "Error body: $errorBody")
                syncManager.queueOperation("CREATE", "CONTACT", contactWithId.id, contactWithId)
            }
        } catch (e: Exception) {
            Log.e("ContactRepository", "❌ Exception during sync: ${e.message}", e)
            syncManager.queueOperation("CREATE", "CONTACT", contactWithId.id, contactWithId)
        }

        return contactWithId.id
    }

    suspend fun updateContact(contact: Contact) {
        Log.d("ContactRepository", "Update contact called: ${contact.name}")

        contactDao.updateContact(contact)
        Log.d("ContactRepository", "Contact updated locally: ${contact.name}")

        val userId = userPreferences.getUserId()
        if (userId != null && syncManager.hasInternetConnection()) {
            try {
                val response = apiService.updateContact(contact.id, contact, userId)
                if (response.isSuccessful) {
                    Log.d("ContactRepository", "Contact updated on server: ${contact.name}")
                } else {
                    syncManager.queueOperation("UPDATE", "CONTACT", contact.id, contact)
                }
            } catch (e: Exception) {
                syncManager.queueOperation("UPDATE", "CONTACT", contact.id, contact)
            }
        } else if (userId != null) {
            syncManager.queueOperation("UPDATE", "CONTACT", contact.id, contact)
        }
    }

    suspend fun deleteContact(contact: Contact) {
        Log.d("ContactRepository", "Delete contact called: ${contact.name}")

        contactDao.deleteContact(contact)
        Log.d("ContactRepository", "Contact deleted locally: ${contact.name}")

        val userId = userPreferences.getUserId()
        if (userId != null && syncManager.hasInternetConnection()) {
            try {
                val response = apiService.deleteContact(contact.id, userId)
                if (response.isSuccessful) {
                    Log.d("ContactRepository", "Contact deleted from server: ${contact.name}")
                } else {
                    syncManager.queueOperation("DELETE", "CONTACT", contact.id, contact)
                }
            } catch (e: Exception) {
                syncManager.queueOperation("DELETE", "CONTACT", contact.id, contact)
            }
        } else if (userId != null) {
            syncManager.queueOperation("DELETE", "CONTACT", contact.id, contact)
        }
    }

    suspend fun addContactMethod(method: ContactMethod): String {
        Log.d("ContactRepository", "Add contact method called: ${method.methodType}")

        val methodWithId = if (method.id.isEmpty()) {
            method.copy(id = java.util.UUID.randomUUID().toString())
        } else {
            method
        }

        contactMethodDao.insertContactMethod(methodWithId)
        Log.d("ContactRepository", "ContactMethod saved locally: ${methodWithId.methodType}")

        val userId = userPreferences.getUserId()
        if (userId != null && syncManager.hasInternetConnection()) {
            try {
                val response = apiService.addContactMethod(methodWithId, userId)
                if (response.isSuccessful) {
                    Log.d("ContactRepository", "ContactMethod synced to server")
                } else {
                    syncManager.queueOperation("CREATE", "CONTACT_METHOD", methodWithId.id, methodWithId)
                }
            } catch (e: Exception) {
                syncManager.queueOperation("CREATE", "CONTACT_METHOD", methodWithId.id, methodWithId)
            }
        } else if (userId != null) {
            syncManager.queueOperation("CREATE", "CONTACT_METHOD", methodWithId.id, methodWithId)
        }

        return methodWithId.id
    }

    suspend fun updateContactMethod(method: ContactMethod) {
        contactMethodDao.updateContactMethod(method)
    }

    suspend fun deleteContactMethod(method: ContactMethod) {
        contactMethodDao.deleteContactMethod(method)
    }

    fun getContactMethods(contactId: String): Flow<List<ContactMethod>> =
        contactMethodDao.getMethodsForContact(contactId)

    suspend fun getContactsCount(): Int = contactDao.getContactsCount()

    suspend fun deleteAllContacts() {
        contactDao.deleteAll()
        contactMethodDao.deleteAll()
    }

    suspend fun getAllContactsSync(): List<Contact> {
        return contactDao.getAllContacts().first()
    }
}
