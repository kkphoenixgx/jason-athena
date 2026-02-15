package br.com.kkphoenix.services.ollamaService.classes;

import java.util.List;

public class TranslationResult {
    private final List<String> kqmlMessages;
    private final int promptEvalCount;
    private final int evalCount;
    private final long totalDuration;
    private final long loadDuration;
    private final long evalDuration;

    public TranslationResult(List<String> kqmlMessages, int promptEvalCount, int evalCount, long totalDuration, long loadDuration, long evalDuration) {
        this.kqmlMessages = kqmlMessages;
        this.promptEvalCount = promptEvalCount;
        this.evalCount = evalCount;
        this.totalDuration = totalDuration;
        this.loadDuration = loadDuration;
        this.evalDuration = evalDuration;
    }

    public List<String> getKqmlMessages() {
        return kqmlMessages;
    }

    public int getPromptEvalCount() {
        return promptEvalCount;
    }

    public int getEvalCount() {
        return evalCount;
    }

    public long getTotalDuration() {
        return totalDuration;
    }

    public long getLoadDuration() {
        return loadDuration;
    }

    public long getEvalDuration() {
        return evalDuration;
    }
}