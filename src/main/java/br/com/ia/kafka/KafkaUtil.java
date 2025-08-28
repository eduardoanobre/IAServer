package br.com.ia.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import br.com.ia.config.IaServerProperties;
import br.com.ia.sdk.Base64MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilitário para testes de conectividade Kafka.
 * 
 * Exemplos de uso:
 * 
 * <pre>
 * {@code
 * @Autowired
 * KafkaUtil kafkaUtil;
 * }
 * 
 * // 1) Ping puro (sem produzir/consumir)
 * boolean up = kafkaUtil.pingBroker(Duration.ofSeconds(2));
 * 
 * // 2) Enviar teste simples (sem esperar ack)
 * String cid1 = kafkaUtil.sendStartupTest();
 * String cid2 = kafkaUtil.sendConnectionTest("workspace");
 * 
 * // 3) Enviar e aguardar ACK
 * boolean ok1 = kafkaUtil.sendStartupTestAndAwaitAck(Duration.ofSeconds(5));
 * boolean ok2 = kafkaUtil.sendConnectionTestAndAwaitAck("workspace", Duration.ofSeconds(5));
 * </pre>
 */
@ConditionalOnProperty(name = "ia.server.enabled", havingValue = "true")
@ConditionalOnBean(StreamBridge.class)
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaUtil {

	private static final String TIMESTAMP = "timestamp";
	private static final String CORRELATION_ID = "correlationId";
	
	private final StreamBridge bridge;
	private final Environment env;
	private final Base64MessageWrapper wrapper;
	private final IaServerProperties props;

	// ---------- BROKER PING (sem produzir/consumir mensagens) ----------

	/**
	 * Verifica conectividade com o cluster usando o AdminClient.
	 * 
	 * @param timeout Timeout para obter resposta do cluster
	 * @return true se conseguiu obter o clusterId dentro do timeout; false caso
	 *         contrário
	 */
	public boolean pingBroker(Duration timeout) {
		String brokers = getBrokers();
		Properties cfg = createAdminConfig(brokers);

		try (Admin admin = Admin.create(cfg)) {
			String clusterId = admin.describeCluster().clusterId().toCompletionStage().toCompletableFuture()
					.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

			log.info("[KAFKA-UTIL] ✅ Broker OK (clusterId={})", clusterId);
			return true;

		} catch (InterruptedException e) {
			log.error("[KAFKA-UTIL] ❌ Broker unreachable at {}: {}", brokers, e.getMessage());
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			log.error("[KAFKA-UTIL] ❌ Broker unreachable at {}: {}", brokers, e.getMessage());
			return false;
		}
	}

	// ---------- STARTUP TEST ----------

	/**
	 * Envia STARTUP_TEST para o tópico de requests.
	 * 
	 * @return correlationId gerado para identificação do teste
	 */
	public String sendStartupTest() {
		String correlationId = UUID.randomUUID().toString();
		long now = System.currentTimeMillis();

		Map<String, Object> msg = createStartupTestMessage(correlationId, now);
		String envelope = wrapper.wrapToBase64(msg, "STARTUP_TEST");

		boolean ok = bridge.send(props.getKafkaTopicRequests(), envelope);
		log.info("[KAFKA-UTIL] STARTUP_TEST {} enviado para {}", ok ? "✅" : "❌", props.getKafkaTopicRequests());

		return correlationId;
	}

	/**
	 * Envia STARTUP_TEST e aguarda um PROCESSING_RESPONSE de "startup_test". Se
	 * disponível, tenta casar por correlationId (no originalData do ack).
	 * 
	 * @param timeout Tempo limite para aguardar a resposta
	 * @return true se recebeu o ACK esperado dentro do timeout
	 */
	public boolean sendStartupTestAndAwaitAck(Duration timeout) {
		long sentAt = System.currentTimeMillis();
		String cid = sendStartupTest();
		return awaitAck(cid, "startup_test", timeout, sentAt);
	}

	// ---------- CONNECTION TEST ----------

	/**
	 * Envia CONNECTION_TEST para o tópico de requests.
	 * 
	 * @param chatSuffix sufixo opcional para o chatId de teste
	 * @return correlationId gerado para identificação do teste
	 */
	public String sendConnectionTest(String chatSuffix) {
		String correlationId = UUID.randomUUID().toString();
		long now = System.currentTimeMillis();

		Map<String, Object> msg = createConnectionTestMessage(correlationId, now, chatSuffix);
		String envelope = wrapper.wrapToBase64(msg, "CONNECTION_TEST");

		boolean ok = bridge.send(props.getKafkaTopicRequests(), envelope);
		log.info("[KAFKA-UTIL] CONNECTION_TEST {} enviado para {}", ok ? "✅" : "❌", props.getKafkaTopicRequests());

		return correlationId;
	}

	/**
	 * Envia CONNECTION_TEST e aguarda um PROCESSING_RESPONSE de "connection_test".
	 * Se disponível, tenta casar por correlationId (no originalData do ack).
	 * 
	 * @param chatSuffix sufixo opcional para o chatId de teste
	 * @param timeout    Tempo limite para aguardar a resposta
	 * @return true se recebeu o ACK esperado dentro do timeout
	 */
	public boolean sendConnectionTestAndAwaitAck(String chatSuffix, Duration timeout) {
		long sentAt = System.currentTimeMillis();
		String cid = sendConnectionTest(chatSuffix);
		return awaitAck(cid, "connection_test", timeout, sentAt);
	}

	// ---------- Métodos privados auxiliares ----------

	private Properties createAdminConfig(String brokers) {
		Properties cfg = new Properties();
		cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
		return cfg;
	}

	private Map<String, Object> createStartupTestMessage(String correlationId, long timestamp) {
		String moduleName = Optional.ofNullable(env.getProperty("spring.application.name")).orElse("unknown");

		return Map.of("test", "message", TIMESTAMP, timestamp, "source", KafkaDebug.WORKSPACE_STARTUP, "module",
				moduleName, "event", "KAFKA_CONNECTION_TEST", CORRELATION_ID, correlationId);
	}

	private Map<String, Object> createConnectionTestMessage(String correlationId, long timestamp, String chatSuffix) {
		String appName = Optional.ofNullable(env.getProperty("spring.application.name")).orElse("unknown");
		String suffix = (chatSuffix == null || chatSuffix.isBlank()) ? appName : chatSuffix;

		return Map.of("chatId", "test-" + suffix + "-" + timestamp, "source", "connection-test", "test", true,
				TIMESTAMP, timestamp, "module", appName, "event", "MANUAL_CONNECTION_TEST", CORRELATION_ID,
				correlationId);
	}

	/**
	 * Aguarda um PROCESSING_RESPONSE no tópico de responses que satisfaça: - reason
	 * == expectedReason - (preferencial) correlationId dentro de
	 * payload.originalData == correlationId - fallback: reason confere e timestamp
	 * do ack >= sentAt
	 * 
	 * @param correlationId  ID da correlação esperado
	 * @param expectedReason Motivo esperado na resposta
	 * @param timeout        Tempo limite para aguardar
	 * @param sentAt         Timestamp de quando a mensagem foi enviada
	 * @return true se recebeu o ACK esperado
	 */
	private boolean awaitAck(String correlationId, String expectedReason, Duration timeout, long sentAt) {
		String brokers = getBrokers();
		String topic = props.getKafkaTopicResponses();

		Properties consumerProps = createConsumerConfig(brokers);

		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
			consumer.subscribe(Collections.singletonList(topic));
			long endTime = System.currentTimeMillis() + timeout.toMillis();

			log.info("[KAFKA-UTIL] Esperando ACK '{}' em {} por até {} ms…", expectedReason, topic, timeout.toMillis());

			return pollForAck(consumer, correlationId, expectedReason, sentAt, endTime);

		} catch (Exception e) {
			log.error("[KAFKA-UTIL] Erro aguardando ACK: {}", e.getMessage(), e);
			return false;
		}
	}

	private Properties createConsumerConfig(String brokers) {
		Properties properties = new Properties();
		properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokers);
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-util-test-" + UUID.randomUUID());
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
		properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
		return properties;
	}

	private boolean pollForAck(KafkaConsumer<String, String> consumer, String correlationId, String expectedReason,
			long sentAt, long endTime) {

		while (System.currentTimeMillis() < endTime) {
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));

			if (records.isEmpty()) {
				continue;
			}

			for (ConsumerRecord<String, String> rec : records) {
				if (processRecord(rec, correlationId, expectedReason, sentAt)) {
					return true;
				}
			}
		}

		log.warn("[KAFKA-UTIL] ⏳ Timeout aguardando ACK '{}'", expectedReason);
		return false;
	}

	private boolean processRecord(ConsumerRecord<String, String> rec, String correlationId, String expectedReason,
			long sentAt) {

		try {
			Map<String, Object> unwrapped = wrapper.unwrapFromBase64(rec.value());
			if (unwrapped == null) {
				return false;
			}

			return validateAndProcessResponse(unwrapped, correlationId, expectedReason, sentAt);

		} catch (Exception parseEx) {
			log.debug("[KAFKA-UTIL] Ignorando registro inválido: {}", parseEx.getMessage());
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private boolean validateAndProcessResponse(Map<String, Object> unwrapped, String correlationId,
			String expectedReason, long sentAt) {

		// Verifica se é uma PROCESSING_RESPONSE
		String type = String.valueOf(unwrapped.get("type"));
		if (!"PROCESSING_RESPONSE".equals(type)) {
			return false;
		}

		// Extrai o payload
		Object payloadObj = unwrapped.get("payload");
		if (!(payloadObj instanceof Map<?, ?> payloadMap)) {
			return false;
		}

		Map<String, Object> payload = (Map<String, Object>) payloadMap;
		String reason = String.valueOf(payload.get("reason"));

		// Verifica se o reason é o esperado
		if (!expectedReason.equals(reason)) {
			return false;
		}

		return checkCorrelationOrTimestamp(payload, correlationId, reason, sentAt);
	}

	private boolean checkCorrelationOrTimestamp(Map<String, Object> payload, String correlationId, String reason,
			long sentAt) {

		// Preferência: casar por correlationId dentro de originalData
		String ackCorrelationId = extractCorrelationId(payload);
		if (ackCorrelationId != null) {
			if (correlationId.equals(ackCorrelationId)) {
				log.info("[KAFKA-UTIL] ✅ ACK recebido (reason={}, cid={})", reason, ackCorrelationId);
				return true;
			} else {
				// outro teste — ignora
				return false;
			}
		}

		// Fallback: verifica timestamp
		long ackTimestamp = extractTimestamp(payload);
		if (ackTimestamp == 0L || ackTimestamp >= sentAt) {
			log.info("[KAFKA-UTIL] ✅ ACK recebido (reason={}, sem cid; aceito por janela temporal)", reason);
			return true;
		}

		return false;
	}

	private String extractCorrelationId(Map<String, Object> payload) {
		Object originalData = payload.get("originalData");
		if (originalData instanceof Map<?, ?> originalDataMap) {
			Object correlationIdObj = originalDataMap.get(CORRELATION_ID);
			if (correlationIdObj != null) {
				return String.valueOf(correlationIdObj);
			}
		}
		return null;
	}

	private long extractTimestamp(Map<String, Object> payload) {
		Object timestampObj = payload.get(TIMESTAMP);
		if (timestampObj != null) {
			try {
				return Long.parseLong(String.valueOf(timestampObj));
			} catch (NumberFormatException e) {
				log.debug("[KAFKA-UTIL] Erro ao parsear timestamp: {}", e.getMessage());
			}
		}
		return 0L;
	}

	private String getBrokers() {
		return Optional.ofNullable(env.getProperty("spring.kafka.bootstrap-servers")).orElse("localhost:9092");
	}
}