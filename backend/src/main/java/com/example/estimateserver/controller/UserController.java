package com.example.estimateserver.controller;

import com.example.estimateserver.model.User;
import com.example.estimateserver.service.UserService;
import com.example.estimateserver.service.WebSocketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final WebSocketService webSocketService;

    public UserController(UserService userService, WebSocketService webSocketService) {
        this.userService = userService;
        this.webSocketService = webSocketService;
    }

    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Phone number is required"));
            }
            userService.sendVerificationCode(phoneNumber);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/session/check")
    public ResponseEntity<Map<String, Boolean>> checkSession(@RequestHeader("X-User-Id") String userId) {
        Optional<User> userOpt = userService.findById(userId);
        boolean isValid = userOpt.isPresent() && userOpt.get().getActiveSessionId() != null;
        return ResponseEntity.ok(Map.of("isValid", isValid));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");
            String code = request.get("code");
            String deviceId = request.get("deviceId");

            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Phone number is required"));
            }

            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Code is required"));
            }

            // Пытаемся выполнить вход
            User user = userService.verifyCode(phoneNumber, code, deviceId);
            return ResponseEntity.ok(user);

        } catch (RuntimeException e) {
            String message = e.getMessage();

            // 409 Conflict - аккаунт уже используется на другом устройстве (ОНЛАЙН)
            if (message.equals("Account already in use on another device")) {
                System.err.println("❌ LOGIN REJECTED: " + message);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", message));
            }

            // 400 Bad Request - неверный код, истекший код, пользователь не найден
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-User-Id") String userId) {
        userService.logout(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<User> getUser(@PathVariable String userId) {
        return userService.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Server is working!");
    }

    @GetMapping("/session/check-with-device")
    public ResponseEntity<Map<String, Boolean>> checkSessionWithDevice(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("deviceId") String deviceId
    ) {
        System.out.println("=== SESSION CHECK WITH DEVICE ===");
        System.out.println("userId: " + userId);
        System.out.println("deviceId from request: " + deviceId);

        Optional<User> userOpt = userService.findById(userId);

        if (userOpt.isEmpty()) {
            System.out.println("❌ User not found!");
            return ResponseEntity.ok(Map.of("isValid", false));
        }

        User user = userOpt.get();
        String activeSessionId = user.getActiveSessionId();

        System.out.println("activeSessionId in DB: " + activeSessionId);
        System.out.println("deviceId equals? " + (activeSessionId != null && activeSessionId.equals(deviceId)));

        boolean hasWebSocket = webSocketService.hasSession(userId);
        System.out.println("hasWebSocket: " + hasWebSocket);

        boolean isValid = activeSessionId != null &&
                activeSessionId.equals(deviceId) &&
                hasWebSocket;

        System.out.println("FINAL isValid: " + isValid);
        System.out.println("=================================");

        return ResponseEntity.ok(Map.of("isValid", isValid));
    }
}