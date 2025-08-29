package br.com.ia.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
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
            Map<String, Object> envelope = Map.of(
                "type", messageType,
                "timestamp", System.currentTimeMillis(),
                "payload", sanitize(message) // <<-- sanitize aqui
            );

            String json = mapper.writeValueAsString(envelope);
            String base64 = Base64.getEncoder()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));

            log.debug("Message wrapped in Base64 - Type: {}", messageType);
            return base64;

        } catch (Exception e) {
            log.error("Error creating Base64 envelope: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Base64 envelope", e);
        }
    }

    /**
     * Sanitiza estruturas para algo serializável por Jackson
     */
    private Object sanitize(Object o) {
        if (o == null ||
            o instanceof String ||
            o instanceof Number ||
            o instanceof Boolean) {
            return o;
        }

        // ConsumerRecord -> metadados + value
        if (o instanceof ConsumerRecord<?, ?> r) {
            return Map.of(
                "topic", r.topic(),
                "partition", r.partition(),
                "offset", r.offset(),
                "timestamp", r.timestamp(),
                "key", r.key() != null ? String.valueOf(r.key()) : null,
                "headers", headersToMap(r),
                "value", sanitize(r.value()) // pode ser String/POJO/byte[]
            );
        }

        // Map genérico
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), sanitize(v)));
            return out;
        }

        // List genérica
        if (o instanceof java.util.List<?> list) {
            return list.stream().map(this::sanitize).toList();
        }

        // byte[] já é serializável como Base64 por Jackson, mas se preferir String:
        if (o instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }

        // Como fallback: toString (evita FAIL_ON_EMPTY_BEANS)
        return String.valueOf(o);
    }

    private Map<String, String> headersToMap(ConsumerRecord<?, ?> r) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        r.headers().forEach(h -> {
            byte[] v = h.value();
            m.put(h.key(), v != null ? new String(v, StandardCharsets.UTF_8) : null);
        });
        return m;
    }

    public Map<String, Object> unwrapFromBase64(String base64Message) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Message);
            String json = new String(decoded, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = mapper.readValue(json, Map.class);
            log.debug("Base64 message decoded - Type: {}", envelope.get("type"));
            return envelope;
        } catch (Exception e) {
            log.error("Error decoding Base64: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean isBase64(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        try {
            return s.matches("^[A-Za-z0-9+/]+={0,2}$") && (s.length() % 4 == 0);
        } catch (Exception e) {
            return false;
        }
    }
}
