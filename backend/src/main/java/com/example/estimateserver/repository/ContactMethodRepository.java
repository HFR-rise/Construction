package com.example.estimateserver.repository;

import com.example.estimateserver.model.ContactMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContactMethodRepository extends JpaRepository<ContactMethod, String> {

    List<ContactMethod> findByContactId(String contactId);

    // Добавить метод для поиска по userId
    @Query("SELECT cm FROM ContactMethod cm WHERE cm.contactId IN (SELECT c.id FROM Contact c WHERE c.userId = :userId)")
    List<ContactMethod> findAllByUserId(@Param("userId") String userId);
}