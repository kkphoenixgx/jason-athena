package br.com.kkphoenix.services.ollamaService.classes;

import com.fasterxml.jackson.annotation.JsonProperty;

// @JsonIgnoreProperties is crucial because the response has many fields we are not interested in
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record IAGenerateResponse(
    String response, 
    long[] context, 
    @JsonProperty("prompt_eval_count") int promptEvalCount,
    @JsonProperty("eval_count") int evalCount,
    @JsonProperty("total_duration") long totalDuration,
    @JsonProperty("load_duration") long loadDuration,
    @JsonProperty("eval_duration") long evalDuration
) {
}