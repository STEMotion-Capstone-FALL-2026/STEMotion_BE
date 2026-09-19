package com.stemotion.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.dto.AiSegmentationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final RestTemplate geminiRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private static final String SEGMENTATION_PROMPT = """
        You are a STEM video script segmentation expert.
        Split the following script into individual scenes for a short educational video clip.
        
        For each scene, provide:
        - narrationText: the narration content for that scene
        - suggestedDuration: duration in seconds (integer, typically 5-15)
        - templateType: one of [TITLE, EXPLANATION, DIAGRAM, CODE, QUIZ, SUMMARY]
        
        Return ONLY a valid JSON object with this exact format:
        {
          "scenes": [
            {
              "narrationText": "...",
              "suggestedDuration": 10,
              "templateType": "EXPLANATION"
            }
          ]
        }
        
        Script to segment:
        """;

    public AiSegmentationResponse segmentScript(String rawScript) {
        // Fallback for local testing without API Key
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("GEMINI_API_KEY is empty. Returning mock segmentation response.");
            return AiSegmentationResponse.builder()
                .scenes(List.of(
                    AiSegmentationResponse.SceneSegment.builder()
                        .narrationText("Xin chào, đây là đoạn mock từ Backend do thiếu API Key.")
                        .suggestedDuration(5)
                        .templateType("TITLE")
                        .build(),
                    AiSegmentationResponse.SceneSegment.builder()
                        .narrationText("Nội dung kịch bản gốc: " + rawScript.substring(0, Math.min(rawScript.length(), 20)) + "...")
                        .suggestedDuration(10)
                        .templateType("EXPLANATION")
                        .build()
                ))
                .build();
        }

        String fullPrompt = SEGMENTATION_PROMPT + rawScript;

        Map<String, Object> payload = buildPayload(fullPrompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        String requestUrl = apiUrl + "?key=" + apiKey;

        try {
            log.info("Sending segmentation request to Gemini API");
            ResponseEntity<String> response = geminiRestTemplate.postForEntity(
                requestUrl, request, String.class
            );

            return parseGeminiResponse(response.getBody());

        } catch (RestClientException ex) {
            log.error("Gemini API call failed: {}", ex.getMessage());
            throw new ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "AI_SERVICE_ERROR",
                "Failed to connect to AI service: " + ex.getMessage()
            );
        }
    }

    private Map<String, Object> buildPayload(String prompt) {
        return Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.3
            )
        );
    }

    private AiSegmentationResponse parseGeminiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty() || candidates.isMissingNode()) {
                throw ApiException.badRequest("Gemini returned no candidates");
            }

            String generatedText = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

            return objectMapper.readValue(generatedText, AiSegmentationResponse.class);

        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Gemini response: {}", ex.getMessage());
            throw new ApiException(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "AI_PARSE_ERROR",
                "Failed to parse AI response into scene segments"
            );
        }
    }
}
