package com.example.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ChatGeminiService {

    @Value("${gemini.api.endpoint}")
    private String apiEndpoint;

    @Value("${gcp.project.id}")
    private String projectId;

    private static final Logger logger = LoggerFactory.getLogger(ChatGeminiService.class);

    public String generateContent(List<Map<String, Object>> conversationHistory, String modelName, String token) throws JsonProcessingException {
        String url = "https://" + apiEndpoint + "/v1/projects/" + projectId + "/locations/us-central1/publishers/google/models/" + modelName + ":streamGenerateContent";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Content-Type", "application/json");

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> requestBodyMap = Map.of("contents", conversationHistory);
        String requestBody = objectMapper.writeValueAsString(requestBodyMap);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        logger.info("Calling Gemini API with URL: {}", url);
        logger.info("Request Body: {}", requestBody);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            logger.info("Gemini API call successful. Response Status: {}", response.getStatusCode());
            logger.info("Response Body: {}", response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                // 스트리밍 응답을 하나로 합치기 (GameGeminiService와 동일한 로직)
                String responseBody = response.getBody();
                List<Map<String, Object>> responseList = objectMapper.readValue(responseBody, new TypeReference<>() {
                });

                StringBuilder completeResponse = new StringBuilder();
                for (Map<String, Object> responseMap : responseList) {
                    if (responseMap.containsKey("candidates")) {
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                        for (Map<String, Object> candidate : candidates) {
                            if (candidate.containsKey("finishReason")) {
                                logger.info("finishReason: {}", candidate.get("finishReason"));
                            }
                            if (candidate.containsKey("content")) {
                                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                                if (content.containsKey("parts")) {
                                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                                    for (Map<String, String> part : parts) {
                                        if (part.containsKey("text")) {
                                            String text = part.get("text");
                                            if (text != null && !text.trim().isEmpty()) {
                                                completeResponse.append(text).append(" ");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                return completeResponse.toString().trim();
            } else {
                logger.error("Gemini API 호출 실패: {} {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Gemini API 호출 실패: " + response.getStatusCode() + " " + response.getBody());
            }
        } catch (Exception e) {
            logger.error("Error calling Gemini API", e);
            throw new RuntimeException("Error calling Gemini API", e);
        }
    }
}