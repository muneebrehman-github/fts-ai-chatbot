package com.fts.aichat;

/**
 * Marker for product-specific tool beans.
 * Implement this on a {@code @Component} that exposes Spring AI {@code @Tool} methods.
 * The starter registers every such bean with both Groq and Gemini chat clients.
 */
public interface FtsAiChatTools {
}
