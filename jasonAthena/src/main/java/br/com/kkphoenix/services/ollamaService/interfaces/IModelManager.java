package br.com.kkphoenix.services.ollamaService.interfaces;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import br.com.kkphoenix.services.ollamaService.classes.TranslationResult;

public interface IModelManager {
  /** Initializes a user session with the base context and agent-specific plans. */
  int initializeUserSession(String sessionId, String agentPlans);
  
  /** Translates a user message into a list of KQML commands using the session context. */
  CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage);
  
  /** Translates a user message including images. */
  CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage, List<String> images);

  /** Updates the persona context for the current model. */
  void setPersonaContext(String personaContext);

  /** Updates the context template. */
  void setContextTemplate(String contextTemplate);

  /** Ends the AI model session for the given session ID. */
  void endSession(String sessionId);
}
