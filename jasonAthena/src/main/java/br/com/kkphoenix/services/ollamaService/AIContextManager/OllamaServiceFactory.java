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
     * @param sessionId The session ID for this service.
     * @param modelOverride Optional model name to override the default configuration.
     * @return A ready-to-use instance of AIService.
     */
    public static AIService createService(String sessionId, String modelOverride) {
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
            
            String contextPath = props.getProperty("ollama.model.context-path");

            // 3. Load Context Template
            String templateContent = "";
            if (contextPath != null && contextPath.startsWith("classpath:")) {
                String resourcePath = contextPath.replace("classpath:", "");
                // Remove leading slash if exists to ensure compatibility with getResourceAsStream
                if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
                
                try (InputStream is = OllamaServiceFactory.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is == null) throw new IOException("Template not found: " + resourcePath);
                    templateContent = FileUtil.readStreamAsString(is);
                }
            } else {
                throw new IllegalArgumentException("Context path must start with 'classpath:' in this framework.");
            }

            // 4. Instantiate Dependencies
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            // 5. Assemble Manager and Service
            IModelManager manager = new OllamaManager(client, mapper, ollamaUrl, modelName, templateContent);
            
            // EMBEDDED OPTIMIZATION: Use FixedThreadPool to avoid resource exhaustion (CPU/RAM)
            return new AIService(manager, sessionId, Executors.newFixedThreadPool(2));

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize OllamaServiceFactory", e);
        }
    }

    public static AIService createService(String sessionId) {
        return createService(sessionId, null);
    }
}