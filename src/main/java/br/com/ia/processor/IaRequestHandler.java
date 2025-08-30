package br.com.ia.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.model.IaRequest;
import br.com.ia.model.enums.ModeloIA;
import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.ia.processor.SafeJsonExtractor.AcaoItem;
import br.com.ia.processor.SafeJsonExtractor.ProcessingResult;
import br.com.ia.sdk.response.AcaoIA;
import br.com.ia.sdk.response.RespostaIA;
import br.com.ia.services.client.responses.ResponsesClient;
import br.com.ia.utils.ObjectToBytesConverter;
import br.com.ia.utils.OpenAICustoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Processador principal para requisições de IA do sistema IAServer.
 * <p>
 * Esta classe é responsável por processar requisições de IA recebidas através
 * de mensageria, executar chamadas para APIs externas (OpenAI/Anthropic) e
 * processar as respostas de forma estruturada. Centraliza toda a lógica de
 * integração com provedores de IA e normalização de respostas.
 * </p>
 * 
 * <h3>Fluxo de processamento:</h3>
 * <ol>
 * <li><strong>Validação:</strong> Verifica integridade da requisição</li>
 * <li><strong>Preparação:</strong> Extrai opções e constrói blocos de
 * contexto</li>
 * <li><strong>Chamada Externa:</strong> Executa requisição para API de IA</li>
 * <li><strong>Processamento:</strong> Extrai e normaliza resposta
 * estruturada</li>
 * <li><strong>Retorno:</strong> Constrói objeto RespostaIA padronizado</li>
 * </ol>
 * 
 * <h3>Funcionalidades principais:</h3>
 * <ul>
 * <li>Construção automática de contexto a partir de shards</li>
 * <li>Extração segura de JSON estruturado das respostas</li>
 * <li>Cálculo automático de custos por modelo</li>
 * <li>Tratamento robusto de erros com fallbacks</li>
 * <li>Logging detalhado para auditoria e debug</li>
 * </ul>
 * 
 * @author Sistema IAServer
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IaRequestHandler {

	private static final String TYPE = "type";
	private static final String STABLE = "stable";
	private static final String UNKNOWN = "unknown";
	private static final String VERSION = "version";

	/**
	 * Cliente para comunicação com APIs externas de IA. Abstrai diferentes
	 * provedores (OpenAI, Anthropic, etc.).
	 */
	private final ResponsesClient responsesClient;

	/**
	 * ObjectMapper para serialização/deserialização JSON. Configurado pelo Spring
	 * com configurações do projeto.
	 */
	private final ObjectMapper mapper;

	private static final String LOG_PREFIX = "[IaRequestHandler]";
	private static final String NO_TEXTUAL_OUTPUT = "(no textual output)";
	private static final String CTX_HEADER_FORMAT = "### CTX:%s v%d%s";
	private static final String STABLE_SUFFIX = " (stable)";
	private static final String ERROR_PAYLOAD_FORMAT = "{\"error\":\"failed to serialize shard %s\"}";

	/**
	 * Processa uma requisição de IA de forma completa e robusta.
	 * <p>
	 * Este método executa o fluxo completo de processamento de uma requisição IA,
	 * incluindo validação, chamada externa, processamento de resposta e tratamento
	 * de erros. Sempre retorna um objeto RespostaIA, mesmo em caso de falha.
	 * </p>
	 * 
	 * <h4>Validações executadas:</h4>
	 * <ul>
	 * <li>CorrelationId obrigatório e válido</li>
	 * <li>Opções de configuração presentes</li>
	 * <li>API Key e modelo especificados</li>
	 * </ul>
	 * 
	 * @param value Map contendo dados da requisição IA
	 * @return RespostaIA processada com dados estruturados ou erro
	 * @throws IllegalArgumentException se correlationId for inválido
	 * 
	 * @implNote Este método nunca lança exceções além de IllegalArgumentException
	 *           para correlationId inválido. Outros erros são encapsulados na
	 *           resposta.
	 */
	public RespostaIA handle(Object value) {

		IaRequest request;
		String correlationId;
		try {
			request = ((IaRequest) ObjectToBytesConverter.bytesToObject((byte[]) value));
			correlationId = request.getCorrelationId();

			if (!StringUtils.hasLength(correlationId)) {
				log.error("{} CorrelationId inválido ou ausente", LOG_PREFIX);
				throw new IllegalArgumentException("CorrelationId inválido ou ausente");
			}
			log.info("{} Iniciando processamento - CorrelationId: {}", LOG_PREFIX, correlationId);

			// Fase 1: Extração e validação de opções
			RequestOptions options = extractAndValidateOptions(request);
			log.debug("{} Opções extraídas - Modelo: {}, MaxTokens: {}", LOG_PREFIX, options.model(),
					options.maxOutputTokens());

			// Fase 2: Construção da requisição para API externa
			ResponsesRequest apiRequest = buildApiRequest(request, options);
			log.debug("{} Requisição construída com {} blocos de contexto", LOG_PREFIX,
					apiRequest.getInput().get(0).getContent().size() - 1);

			// Fase 3: Chamada para API externa
			log.info("{} Executando chamada para API externa", LOG_PREFIX);

			var apiResponse = responsesClient.createResponse(options.apiKey(), apiRequest);

			log.info("{} Resposta recebida - Modelo: {}, InputTokens: {}, OutputTokens: {}", LOG_PREFIX,
					apiResponse.getModel(), getTokenCount(apiResponse.getUsage(), "input_tokens"),
					getTokenCount(apiResponse.getUsage(), "output_tokens"));

			// Fase 4: Processamento da resposta
			return processApiResponse(apiResponse, correlationId);

		} catch (Exception e) {
			return createErrorResponse("XXX", e.getMessage());
		}
	}

	/**
	 * Record para encapsular opções extraídas da requisição. Melhora legibilidade e
	 * type safety.
	 */
	private record RequestOptions(String apiKey, String model, Double temperature, Integer maxOutputTokens,
			String instructions, Map<String, Object> rawOptions) {
	}

	/**
	 * Extrai e valida todas as opções necessárias da requisição.
	 * <p>
	 * Centraliza a lógica de extração e validação de parâmetros, aplicando valores
	 * padrão quando apropriado e validando campos obrigatórios.
	 * </p>
	 * 
	 * @param request Requisição IA original
	 * @return RequestOptions com dados validados
	 * @throws IllegalArgumentException se opções obrigatórias estiverem ausentes
	 */
	private RequestOptions extractAndValidateOptions(IaRequest request) {
		Map<String, Object> opts = Optional.ofNullable(request.getOptions()).orElse(Map.of());

		if (opts.isEmpty()) {
			throw new IllegalArgumentException("Opções de configuração são obrigatórias");
		}

		// Extração de campos obrigatórios
		String apiKey = extractApiKey(opts);
		String modelo = extractModel(opts).key();

		// Extração de campos opcionais com defaults
		Double temperature = extractTemperature(opts);
		Integer maxOutputTokens = extractMaxOutputTokens(opts);
		String instructions = extractInstructions(opts);

		return new RequestOptions(apiKey, modelo, temperature, maxOutputTokens, instructions, opts);
	}

	/**
	 * Extrai e valida API Key das opções.
	 * 
	 * @param options Mapa de opções da requisição
	 * @return API Key válida
	 * @throws IllegalArgumentException se API Key estiver ausente
	 */
	private String extractApiKey(Map<String, Object> options) {
		String apiKey = (String) options.get(IaRequest.API_KEY);
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException("API Key é obrigatória nas opções");
		}
		return apiKey;
	}

	/**
	 * Extrai e valida modelo das opções.
	 * 
	 * @param options Mapa de opções da requisição
	 * @return Nome do modelo válido
	 * @throws IllegalArgumentException se modelo estiver ausente
	 */
	private ModeloIA extractModel(Map<String, Object> options) {
		return (ModeloIA) options.get(IaRequest.MODELO);
	}

	/**
	 * Extrai temperatura das opções com valor padrão.
	 * 
	 * @param options Mapa de opções da requisição
	 * @return Valor de temperatura (default: 0.7)
	 */
	private Double extractTemperature(Map<String, Object> options) {
		Object tempObj = options.get(IaRequest.TEMPERATURE);
		if (tempObj instanceof Number number) {
			return number.doubleValue();
		}
		return 0.7; // Valor padrão
	}

	/**
	 * Extrai máximo de tokens de saída das opções.
	 * 
	 * @param options Mapa de opções da requisição
	 * @return Número máximo de tokens ou null para usar padrão da API
	 */
	private Integer extractMaxOutputTokens(Map<String, Object> options) {
		Object tokensObj = options.get(IaRequest.MAX_OUTPUT_TOKENS);
		if (tokensObj != null) {
			try {
				return Integer.valueOf(String.valueOf(tokensObj));
			} catch (NumberFormatException e) {
				log.warn("{} Valor inválido para maxOutputTokens: {}", LOG_PREFIX, tokensObj);
			}
		}
		return null;
	}

	/**
	 * Extrai instruções específicas das opções.
	 * 
	 * @param options Mapa de opções da requisição
	 * @return String com instruções ou null
	 */
	private String extractInstructions(Map<String, Object> options) {
		return (String) options.getOrDefault(IaRequest.INSTRUCTIONS, null);
	}

	/**
	 * Constrói requisição para API externa a partir dos dados processados.
	 * <p>
	 * Monta a estrutura completa da requisição incluindo contexto, prompt do
	 * usuário, configurações de modelo e metadados para rastreamento.
	 * </p>
	 * 
	 * @param request Requisição IA original
	 * @param options Opções extraídas e validadas
	 * @return ResponsesRequest pronta para envio à API
	 */
	private ResponsesRequest buildApiRequest(IaRequest request, RequestOptions options) {
		// Construção dos blocos de conteúdo
		List<ResponsesRequest.ContentBlock> contentBlocks = buildContextBlocks(options.rawOptions());

		// Adição do prompt principal do usuário
		contentBlocks.add(ResponsesRequest.ContentBlock.builder().type("input_text").text(request.getPrompt()).build());

		// Construção do item de entrada
		List<ResponsesRequest.InputItem> input = List
				.of(ResponsesRequest.InputItem.builder().role("user").content(contentBlocks).build());

		// Montagem da requisição completa
		ResponsesRequest apiRequest = new ResponsesRequest();
		apiRequest.setInput(input);
		apiRequest.setMaxOutputTokens(options.maxOutputTokens());
		apiRequest.setModel(options.model());
		apiRequest.setTemperature(options.temperature());
		apiRequest.setPromptCacheKey(request.getChatId());
		apiRequest.setSafetyIdentifier(request.getChatId());
		apiRequest.setInstructions(options.instructions());
		apiRequest.setStore(true);

		// Metadados para rastreamento
		apiRequest.setMetadata(Map.of("correlation_id", request.getCorrelationId(), "chat_id", request.getChatId()));

		return apiRequest;
	}

	/**
	 * Constrói blocos de contexto a partir dos shards fornecidos.
	 * <p>
	 * Processa a lista de context shards, ordena por estabilidade e tipo, e
	 * converte cada shard em um bloco de conteúdo formatado para inclusão na
	 * requisição da API.
	 * </p>
	 * 
	 * <h4>Ordenação dos shards:</h4>
	 * <ol>
	 * <li>Shards estáveis primeiro (stable=true)</li>
	 * <li>Por tipo alfabético</li>
	 * <li>Por versão crescente</li>
	 * </ol>
	 * 
	 * @param options Mapa de opções contendo context_shards
	 * @return Lista de ContentBlocks prontos para uso
	 */
	private List<ResponsesRequest.ContentBlock> buildContextBlocks(Map<String, Object> options) {
		Object rawShards = options.get(IaRequest.CONTEXT_SHARDS);

		if (!(rawShards instanceof List<?> shardsList) || shardsList.isEmpty()) {
			log.debug("{} Nenhum context shard fornecido", LOG_PREFIX);
			return new ArrayList<>();
		}

		// Conversão segura para lista de Maps
		List<Map<String, Object>> validShards = extractValidShards(shardsList);

		if (validShards.isEmpty()) {
			log.warn("{} Nenhum shard válido encontrado", LOG_PREFIX);
			return new ArrayList<>();
		}

		// Ordenação por estabilidade, tipo e versão
		validShards.sort(createShardComparator());

		// Conversão para blocos de conteúdo
		return convertShardsToContentBlocks(validShards);
	}

	/**
	 * Extrai apenas shards válidos da lista raw.
	 * 
	 * @param rawShards Lista raw de objetos
	 * @return Lista de Maps representando shards válidos
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> extractValidShards(List<?> rawShards) {
		List<Map<String, Object>> validShards = new ArrayList<>();

		for (Object shard : rawShards) {
			if (shard instanceof Map<?, ?> shardMap) {
				try {
					Map<String, Object> typedShard = (Map<String, Object>) shardMap;
					validShards.add(typedShard);
				} catch (ClassCastException e) {
					log.warn("{} Shard com tipo inválido ignorado: {}", LOG_PREFIX, shard.getClass());
				}
			}
		}

		return validShards;
	}

	/**
	 * Cria comparator para ordenação de shards. Prioriza shards estáveis, depois
	 * ordena por tipo e versão.
	 * 
	 * @return Comparator configurado para shards
	 */
	private Comparator<Map<String, Object>> createShardComparator() {
		return Comparator.<Map<String, Object>, Boolean>comparing(s -> !Boolean.TRUE.equals(s.get(STABLE)))
				.thenComparing(s -> String.valueOf(s.getOrDefault(TYPE, "")))
				.thenComparingInt(s -> parseIntSafely(s.get(VERSION)));
	}

	/**
	 * Converte lista de shards em blocos de conteúdo formatados.
	 * 
	 * @param shards Lista de shards validados
	 * @return Lista de ContentBlocks formatados
	 */
	private List<ResponsesRequest.ContentBlock> convertShardsToContentBlocks(List<Map<String, Object>> shards) {
		List<ResponsesRequest.ContentBlock> blocks = new ArrayList<>();

		for (Map<String, Object> shard : shards) {
			String type = String.valueOf(shard.getOrDefault(TYPE, UNKNOWN));
			int version = parseIntSafely(shard.get(VERSION));
			boolean stable = Boolean.TRUE.equals(shard.get(STABLE));
			Object payload = shard.get("payload");

			// Formatação do cabeçalho do contexto
			String header = CTX_HEADER_FORMAT.formatted(type, version, stable ? STABLE_SUFFIX : "");

			// Serialização segura do payload
			String serializedPayload = serializePayloadSafely(payload, type);

			// Criação do bloco de conteúdo
			blocks.add(ResponsesRequest.ContentBlock.builder().type("input_text")
					.text(header + "\n" + serializedPayload).build());
		}

		return blocks;
	}

	/**
	 * Serializa payload do shard de forma segura.
	 * 
	 * @param payload   Objeto payload do shard
	 * @param shardType Tipo do shard para logging de erro
	 * @return JSON serializado ou mensagem de erro
	 */
	private String serializePayloadSafely(Object payload, String shardType) {
		try {
			return mapper.writeValueAsString(Objects.requireNonNullElse(payload, Map.of()));
		} catch (Exception e) {
			log.error("{} Falha ao serializar shard do tipo {}: {}", LOG_PREFIX, shardType, e.getMessage());
			return ERROR_PAYLOAD_FORMAT.formatted(shardType);
		}
	}

	/**
	 * Processa resposta da API externa e constrói RespostaIA padronizada.
	 * <p>
	 * Extrai informações da resposta, calcula custos, processa JSON estruturado e
	 * monta objeto final com todas as informações necessárias para o sistema.
	 * </p>
	 * 
	 * @param apiResponse   Resposta da API externa
	 * @param correlationId ID de correlação para rastreamento
	 * @return RespostaIA processada e estruturada
	 */
	private RespostaIA processApiResponse(ResponsesResponse apiResponse, String correlationId) {
		// Extração de dados básicos
		String responseText = extractResponseText(apiResponse);
		int inputTokens = getTokenCount(apiResponse.getUsage(), "input_tokens");
		int outputTokens = getTokenCount(apiResponse.getUsage(), "output_tokens");
		ModeloIA modelo = ModeloIA.from(apiResponse.getModel());

		// Cálculo de custo
		BigDecimal custo = OpenAICustoUtil.calcularCustoPorUsage(apiResponse.getModel(), inputTokens, outputTokens);

		// Processamento de JSON estruturado
		StructuredData structuredData = extractStructuredData(responseText);

		log.info("{} Processamento concluído - CorrelationId: {}, Tokens: {}/{}, Custo: ${}", LOG_PREFIX, correlationId,
				inputTokens, outputTokens, custo);

		return new RespostaIA(apiResponse.getCorrelation_id(), modelo, structuredData.erro(), inputTokens, outputTokens,
				structuredData.resumo(), custo, responseText, structuredData.acoes());
	}

	/**
	 * Record para encapsular dados estruturados extraídos.
	 */
	private record StructuredData(String erro, String resumo, List<AcaoIA> acoes) {
	}

	/**
	 * Extrai dados estruturados do texto de resposta JSON.
	 * <p>
	 * Utiliza SafeJsonExtractor para processar de forma segura o JSON retornado
	 * pela IA, convertendo para objetos tipados do sistema.
	 * </p>
	 * 
	 * @param responseText Texto da resposta da API
	 * @return StructuredData com dados extraídos ou valores padrão
	 */
	private StructuredData extractStructuredData(String responseText) {
		Optional<ProcessingResult> extractionResult = SafeJsonExtractor.extractSafely(responseText);

		if (extractionResult.isEmpty()) {
			log.warn("{} Falha na extração de dados estruturados", LOG_PREFIX);
			return new StructuredData(null, null, null);
		}

		ProcessingResult result = extractionResult.get();
		List<AcaoIA> acoes = convertAcoesToTypedList(result.getAcoes());

		log.debug("{} Dados estruturados extraídos - Ações: {}, Resumo: {}, Erro: {}", LOG_PREFIX, acoes.size(),
				result.getResumo() != null, result.getErro() != null);

		return new StructuredData(result.getErro(), result.getResumo(), acoes);
	}

	/**
	 * Converte lista de AcaoItem para lista de AcaoIA tipada.
	 * <p>
	 * Processa cada ação extraída, convertendo dados JSON para tipos apropriados e
	 * criando objetos AcaoIA padronizados.
	 * </p>
	 * 
	 * @param rawAcoes Lista de AcaoItem do extrator
	 * @return Lista de AcaoIA convertida
	 */
	private List<AcaoIA> convertAcoesToTypedList(List<AcaoItem> rawAcoes) {
		if (rawAcoes == null || rawAcoes.isEmpty()) {
			return new ArrayList<>();
		}

		List<AcaoIA> acoes = new ArrayList<>();

		for (AcaoItem acaoItem : rawAcoes) {
			try {
				List<Object> dadosConvertidos = convertJsonNodeToObjectList(acaoItem.getDados());
				AcaoIA acao = new AcaoIA(acaoItem.getMetodo(), dadosConvertidos);
				acoes.add(acao);
			} catch (Exception e) {
				log.error("{} Erro ao converter ação {}: {}", LOG_PREFIX, acaoItem.getMetodo(), e.getMessage());
				// Continua processamento de outras ações
			}
		}

		return acoes;
	}

	/**
	 * Converte JsonNode para lista de objetos tipados.
	 * <p>
	 * Manuseia diferentes tipos de estruturas JSON (objeto, array, primitivo) e
	 * converte para lista de objetos Java apropriados.
	 * </p>
	 * 
	 * @param jsonNode Nó JSON a ser convertido
	 * @return Lista de objetos convertidos
	 */
	@SuppressWarnings("unchecked")
	private List<Object> convertJsonNodeToObjectList(JsonNode jsonNode) {
		List<Object> resultList = new ArrayList<>();

		if (jsonNode == null || jsonNode.isNull()) {
			return resultList; // Lista vazia para valores nulos
		}

		try {
			if (jsonNode.isObject()) {
				// Objeto único -> adiciona Map à lista
				Map<String, Object> objectMap = mapper.convertValue(jsonNode, Map.class);
				resultList.add(objectMap);
			} else if (jsonNode.isArray()) {
				// Array -> adiciona todos elementos à lista
				List<Object> arrayList = mapper.convertValue(jsonNode, List.class);
				resultList.addAll(arrayList);
			} else {
				// Valor primitivo -> adiciona diretamente
				Object primitiveValue = mapper.convertValue(jsonNode, Object.class);
				resultList.add(primitiveValue);
			}
		} catch (Exception e) {
			log.error("{} Erro na conversão de JsonNode: {}", LOG_PREFIX, e.getMessage());
			// Retorna lista vazia em caso de erro
		}

		return resultList;
	}

	/**
	 * Extrai texto da resposta da API externa de forma segura.
	 * <p>
	 * Navega pela estrutura aninhada da resposta e extrai o conteúdo textual, com
	 * fallback para mensagem padrão em caso de estrutura inesperada.
	 * </p>
	 * 
	 * @param response Resposta da API externa
	 * @return Texto extraído ou mensagem padrão
	 */
	private static String extractResponseText(ResponsesResponse response) {
		try {
			if (response.getOutput() != null && !response.getOutput().isEmpty() && response.getOutput().get(0) != null
					&& response.getOutput().get(0).getContent() != null
					&& !response.getOutput().get(0).getContent().isEmpty()
					&& response.getOutput().get(0).getContent().get(0) != null) {

				var contentBlock = response.getOutput().get(0).getContent().get(0);
				String text = contentBlock.getText();

				return StringUtils.hasText(text) ? text : NO_TEXTUAL_OUTPUT;
			}
		} catch (Exception e) {
			log.warn("{} Erro ao extrair texto da resposta: {}", LOG_PREFIX, e.getMessage());
		}

		return NO_TEXTUAL_OUTPUT;
	}

	/**
	 * Obtém contagem de tokens de forma segura.
	 * <p>
	 * Extrai valores de token do mapa de usage com tratamento robusto de valores
	 * nulos ou inválidos.
	 * </p>
	 * 
	 * @param usage     Mapa de usage da resposta
	 * @param tokenType Tipo de token ("input_tokens", "output_tokens")
	 * @return Número de tokens ou 0 se inválido
	 */
	private static int getTokenCount(Map<String, Object> usage, String tokenType) {
		if (usage == null || !usage.containsKey(tokenType)) {
			return 0;
		}

		Object tokenValue = usage.get(tokenType);
		if (tokenValue == null) {
			return 0;
		}

		try {
			return Integer.parseInt(String.valueOf(tokenValue));
		} catch (NumberFormatException e) {
			log.warn("{} Valor inválido para {}: {}", LOG_PREFIX, tokenType, tokenValue);
			return 0;
		}
	}

	/**
	 * Converte valor para inteiro de forma segura. Utilizado para parsing de
	 * versões de shards.
	 * 
	 * @param value Valor a ser convertido
	 * @return Valor inteiro ou 0 se inválido
	 */
	private static int parseIntSafely(Object value) {
		if (value == null) {
			return 0;
		}

		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Cria resposta de erro padronizada. Utilizada quando o processamento falha
	 * completamente.
	 * 
	 * @param correlationId ID de correlação para rastreamento
	 * @param errorMessage  Mensagem de erro descritiva
	 * @return RespostaIA com informações de erro
	 */
	private static RespostaIA createErrorResponse(String correlationId, String errorMessage) {
		return new RespostaIA(correlationId, null, // modelo
				errorMessage, // erro
				0, // tokens prompt
				0, // tokens resposta
				null, // resumo
				null, // custo
				null, // resposta raw
				null // ações
		);
	}

	/**
	 * Verifica se o handler está configurado corretamente. Método de diagnóstico
	 * para validação de dependências.
	 * 
	 * @return true se todos os componentes necessários estão disponíveis
	 */
	public boolean isHealthy() {
		boolean healthy = responsesClient != null && mapper != null;
		if (!healthy) {
			log.error("{} Health check failed - missing dependencies", LOG_PREFIX);
		}
		return healthy;
	}

	/**
	 * Retorna informações de diagnóstico sobre o handler. Útil para monitoramento e
	 * debug.
	 * 
	 * @return Map com informações de configuração e estado
	 */
	public Map<String, Object> getDiagnosticInfo() {
		return Map.of("responsesClientConfigured", responsesClient != null, "mapperConfigured", mapper != null,
				"healthy", isHealthy(), "component", "IaRequestHandler", VERSION, "1.0", "features",
				List.of("structured_json_extraction", "context_shards_processing", "automatic_cost_calculation",
						"robust_error_handling"));
	}
}