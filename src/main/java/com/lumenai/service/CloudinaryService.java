package com.lumenai.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean enabled;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret
    ) {
        this.enabled = !cloudName.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();

        if (this.enabled) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true
            ));
            log.info("Cloudinary storage initialized (cloud: {})", cloudName);
        } else {
            this.cloudinary = null;
            log.info("Cloudinary not configured — local disk storage will be used");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Uploads a file to Cloudinary and returns the secure public URL.
     * Folder is used to organise uploads (e.g. "lumenai/documents").
     */
    @SuppressWarnings("unchecked")
    public String upload(MultipartFile file, String folder) throws IOException {
        if (!enabled) {
            throw new IllegalStateException("Cloudinary is not configured");
        }

        Map<String, Object> options = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "raw",  // supports PDFs, TXTs, any file
                "use_filename", true,
                "unique_filename", true
        );

        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), options);
        String secureUrl = (String) result.get("secure_url");
        log.info("Uploaded {} to Cloudinary: {}", file.getOriginalFilename(), secureUrl);
        return secureUrl;
    }

    /**
     * Deletes a file from Cloudinary by its public ID.
     * The public ID is the path without the file extension, e.g. "lumenai/documents/abc123_file"
     */
    @SuppressWarnings("unchecked")
    public void delete(String publicId) {
        if (!enabled || publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
            log.info("Deleted from Cloudinary: {}", publicId);
        } catch (IOException e) {
            log.warn("Failed to delete from Cloudinary ({}): {}", publicId, e.getMessage());
        }
    }

    /**
     * Extracts the Cloudinary public ID from a secure URL.
     * Used when deleting a previously uploaded file.
     */
    public String extractPublicId(String secureUrl) {
        if (secureUrl == null || !secureUrl.contains("/upload/")) return null;
        // URL format: https://res.cloudinary.com/{cloud}/raw/upload/v{version}/{folder}/{filename}
        int uploadIdx = secureUrl.indexOf("/upload/") + "/upload/".length();
        String path = secureUrl.substring(uploadIdx);
        // Strip version prefix if present (v1234567890/)
        if (path.matches("v\\d+/.*")) {
            path = path.substring(path.indexOf('/') + 1);
        }
        // Strip file extension
        int dotIdx = path.lastIndexOf('.');
        return dotIdx > 0 ? path.substring(0, dotIdx) : path;
    }
}
