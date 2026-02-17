package br.com.kkphoenix;

import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSyntax.Literal;
import jason.asSemantics.Agent;

import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import jason.asSyntax.StringTermImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Map;

import br.com.kkphoenix.services.ollamaService.AIService;
import br.com.kkphoenix.services.ollamaService.AIContextManager.OllamaServiceFactory;
import br.com.kkphoenix.util.FileUtil;
import br.com.kkphoenix.components.Syllabus;
import br.com.kkphoenix.components.Praxis;
import br.com.kkphoenix.components.Logos;


public class Athena extends AgArch {

    private AIService aiService;
    private static final Logger logger = Logger.getLogger(Athena.class.getName());
    private Syllabus syllabus;
    private Praxis praxis;
    private Logos logos;

    // Context Definitions
    private StringBuilder masContext = new StringBuilder();
    private String personaContext = "";

    // translationContext is loaded via OllamaServiceFactory (classpath:traduction-context.txt)
    private List<String> pendingImages = new ArrayList<>(); // Images waiting to be sent

    private boolean isThinking = false;

    @Override
    public void init() throws Exception {
        super.init();
        // Syllabus will be initialized lazily or when AI Service is ready
        this.praxis = new Praxis(this);
    }

    @Override
    public void act(ActionExec action) {

        if (this.logos != null) {
            this.logos.notifyActivity();
        }

        String functor = action.getActionTerm().getFunctor();

        if (functor.equals("addPersona")) {
            handleAddPersona(action);
            actionExecuted(action);
            return;
        }

        if (functor.equals("addContext")) {
            handleAddContext(action);
            actionExecuted(action);
            return;
        }

        if (functor.equals("removeContext")) {
            handleRemoveContext(action);
            actionExecuted(action);
            return;
        }

        if (functor.equals("ask_llm")) {
            handleAskLlm(action);
            return;
        }

        if (functor.equals("reflectPlans")) {
            handleReflectPlans(action);
            actionExecuted(action);
            return;
        }

        if (functor.equals("startThink")) {
            handleStartThink(action);
            actionExecuted(action);
            return;
        }

        if (functor.equals("stopThink")) {
            stopThinking();
            action.setResult(true);
            actionExecuted(action);
            return;
        }

        // Req 3.4: Athena.Syllabus(persona, model, msg, [contexts])
        if (functor.equals("Athena.Syllabus") || functor.equals("syllabus")) {
            handleSyllabus(action);
            return;
        }

        // Req 4.5: Athena.Praxis(kqmlMessage)
        if (functor.equals("Athena.Praxis") || functor.equals("praxis")) {
            handlePraxis(action);
            return;
        }

        // Req 4.5.1: Athena.Praxis.nap(N, Ram%)
        if (functor.equals("Athena.Praxis.nap")) {
            handlePraxisNap(action);
            return;
        }

        // Req 4.5.2: Athena.Praxis.garbage_counter_collector(N, Min)
        if (functor.equals("Athena.Praxis.garbage_counter_collector")) {
            handlePraxisCollector(action);
            return;
        }

        // Req 2.1: Athena.Logos(Timeout, [Triggers], [CriticalTriggers], [PostPlans])
        if (functor.equals("Athena.Logos")) {
            handleLogos(action);
            return;
        }
       
        super.act(action);
    }

    /** Method called by Internal Action .startThink. Initializes context and adds 'incorporated' belief when finished. */
    public void startThinking(String model) {
        if (this.masContext.length() == 0) {
            this.masContext.append(getTS().getAg().getPL().toString());
        }
        
        ensureAiService(model);

        logger.info("Starting thinking process (Context Load) for: " + getAgName());
        
        String fullPrompt = (this.personaContext.isEmpty() ? "" : this.personaContext + "\n\n") + this.masContext.toString();
        aiService.initialize(getAgName(), fullPrompt).thenRun(() -> {
            try {
                getTS().getAg().addBel(Literal.parseLiteral("incorporated"));
                this.isThinking = true;
                logger.info("'incorporated' belief added to agent " + getAgName());
            } catch (Exception e) {
                logger.severe("Error adding incorporated belief: " + e.getMessage());
            }
        }).exceptionally(ex -> {
            logger.severe("CRITICAL FAILURE initializing thought (StartThink): " + ex.getMessage());
            return null;
        });
    }

    private void ensureAiService(String model) {
        if (this.aiService == null) {
            // Fallback: Se nenhum modelo for passado, tenta ler dos parâmetros do agente
            if (model == null && getTS().getSettings().getUserParameters().containsKey("model")) {
                model = getTS().getSettings().getUserParameters().get("model").toString().replace("\"", "");
            }
            this.aiService = OllamaServiceFactory.createService(model);
            this.syllabus = new Syllabus(this.aiService, getAgName());
        }
    }

    /** Method called by Internal Action .stopThink. Ends session and removes 'incorporated' belief. */
    public void stopThinking() {
        if (aiService != null) {
            aiService.endSession(getAgName());
            this.isThinking = false;
            try {
                getTS().getAg().delBel(Literal.parseLiteral("incorporated"));
                logger.info("Session ended and 'incorporated' belief removed from agent " + getAgName());
                if (syllabus != null) syllabus.shutdown();
                if (logos != null) logos.shutdown();
            } catch (Exception e) {
                logger.warning("Error removing incorporated belief: " + e.getMessage());
            }
        }
    }


    // --- Action Handlers ---

    private void handleAddPersona(ActionExec action) {
        String content = action.getActionTerm().getTerm(0).toString().replace("\"", "");
        try {
            setPersona(content);
            action.setResult(true);
        } catch (IOException e) {
            logger.warning("Error reading persona file: " + e.getMessage());
            action.setResult(false);
        }
    }

    private void handleAddContext(ActionExec action) {
        String type = action.getActionTerm().getTerm(0).toString().replace("\"", "");
        String content = action.getActionTerm().getTerm(1).toString().replace("\"", "");

        try {
            addContext(type, content);
            action.setResult(true);
        } catch (IOException e) {
            logger.warning("Error processing context: " + e.getMessage());
            action.setResult(false);
        }
    }

    private void handleRemoveContext(ActionExec action) {
        String type = action.getActionTerm().getTerm(0).toString().replace("\"", "");
        removeContext(type);
        action.setResult(true);
    }

    private void handleAskLlm(ActionExec action) {
        String userMessage = action.getActionTerm().getTerm(0).toString();
        Term responseTerm = (action.getActionTerm().getArity() > 1) ? action.getActionTerm().getTerm(1) : null;
        logger.info("Dispatching question to LLM: " + truncateLog(userMessage));

        // Sends pending images along with the question and clears the queue
        List<String> imagesToSend = new ArrayList<>(this.pendingImages);
        this.pendingImages.clear();

        aiService.ask(getAgName(), userMessage, this.personaContext, imagesToSend)
            .thenAccept(responses -> {
                // Se houver variável de retorno, unifica com a primeira resposta (ou lista)
                if (responseTerm != null && !responses.isEmpty()) {
                    // Unifica como String simples para facilitar o print no .asl
                    String combinedResponse = String.join("\n", responses);
                    Unifier un = action.getIntention().peek().getUnif();
                    un.unifies(responseTerm, new StringTermImpl(combinedResponse));
                }

                for (String msg : responses) {
                    logger.info("[LLM Response] " + msg);
                    // Futuro: Injeção via Praxis
                }
                action.setResult(true);
                actionExecuted(action);
            })
            .exceptionally(e -> {
                logger.warning("Error querying LLM: " + e.getMessage());
                action.setResult(false);
                action.setFailureReason(Literal.parseLiteral("llm_error(\"" + e.getMessage() + "\")"), "Error communicating with LLM");
                actionExecuted(action);
                return null;
            });
    }

    /**
     * Req 3.4: Athena.Syllabus(persona, model, msg, [contexts])
     * Automatically reflects plans and processes via Syllabus component.
     */
    private void handleSyllabus(ActionExec action) {
        try {
            // 1. Parse Arguments
            String persona = action.getActionTerm().getTerm(0).toString().replace("\"", "");
            String model = action.getActionTerm().getTerm(1).toString().replace("\"", "");
            ensureAiService(model); // Lazy Init: Garante que o Syllabus exista
            String msg = action.getActionTerm().getTerm(2).toString().replace("\"", "");
            
            // 2. Resolve Persona (File or String)
            String resolvedPersona = resolveContent(persona);

            // 2.1 Parse Contexts (Optional 4th argument)
            List<String> contextImages = new ArrayList<>(this.pendingImages);
            if (action.getActionTerm().getArity() > 3) {
                Term contextTerm = action.getActionTerm().getTerm(3);
                if (contextTerm.isList()) {
                    for (Term t : ((jason.asSyntax.ListTerm) contextTerm).getAsList()) {
                        String path = t.toString().replace("\"", "");
                        if (isImageFile(path)) {
                            try {
                                contextImages.add(FileUtil.readFileAsBase64(path));
                            } catch (Exception e) { logger.warning("Failed to load context image: " + path); }
                        }
                    }
                }
            }

            // 3. Ensure Context Exists (Req 3.4)
            ensureMasContext();

            // 4. Call Syllabus
            logger.info("Syllabus activated: " + msg);
            syllabus.processKQMLSemanticParsing(resolvedPersona, this.masContext.toString(), msg, contextImages, model)
                .thenAccept(result -> {
                    logger.info("Syllabus Result:\n" + result);
                    if (praxis != null) praxis.inject(result);
                });

            action.setResult(true);
            actionExecuted(action);
        } catch (Exception e) {
            logger.severe("Syllabus execution failed: " + e.getMessage());
            action.setResult(false);
            actionExecuted(action);
        }
    }

    private void handlePraxis(ActionExec action) {
        String content = action.getActionTerm().getTerm(0).toString().replace("\"", "");
        if (praxis != null) {
            praxis.inject(content);
            action.setResult(true);
        } else {
            action.setResult(false);
        }
        actionExecuted(action);
    }

    private void handlePraxisNap(ActionExec action) {
        try {
            int n = (int) ((jason.asSyntax.NumberTerm) action.getActionTerm().getTerm(0)).solve();
            double ram = ((jason.asSyntax.NumberTerm) action.getActionTerm().getTerm(1)).solve();
            if (praxis != null) {
                praxis.configureNap(n, ram);
                action.setResult(true);
            }
        } catch (Exception e) {
            logger.warning("Invalid arguments for Athena.Praxis.nap: " + e.getMessage());
            action.setResult(false);
        }
        actionExecuted(action);
    }

    private void handlePraxisCollector(ActionExec action) {
        try {
            int n = (int) ((jason.asSyntax.NumberTerm) action.getActionTerm().getTerm(0)).solve();
            int min = (int) ((jason.asSyntax.NumberTerm) action.getActionTerm().getTerm(1)).solve();
            if (praxis != null) {
                praxis.configureCollector(n, min);
                action.setResult(true);
            }
        } catch (Exception e) {
            logger.warning("Invalid arguments for Athena.Praxis.garbage_counter_collector: " + e.getMessage());
            action.setResult(false);
        }
        actionExecuted(action);
    }

    private void handleLogos(ActionExec action) {
        try {
            long timeout = (long) ((jason.asSyntax.NumberTerm) action.getActionTerm().getTerm(0)).solve();
            List<String> triggers = new ArrayList<>();
            List<String> criticalTriggers = new ArrayList<>();
            List<String> postPlans = new ArrayList<>();
            
            // Parse Lists
            // Arg 1: Normal Triggers
            if (action.getActionTerm().getArity() > 1) extractList(action.getActionTerm().getTerm(1), triggers);
            
            // Arg 2: Critical Triggers (Optional, backward compatibility check)
            // If arity is 3, assume [Triggers], [PostPlans] (Old signature)
            // If arity is 4, assume [Triggers], [Critical], [PostPlans] (New signature)
            if (action.getActionTerm().getArity() == 3) {
                 extractList(action.getActionTerm().getTerm(2), postPlans);
            } else if (action.getActionTerm().getArity() >= 4) {
                 extractList(action.getActionTerm().getTerm(2), criticalTriggers);
                 extractList(action.getActionTerm().getTerm(3), postPlans);
            }
            
            if (this.logos != null) {
                this.logos.shutdown();
            }

            this.logos = new Logos(this, timeout, triggers, criticalTriggers, postPlans);
            action.setResult(true);
        } catch (Exception e) {
            logger.warning("Failed to initialize Logos: " + e.getMessage());
            action.setResult(false);
        }
        actionExecuted(action);
    }

    private void extractList(Term term, List<String> list) {
        if (term.isList()) {
            for (Term t : ((jason.asSyntax.ListTerm) term).getAsList()) {
                list.add(t.toString());
            }
        }
    }

    private boolean isImageFile(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png");
    }

    private void handleReflectPlans(ActionExec action) {
        this.masContext.setLength(0); // Clear previous context to avoid duplication
        try {
            // Tenta obter todos os agentes via infraestrutura centralizada (Reflection para evitar dependência de compilação)
            Class<?> runnerClass = Class.forName("jason.infra.centralised.RunCentralisedMAS");
            Object runner = runnerClass.getMethod("getRunner").invoke(null);
            Map<?, ?> agents = (Map<?, ?>) runnerClass.getMethod("getAgs").invoke(runner);
            
            for (Map.Entry<?, ?> entry : agents.entrySet()) {
                String name = (String) entry.getKey();
                AgArch agArch = (AgArch) entry.getValue();
                Agent ag = agArch.getTS().getAg();
                String src = ag.getASLSrc();
                
                this.addMasContext("// --- Agent: " + name + " ---\n");
                
                if (src != null && !src.isEmpty()) {
                    try {
                        this.addMasContext(FileUtil.readFileAsString(src));
                        logger.info("Reflected plans from agent: " + name);
                    } catch (Exception e) {
                        this.addMasContext(ag.getPL().toString());
                    }
                } else {
                    this.addMasContext(ag.getPL().toString());
                }
                this.addMasContext("\n\n");
            }
            action.setResult(true);
            return;
        } catch (Throwable t) {
            logger.warning("Could not reflect all agents (Infrastructure access failed). Reflecting only self.");
            this.masContext.setLength(0); // Reset context to ensure clean fallback
        }

        // Fallback: Se falhar (ex: não for CentralisedMAS), reflete apenas o próprio agente
        String agentSource = getTS().getAg().getASLSrc();
        
        if (agentSource != null && !agentSource.isEmpty()) {
            try {
                String content = FileUtil.readFileAsString(agentSource);
                this.addMasContext(content);
                logger.info("Plans reflected from source file: " + agentSource);
                action.setResult(true);
                return;
            } catch (Exception e) {
                logger.warning("Failed to read source file (" + e.getMessage() + "). Using default Plan Library.");
            }
        }
        
        // Fallback: Uses Jason's internal representation (no comments)
        this.addMasContext(getTS().getAg().getPL().toString());
        logger.info("Plans reflected from memory (Plan Library).");
        action.setResult(true);
    }

    /** Ensures masContext is populated. Only loads from memory if currently empty. */
    private synchronized void ensureMasContext() {
        // Only fallback to raw Plan Library if no context was loaded via .reflectPlans or .startThink
        if (this.masContext.length() == 0) {
             this.masContext.append(getTS().getAg().getPL().toString());
        }
    }

    private void handleStartThink(ActionExec action) {
        String model = null;
        if (action.getActionTerm().getArity() > 0) {
            model = action.getActionTerm().getTerm(0).toString().replace("\"", "");
        }
        startThinking(model);
        action.setResult(true);
    }

    @Override
    public void stop() {
        if (aiService != null) aiService.endSession(getAgName());
        super.stop();
    }

    // --- Public API for StdLib ---

    public void setPersona(String content) throws IOException {
        this.personaContext = resolveContent(content);
        logger.info("Persona defined and fixed: " + truncateLog(this.personaContext));
    }

    public void addContext(String type, String content) throws IOException {
        if ("persona".equalsIgnoreCase(type)) {
            setPersona(content);
        } 
        else if ("mas".equalsIgnoreCase(type) || "plans".equalsIgnoreCase(type)) {
            this.addMasContext(resolveContent(content));
            logger.info("Context [MAS] added.");
        }
        else if ("image".equalsIgnoreCase(type) || "video".equalsIgnoreCase(type)) {
            if (Files.exists(Paths.get(content))) {
                String base64Image = FileUtil.readFileAsBase64(content);
                this.pendingImages.add(base64Image);
                logger.info("Context [" + type + "] added to queue (" + content + ").");
            } else {
                throw new IOException("Image/Video file not found: " + content);
            }
        }
    }

    public void removeContext(String type) {
        if ("persona".equalsIgnoreCase(type)) {
            this.personaContext = "";
            logger.info("Context [Persona] removed.");
        } else if ("image".equalsIgnoreCase(type)) {
            this.pendingImages.clear();
            logger.info("Context [Image] (pending queue) cleared.");
        }
    }

    public AIService getAiService() { return this.aiService; }
    public List<String> getPendingImages() { return this.pendingImages; }
    public void clearPendingImages() { this.pendingImages.clear(); }

    // --- Helpers ---

    private String resolveContent(String content) throws IOException {
        Path path = Paths.get(content);
        return Files.exists(path) ? Files.readString(path) : content;
    }

    private String truncateLog(String text) {
        return (text.length() > 30) ? text.substring(0, 30) + "..." : text;
    }

    public synchronized void addMasContext(String content) {
        if (this.masContext.length() == 0) {
            this.masContext.append(content);
        } else {
            this.masContext.append("\n\n").append(content);
        }
    }

    public Syllabus getSyllabus() {
        return this.syllabus;
    }

    public Praxis getPraxis() {
        return this.praxis;
    }

    public String getPersonaContext() {
        return this.personaContext;
    }

    public synchronized String getMasContext() {
        return this.masContext.toString();
    }
}