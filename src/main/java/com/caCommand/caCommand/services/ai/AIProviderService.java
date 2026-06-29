package com.caCommand.caCommand.services.ai;

public interface AIProviderService {
    
    /**
     * Identifies the name of the provider (e.g., "GEMINI", "OPENAI")
     */
    String getProviderName();

    /**
     * Generates a text response based on a text prompt.
     */
    String generateText(String prompt);

    /**
     * Generates a text response based on a text prompt and an image.
     * @param prompt Text prompt instructions.
     * @param imageBase64 Base64 encoded image string.
     */
    String generateTextFromImage(String prompt, String imageBase64);
}
