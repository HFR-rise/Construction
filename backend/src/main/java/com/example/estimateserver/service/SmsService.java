package com.example.estimateserver.service;

import org.springframework.stereotype.Service;

@Service
public class SmsService {

    public void sendCode(String phoneNumber, String code) {
        System.out.println("=====================================");
        System.out.println("SMS to " + phoneNumber + ": Your code is " + code);
        System.out.println("=====================================");
    }
}