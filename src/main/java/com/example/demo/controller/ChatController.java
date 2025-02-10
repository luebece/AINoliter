package com.example.demo.controller;

import com.example.demo.service.ChatGeminiService;
import com.example.demo.utils.ChatUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class ChatController {

    @GetMapping("/chat")
    public String chat() {
        return "forward:html/chat.html";
    }

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatGeminiService chatGeminiService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 사용자별 대화 기록을 저장하기 위한 맵
    private final Map<String, List<Map<String, Object>>> userChatHistories = new ConcurrentHashMap<>();
    private final Map<String, String> userCurrentModelNames = new ConcurrentHashMap<>();

    @MessageMapping("/chat")
    @SendToUser("/queue/messages")
    public ChatController.OutputMessage handleChat(ChatController.Message message, StompHeaderAccessor accessor) throws Exception {
        String userMessage = message.getText();
        String modelName = message.getModelName();

        // StompHeaderAccessor에서 세션 ID 가져오기
        String sessionId = accessor.getSessionId();

        // WebSocketSession에서 HttpSession 가져오기
        HttpSession httpSession = (HttpSession) accessor.getSessionAttributes().get("HTTP.SESSION");

        // 세션 ID와 사용자 이름을 로깅
        logger.info("handleChat - Session ID: {}, User: {}", sessionId, sessionId);

        // 세션에서 토큰 가져오기
        String token = (String) httpSession.getAttribute("accessToken");

        // 토큰이 없는 경우 에러 처리
        if (token == null) {
            logger.error("Access token not found in session.");
            httpSession.invalidate();
            return new ChatController.OutputMessage(userMessage, "Access token not found. Please login again.", sessionId); // sessionId 추가
        }

        // 사용자별 대화 기록 및 현재 모델 이름 가져오기
        List<Map<String, Object>> chatHistory = userChatHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
        String currentModelName = userCurrentModelNames.getOrDefault(sessionId, "");

        String geminiResponse = "";

        // 모델이 변경된 경우 대화 기록 초기화
        if (!modelName.equals(currentModelName)) {
            chatHistory.clear();
            userCurrentModelNames.put(sessionId, modelName);
        }

        try {
            // 대화 기록에 사용자 메시지 추가
            ChatUtils.addUserMessageToHistory(chatHistory, userMessage);

            // Gemini API 호출
            geminiResponse = chatGeminiService.generateContent(chatHistory, modelName, token);
            if (geminiResponse == null) {
                throw new RuntimeException("Gemini API returned null response.");
            }

            // 대화 기록에 Gemini 응답 추가
            ChatUtils.addAssistantMessageToHistory(chatHistory, geminiResponse);

            // 사용자 메시지와 Gemini 응답을 함께 반환 (sessionId 추가)
            userChatHistories.put(sessionId, chatHistory);
            return new ChatController.OutputMessage(userMessage, geminiResponse, sessionId); // sessionId 추가

        } catch (Exception e) {
            logger.error("Error generating content", e);

            // 에러 메시지를 클라이언트에게 전송
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", Map.of(
                    "error", "Gemini API 호출 중 오류가 발생했습니다.",
                    "details", e.getMessage()
            ));

            // 에러 발생 시 사용자 메시지만 반환 (sessionId 추가)
            return new ChatController.OutputMessage(userMessage, "죄송합니다. 메시지 생성에 실패했습니다.", sessionId); // sessionId 추가
        }
    }

    // 내부 클래스: 메시지 구조
    public static class Message {
        private String text;
        private String modelName;
        private String token;

        // Constructor
        public Message() {}

        public Message(String text, String modelName, String token) {
            this.text = text;
            this.modelName = modelName;
            this.token = token;
        }

        // Getter and Setter
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    // 내부 클래스: 출력 메시지 구조
    public static class OutputMessage {
        private String text;
        private String geminiResponse;
        private String userName; // sessionId

        public OutputMessage(String text, String geminiResponse, String userName) {
            this.text = text;
            this.geminiResponse = geminiResponse;
            this.userName = userName;
        }

        // Getter and Setter
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getGeminiResponse() {
            return geminiResponse;
        }

        public void setGeminiResponse(String geminiResponse) {
            this.geminiResponse = geminiResponse;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }
}