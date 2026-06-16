package com.example.estimateserver.repository;

import com.example.estimateserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // Найти пользователя по номеру телефона (точное совпадение)
    Optional<User> findByPhoneNumber(String phoneNumber);

    // Найти пользователя по номеру телефона
    @Query("SELECT u FROM User u WHERE REPLACE(REPLACE(REPLACE(u.phoneNumber, '-', ''), ' ', ''), '(', '') = :phoneNumber")
    Optional<User> findByPhoneNumberNormalized(@Param("phoneNumber") String phoneNumber);

    // Проверить существование пользователя по номеру телефона
    boolean existsByPhoneNumber(String phoneNumber);

    // Найти всех пользователей с указанным номером (для случая дубликатов)
    Optional<User> findByPhoneNumberContaining(String phoneNumber);
}