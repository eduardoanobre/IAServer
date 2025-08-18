package br.com.ia.kafka;

import java.util.Map;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import br.com.ia.sdk.Base64MessageWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaDebug {

    public static final String WORKSPACE_STARTUP = "workspace-startup";
    
    private final StreamBridge bridge;
    private final Environment env;
    private final Base64MessageWrapper messageWrapper;

  //  @PostConstruct
    public void testarKafka() {
        log.info("🔍 Starting Kafka connection test with Base64 standardization...");

        String brokers = env.getProperty("spring.cloud.stream.kafka.binder.brokers");
        String serverPort = env.getProperty("server.port");
        String appName = env.getProperty("spring.application.name");

        log.info("📡 Brokers: {}", brokers);
        log.info("🚪 Server port: {}", serverPort);
        log.info("📱 App name: {}", appName);

        try {
            // Create test message
            Map<String, Object> mensagemTeste = Map.of(
                "test", "message", 
                "timestamp", String.valueOf(System.currentTimeMillis()), 
                "source", WORKSPACE_STARTUP,
                "module", appName != null ? appName : "unknown",
                "event", "KAFKA_CONNECTION_TEST"
            );

            // FIXED: Wrap in Base64 using standardized wrapper (returns String, not byte[])
            String base64Message = messageWrapper.wrapToBase64(mensagemTeste, "STARTUP_TEST");
            
            log.info("📦 Sending Base64 wrapped test message...");
            log.debug("Base64 message length: {} characters", base64Message.length());

            // FIXED: Send String directly (not byte[])
            boolean resultado = bridge.send("ia.requests", base64Message);

            if (resultado) {
                log.info("✅ Kafka working! Base64 message sent successfully to IAServer");
                log.info("🎯 Message type: STARTUP_TEST - IAServer should ignore this gracefully");
            } else {
                log.error("❌ Failed to send message - result: false");
            }

        } catch (Exception e) {
            log.error("❌ Error testing Kafka: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            log.error("Full stack trace:", e);
        }

        log.info("🏁 Kafka test completed");
    }

    /**
     * Sends a connection test to IAServer (can be called manually)
     */
    public boolean enviarTesteConexao() {
        try {
            log.info("🧪 Sending manual connection test to IAServer...");
            
            String appName = env.getProperty("spring.application.name");
            
            Map<String, Object> connectionTest = Map.of(
                "chatId", "test-" + appName + "-" + System.currentTimeMillis(),
                "source", "connection-test",
                "test", true,
                "timestamp", System.currentTimeMillis(),
                "module", appName != null ? appName : "unknown",
                "event", "MANUAL_CONNECTION_TEST"
            );

            // FIXED: This returns String, not byte[]
            String base64Message = messageWrapper.wrapToBase64(connectionTest, "CONNECTION_TEST");
            
            // FIXED: Send String directly
            boolean resultado = bridge.send("ia.requests", base64Message);

            if (resultado) {
                log.info("✅ Manual connection test sent successfully");
            } else {
                log.error("❌ Failed to send manual connection test");
            }

            return resultado;

        } catch (Exception e) {
            log.error("❌ Error sending manual connection test: {}", e.getMessage(), e);
            return false;
        }
    }
}