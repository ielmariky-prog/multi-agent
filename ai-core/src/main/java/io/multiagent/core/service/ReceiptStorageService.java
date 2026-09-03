package io.multiagent.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class ReceiptStorageService {

    @Value("${ai-core.ocr.storage-path:/tmp/receipts}")
    private String storagePath;

    public Path save(MultipartFile file, String id) {
        try {
            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            String safeName = sanitize(file.getOriginalFilename());
            Path target = dir.resolve(id + "-" + safeName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("📥 Fichier reçu : {} ({} octets) → {}", safeName, file.getSize(), target);
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de stocker le fichier uploadé", e);
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "receipt.bin";
        }
        String clean = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean.length() > 120 ? clean.substring(clean.length() - 120) : clean;
    }
}
