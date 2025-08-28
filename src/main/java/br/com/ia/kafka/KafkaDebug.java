package br.com.ia.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import br.com.ia.config.IaServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDebug {

	public static final String WORKSPACE_STARTUP = "workspace-startup";
	private final KafkaTemplate<String, byte[]> kafkaTemplate;
	private final IaServerProperties props;

	@EventListener(ApplicationReadyEvent.class)
	public void startupPing() {
		if (!props.isClientEnabled())
			return;

		String topic = props.getKafkaTopicRequests();
		String message = "{\"type\":\"STARTUP_TEST\",\"payload\":\"hello\"}";
		byte[] value = props.isBase64WrapperEnabled()
				? Base64.getEncoder().encode(message.getBytes(StandardCharsets.UTF_8))
				: message.getBytes(StandardCharsets.UTF_8);

		log.info("📦 Sending {} test message to topic '{}'",
				props.isBase64WrapperEnabled() ? "Base64 wrapped" : "plain", topic);

		kafkaTemplate.send(new ProducerRecord<>(topic, value)).whenComplete((md, ex) -> {
			if (ex == null) {
				log.info("✅ Kafka working! Base64 message sent successfully to IAServer");
			} else {
				log.error("❌ Kafka send failed", ex);
			}
		});
	}
}
