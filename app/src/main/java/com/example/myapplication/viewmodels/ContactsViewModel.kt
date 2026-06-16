package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.repository.ContactRepository
import com.example.myapplication.services.SyncManager
import com.example.myapplication.utils.FuzzySearch
import com.example.myapplication.utils.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter(
    val displayName: String
) {
    BY_NAME("По имени"),
    BY_DESCRIPTION("По описанию"),
    BY_PHONE("Телефон"),
    BY_TELEGRAM("Telegram"),
    BY_VK("VK"),
    BY_EMAIL("Email"),
    BY_OTHER("Другой способ связи")
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repo: ContactRepository,
    private val syncManager: SyncManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _currentFilter = MutableStateFlow(SearchFilter.BY_NAME)
    val currentFilter: StateFlow<SearchFilter> = _currentFilter

    private val _contactMethodsCache = MutableStateFlow<Map<String, List<ContactMethod>>>(emptyMap())
    private val _duplicatesCache = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val duplicatesCache: StateFlow<Map<String, List<String>>> = _duplicatesCache

    private val _duplicatesVersion = MutableStateFlow(0)
    val duplicatesVersion: StateFlow<Int> = _duplicatesVersion

    private val _editingContact = MutableStateFlow<Contact?>(null)
    val editingContact: StateFlow<Contact?> = _editingContact

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        loadContacts()
        loadAllContactMethods()
    }

    private fun updateMethodsCache(contactId: String, methods: List<ContactMethod>) {
        val currentCache = _contactMethodsCache.value.toMutableMap()
        currentCache[contactId] = methods
        _contactMethodsCache.value = currentCache
        updateDuplicatesCache()
    }

    private fun normalizePhoneForSearch(phone: String): String {
        if (phone.length >= 2) {
            if (phone[0] == '+' && phone[1] != '7') {
                return ""
            }
        }
        val digitsOnly = phone.replace(Regex("[^\\d]"), "")

        return when {
            digitsOnly.startsWith("8") && digitsOnly.length == 11 -> "7" + digitsOnly.substring(1)
            digitsOnly.startsWith("8") -> "7" + digitsOnly.substring(1)
            digitsOnly.startsWith("7") -> digitsOnly
            digitsOnly.length == 10 -> "7" + digitsOnly
            digitsOnly.isNotEmpty() && digitsOnly.length <= 10 && digitsOnly.all { it.isDigit() } -> "7" + digitsOnly
            else -> digitsOnly
        }
    }

    private fun updateDuplicatesCache() {
        val methodsMap = _contactMethodsCache.value
        val contactsList = _contacts.value

        val valueToContacts = mutableMapOf<String, MutableList<String>>()

        contactsList.forEach { contact ->
            val methods = methodsMap[contact.id] ?: emptyList()
            methods.forEach { method ->
                val methodType = method.methodType.lowercase()
                val methodValue = method.value.lowercase()

                val normalizedValue = when {
                    methodType.contains("телефон") || methodType.contains("phone") -> {
                        normalizePhoneForSearch(methodValue)
                    }
                    else -> methodValue
                }

                if (normalizedValue.isNotBlank()) {
                    valueToContacts.getOrPut(normalizedValue) { mutableListOf() }.add(contact.id)
                }
            }
        }

        val duplicates = mutableMapOf<String, MutableList<String>>()

        valueToContacts.forEach { (_, contactIds) ->
            if (contactIds.size > 1) {
                contactIds.forEach { contactId ->
                    val otherContacts = contactIds.filter { it != contactId }.toMutableList()
                    if (otherContacts.isNotEmpty()) {
                        duplicates.getOrPut(contactId) { mutableListOf() }.addAll(otherContacts)
                    }
                }
            }
        }

        _duplicatesCache.value = duplicates
        _duplicatesVersion.value += 1
    }

    fun hasDuplicates(contactId: String): Boolean = _duplicatesCache.value.containsKey(contactId)

    fun getDuplicateContacts(contactId: String): List<String> = _duplicatesCache.value[contactId] ?: emptyList()

    val filteredContacts: StateFlow<List<Contact>> = combine(
        _contacts,
        _searchQuery,
        _currentFilter,
        _contactMethodsCache
    ) { contacts, query, filter, methodsCache ->
        if (query.isBlank()) {
            contacts
        } else {
            val normalizedQuery = query.lowercase().trim()

            when (filter) {
                SearchFilter.BY_NAME -> {
                    FuzzySearch.filter(
                        items = contacts,
                        query = query,
                        textExtractor = { it.name },
                        maxDistance = getMaxDistance(query)
                    )
                }
                SearchFilter.BY_DESCRIPTION -> {
                    FuzzySearch.filter(
                        items = contacts,
                        query = query,
                        textExtractor = { it.description },
                        maxDistance = getMaxDistance(query)
                    )
                }
                SearchFilter.BY_PHONE -> {
                    when {
                        normalizedQuery.isEmpty() -> contacts
                        normalizedQuery == "+" -> {
                            contacts.filter { contact ->
                                val methods = methodsCache[contact.id] ?: emptyList()
                                methods.any { method ->
                                    val methodType = method.methodType.lowercase()
                                    methodType.contains("телефон") || methodType.contains("phone")
                                }
                            }
                        }
                        else -> {
                            contacts.filter { contact ->
                                val methods = methodsCache[contact.id] ?: emptyList()
                                methods.any { method ->
                                    val methodType = method.methodType.lowercase()
                                    val methodValue = method.value.lowercase()

                                    if (methodType.contains("телефон") || methodType.contains("phone")) {
                                        val normalizedValue = normalizePhoneForSearch(methodValue)
                                        val normalizedQueryPhone = normalizePhoneForSearch(normalizedQuery)
                                        normalizedQueryPhone.isNotEmpty() && normalizedValue.startsWith(normalizedQueryPhone)
                                    } else false
                                }
                            }
                        }
                    }
                }
                SearchFilter.BY_TELEGRAM -> {
                    contacts.filter { contact ->
                        val methods = methodsCache[contact.id] ?: emptyList()
                        methods.any { method ->
                            val methodType = method.methodType.lowercase()
                            val methodValue = method.value.lowercase()
                            methodType.contains("telegram") && {
                                val valueWithoutAt = methodValue.removePrefix("@")
                                val queryWithoutAt = normalizedQuery.removePrefix("@")
                                valueWithoutAt.contains(queryWithoutAt)
                            }()
                        }
                    }
                }
                SearchFilter.BY_VK -> {
                    contacts.filter { contact ->
                        val methods = methodsCache[contact.id] ?: emptyList()
                        methods.any { method ->
                            val methodType = method.methodType.lowercase()
                            val methodValue = method.value.lowercase()
                            methodType.contains("vk") && {
                                val valueWithoutAt = methodValue.removePrefix("@")
                                val queryWithoutAt = normalizedQuery.removePrefix("@")
                                valueWithoutAt.contains(queryWithoutAt)
                            }()
                        }
                    }
                }
                SearchFilter.BY_EMAIL -> {
                    contacts.filter { contact ->
                        val methods = methodsCache[contact.id] ?: emptyList()
                        methods.any { method ->
                            val methodType = method.methodType.lowercase()
                            val methodValue = method.value.lowercase()
                            (methodType.contains("email") || methodType.contains("почта")) &&
                                    methodValue.contains(normalizedQuery)
                        }
                    }
                }
                SearchFilter.BY_OTHER -> {
                    contacts.filter { contact ->
                        val methods = methodsCache[contact.id] ?: emptyList()
                        methods.any { method ->
                            val methodType = method.methodType.lowercase()
                            val methodValue = method.value.lowercase()
                            !methodType.contains("телефон") &&
                                    !methodType.contains("phone") &&
                                    !methodType.contains("telegram") &&
                                    !methodType.contains("vk") &&
                                    !methodType.contains("email") &&
                                    !methodType.contains("почта") && {
                                val valueWithoutAt = methodValue.removePrefix("@")
                                valueWithoutAt.contains(normalizedQuery)
                            }()
                        }
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun loadContacts() {
        viewModelScope.launch {
            repo.getAllContacts().collect { list ->
                _contacts.value = list
                list.forEach { contact ->
                    loadMethodsForContact(contact.id)
                }
            }
        }
    }

    private fun loadMethodsForContact(contactId: String) {
        viewModelScope.launch {
            repo.getContactMethods(contactId).collect { methods ->
                updateMethodsCache(contactId, methods)
            }
        }
    }

    private fun loadAllContactMethods() {
        viewModelScope.launch {
            _contacts.collect { contacts ->
                contacts.forEach { contact ->
                    loadMethodsForContact(contact.id)
                }
            }
        }
    }

    private fun getMaxDistance(query: String): Int = when (query.length) {
        1 -> 0
        2 -> 1
        else -> 2
    }

    fun addContactWithMethods(name: String, description: String, methods: List<ContactMethod>) {
        viewModelScope.launch {
            try {
                Log.e("ContactsViewModel", "!!! ADD CONTACT WITH METHODS CALLED !!!")
                Log.d("ContactsViewModel", "Name: $name")
                Log.d("ContactsViewModel", "Description: $description")
                Log.d("ContactsViewModel", "Methods count: ${methods.size}")

                val userId = userPreferences.getUserId()
                Log.e("ContactsViewModel", "Current userId: '$userId'")

                if (userId == null) {
                    Log.e("ContactsViewModel", "❌ Cannot create contact: userId is null!")
                    _errorMessage.value = "Ошибка: пользователь не авторизован"
                    return@launch
                }

                val contact = Contact(
                    name = name,
                    description = description,
                    userId = userId
                )

                Log.e("ContactsViewModel", "Calling repo.addContact()...")
                val contactId = repo.addContact(contact)
                Log.e("ContactsViewModel", "✅✅✅ Contact created with id: $contactId ✅✅✅")

                methods.forEach { method ->
                    Log.d("ContactsViewModel", "Adding method: ${method.methodType} -> ${method.value}")
                    val newMethod = ContactMethod(
                        contactId = contactId,
                        methodType = method.methodType,
                        value = method.value,
                        userId = userId
                    )
                    repo.addContactMethod(newMethod)
                }

                _showAddDialog.value = false
                loadContacts()
                Log.e("ContactsViewModel", "✅ Contact creation completed successfully!")

            } catch (e: Exception) {
                Log.e("ContactsViewModel", "❌ Error creating contact: ${e.message}", e)
                _errorMessage.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun addContactMethod(contactId: String, methodType: String, value: String) {
        viewModelScope.launch {
            val method = ContactMethod(
                contactId = contactId,
                methodType = methodType,
                value = value,
                userId = userPreferences.getUserId() ?: ""
            )
            repo.addContactMethod(method)
            loadMethodsForContact(contactId)
        }
    }

    fun updateContactMethod(method: ContactMethod) {
        viewModelScope.launch {
            repo.updateContactMethod(method)
            loadMethodsForContact(method.contactId)
        }
    }

    fun deleteContactMethod(method: ContactMethod) {
        viewModelScope.launch {
            repo.deleteContactMethod(method)
            loadMethodsForContact(method.contactId)
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            repo.updateContact(contact)
            _editingContact.value = null
        }
    }

    fun startEditing(contact: Contact) {
        _editingContact.value = contact
    }

    fun showAddDialog() {
        _showAddDialog.value = true
    }

    fun hideAddDialog() {
        _showAddDialog.value = false
    }

    fun clearEditing() {
        _editingContact.value = null
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repo.deleteContact(contact)
            val currentCache = _contactMethodsCache.value.toMutableMap()
            currentCache.remove(contact.id)
            _contactMethodsCache.value = currentCache
            updateDuplicatesCache()
        }
    }

    fun getContactMethods(contactId: String): Flow<List<ContactMethod>> {
        return repo.getContactMethods(contactId)
    }

    fun refreshData() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true

            try {
                val userId = userPreferences.getUserId()
                if (userId != null && syncManager.hasInternetConnection()) {
                    Log.d("ContactsViewModel", "Refreshing contacts from server")
                    syncManager.syncDataFromServer(userId)
                    delay(1000)
                    loadContacts()
                    loadAllContactMethods()
                } else {
                    Log.d("ContactsViewModel", "No internet or user not logged in, skipping refresh")
                }
            } catch (e: Exception) {
                Log.e("ContactsViewModel", "Error refreshing data: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun updateSearchFilter(filter: SearchFilter) {
        _currentFilter.value = filter
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}