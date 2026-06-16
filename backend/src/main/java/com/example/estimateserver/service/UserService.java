package com.example.estimateserver.service;

import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.User;
import com.example.estimateserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SmsService smsService;
    private final WebSocketService webSocketService;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository userRepository, SmsService smsService, WebSocketService webSocketService) {
        this.userRepository = userRepository;
        this.smsService = smsService;
        this.webSocketService = webSocketService;
    }

    @Transactional
    public void sendVerificationCode(String phoneNumber) {
        String code = String.format("%06d", random.nextInt(1000000));

        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            user = new User(phoneNumber);
            user.setId(UUID.randomUUID().toString());
            user.setUserId(user.getId());
            user.setCreatedAt(new Date());
            user.setLastActiveAt(new Date());
        }

        user.setVerificationCode(code);
        user.setCodeExpiresAt(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
        user.setVerified(false);

        userRepository.save(user);
        smsService.sendCode(phoneNumber, code);
        System.out.println("Code for " + phoneNumber + ": " + code);
    }

    @Transactional
    public User verifyCode(String phoneNumber, String code) {
        return verifyCode(phoneNumber, code, null);
    }

    @Transactional
    public User verifyCode(String phoneNumber, String code, String deviceId) {
        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOpt.get();

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
            throw new RuntimeException("Invalid code");
        }

        if (user.getCodeExpiresAt() != null && user.getCodeExpiresAt().before(new Date())) {
            throw new RuntimeException("Code expired");
        }

        String finalDeviceId = deviceId != null ? deviceId : UUID.randomUUID().toString();

        System.out.println("🔐 VERIFY CODE START");
        System.out.println("   User ID: " + user.getId());
        System.out.println("   Phone: " + user.getPhoneNumber());
        System.out.println("   Current activeSessionId in DB: " + user.getActiveSessionId());
        System.out.println("   New deviceId: " + finalDeviceId);

        if (user.getActiveSessionId() != null && !user.getActiveSessionId().equals(finalDeviceId)) {

            boolean hasExistingSession = webSocketService.hasAnySession(user.getId());

            boolean isOldDeviceOnline = webSocketService.hasSession(user.getId());

            System.out.println("   Old deviceId: " + user.getActiveSessionId());
            System.out.println("   Checking WebSocket session for userId: " + user.getId());
            System.out.println("   Has any session: " + hasExistingSession);
            System.out.println("   Is old device online: " + isOldDeviceOnline);


            if (isOldDeviceOnline) {
                System.err.println("❌ LOGIN REJECTED: User " + user.getPhoneNumber() +
                        " already ONLINE on device " + user.getActiveSessionId());
                System.err.println("   New device " + finalDeviceId + " is BLOCKED");
                throw new RuntimeException("Account already in use on another device");

            } else {
                System.out.println("✅ Old device is OFFLINE, allowing new device to login");

                if (hasExistingSession) {
                    System.out.println("🔴🔴🔴 WILL REMOVE OLD SESSION for user: " + user.getId() +
                            " because new device is logging in");
                    webSocketService.removeSession(user.getId());
                    System.out.println("   Removed old session for user: " + user.getId());

                    System.out.println("📦 Queuing FORCE_LOGOUT for old device: " + user.getId());
                    webSocketService.queueForceLogoutForOfflineUser(user.getId());
                    System.out.println("   Queued FORCE_LOGOUT for old device (will receive on reconnect)");
                }
            }
        } else if (user.getActiveSessionId() != null && user.getActiveSessionId().equals(finalDeviceId)) {
            System.out.println("   Same device is logging in again, allowing");
        } else {
            System.out.println("   No active session found, first time login");
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setCodeExpiresAt(null);
        user.setLastActiveAt(new Date());
        user.setActiveSessionId(finalDeviceId);
        user.setCurrentDeviceInfo(finalDeviceId);

        if (user.getUserId() == null) {
            user.setUserId(user.getId());
        }

        User savedUser = userRepository.save(user);

        System.out.println("✅ User " + savedUser.getPhoneNumber() + " logged in with device: " + finalDeviceId);
        System.out.println("   Active session in DB: " + savedUser.getActiveSessionId());
        System.out.println("🔐 VERIFY CODE END");
        System.out.println();

        return savedUser;
    }

    @Transactional
    public void logout(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            System.out.println("👋 User logout: " + user.getPhoneNumber());
            System.out.println("   Current device: " + user.getActiveSessionId());
            System.out.println("🔴🔴🔴 LOGOUT CALLED for user: " + userId);

            webSocketService.removeSession(userId);

            user.setActiveSessionId(null);
            user.setCurrentDeviceInfo(null);
            userRepository.save(user);

            System.out.println("   Session cleared for user: " + userId);
        }
    }

    @Transactional
    public void forceLogout(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            System.out.println("⚠️ Force logout for user: " + user.getPhoneNumber());
            System.out.println("   Current device: " + user.getActiveSessionId());

            boolean isOnline = webSocketService.hasSession(userId);
            System.out.println("   Device is online? " + isOnline);

            if (isOnline) {
                webSocketService.sendForceLogout(userId);
                System.out.println("   Force logout sent to online device: " + userId);
            } else {
                System.out.println("   Device is offline, just clearing session in DB");
                webSocketService.queueForceLogoutForOfflineUser(userId);
            }

            user.setActiveSessionId(null);
            user.setCurrentDeviceInfo(null);
            userRepository.save(user);

            System.out.println("   Session cleared from DB for user: " + userId);
        }
    }

    /**
     * Проверка валидности сессии при подключении WebSocket
     * ✅ ТАЙМАУТ УДАЛЁН! Только проверка deviceId
     */
    @Transactional
    public boolean isSessionValid(String userId, String deviceId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            System.out.println("⚠️ isSessionValid: User not found: " + userId);
            return false;
        }
        User user = userOpt.get();

        String activeSessionId = user.getActiveSessionId();

        boolean deviceMatches = activeSessionId != null && activeSessionId.equals(deviceId);

        if (!deviceMatches) {
            System.out.println("⚠️ Session invalid: deviceId mismatch for user " + userId +
                    ". Expected: " + activeSessionId + ", Got: " + deviceId);
            return false;
        }

        boolean hasWebSocketInMemory = webSocketService.hasSession(userId);

        System.out.println("📊 isSessionValid: userId=" + userId +
                ", deviceId=" + deviceId +
                ", activeSessionId=" + activeSessionId +
                ", deviceMatches=true" +
                ", hasWebSocketInMemory=" + hasWebSocketInMemory +
                ", isValid=true (no timeout)");

        return true;
    }

    /**
     * Проверка, есть ли у пользователя активная сессия (без проверки deviceId)
     */
    @Transactional
    public boolean hasActiveSession(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        return user.getActiveSessionId() != null && webSocketService.hasSession(userId);
    }

    /**
     * Проверка, занят ли аккаунт (есть ли активная сессия)
     */
    @Transactional
    public boolean isAccountOccupied(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();

        boolean hasWebSocket = webSocketService.hasSession(userId);
        boolean hasAnySession = webSocketService.hasAnySession(userId);

        return hasWebSocket || hasAnySession;
    }

    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }
}
