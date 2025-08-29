package br.com.ia.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class IaEnvironmentOverrides implements EnvironmentPostProcessor, Ordered {

    private static final String DEBUG = "DEBUG";
	private static final String TEXT_PLAIN = "text/plain";
    private static final String PS_NAME = "iaServerOverridesEarly";

    private static final String KAFKA_BROKERS      = "192.168.10.116:9092";
    private static final String TOPIC_REQUESTS     = "ia.requests";
    private static final String TOPIC_RESPONSES    = "ia.responses";
    private static final String INPUT_GROUP        = "ia-processor-v10";
    private static final String REPLIES_REQUIRED_GROUPS = "workspace-ia-replies";
    private static final int RESPONSES_PARTITION_COUNT = 3;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        System.out.println("[IaEnvironmentOverrides] >>> applying early overrides"); // NOSONAR
        Map<String, Object> p = new LinkedHashMap<>();

        // --- Kafka (Spring for Apache Kafka — usado por seus @KafkaListener e templates) ---
        p.put("spring.kafka.bootstrap-servers", KAFKA_BROKERS);

        // --- Definição da função e roteamento (sempre server+client no mesmo módulo) ---
        p.put("spring.cloud.function.definition", "processIaConsumer");
        p.put("spring.cloud.stream.function.definition", "processIaConsumer");
        p.put("spring.cloud.stream.function.routing.enabled", false);

        // --- Ligações função <-> nomes de bindings (claras/estáveis) ---
        p.put("spring.cloud.stream.function.bindings.processIaConsumer-in-0",  "processIaConsumer-in-0");
        p.put("spring.cloud.stream.function.bindings.processIaConsumer-out-0", "iaReplies-out-0");

        // --- INPUT (consumer de requests) ---
        p.put("spring.cloud.stream.bindings.processIaConsumer-in-0.destination",  TOPIC_REQUESTS);
        p.put("spring.cloud.stream.bindings.processIaConsumer-in-0.group",        INPUT_GROUP);
        p.put("spring.cloud.stream.bindings.processIaConsumer-in-0.content-type", TEXT_PLAIN);
        p.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.max-attempts",              3);
        p.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.back-off-initial-interval", 1000);
        p.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.back-off-max-interval",     10000);
        p.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.concurrency",               2);

        // --- OUTPUT (producer de respostas) ---
        p.put("spring.cloud.stream.bindings.iaReplies-out-0.destination",  TOPIC_RESPONSES);
        p.put("spring.cloud.stream.bindings.iaReplies-out-0.content-type", TEXT_PLAIN);
        // nomes legacy e novos (algumas versões olham um, outras o outro)
        p.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.partitionCount",  RESPONSES_PARTITION_COUNT);
        p.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.requiredGroups",  REPLIES_REQUIRED_GROUPS);
        p.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.partition-count", RESPONSES_PARTITION_COUNT);
        p.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.required-groups", REPLIES_REQUIRED_GROUPS);

        // --- (Opcional) Saída adicional via StreamBridge, se você usar em algum ponto ---
        p.put("spring.cloud.stream.bindings.streamBridge-in-0.consumer.auto-startup",  true);
        p.put("spring.cloud.stream.bindings.streamBridge-out-0.producer.auto-startup", true);
        p.put("spring.cloud.stream.bindings.iaRequests-out-0.destination",  TOPIC_REQUESTS);
        p.put("spring.cloud.stream.bindings.iaRequests-out-0.content-type", TEXT_PLAIN);

        // --- Binder Kafka – opções globais (hardcoded) ---
        p.put("spring.cloud.stream.kafka.binder.auto-create-topics",  true);
        p.put("spring.cloud.stream.kafka.binder.auto-add-partitions", false);
        p.put("spring.cloud.stream.kafka.binder.required-acks",       "1");
        p.put("spring.cloud.stream.kafka.binder.min-partition-count", 1);
        p.put("spring.cloud.stream.kafka.binder.replication-factor",  1);

        // --- Defaults Kafka Consumer (binder) ---
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.auto.offset.reset",       "latest");
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.auto.commit.interval.ms", 5000);
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.max.poll.records",        1);
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.max.poll.interval.ms",    300000);
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.session.timeout.ms",      45000);
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.heartbeat.interval.ms",   3000);
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.fetch.min.bytes",         1);
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.fetch.max.wait.ms",       500);
        p.put("spring.cloud.stream.kafka.binder.consumer-properties.max.partition.fetch.bytes", 1048576);

        // --- Defaults Kafka Producer (binder) ---
        p.put("spring.cloud.stream.kafka.binder.producer-properties.enable.idempotence", true);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.acks",              "all");
        p.put("spring.cloud.stream.kafka.binder.producer-properties.retries",           3);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.retry.backoff.ms",  1000);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.batch.size",        16384);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.linger.ms",         5);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.buffer.memory",     33554432L);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.max.in.flight.requests.per.connection", 5);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.request.timeout.ms",  25000);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.delivery.timeout.ms", 30000);
        p.put("spring.cloud.stream.kafka.binder.producer-properties.compression.type",    "snappy");

        // --- Suas configs internas (que outros beans consomem via @Value) ---
        p.put("ia.processing.request-timeout-ms", 300000);
        p.put("ia.pending.reply-timeout-ms",      30000);
        p.put("ia.pending.cleanup-interval-minutes", 5);
        p.put("ia.pending.max-pending-requests",  1000);
        p.put("ia.base64-wrapper.enabled",        true);
        p.put("ia.module.name",                   "workspace"); // apenas para logs/identidade
            
        p.put("spring.cloud.stream.kafka.binder.producer-properties.key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        p.put("spring.cloud.stream.kafka.binder.producer-properties.value.serializer","org.apache.kafka.common.serialization.StringSerializer");
        p.put("spring.kafka.producer.value-serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        
        p.put("spring.kafka.producer.acks", "all");
        p.put("spring.kafka.producer.retries", 3);
        
        p.put("logging.level.org.springframework.cloud.stream", DEBUG);
        p.put("logging.level.org.springframework.cloud.stream.binding", DEBUG);
        p.put("logging.level.org.springframework.cloud.function", DEBUG);
        p.put("logging.level.org.springframework.cloud.stream.binder.kafka", DEBUG);

        env.getPropertySources().addFirst(new MapPropertySource(PS_NAME, p));
        System.out.println("[IaEnvironmentOverrides] property source '" + PS_NAME + "' registered with " + p.size() + " keys"); // NOSONAR
    }

    @Override
    public int getOrder() {
        // Rodar MUITO cedo
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
