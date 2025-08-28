package br.com.ia.config;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

public class FunctionDefinitionWatchdog implements ApplicationListener<ApplicationPreparedEvent>, Ordered {

    private static final String[] KEYS_TO_TRACE = {
        // function defs & routing
        "spring.cloud.function.definition",
        "spring.cloud.stream.function.definition",
        "spring.cloud.stream.function.routing.enabled",

        // streamBridge bindings (disable/destination/auto-startup)
        "spring.cloud.stream.bindings.streamBridge-in-0.consumer.auto-startup",
        "spring.cloud.stream.bindings.streamBridge-out-0.producer.auto-startup",
        "spring.cloud.stream.bindings.streamBridge-in-0.destination",
        "spring.cloud.stream.bindings.streamBridge-out-0.destination",

        // seus bindings usuais
        "spring.cloud.stream.bindings.iaRequests-out-0.destination",
        "spring.cloud.stream.bindings.iaReplies-out-0.destination",

        // opcional: o consumer real quando serverEnabled=true
        "spring.cloud.stream.bindings.processIaConsumer-in-0.destination"
    };

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();

        System.out.println("[FunctionDefinitionWatchdog] ---- PropertySources (top -> bottom) ----"); // NOSONAR
        for (PropertySource<?> ps : env.getPropertySources()) {
            boolean printedHeader = false;
            for (String key : KEYS_TO_TRACE) {
                if (ps.containsProperty(key)) {
                    if (!printedHeader) {
                        System.out.println("[FunctionDefinitionWatchdog] " + ps.getName() + ":"); // NOSONAR
                        printedHeader = true;
                    }
                    Object raw = ps.getProperty(key);
                    System.out.println("  -> " + key + " = " + printable(raw)); // NOSONAR
                }
            }
        }

        System.out.println("[FunctionDefinitionWatchdog] ---- Resolved (Environment#getProperty) ----"); // NOSONAR
        for (String key : KEYS_TO_TRACE) {
            String val = env.getProperty(key);
            System.out.println("  " + key + " = " + printable(val)); // NOSONAR
        }
        System.out.println("[FunctionDefinitionWatchdog] -------------------------------------------"); // NOSONAR
    }

    private static String printable(Object v) {
        if (v == null) return "<null>";
        String s = String.valueOf(v);
        return s.isEmpty() ? "<empty>" : s;
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
