package br.com.kkphoenix.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import br.com.kkphoenix.Athena;

/**
 * Internal Action: .removeContext(Type)
 * Removes a specific type of context (persona, image).
 */
public class RemoveContext extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (ts.getAgArch() instanceof Athena arch) {
            String type = args[0].toString().replace("\"", "");
            arch.removeContext(type);
            return true;
        }
        throw new Exception("Agent architecture must be 'Athena' to use .removeContext");
    }
}
