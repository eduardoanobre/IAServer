package br.com.ia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class KafkaErrorConfig {

	@Bean
	public DefaultErrorHandler kafkaErrorHandler() {
		DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
			log.warn("Mensagem ignorada - Topic: {}, Offset: {}, Partition: {}, Error: {}", record.topic(),
					record.offset(), record.partition(), exception.getMessage());
		}, new FixedBackOff(0L, 0L) // No retry
		);

		// Ignore all types of errors
		errorHandler.addNotRetryableExceptions(Exception.class);
		errorHandler.setAckAfterHandle(true);

		log.info("Error Handler configured - will ignore all problematic messages");

		return errorHandler;
	}
}