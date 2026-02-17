package br.com.kkphoenix.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import br.com.kkphoenix.Athena;

/**
 * Internal Action: .addPersona(Content)
 * Sets the agent's persona context. Content can be a string or a file path.
 */
public class AddPersona extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (ts.getAgArch() instanceof Athena arch) {
            String content = args[0].toString().replace("\"", "");
            arch.setPersona(content);
            return true;
        }
        throw new Exception("Agent architecture must be 'Athena' to use .addPersona");
    }
}
