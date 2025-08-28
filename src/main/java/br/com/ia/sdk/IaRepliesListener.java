package br.com.ia.sdk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import br.com.ia.config.IaServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ia.client.enabled", havingValue = "true", matchIfMissing = true)
public class IaRepliesListener {

    private final BaseIAResponseHandler handler;
    private final IaServerProperties props;

    // Usando SpEL para garantir tópicos e groupId válidos, vindos do bean de propriedades.
    @KafkaListener(
        topics  = "#{@iaServerProperties.kafkaTopicResponses}",
        groupId = "#{@iaServerProperties.computedWorkspaceRepliesGroup}"
    )
    public void onMessage(String payload) {
        try {
            String group = props.getComputedWorkspaceRepliesGroup();
            log.info("[SDK-REPLIES] group={} received response len={}", group, (payload == null ? 0 : payload.length()));
            handler.processResponse(payload);
        } catch (Exception e) {
            log.error("[SDK-REPLIES] error: {}", e.getMessage(), e);
        }
    }
}
