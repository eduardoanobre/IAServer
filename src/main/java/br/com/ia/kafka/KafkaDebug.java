package br.com.ia.kafka;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.com.ia.messaging.MessageType;
import br.com.ia.publisher.IaPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDebug {

	public static final String WORKSPACE_STARTUP = "workspace-startup";

	private final IaPublisher publisher;

	@EventListener(ApplicationReadyEvent.class)
	public void startupPing() {
		String message = "{\"type\":\"STARTUP_TEST\",\"payload\":\"hello\"}";
		log.info("📦 Enviando mensagem de teste!");

		publisher.publish(MessageType.STARTUP_TEST, message, MessageType.STARTUP_TEST.name()).whenComplete((md, ex) -> {
			if (ex == null) {
				log.info("✅ Kafka funcionando. Mensagem de teste enviada com sucesso!");
			} else {
				log.error("❌ Kafka send failed", ex);
			}
		});
	}
}
