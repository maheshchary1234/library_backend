package com.lumenai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumenai.dto.QuizQuestionResponse;
import com.lumenai.entity.Document;
import com.lumenai.entity.Flashcard;
import com.lumenai.entity.User;
import com.lumenai.repository.DocumentRepository;
import com.lumenai.repository.FlashcardRepository;
import com.lumenai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final DocumentRepository documentRepository;
    private final FlashcardRepository flashcardRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-3.5-turbo";
    private static final int MAX_CONTEXT_CHARS = 3000;
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private boolean isAiEnabled() {
        return openAiApiKey != null && !openAiApiKey.isBlank() && !openAiApiKey.startsWith("sk-your");
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_CONTEXT_CHARS ? text.substring(0, MAX_CONTEXT_CHARS) + "..." : text;
    }

    private Document getAuthorizedDocument(Long documentId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        if (!document.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized document access");
        }
        return document;
    }

    /**
     * Calls OpenAI Chat Completions API with the given prompt.
     */
    private String callOpenAI(String systemPrompt, String userMessage) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        body.put("temperature", 0.7);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userMessage);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI API error: " + response.code() + " " + response.message());
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            return json.path("choices").path(0).path("message").path("content").asText("");
        }
    }

    // ─── Summary ────────────────────────────────────────────────────────────────

    public String generateSummary(Long documentId, String email) {
        Document document = getAuthorizedDocument(documentId, email);
        String text = truncate(document.getContent());
        String title = document.getTitle();

        if (text.isBlank()) {
            return "# Summary of " + title + "\n\nNo text content was found in this document.";
        }

        if (isAiEnabled()) {
            try {
                String systemPrompt = "You are an expert academic summarizer. Produce a structured markdown summary.";
                String userMessage = String.format("""
                    Summarize this document titled "%s" in markdown format with these sections:
                    ## Overview
                    ## Key Points (bullet list of 5-7 points)
                    ## Key Quote (one impactful quote from the text)
                    ## Study Recommendation
                    
                    Document content:
                    %s
                    """, title, text);

                return callOpenAI(systemPrompt, userMessage);
            } catch (Exception e) {
                log.warn("OpenAI summary failed, using stub: {}", e.getMessage());
            }
        }

        return buildStubSummary(title, text);
    }

    // ─── Quiz ────────────────────────────────────────────────────────────────────

    public List<QuizQuestionResponse> generateQuiz(Long documentId, String email) {
        Document document = getAuthorizedDocument(documentId, email);
        String text = truncate(document.getContent());
        String title = document.getTitle().replace(".txt", "").replace(".pdf", "");

        if (isAiEnabled()) {
            try {
                String systemPrompt = "You are a quiz generator. Always respond with valid JSON only.";
                String userMessage = String.format("""
                    Generate exactly 5 multiple-choice quiz questions based on this document.
                    
                    Document: "%s"
                    Content: %s
                    
                    Return ONLY a JSON array with no extra text:
                    [{"id":1,"question":"...","options":["A","B","C","D"],"answer":"A","explanation":"..."}]
                    """, title, text);

                String json = callOpenAI(systemPrompt, userMessage);
                List<QuizQuestionResponse> result = parseQuizJson(json);
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                log.warn("OpenAI quiz generation failed, using stub: {}", e.getMessage());
            }
        }

        return buildStubQuiz(title, text, document.getTitle());
    }

    // ─── Flashcards ─────────────────────────────────────────────────────────────

    public List<Flashcard> generateFlashcards(Long documentId, String email) {
        Document document = getAuthorizedDocument(documentId, email);

        List<Flashcard> existing = flashcardRepository.findByDocumentId(documentId);
        if (!existing.isEmpty()) return existing;

        String text = truncate(document.getContent());
        String title = document.getTitle().replace(".txt", "").replace(".pdf", "");

        if (isAiEnabled()) {
            try {
                String systemPrompt = "You are a flashcard generator. Always respond with valid JSON only.";
                String userMessage = String.format("""
                    Create exactly 6 study flashcards from this document.
                    
                    Document: "%s"
                    Content: %s
                    
                    Return ONLY a JSON array:
                    [{"question":"What is...?","answer":"It is..."}]
                    """, title, text);

                String json = callOpenAI(systemPrompt, userMessage);
                List<Flashcard> cards = parseFlashcardJson(json, documentId);
                if (!cards.isEmpty()) return flashcardRepository.saveAll(cards);
            } catch (Exception e) {
                log.warn("OpenAI flashcard generation failed, using stub: {}", e.getMessage());
            }
        }

        return flashcardRepository.saveAll(buildStubFlashcards(title, text, documentId));
    }

    // ─── Chat ────────────────────────────────────────────────────────────────────

    public String chatWithDocument(Long documentId, String message, String email) {
        Document document = getAuthorizedDocument(documentId, email);
        String text = truncate(document.getContent());

        if (isAiEnabled()) {
            try {
                String systemPrompt = String.format("""
                    You are LumenAi, an expert AI study tutor. The user is studying "%s".
                    Use the document content below as your knowledge base. Answer clearly and helpfully in markdown.
                    
                    Document content:
                    %s
                    """, document.getTitle(), text);

                return callOpenAI(systemPrompt, message);
            } catch (Exception e) {
                log.warn("OpenAI chat failed, using stub: {}", e.getMessage());
            }
        }

        return buildStubChatResponse(document.getTitle(), text, message);
    }

    // ─── Stubs ───────────────────────────────────────────────────────────────────

    private String buildStubSummary(String title, String text) {
        String cleanTitle = title.replace(".txt", "").replace(".pdf", "");
        return "# Executive Summary: " + cleanTitle + "\n\n" +
               "## Overview\nThis document covers **" + cleanTitle + "**, serving as a study resource.\n\n" +
               "## Key Points\n" +
               "- **Topic**: " + (text.length() > 100 ? text.substring(0, 100).trim() + "..." : text) + "\n" +
               "- **Domain**: AI-powered educational content\n- **Goal**: Knowledge retention\n\n" +
               "## Key Quote\n> \"" + (text.length() > 200 ? text.substring(0, 200).trim() + "..." : text) + "\"\n\n" +
               "## Study Recommendation\nUse flashcards and the quiz to maximize retention.\n\n" +
               "> 💡 *Add your `OPENAI_API_KEY` to unlock AI-powered summaries.*";
    }

    private List<QuizQuestionResponse> buildStubQuiz(String title, String text, String fullTitle) {
        return Arrays.asList(
            QuizQuestionResponse.builder().id(1L)
                .question("What is the primary subject of '" + fullTitle + "'?")
                .options(Arrays.asList(title + " principles", "Database tuning", "AWS S3 policies", "Gmail SMTP"))
                .answer(title + " principles")
                .explanation("The document focuses on " + title + ".").build(),
            QuizQuestionResponse.builder().id(2L)
                .question("Which excerpt appears in this document?")
                .options(Arrays.asList(
                    text.length() > 70 ? text.substring(0, 70).trim() + "..." : "LumenAi modules",
                    "System.out.println('Hello');", "docker-compose up -d", "npm run dev"))
                .answer(text.length() > 70 ? text.substring(0, 70).trim() + "..." : "LumenAi modules")
                .explanation("Matches extracted document text.").build(),
            QuizQuestionResponse.builder().id(3L)
                .question("What is the best study approach for this document?")
                .options(Arrays.asList("Flashcards + summary + quizzes", "Read once", "Skip to end", "Memorize verbatim"))
                .answer("Flashcards + summary + quizzes")
                .explanation("Active recall maximizes retention.").build()
        );
    }

    private List<Flashcard> buildStubFlashcards(String title, String text, Long documentId) {
        return Arrays.asList(
            Flashcard.builder().question("What is the central theme of this document?")
                .answer("The central theme is: " + title + ".").documentId(documentId).build(),
            Flashcard.builder().question("Provide a key excerpt from this document.")
                .answer("\"" + (text.length() > 120 ? text.substring(0, 120).trim() + "..." : text) + "\"")
                .documentId(documentId).build(),
            Flashcard.builder().question("How should you study this document?")
                .answer("Use active recall: flashcards, AI summaries, and practice quizzes.")
                .documentId(documentId).build()
        );
    }

    private String buildStubChatResponse(String title, String text, String message) {
        if (message.toLowerCase().contains("summary") || message.toLowerCase().contains("summarize")) {
            return "**Summary**: The document **'" + title + "'** covers — " +
                   (text.length() > 150 ? text.substring(0, 150) + "..." : text);
        }
        return "I'm your AI Tutor for **" + title + "**.\n\nYou asked: *\"" + message + "\"*\n\n" +
               "From the document:\n> \"" + (text.length() > 250 ? text.substring(0, 250).trim() + "..." : text) + "\"\n\n" +
               "I can also generate a quiz, flashcards, or summarize key sections.\n\n" +
               "> 💡 *Set `OPENAI_API_KEY` to enable real AI responses.*";
    }

    // ─── JSON Parsers ────────────────────────────────────────────────────────────

    private List<QuizQuestionResponse> parseQuizJson(String json) {
        try {
            String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            // Find the JSON array
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');
            if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1);

            var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, QuizQuestionResponse.class);
            return objectMapper.readValue(cleaned, listType);
        } catch (Exception e) {
            log.warn("Failed to parse quiz JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Flashcard> parseFlashcardJson(String json, Long documentId) {
        try {
            String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');
            if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1);

            JsonNode nodes = objectMapper.readTree(cleaned);
            List<Flashcard> cards = new ArrayList<>();
            for (JsonNode node : nodes) {
                cards.add(Flashcard.builder()
                        .question(node.path("question").asText())
                        .answer(node.path("answer").asText())
                        .documentId(documentId)
                        .build());
            }
            return cards;
        } catch (Exception e) {
            log.warn("Failed to parse flashcard JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
