package br.com.ia.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpringCloudUtils {

    private static final String DEFAULT = " (default)";
	private static final String ERP_IA_PROCESSING_MAX_CONCURRENT_REQUESTS2 = "erp.ia.processing.max-concurrent-requests";
	private static final String ERP_IA_PROCESSING_MAX_CONCURRENT_REQUESTS = ERP_IA_PROCESSING_MAX_CONCURRENT_REQUESTS2;
	private static final String SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_OUT_0_PRODUCER_PARTITION_COUNT = "spring.cloud.stream.bindings.processIa-out-0.producer.partition-count";
	private static final String SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_IN_0_CONSUMER_CONCURRENCY = "spring.cloud.stream.bindings.processIa-in-0.consumer.concurrency";
	private static final String FALSE = "false";

	/**
     * Exibe graficamente toda a configuração do Spring Cloud Stream
     */
    public void logSpringCloudConfiguration(Environment environment) {
        log.info("╔═════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                       SPRING CLOUD STREAM CONFIGURATION                     ║");
        log.info("╚═════════════════════════════════════════════════════════════════════════════╝");
        
        logApplicationInfo(environment);
        logKafkaBrokerConfiguration(environment);
        logBindingsConfiguration(environment);
        logInputBindingDetails(environment);
        logOutputBindingDetails(environment);
        logKafkaSpecificConfiguration(environment);
        logResilienceConfiguration(environment);
        logIAProcessingConfiguration(environment);
        logHealthMonitoringConfiguration(environment);
        
        drawArchitectureDiagram(environment);
        
        log.info("╔══════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                    CONFIGURAÇÃO FINALIZADA COM SUCESSO                       ║");
        log.info("╚══════════════════════════════════════════════════════════════════════════════╝");
    }

    private void logApplicationInfo(Environment environment) {
        log.info("");
        log.info("🚀 APPLICATION INFO:");
        log.info("   ├─ Nome: {}", getProperty(environment, "spring.application.name", "N/A"));
        log.info("   ├─ Porta: {}", getProperty(environment, "server.port", "8080"));
        log.info("   └─ Encoding: {}", getProperty(environment, "server.servlet.encoding.charset", "UTF-8"));
    }

    private void logKafkaBrokerConfiguration(Environment environment) {
        log.info("");
        log.info("🌐 KAFKA BROKER CONFIGURATION:");
        log.info("   ├─ Brokers: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.brokers", "localhost:9092"));
        log.info("   ├─ Auto Create Topics: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.auto-create-topics", FALSE));
        log.info("   ├─ Auto Add Partitions: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.auto-add-partitions", FALSE));
        log.info("   ├─ Required Acks: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.required-acks", "1"));
        log.info("   ├─ Min Partition Count: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.min-partition-count", "1"));
        log.info("   └─ Replication Factor: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.replication-factor", "1"));
    }

    private void logBindingsConfiguration(Environment environment) {
        log.info("");
        log.info("🔗 FUNCTION BINDINGS:");
        log.info("   ├─ Routing Enabled: {}", getProperty(environment, "spring.cloud.stream.function.routing.enabled", FALSE));
        log.info("   └─ Function Binding: {} → {}", 
            getProperty(environment, "spring.cloud.stream.function.bindings.processIa-in-0", "N/A"),
            "input");
    }

    private void logInputBindingDetails(Environment environment) {
        log.info("");
        log.info("📥 INPUT BINDING (processIa-in-0):");
        log.info("   ├─ Destination Topic: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.destination", "N/A"));
        log.info("   ├─ Consumer Group: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.group", "N/A"));
        log.info("   ├─ Content Type: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.content-type", "application/json"));
        log.info("   ├─ Auto Startup: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.consumer.auto-startup", "true"));
        log.info("   ├─ Concurrency: {}", getProperty(environment, SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_IN_0_CONSUMER_CONCURRENCY, "1"));
        log.info("   ├─ Max Attempts: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.consumer.max-attempts", "3"));
        log.info("   ├─ Back-off Interval: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.consumer.back-off-initial-interval", "1000"));
        log.info("   ├─ DLQ Enabled: {}", getProperty(environment, "spring.cloud.stream.kafka.bindings.processIa-in-0.consumer.enable-dlq", FALSE));
        log.info("   └─ DLQ Name: {}", getProperty(environment, "spring.cloud.stream.kafka.bindings.processIa-in-0.consumer.dlq-name", "N/A"));
    }

    private void logOutputBindingDetails(Environment environment) {
        log.info("");
        log.info("📤 OUTPUT BINDING (processIa-out-0):");
        log.info("   ├─ Destination Topic: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-out-0.destination", "N/A"));
        log.info("   ├─ Content Type: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-out-0.content-type", "application/json"));
        log.info("   ├─ Partition Count: {}", getProperty(environment, SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_OUT_0_PRODUCER_PARTITION_COUNT, "1"));
        log.info("   └─ Required Groups: {}", getProperty(environment, "spring.cloud.stream.bindings.processIa-out-0.producer.required-groups", "N/A"));
    }

    private void logKafkaSpecificConfiguration(Environment environment) {
        log.info("");
        log.info("⚙️  KAFKA CONSUMER PROPERTIES:");
        log.info("   ├─ Auto Offset Reset: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.consumer-properties.auto.offset.reset", "latest"));
        log.info("   ├─ Enable Auto Commit: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.consumer-properties.enable.auto.commit", "true"));
        log.info("   ├─ Max Poll Records: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.consumer-properties.max.poll.records", "500"));
        log.info("   ├─ Max Poll Interval: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.consumer-properties.max.poll.interval.ms", "300000"));
        log.info("   ├─ Session Timeout: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.consumer-properties.session.timeout.ms", "30000"));
        log.info("   └─ Heartbeat Interval: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.consumer-properties.heartbeat.interval.ms", "3000"));

        log.info("");
        log.info("⚙️  KAFKA PRODUCER PROPERTIES:");
        log.info("   ├─ Acks: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.producer-properties.acks", "1"));
        log.info("   ├─ Retries: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.producer-properties.retries", "2147483647"));
        log.info("   ├─ Idempotence: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.producer-properties.enable.idempotence", FALSE));
        log.info("   ├─ Request Timeout: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.producer-properties.request.timeout.ms", "30000"));
        log.info("   ├─ Delivery Timeout: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.producer-properties.delivery.timeout.ms", "120000"));
        log.info("   └─ Compression: {}", getProperty(environment, "spring.cloud.stream.kafka.binder.producer-properties.compression.type", "none"));
    }

    private void logResilienceConfiguration(Environment environment) {
        log.info("");
        log.info("🛡️  RESILIENCE4J CIRCUIT BREAKER:");
        log.info("   ├─ Instance: iaClient");
        log.info("   ├─ Health Indicator: {}", getProperty(environment, "resilience4j.circuitbreaker.instances.iaClient.register-health-indicator", FALSE));
        log.info("   ├─ Sliding Window Size: {}", getProperty(environment, "resilience4j.circuitbreaker.instances.iaClient.sliding-window-size", "100"));
        log.info("   ├─ Min Calls: {}", getProperty(environment, "resilience4j.circuitbreaker.instances.iaClient.minimum-number-of-calls", "10"));
        log.info("   ├─ Failure Rate Threshold: {}%", getProperty(environment, "resilience4j.circuitbreaker.instances.iaClient.failure-rate-threshold", "50"));
        log.info("   └─ Wait Duration: {}", getProperty(environment, "resilience4j.circuitbreaker.instances.iaClient.wait-duration-in-open-state", "60s"));
    }

    private void logIAProcessingConfiguration(Environment environment) {
        log.info("");
        log.info("🤖 IA PROCESSING CONFIGURATION:");
        log.info("   ├─ Max Payload Size: {}", getProperty(environment, "erp.ia.max-payload-size", "500000"));
        log.info("   ├─ Reply Timeout: {}", getProperty(environment, "erp.ia.reply-timeout-ms", "60000"));
        log.info("   ├─ Max Pending Requests: {}", getProperty(environment, "erp.ia.max-pending-requests", "1000"));
        log.info("   ├─ Max Concurrent Requests: {}", getProperty(environment, ERP_IA_PROCESSING_MAX_CONCURRENT_REQUESTS, "10"));
        log.info("   ├─ Queue Capacity: {}", getProperty(environment, "erp.ia.processing.queue-capacity", "1000"));
        log.info("   ├─ Timeout Seconds: {}", getProperty(environment, "erp.ia.processing.timeout-seconds", "300"));
        log.info("   ├─ Base64 Wrapper Enabled: {}", getProperty(environment, "ia.base64.wrapper.enabled", FALSE));
        log.info("   └─ Connection Test Interval: {}s", getProperty(environment, "ia.connection.test.interval.seconds", "30"));
    }

    private void logHealthMonitoringConfiguration(Environment environment) {
        log.info("");
        log.info("💊 HEALTH & MONITORING:");
        log.info("   ├─ Kafka Health Enabled: {}", getProperty(environment, "management.health.kafka.enabled", "true"));
        log.info("   ├─ Health Details: {}", getProperty(environment, "management.endpoint.health.show-details", "never"));
        log.info("   └─ Exposed Endpoints: {}", getProperty(environment, "management.endpoints.web.exposure.include", "health,info"));
    }

    private void drawArchitectureDiagram(Environment environment) {
        String inputTopic = getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.destination", "input-topic");
        String outputTopic = getProperty(environment, "spring.cloud.stream.bindings.processIa-out-0.destination", "output-topic");
        String consumerGroup = getProperty(environment, "spring.cloud.stream.bindings.processIa-in-0.group", "default-group");
        String brokers = getProperty(environment, "spring.cloud.stream.kafka.binder.brokers", "localhost:9092");
        String dlqTopic = getProperty(environment, "spring.cloud.stream.kafka.bindings.processIa-in-0.consumer.dlq-name", "N/A");
        String serverPort = getProperty(environment, "server.port", "8080");
        String partitionCount = getProperty(environment, SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_OUT_0_PRODUCER_PARTITION_COUNT, "1");
        String consumerConcurrency = getProperty(environment, SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_IN_0_CONSUMER_CONCURRENCY, "1");
        String processingConcurrency = getProperty(environment, ERP_IA_PROCESSING_MAX_CONCURRENT_REQUESTS, "10");

        log.info("");
        log.info("📊 ARCHITECTURE DIAGRAM:");
        log.info("╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                                      KAFKA CLUSTER                                           ║");
        log.info("║                                      {}      ║", formatField(brokers, 50));
        log.info("╠══════════════════════════════════════════════════════════════════════════════════════════════╣");
        log.info("║                                                                                              ║");
        log.info("║  ┌───────────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐       ║");
        log.info("║  │    INPUT TOPIC            │───▶│    APPLICATION       │───▶│   OUTPUT TOPIC       │       ║");
        
        log.info("║  │ {} │    │ {} │    │ {} │       ║", 
            formatField(inputTopic, 25),
            formatField("ia-processor", 20),
            formatField(outputTopic, 20));
        
        
        
        log.info("║  │ Group: {}│    │ (port: {}) │    │ Partitions: {} │       ║", 
            formatField(consumerGroup, 19),
            formatField(serverPort.replace("(", "").replace(")", ""), 12),
            formatField(partitionCount, 8));
        
        log.info("║  │ Concurrency: {} │    │ Concurrency: {} │    │                      │       ║", 
            formatField(consumerConcurrency, 12),
            formatField(processingConcurrency, 7));
        
        log.info("║  └───────────────────────────┘    └──────────────────────┘    └──────────────────────┘       ║");
        
        log.info("║           │                                                                                  ║");
        log.info("║           ▼ (on failure)                                                                     ║");
        log.info("║  ┌────────────────────┐                                                                      ║");
        log.info("║  │     DLQ TOPIC      │                                                                      ║");
        log.info("║  │{}│                                                                      ║", formatField(dlqTopic, 20));
        log.info("║  └────────────────────┘                                                                      ║");
        log.info("║                                                                                              ║");
        log.info("╚══════════════════════════════════════════════════════════─────═══════════════════════════════╝");
        
        log.info("");
        log.info("🔄 FLOW SUMMARY:");
        log.info("   1️⃣  Messages arrive at: {} (group: {})", cleanValue(inputTopic), cleanValue(consumerGroup));
        log.info("   2️⃣  Processed by: {} concurrent consumers", cleanValue(consumerConcurrency));
        log.info("   3️⃣  IA Processing: max {} concurrent requests", cleanValue(processingConcurrency));
        log.info("   4️⃣  Responses sent to: {} ({} partitions)", cleanValue(outputTopic), cleanValue(partitionCount));
        log.info("   5️⃣  Failed messages → DLQ: {}", cleanValue(dlqTopic));
    }
    
	/**
	 * Formata um campo para exibição no diagrama com tamanho fixo
	 * 
	 * @param value     valor original
	 * @param maxLength tamanho máximo do campo
	 * @return string formatada com tamanho fixo
	 */
	private String formatField(String value, int maxLength) {
		if (value == null || value.trim().isEmpty()) {
			value = "N/A";
		}

		// Remove sufixo " (default)" se existir para exibição mais limpa
		String cleanValue = value.replace(DEFAULT, "");

		if (cleanValue.length() > maxLength) {
			// Se exceder o tamanho, corta e adiciona "..."
			return cleanValue.substring(0, maxLength - 3) + "...";
		} else {
			// Se for menor, preenche com espaços à direita
			int spacesToAdd = maxLength - cleanValue.length();
			return cleanValue + " ".repeat(spacesToAdd); 
		}
	}

	/**
	 * Remove sufixo " (default)" para exibição no resumo
	 */
	private String cleanValue(String value) {
		return value != null ? value.replace(DEFAULT, "") : "N/A";
	}

	private String getProperty(Environment environment, String key, String defaultValue) {
		String value = environment.getProperty(key);
		return value != null ? value : defaultValue + DEFAULT;
	}
}
