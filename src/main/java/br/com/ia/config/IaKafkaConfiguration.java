package br.com.ia.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Centralized Kafka Configuration for IA Server SDK
 * 
 * This configuration is part of the IAServer module and provides all Kafka
 * beans and configurations needed by any application that uses the IA SDK.
 * 
 * Applications only need to configure:
 * - spring.kafka.bootstrap-servers (IP:PORT)
 * 
 * All other Kafka settings are centralized here.
 */
@Configuration
@Slf4j
public class IaKafkaConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // =============================================================================
    // PRODUCER CONFIGURATION
    // =============================================================================

    @Bean
    public ProducerFactory<String, String> iaProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Connection
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // Reliability settings - optimized for IA processing
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 25000);
        
        // Performance settings - tuned for IA responses
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB batches
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5); // Wait 5ms for batching
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // Compress large responses
        
        // Idempotence for exactly-once semantics
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        log.info("[IA-KAFKA-CONFIG] Producer factory configured - Servers: {}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> iaKafkaTemplate(ProducerFactory<String, String> iaProducerFactory) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(iaProducerFactory);
        
        // Default topic for IA responses
        template.setDefaultTopic("ia.responses");
        
        // Error handling
        template.setObservationEnabled(true);
        
        log.info("[IA-KAFKA-CONFIG] KafkaTemplate created with default topic: ia.responses");
        return template;
    }

    // =============================================================================
    // CONSUMER CONFIGURATION
    // =============================================================================

    @Bean
    public ConsumerFactory<String, String> iaConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Connection
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Consumer settings - optimized for IA processing
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
        
        // Performance settings
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1); // Process one IA request at a time
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 minutes (IA processing can be slow)
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000); // 30 seconds
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000); // 10 seconds
        
        // Fetch settings - tuned for large IA requests
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        configProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576); // 1MB
        
        log.info("[IA-KAFKA-CONFIG] Consumer factory configured - Servers: {}", bootstrapServers);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> iaKafkaListenerContainerFactory(
            ConsumerFactory<String, String> iaConsumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(iaConsumerFactory);
        
        // Concurrency settings - careful not to overwhelm IA service
        factory.setConcurrency(2); // Max 2 concurrent IA requests
        
        // Error handling
        factory.setCommonErrorHandler(null); // Use default error handler
        
        // Batch processing disabled for IA
        factory.setBatchListener(false);
        
        log.info("[IA-KAFKA-CONFIG] Listener container factory configured with concurrency: 2");
        return factory;
    }

    // =============================================================================
    // SPRING CLOUD STREAM CONFIGURATION PROPERTIES
    // =============================================================================

    /**
     * Returns default Spring Cloud Stream properties for IA applications
     * Applications can override these by setting properties in their application.yml
     */
    @Bean
    public IaStreamProperties iaStreamProperties() {
        IaStreamProperties properties = new IaStreamProperties();
        
        // Input binding defaults
        properties.setInputDestination("ia.requests");
        properties.setInputGroup("ia-processor-v10");
        properties.setInputMaxAttempts(3);
        properties.setInputBackOffInitialInterval(1000);
        properties.setInputBackOffMaxInterval(10000);
        properties.setInputConcurrency(2);
        
        // Output binding defaults
        properties.setOutputDestination("ia.responses");
        properties.setOutputPartitionCount(1);
        properties.setOutputRequiredGroups("workspace-ia-replies");
        
        // Kafka binder defaults
        properties.setAutoCreateTopics(true);
        properties.setAutoAddPartitions(false);
        properties.setRequiredAcks(1);
        properties.setMinPartitionCount(1);
        properties.setReplicationFactor(1);
        
        log.info("[IA-KAFKA-CONFIG] Stream properties configured - Input: {}, Output: {}", 
                properties.getInputDestination(), properties.getOutputDestination());
        
        return properties;
    }

    /**
     * Configuration properties holder for IA Stream settings
     */
    public static class IaStreamProperties {
        // Input settings
        private String inputDestination = "ia.requests";
        private String inputGroup = "ia-processor-v10";
        private int inputMaxAttempts = 3;
        private int inputBackOffInitialInterval = 1000;
        private int inputBackOffMaxInterval = 10000;
        private int inputConcurrency = 2;
        
        // Output settings
        private String outputDestination = "ia.responses";
        private int outputPartitionCount = 1;
        private String outputRequiredGroups = "workspace-ia-replies";
        
        // Binder settings
        private boolean autoCreateTopics = true;
        private boolean autoAddPartitions = false;
        private int requiredAcks = 1;
        private int minPartitionCount = 1;
        private int replicationFactor = 1;
        
        // Getters and setters
        public String getInputDestination() { return inputDestination; }
        public void setInputDestination(String inputDestination) { this.inputDestination = inputDestination; }
        
        public String getInputGroup() { return inputGroup; }
        public void setInputGroup(String inputGroup) { this.inputGroup = inputGroup; }
        
        public int getInputMaxAttempts() { return inputMaxAttempts; }
        public void setInputMaxAttempts(int inputMaxAttempts) { this.inputMaxAttempts = inputMaxAttempts; }
        
        public int getInputBackOffInitialInterval() { return inputBackOffInitialInterval; }
        public void setInputBackOffInitialInterval(int inputBackOffInitialInterval) { 
            this.inputBackOffInitialInterval = inputBackOffInitialInterval; 
        }
        
        public int getInputBackOffMaxInterval() { return inputBackOffMaxInterval; }
        public void setInputBackOffMaxInterval(int inputBackOffMaxInterval) { 
            this.inputBackOffMaxInterval = inputBackOffMaxInterval; 
        }
        
        public int getInputConcurrency() { return inputConcurrency; }
        public void setInputConcurrency(int inputConcurrency) { this.inputConcurrency = inputConcurrency; }
        
        public String getOutputDestination() { return outputDestination; }
        public void setOutputDestination(String outputDestination) { this.outputDestination = outputDestination; }
        
        public int getOutputPartitionCount() { return outputPartitionCount; }
        public void setOutputPartitionCount(int outputPartitionCount) { this.outputPartitionCount = outputPartitionCount; }
        
        public String getOutputRequiredGroups() { return outputRequiredGroups; }
        public void setOutputRequiredGroups(String outputRequiredGroups) { 
            this.outputRequiredGroups = outputRequiredGroups; 
        }
        
        public boolean isAutoCreateTopics() { return autoCreateTopics; }
        public void setAutoCreateTopics(boolean autoCreateTopics) { this.autoCreateTopics = autoCreateTopics; }
        
        public boolean isAutoAddPartitions() { return autoAddPartitions; }
        public void setAutoAddPartitions(boolean autoAddPartitions) { this.autoAddPartitions = autoAddPartitions; }
        
        public int getRequiredAcks() { return requiredAcks; }
        public void setRequiredAcks(int requiredAcks) { this.requiredAcks = requiredAcks; }
        
        public int getMinPartitionCount() { return minPartitionCount; }
        public void setMinPartitionCount(int minPartitionCount) { this.minPartitionCount = minPartitionCount; }
        
        public int getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }
    }
}