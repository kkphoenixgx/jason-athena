package br.com.kkphoenix.services.ollamaService.AIContextManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // The Context Template (Translation-Context or Persona-Context)
    private String contextTemplate;
    
    // Volatile ensures immediate visibility between threads when persona is updated
    private volatile String personaContext = "";

    private final OllamaOptions ollamaOptions = new OllamaOptions(
        4096
    );

    // Active Sessions armazena o "Context Vector" (long[]), que é a memória técnica 
    // da conversa mantida pelo Ollama (não confundir com o texto de contexto do agente).
    // Active Sessions stores the "Context Vector" (long[]), which is the technical memory
    // of the conversation maintained by Ollama (not to be confused with the agent's context text).
    private final Map<String, long[]> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Constructor for manual dependency injection.
     * @param client The HTTP Client instance.
     * @param objectMapper The Jackson Object Mapper.
     * @param ollamaUrl The URL of the Ollama API.
     * @param modelName The name of the model to use (e.g., "gemma:2b").
     * @param contextTemplate The content of the context prompt template string.
     */
    public OllamaManager(HttpClient client, ObjectMapper objectMapper, String ollamaUrl, String modelName, String contextTemplate) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.ollamaUrl = ollamaUrl;
        this.modelName = modelName;
        this.contextTemplate = contextTemplate;
    }

    @Override
    public void setContextTemplate(String contextTemplate) {
        this.contextTemplate = contextTemplate;
    }

    @Override
    public void setPersonaContext(String personaContext) {
        this.personaContext = personaContext;
    }

    /** Translates a user message into a list of KQML commands using the session context. */
    @Override
    public CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage) {
        return translateMessage(sessionId, userMessage, null);
    }

    /** Overload to support images in the translation request. */
    @Override
    public CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage, List<String> images) {
        try {
            final long[] currentContext = activeSessions.get(sessionId);
            if (currentContext == null) {
                throw new IllegalStateException("Error: User session was not correctly initialized.");
            }

            // Injects personaContext as 'system' prompt to keep it pinned/fresh
            IAGenerateRequest request = new IAGenerateRequest(modelName, userMessage, this.personaContext, currentContext, ollamaOptions, images);
            logger.debug("Ollama translate request for session {}", sessionId);

            CompletableFuture<TranslationResult> future = makeRequest(request).thenApply(response -> {
                activeSessions.put(sessionId, response.context());
                String rawResponse = response.response().trim();
                
                logger.info("Ollama raw response for session {}: {}", sessionId, rawResponse);
               
                List<String> kqmlMessages = parseKqmlMessages(rawResponse);
                return new TranslationResult(
                    kqmlMessages, 
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

    /** Parses the raw text response into a list of non-empty lines (KQML commands). */
    private List<String> parseKqmlMessages(String rawResponse) {
        return rawResponse.lines()
                          .map(String::trim)
                          .filter(line -> !line.isEmpty())
                          .collect(Collectors.toList());
    }

    /** Ends the AI model session for the given session ID. */
    @Override
    public void endSession(String sessionId) {
        if (activeSessions.remove(sessionId) != null) {
            logger.info("AI model session ended for: {}", sessionId);
        }
        // Cancels any pending request for this session
        CompletableFuture<?> pending = pendingRequests.remove(sessionId);
        if (pending != null && !pending.isDone()) {
            pending.cancel(true);
            logger.warn("Cancelled pending AI request for session {}", sessionId);
        }
    }

    /** Initializes a user session with the base context and agent-specific plans. */
    @Override
    public int initializeUserSession(String sessionId, String agentPlans) {
        try {
            // Prepares MAS-Context (Agent Plans)
            logger.debug("MAS-Context (Agent Plans) loaded size: {}", agentPlans.length());

            // Combines with Translation-Context or Persona-Context (Template)
            // The template must contain the placeholder ##PLANOS_DO_AGENTE##
            // OPTIMIZATION: Use StringBuilder to avoid multiple large String allocations
            StringBuilder promptBuilder = new StringBuilder(contextTemplate.length() + agentPlans.length() + 500);

            // Injects Persona Context if it exists
            if (this.personaContext != null && !this.personaContext.isEmpty()) {
                promptBuilder.append(this.personaContext).append("\n\n");
            }
            
            // OTIMIZAÇÃO AVANÇADA: Evita criar string temporária com .replace()
            int placeholderIdx = contextTemplate.indexOf("##PLANOS_DO_AGENTE##");
            if (placeholderIdx != -1) {
                promptBuilder.append(contextTemplate, 0, placeholderIdx);
                promptBuilder.append(agentPlans);
                promptBuilder.append(contextTemplate, placeholderIdx + "##PLANOS_DO_AGENTE##".length(), contextTemplate.length());
            } else {
                promptBuilder.append(contextTemplate);
            }
 
            IAGenerateRequest request = new IAGenerateRequest(modelName, promptBuilder.toString(), ollamaOptions);
            logger.debug("Ollama init request for session {} (Model: {})", sessionId, modelName);
     
            CompletableFuture<IAGenerateResponse> future = makeRequest(request);
            pendingRequests.put(sessionId, future);
            IAGenerateResponse response = future.get(); // Block only on initialization
     
            // Stores the context vector returned by Ollama (Session Memory)
            activeSessions.put(sessionId, response.context());
            logger.info("Session {} initialized. Ollama memory context updated. Time: {}ms", sessionId, response.totalDuration() / 1_000_000);
            return response.promptEvalCount();
        } catch (InterruptedException e) {
            logger.error("Failed to initialize user session {} due to an interruption error.", sessionId, e);
            throw new RuntimeException("Failed to initialize user session: " + sessionId, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                logger.error("Failed to initialize user session {} due to a network error: {}", sessionId, cause.getMessage());

                throw new RuntimeException("Cannot connect to AI model. Please check network connectivity and firewall settings.", cause);
            }
            throw new RuntimeException("An unexpected error occurred during AI session initialization.", e);
        } finally {
            pendingRequests.remove(sessionId);
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