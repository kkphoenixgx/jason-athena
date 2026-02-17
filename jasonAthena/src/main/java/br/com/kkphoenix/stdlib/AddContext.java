package br.com.kkphoenix.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import br.com.kkphoenix.Athena;

/**
 * Internal Action: .addContext(Type, Content)
 * Adds context to the agent (mas, plans, image, video).
 */
public class AddContext extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (ts.getAgArch() instanceof Athena arch) {
            String type = args[0].toString().replace("\"", "");
            String content = args[1].toString().replace("\"", "");
            arch.addContext(type, content);
            return true;
        }
        throw new Exception("Agent architecture must be 'Athena' to use .addContext");
    }
}
