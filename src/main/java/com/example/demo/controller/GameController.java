package com.example.demo.controller;

import com.example.demo.service.GameGeminiService;
import com.example.demo.utils.ChatUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/game")
public class GameController {

    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    private static final int DEFAULT_MAX_CHOICES = 10;
    private static final int MAX_HISTORY_SIZE = 10; // 최대 대화 기록 길이

    @Autowired
    private GameGeminiService geminiService;

    @Value("${gcp.project.id}")
    private String projectId;

    // 게임 상태를 나타내는 enum
    private enum GameState {
        START, // 게임 시작 전
        NAME_INPUT, // 이름 입력
        IN_GAME, // 게임 진행 중
        END // 게임 종료
    }

    // 사용자별 게임 상태를 저장하는 맵 (세션 ID -> GameState)
    private final Map<String, GameState> userGameStates = new ConcurrentHashMap<>();
    // 사용자별 게임 대화 기록을 저장하는 맵 (세션 ID -> 대화 기록)
    private final Map<String, List<Map<String, Object>>> userHistories = new ConcurrentHashMap<>();
    // 사용자별 선택 횟수를 저장하는 맵 (세션 ID -> 선택 횟수)
    private final Map<String, Integer> userChoiceCounts = new ConcurrentHashMap<>();
    // 사용자별 최대 선택 횟수를 저장하는 맵 (세션 ID -> 최대 선택 횟수)
    private final Map<String, Integer> userMaxChoices = new ConcurrentHashMap<>();

    /**
     * 최대 선택 횟수를 설정합니다.
     *
     * @param max     최대 선택 횟수 (1~50)
     * @param session HttpSession 객체
     * @return 설정 결과 메시지
     */
    @PostMapping("/set-max-choices")
    public ResponseEntity<String> setMaxChoices(@RequestParam int max, HttpSession session) {
        String sessionId = session.getId();
        if (max < 1 || max > 50) {
            return ResponseEntity.badRequest().body("1~50 사이의 값을 입력해주세요");
        }
        userMaxChoices.put(sessionId, max);
        logger.info("Max choices updated to: {} for session: {}", max, sessionId);
        return ResponseEntity.ok("최대 선택 횟수가 " + max + "회로 설정되었습니다");
    }

    /**
     * 게임을 초기화합니다.
     *
     * @param session HttpSession 객체
     * @return 초기화 결과 메시지
     */
    @PostMapping("/reset")
    @ResponseBody
    public ResponseEntity<String> resetGame(HttpSession session) {
        String sessionId = session.getId();
        userGameStates.put(sessionId, GameState.START);
        userHistories.remove(sessionId); // 해당 세션의 대화 기록 제거
        userChoiceCounts.remove(sessionId); // 해당 세션의 선택 횟수 제거

        // 세션에서 최대 선택 횟수 가져오기
        Integer sessionMax = (Integer) session.getAttribute("maxChoices");
        int maxChoices = (sessionMax != null) ? sessionMax : DEFAULT_MAX_CHOICES;
        userMaxChoices.put(sessionId, maxChoices);

        logger.info("Game reset for session: {} with max choices: {}", sessionId, maxChoices);
        return ResponseEntity.ok("게임 상태 초기화 완료. 최대 선택 횟수: " + maxChoices);
    }

    /**
     * StompHeaderAccessor에서 세션 ID를 가져옵니다.
     *
     * @param accessor StompHeaderAccessor 객체
     * @return 세션 ID
     */
    private String getSessionId(StompHeaderAccessor accessor) {
        return accessor.getSessionId();
    }

    /**
     * Imagen 3 API를 호출하여 이미지를 생성합니다.
     *
     * @param prompt 이미지 생성을 위한 프롬프트
     * @param token  인증 토큰
     * @return 생성된 이미지 데이터 (Base64 인코딩)
     */
    @PostMapping("/api/generate-image")
    public ResponseEntity<String> generateImage(@RequestParam String prompt, @RequestHeader("Authorization") String token) {
        logger.info("Received token for Imagen API: {}", token);

        if (token.startsWith("Bearer ")) {
            token = token.substring(7); // "Bearer " 접두사 제거
        }

        String url = "https://us-central1-aiplatform.googleapis.com/v1/projects/" + projectId + "/locations/us-central1/publishers/google/models/imagen-3.0-fast-generate-001:predict";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Content-Type", "application/json");

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> requestBodyMap = Map.of(
                "instances", List.of(Map.of("prompt", prompt)),
                "parameters", Map.of("sampleCount", 1)
        );

        try {
            String requestBody = objectMapper.writeValueAsString(requestBodyMap);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Imagen API response: {}", response.getBody()); // 응답 로깅

                // Base64 인코딩된 이미지 데이터를 JSON 응답에 포함
                String base64ImageData = extractBase64ImageData(response.getBody());
                if (base64ImageData != null) {
                    Map<String, String> imageData = Collections.singletonMap("imageData", base64ImageData);
                    return ResponseEntity.ok(objectMapper.writeValueAsString(imageData));
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to extract image data.");
                }
            } else {
                logger.error("Imagen 3 API 호출 실패: {} {}", response.getStatusCode(), response.getBody());
                return ResponseEntity.status(response.getStatusCode()).body("Imagen 3 API 호출 실패: " + response.getBody());
            }
        } catch (Exception e) {
            logger.error("Error calling Imagen 3 API", e);
            return ResponseEntity.status(500).body("Imagen 3 API 호출 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * JSON 응답에서 Base64 인코딩된 이미지 데이터를 추출합니다.
     *
     * @param responseBody Imagen API의 응답 본문
     * @return Base64 인코딩된 이미지 데이터
     */
    private String extractBase64ImageData(String responseBody) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, new TypeReference<>() {
            });

            if (responseMap.containsKey("predictions")) {
                List<Map<String, Object>> predictions = (List<Map<String, Object>>) responseMap.get("predictions");
                if (!predictions.isEmpty()) {
                    Map<String, Object> prediction = predictions.get(0);
                    if (prediction.containsKey("bytesBase64Encoded")) {
                        return (String) prediction.get("bytesBase64Encoded");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting base64 image data", e);
        }

        return null;
    }

    /**
     * 게임 진행 메시지를 처리합니다.
     *
     * @param message  사용자가 보낸 메시지
     * @param accessor StompHeaderAccessor 객체
     * @return Gemini API의 응답 메시지
     * @throws Exception
     */
    @MessageMapping("/game")
    @SendToUser("/queue/game")
    public GameController.OutputMessage handleGame(GameController.Message message, StompHeaderAccessor accessor) throws Exception {
        String userChoice = message.getText();
        String modelName = message.getModelName();
        String sessionId = getSessionId(accessor); // 세션 ID 가져오기

        // StompHeaderAccessor에서 Authorization 헤더 가져오기
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 토큰이 null인 경우 예외 처리
        if (token == null) {
            logger.error("accessToken not found in header.");
            return new GameController.OutputMessage("Error", "AccessToken not found.");
        }

        // 사용자별 게임 상태, 대화 기록, 선택 횟수 가져오기
        GameState currentState = userGameStates.getOrDefault(sessionId, GameState.START);
        List<Map<String, Object>> gameHistory = userHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
        int choiceCount = userChoiceCounts.getOrDefault(sessionId, 0);
        int maxChoices = userMaxChoices.getOrDefault(sessionId, DEFAULT_MAX_CHOICES);

        String geminiResponse = "";
        logger.info("Current choice count: {}/{} for session: {}", choiceCount, maxChoices, sessionId);

        logger.info("Received message: {} from session: {}", message, sessionId);
        logger.info("Current State: {} for session: {}", currentState, sessionId);
        logger.info("User Choice: {} for session: {}", userChoice, sessionId);
        logger.info("Game History: {} for session: {}", gameHistory, sessionId);

        switch (currentState) {
            case START:
                gameHistory.clear();
                choiceCount = 0;
                // 게임 시작 메시지 생성 (초기 프롬프트)
                String initialPrompt = "당신은 텍스트 기반 마왕을 무찌르는 이세계 모험의 NPC입니다. Python 코드를 생성하지 말고, 텍스트로만 응답하세요. 플레이어의 이름을 묻고 플레이어가 이름을 입력하면 모험을 시작하세요. 이름을 혼자 가정하지 마세요. 편하게 반말로 말하세요";
                ChatUtils.addAssistantMessageToHistory(gameHistory, initialPrompt);

                // Gemini API 호출
                geminiResponse = geminiService.generateContent(gameHistory, modelName, token);
                // 게임 상태를 이름 입력 단계로 변경
                userGameStates.put(sessionId, GameState.NAME_INPUT);
                // 선택 횟수 증가
                userChoiceCounts.put(sessionId, choiceCount + 1);
                break;

            case NAME_INPUT:
                // 사용자 이름 가져오기
                String userName = userChoice;
                // 이름 입력 단계의 대화 기록 초기화
                gameHistory.clear();
                // 이름 입력 후 다음 단계로 진행하는 프롬프트 생성
                String namePrompt = "플레이어가 이름을 입력했습니다. 이름은 다음과 같습니다: " + userName + "\n" +
                        "이 이름을 부르고, 환영 메시지를 출력하세요. 그리고 마왕을 무찌르는 이세계 모험를 시작할 수 있도록 선택지 3개를 제공하세요. 선택지는 1. 2. 3. 이런식으로 제공하세요. 편하게 반말로 말하세요.";
                ChatUtils.addAssistantMessageToHistory(gameHistory, namePrompt);

                // Gemini API 호출
                geminiResponse = geminiService.generateContent(gameHistory, modelName, token);
                // 게임 상태를 게임 진행 중 단계로 변경
                userGameStates.put(sessionId, GameState.IN_GAME);
                // 선택 횟수 증가
                userChoiceCounts.put(sessionId, choiceCount + 1);
                break;

            case IN_GAME:
                // 게임 진행 중 단계에서 사용자 입력 메시지 추가 (start 메시지가 아니고, 게임 시작 상태가 아닌 경우)
                if (!"start".equals(userChoice) && currentState != GameState.START) {
                    ChatUtils.addUserMessageToHistory(gameHistory, userChoice);
                }
                // 선택 횟수 증가
                choiceCount++;
                userChoiceCounts.put(sessionId, choiceCount);

                // 대화 기록 길이 제한 (최대 MAX_HISTORY_SIZE 개만 유지)
                if (gameHistory.size() > MAX_HISTORY_SIZE) {
                    gameHistory.remove(0);
                }

                // 마지막 사용자 선택 저장
                String lastUserChoice = userChoice;

                // 최대 선택 횟수에 도달했는지 확인
                if (choiceCount >= maxChoices) {
                    // 게임 종료 상태로 변경
                    userGameStates.put(sessionId, GameState.END);

                    // 엔딩 텍스트 생성
                    String endingPrompt = createEndingPrompt(gameHistory);
                    // 엔딩 텍스트를 대화 기록에 추가
                    gameHistory.clear();
                    ChatUtils.addAssistantMessageToHistory(gameHistory, endingPrompt);
                    logger.info("endingPrompt: {} for session: {}", endingPrompt, sessionId);
                    logger.info("gameHistory: {} for session: {}", gameHistory, sessionId);

                    // Gemini API 호출
                    geminiResponse = geminiService.generateContent(gameHistory, modelName, token);

                    // Gemini API 응답이 없거나 비어 있는 경우 기본 메시지 설정
                    if (geminiResponse == null || geminiResponse.trim().isEmpty()) {
                        logger.error("Gemini API returned empty response for ending.");
                        geminiResponse = "마왕을 무찌르는 이세계 모험이 끝났습니다. 게임을 즐겨주셔서 감사합니다!";
                    } else {
                        // 응답에 "게임이 종료되었습니다."가 없으면 추가
                        if (!geminiResponse.contains("게임이 종료되었습니다.")) {
                            geminiResponse += "\n fin";
                        }
                    }

                    // 게임 종료 후 대화 기록 초기화
                    gameHistory.clear();
                } else {
                    // 게임 진행 중일 때 대화 기록 초기화
                    gameHistory.clear();
                    // 사용자 메시지를 대화 기록에 추가
                    ChatUtils.addUserMessageToHistory(gameHistory, lastUserChoice);

                    // 스토리 진행 프롬프트 생성
                    String storyPrompt = "플레이어의 마지막 선택: " + lastUserChoice + "\n" +
                            "이 선택을 바탕으로 마왕을 무찌르는 이세계 모험 이야기를 이어가고, 새로운 선택지 3개를 제공하세요. 선택지는 1. 2. 3. 이런식으로 제공하세요. Python 코드를 생성하지 말고, 텍스트로만 응답하세요. 편하게 반말로 말하세요.";

                    // 스토 진행 프롬프트를 대화 기록에 추가
                    ChatUtils.addAssistantMessageToHistory(gameHistory, storyPrompt);

                    logger.info("storyPrompt: {} for session: {}", storyPrompt, sessionId);
                    logger.info("gameHistory: {} for session: {}", gameHistory, sessionId);

                    // Gemini API 호출
                    geminiResponse = geminiService.generateContent(gameHistory, modelName, token);

                    // Gemini API 응답이 없거나 비어 있는 경우 기본 메시지 설정
                    if (geminiResponse == null || geminiResponse.trim().isEmpty()) {
                        logger.error("Gemini API returned empty response.");
                        geminiResponse = "죄송합니다. 응답을 생성하지 못했습니다.";
                    }
                }

                // 이미지 생성을 위한 요약 프롬프트 생성
                String summarizedPrompt = summarizeForImagen(geminiResponse, modelName, token);
                logger.info("summarizedPrompt: {} for session: {}", summarizedPrompt, sessionId);

                // Gemini 응답과 요약 프롬프트를 맵에 저장
                Map<String, String> combinedResponse = new HashMap<>();
                combinedResponse.put("geminiResponse", geminiResponse);
                combinedResponse.put("summarizedPrompt", summarizedPrompt);

                // 클라이언트로 응답 메시지 전송
                return new GameController.OutputMessage(userChoice, combinedResponse);

            case END:
                // 게임 종료 상태일 때 대화 기록 초기화
                gameHistory.clear();
                // 게임 종료 메시지 생성
                geminiResponse = "게임이 종료되었습니다. 다시 시작하려면 페이지를 새로고침하세요.";
                // 게임 상태를 시작 상태로 변경
                userGameStates.put(sessionId, GameState.START);
                break;
        }

        // 클라이언트로 응답 메시지 전송
        return new GameController.OutputMessage(userChoice, geminiResponse);
    }

    /**
     * 게임 기록을 바탕으로 엔딩 텍스트를 생성하기 위한 프롬프트를 생성합니다.
     *
     * @param gameHistory 게임 대화 기록
     * @return 엔딩 텍스트 생성을 위한 프롬프트
     */
    private String createEndingPrompt(List<Map<String, Object>> gameHistory) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("지금까지의 마왕을 무찌르는 이세계 모험 내용을 바탕으로, 이 게임의 엔딩 텍스트를 생성해주세요. 편하게 반말로 말하세요. \n");

        // 사용자 선택 이력 추출
        List<String> userChoices = gameHistory.stream()
                .filter(entry -> "user".equals(entry.get("role")))
                .flatMap(entry -> ((List<Map<String, String>>) entry.get("parts")).stream())
                .map(part -> part.get("text"))
                .collect(Collectors.toList());

        // 사용자 선택 이력 추가
        if (!userChoices.isEmpty()) {
            promptBuilder.append("플레이어의 선택 이력: ");
            for (int i = 0; i < userChoices.size(); i++) {
                promptBuilder.append(userChoices.get(i));
                if (i < userChoices.size() - 1) {
                    promptBuilder.append(", ");
                }
            }
            promptBuilder.append("\n");
        }

        // 엔딩 텍스트에 작별 인사 포함
        promptBuilder.append("엔딩 텍스트는 플레이어에게 작별 인사를 하세요.");
        // Python 코드 생성 금지 지시
        promptBuilder.append("Python 코드를 생성하지 말고, 텍스트로만 응답하세요.");

        return promptBuilder.toString();
    }

    /**
     * Gemini API의 응답을 요약하여 이미지 생성에 적합한 프롬프트를 생성합니다.
     *
     * @param geminiResponse Gemini API의 응답
     * @param modelName      모델 이름
     * @param token          인증 토큰
     * @return 이미지 생성을 위한 요약 프롬프트
     * @throws Exception
     */
    private String summarizeForImagen(String geminiResponse, String modelName, String token) throws Exception {
        List<Map<String, Object>> summaryPrompt = new ArrayList<>();

        // 요약을 위한 프롬프트 생성
        ChatUtils.addUserMessageToHistory(summaryPrompt, "다음 텍스트에서 키워드 추출:");
        ChatUtils.addAssistantMessageToHistory(summaryPrompt, geminiResponse);
        ChatUtils.addUserMessageToHistory(summaryPrompt, "Based on the summary, extract 5 keywords in English for generating an image. Respond only in English, and separate keywords with commas.");

        // Gemini API 호출
        String keywordsResponse = geminiService.generateContent(summaryPrompt, modelName, token);

        // 키워드 추출
        String keywordText = keywordsResponse.replaceAll(".*:", "").trim();

        // 키워드가 없으면 기본 키워드 추가
        if (keywordText.isEmpty()) {
            keywordText = "fantasy";
        } else {
            keywordText += ", fantasy";
        }

        logger.info("Keywords for Imagen: {}", keywordText);

        return keywordText;
    }

    /**
     * 클라이언트로부터 받은 메시지를 나타내는 내부 클래스
     */
    public static class Message {
        private String text;
        private String modelName;
        private String token;

        // 기본 생성자
        public Message() {
        }

        // 매개변수가 있는 생성자
        public Message(String text, String modelName, String token) {
            this.text = text;
            this.modelName = modelName;
            this.token = token;
        }

        // Getter, Setter
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

    /**
     * 클라이언트로 보내는 응답 메시지를 나타내는 내부 클래스
     */
    public static class OutputMessage {
        private String text;
        private Object geminiResponse;

        // 기본 생성자
        public OutputMessage() {
        }

        // 매개변수가 있는 생성자
        public OutputMessage(String text, Object geminiResponse) {
            this.text = text;
            this.geminiResponse = geminiResponse;
        }

        // Getter, Setter
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Object getGeminiResponse() {
            return geminiResponse;
        }

        public void setGeminiResponse(Object geminiResponse) {
            this.geminiResponse = geminiResponse;
        }
    }
}