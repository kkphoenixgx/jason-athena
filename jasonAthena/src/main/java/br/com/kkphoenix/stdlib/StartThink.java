package br.com.kkphoenix.stdlib;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;
import jason.asSyntax.StringTerm;
import br.com.kkphoenix.Athena;

/**
 * Internal Action: .startThink
 * Triggers AI context initialization and adds the 'incorporated' belief when ready.
 */
public class StartThink extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (ts.getAgArch() instanceof Athena) {
            Athena arch = (Athena) ts.getAgArch();
            
            String model = null;
            if (args.length > 0) {
                if (args[0].isString()) {
                    model = ((StringTerm) args[0]).getString();
                } else {
                    model = args[0].toString().replace("\"", "");
                }
            }
            
            arch.startThinking(model);
            return true;
        }
        throw new Exception("Agent architecture must be 'Athena' to use .startThink");
    }
}