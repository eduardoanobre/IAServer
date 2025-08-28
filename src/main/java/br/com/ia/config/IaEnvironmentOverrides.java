package br.com.ia.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class IaEnvironmentOverrides {

    private static final String TEXT_PLAIN = "text/plain";

    private final IaServerProperties p;
    private final ConfigurableEnvironment env;

    @PostConstruct
    void publishOverrides() {
        Map<String, Object> o = new HashMap<>();

        if (p.isServerEnabled()) {
            // INPUT (consumer de requests)
            o.put("spring.cloud.stream.bindings.processIaConsumer-in-0.destination", p.getKafkaTopicRequests());
            o.put("spring.cloud.stream.bindings.processIaConsumer-in-0.group",       p.getBindingsInputGroup());
            o.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.max-attempts",              p.getBindingsInputMaxAttempts());
            o.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.back-off-initial-interval", p.getBindingsInputBackoffInitialMs());
            o.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.back-off-max-interval",     p.getBindingsInputBackoffMaxMs());
            o.put("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.concurrency",               p.getBindingsInputConcurrency());
            o.put("spring.cloud.stream.bindings.processIaConsumer-in-0.content-type", TEXT_PLAIN);

            // OUTPUT (producer de replies) — usado pelo servidor para responder
            if (StringUtils.hasText(p.getBindingsOutputDestination())) {
                o.put("spring.cloud.stream.bindings.iaReplies-out-0.destination",   p.getBindingsOutputDestination());
                o.put("spring.cloud.stream.bindings.iaReplies-out-0.content-type",  TEXT_PLAIN);

                // nomes legacy e novos
                o.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.partitionCount",  p.getBindingsOutputPartitionCount());
                o.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.requiredGroups",  p.getBindingsOutputRequiredGroups());
                o.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.partition-count", p.getBindingsOutputPartitionCount());
                o.put("spring.cloud.stream.bindings.iaReplies-out-0.producer.required-groups", p.getBindingsOutputRequiredGroups());
            }

            // OUTPUT extra opcional (requests via StreamBridge) – só no servidor se você realmente usar
            o.put("spring.cloud.stream.bindings.iaRequests-out-0.destination",  p.getKafkaTopicRequests());
            o.put("spring.cloud.stream.bindings.iaRequests-out-0.content-type", TEXT_PLAIN);

            // Binder Kafka — opções globais
            o.put("spring.cloud.stream.kafka.binder.auto-create-topics",  p.isBinderAutoCreateTopics());
            o.put("spring.cloud.stream.kafka.binder.auto-add-partitions", p.isBinderAutoAddPartitions());
            o.put("spring.cloud.stream.kafka.binder.required-acks",       p.getBinderRequiredAcks());
            o.put("spring.cloud.stream.kafka.binder.min-partition-count", p.getBinderMinPartitionCount());
            o.put("spring.cloud.stream.kafka.binder.replication-factor",  p.getBinderReplicationFactor());

            // Consumer tuning
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.auto.offset.reset",       p.getConsumerAutoOffsetReset());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.auto.commit.interval.ms", p.getConsumerAutoCommitIntervalMs());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.max.poll.records",        p.getConsumerMaxPollRecords());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.max.poll.interval.ms",    p.getConsumerMaxPollIntervalMs());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.session.timeout.ms",      p.getConsumerSessionTimeoutMs());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.heartbeat.interval.ms",   p.getConsumerHeartbeatIntervalMs());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.fetch.min.bytes",         p.getConsumerFetchMinBytes());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.fetch.max.wait.ms",       p.getConsumerFetchMaxWaitMs());
            o.put("spring.cloud.stream.kafka.binder.consumer-properties.max.partition.fetch.bytes", p.getConsumerMaxPartitionFetchBytes());

            // Producer tuning
            o.put("spring.cloud.stream.kafka.binder.producer-properties.acks",                    p.getProducerAcks());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.retries",                 p.getProducerRetries());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.retry.backoff.ms",        p.getProducerRetryBackoffMs());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.delivery.timeout.ms",     p.getProducerDeliveryTimeoutMs());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.request.timeout.ms",      p.getProducerRequestTimeoutMs());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.batch.size",              p.getProducerBatchSize());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.linger.ms",               p.getProducerLingerMs());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.buffer.memory",           p.getProducerBufferMemory());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.compression.type",        p.getProducerCompressionType());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.enable.idempotence",      p.isProducerEnableIdempotence());
            o.put("spring.cloud.stream.kafka.binder.producer-properties.max.in.flight.requests.per.connection", p.getProducerMaxInFlightReqPerConn());
        }

        env.getPropertySources().addFirst(new MapPropertySource("iaServerOverrides", o));
    }
}
