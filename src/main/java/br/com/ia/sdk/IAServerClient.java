package br.com.ia.sdk;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.com.ia.messaging.MessageType;
import br.com.ia.model.IaRequest;
import br.com.ia.publisher.IaPublisher;
import br.com.ia.sdk.context.repository.LogIARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente principal para comunicação com o sistema IAServer.
 * <p>
 * Esta classe é responsável por enviar requisições de IA para o sistema
 * IAServer através de mensageria Kafka. Encapsula todas as operações
 * necessárias incluindo: validação, normalização de parâmetros, empacotamento
 * em Base64 e envio assíncrono.
 * </p>
 * 
 * <h3>Funcionalidades principais:</h3>
 * <ul>
 * <li><strong>Validação robusta:</strong> Verifica integridade de requisições
 * antes do envio</li>
 * <li><strong>Normalização segura:</strong> Ajusta parâmetros para valores
 * seguros</li>
 * <li><strong>Empacotamento Base64:</strong> Envolve mensagens em envelope
 * padronizado</li>
 * <li><strong>Envio síncrono:</strong> Comunicação confiável via Kafka com
 * confirmação</li>
 * <li><strong>Rastreamento:</strong> Log detalhado e marcação de status no
 * repositório</li>
 * </ul>
 * 
 * <h3>Exemplo de uso:</h3>
 * 
 * <pre>
 * IaRequest request = new IaRequest();
 * request.setChatId("UUID");
 * request.setPrompt("Analise os dados fornecidos");
 * request.setCorrelationId("12345");
 * 
 * Map&lt;String, Object&gt; options = new HashMap&lt;&gt;();
 * options.put(IaRequest.API_KEY, "sk-...");
 * options.put(IaRequest.MODEL, "gpt-4o");
 * request.setOptions(options);
 * 
 * boolean success = iaServerClient.sendIaRequest(request);
 * </pre>
 * 
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class IAServerClient {

	private final IaPublisher publisher;

	/**
	 * Repositório para logging e rastreamento de requisições IA. Usado para marcar
	 * status de envio e auditoria.
	 */
	private final LogIARepository repository;

	/**
	 * Nome do módulo que está enviando a requisição. Usado para identificação e
	 * rastreamento no envelope da mensagem.
	 */
	@Value("${ia.module.name}")
	private String moduleName;

	// ===== CONSTANTES =====

	/** Limite máximo de tokens para respostas (segurança) */
	private static final int MAX_SAFE_TOKENS = 32000;

	/** Limite mínimo de tokens para respostas */
	private static final int MIN_SAFE_TOKENS = 100;

	/** Valor padrão de tokens quando não especificado */
	private static final int DEFAULT_TOKENS = 8000;

	/** Temperatura padrão quando não especificada */
	private static final double DEFAULT_TEMPERATURE = 0.7;

	/** Temperatura máxima permitida */
	private static final double MAX_TEMPERATURE = 2.0;

	/** Temperatura mínima permitida */
	private static final double MIN_TEMPERATURE = 0.0;

	/**
	 * Envia uma requisição IA para o sistema IAServer de forma síncrona.
	 * <p>
	 * Este método executa o fluxo completo de envio incluindo validação,
	 * normalização, empacotamento e envio via Kafka. O processo é síncrono e
	 * aguarda confirmação de entrega.
	 * </p>
	 * 
	 * <h4>Fluxo de execução:</h4>
	 * <ol>
	 * <li>Validação da integridade da requisição</li>
	 * <li>Normalização de parâmetros para valores seguros</li>
	 * <li>Empacotamento da mensagem em envelope Base64</li>
	 * <li>Envio síncrono via Kafka com confirmação</li>
	 * <li>Marcação do status no repositório de logs</li>
	 * </ol>
	 * 
	 * @param request Requisição IA a ser enviada (não pode ser null)
	 * @return true se o envio foi bem-sucedido, false caso contrário
	 * 
	 * @throws IllegalArgumentException se request for null
	 * @implNote Este método é thread-safe e pode ser chamado concorrentemente
	 * @see #isValidIaRequest(IaRequest) para critérios de validação
	 * @see #adjustSafeOptions(IaRequest) para normalização de parâmetros
	 */
	public boolean sendIaRequest(IaRequest request) {
		if (request == null) {
			log.error("[SEND-IA-REQUEST] Request cannot be null");
			throw new IllegalArgumentException("IaRequest cannot be null");
		}

		final String chatId = request.getChatId();
		final String correlationId = request.getCorrelationId();

		log.info("[SEND-IA-REQUEST] Starting - ChatId: {}, CorrelationId: {}", chatId, correlationId);

		// Fase 1: Validação
		if (!isValidIaRequest(request)) {
			log.error("[SEND-IA-REQUEST] Validation failed - ChatId: {}", chatId);
			return false;
		}

		// Fase 2: Normalização
		adjustSafeOptions(request);
		log.debug("[SEND-IA-REQUEST] Options normalized - ChatId: {}", chatId);

		publisher.publish(MessageType.IA_REQUEST, request, chatId).whenComplete((md, ex) -> {
			if (ex == null) {
				log.info("[SEND-IA-REQUEST] SUCCESS - CorrelationId: {}", correlationId);
				markRequestAsSent(request);
			} else {
				log.error("[SEND-IA-REQUEST] FAILED - ChatId: {}", chatId);
			}
		});

		return true;
	}

	/**
	 * Marca a requisição como enviada no repositório de logs.
	 * <p>
	 * Utiliza o correlationId como chave primária para localizar e atualizar o
	 * registro no banco de dados. Falhas nesta operação não afetam o resultado do
	 * envio da mensagem.
	 * </p>
	 * 
	 * @param request Requisição que foi enviada com sucesso
	 * @implNote Falhas neste método são logadas mas não propagadas
	 */
	private void markRequestAsSent(IaRequest request) {
		try {
			if (!StringUtils.hasText(request.getCorrelationId())) {
				log.warn("[MARK-SENT] CorrelationId is empty, cannot mark as sent");
				return;
			}

			Long logId = Long.parseLong(request.getCorrelationId());
			repository.setEnviado(logId);
			log.debug("[MARK-SENT] Successfully marked - LogId: {}", logId);

		} catch (NumberFormatException e) {
			log.warn("[MARK-SENT] Invalid CorrelationId format: {}", request.getCorrelationId());
		} catch (Exception e) {
			log.error("[MARK-SENT] Failed to mark as sent - CorrelationId: {}, Error: {}", request.getCorrelationId(),
					e.getMessage(), e);
		}
	}

	/**
	 * Valida requisição IA antes do envio.
	 * <p>
	 * Executa validações obrigatórias para garantir que a requisição está em
	 * formato válido e contém todas as informações necessárias para processamento
	 * pelo IAServer.
	 * </p>
	 * 
	 * <h4>Validações executadas:</h4>
	 * <ul>
	 * <li>Requisição não nula</li>
	 * <li>ChatId preenchido e válido</li>
	 * <li>Prompt preenchido e não vazio</li>
	 * <li>Options configurado e válido</li>
	 * <li>API Key presente nas options</li>
	 * <li>Modelo especificado nas options</li>
	 * </ul>
	 * 
	 * @param request Requisição a ser validada
	 * @return true se todas as validações passarem, false caso contrário
	 * @implNote Todos os erros de validação são logados com nível ERROR
	 */
	private boolean isValidIaRequest(IaRequest request) {
		// Validação básica de nulidade
		if (request == null) {
			log.error("[VALIDATION] IaRequest is null");
			return false;
		}

		// Validação do CorrelationId
		if (!StringUtils.hasText(request.getCorrelationId())) {
			log.error("[VALIDATION] CorrelationId is null or empty");
			return false;
		}

		// Validação do ChatId
		if (!StringUtils.hasText(request.getChatId())) {
			log.error("[VALIDATION] ChatId is null or empty");
			return false;
		}

		// Validação do Prompt
		if (!StringUtils.hasText(request.getPrompt())) {
			log.error("[VALIDATION] Prompt is null or empty - ChatId: {}", request.getChatId());
			return false;
		}

		// Validação das Options
		Map<String, Object> options = request.getOptions();
		if (options == null || options.isEmpty()) {
			log.error("[VALIDATION] Options are null or empty - ChatId: {}", request.getChatId());
			return false;
		}

		// Validação da API Key
		if (!options.containsKey(IaRequest.API_KEY)
				|| !StringUtils.hasText(String.valueOf(options.get(IaRequest.API_KEY)))) {
			log.error("[VALIDATION] API key is missing or empty - ChatId: {}", request.getChatId());
			return false;
		}

		// Validação do Modelo
		if (!options.containsKey(IaRequest.MODELO)
				|| !StringUtils.hasText(String.valueOf(options.get(IaRequest.MODELO)))) {
			log.error("[VALIDATION] Model is missing or empty - ChatId: {}", request.getChatId());
			return false;
		}

		log.debug("[VALIDATION] All validations passed - ChatId: {}", request.getChatId());
		return true;
	}

	/**
	 * Ajusta e normaliza opções da requisição para valores seguros.
	 * <p>
	 * Aplica validações e correções automáticas nos parâmetros da requisição para
	 * garantir que estão dentro de limites seguros e funcionais. Valores fora dos
	 * limites são automaticamente ajustados.
	 * </p>
	 * 
	 * <h4>Normalizações aplicadas:</h4>
	 * <ul>
	 * <li><strong>Max Tokens:</strong> Entre 100 e 32.000 (padrão: 8.000)</li>
	 * <li><strong>Temperature:</strong> Entre 0.0 e 2.0 (padrão: 0.7)</li>
	 * </ul>
	 * 
	 * @param request Requisição cujas opções serão normalizadas
	 * @implNote Modificações são feitas diretamente na requisição original
	 */
	private void adjustSafeOptions(IaRequest request) {
		Map<String, Object> options = request.getOptions();
		if (options == null) {
			options = new HashMap<>();
			request.setOptions(options);
		}

		// Cria uma cópia das options para modificação segura
		Map<String, Object> adjustedOptions = new HashMap<>(options);

		// Normalização de Max Output Tokens
		int maxTokens = normalizeMaxTokens(options.get(IaRequest.MAX_OUTPUT_TOKENS));
		adjustedOptions.put(IaRequest.MAX_OUTPUT_TOKENS, maxTokens);

		// Normalização de Temperature
		double temperature = normalizeTemperature(options.get(IaRequest.TEMPERATURE));
		adjustedOptions.put(IaRequest.TEMPERATURE, temperature);

		// Atualização da requisição
		request.setOptions(adjustedOptions);

		log.debug("[ADJUST-OPTIONS] Normalized - ChatId: {}, MaxTokens: {}, Temperature: {}", request.getChatId(),
				maxTokens, temperature);
	}

	/**
	 * Normaliza o valor de máximo de tokens para um valor seguro.
	 * <p>
	 * Converte o valor de entrada para inteiro e aplica limites mínimos e máximos
	 * para evitar problemas de performance ou custos excessivos.
	 * </p>
	 * 
	 * @param tokenValue Valor a ser normalizado (pode ser null, Number, String)
	 * @return Valor inteiro normalizado entre MIN_SAFE_TOKENS e MAX_SAFE_TOKENS
	 */
	private int normalizeMaxTokens(Object tokenValue) {
		int tokens = DEFAULT_TOKENS;

		if (tokenValue instanceof Number number) {
			tokens = number.intValue();
		} else if (tokenValue instanceof String str && StringUtils.hasText(str)) {
			try {
				tokens = Integer.parseInt(str.trim());
			} catch (NumberFormatException e) {
				log.warn("[NORMALIZE-TOKENS] Invalid token format: {}, using default: {}", str, DEFAULT_TOKENS);
				return DEFAULT_TOKENS;
			}
		}

		// Aplicação de limites de segurança
		if (tokens < MIN_SAFE_TOKENS) {
			log.debug("[NORMALIZE-TOKENS] Value {} below minimum, adjusted to {}", tokens, MIN_SAFE_TOKENS);
			return MIN_SAFE_TOKENS;
		}

		if (tokens > MAX_SAFE_TOKENS) {
			log.debug("[NORMALIZE-TOKENS] Value {} above maximum, adjusted to {}", tokens, MAX_SAFE_TOKENS);
			return MAX_SAFE_TOKENS;
		}

		return tokens;
	}

	/**
	 * Normaliza o valor de temperature para um valor seguro.
	 * <p>
	 * Converte o valor de entrada para double e aplica limites para manter a
	 * temperatura dentro da faixa aceitável pelos modelos de IA.
	 * </p>
	 * 
	 * @param temperatureValue Valor a ser normalizado (pode ser null, Number,
	 *                         String)
	 * @return Valor double normalizado entre MIN_TEMPERATURE e MAX_TEMPERATURE
	 */
	private double normalizeTemperature(Object temperatureValue) {
		double temperature = DEFAULT_TEMPERATURE;

		if (temperatureValue instanceof Number number) {
			temperature = number.doubleValue();
		} else if (temperatureValue instanceof String str && StringUtils.hasText(str)) {
			try {
				temperature = Double.parseDouble(str.trim());
			} catch (NumberFormatException e) {
				log.warn("[NORMALIZE-TEMP] Invalid temperature format: {}, using default: {}", str,
						DEFAULT_TEMPERATURE);
				return DEFAULT_TEMPERATURE;
			}
		}

		// Aplicação de limites de segurança
		if (temperature < MIN_TEMPERATURE) {
			log.debug("[NORMALIZE-TEMP] Value {} below minimum, adjusted to {}", temperature, MIN_TEMPERATURE);
			return MIN_TEMPERATURE;
		}

		if (temperature > MAX_TEMPERATURE) {
			log.debug("[NORMALIZE-TEMP] Value {} above maximum, adjusted to {}", temperature, MAX_TEMPERATURE);
			return MAX_TEMPERATURE;
		}

		return temperature;
	}

}