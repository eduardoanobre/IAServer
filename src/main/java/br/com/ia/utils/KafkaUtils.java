package br.com.ia.utils;

import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class KafkaUtils {

	/**
	 * Loga todas as configurações do Kafka encontradas nos arquivos de configuração
	 * 
	 * @param environment o Environment do Spring para acessar as propriedades
	 */
	public void logKafkaProperties(Environment environment) {
		log.info("╔════════════════════════════════════════════════════════════════╗");
		log.info("║              Carregando variáveis Kafka                        ║");
		log.info("╚════════════════════════════════════════════════════════════════╝");
		log.info("=== Inicializando variáveis de ambiente ===");
		log.info("=== Configurações do Kafka ===");

		// Lista das principais propriedades do Kafka
		String[] kafkaProperties = { 
                "spring.kafka.bootstrap-servers",
                "spring.kafka.client-id",
                "spring.kafka.consumer.group-id",
                "spring.kafka.consumer.auto-offset-reset",
                "spring.kafka.consumer.key-deserializer",
                "spring.kafka.consumer.value-deserializer",
                "spring.kafka.consumer.fetch-min-size",
                "spring.kafka.consumer.fetch-max-wait",
                "spring.kafka.consumer.max-poll-records",
                "spring.kafka.consumer.enable-auto-commit",
                "spring.kafka.consumer.auto-commit-interval",
                "spring.kafka.producer.key-serializer",
                "spring.kafka.producer.value-serializer",
                "spring.kafka.producer.acks",
                "spring.kafka.producer.retries",
                "spring.kafka.producer.batch-size",
                "spring.kafka.producer.linger-ms",
                "spring.kafka.producer.buffer-memory",
                "spring.kafka.producer.compression-type",
                "spring.kafka.security.protocol",
                "spring.kafka.ssl.trust-store-location",
                "spring.kafka.ssl.trust-store-password",
                "spring.kafka.ssl.key-store-location",
                "spring.kafka.ssl.key-store-password",
                "spring.kafka.sasl.mechanism",
                "spring.kafka.sasl.jaas.config",
                "spring.kafka.properties.sasl.mechanism",
                "spring.kafka.properties.security.protocol",
                "spring.kafka.listener.concurrency",
                "spring.kafka.listener.poll-timeout",
                "spring.kafka.listener.type",
                "spring.kafka.listener.ack-mode",
                "spring.kafka.listener.client-id",
                "spring.kafka.listener.log-container-config",
                "spring.kafka.admin.client-id",
                "spring.kafka.admin.fail-fast",
                "spring.kafka.admin.properties.*",
                "spring.kafka.streams.application-id",
                "spring.kafka.streams.client-id",
                "spring.kafka.streams.replication-factor",
                "spring.kafka.streams.state-dir",
                "spring.kafka.jaas.enabled",
                "spring.kafka.jaas.login-module",
                "spring.kafka.jaas.control-flag",
                "spring.kafka.jaas.options.*" };

		boolean foundKafkaConfig = false;

		// Verificar propriedades padrão do Kafka
		for (String property : kafkaProperties) {
			String value = environment.getProperty(property);
			if (value != null) {
				foundKafkaConfig = true;
				logPropertyValue(property, value);
			}
		}

		// Verificar propriedades dinâmicas do Kafka (spring.kafka.properties.*)
		foundKafkaConfig = logDynamicKafkaProperties(environment) || foundKafkaConfig;

		if (!foundKafkaConfig) {
			log.info("Nenhuma configuração específica do Kafka encontrada nos arquivos de configuração");
		}

		log.info("=== Fim das configurações do Kafka ===");
	}

	/**
	 * Loga propriedades específicas da aplicação
	 * 
	 * @param environment  o Environment do Spring
	 * @param propertyName nome da propriedade
	 * @param currentValue valor atual da propriedade
	 * @param defaultValue valor padrão da propriedade
	 */
	public void logConfigProperty(Environment environment, String propertyName, String currentValue,
			String defaultValue) {
		String configValue = environment.getProperty(propertyName);
		if (configValue != null) {
			log.info("{}: {} (obtido do arquivo de configuração)", propertyName, currentValue);
		} else {
			log.info("{}: {} (valor padrão)", propertyName, currentValue);
		}
	}

	private void logPropertyValue(String property, String value) {
		if (isSensitiveProperty(property)) {
			log.info("{}: ******* (configurado - valor mascarado por segurança)", property);
		} else {
			log.info("{}: {}", property, value);
		}
	}

	private boolean isSensitiveProperty(String property) {
		String lowerProperty = property.toLowerCase();
		return lowerProperty.contains("password") || lowerProperty.contains("secret") || lowerProperty.contains("jaas")
				|| lowerProperty.contains("key-store") || lowerProperty.contains("trust-store");
	}

	private boolean logDynamicKafkaProperties(Environment environment) {
		boolean foundDynamic = false;

		MutablePropertySources propertySources = ((AbstractEnvironment) environment).getPropertySources();
		for (PropertySource<?> propertySource : propertySources) {
			foundDynamic = processPropertySource(propertySource, environment, foundDynamic);
		}

		return foundDynamic;
	}

	private boolean processPropertySource(PropertySource<?> propertySource, Environment environment,
			boolean foundDynamic) {
		if (!(propertySource instanceof EnumerablePropertySource)) {
			return foundDynamic;
		}

		EnumerablePropertySource<?> enumerableSource = (EnumerablePropertySource<?>) propertySource;
		for (String name : enumerableSource.getPropertyNames()) {
			foundDynamic = processKafkaProperty(name, environment, foundDynamic);
		}

		return foundDynamic;
	}

	private boolean processKafkaProperty(String name, Environment environment, boolean foundDynamic) {
		if (!name.startsWith("spring.kafka.") || isAlreadyListed(name)) {
			return foundDynamic;
		}

		String value = environment.getProperty(name);
		if (value != null) {
			logPropertyValue(name, value);
			return true;
		}

		return foundDynamic;
	}

	private boolean isAlreadyListed(String propertyName) {
		// Evita duplicatas das propriedades já listadas no array principal
		return propertyName.matches(
				"spring\\.kafka\\.(bootstrap-servers|client-id|consumer\\.|producer\\.|security\\.|ssl\\.|sasl\\.|listener\\.|admin\\.|streams\\.|jaas\\.).*");
	}
}
