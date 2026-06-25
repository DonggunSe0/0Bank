package com.team10.backend.domain.youngPolicy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class GeminiChatService {

    private final RestClient restClient;
    private final String apiKey;

    @Value("classpath:prompts/fallback-response.txt")
    private Resource fallbackResponseResource;

    private String fallbackResponse;

    public GeminiChatService(
            RestClient restClient,
            @Value("${gemini.api-key:}") String apiKey
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void init() {
        try {
            this.fallbackResponse = StreamUtils.copyToString(
                    fallbackResponseResource.getInputStream(),
                    StandardCharsets.UTF_8
            );
            log.info("Successfully loaded Gemini fallback response message from resources.");
        } catch (IOException e) {
            log.error("Failed to load Gemini fallback response message from resources.", e);
            throw new IllegalStateException("Gemini fallback response file is missing or unreadable.", e);
        }
    }

    public String generateContent(String systemInstruction, String promptText) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is missing. Returning fallback mock response.");
            return generateFallbackResponse();
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
            String fullPrompt = systemInstruction + "\n\n[사용자 요청 내용]\n" + promptText;
            GeminiRequest requestBody = new GeminiRequest(List.of(new Content(List.of(new Part(fullPrompt)))));

            GeminiResponse response = restClient.post()
                    .uri(url)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            String result = extractText(response);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("Gemini API call failed", e);
        }

        return generateFallbackResponse();
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
            return null;
        }
        return candidate.content().parts().get(0).text();
    }

    private String generateFallbackResponse() {
        return this.fallbackResponse;
    }

    // Gemini API Request/Response DTOs
    private record GeminiRequest(List<Content> contents) {}
    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
}
