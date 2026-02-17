package br.com.kkphoenix.services.ollamaService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.kkphoenix.services.ollamaService.interfaces.IModelManager;
import br.com.kkphoenix.services.ollamaService.classes.TranslationResult;

public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private final IModelManager modelManager;

    private final Executor executor;

    public AIService(IModelManager modelManager, Executor executor) {
        this.modelManager = modelManager;
        this.executor = executor;
    }

    public CompletableFuture<Void> initialize(String sessionId, String prompt) {
        return CompletableFuture.runAsync(() -> {
            int cost = modelManager.initializeSession(sessionId, prompt);
            logger.info("[{}] Session initialized. Context cost: {} tokens.", sessionId, cost);
        }, executor);
    }

    public CompletableFuture<List<String>> ask(String sessionId, String message, String systemPrompt, List<String> images) {
        return ask(sessionId, null, message, systemPrompt, images);
    }

    public CompletableFuture<List<String>> ask(String sessionId, String model, String message, String systemPrompt, List<String> images) {
        return modelManager.translateMessage(sessionId, model, message, systemPrompt, images)
            .thenApply(TranslationResult::getKqmlMessages)
            .exceptionally(e -> {
                logger.error("[{}] Error processing AI request", sessionId, e);
                return List.of();
            });
    }

    public void endSession(String sessionId) {
        modelManager.endSession(sessionId);
    }
}