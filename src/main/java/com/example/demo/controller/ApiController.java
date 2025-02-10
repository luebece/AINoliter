package com.example.demo.controller;

import com.example.demo.service.GoogleTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final GoogleTokenService googleTokenService;

    @Autowired
    public ApiController(GoogleTokenService googleTokenService) {
        this.googleTokenService = googleTokenService;
    }

    @GetMapping("/get-access-token")
    public ResponseEntity<Map<String, String>> getAccessToken(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String accessToken = (String) session.getAttribute("accessToken");

        if (accessToken == null) {
            try {
                accessToken = googleTokenService.getAccessToken();
                session.setAttribute("accessToken", accessToken);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Collections.singletonMap("error", "Failed to generate access token"));
            }
        }

        return ResponseEntity.ok(Collections.singletonMap("accessToken", accessToken));
    }
}