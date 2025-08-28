// src/main/java/br/com/ia/services/IaProcessor.java
package br.com.ia.services;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import br.com.ia.messaging.MessageType;
import br.com.ia.model.IaResponse;
import br.com.ia.processor.IaDuplicateDetector;
import br.com.ia.processor.IaMessageClassifier;
import br.com.ia.processor.IaMessageNormalizer;
import br.com.ia.processor.IaReplyPublisher;
import br.com.ia.processor.IaRequestHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * IA PROCESSOR (único ponto de entrada via Cloud Stream):
 *   - @Bean Consumer<Object> processIaConsumer()
 * Quebra de responsabilidades:
 *   - Normalização   -> IaMessageNormalizer
 *   - Classificação  -> IaMessageClassifier (MessageType)
 *   - Deduplicação   -> IaDuplicateDetector
 *   - Processamento  -> IaRequestHandler
 *   - Publicação     -> IaReplyPublisher
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ia.server.enabled", havingValue = "true", matchIfMissing = false)
public class IaProcessor {

    private final IaMessageNormalizer normalizer;
    private final IaMessageClassifier classifier;
    private final IaDuplicateDetector dedup;
    private final IaRequestHandler iaHandler;
    private final IaReplyPublisher publisher;
    
    @Bean
    public Consumer<Object> processIaConsumer() {
        log.info("[IA-PROCESSOR] Consumer 'processIaConsumer' registered!");
        return input -> {
            final long t0 = System.currentTimeMillis();
            try {
                log.info("[IA-PROCESSOR] PROCESSING INPUT - Type: {}", input != null ? input.getClass().getSimpleName() : "null");

                // Dedup early
                String h = dedup.hash(input);
                if (dedup.isDuplicate(h)) {
                    log.warn("[IA-PROCESSOR] DUPLICATE MESSAGE DETECTED - IGNORING");
                    return;
                }

                // Normalize
                Map<String, Object> normalized = normalizer.normalize(input);
                if (normalized == null) {
                    log.warn("[IA-PROCESSOR] Normalization failed");
                    publisher.sendError("normalization_failed", "Could not normalize input");
                    return;
                }

                // Classify
                MessageType type = classifier.identify(normalized);
                log.info("[IA-PROCESSOR] MESSAGE TYPE: {}", type);

                // Route
                switch (type) {
                    case STARTUP_TEST -> {
                        publisher.sendProcessingResponse("ignored", "startup_test", "Startup test processed successfully", null);
                    }
                    case CONNECTION_TEST -> {
                        publisher.sendProcessingResponse("ignored", "connection_test", "Connection test processed successfully", null);
                    }
                    case IA_REQUEST -> {
                        // payload pode estar em "payload" se for envelopado
                        Map<String, Object> payload = normalized;
                        if (normalized.containsKey("payload") && normalized.get("payload") instanceof Map<?, ?> p) {
                            @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) p;
                            payload = mm;
                        }

                        IaResponse res = iaHandler.handle(payload);
                        if (res != null) {
                            publisher.sendWrapped(res, "IA_RESPONSE");
                            dedup.markProcessed(h);
                        } else {
                            log.debug("[IA-PROCESSOR] No response (handler returned null)");
                        }
                    }
                    case IA_RESPONSE_LOOP, PROCESSING_RESPONSE, DUPLICATE_MESSAGE -> {
                        log.warn("[IA-PROCESSOR] Ignored {} to prevent loop/no-op", type);
                    }
                    case UNKNOWN -> {
                        publisher.sendProcessingResponse("ignored", "unknown_format", "Unrecognized message format", normalized);
                    }
                }

            } catch (Exception e) {
                log.error("[IA-PROCESSOR] CRITICAL EXCEPTION: {}", e.getMessage(), e);
                publisher.sendError("processing_exception", e.getMessage());
            } finally {
                log.info("[IA-PROCESSOR] Processing completed in {}ms", System.currentTimeMillis() - t0);
            }
        };
    }
}
