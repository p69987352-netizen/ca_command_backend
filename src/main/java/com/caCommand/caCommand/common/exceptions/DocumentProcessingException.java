package com.caCommand.caCommand.common.exceptions;

public class DocumentProcessingException extends BusinessException {
    public DocumentProcessingException(String message) {
        super(message);
    }
    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
