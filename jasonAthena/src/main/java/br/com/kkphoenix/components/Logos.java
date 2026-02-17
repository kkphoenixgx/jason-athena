package br.com.kkphoenix.components;

import jason.asSemantics.Agent;
import jason.asSyntax.Literal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import br.com.kkphoenix.Athena;

/**
 * LOGOS (The Observer)
 * Monitors agent state (idle time, sensor triggers) and dispatches cognitive tasks
 * to Syllabus without blocking the main agent thread.
 */
public class Logos {
    private static final Logger logger = Logger.getLogger(Logos.class.getName());
    private final Athena arch;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    private final long timeoutMs;
    private final List<String> triggers;
    private final List<String> criticalTriggers;
    private final List<String> postPlans;
    
    private long lastActivityTime;

    public Logos(Athena arch, long timeoutMs, List<String> triggers, List<String> criticalTriggers, List<String> postPlans) {
        this.arch = arch;
        this.timeoutMs = timeoutMs;
        this.triggers = triggers;
        this.criticalTriggers = criticalTriggers;
        this.postPlans = postPlans;
        this.lastActivityTime = System.currentTimeMillis();
        
        logger.info("Logos initialized. Timeout: " + timeoutMs + "ms. Triggers: " + triggers + " (Critical: " + criticalTriggers + ")");
        startMonitoring();
    }
    
    public void notifyActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long idleTime = System.currentTimeMillis() - lastActivityTime;
                boolean isIdle = idleTime > timeoutMs;
                String firedTrigger = checkTriggers(triggers);
                String firedCritical = checkTriggers(criticalTriggers);
                
                boolean triggerFired = (firedTrigger != null);
                boolean criticalFired = (firedCritical != null);

                // FIX: Priority Handling (The 30-Second Blindness)
                // Safety Check: Syllabus might not be initialized yet if .startThink wasn't called
                if (arch.getSyllabus() == null) return;

                if (arch.getSyllabus().isBusy()) {
                    if (criticalFired) {
                        logger.warning("Logos: CRITICAL TRIGGER detected (" + firedCritical + "). Preempting current thought.");
                        arch.getSyllabus().cancelCurrentTask();
                        // Proceed to dispatch new task immediately
                    } else {
                        return; // Ignore normal triggers while busy
                    }
                }
                
                if (isIdle || triggerFired || criticalFired) {
                    notifyActivity(); // QA Fix: Reset timer to prevent immediate re-triggering loop if agent remains idle
                    String reason = criticalFired ? "CRITICAL TRIGGER: " + firedCritical : 
                                   (triggerFired ? "Trigger: " + firedTrigger : "Agent is idle.");
                    
                    logger.info("Logos activating Syllabus. Reason: " + reason);

                    // Dispatch to Syllabus (Async)
                    arch.getSyllabus().processKQMLSemanticParsing(
                        arch.getPersonaContext(),
                        arch.getMasContext(),
                        "System Alert: " + reason + " What should I do?",
                        new ArrayList<>(), // No images for background thought
                        null // Use default model
                    ).thenAccept(result -> {
                        // Callback to Praxis
                        if (arch.getPraxis() != null) {
                            arch.getPraxis().inject(result);
                            executePostPlans();
                        }
                    });
                }
            } catch (Exception e) {
                logger.warning("Logos monitoring error: " + e.getMessage());
            }
        }, 1000, 500, TimeUnit.MILLISECONDS);
    }
    
    private String checkTriggers(List<String> listToCheck) {
        if (listToCheck == null || listToCheck.isEmpty()) return null;
        Agent ag = arch.getTS().getAg();
        
        // REVISOR 2 FIX: Do not lock the whole agent. Belief Base (BB) usually has its own lock or is safer to read.
        // We accept a small risk of dirty read to avoid freezing the agent.
        synchronized (ag.getBB()) {
            return listToCheck.stream()
                .filter(t -> {
                    try {
                        // Safety-Critical: Handle invalid trigger strings gracefully
                        Literal l = Literal.parseLiteral(t);
                        return ag.findBel(l, null) != null;
                    } catch (Exception e) {
                        logger.warning("Logos ignored invalid trigger syntax: " + t);
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
        }
    }

    private void executePostPlans() {
        if (postPlans == null || postPlans.isEmpty()) return;
        for (String plan : postPlans) {
            // Inject post-cognition plans as goals (e.g., !update_dashboard)
            // We use Praxis to inject them to ensure consistency and logging
            // QA Fix: Use '+!' (Achievement Goal Addition) instead of raw '!' to ensure Praxis parsing
            String command = plan.startsWith("+!") ? plan : "+!" + (plan.startsWith("!") ? plan.substring(1) : plan);
            arch.getPraxis().inject(command);
        }
    }
    
    public void shutdown() {
        scheduler.shutdownNow();
    }
}