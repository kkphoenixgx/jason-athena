package br.com.kkphoenix.services.ollamaService.interfaces;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import br.com.kkphoenix.services.ollamaService.classes.TranslationResult;

public interface IModelManager {

  /** Initializes a user session with a raw prompt. */
  int initializeSession(String sessionId, String prompt);
  
  /** Translates a user message into a list of KQML commands using the session context. */
  CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage);
  
  /** Translates a user message including images. */
  CompletableFuture<TranslationResult> translateMessage(String sessionId, String modelOverride, String userMessage, String systemPrompt, List<String> images);

  /** Ends the AI model session for the given session ID. */
  void endSession(String sessionId);
}
