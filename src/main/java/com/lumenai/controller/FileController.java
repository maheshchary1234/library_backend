package com.lumenai.controller;

import com.lumenai.entity.Document;
import com.lumenai.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<Document> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        Document document = fileService.uploadFile(file, authentication.getName());
        return ResponseEntity.ok(document);
    }

    @GetMapping("/all")
    public ResponseEntity<List<Document>> getAll(Authentication authentication) {
        List<Document> documents = fileService.getAllDocuments(authentication.getName());
        return ResponseEntity.ok(documents);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id, Authentication authentication) {
        fileService.deleteDocument(id, authentication.getName());
        return ResponseEntity.ok("Document deleted successfully");
    }
}
