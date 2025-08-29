package br.com.ia.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDebug {

	public static final String WORKSPACE_STARTUP = "workspace-startup";
	private final KafkaTemplate<byte[], byte[]> kafkaTemplate;

	// Lidos do env (preenchidos pelo EnvironmentPostProcessor)
	@Value("${ia.client.enabled:true}")
	private boolean clientEnabled;

	@Value("${spring.cloud.stream.bindings.processIaConsumer-in-0.destination:ia.requests}")
	private String requestsTopic;

	@Value("${ia.base64-wrapper.enabled:true}")
	private boolean base64WrapperEnabled;

	@EventListener(ApplicationReadyEvent.class)
	public void startupPing() {
		String message = "{\"type\":\"STARTUP_TEST\",\"payload\":\"hello\"}";
		byte[] value = base64WrapperEnabled ? Base64.getEncoder().encode(message.getBytes(StandardCharsets.UTF_8))
				: message.getBytes(StandardCharsets.UTF_8);

		log.info("📦 Sending {} test message to topic '{}'", base64WrapperEnabled ? "Base64 wrapped" : "plain",
				requestsTopic);

		kafkaTemplate.send(requestsTopic, "STARTUP_TEST".getBytes(StandardCharsets.UTF_8), value)
				.whenComplete((md, ex) -> {
					if (ex == null) {
						log.info("✅ Kafka working! {} message sent successfully to IAServer",
								base64WrapperEnabled ? "Base64" : "Plain");
					} else {
						log.error("❌ Kafka send failed", ex);
					}
				});
	}
}
