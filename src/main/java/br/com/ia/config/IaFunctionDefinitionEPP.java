package br.com.ia.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class IaFunctionDefinitionEPP implements EnvironmentPostProcessor, Ordered {

    private static final String SPRING_CLOUD_STREAM_FUNCTION_DEFINITION = "spring.cloud.stream.function.definition";
	private static final String SPRING_CLOUD_FUNCTION_DEFINITION = "spring.cloud.function.definition";
	
	private static final String PS_NAME = "iaFunctionDefinitionEarlyOverride";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        System.out.println("[IaFunctionDefinitionEPP] >>> running (order=HIGHEST_PRECEDENCE)");  // NOSONAR

        boolean serverEnabled = Boolean.parseBoolean(env.getProperty("ia.server.enabled", "false"));
        System.out.println("[IaFunctionDefinitionEPP] ia.server.enabled = " + serverEnabled); // NOSONAR
        System.out.println("[IaFunctionDefinitionEPP] previous 'spring.cloud.function.definition' = " + env.getProperty(SPRING_CLOUD_FUNCTION_DEFINITION)); // NOSONAR
        System.out.println("[IaFunctionDefinitionEPP] previous 'spring.cloud.stream.function.definition' = " + env.getProperty(SPRING_CLOUD_STREAM_FUNCTION_DEFINITION)); // NOSONAR

        Map<String, Object> o = new LinkedHashMap<>();

        if (!serverEnabled) {
            // workspace mode: zera function defs e desliga auto-startup dos canais do streamBridge
            o.put(SPRING_CLOUD_FUNCTION_DEFINITION, "");
            o.put(SPRING_CLOUD_STREAM_FUNCTION_DEFINITION, "");
            o.put("spring.cloud.stream.function.routing.enabled", false);

            // não coloque DESTINATION; apenas garanta que não iniciem
            o.put("spring.cloud.stream.bindings.streamBridge-in-0.consumer.auto-startup", false);
            o.put("spring.cloud.stream.bindings.streamBridge-out-0.producer.auto-startup", false);

            // silencia o WARN do registrador
            o.put("logging.level.org.springframework.cloud.stream.function.FunctionConfiguration$FunctionBindingRegistrar", "ERROR");
            System.out.println("[IaFunctionDefinitionEPP] workspace mode -> function defs cleared, streamBridge bindings auto-startup=false"); // NOSONAR
        } else {
            // server mode: apenas o consumer
            o.put(SPRING_CLOUD_FUNCTION_DEFINITION, "processIaConsumer");
            o.put(SPRING_CLOUD_STREAM_FUNCTION_DEFINITION, "processIaConsumer");
            o.put("spring.cloud.stream.function.routing.enabled", false);
            System.out.println("[IaFunctionDefinitionEPP] server mode -> function definition set to 'processIaConsumer'"); // NOSONAR
        }

        env.getPropertySources().addFirst(new MapPropertySource(PS_NAME, o));
        System.out.println("[IaFunctionDefinitionEPP] property source '" + PS_NAME + "' registered with keys: " + o.keySet()); // NOSONAR

        System.out.println("[IaFunctionDefinitionEPP] final 'spring.cloud.function.definition' = " + env.getProperty(SPRING_CLOUD_FUNCTION_DEFINITION)); // NOSONAR
        System.out.println("[IaFunctionDefinitionEPP] final 'spring.cloud.stream.function.definition' = " + env.getProperty(SPRING_CLOUD_STREAM_FUNCTION_DEFINITION)); // NOSONAR
        System.out.println("[IaFunctionDefinitionEPP] final streamBridge-in-0.consumer.auto-startup = " + env.getProperty("spring.cloud.stream.bindings.streamBridge-in-0.consumer.auto-startup")); // NOSONAR
        System.out.println("[IaFunctionDefinitionEPP] final streamBridge-out-0.producer.auto-startup = " + env.getProperty("spring.cloud.stream.bindings.streamBridge-out-0.producer.auto-startup")); // NOSONAR
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
