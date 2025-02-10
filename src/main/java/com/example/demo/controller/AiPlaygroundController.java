package com.example.demo.controller;

import com.example.demo.service.GoogleTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;
import java.util.Map;

@Controller
@RequestMapping("/")
public class AiPlaygroundController {

    private static final Logger logger = LoggerFactory.getLogger(AiPlaygroundController.class);

    private final GoogleTokenService googleTokenService;

    public AiPlaygroundController(GoogleTokenService googleTokenService) {
        this.googleTokenService = googleTokenService;
    }

    @GetMapping("/")
    public String aiPlayground(HttpServletRequest request) {
        HttpSession session = request.getSession();
        try {
            String token = googleTokenService.getAccessToken();
            session.setAttribute("accessToken", token);
            logger.info("New access token generated and stored in session: {}", token);
            // 세션에 토큰이 없거나 만료된 경우 새로 발급
            if (session.getAttribute("accessToken") == null) {
            } else {
                logger.info("Access token found in session.");
            }
        } catch (Exception e) {
            logger.error("Error generating access token", e);
            // 토큰 발급 실패 시, 세션 무효화 및 로그인 페이지로 리다이렉트
            session.invalidate();
            return "redirect:/oauth2/authorization/google";
        }

        return "forward:/html/ai_playground.html";
    }

    @GetMapping("/game")
    public String game(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession();

        // 세션에 accessToken이 없는 경우, 새로 발급받아 session과 model에 추가
        if (session.getAttribute("accessToken") == null) {
            try {
                String token = googleTokenService.getAccessToken();
                session.setAttribute("accessToken", token);
                model.addAttribute("accessToken", token);
                logger.info("New access token generated and stored in session and model: {}", token);
            } catch (Exception e) {
                logger.error("Error generating access token", e);
                // 토큰 발급 실패 시, 세션 무효화 및 로그인 페이지로 리다이렉트
                session.invalidate();
                return "redirect:/oauth2/authorization/google";
            }
        } else {
            // 세션에 accessToken이 존재하는 경우, model에 추가
            String accessToken = (String) session.getAttribute("accessToken");
            model.addAttribute("accessToken", accessToken);
            logger.info("accessToken added to model: {}", accessToken);
        }

        // 모델에 추가된 accessToken 로그 출력
        logger.info("Model attributes: {}", model.asMap());

        return "forward:/html/game.html"; // game.html을 렌더링
    }



    // 토큰 발급 API
    @GetMapping("/api/get-token")
    @ResponseBody
    public Map<String, String> getToken() {
        try {
            String token = googleTokenService.getAccessToken();
            return Collections.singletonMap("token", token);
        } catch (Exception e) {
            logger.error("Error generating access token", e);
            return Collections.singletonMap("error", "Error generating access token: " + e.getMessage());
        }
    }
}