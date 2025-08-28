package br.com.ia.processor.core;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import br.com.ia.config.IaServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IaResponseSender {

	private final IaServerProperties props;
	private final KafkaTemplate<String, String> kafka;

	public void send(String payload) {
		String topic = props.getKafkaTopicResponses();
		kafka.send(topic, payload).whenComplete((r, ex) -> {
			if (ex != null)
				log.error("[IA-PRODUCER] failed to send: {}", ex.getMessage());
			else
				log.debug("[IA-PRODUCER] sent to {}", topic);
		});
	}
}
