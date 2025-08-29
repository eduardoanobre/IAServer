package br.com.ia.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmbienteUtils {

    private static final String SPRING_CLOUD_STREAM_BINDINGS_IA_REPLIES_OUT_0_DESTINATION =
            "spring.cloud.stream.bindings.iaReplies-out-0.destination";
    private static final String SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_GROUP =
            "spring.cloud.stream.bindings.processIaConsumer-in-0.group";
    private static final String SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_DESTINATION =
            "spring.cloud.stream.bindings.processIaConsumer-in-0.destination";
    private static final String SPRING_KAFKA_BOOTSTRAP_SERVERS = "spring.kafka.bootstrap-servers";

    private final Environment env;

    private final ObjectProvider<KafkaTemplate<byte[], byte[]>> kafkaTemplateProvider;
    private final ObjectProvider<KafkaProperties> kafkaPropertiesProvider;
    private final ObjectProvider<ProducerFactory<byte[], byte[]>> producerFactoryProvider;
    private final ObjectProvider<ConsumerFactory<byte[], byte[]>> consumerFactoryProvider;
    private final ObjectProvider<KafkaListenerEndpointRegistry> kafkaListenerEndpointRegistryProvider;
    private final ObjectProvider<ConcurrentMessageListenerContainer<?, ?>> listenerContainersProvider;

    // ====== API pública ======
    public void logSpringCloudConfiguration() {

        boolean serverEnabled = getBoolean("ia.server.enabled", true);

        log.info("╔═════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                       SPRING CLOUD STREAM CONFIGURATION                     ║");
        log.info("╚═════════════════════════════════════════════════════════════════════════════╝");

        log.info("🔧 IAServer – snapshot de configuração:");
        log.info("   spring.kafka.bootstrap-servers = {}", env.getProperty(SPRING_KAFKA_BOOTSTRAP_SERVERS));
        log.info("   function.definition            = {}", env.getProperty("spring.cloud.function.definition"));
        log.info("   stream.fn.definition           = {}", env.getProperty("spring.cloud.stream.function.definition"));
        log.info("   input.binding                  = processIaConsumer-in-0");
        log.info("   input.destination              = {}", env.getProperty(SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_DESTINATION));
        log.info("   input.group                    = {}", env.getProperty(SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_GROUP));
        log.info("   output.binding                 = iaReplies-out-0");
        log.info("   output.destination             = {}", env.getProperty(SPRING_CLOUD_STREAM_BINDINGS_IA_REPLIES_OUT_0_DESTINATION));
        log.info("   output.required-groups         = {}",
                env.getProperty("spring.cloud.stream.bindings.iaReplies-out-0.producer.requiredGroups",
                        env.getProperty("spring.cloud.stream.bindings.iaReplies-out-0.producer.required-groups")));
        log.info("   ia.module.name                 = {}", env.getProperty("ia.module.name"));
        log.info("   ia.processing.request-timeout  = {} ms", env.getProperty("ia.processing.request-timeout-ms"));
        log.info("   ia.pending.reply-timeout       = {} ms", env.getProperty("ia.pending.reply-timeout-ms"));
        log.info("   ia.pending.cleanup-interval    = {} min", env.getProperty("ia.pending.cleanup-interval-minutes"));
        log.info("   ia.pending.max-pending         = {}", env.getProperty("ia.pending.max-pending-requests"));

        logApplicationInfo();

        if (serverEnabled) {
            // MODO SERVER -> imprime configs do Binder (spring.cloud.stream.*)
            logKafkaBrokerConfigurationFromBinder();
            logBindingsConfigurationFromBinder();
            logInputBindingDetailsFromBinder();
            logOutputBindingDetailsFromBinder();
            logKafkaSpecificConfigurationFromBinder();
        } else {
            // MODO CLIENT -> imprime configs EFETIVAS do Spring Kafka
            log.info("➡️  Modo CLIENT ativo (ia.server.enabled=false). Exibindo configuração EFETIVA do Spring Kafka.");
            logKafkaEffectiveConfiguration();
            logKafkaEffectiveListeners();
        }

        logIaProcessingConfiguration();
        drawArchitectureDiagram(serverEnabled);

        log.info("╔══════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                    CONFIGURAÇÃO FINALIZADA COM SUCESSO                       ║");
        log.info("╚══════════════════════════════════════════════════════════════════════════════╝");
    }

    // =========================================
    // SEÇÃO: INFO APP
    // =========================================
    private void logApplicationInfo() {
        String appName  = env.getProperty("spring.application.name", "app");
        String port     = orNI(env.getProperty("local.server.port", env.getProperty("server.port")));
        String encoding = env.getProperty("file.encoding", "N/I");

        log.info("");
        log.info("🚀 APPLICATION INFO:");
        log.info("   ├─ Nome: {}", appName);
        log.info("   ├─ Porta: {}", port);
        log.info("   ├─ Encoding: {}", encoding);
        log.info("   ├─ ia.module.name: {}", env.getProperty("ia.module.name", "N/I"));
        log.info("   ├─ ia.server.enabled: {}", String.valueOf(getBoolean("ia.server.enabled", true)));
        log.info("   └─ ia.client.enabled: {}", String.valueOf(getBoolean("ia.client.enabled", true)));
        log.info("");
    }

    // =========================================
    // SEÇÃO: BINDER (quando serverEnabled=true)
    // =========================================
    private void logKafkaBrokerConfigurationFromBinder() {
        log.info("🌐 KAFKA BROKER CONFIGURATION (Binder):");
        logLine("   ├─ Brokers", orNI(env.getProperty(SPRING_KAFKA_BOOTSTRAP_SERVERS)));
        logLine("   ├─ Auto Create Topics", prop("spring.cloud.stream.kafka.binder.autoCreateTopics"));
        logLine("   ├─ Auto Add Partitions", prop("spring.cloud.stream.kafka.binder.autoAddPartitions"));
        logLine("   ├─ Required Acks", prop("spring.cloud.stream.kafka.binder.requiredAcks"));
        logLine("   ├─ Min Partition Count", prop("spring.cloud.stream.kafka.binder.minPartitionCount"));
        logLine("   └─ Replication Factor", prop("spring.cloud.stream.kafka.binder.replicationFactor"));
        log.info("");
    }

    private void logBindingsConfigurationFromBinder() {
        log.info("🔗 FUNCTION BINDINGS (Binder):");
        logLine("   ├─ Function Definition", prop("spring.cloud.function.definition"));
        logLine("   ├─ Input Binding  ", "processIaConsumer-in-0");
        logLine("   └─ Output Binding ", "iaReplies-out-0");
        log.info("");
    }

    private void logInputBindingDetailsFromBinder() {
        log.info("📥 INPUT BINDING (processIaConsumer-in-0):");
        logLine("   ├─ Destination", prop(SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_DESTINATION));
        logLine("   ├─ Group",       prop(SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_GROUP));
        logLine("   ├─ Concurrency", prop("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.concurrency"));
        logLine("   └─ Content Type",prop("spring.cloud.stream.bindings.processIaConsumer-in-0.content-type"));
        log.info("");
    }

    private void logOutputBindingDetailsFromBinder() {
        log.info("📤 OUTPUT BINDING (iaReplies-out-0):");
        logLine("   ├─ Destination Topic", prop(SPRING_CLOUD_STREAM_BINDINGS_IA_REPLIES_OUT_0_DESTINATION));
        logLine("   ├─ Content Type",      prop("spring.cloud.stream.bindings.iaReplies-out-0.content-type"));
        logLine("   ├─ Partition Count",   prop("spring.cloud.stream.bindings.iaReplies-out-0.producer.partitionCount"));
        logLine("   └─ Required Groups",   prop("spring.cloud.stream.bindings.iaReplies-out-0.producer.requiredGroups"));
        log.info("");
    }

    private void logKafkaSpecificConfigurationFromBinder() {
        log.info("⚙️  KAFKA (Binder) – CONSUMER PROPS:");
        logLine("   ├─ Auto Offset Reset", prop("spring.cloud.stream.kafka.bindings.processIaConsumer-in-0.consumer.configuration.auto.offset.reset"));
        logLine("   ├─ Enable Auto Commit",prop("spring.cloud.stream.kafka.bindings.processIaConsumer-in-0.consumer.configuration.enable.auto.commit"));
        logLine("   ├─ Max Poll Records",  prop("spring.cloud.stream.kafka.bindings.processIaConsumer-in-0.consumer.configuration.max.poll.records"));
        logLine("   └─ Max Poll Interval", prop("spring.cloud.stream.kafka.bindings.processIaConsumer-in-0.consumer.configuration.max.poll.interval.ms"));
        log.info("");

        log.info("⚙️  KAFKA (Binder) – PRODUCER PROPS:");
        logLine("   ├─ Acks",               prop("spring.cloud.stream.kafka.bindings.iaReplies-out-0.producer.configuration.acks"));
        logLine("   ├─ Retries",            prop("spring.cloud.stream.kafka.bindings.iaReplies-out-0.producer.configuration.retries"));
        logLine("   ├─ Idempotence",        prop("spring.cloud.stream.kafka.bindings.iaReplies-out-0.producer.configuration.enable.idempotence"));
        logLine("   ├─ Request Timeout",    prop("spring.cloud.stream.kafka.bindings.iaReplies-out-0.producer.configuration.request.timeout.ms"));
        logLine("   ├─ Delivery Timeout",   prop("spring.cloud.stream.kafka.bindings.iaReplies-out-0.producer.configuration.delivery.timeout.ms"));
        logLine("   └─ Compression",        prop("spring.cloud.stream.kafka.bindings.iaReplies-out-0.producer.configuration.compression.type"));
        log.info("");
    }

    private void logKafkaEffectiveConfiguration() {
        log.info("🌐 SPRING KAFKA – CONFIGURAÇÃO EFETIVA:");

        KafkaProperties kp = kafkaPropertiesProvider.getIfAvailable();
        KafkaTemplate<byte[], byte[]> kt = kafkaTemplateProvider.getIfAvailable();
        ProducerFactory<byte[], byte[]> pf = (kt != null) ? kt.getProducerFactory()
                : producerFactoryProvider.getIfAvailable();
        ConsumerFactory<byte[], byte[]> cf = consumerFactoryProvider.getIfAvailable();

        // Brokers
        String bootstrap = (kp != null && kp.getBootstrapServers() != null && !kp.getBootstrapServers().isEmpty())
                ? String.join(",", kp.getBootstrapServers())
                : env.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
        logLine("   ├─ Brokers", orNI(bootstrap));

        // -------- PRODUCER --------
        Map<String, Object> prod = (pf != null) ? pf.getConfigurationProperties() : Map.of();

        log.info("   ├─ PRODUCER:");

        // acks (Kafka 3.6 default: "all")
        logLine("   │  ├─ acks",
                firstNonBlank(
                        val(prod, ProducerConfig.ACKS_CONFIG),
                        fromProducerProps(kp, "acks"),
                        envAny("spring.kafka.producer.acks", "spring.kafka.producer.properties.acks"),
                        "all"
                ));

        // enable.idempotence (Kafka 3.6 default: true)
        logLine("   │  ├─ enable.idempotence",
                firstNonBlank(
                        val(prod, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG),
                        fromProducerProps(kp, "enable.idempotence"),
                        envAny("spring.kafka.producer.properties.enable.idempotence"),
                        "true"
                ));

        // retries (Kafka 3.6 default: Integer.MAX_VALUE)
        logLine("   │  ├─ retries",
                firstNonBlank(
                        val(prod, ProducerConfig.RETRIES_CONFIG),
                        fromProducerProps(kp, "retries"),
                        envAny("spring.kafka.producer.retries", "spring.kafka.producer.properties.retries"),
                        String.valueOf(Integer.MAX_VALUE)
                ));

        // linger.ms
        logLine("   │  ├─ linger.ms",
                firstNonBlank(
                        val(prod, ProducerConfig.LINGER_MS_CONFIG),
                        fromProducerProps(kp, "linger.ms"),
                        envAny("spring.kafka.producer.linger-ms", "spring.kafka.producer.properties.linger.ms"),
                        "0"
                ));

        logLine("   │  ├─ batch.size",
                firstNonBlank(
                        val(prod, ProducerConfig.BATCH_SIZE_CONFIG),
                        fromProducerProps(kp, "batch.size"),
                        envAny("spring.kafka.producer.batch-size", "spring.kafka.producer.properties.batch.size"),
                        "16384"
                ));

        logLine("   │  ├─ compression.type",
                firstNonBlank(
                        val(prod, ProducerConfig.COMPRESSION_TYPE_CONFIG),
                        fromProducerProps(kp, "compression.type"),
                        envAny("spring.kafka.producer.compression-type", "spring.kafka.producer.properties.compression.type"),
                        "none"
                ));

        logLine("   │  ├─ request.timeout.ms",
                firstNonBlank(
                        val(prod, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG),
                        fromProducerProps(kp, "request.timeout.ms"),
                        envAny("spring.kafka.producer.properties.request.timeout.ms"),
                        "30000"
                ));

        logLine("   │  └─ delivery.timeout.ms",
                firstNonBlank(
                        val(prod, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG),
                        fromProducerProps(kp, "delivery.timeout.ms"),
                        envAny("spring.kafka.producer.properties.delivery.timeout.ms"),
                        "120000"
                ));

        // -------- CONSUMER --------
        Map<String, Object> cons = (cf != null) ? cf.getConfigurationProperties() : Map.of();

        // Dados reais do primeiro listener (quando existir)
        String realGroup = firstListenerGroup().orElse(null);

        log.info("   └─ CONSUMER:");

        logLine("      ├─ group.id",
                firstNonBlank(
                        realGroup,
                        val(cons, ConsumerConfig.GROUP_ID_CONFIG),
                        envAny("spring.kafka.consumer.group-id"),
                        "N/I"
                ));

        // auto.offset.reset (Kafka default: latest)
        logLine("      ├─ auto.offset.reset",
                firstNonBlank(
                        val(cons, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG),
                        (kp != null && kp.getConsumer() != null) ? kp.getConsumer().getAutoOffsetReset() : null,
                        fromConsumerProps(kp, "auto.offset.reset"),
                        envAny("spring.kafka.consumer.auto-offset-reset", "spring.kafka.consumer.properties.auto.offset.reset"),
                        "latest"
                ));

        // enable.auto.commit (Boot costuma defaultar para false)
        logLine("      ├─ enable.auto.commit",
                firstNonBlank(
                        val(cons, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG),
                        (kp != null && kp.getConsumer() != null) ? boolToStr(kp.getConsumer().getEnableAutoCommit()) : null,
                        fromConsumerProps(kp, "enable.auto.commit"),
                        envAny("spring.kafka.consumer.enable-auto-commit", "spring.kafka.consumer.properties.enable.auto.commit"),
                        "true"
                ));

        // max.poll.records
        logLine("      ├─ max.poll.records",
                firstNonBlank(
                        val(cons, ConsumerConfig.MAX_POLL_RECORDS_CONFIG),
                        (kp != null && kp.getConsumer() != null) ? intToStr(kp.getConsumer().getMaxPollRecords()) : null,
                        fromConsumerProps(kp, "max.poll.records"),
                        envAny("spring.kafka.consumer.max-poll-records", "spring.kafka.consumer.properties.max.poll.records"),
                        "500"
                ));

        // max.poll.interval.ms
        logLine("      ├─ max.poll.interval.ms",
                firstNonBlank(
                        val(cons, ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG),
                        fromConsumerProps(kp, "max.poll.interval.ms"),
                        envAny("spring.kafka.consumer.properties.max.poll.interval.ms"),
                        "300000"
                ));

        // session.timeout.ms
        logLine("      ├─ session.timeout.ms",
                firstNonBlank(
                        val(cons, ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG),
                        fromConsumerProps(kp, "session.timeout.ms"),
                        envAny("spring.kafka.consumer.properties.session.timeout.ms"),
                        "45000"
                ));

        // heartbeat.interval.ms
        logLine("      └─ heartbeat.interval.ms",
                firstNonBlank(
                        val(cons, ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG),
                        fromConsumerProps(kp, "heartbeat.interval.ms"),
                        envAny("spring.kafka.consumer.properties.heartbeat.interval.ms"),
                        "3000"
                ));

        log.info("");
    }

    private String groupIdEffective(Map<String, Object> cons) {
        // Preferir grupo configurado via properties (SDK listener)
        String sdkGroup = firstNonBlank(
                env.getProperty("ia.sdk.workspace.replies.group"),
                env.getProperty("sdk.workspace.replies.group")
        );
        if (notBlank(sdkGroup)) return sdkGroup;

        // Fallback: consumerFactory
        Object g = cons.get(ConsumerConfig.GROUP_ID_CONFIG);
        if (g != null) return String.valueOf(g);

        // Último fallback: KafkaProperties
        KafkaProperties kp = kafkaPropertiesProvider.getIfAvailable();
        if (kp != null && kp.getConsumer() != null) {
            String gid = kp.getConsumer().getGroupId();
            if (notBlank(gid)) return gid;
        }
        return "N/I";
    }

    private void logKafkaEffectiveListeners() {
        log.info("🧩 LISTENERS / CONTAINERS (EFETIVOS):");

        boolean any = false;

        KafkaListenerEndpointRegistry reg = kafkaListenerEndpointRegistryProvider.getIfAvailable();
        if (reg != null) {
            for (MessageListenerContainer c : reg.getListenerContainers()) {
                anyLogContainer(c, "(@KafkaListener)");
                any = true;
            }
        }

        for (ConcurrentMessageListenerContainer<?, ?> c : listenerContainersProvider) {
            anyLogContainer(c, "(programático)");
            any = true;
        }

        if (!any) {
            log.info("   └─ Nenhum container de listener detectado no contexto (ok se apenas produzir mensagens).");
        }
        log.info("");
    }

    private void anyLogContainer(MessageListenerContainer c, String source) {
        ContainerProperties cp = c.getContainerProperties();
        List<String> topicList = extractTopics(cp);

        String group = orNI(cp.getGroupId());
        Integer concurrency = (c instanceof ConcurrentMessageListenerContainer<?, ?> cc)
                ? cc.getConcurrency() : null;

        log.info("   ├─ Container {} {}:", c.getListenerId(), source);
        log.info("   │  ├─ group.id: {}", group);
        log.info("   │  ├─ topics  : {}", topicList.isEmpty() ? "N/I (pode ser TopicPartitions)" : topicList);
        log.info("   │  └─ concurrency: {}", (concurrency != null) ? concurrency : "N/I");
    }

    private List<String> extractTopics(ContainerProperties cp) {
        if (cp == null) return List.of();
        String[] topics = cp.getTopics();
        if (topics != null && topics.length > 0) return Arrays.asList(topics);
        var tps = cp.getTopicPartitions();
        if (tps != null && tps.length > 0) {
            return List.of(tps[0].getTopic()); // apenas o primeiro para exibir
        }
        return List.of();
    }

    private Optional<ConcurrentMessageListenerContainer<?, ?>> firstListenerContainer() {
        KafkaListenerEndpointRegistry reg = kafkaListenerEndpointRegistryProvider.getIfAvailable();
        if (reg != null) {
            for (MessageListenerContainer c : reg.getListenerContainers()) {
                if (c instanceof ConcurrentMessageListenerContainer<?, ?> cc) {
                    return Optional.of(cc);
                }
            }
        }
        for (ConcurrentMessageListenerContainer<?, ?> c : listenerContainersProvider) {
            if (c != null) {  
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstListenerGroup() {
        return firstListenerContainer()
                .map(ConcurrentMessageListenerContainer::getContainerProperties)
                .map(ContainerProperties::getGroupId)
                .filter(this::notBlank);
    }

    private Optional<Integer> firstListenerConcurrency() {
        return firstListenerContainer().map(ConcurrentMessageListenerContainer::getConcurrency);
    }

    private Optional<String> firstListenerTopic() {
        KafkaListenerEndpointRegistry reg = kafkaListenerEndpointRegistryProvider.getIfAvailable();
        if (reg != null) {
            for (MessageListenerContainer c : reg.getListenerContainers()) {
                List<String> list = extractTopics(c.getContainerProperties());
                if (!list.isEmpty()) return Optional.of(list.get(0));
            }
        }
        for (ConcurrentMessageListenerContainer<?, ?> c : listenerContainersProvider) {
            List<String> list = extractTopics(c.getContainerProperties());
            if (!list.isEmpty()) return Optional.of(list.get(0));
        }
        return Optional.empty();
    }

    // =========================================
    // SEÇÃO: IA / DIAGRAMA
    // =========================================
    private void logIaProcessingConfiguration() {
        log.info("🤖 IA PROCESSING CONFIGURATION:");
        logLine("   ├─ Request Timeout (ms)",   String.valueOf(getLong("ia.processing.request-timeout-ms", 300_000L)));
        logLine("   ├─ Pending Reply Timeout (ms)", String.valueOf(getLong("ia.pending.reply-timeout-ms", 30_000L)));
        logLine("   ├─ Pending Cleanup Interval (min)", String.valueOf(getLong("ia.pending.cleanup-interval-minutes", 5L)));
        logLine("   ├─ Pending Max Requests",   String.valueOf(getInt("ia.pending.max-pending-requests", 1000)));
        logLine("   └─ Base64 Wrapper Enabled", String.valueOf(getBoolean("ia.base64-wrapper-enabled", true)));
        log.info("");
    }

    // ======= Diagrama com larguras fixas e consistentes =======
    private static final int BOX_INPUT  = 27;
    private static final int BOX_APP    = 22;
    private static final int BOX_OUTPUT = 22;
    private static final String GAP = "    "; // espaço entre caixinhas
    private static final String LM  = "  ";   // margem interna

    private void drawArchitectureDiagram(boolean serverEnabled) {
        KafkaProperties kp = kafkaPropertiesProvider.getIfAvailable();
        ConsumerFactory<byte[], byte[]> cf = consumerFactoryProvider.getIfAvailable();

        String app = env.getProperty("spring.application.name", "app");

        String brokers = "N/I";
        if (kp != null && kp.getBootstrapServers() != null && !kp.getBootstrapServers().isEmpty()) {
            brokers = String.join(",", kp.getBootstrapServers());
        } else {
            String b = env.getProperty(SPRING_KAFKA_BOOTSTRAP_SERVERS);
            if (notBlank(b)) brokers = b;
        }

        String inputTopic;
        String outputTopic;

        if (serverEnabled) {
            inputTopic  = orNI(env.getProperty(SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_DESTINATION));
            outputTopic = orNI(env.getProperty(SPRING_CLOUD_STREAM_BINDINGS_IA_REPLIES_OUT_0_DESTINATION));
        } else {
            // CLIENT: consome respostas e produz requests
            inputTopic  = firstListenerTopic().orElse(orNI(topicResponses()));
            outputTopic = orNI(topicRequests());
        }

        String group;
        if (serverEnabled) {
            group = orNI(env.getProperty("SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_GROUP"));
        } else {
            Map<String, Object> configProperties = (cf != null) ? cf.getConfigurationProperties() : Map.of();
            String effectiveGroupId = groupIdEffective(configProperties);
            String fallbackGroup = firstListenerGroup().orElse(effectiveGroupId);
            group = orNI(fallbackGroup);
        }
        
        String partitions = serverEnabled
                ? orNI(env.getProperty("spring.cloud.stream.bindings.iaReplies-out-0.producer.partitionCount"))
                : "N/I";

        String concurrency = serverEnabled
                ? orNI(env.getProperty("spring.cloud.stream.bindings.processIaConsumer-in-0.consumer.concurrency"))
                : orNI(firstListenerConcurrency().map(String::valueOf).orElse("N/I"));

        int innerWidth =
                LM.length()
                        + (1 + BOX_INPUT  + 1)
                        + GAP.length()
                        + (1 + BOX_APP    + 1)
                        + GAP.length()
                        + (1 + BOX_OUTPUT + 1)
                        + LM.length();

        String topBorder    = "╔" + "═".repeat(innerWidth) + "╗";
        String middleBorder = "╠" + "═".repeat(innerWidth) + "╣";
        String bottomBorder = "╚" + "═".repeat(innerWidth) + "╝";

        String titleLine   = "║" + pad(center("KAFKA CLUSTER", innerWidth), innerWidth) + "║";
        String brokerLine  = "║" + pad(center(brokers,       innerWidth), innerWidth) + "║";

        String boxesTop    = "║" + LM + topCell(BOX_INPUT)  + GAP + topCell(BOX_APP)  + GAP + topCell(BOX_OUTPUT)  + LM + "║";
        String headersRow  = "║" + LM
                + cell(center("INPUT TOPIC", BOX_INPUT), BOX_INPUT) + GAP
                + cell(center("APPLICATION", BOX_APP), BOX_APP)     + GAP
                + cell(center("OUTPUT TOPIC", BOX_OUTPUT), BOX_OUTPUT)
                + LM + "║";

        String valuesRow   = "║" + LM
                + cell(inputTopic, BOX_INPUT) + GAP
                + cell(app, BOX_APP)          + GAP
                + cell(outputTopic, BOX_OUTPUT)
                + LM + "║";

        String groupRow    = "║" + LM
                + cell("Group: " + group, BOX_INPUT) + GAP
                + cell("", BOX_APP)                   + GAP
                + cell("Partitions: " + partitions, BOX_OUTPUT)
                + LM + "║";

        String concRow     = "║" + LM
                + cell("Concurrency: " + concurrency, BOX_INPUT) + GAP
                + cell("", BOX_APP)                               + GAP
                + cell("", BOX_OUTPUT)
                + LM + "║";

        String boxesBottom = "║" + LM + bottomCell(BOX_INPUT) + GAP + bottomCell(BOX_APP) + GAP + bottomCell(BOX_OUTPUT) + LM + "║";

        log.info("📊 ARCHITECTURE DIAGRAM:");
        log.info(topBorder);
        log.info(titleLine);
        log.info(brokerLine);
        log.info(middleBorder);
        log.info(boxesTop);
        log.info(headersRow);
        log.info(valuesRow);
        log.info(groupRow);
        log.info(concRow);
        log.info(boxesBottom);
        log.info(bottomBorder);
    }

    // =========================================
    // HELPERS
    // =========================================
    private String prop(String key) {
        return orNI(env.getProperty(key));
    }

    private String val(Map<String, Object> map, String key) {
        return orNI(map.get(key));
    }

    private void logLine(String label, String value) {
        log.info("{}: {}", label, orNI(value));
    }

    private String orNI(Object v) {
        String s = (v == null) ? null : String.valueOf(v);
        return notBlank(s) ? s : "N/I";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "N/I";
        for (String v : values) {
            if (notBlank(v) && !"N/I".equals(v)) return v;
        }
        return "N/I";
    }

    // ===== Helpers de layout para o diagrama =====
    private String pad(String s, int w) {
        if (s == null) s = "";
        if (s.length() == w) return s;
        if (s.length() < w)  return s + " ".repeat(w - s.length());
        return (w >= 1) ? s.substring(0, Math.max(0, w - 1)) + "…" : "";
    }

    private String center(String s, int w) {
        if (s == null) s = "";
        if (s.length() >= w) return pad(s, w);
        int left = (w - s.length()) / 2;
        int right = w - s.length() - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }

    private String cell(String content, int width) {
        return "│" + pad(content, width) + "│";
    }

    private String fromProducerProps(KafkaProperties kp, String key) {
        if (kp == null || kp.getProducer() == null) return null;
        Object v = kp.getProducer().getProperties().get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private String envAny(String... keys) {
        for (String k : keys) {
            String v = env.getProperty(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String fromConsumerProps(KafkaProperties kp, String key) {
        if (kp == null || kp.getConsumer() == null) return null;
        Object v = kp.getConsumer().getProperties().get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private String boolToStr(Boolean b) { return b == null ? null : String.valueOf(b); }
    private String intToStr(Integer i)  { return i == null ? null : String.valueOf(i); }
    private String topCell(int width)    { return "┌" + "─".repeat(width) + "┐"; }
    private String bottomCell(int width) { return "└" + "─".repeat(width) + "┘"; }

    // ===== Helpers de leitura tipada do Environment =====
    private boolean getBoolean(String key, boolean def) {
        Boolean v = env.getProperty(key, Boolean.class);
        return v != null ? v : def;
    }
    private long getLong(String key, long def) {
        Long v = env.getProperty(key, Long.class);
        return v != null ? v : def;
    }
    private int getInt(String key, int def) {
        Integer v = env.getProperty(key, Integer.class);
        return v != null ? v : def;
    }

    // ===== Tópicos padrão quando não estiverem presentes no binder =====
    private String topicRequests() {
        // Preferir o destino configurado no binder (mesmo em client mode)
        String t = env.getProperty(SPRING_CLOUD_STREAM_BINDINGS_PROCESS_IA_CONSUMER_IN_0_DESTINATION);
        return notBlank(t) ? t : "ia.requests";
    }
    private String topicResponses() {
        String t = env.getProperty(SPRING_CLOUD_STREAM_BINDINGS_IA_REPLIES_OUT_0_DESTINATION);
        return notBlank(t) ? t : "ia.responses";
    }
}
