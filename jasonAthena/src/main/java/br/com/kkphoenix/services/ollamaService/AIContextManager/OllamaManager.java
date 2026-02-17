package br.com.kkphoenix.services.ollamaService.AIContextManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.kkphoenix.services.ollamaService.classes.IAGenerateRequest;
import br.com.kkphoenix.services.ollamaService.classes.IAGenerateResponse;
import br.com.kkphoenix.services.ollamaService.classes.OllamaOptions;
import br.com.kkphoenix.services.ollamaService.classes.TranslationResult;
import br.com.kkphoenix.services.ollamaService.interfaces.IModelManager;

import java.net.http.HttpRequest;


public class OllamaManager implements IModelManager {

    private static final Logger logger = LoggerFactory.getLogger(OllamaManager.class);

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String ollamaUrl;
    private final String modelName;
    
    private final OllamaOptions ollamaOptions = new OllamaOptions(
        4096
    );

    // Active Sessions armazena o "Context Vector" (long[]), que é a memória técnica 
    // da conversa mantida pelo Ollama.
    private final Map<String, long[]> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Constructor for manual dependency injection.
     * @param client The HTTP Client instance.
     * @param objectMapper The Jackson Object Mapper.
     * @param ollamaUrl The URL of the Ollama API.
     * @param modelName The name of the model to use (e.g., "gemma:2b").
     */
    public OllamaManager(HttpClient client, ObjectMapper objectMapper, String ollamaUrl, String modelName) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.ollamaUrl = ollamaUrl;
        this.modelName = modelName;
    }

    @Override
    public CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage) {
        return translateMessage(sessionId, null, userMessage, null, null);
    }

    @Override
    public CompletableFuture<TranslationResult> translateMessage(String sessionId, String modelOverride, String userMessage, String systemPrompt, List<String> images) {
        try {
            final long[] currentContext = activeSessions.get(sessionId);
            if (currentContext == null) {
                throw new IllegalStateException("Error: User session was not correctly initialized.");
            }

            // System prompt is now passed dynamically per request (Context Injection)
            String targetModel = (modelOverride != null && !modelOverride.isEmpty()) ? modelOverride : this.modelName;
            IAGenerateRequest request = new IAGenerateRequest(targetModel, userMessage, systemPrompt, currentContext, ollamaOptions, images);
            logger.debug("Ollama translate request for session {}", sessionId);

            CompletableFuture<TranslationResult> future = makeRequest(request).thenApply(response -> {
                activeSessions.put(sessionId, response.context());
                String rawResponse = response.response().trim();
                
                // Raw output for now, Syllabus will handle strict parsing later
                List<String> messages = parseKqmlMessages(rawResponse);
                
                return new TranslationResult(
                    messages, 
                    response.promptEvalCount(),
                    response.evalCount(),
                    response.totalDuration(),
                    response.loadDuration(),
                    response.evalDuration()
                );
            });

            pendingRequests.put(sessionId, future);
            future.whenComplete((result, ex) -> pendingRequests.remove(sessionId));
            return future;
        } catch (Exception e) {
            logger.error("Failed to translate message for session {}", sessionId, e);
            return CompletableFuture.failedFuture(new RuntimeException("Failed to translate message for session " + sessionId, e));
        }
    }

    private List<String> parseKqmlMessages(String rawResponse) {
        return rawResponse.lines()
                          .map(String::trim)
                          .filter(line -> !line.isEmpty())
                          .collect(Collectors.toList());
    }

    @Override
    public void endSession(String sessionId) {
        activeSessions.remove(sessionId);
        CompletableFuture<?> pending = pendingRequests.remove(sessionId);
        if (pending != null) pending.cancel(true);
        logger.info("Session {} terminated and context cleared.", sessionId);
    }

    @Override
    public int initializeSession(String sessionId, String prompt) {
        try {
            IAGenerateRequest request = new IAGenerateRequest(modelName, prompt, ollamaOptions);
            
            // Synchronous block for initialization (Bootstrapping)
            IAGenerateResponse response = makeRequest(request).get();
            
            activeSessions.put(sessionId, response.context());
            return response.promptEvalCount();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize session: " + sessionId, e);
        }
    }

    /** Makes a POST request to the Ollama API with the given JSON body. */
    private CompletableFuture<IAGenerateResponse> makeRequest(Object requestBody) {
        try {
            // OPTIMIZATION: Serialize directly to byte[] to avoid large String allocation (UTF-16 overhead)
            byte[] jsonBytes = objectMapper.writeValueAsBytes(requestBody);
            logger.debug("Sending request to Ollama (Payload: {} bytes)...", jsonBytes.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
                    .timeout(Duration.ofMinutes(20))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(httpResponse -> {
                logger.debug("Received response from Ollama (Status: {}).", httpResponse.statusCode());
                if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                    try {
                        return objectMapper.readValue(httpResponse.body(), IAGenerateResponse.class);
                    } catch (IOException e) {
                        logger.error("Failed to parse Ollama response", e);
                        throw new CompletionException(e);
                    }
                } else {
                    logger.error("Ollama API returned error. Status: {}, Body: {}", httpResponse.statusCode(), httpResponse.body());
                    throw new CompletionException(new IOException("Request to Ollama API failed with status code " + httpResponse.statusCode()));
                }
            });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

}