package com.example.demo.controller;

import com.example.demo.service.ChatGeminiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Controller
public class AiChatController {

    @GetMapping("/ai_chat")
    public String chat() {
        return "forward:html/ai_chat.html";
    }

    private static final Logger logger = LoggerFactory.getLogger(AiChatController.class);

    @Autowired
    private ChatGeminiService chatGeminiService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Map<String, Map<String, String>> userModelPersonalities = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> userChatHistories = new ConcurrentHashMap<>();
    private static final String MODEL1_ID = "model1";
    private static final String MODEL2_ID = "model2";

    // 대화 시작 처리
    @MessageMapping("/start-ai-chat")
    @SendToUser("/queue/ai-messages")
    public AiOutputMessage startAiChat(Message message, StompHeaderAccessor accessor) throws Exception {
        String sessionId = accessor.getSessionId(); // 세션 ID를 사용자로 간주
        String userName = sessionId;
        logger.info("startAiChat - Session ID: {}, User: {}", sessionId, userName);

        HttpSession httpSession = (HttpSession) accessor.getSessionAttributes().get("HTTP.SESSION");
        String token = (String) httpSession.getAttribute("accessToken");
        String modelName = "gemini-2.0-flash-exp";

        if (token == null) {
            handleTokenError(httpSession);
            return new AiOutputMessage("세션이 만료되었습니다. 다시 로그인 해주세요.", null);
        }

        // 사용자별 성격 설정 가져오기
        Map<String, String> modelPersonalities = userModelPersonalities.computeIfAbsent(userName, k -> new HashMap<>());
        String model1Personality = modelPersonalities.get(MODEL1_ID);
        String model2Personality = modelPersonalities.get(MODEL2_ID);
        if (model1Personality == null || model2Personality == null) {
            return new AiOutputMessage("모델 성격을 먼저 설정해주세요.", null);
        }

        // 사용자별 대화 기록 초기화
        List<Map<String, Object>> chatHistory = userChatHistories.computeIfAbsent(userName, k -> new ArrayList<>());
        chatHistory.clear();

        // 모델1 응답 생성
        String model1Response = generateModelResponse(
                MODEL1_ID,
                model1Personality + " 자유롭게 대화를 시작해보세요.",
                modelName,
                token
        );
        chatHistory.add(createHistoryEntry("모델 1: " + model1Response));

        // 0.5초 대기
        TimeUnit.MILLISECONDS.sleep(500);

        // 모델2 응답 생성
        String model2Response = generateModelResponse(
                MODEL2_ID,
                model2Personality + " 모델1의 발언: " + model1Response,
                modelName,
                token
        );
        chatHistory.add(createHistoryEntry("모델 2: " + model2Response));

        // 사용자별 대화 기록 저장
        userChatHistories.put(userName, chatHistory);

        return new AiOutputMessage(model1Response, model2Response);
    }

    // 대화 계속 처리
    @MessageMapping("/continue-ai-chat")
    @SendToUser("/queue/ai-messages")
    public AiOutputMessage continueAiChat(Message message, StompHeaderAccessor accessor) throws InterruptedException {
        String sessionId = accessor.getSessionId(); // 세션 ID를 사용자로 간주
        String userName = sessionId;
        logger.info("continueAiChat - Session ID: {}, User: {}", sessionId, userName);

        HttpSession httpSession = (HttpSession) accessor.getSessionAttributes().get("HTTP.SESSION");
        String token = (String) httpSession.getAttribute("accessToken");
        String modelName = "gemini-2.0-pro-exp-02-05";

        try {
            if (token == null) {
                handleTokenError(httpSession);
                return new AiOutputMessage("세션이 만료되었습니다. 다시 로그인 해주세요.", null);
            }

            // 사용자별 대화 기록 가져오기
            List<Map<String, Object>> chatHistory = userChatHistories.get(userName);
            if (chatHistory == null || chatHistory.isEmpty()) {
                return new AiOutputMessage("대화를 먼저 시작해주세요.", null);
            }

            Map<String, Object> lastEntry = chatHistory.get(chatHistory.size() - 1);
            List<Map<String, String>> parts = (List<Map<String, String>>) lastEntry.get("parts");
            String lastMessage = parts.get(0).get("text");

            boolean isLastFromModel1 = lastMessage.startsWith("모델 1:");
            String firstModelId = isLastFromModel1 ? MODEL2_ID : MODEL1_ID;
            String firstResponse = processModelResponse(firstModelId, lastMessage, modelName, token, userName);

            // 0.5초 대기
            TimeUnit.MILLISECONDS.sleep(500);

            String secondModelId = firstModelId.equals(MODEL1_ID) ? MODEL2_ID : MODEL1_ID;
            String secondResponse = processModelResponse(secondModelId, firstResponse, modelName, token, userName);

            // 사용자별 대화 기록 저장 (마지막에 저장)
            userChatHistories.put(userName, chatHistory);

            // 두 모델의 응답을 모두 포함한 AiOutputMessage 객체 생성 및 반환
            return new AiOutputMessage(firstResponse, secondResponse);

        } catch (Exception e) {
            logger.error("대화 계속 중 오류: {}", e.getMessage(), e);
            return new AiOutputMessage("AI 응답 생성에 실패했습니다.", null);
        }
    }

    // 공통 유틸리티 메서드들
    private String generateModelResponse(String modelId, String prompt, String modelName, String token) throws Exception {
        return chatGeminiService.generateContent(
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                modelName,
                token
        );
    }

    private String processModelResponse(String modelId, String lastMessage, String modelName, String token, String userName)
            throws Exception {
        // 사용자별 성격 설정 가져오기
        Map<String, String> modelPersonalities = userModelPersonalities.get(userName);
        String personality = modelPersonalities.get(modelId);
        if (personality == null) {
            throw new IllegalStateException("모델 성격 설정 오류");
        }

        String cleanMessage = lastMessage.replaceAll("^모델 [12]:\\s*", "").trim();
        String prompt = String.format("[%s 성격] %s. 다음 발언에 답변: %s",
                modelId, personality, cleanMessage);

        String response = chatGeminiService.generateContent(
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                modelName,
                token
        );

        String formattedResponse = String.format("모델 %s: %s",
                modelId.equals(MODEL1_ID) ? "1" : "2", response);

        // 사용자별 대화 기록 가져오기
        List<Map<String, Object>> chatHistory = userChatHistories.get(userName);
        chatHistory.add(createHistoryEntry(formattedResponse));

        return response;
    }

    private Map<String, Object> createHistoryEntry(String message) {
        return Map.of(
                "role", "assistant",
                "parts", List.of(Map.of("text", message))
        );
    }

    private void handleTokenError(HttpSession session) {
        logger.error("유효하지 않은 세션");
        if (session != null) session.invalidate();
    }

    // 성격 설정 처리
    @MessageMapping("/set-personality")
    @SendToUser("/queue/personality-updates")
    public String setPersonality(PersonalityMessage message, StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId(); // 세션 ID를 사용자로 간주
        String userName = sessionId; // Principal 대신 sessionId 사용

        Map<String, String> modelPersonalities = userModelPersonalities.computeIfAbsent(userName, k -> new HashMap<>());
        modelPersonalities.put(message.getModelId(), message.getPersonality());
        return String.format("모델 %s 성격 설정 완료", message.getModelId());
    }

    // 내부 클래스: 메시지 구조
    public static class Message {
        private String text;
        private String token;

        // Getter and Setter
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    // 내부 클래스: 출력 메시지 구조 (Lombok, @ToString 사용 안 함)
    public static class AiOutputMessage {
        private String model1Response;
        private String model2Response;

        public AiOutputMessage(String model1Response, String model2Response) {
            this.model1Response = model1Response;
            this.model2Response = model2Response;
        }

        public AiOutputMessage(String errorMessage, Object o) {
            this.model1Response = errorMessage;
            this.model2Response = null;
        }

        public String getModel1Response() {
            return model1Response;
        }

        public void setModel1Response(String model1Response) {
            this.model1Response = model1Response;
        }

        public String getModel2Response() {
            return model2Response;
        }

        public void setModel2Response(String model2Response) {
            this.model2Response = model2Response;
        }

        @Override
        public String toString() {
            return "AiOutputMessage{" +
                    "model1Response='" + model1Response + '\'' +
                    ", model2Response='" + model2Response + '\'' +
                    '}';
        }
    }

    // 내부 클래스: 성격 설정 메시지 구조
    public static class PersonalityMessage {
        private String modelId;
        private String personality;

        // Getter and Setter
        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public String getPersonality() {
            return personality;
        }

        public void setPersonality(String personality) {
            this.personality = personality;
        }
    }
}