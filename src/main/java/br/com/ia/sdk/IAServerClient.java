package br.com.ia.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.model.IaRequest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * IA Server Client - Updated for Centralized Kafka Configuration
 * 
 * Now uses the centralized KafkaTemplate from IaKafkaConfiguration
 * instead of creating its own producer.
 */
@Component
@Slf4j
public class IAServerClient {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${ia.kafka.topics.requests:ia.requests}")
    private String iaRequestsTopic;

    @Value("${ia.base64.wrapper.enabled:true}")
    private boolean base64WrapperEnabled;

    @Value("${ia.test.chatid.prefix:test-}")
    private String testChatIdPrefix;

    @Value("${spring.application.name:workspace}")
    private String moduleName;

    // Constructor injection using the centralized KafkaTemplate
    public IAServerClient(ObjectMapper objectMapper, 
                         @Qualifier("iaKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("[IA-SERVER-CLIENT] Initialized - Module: {}, Topic: {}, Base64: {}", 
                moduleName, iaRequestsTopic, base64WrapperEnabled);
        log.info("[IA-SERVER-CLIENT] Using centralized KafkaTemplate from IaKafkaConfiguration");
    }

    /**
     * Sends IA Request to IAServer synchronously
     */
    public boolean sendIaRequest(IaRequest request) {
        try {
            log.info("[SEND-IA-REQUEST] Starting - ChatId: {}", request.getChatId());
            
            // Log request details for debugging
            if (log.isDebugEnabled()) {
                log.debug("[SEND-IA-REQUEST] Prompt length: {}, Options keys: {}",
                        request.getPrompt() != null ? request.getPrompt().length() : 0,
                        request.getOptions() != null ? request.getOptions().keySet() : "null");
                
                // Log context shards count if present
                Object contextShards = request.getOptions() != null ? 
                    request.getOptions().get(IaRequest.CONTEXT_SHARDS) : null;
                if (contextShards instanceof java.util.List) {
                    log.debug("[SEND-IA-REQUEST] Context shards count: {}", ((java.util.List<?>) contextShards).size());
                }
            }

            // Validate request first
            if (!isValidIaRequest(request)) {
                log.error("[SEND-IA-REQUEST] Invalid request for ChatId: {}", request.getChatId());
                return false;
            }

            // Adjust options for safety
            adjustSafeOptions(request);
            log.debug("[SEND-IA-REQUEST] Request validation and adjustment completed");

            // Wrap message in Base64 envelope
            String base64Message = wrapMessageToBase64(request, "IA_REQUEST");
            log.debug("[SEND-IA-REQUEST] Base64 wrapping completed - Length: {} chars", 
                     base64Message.length());

            // Send message using centralized KafkaTemplate
            boolean success = sendMessage(base64Message, request.getChatId());
            
            if (success) {
                log.info("[SEND-IA-REQUEST] SUCCESS - Message sent for ChatId: {}", request.getChatId());
            } else {
                log.error("[SEND-IA-REQUEST] FAILED - Could not send message for ChatId: {}", request.getChatId());
            }
            
            return success;

        } catch (Exception e) {
            log.error("[SEND-IA-REQUEST] EXCEPTION - ChatId: {}, Error: {}", 
                     request.getChatId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends connection test to IAServer
     */
    public boolean sendConnectionTest() {
        try {
            log.info("[CONNECTION-TEST] Starting from module: {}", moduleName);

            Map<String, Object> testMessage = Map.of(
                "chatId", testChatIdPrefix + System.currentTimeMillis(),
                "source", "connection-test",
                "test", true,
                "timestamp", System.currentTimeMillis(),
                "module", moduleName
            );

            String base64Message = wrapMessageToBase64(testMessage, "CONNECTION_TEST");
            boolean result = sendMessage(base64Message, "connection-test");
            
            log.info("[CONNECTION-TEST] Result: {}", result ? "SUCCESS" : "FAILED");
            return result;

        } catch (Exception e) {
            log.error("[CONNECTION-TEST] EXCEPTION from module {}: {}", 
                     moduleName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends startup notification to IAServer
     */
    public boolean sendStartupNotification() {
        try {
            log.info("[STARTUP-NOTIFICATION] Starting from module: {}", moduleName);

            Map<String, Object> startupMessage = Map.of(
                "source", moduleName + "-startup",
                "timestamp", System.currentTimeMillis(),
                "module", moduleName,
                "event", "MODULE_STARTUP"
            );

            String base64Message = wrapMessageToBase64(startupMessage, "STARTUP_TEST");
            boolean result = sendMessage(base64Message, "startup-test");
            
            log.info("[STARTUP-NOTIFICATION] Result: {}", result ? "SUCCESS" : "FAILED");
            return result;

        } catch (Exception e) {
            log.error("[STARTUP-NOTIFICATION] EXCEPTION from module {}: {}", 
                     moduleName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Health check method
     */
    public boolean isIAServerHealthy() {
        try {
            log.info("[HEALTH-CHECK] Testing IAServer connectivity...");
            boolean result = sendConnectionTest();
            log.info("[HEALTH-CHECK] IAServer is {}", result ? "HEALTHY" : "UNHEALTHY");
            return result;
        } catch (Exception e) {
            log.error("[HEALTH-CHECK] EXCEPTION: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send message using centralized KafkaTemplate
     */
    private boolean sendMessage(String base64Message, String chatId) {
        try {
            log.debug("[KAFKA-SEND] Sending message for ChatId: {}", chatId);

            // Synchronous send with blocking call
            SendResult<String, String> result = kafkaTemplate
                .send(iaRequestsTopic, chatId, base64Message)
                .get(); // This blocks until completion

            log.info("[KAFKA-SEND] SUCCESS - ChatId: {}, Topic: {}, Partition: {}, Offset: {}", 
                    chatId,
                    iaRequestsTopic,
                    result.getRecordMetadata().partition(), 
                    result.getRecordMetadata().offset());

            return true;

        } catch (Exception e) {
            log.error("[KAFKA-SEND] FAILED - ChatId: {}, Topic: {}, Error: {}", 
                     chatId, iaRequestsTopic, e.getMessage());
            log.debug("[KAFKA-SEND] Exception details:", e);
            return false;
        }
    }

    /**
     * Wraps any message in Base64 envelope
     */
    private String wrapMessageToBase64(Object message, String messageType) throws Exception {
        log.debug("[WRAP-BASE64] Type: {}, Base64 enabled: {}", messageType, base64WrapperEnabled);

        if (!base64WrapperEnabled) {
            log.debug("[WRAP-BASE64] Base64 disabled, using plain JSON");
            return objectMapper.writeValueAsString(message);
        }

        Map<String, Object> envelope = Map.of(
            "type", messageType,
            "timestamp", System.currentTimeMillis(),
            "module", moduleName,
            "payload", message
        );

        String json = objectMapper.writeValueAsString(envelope);
        log.debug("[WRAP-BASE64] JSON length: {} chars", json.length());

        String base64String = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        log.debug("[WRAP-BASE64] Base64 length: {} chars", base64String.length());
        
        return base64String;
    }

    /**
     * Validates IaRequest before sending
     */
    private boolean isValidIaRequest(IaRequest request) {
        if (request == null) {
            log.error("[VALIDATION] IaRequest is null");
            return false;
        }

        if (request.getChatId() == null || request.getChatId().trim().isEmpty()) {
            log.error("[VALIDATION] ChatId is null or empty");
            return false;
        }

        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            log.error("[VALIDATION] Prompt is null or empty for ChatId: {}", request.getChatId());
            return false;
        }

        if (request.getOptions() == null) {
            log.error("[VALIDATION] Options are null for ChatId: {}", request.getChatId());
            return false;
        }

        // Check for API key
        if (!request.getOptions().containsKey(IaRequest.API_KEY)) {
            log.error("[VALIDATION] API key is missing in options for ChatId: {}", request.getChatId());
            return false;
        }

        // Check for required model field
        if (!request.getOptions().containsKey(IaRequest.MODEL)) {
            log.error("[VALIDATION] Model is missing in options for ChatId: {}", request.getChatId());
            return false;
        }

        // Reject test chatIds for regular requests
        if (request.getChatId().startsWith("test-")) {
            log.warn("[VALIDATION] Rejecting test chatId for regular request: {}", request.getChatId());
            return false;
        }

        log.debug("[VALIDATION] Request validated successfully for ChatId: {}", request.getChatId());
        return true;
    }

    /**
     * Adjusts and validates request options for safety
     */
    private void adjustSafeOptions(IaRequest request) {
        if (request.getOptions() == null) {
            request.setOptions(new HashMap<>());
        }

        Map<String, Object> options = request.getOptions();

        // Safely get and adjust max output tokens
        Object maxTokensObj = options.get(IaRequest.MAX_OUTPUT_TOKENS);
        int maxOutputTokens = safeTokens(maxTokensObj instanceof Number ? 
            ((Number) maxTokensObj).intValue() : 1024);

        // Safely get and adjust temperature
        Object temperatureObj = options.get(IaRequest.TEMPERATURE);
        double temperature = safeTemperature(temperatureObj instanceof Number ? 
            ((Number) temperatureObj).doubleValue() : 0.3);

        // Update options with safe values
        Map<String, Object> correctedOptions = new HashMap<>(options);
        correctedOptions.put(IaRequest.MAX_OUTPUT_TOKENS, maxOutputTokens);
        correctedOptions.put(IaRequest.TEMPERATURE, temperature);

        request.setOptions(correctedOptions);
        
        log.debug("[ADJUST-OPTIONS] ChatId: {}, Tokens: {}, Temperature: {}", 
                 request.getChatId(), maxOutputTokens, temperature);
    }

    private int safeTokens(Object tokensObj) {
        int tokens = 1024;
        if (tokensObj instanceof Number) {
            tokens = ((Number) tokensObj).intValue();
        }
        if (tokens <= 0) tokens = 1024;
        if (tokens > 4096) tokens = 4096;
        return tokens;
    }

    private double safeTemperature(Object temperatureObj) {
        double temp = 0.3;
        if (temperatureObj instanceof Number) {
            temp = ((Number) temperatureObj).doubleValue();
        }
        if (temp < 0) temp = 0.0;
        if (temp > 2.0) temp = 2.0;
        return temp;
    }
}