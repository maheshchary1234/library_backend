package com.lumenai.controller;

import com.lumenai.dto.AiRequest;
import com.lumenai.dto.QuizQuestionResponse;
import com.lumenai.entity.Flashcard;
import com.lumenai.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;

    @PostMapping("/summary")
    public ResponseEntity<String> getSummary(@RequestBody AiRequest request, Authentication authentication) {
        String summary = aiService.generateSummary(request.getDocumentId(), authentication.getName());
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/quiz")
    public ResponseEntity<List<QuizQuestionResponse>> getQuiz(@RequestBody AiRequest request, Authentication authentication) {
        List<QuizQuestionResponse> quiz = aiService.generateQuiz(request.getDocumentId(), authentication.getName());
        return ResponseEntity.ok(quiz);
    }

    @PostMapping("/flashcards")
    public ResponseEntity<List<Flashcard>> getFlashcards(@RequestBody AiRequest request, Authentication authentication) {
        List<Flashcard> flashcards = aiService.generateFlashcards(request.getDocumentId(), authentication.getName());
        return ResponseEntity.ok(flashcards);
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody AiRequest request, Authentication authentication) {
        String response = aiService.chatWithDocument(
                request.getDocumentId(),
                request.getMessage(),
                authentication.getName()
        );
        return ResponseEntity.ok(Map.of("response", response));
    }
}
