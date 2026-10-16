package com.example.audiobackend.service;

import org.springframework.stereotype.Service;

@Service
public interface ChatService {
    void chat(String prompt, String visitorId);
}
