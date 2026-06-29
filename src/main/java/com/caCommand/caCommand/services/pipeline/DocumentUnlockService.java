package com.caCommand.caCommand.services.pipeline;

import com.caCommand.caCommand.common.exceptions.DocumentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class DocumentUnlockService {

    /**
     * Unlocks a PDF document if it is password protected and removes the encryption.
     * @param pdfBytes The original PDF bytes
     * @param password The password to unlock
     * @return The unlocked PDF bytes, or the original bytes if not encrypted
     */
    public byte[] unlockDocument(byte[] pdfBytes, String password) {
        try (PDDocument document = PDDocument.load(pdfBytes, password)) {
            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
                
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    document.save(baos);
                    return baos.toByteArray();
                }
            } else {
                return pdfBytes;
            }
        } catch (Exception e) {
            log.error("Failed to unlock document", e);
            throw new DocumentProcessingException("Failed to unlock document: " + e.getMessage(), e);
        }
    }
}
