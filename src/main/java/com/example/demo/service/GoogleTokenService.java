package com.example.demo.service;

import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

@Service
public class GoogleTokenService {

    @Value("${google.application.credentials}")
    private String keyFilePath; // String 타입으로 변경
    private GoogleCredentials credentials;

    @PostConstruct
    public void init() throws IOException {
        // 클래스패스 리소스로 파일 로드
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(keyFilePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource file not found: " + keyFilePath);
        }

        // InputStream을 사용해 GoogleCredentials 생성
        credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
    }

    public String getAccessToken() throws IOException {
        // 액세스 토큰 발급
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}