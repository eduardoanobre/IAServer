package br.com.ia.kafka;

import java.util.Map;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaDebug {

	private final StreamBridge bridge;
	private final Environment env;

	@PostConstruct
	public void testarKafka() {
		log.info("🔍 Iniciando teste de conexão Kafka...");

		String brokers = env.getProperty("spring.cloud.stream.kafka.binder.brokers");
		String serverPort = env.getProperty("server.port");
		String appName = env.getProperty("spring.application.name");

		log.info("📡 Brokers: {}", brokers);
		log.info("🚪 Server port: {}", serverPort);
		log.info("📱 App name: {}", appName);

		String kafkaBootstrap = env.getProperty("KAFKA_BOOTSTRAP_SERVERS");
		log.info("🌍 KAFKA_BOOTSTRAP_SERVERS env: {}", kafkaBootstrap);

		try {
			Map<String, String> mensagemTeste = Map.of("test", "message", "timestamp",
					String.valueOf(System.currentTimeMillis()), "source", "workspace-startup");

			boolean resultado = bridge.send("processIa-in-0", mensagemTeste);

			if (resultado) {
				log.info("✅ Kafka funcionando! Mensagem enviada com sucesso");
			} else {
				log.error("❌ Falha ao enviar mensagem - resultado: false");
			}

		} catch (Exception e) {
			log.error("❌ Erro ao testar Kafka: {} - {}", e.getClass().getSimpleName(), e.getMessage());
			log.error("Stack trace completo:", e);
		}

		log.info("🏁 Teste de Kafka concluído");
	}
}
