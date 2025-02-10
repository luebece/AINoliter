package com.example.demo.utils;

import com.example.demo.controller.ChatController;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class ChatUtils {

    /**
     * 대화 기록에 사용자 메시지를 추가합니다.
     *
     * @param history     대화 기록 리스트
     * @param userMessage 사용자 메시지
     */
    public static void addUserMessageToHistory(List<Map<String, Object>> history, String userMessage) {
        Map<String, Object> userMessageMap = Map.of(
                "role", "user", // 역할: 사용자
                "parts", List.of(Map.of("text", userMessage)) // 메시지 내용
        );
        history.add(userMessageMap);
    }

    /**
     * 대화 기록에 Gemini 응답을 추가합니다.
     *
     * @param history          대화 기록 리스트
     * @param assistantMessage Gemini 응답 메시지
     */
    public static void addAssistantMessageToHistory(List<Map<String, Object>> history, String assistantMessage) {
        Map<String, Object> assistantMessageMap = Map.of(
                "role", "model", // 역할: 모델 (Gemini)
                "parts", List.of(Map.of("text", assistantMessage)) // 메시지 내용
        );
        history.add(assistantMessageMap);
    }

    /**
     * Gemini API의 응답에서 텍스트를 추출합니다.
     *
     * @param geminiResponse Gemini API의 응답 (JSON 형식)
     * @return 추출된 텍스트
     * @throws JsonProcessingException JSON 파싱 중 오류 발생 시
     */
    public static String extractTextFromGeminiResponse(String geminiResponse) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        StringBuilder textContent = new StringBuilder();

        try {
            // JSON 배열로 파싱
            List<Map<String, Object>> responseList = objectMapper.readValue(geminiResponse, new TypeReference<>() {});

            for (Map<String, Object> responseMap : responseList) {
                if (responseMap.containsKey("candidates")) {
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                    for (Map<String, Object> candidate : candidates) {
                        if (candidate.containsKey("content")) {
                            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                            if (content.containsKey("parts")) {
                                List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                                for (Map<String, String> part : parts) {
                                    if (part.containsKey("text")) {
                                        String text = part.get("text");
                                        textContent.append(text).append(" ");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing Gemini response", e);
        }

        return textContent.toString().trim();
    }

    /**
     * Gemini API의 응답에서 완전한 텍스트를 추출합니다.
     * (여러 청크로 나뉜 응답을 하나로 합칩니다.)
     *
     * @param responseList Gemini API의 응답 리스트
     * @return 완전한 텍스트
     * @throws JsonProcessingException JSON 파싱 중 오류 발생 시
     */
    public static String extractCompleteResponse(List<Map<String, Object>> responseList) throws JsonProcessingException {
        StringBuilder completeResponse = new StringBuilder();

        for (Map<String, Object> responseMap : responseList) {
            if (responseMap.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                for (Map<String, Object> candidate : candidates) {
                    if (candidate.containsKey("content")) {
                        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                        if (content.containsKey("parts")) {
                            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                            for (Map<String, String> part : parts) {
                                if (part.containsKey("text")) {
                                    String text = part.get("text").trim();
                                    if (!text.isEmpty()) {
                                        completeResponse.append(text).append(" ");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return completeResponse.toString().trim(); // 여기에 return 문이 누락되어 있었습니다.
    }

    public static String extractTextFromOutputMessageList(List<ChatController.OutputMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatController.OutputMessage message : messages) {
            if (message.getGeminiResponse() != null) {
                sb.append(message.getGeminiResponse()).append("\n");
            }
        }
        return sb.toString().trim();
    }
}