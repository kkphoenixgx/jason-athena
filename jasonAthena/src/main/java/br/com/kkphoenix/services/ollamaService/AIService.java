package br.com.kkphoenix.services.ollamaService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.kkphoenix.services.ollamaService.interfaces.IModelManager;

public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final IModelManager modelManager;

    private final String sessionId;
    private final Executor executor;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public AIService(IModelManager modelManager, String sessionId, Executor executor) {
        this.modelManager = modelManager;
        this.sessionId = sessionId;
        this.executor = executor;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    public CompletableFuture<Void> initialize(String agentPlans, String persona) {
        updatePersonaIfSupported(persona);
        return CompletableFuture.runAsync(() -> {
            int initialContextCost = this.modelManager.initializeUserSession(sessionId, agentPlans);
            logger.info("[{}] Initial context cost (prompt_eval_count): {} tokens.", sessionId, initialContextCost);
            if (initialContextCost > 2000) {
                logger.warn("[{}] ATTENTION: Initial context size is over 2000 tokens. This may impact performance.", sessionId);
            }
        }, executor);
    }

    public void updatePersona(String persona) {
        updatePersonaIfSupported(persona);
    }

    public CompletableFuture<List<String>> getKQMLMessages(String sessionId, String message) {
        return getKQMLMessages(sessionId, message, null);
    }

    public CompletableFuture<List<String>> getKQMLMessages(String sessionId, String message, List<String> images) {
        CompletableFuture<br.com.kkphoenix.services.ollamaService.classes.TranslationResult> future = modelManager.translateMessage(sessionId, message, images);
        return future.thenApply(result -> {
                logger.info("[{}] Translation stats - Prompt: {} tokens, Response: {} tokens, Time: {}ms", 
                    sessionId, result.getPromptEvalCount(), result.getEvalCount(), result.getTotalDuration() / 1_000_000);
                logAudit(sessionId, message, result);
                return result.getKqmlMessages();
            }).exceptionally(e -> {
                logger.error("[{}] Error processing AI translation", sessionId, e);
                return List.of("Error processing AI translation: " + e.getMessage());
            });
    }

    public void endSession() {
        this.modelManager.endSession(sessionId);
    }

    private void updatePersonaIfSupported(String persona) {
        modelManager.setPersonaContext(persona);
    }

    private void logAudit(String sessionId, String userMessage, br.com.kkphoenix.services.ollamaService.classes.TranslationResult result) {
        try (PrintWriter out = new PrintWriter(new FileWriter("athena_audit.json", true))) {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("session_id", sessionId);
            logEntry.put("input_preview", userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("prompt_tokens", result.getPromptEvalCount());
            metrics.put("response_tokens", result.getEvalCount());
            metrics.put("total_duration_ns", result.getTotalDuration());
            metrics.put("load_duration_ns", result.getLoadDuration());
            metrics.put("eval_duration_ns", result.getEvalDuration());
            metrics.put("total_duration_ms", result.getTotalDuration() / 1_000_000);
            
            logEntry.put("metrics", metrics);
            
            out.println(objectMapper.writeValueAsString(logEntry));
        } catch (IOException e) {
            logger.error("Failed to write to audit log", e);
        }
    }
}