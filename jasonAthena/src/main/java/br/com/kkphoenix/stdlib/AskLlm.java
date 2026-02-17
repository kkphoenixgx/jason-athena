package br.com.kkphoenix.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.Intention;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.StringTermImpl;
import jason.asSyntax.Term;
import br.com.kkphoenix.Athena;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal Action: .ask_llm(Question, [Result])
 * Asks the LLM a question asynchronously.
 * Suspends the intention until the answer is received.
 */
public class AskLlm extends DefaultInternalAction {

    @Override
    public boolean suspendIntention() {
        return true;
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (!(ts.getAgArch() instanceof Athena)) {
            throw new Exception("Agent architecture must be 'Athena' to use .ask_llm");
        }

        Athena arch = (Athena) ts.getAgArch();
        String userMessage = args[0].toString().replace("\"", "");
        
        // Capture context for async callback
        Intention intention = ts.getC().getSelectedIntention();
        
        // Prepare images
        List<String> imagesToSend = new ArrayList<>(arch.getPendingImages());
        arch.clearPendingImages();

        // Async Call
        arch.getAiService().ask(arch.getAgName(), userMessage, arch.getPersonaContext(), imagesToSend)
            .thenAccept(responses -> {
                if (!responses.isEmpty()) {
                    // Unify result if variable provided
                    if (args.length > 1) {
                        String combinedResponse = String.join("\n", responses);
                        un.unifies(args[1], new StringTermImpl(combinedResponse));
                    }
                }
                // Resume the intention
                ts.getC().addRunningIntention(intention);
                arch.wake(); // Wake up the agent cycle
            })
            .exceptionally(e -> {
                ts.getLogger().warning("Error in .ask_llm: " + e.getMessage());
                // Resume even on error to avoid stuck intention (could unify with "error" if needed)
                ts.getC().addRunningIntention(intention);
                arch.wake();
                return null;
            });

        return true; // Intention suspended
    }
}
