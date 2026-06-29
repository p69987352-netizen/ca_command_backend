package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.services.S3StorageService;
import com.caCommand.caCommand.services.LegacyDocumentExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/client/{id}")
@RequiredArgsConstructor
public class DocumentUploadController {

    private final ClientRepository clientRepository;
    private final S3StorageService s3StorageService;
    private final LegacyDocumentExtractionService documentExtractionService;

    @PostMapping("/upload-multiple")
    public String uploadMultipleDocuments(@PathVariable UUID id, @RequestParam("files") List<MultipartFile> files) {
        Client client = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid client Id:" + id));

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    String originalFilename = file.getOriginalFilename();
                    byte[] bytes = file.getBytes();
                    
                    // Upload to S3
                    String s3Url = s3StorageService.uploadMedia(bytes, client.getPhoneNumber() + "_" + System.currentTimeMillis() + "_" + originalFilename);
                    
                    if (s3Url != null) {
                        // The DocumentExtractionService will run asynchronously and handle AI verification & extraction
                        documentExtractionService.processUploadedDocument(client.getId(), originalFilename, s3Url, "Document");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return "redirect:/admin/client/" + id;
    }
}
