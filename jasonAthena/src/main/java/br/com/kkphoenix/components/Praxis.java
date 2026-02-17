package br.com.kkphoenix.components;

import jason.asSemantics.Agent;
import jason.asSemantics.Intention;
import jason.asSyntax.Literal;
import jason.asSyntax.Plan;
import jason.asSyntax.Pred;
import jason.asSyntax.Trigger;
import jason.asSyntax.Trigger.TEOperator;
import jason.asSyntax.Trigger.TEType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import br.com.kkphoenix.Athena;

/**
 * PRAXIS (The Injector)
 * Responsible for validation, callback injection, and memory management.
 * Acts as the bridge between the cognitive output (String) and the agent's mind (BDI).
 */
public class Praxis {

    private static final Logger logger = LoggerFactory.getLogger(Praxis.class);
    private final Athena arch;
    
    // Optimization: Pre-compiled literal for frequent lookups
    private static final Literal EPHEMERAL_ANNOT = Literal.parseLiteral("type(athena_ephemeral)");

    // Memory Management v3.2
    private final Map<String, AtomicInteger> usageTracker = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Policies
    private boolean napEnabled = false;
    private int napKeep = 0;
    private double napThreshold = 0.0;
    
    private boolean collectorEnabled = false;

    public Praxis(Athena arch) {
        this.arch = arch;
        startRamMonitor();
    }

    /**
     * Validates and injects the cognitive result into the agent.
     * Handles Plans (<-), Goals (!), Beliefs (+/-), and inferred commands.
     * @param rawOutput The output from Syllabus or direct directive.
     */
    public void inject(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) return;

        Agent ag = arch.getTS().getAg();
        
        rawOutput.lines().forEach(line -> {
            line = line.trim();
            if (line.isEmpty()) return;

            try {
                // 1. Try parsing as a Plan (contains "<-")
                if (line.contains("<-")) {
                    // Spec 4.4 Compliance: Auto-correct "!trigger" to "+!trigger" for plans
                    String planLine = line.trim();
                    if (planLine.startsWith("!")) {
                        planLine = "+" + planLine;
                    }
                    Plan p = Plan.parse(planLine);

                    // REVISOR 2 FIX: Removed dangerous synchronization on 'ag'.
                    // Instead of modifying PL directly, we should use Jason's infrastructure if possible,
                    // but since Plan Library isn't thread-safe, we must minimize the lock window 
                    // or defer to the agent thread. 
                    // For now, we will keep the lock but strictly for the check, NOT for the parsing logic above.
                    
                    // Better approach: Check existence is hard without lock. 
                    // We will assume the agent cycle handles the addition if we use a safe method.
                    // However, Jason doesn't have a thread-safe 'addPlan' queue exposed easily.
                    // We will optimize the lock to be as short as possible.
                    
                    synchronized (ag.getPL()) { // Lock only the Plan Library, not the whole Agent
                        Plan existing = findEquivalentEphemeralPlan(ag, p);
                        if (existing != null) {
                            String labelKey = existing.getLabel().toString();
                            trackUsage(labelKey);
                            return;
                        }

                        Pred label = p.getLabel();
                        if (label == null) {
                            label = (Pred) Literal.parseLiteral("athena_" + System.nanoTime());
                        }
                        label.addSource(Literal.parseLiteral("athena"));
                        label.addAnnot(Literal.parseLiteral("rationale(\"cognitive_inference\")"));
                        label.addAnnot(EPHEMERAL_ANNOT.copy()); // Use copy to avoid sharing mutable terms
                        trackUsage(label.toString());
                        p.setLabel(label);
                        ag.getPL().add(p);
                    }
                    logger.info("Athena blesses you with Intelligence: [PLAN] " + p.getTrigger());
                    return;
                }

                // 2. Try parsing as a Trigger (Event: +!, -!, +, -)
                if (line.startsWith("!") || line.startsWith("+") || line.startsWith("-")) {
                    // QA Fix: '!' is not a valid trigger start in Jason parser (must be +! or -!).
                    // We auto-correct '!goal' to '+!goal'.
                    String parsableLine = line.startsWith("!") ? "+" + line : line;
                    
                    Trigger te = Trigger.parseTrigger(parsableLine);
                    if (te != null) {
                        te.getLiteral().addSource(Literal.parseLiteral("athena"));
                        te.getLiteral().addAnnot(Literal.parseLiteral("rationale(\"cognitive_inference\")"));
                        arch.getTS().getC().addEvent(new jason.asSemantics.Event(te, new Intention()));
                        logger.info("Praxis has spoken. Order broadcasted to swarm: [EVENT] " + te);
                        return;
                    }
                }
                
                // 3. Fallback: Inferred Goal (+!) for bare commands (e.g., "patrol" -> "+!patrol")
                // This aligns with "Verb Supremacy": commands are usually goals to be achieved.
                try {
                    Trigger te = Trigger.parseTrigger("+!" + line);
                    te.getLiteral().addSource(Literal.parseLiteral("athena"));
                    te.getLiteral().addAnnot(Literal.parseLiteral("rationale(\"cognitive_inference\")"));
                    arch.getTS().getC().addEvent(new jason.asSemantics.Event(te, new Intention()));
                    logger.info("Praxis has spoken. Order broadcasted to swarm: [GOAL] " + te);
                } catch (Exception e) {
                    // 4. Last Resort: Treat as simple Belief
                    Literal bel = Literal.parseLiteral(line);
                    bel.addSource(Literal.parseLiteral("athena"));
                    bel.addAnnot(Literal.parseLiteral("rationale(\"cognitive_inference\")"));
                    
                    Trigger te = new Trigger(TEOperator.add, TEType.belief, bel);
                    arch.getTS().getC().addEvent(new jason.asSemantics.Event(te, new Intention()));
                    logger.info("Athena blesses you with Intelligence: [BELIEF] " + bel);
                }

            } catch (Exception e) {
                logger.error("Praxis failed to process line: " + line + " (" + e.getMessage() + ")");
            }
        });
    }

    /** v3.2: Tracks usage of ephemeral items. */
    private void trackUsage(String key) {
        usageTracker.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /** v3.2: Configures NAP mode (Critical RAM cleanup). */
    public void configureNap(int keepN, double ramPercentage) {
        this.napKeep = keepN;
        this.napThreshold = ramPercentage / 100.0;
        this.napEnabled = true;
        logger.info("Praxis NAP Policy activated: Keep " + keepN + " items if RAM > " + ramPercentage + "%");
    }

    /** v3.2: Configures Garbage Counter Collector (Frequency cleanup). */
    public void configureCollector(int keepN, int minutes) {
        this.collectorEnabled = true;
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("Praxis Collector: Running scheduled cleanup...");
            runGc(keepN, false); // Collector just prunes, does not promote
        }, minutes, minutes, TimeUnit.MINUTES);
        logger.info("Praxis Collector Policy activated: Keep " + keepN + " items every " + minutes + " min.");
    }

    private void startRamMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!napEnabled) return;
            
            // FIX: "The Heap Fallacy" - Monitor System RAM (Linux), not just Java Heap
            try {
                double systemUsage = getSystemRamUsage();
                
                if (systemUsage > napThreshold) {
                    logger.warn("Praxis NAP: SYSTEM RAM usage critical (" + String.format("%.2f", systemUsage*100) + "%). Initiating emergency protocols...");
                    
                    // 1. Soft Clean (Internal GC)
                    runGc(napKeep, true); // Nap promotes survivors to save them before crash
                    
                    // 2. Hard Check: If still critical after GC, kill the heaviest external process (Ollama)
                    // We give it a second to reflect GC changes, though OS stats lag.
                    if (getSystemRamUsage() > 0.95) { // 95% Panic Threshold
                        logger.error("Praxis DEFCON 1: RAM at 95%+. Killing Ollama to save the Agent.");
                        killOllama();
                    }
                }
            } catch (Exception e) {
                logger.error("Praxis RAM Monitor failed: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private double getSystemRamUsage() throws java.io.IOException {
        // Reads /proc/meminfo for Linux RPi 5 compatibility
        List<String> lines = Files.readAllLines(Paths.get("/proc/meminfo"));
        long total = 0, available = 0;
        
        for (String line : lines) {
            if (line.startsWith("MemTotal:")) total = parseKb(line);
            if (line.startsWith("MemAvailable:")) available = parseKb(line);
        }
        
        if (total == 0) return 0.0;
        return 1.0 - ((double) available / total);
    }

    private long parseKb(String line) {
        String num = line.replaceAll("[^0-9]", "");
        return Long.parseLong(num);
    }

    private void killOllama() {
        try {
            // Safety-Critical: Force kill to prevent OOM Killer from taking out the JVM
            new ProcessBuilder("pkill", "-f", "ollama").start();
            // Notify architecture to restart service on next demand
            arch.stopThinking(); 
        } catch (Exception e) {
            logger.error("Failed to execute kill switch", e);
        }
    }

    /** Executes the Garbage Collection logic. */
    private synchronized void runGc(int keepN, boolean promote) {
        try {
            Agent ag = arch.getTS().getAg();
            List<Plan> ephemeralPlans = new ArrayList<>();
            
            // REVISOR 2 FIX: Lock only Plan Library
            synchronized (ag.getPL()) {
                Iterator<Plan> it = ag.getPL().iterator();
                
                // 1. Identify Ephemeral Plans
                while(it.hasNext()) {
                    Plan p = it.next();
                    if (p.getLabel() != null && p.getLabel().hasAnnot(EPHEMERAL_ANNOT)) {
                        ephemeralPlans.add(p);
                    }
                }
                
                // 2. Sort by Usage (Descending)
                ephemeralPlans.sort((p1, p2) -> {
                    int u1 = usageTracker.getOrDefault(p1.getLabel().toString(), new AtomicInteger(0)).get();
                    int u2 = usageTracker.getOrDefault(p2.getLabel().toString(), new AtomicInteger(0)).get();
                    return Integer.compare(u2, u1);
                });
                
                // 3. Remove Excess (The "Fire of Straw" plans)
                if (ephemeralPlans.size() > keepN) {
                    List<Plan> toRemove = ephemeralPlans.subList(keepN, ephemeralPlans.size());
                    for (Plan p : toRemove) {
                        ag.getPL().remove(p.getLabel());
                        usageTracker.remove(p.getLabel().toString());
                    }
                    logger.info("Praxis GC: Removed " + toRemove.size() + " ephemeral plans. Kept top " + keepN + ".");
                }

                // 4. Promote Survivors (Nap Mode - Spec 4.5.1)
                if (promote) {
                    // Survivors are the first 'keepN' elements (or all if size < keepN)
                    int limit = Math.min(ephemeralPlans.size(), keepN);
                    List<Plan> survivors = ephemeralPlans.subList(0, limit);
                    
                    for (Plan p : survivors) {
                        if (p.getLabel().delAnnot(EPHEMERAL_ANNOT)) {
                            usageTracker.remove(p.getLabel().toString());
                        }
                    }
                    if (!survivors.isEmpty()) logger.info("Praxis NAP: Promoted " + survivors.size() + " plans to permanent memory.");
                }
            }
        } catch (Exception e) {
            logger.error("Praxis GC failed", e);
        }
    }

    /** Helper to find if an equivalent ephemeral plan already exists in the library. */
    private Plan findEquivalentEphemeralPlan(Agent ag, Plan newPlan) {
        Iterator<Plan> it = ag.getPL().iterator();
        while (it.hasNext()) {
            Plan p = it.next();
            if (p.getLabel() != null && p.getLabel().hasAnnot(EPHEMERAL_ANNOT)) {
                // QA Fix: Must check Context equality as well. 
                // Plans with same trigger/body but different contexts (e.g. : day vs : night) are DIFFERENT plans.
                boolean contextMatch = (p.getContext() == null && newPlan.getContext() == null) ||
                                       (p.getContext() != null && p.getContext().equals(newPlan.getContext()));
                
                if (p.getTrigger().equals(newPlan.getTrigger()) && contextMatch && p.getBody().equals(newPlan.getBody())) {
                    return p;
                }
            }
        }
        return null;
    }
}
