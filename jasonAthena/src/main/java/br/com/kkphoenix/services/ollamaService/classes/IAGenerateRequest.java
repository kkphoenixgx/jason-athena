package br.com.kkphoenix.services.ollamaService.classes;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 
 * Represents the JSON request body sent to the Ollama /api/generate endpoint. 
 * It includes the model name, prompt, and optional parameters like context and options. 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IAGenerateRequest(
    String model,
    String prompt,
    String system,
    long[] context,
    OllamaOptions options,
    @JsonProperty("stream") boolean stream,
    @JsonProperty("think") Boolean think,
    @JsonProperty("keep_alive") String keepAlive,
    @JsonProperty("images") List<String> images
) {
    
    // Simplified constructor for initialization (no images, no extra system prompt)
    public IAGenerateRequest(String model, String prompt, long[] context, OllamaOptions options) {
        this(model, prompt, null, context, options, false, false, "5m", null);
    }

    // Simplified constructor for simple prompt (no previous context)
    public IAGenerateRequest(String model, String prompt, OllamaOptions options) {
        this(model, prompt, null, null, options, false, false, "5m", null);
    }

    // Constructor for requests with images and context
    public IAGenerateRequest(String model, String prompt, long[] context, OllamaOptions options, List<String> images) {
        this(model, prompt, null, context, options, false, false, "5m", images);
    }

    // Full constructor with explicit System Prompt
    public IAGenerateRequest(String model, String prompt, String system, long[] context, OllamaOptions options, List<String> images) {
        this(model, prompt, system, context, options, false, false, "5m", images);
    }
}
