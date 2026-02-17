package br.com.kkphoenix.components;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.kkphoenix.services.ollamaService.AIService;
import br.com.kkphoenix.util.FileUtil;

/**
 * SYLLABUS (The Cortex)
 * Responsible for heavy cognitive processing, context management, and translation.
 * Runs in a dedicated worker thread to avoid blocking the agent's main cycle.
 */
public class Syllabus {

    private static final Logger logger = LoggerFactory.getLogger(Syllabus.class);

    private final AIService aiService;
    private final String sessionId;
    private final ExecutorService executor;
    
    // Cognitive State
    private String currentPersonaHash = "";
    private String translationTemplate = "";
    private String delegationTemplate = "";
    
    private final AtomicBoolean isBusy = new AtomicBoolean(false);
    private CompletableFuture<String> currentTask = null;

    public Syllabus(AIService aiService, String agentName) {
        this.aiService = aiService;
        this.sessionId = agentName; // Shared session with Athena to access initialized context
        this.executor = Executors.newSingleThreadExecutor(); // Thread B (Cognitive)
        loadTemplates();
    }

    private void loadTemplates() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("traduction-context.txt")) {
            if (is != null) {
                this.translationTemplate = FileUtil.readStreamAsString(is);
            } else {
                logger.warn("traduction-context.txt not found in classpath. Using default fallback.");
                this.translationTemplate = "##PLANOS_DO_AGENTE##";
            }
        } catch (IOException e) {
            logger.error("Failed to load translation template", e);
            this.translationTemplate = "##PLANOS_DO_AGENTE##";
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("delegation-context.txt")) {
            if (is != null) {
                this.delegationTemplate = FileUtil.readStreamAsString(is);
            } else {
                logger.warn("delegation-context.txt not found in classpath. Using default fallback.");
                this.delegationTemplate = "AGENT_LIST: %s\nDATA: %s";
            }
        } catch (IOException e) {
            logger.error("Failed to load delegation template", e);
            this.delegationTemplate = "AGENT_LIST: %s\nDATA: %s";
        }
    }

    /**
     * Main cognitive entry point.
     * @param persona The current persona text.
     * @param plans The agent's plans (MAS Context).
     * @param userInput The trigger message or observation.
     * @param images Visual context.
     * @param modelOverride Optional model to use for this specific thought.
     * @return A future containing the raw output from the LLM.
     */
    public CompletableFuture<String> processKQMLSemanticParsing(String persona, String plans, String userInput, List<String> images, String modelOverride) {
        if (!isBusy.compareAndSet(false, true)) {
            logger.warn("Syllabus is busy. Dropping cognitive request.");
            return CompletableFuture.failedFuture(new RuntimeException("Syllabus is busy"));
        }

        // We use the executor to offload the preparation logic (template replacement, etc)
        this.currentTask = CompletableFuture.supplyAsync(() -> {
            // 1. Construct Prompt (Moved up to allow re-initialization with full context)
            String systemPrompt = translationTemplate.replace("##PLANOS_DO_AGENTE##", plans);
            if (persona != null && !persona.isEmpty()) {
                systemPrompt = persona + "\n\n" + systemPrompt;
            }

            // 2. Context Management
            // If persona changes, the mental model is invalid. We clear and RE-INITIALIZE.
            String newPersonaHash = String.valueOf(persona.hashCode());
            if (!newPersonaHash.equals(currentPersonaHash)) {
                logger.info("Syllabus: Persona Shifted. New mental model loaded.");
                aiService.endSession(sessionId);
                // Re-initialize synchronously within this worker thread to ensure context exists before 'ask'
                aiService.initialize(sessionId, systemPrompt).join();
                this.currentPersonaHash = newPersonaHash;
            }

            return systemPrompt;
        }, executor)
        .thenCompose(systemPrompt -> 
            // 3. Call AI Service (Async I/O)
            // The AIService handles the low-level communication and maintains the context vector for this session.
            aiService.ask(sessionId, modelOverride, userInput, systemPrompt, images)
        )
        .thenApply(this::applyStrictRegex) // Req 3.2: Tradução Estrita & Regex
        // CRITICAL FIX: Watchdog Timer. If Ollama hangs, we must release the lock.
        .orTimeout(30, TimeUnit.SECONDS)
        .whenComplete((result, ex) -> {
            isBusy.set(false); // Release lock
            if (ex != null) logger.error("Syllabus processing error", ex);
            this.currentTask = null;
        });
        
        return this.currentTask;
    }

    /**
     * Subrotina Cognitiva: Roteador de Mensagens (Message Router).
     * Analisa uma lista de crenças, planos ou mensagens KQML e decide para quem enviar (Broadcast ou P2P).
     * @param content A lista de crenças, planos ou intenções a serem distribuídos.
     * @param knownAgents Lista de agentes conhecidos no sistema.
     */
    public CompletableFuture<String> routeInformation(String content, List<String> knownAgents) {
        if (!isBusy.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new RuntimeException("Syllabus busy"));
        }

        // FIX: Assign to currentTask for cancellation support and add timeout safety
        this.currentTask = CompletableFuture.supplyAsync(() -> {
            // 1. Prompt Especializado para Roteamento (Stateless - não usa a Persona do Agente)
            return delegationTemplate.formatted(knownAgents, content);
        }, executor)
        .thenCompose(prompt -> aiService.ask(sessionId + "_router", null, prompt, null, null)) // Sessão separada "_router" para não poluir o chat
        .thenApply(this::applyStrictRegex)
        .orTimeout(30, TimeUnit.SECONDS) // Safety timeout like in processKQMLSemanticParsing()
        .whenComplete((res, ex) -> {
            isBusy.set(false);
            if (ex != null) logger.error("Router processing error", ex);
            this.currentTask = null;
        });
        
        return this.currentTask;
    }

    /** Forcefully cancels the current cognitive task to free resources for higher priority thoughts. */
    public void cancelCurrentTask() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
            logger.warn("Syllabus: Cognitive task INTERRUPTED by priority override.");
        }
        isBusy.set(false);
    }

    /**
     * Req 3.2: Mecanismo de Segurança (Regex).
     * Extracts only valid KQML-like commands, ignoring chat/markdown.
     */
    private String applyStrictRegex(List<String> rawLines) {
        // Req 3.2: Regex updated to support full Plans (<-), Annotations ([...]), and Beliefs/Goals.
        // Matches lines starting with +, -, !, or a lowercase letter (action/plan).
        // Allows typical Jason syntax characters: ( ) [ ] _ , " . : < -
        // Filters out conversational text like "Here is the code:" or "Checklist:"
        Pattern kqmlPattern = Pattern.compile("^\\s*([-+!]+|[a-z_])\\w*.*$");
        
        return rawLines.stream()
            .map(String::trim)
            .filter(line -> kqmlPattern.matcher(line).matches())
            .collect(Collectors.joining("\n"));
    }
    
    public boolean isBusy() {
        return isBusy.get();
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
}
