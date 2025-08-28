package br.com.ia.processor;

import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import br.com.ia.config.IaServerProperties;
import br.com.ia.sdk.Base64MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IaReplyPublisher {

    private final IaServerProperties props;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Base64MessageWrapper wrapper;

    public void sendWrapped(Object payload, String type) {
        try {
            String msg = wrapper.wrapToBase64(payload, type);
            sendRaw(msg);
        } catch (Exception e) {
            log.error("[PUBLISHER] wrap/send error: {}", e.getMessage(), e);
        }
    }

    public void sendProcessingResponse(String status, String reason, String message, Object originalData) {
        Map<String, Object> resp = Map.of(
            "status", status,
            "reason", reason,
            "message", message,
            "timestamp", System.currentTimeMillis(),
            "processor", "IaProcessor",
            "originalData", originalData != null ? originalData : Map.of()
        );
        sendWrapped(resp, "PROCESSING_RESPONSE");
    }

    public void sendError(String reason, String message) {
        sendProcessingResponse("error", reason, message, null);
    }

    public void sendRaw(String msg) {
        String topic = props.getKafkaTopicResponses();
        kafkaTemplate.send(topic, msg).whenComplete((res, ex) -> {
            if (ex != null) {
                log.error("[PUBLISHER] Failed to send to {}: {}", topic, ex.getMessage(), ex);
            } else {
                log.debug("[PUBLISHER] Sent to {} (p={}, o={})",
                    topic, res.getRecordMetadata().partition(), res.getRecordMetadata().offset());
            }
        });
    }
}
