package br.com.kkphoenix.services.ollamaService.classes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the options that can be sent to the Ollama API.
 * This allows for fine-tuning model parameters like context window size.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaOptions(
    @JsonProperty("num_ctx") int numCtx
) {}