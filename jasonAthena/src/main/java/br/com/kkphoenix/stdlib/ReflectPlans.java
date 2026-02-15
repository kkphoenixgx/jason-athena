package br.com.kkphoenix.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import br.com.kkphoenix.Athena;
import br.com.kkphoenix.util.FileUtil;
import java.util.logging.Logger;

/**
 * Internal Action: .reflectPlans
 * Reads the agent's source code (.asl) and sets it as MAS-Context.
 * Preserves comments and original formatting, useful for the LLM.
 */
public class ReflectPlans extends DefaultInternalAction {

    private static final Logger logger = Logger.getLogger(ReflectPlans.class.getName());

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (ts.getAgArch() instanceof Athena arch) {
            String agentSource = ts.getAg().getASLSrc();
            
            if (agentSource != null && !agentSource.isEmpty()) {
                try {
                    String content = FileUtil.readFileAsString(agentSource);
                    arch.addMasContext(content);
                    logger.info("Plans reflected from source file: " + agentSource);
                    return true;
                } catch (Exception e) {
                    logger.warning("Failed to read source file (" + e.getMessage() + "). Using default Plan Library.");
                }
            }
            
            // Fallback: Uses Jason's internal representation (no comments)
            arch.addMasContext(ts.getAg().getPL().toString());
            logger.info("Plans reflected from memory (Plan Library).");
            return true;
        }
        throw new Exception("Agent architecture must be 'Athena' to use .reflectPlans");
    }
}