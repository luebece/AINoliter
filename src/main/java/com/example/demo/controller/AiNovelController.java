package com.example.demo.controller;

import com.example.demo.service.ChatGeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class AiNovelController {

    @Autowired
    private ChatGeminiService chatGeminiService;

    @GetMapping("/ai_novel")
    public String novelAi() {
        return "forward:/html/ai_novel.html";
    }

    // 사용자별 대화 기록을 저장하기 위한 맵
    private final Map<String, List<Map<String, Object>>> userConversationHistories = new ConcurrentHashMap<>();

    @MessageMapping("/novel-input")
    @SendToUser("/queue/novel-updates")
    public AiNovelController.StoryUpdate handleNovelInput(AiNovelController.Message message, StompHeaderAccessor accessor) throws Exception {
        String userInput = message.getText();
        String modelName = "gemini-2.0-pro-exp-02-05"; // 사용할 모델 이름
        String sessionId = accessor.getSessionId(); // 세션 ID 가져오기

        // WebSocketSession에서 HttpSession 가져오기
        HttpSession httpSession = (HttpSession) accessor.getSessionAttributes().get("HTTP.SESSION");
        String token = (String) httpSession.getAttribute("accessToken");

        if (token == null) {
            return new AiNovelController.StoryUpdate("로그인이 필요합니다. 다시 로그인해주세요.", true);
        }

        // 사용자별 대화 기록 가져오기
        List<Map<String, Object>> conversationHistory = userConversationHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // 사용자 입력이 주제 선택인 경우
        if (message.isTopicSelection()) {
            // 대화 기록 초기화
            conversationHistory.clear();

            // 사용자 입력(initialPrompt)을 대화 기록에 추가
            Map<String, Object> initialMessage = new HashMap<>();
            initialMessage.put("role", "user");
            initialMessage.put("parts", List.of(Map.of("text", userInput)));
            conversationHistory.add(initialMessage);

            // Gemini API를 호출하여 초기 스토리 생성
            String storyResponse = chatGeminiService.generateContent(conversationHistory, modelName, token);

            // Gemini의 응답을 대화 기록에 추가
            Map<String, Object> assistantMessage = new HashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("parts", List.of(Map.of("text", storyResponse)));
            conversationHistory.add(assistantMessage);

            // 사용자별 대화 기록 저장
            userConversationHistories.put(sessionId, conversationHistory);

            // Gemini의 응답을 클라이언트로 전송
            return new AiNovelController.StoryUpdate(storyResponse, false);
        }

        // 일반 사용자 입력인 경우
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("parts", List.of(Map.of("text", userInput)));
        conversationHistory.add(userMessage);

        // Gemini API를 호출하여 스토리 생성
        String storyResponse = chatGeminiService.generateContent(conversationHistory, modelName, token);

        // Gemini의 응답을 대화 기록에 추가
        Map<String, Object> assistantMessage = new HashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("parts", List.of(Map.of("text", storyResponse)));
        conversationHistory.add(assistantMessage);

        // 사용자별 대화 기록 저장
        userConversationHistories.put(sessionId, conversationHistory);

        return new AiNovelController.StoryUpdate(storyResponse, false);
    }

    // 메시지 클래스
    public static class Message {
        private String text;
        private boolean isTopicSelection;

        public Message() {}

        public Message(String text, boolean isTopicSelection) {
            this.text = text;
            this.isTopicSelection = isTopicSelection;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isTopicSelection() {
            return isTopicSelection;
        }

        public void setTopicSelection(boolean topicSelection) {
            isTopicSelection = topicSelection;
        }
    }

    // 스토리 업데이트 클래스
    public static class StoryUpdate {
        private String text;
        private boolean isUserMessage;

        public StoryUpdate(String text, boolean isUserMessage) {
            this.text = text;
            this.isUserMessage = isUserMessage;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isUserMessage() {
            return isUserMessage;
        }

        public void setUserMessage(boolean userMessage) {
            isUserMessage = userMessage;
        }
    }
}