package br.com.kkphoenix.services.ollamaService.AIContextManager;


import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.kkphoenix.services.ollamaService.AIService;
import br.com.kkphoenix.services.ollamaService.interfaces.IModelManager;
import br.com.kkphoenix.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Properties;
import java.util.concurrent.Executors;

public class OllamaServiceFactory {

    /**
     * Creates a fully configured instance of AIService reading from application.properties.
     * @param modelOverride Optional model name to override the default configuration.
     * @return A ready-to-use instance of AIService.
     */
    public static AIService createService(String modelOverride) {
        try {
            // 1. Load Properties
            Properties props = new Properties();
            try (InputStream input = OllamaServiceFactory.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (input == null) {
                    throw new RuntimeException("application.properties file not found in classpath.");
                }
                props.load(input);
            }

            // 2. Extract Configurations
            String ollamaUrl = props.getProperty("ollama.api.url", "http://localhost:11434/api/generate");
            String modelName = props.getProperty("ollama.model.name", "ministral-3:3b");
            
            if (modelOverride != null && !modelOverride.isEmpty()) {
                modelName = modelOverride;
            }
            
            // 4. Instantiate Dependencies
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            // 5. Assemble Manager and Service
            IModelManager manager = new OllamaManager(client, mapper, ollamaUrl, modelName);
            
            // EMBEDDED OPTIMIZATION: Use FixedThreadPool to avoid resource exhaustion (CPU/RAM)
            return new AIService(manager, Executors.newFixedThreadPool(2));

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize OllamaServiceFactory", e);
        }
    }

    public static AIService createService() {
        return createService(null);
    }
}