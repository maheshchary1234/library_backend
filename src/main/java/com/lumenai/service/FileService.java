package com.lumenai.service;

import com.lumenai.entity.Document;
import com.lumenai.entity.User;
import com.lumenai.repository.DocumentRepository;
import com.lumenai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Value("${storage.provider:local}")
    private String storageProvider;

    private static final String UPLOAD_DIR = "uploads";
    private static final String CLOUDINARY_FOLDER = "lumenai/documents";

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    public Document uploadFile(MultipartFile file, String email) {
        User user = getUser(email);
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("Invalid file name");
        }

        // Extract text content
        String content = extractContent(file, originalFilename);

        // Store the file
        String fileUrl = storeFile(file, originalFilename);

        Document document = Document.builder()
                .userId(user.getId())
                .title(originalFilename)
                .fileUrl(fileUrl)
                .content(content)
                .uploadedAt(Instant.now())
                .build();

        return documentRepository.save(document);
    }

    public List<Document> getAllDocuments(String email) {
        User user = getUser(email);
        return documentRepository.findByUserId(user.getId());
    }

    public void deleteDocument(Long id, String email) {
        User user = getUser(email);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!document.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized delete request");
        }

        // Delete from storage
        deleteFromStorage(document.getFileUrl());
        documentRepository.delete(document);
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private String extractContent(MultipartFile file, String filename) {
        try {
            if (filename.toLowerCase().endsWith(".txt")) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (filename.toLowerCase().endsWith(".pdf")) {
                try (org.apache.pdfbox.pdmodel.PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                    return new PDFTextStripper().getText(pdf);
                }
            } else {
                throw new RuntimeException("Unsupported file type — only PDF and TXT are supported.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse document content: " + e.getMessage(), e);
        }
    }

    private String storeFile(MultipartFile file, String originalFilename) {
        if ("cloudinary".equalsIgnoreCase(storageProvider) && cloudinaryService.isEnabled()) {
            try {
                return cloudinaryService.upload(file, CLOUDINARY_FOLDER);
            } catch (IOException e) {
                log.warn("Cloudinary upload failed, falling back to local: {}", e.getMessage());
            }
        }

        // Default: local disk storage
        return storeLocally(file, originalFilename);
    }

    private String storeLocally(MultipartFile file, String originalFilename) {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            String uniqueFilename = UUID.randomUUID() + "_" + originalFilename;
            Path filePath = Paths.get(UPLOAD_DIR, uniqueFilename);
            Files.copy(file.getInputStream(), filePath);
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file locally: " + e.getMessage(), e);
        }
    }

    private void deleteFromStorage(String fileUrl) {
        if (fileUrl == null) return;

        if ("cloudinary".equalsIgnoreCase(storageProvider) && cloudinaryService.isEnabled()
                && fileUrl.startsWith("https://res.cloudinary.com")) {
            String publicId = cloudinaryService.extractPublicId(fileUrl);
            cloudinaryService.delete(publicId);
        } else {
            // Local file
            try {
                Files.deleteIfExists(Paths.get(fileUrl));
            } catch (IOException e) {
                log.warn("Could not delete local file {}: {}", fileUrl, e.getMessage());
            }
        }
    }
}
