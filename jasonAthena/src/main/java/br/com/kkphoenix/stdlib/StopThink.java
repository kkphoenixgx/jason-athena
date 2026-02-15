package br.com.kkphoenix.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import br.com.kkphoenix.Athena;

/**
 * Internal Action: .stopThink
 * Ends the AI session and removes the 'incorporated' belief.
 */
public class StopThink extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (ts.getAgArch() instanceof Athena) {
            ((Athena) ts.getAgArch()).stopThinking();
            return true;
        }
        throw new Exception("Agent architecture must be 'Athena' to use .stopThink");
    }
}