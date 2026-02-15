package br.com.kkphoenix;

import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSyntax.Literal;
import jason.asSemantics.Agent;

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


public class Athena extends AgArch {

    private AIService aiService;
    private static final Logger logger = Logger.getLogger(Athena.class.getName());

    // Context Definitions
    private StringBuilder masContext = new StringBuilder();
    private String personaContext = "";

    // translationContext is loaded via OllamaServiceFactory (classpath:traduction-context.txt)
    private List<String> pendingImages = new ArrayList<>(); // Images waiting to be sent

    private boolean isThinking = false;

    @Override
    public void init() throws Exception {
        super.init();
    }

    @Override
    public void act(ActionExec action) {

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
       
        super.act(action);
    }

    /** Method called by Internal Action .startThink. Initializes context and adds 'incorporated' belief when finished. */
    public void startThinking(String model) {
        if (this.masContext.length() == 0) {
            this.masContext.append(getTS().getAg().getPL().toString());
        }
        
        if (this.aiService == null) {
            // Fallback: Se nenhum modelo for passado, tenta ler dos parâmetros do agente
            if (model == null && getTS().getSettings().getUserParameters().containsKey("model")) {
                model = getTS().getSettings().getUserParameters().get("model").toString();
                model = model.replace("\"", "");
            }
            this.aiService = OllamaServiceFactory.createService(getAgName(), model);
        }

        logger.info("Starting thinking process (Context Load) for: " + getAgName());
        
        aiService.initialize(this.masContext.toString(), this.personaContext).thenRun(() -> {
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

    /** Method called by Internal Action .stopThink. Ends session and removes 'incorporated' belief. */
    public void stopThinking() {
        if (aiService != null) {
            aiService.endSession();
            this.isThinking = false;
            try {
                getTS().getAg().delBel(Literal.parseLiteral("incorporated"));
                logger.info("Session ended and 'incorporated' belief removed from agent " + getAgName());
            } catch (Exception e) {
                logger.warning("Error removing incorporated belief: " + e.getMessage());
            }
        }
    }


    // --- Action Handlers ---

    private void handleAddPersona(ActionExec action) {
        String content = action.getActionTerm().getTerm(0).toString().replace("\"", "");
        try {
            this.personaContext = resolveContent(content);
            
            if (aiService != null) {
                aiService.updatePersona(this.personaContext);
            }
            
            logger.info("Persona defined and fixed: " + truncateLog(this.personaContext));
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
            if ("persona".equalsIgnoreCase(type)) {
                this.personaContext = resolveContent(content);
                logger.info("Context [Persona] updated.");
                if (aiService != null) aiService.updatePersona(this.personaContext);
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
                    logger.warning("Image/Video file not found: " + content);
                    action.setResult(false);
                    return;
                }
            }
            action.setResult(true);
        } catch (IOException e) {
            logger.warning("Error processing context: " + e.getMessage());
            action.setResult(false);
        }
    }

    private void handleRemoveContext(ActionExec action) {
        String type = action.getActionTerm().getTerm(0).toString().replace("\"", "");

        if ("persona".equalsIgnoreCase(type)) {
            this.personaContext = "";
            logger.info("Context [Persona] removed.");
            if (aiService != null) aiService.updatePersona("");
        } else if ("image".equalsIgnoreCase(type)) {
            this.pendingImages.clear();
            logger.info("Context [Image] (pending queue) cleared.");
        }

        if (this.isThinking) {
            logger.info("Active session. Resetting system instructions...");
            aiService.getKQMLMessages(getAgName(), "SYSTEM UPDATE: Forget previous persona instructions. Revert to default neutral assistant behavior.");
        }
        action.setResult(true);
    }

    private void handleAskLlm(ActionExec action) {
        String userMessage = action.getActionTerm().getTerm(0).toString();
        logger.info("Dispatching question to LLM: " + truncateLog(userMessage));

        // Sends pending images along with the question and clears the queue
        List<String> imagesToSend = new ArrayList<>(this.pendingImages);
        this.pendingImages.clear();

        aiService.getKQMLMessages(getAgName(), userMessage, imagesToSend)
            .thenAccept(kqmlResponses -> {
                for (String msg : kqmlResponses) {
                    logger.info("[LLM Response] " + msg);
                    // Future: Inject perceptions here (e.g., addBel)
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

    private void handleReflectPlans(ActionExec action) {
        try {
            // Tenta obter todos os agentes via infraestrutura centralizada (Reflection para evitar dependência de compilação)
            Class<?> runnerClass = Class.forName("jason.infra.centralised.RunCentralisedMAS");
            Object runner = runnerClass.getMethod("getRunner").invoke(null);
            Map<?, ?> agents = (Map<?, ?>) runnerClass.getMethod("getAgs").invoke(runner);
            
            for (Object obj : agents.values()) {
                AgArch agArch = (AgArch) obj;
                Agent ag = agArch.getTS().getAg();
                String name = agArch.getAgName();
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
        if (aiService != null) aiService.endSession();
        super.stop();
    }

    // --- Helpers ---

    private String resolveContent(String content) throws IOException {
        Path path = Paths.get(content);
        return Files.exists(path) ? Files.readString(path) : content;
    }

    private String truncateLog(String text) {
        return (text.length() > 30) ? text.substring(0, 30) + "..." : text;
    }

    public void addMasContext(String content) {
        if (this.masContext.length() == 0) {
            this.masContext.append(content);
        } else {
            this.masContext.append("\n\n").append(content);
        }
    }
}