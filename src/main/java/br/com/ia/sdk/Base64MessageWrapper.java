package br.com.ia.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class Base64MessageWrapper {
    
    private final ObjectMapper mapper;
    
    /**
     * Wraps any object in Base64 envelope
     */
    public String wrapToBase64(Object message, String messageType) {
        try {
            // Create standardized envelope
            Map<String, Object> envelope = Map.of(
                "type", messageType,
                "timestamp", System.currentTimeMillis(),
                "payload", message
            );
            
            // Serialize to JSON
            String json = mapper.writeValueAsString(envelope);
            
            // Convert to Base64
            String base64 = Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8)
            );
            
            log.debug("Message wrapped in Base64 - Type: {}", messageType);
            return base64;
            
        } catch (Exception e) {
            log.error("Error creating Base64 envelope: {}", e.getMessage());
            throw new RuntimeException("Failed to create Base64 envelope", e);
        }
    }
    
    /**
     * Unwraps Base64 message to object
     */
    public Map<String, Object> unwrapFromBase64(String base64Message) {
        try {
            // Decode Base64
            byte[] decoded = Base64.getDecoder().decode(base64Message);
            String json = new String(decoded, StandardCharsets.UTF_8);
            
            // Parse JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = mapper.readValue(json, Map.class);
            
            String type = (String) envelope.get("type");
            log.debug("Base64 message decoded - Type: {}", type);
            
            return envelope;
            
        } catch (Exception e) {
            log.error("Error decoding Base64: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if string looks like Base64
     */
    public boolean isBase64(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        try {
            return s.matches("^[A-Za-z0-9+/]+={0,2}$") && (s.length() % 4 == 0);
        } catch (Exception e) {
            return false;
        }
    }
}