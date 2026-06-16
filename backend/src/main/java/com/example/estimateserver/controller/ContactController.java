package com.example.estimateserver.controller;

import com.example.estimateserver.model.Contact;
import com.example.estimateserver.model.ContactMethod;
import com.example.estimateserver.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@CrossOrigin(origins = "*")
public class ContactController {

    private final SyncService syncService;

    public ContactController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping
    public ResponseEntity<List<Contact>> getAllContacts(@RequestHeader("X-User-Id") String userId) {
        List<Contact> contacts = syncService.getContactsForUser(userId);
        return ResponseEntity.ok(contacts);
    }

    /**
     * GET /api/contacts/{contactId}/methods
     * Получить все способы связи для контакта
     * Android вызывает: GET http://localhost:8080/api/contacts/123/methods
     */
    @GetMapping("/{contactId}/methods")
    public ResponseEntity<List<ContactMethod>> getContactMethods(@PathVariable String contactId) {
        List<ContactMethod> methods = syncService.getContactMethods(contactId);
        return ResponseEntity.ok(methods);
    }

    /**
     * POST /api/contacts
     * Создать новый контакт
     * Android вызывает: POST http://localhost:8080/api/contacts
     * Тело запроса: { "name": "Иван", "description": "Заказчик" }
     * Header: X-User-Id: {userId}
     */
    @PostMapping
    public ResponseEntity<Contact> createContact(@RequestBody Contact contact,
                                                 @RequestHeader("X-User-Id") String userId) {
        Contact created = syncService.addContact(contact, userId);
        return ResponseEntity.ok(created);
    }

    /**
     * PUT /api/contacts/{id}
     * Обновить существующий контакт
     * Android вызывает: PUT http://localhost:8080/api/contacts/123
     * Header: X-User-Id: {userId}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Contact> updateContact(@PathVariable String id,
                                                 @RequestBody Contact contact,
                                                 @RequestHeader("X-User-Id") String userId) {
        contact.setId(id);
        Contact updated = syncService.updateContact(contact, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/contacts/{id}
     * Удалить контакт
     * Android вызывает: DELETE http://localhost:8080/api/contacts/123
     * Header: X-User-Id: {userId}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable String id,
                                              @RequestHeader("X-User-Id") String userId) {
        syncService.deleteContact(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/contacts/methods
     * Добавить способ связи для контакта
     * Android вызывает: POST http://localhost:8080/api/contacts/methods
     * Тело запроса: { "contactId": "123", "methodType": "Телефон", "value": "+79161234567" }
     * Header: X-User-Id: {userId}
     */
    @PostMapping("/methods")
    public ResponseEntity<ContactMethod> addContactMethod(@RequestBody ContactMethod method,
                                                          @RequestHeader("X-User-Id") String userId) {
        ContactMethod created = syncService.addContactMethod(method, userId);
        return ResponseEntity.ok(created);
    }

    /**
     * GET /api/contacts/search/by-phone
     * Найти контакты по номеру телефона
     * Android вызывает: GET http://localhost:8080/api/contacts/search/by-phone?phone=79161234567
     */
    @GetMapping("/search/by-phone")
    public ResponseEntity<List<Contact>> searchContactsByPhone(@RequestParam String phone,
                                                               @RequestHeader("X-User-Id") String userId) {
        List<Contact> contacts = syncService.searchContactsByPhoneForUser(phone, userId);
        return ResponseEntity.ok(contacts);
    }
}
