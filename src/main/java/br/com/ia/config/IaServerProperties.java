package br.com.ia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "ia")
public class IaServerProperties {

    // -----------------------
    // Flags de habilitação
    // -----------------------
    private boolean serverEnabled = false; // IaServer (consumer de requests + producer de responses)
    private boolean clientEnabled = true;  // Workspace/SDK (consumer de responses)

    // -----------------------
    // Identidade do módulo
    // -----------------------
    private String moduleName = "workspace";  // usado em logs/envelopes e no group default computed

    // -----------------------
    // Tópicos
    // -----------------------
    private String kafkaTopicRequests  = "ia.requests";
    private String kafkaTopicResponses = "ia.responses";
    
    // -----------------------
    // Base64 wrapper
    // -----------------------
    private boolean base64WrapperEnabled = true;    

    // -----------------------
    // INPUT (consumer params) – só valem quando serverEnabled=true
    // -----------------------
    private String bindingsInputGroup = "ia-processor-v10";
    private int    bindingsInputMaxAttempts = 3;
    private long   bindingsInputBackoffInitialMs = 1000;
    private long   bindingsInputBackoffMaxMs     = 10000;
    private int    bindingsInputConcurrency      = 2;

    // ------------------------
    // OUTPUT (producer params) – só valem quando serverEnabled=true
    // ------------------------
    private String bindingsOutputDestination    = "ia.responses";
    private int    bindingsOutputPartitionCount = 1;
    private String bindingsOutputRequiredGroups = "workspace-ia-replies"; 

    // -----------------------
    // Binder - opções globais
    // -----------------------
    private boolean binderAutoCreateTopics = true;
    private boolean binderAutoAddPartitions = false;
    private String  binderRequiredAcks = "1";
    private int     binderMinPartitionCount = 1;
    private short   binderReplicationFactor = 1;

    // -----------------------
    // Consumer tuning
    // -----------------------
    private String  consumerAutoOffsetReset = "latest";
    private int     consumerAutoCommitIntervalMs = 5000;
    private int     consumerMaxPollRecords = 1;
    private int     consumerMaxPollIntervalMs = 300000;
    private int     consumerSessionTimeoutMs = 45000;
    private int     consumerHeartbeatIntervalMs = 3000;
    private int     consumerFetchMinBytes = 1;
    private int     consumerFetchMaxWaitMs = 500;
    private int     consumerMaxPartitionFetchBytes = 1048576;

    // -----------------------
    // Producer tuning
    // -----------------------
    private boolean producerEnableIdempotence = true;
    private String  producerAcks = "all";
    private int     producerRetries = 3;
    private int     producerRetryBackoffMs = 1000;
    private int     producerBatchSize = 16384;
    private int     producerLingerMs = 5;
    private long    producerBufferMemory = 33554432L;
    private int     producerMaxInFlightReqPerConn = 5;
    private int     producerRequestTimeoutMs = 25000;
    private int     producerDeliveryTimeoutMs = 30000;
    private String  producerCompressionType = "snappy";

    // -----------------------
    // IA – timeouts diversos
    // -----------------------
    private long processingRequestTimeoutMs = 300000;

    // -----------------------
    // PendingIaRequestStore
    // -----------------------
    private long pendingReplyTimeoutMs         = 30000; // ia.pending.reply-timeout-ms
    private long pendingCleanupIntervalMinutes = 5;     // ia.pending.cleanup-interval-minutes
    private int  pendingMaxPendingRequests     = 1000;  // ia.pending.max-pending-requests

    // -----------------------
    // SDK group opcional
    // -----------------------
    private String kafkaGroupsWorkspaceIaReplies;

    /** Group “computado” para o listener de respostas do SDK */
    public String getComputedWorkspaceRepliesGroup() {
        if (kafkaGroupsWorkspaceIaReplies != null && !kafkaGroupsWorkspaceIaReplies.isBlank()) {
            return kafkaGroupsWorkspaceIaReplies;
        }
        String module = (moduleName == null || moduleName.isBlank()) ? "workspace" : moduleName.trim();
        return module + "-ia-replies";
    }

    // =========================================================================
    // LOG DE ARRANQUE
    // =========================================================================
    @PostConstruct
    void logOnStartup() {
        // Se o requiredGroups vier vazio, herdamos o computed group
        if (bindingsOutputRequiredGroups == null || bindingsOutputRequiredGroups.isBlank()) {
            bindingsOutputRequiredGroups = getComputedWorkspaceRepliesGroup();
        }

        log.info("🔧 IAServer – propriedades carregadas no startup:");
        log.info("   spring.kafka.bootstrap-servers = (via Spring se houver; sem .properties usamos defaults do app chamador)");
        log.info("   input.destination              = {}", getKafkaTopicRequests());
        log.info("   input.group                    = {}", getBindingsInputGroup());
        log.info("   input.max-attempts             = {}", getBindingsInputMaxAttempts());
        log.info("   input.backoff.initial.ms       = {}", getBindingsInputBackoffInitialMs());
        log.info("   input.backoff.max.ms           = {}", getBindingsInputBackoffMaxMs());
        log.info("   input.concurrency              = {}", getBindingsInputConcurrency());
        log.info("   output.destination             = {}", getBindingsOutputDestination());
        log.info("   output.partition-count         = {}", getBindingsOutputPartitionCount());
        log.info("   output.required-groups         = {}", getBindingsOutputRequiredGroups());
        log.info("   ia.kafka.topics.responses      = {}", getKafkaTopicResponses());
        log.info("   sdk.workspace.replies.group    = {}", getComputedWorkspaceRepliesGroup());
        log.info("   binder.auto-create-topics      = {}", isBinderAutoCreateTopics());
        log.info("   binder.auto-add-partitions     = {}", isBinderAutoAddPartitions());
        log.info("   binder.required-acks           = {}", getBinderRequiredAcks());
        log.info("   binder.min-partition-count     = {}", getBinderMinPartitionCount());
        log.info("   binder.replication-factor      = {}", getBinderReplicationFactor());
        log.info("   consumer.max.poll.records      = {}", getConsumerMaxPollRecords());
        log.info("   consumer.fetch.min.bytes       = {}", getConsumerFetchMinBytes());
        log.info("   consumer.fetch.max.wait.ms     = {}", getConsumerFetchMaxWaitMs());
        log.info("   consumer.max.partition.fetch   = {}", getConsumerMaxPartitionFetchBytes());
        log.info("   producer.enable.idempotence    = {}", isProducerEnableIdempotence());
        log.info("   producer.acks                  = {}", getProducerAcks());
        log.info("   producer.retries               = {}", getProducerRetries());
        log.info("   producer.retry.backoff.ms      = {}", getProducerRetryBackoffMs());
        log.info("   producer.batch.size            = {}", getProducerBatchSize());
        log.info("   producer.linger.ms             = {}", getProducerLingerMs());
        log.info("   producer.buffer.memory         = {}", getProducerBufferMemory());
        log.info("   producer.max.in.flight         = {}", getProducerMaxInFlightReqPerConn());
        log.info("   producer.request.timeout.ms    = {}", getProducerRequestTimeoutMs());
        log.info("   producer.delivery.timeout.ms   = {}", getProducerDeliveryTimeoutMs());
        log.info("   producer.compression.type      = {}", getProducerCompressionType());
        log.info("   ia.processing.request-timeout  = {} ms", getProcessingRequestTimeoutMs());
        log.info("   ia.pending.reply-timeout-ms    = {}", getPendingReplyTimeoutMs());
        log.info("   ia.pending.cleanup-interval    = {} min", getPendingCleanupIntervalMinutes());
        log.info("   ia.pending.max-pending         = {}", getPendingMaxPendingRequests());
    }
}
