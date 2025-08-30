package br.com.ia.sdk;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.instructions.IAServerInstructions;
import br.com.ia.model.IaRequest;
import br.com.ia.sdk.context.ContextShard;
import br.com.ia.sdk.context.ContextShardDTOs;
import br.com.ia.sdk.context.ContextShards;
import br.com.ia.sdk.context.entity.LogIA;
import br.com.ia.sdk.context.repository.LogIARepository;
import br.com.ia.sdk.context.service.ShardService;
import br.com.ia.sdk.exception.IAExecutionException;
import br.com.ia.sdk.transport.PromptRequestPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptExecutorImpl implements PromptExecutor {

	private final ObjectMapper objectMapper;
	private final ShardService shardService;
	private final IAServerClient iaServerClient;
	private final LogIARepository repository;

	@Value("${erp.ia.max-payload-size:500000}")
	private int maxPayloadSize;

	@Value("${erp.ia.max-shard-text-length:2000}")
	private int maxShardTextLength;

	@Value("${erp.ia.max-prompt-length:5000}")
	private int maxPromptLength;

	/** true => variante "com ref" (fingerprint). false => header-only (padrão). */
	@Value("${erp.ia.stable-shard-ref-enabled:false}")
	private boolean stableShardRefEnabled;

	private String shardRemovalOrderCsv;

	@Override
	public boolean executaPrompt(PromptRequest request) throws IAExecutionException {

		preValidacoes(request);

		var chatId = request.getChatId();
		log.info("🚀 Iniciando execução de prompt para chatId: {}", chatId);

		upsertShards(request, chatId);

		request = otimizarEValidar(request);
		log.debug("✅ Payload otimizado e validado");

		boolean sent = iaServerClient.sendIaRequest(criarIaRequest(request));

		if (sent) {
			log.info("IA Request sent to IAServer - ChatId: {}", chatId);
		} else {
			log.error("Failed to send IA Request to IAServer - ChatId: {}", chatId);
		}

		return sent;
	}

	private IaRequest criarIaRequest(PromptRequest request) {

		// Build complete context shards
		List<ContextShard> shardsCompleta = montarContextShards(request);

		// Convert ContextShards to the format expected by IAServer
		List<Map<String, Object>> contextShardsData = new ArrayList<>();
		for (ContextShard shard : shardsCompleta) {
			Map<String, Object> shardData = Map.of(
					IaRequest.SHARD_TYPE, shard.type(), 
					IaRequest.SHARD_VERSION, shard.version(), 
					IaRequest.SHARD_STABLE, shard.stable(), 
					IaRequest.SHARD_PAYLOAD, shard.payload() != null ? shard.payload() : Map.of());
			contextShardsData.add(shardData);
		}

		// Create options for IaRequest
		Map<String, Object> options = Map.of(
				IaRequest.API_KEY, request.getApiKey(), 
				IaRequest.INSTRUCTIONS, request.getInstructions(), 
				IaRequest.TEMPERATURE, normalizeTemperature(request.getTemperatura()), 
				IaRequest.MAX_OUTPUT_TOKENS, request.getComplexity().getTokens(), 
				IaRequest.CONTEXT_SHARDS, contextShardsData, 
				IaRequest.TEXT, request.getSchema(), 
				IaRequest.MODULE_KEY, request.getModuleKey(), 
				IaRequest.VERSAO_SCHEMA, request.getSchemaVersion(), 
				IaRequest.VERSAO_REGRAS_MODULO, request.getInstructions(),
				IaRequest.MODELO, request.getModelo());

		var req = new IaRequest();
		req.setChatId(request.getChatId());
		req.setPrompt(request.getPrompt());
		req.setOptions(options);
		criaLogIA(req);

		return req;
	}

	private void criaLogIA(IaRequest request) {
		var logIA = new LogIA();
		logIA.setEnviado(false);
		logIA.setIdChat(request.getChatId());
		logIA.setPrompt(request.getPrompt());
		repository.save(logIA);
		request.setCorrelationId(Long.toString(logIA.getId()));
	}

	private List<ContextShard> montarContextShards(PromptRequest request) {
		var list = new ArrayList<ContextShard>();
		list.add(IAServerInstructions.getShard());
		list.add(request.getShardInstrucao());
		list.add(request.getShardObjetivo());
		list.add(request.getShardEscopo());
		list.add(request.getShardDescricao());
		list.add(request.getShardParticipantes());

		if (request.getShards() != null && !request.getShards().isEmpty()) {
			list.addAll(request.getShards());
		}

		return list;
	}

	/**
	 * Normalizes temperature from 0-100 to 0.0-2.0 range
	 */
	private double normalizeTemperature(double d) {
		if (d < 0)
			return 0.3;
		if (d > 100)
			return 2.0;
		return (d / 100.0) * 2.0;
	}

	private void preValidacoes(PromptRequest request) throws IAExecutionException {
		if (request == null)
			throw new IAExecutionException("PromptRequest nulo");
		if (isBlank(request.getChatId()))
			throw new IAExecutionException("chatId obrigatório");
		if (isBlank(request.getPrompt()))
			throw new IAExecutionException("prompt obrigatório");
		if (isBlank(request.getApiKey()))
			throw new IAExecutionException("apiKey obrigatório");
		log.debug("✅ Pré-validações concluídas");
	}

	private void upsertShards(PromptRequest request, String chatId) {
		var shardDescricao = request.getShardDescricao();
		shardService.upsert(chatId, shardDescricao);

		var shardEscopo = request.getShardEscopo();
		shardService.upsert(chatId, shardEscopo);

		var shardInstrucao = request.getShardInstrucao();
		shardService.upsert(chatId, shardInstrucao);

		var shardObjetivo = request.getShardObjetivo();
		shardService.upsert(chatId, shardObjetivo);

		var shardParticipantes = request.getShardParticipantes();
		shardService.upsert(chatId, shardParticipantes);

		for (ContextShard shard : request.getShards()) {
			shardService.upsert(chatId, shard);
		}

		log.debug("✅ Upserts concluídos");
	}

	/** Valida e otimiza o payload (parando assim que ficar dentro do limite) */
	private PromptRequest otimizarEValidar(PromptRequest r) throws IAExecutionException {
		try {
			int atual = calcularTamanhoPayload(r);
			if (atual <= maxPayloadSize) {
				log.debug("Payload dentro do limite: {} bytes", atual);
				return r;
			}

			log.warn("Payload excede limite: {} bytes > {} bytes. Iniciando otimização...", atual, maxPayloadSize);
			PromptRequest x = new PromptRequest(r);

			// 1) prompt
			x = otimizarPrompt(x);
			if (calcularTamanhoPayload(x) <= maxPayloadSize)
				return x;

			// 2) shards (truncagem de texto nos payloads)
			x = otimizarContextShards(x);
			if (calcularTamanhoPayload(x) <= maxPayloadSize)
				return x;

			// 3) shards estáveis: header-only ou com ref (fingerprint)
			x = stableShardRefEnabled ? stripStableShardPayloadsWithRef(x) : stripStableShardPayloadsHeader(x);
			if (calcularTamanhoPayload(x) <= maxPayloadSize)
				return x;

			// 4) instructions
			x = otimizarInstructions(x);
			if (calcularTamanhoPayload(x) <= maxPayloadSize)
				return x;

			// 5) poda por prioridade (se configurada)
			x = podarShardsPorPrioridade(x);
			if (calcularTamanhoPayload(x) <= maxPayloadSize)
				return x;

			int t = calcularTamanhoPayload(x);
			throw new IAExecutionException(
					"Payload muito grande mesmo após otimização: %d bytes > %d bytes".formatted(t, maxPayloadSize));

		} catch (JsonProcessingException e) {
			throw new IAExecutionException("Erro ao serializar payload para validação: " + e.getMessage(), e);
		}
	}

	/** Constrói o wrapper serializável para estimar tamanho com segurança */
	private PromptRequestPayload buildPayload(PromptRequest req) {
		// Converte o domínio para DTOs estáveis SOMENTE aqui (fronteira)
		final var shardDTOs = ContextShardDTOs.copyOf(req.getShards());
		return new PromptRequestPayload(
				"ID_000000",
				req.getChatId(), 
				req.getModelo().name(), 
				req.getPrompt(), 
				shardDTOs,
				16000, 
				req.getTemperatura());
	}

	/** Calcula o tamanho aproximado do payload (via wrapper serializável) */
	private int calcularTamanhoPayload(PromptRequest request) throws JsonProcessingException {
		var payload = buildPayload(request);
		byte[] json = objectMapper.writeValueAsBytes(payload);
		return json.length;
	}

	/** Otimiza o prompt truncando se necessário */
	private PromptRequest otimizarPrompt(PromptRequest request) {
		if (request.getPrompt() != null && request.getPrompt().length() > maxPromptLength) {
			String promptOriginal = request.getPrompt();
			String promptTruncado = truncarTextoInteligente(promptOriginal, maxPromptLength);
			request.setPrompt(promptTruncado);
			log.debug("Prompt truncado: {} → {} caracteres", promptOriginal.length(), promptTruncado.length());
		}
		return request;
	}

	/** Otimiza context shards removendo ou truncando conteúdo (domínio) */
	private PromptRequest otimizarContextShards(PromptRequest r) {
		if (r.getShards() == null || r.getShards().isEmpty())
			return r;

		List<ContextShard> shards = r.getShards();
		List<ContextShard> shardsOtimizados = new ArrayList<>(shards.size());

		for (ContextShard shard : shards) {
			ContextShard shardOtimizado = otimizarShard(shard);
			if (shardOtimizado != null)
				shardsOtimizados.add(shardOtimizado);
		}

		r.setShards(shardsOtimizados);
		log.debug("Context shards otimizados: {} → {} shards", shards.size(), shardsOtimizados.size());
		return r;
	}

	/** Otimiza um shard individual (domínio) */
	private ContextShard otimizarShard(ContextShard shard) {
		Map<String, Object> payload = shard.payload();
		if (payload == null)
			return shard;

		Map<String, Object> payloadOtimizado = new HashMap<>(payload);

		// Trunca o campo "texto" (se existir) respeitando maxShardTextLength
		Object textoObj = payloadOtimizado.get(ContextShards.TEXTO);
		if (textoObj instanceof String texto && texto.length() > maxShardTextLength) {
			String textoTruncado = truncarTextoInteligente(texto, maxShardTextLength);
			payloadOtimizado.put(ContextShards.TEXTO, textoTruncado);
			log.debug("Shard {} texto truncado: {} → {} caracteres", shard.type(), texto.length(),
					textoTruncado.length());
		}

		// Cria um novo shard efêmero com o payload otimizado
		return ContextShards.ephemeral(shard.type(), shard.version(), shard.stable(), payloadOtimizado);
	}

	/**
	 * Para shards estáveis, envia {"ref": "<sha256>"} em vez do payload completo
	 */
	private PromptRequest stripStableShardPayloadsWithRef(PromptRequest r) {
		if (r.getShards() == null || r.getShards().isEmpty())
			return r;

		var out = new ArrayList<ContextShard>(r.getShards().size());
		for (var s : r.getShards()) {
			if (Boolean.TRUE.equals(s.stable()) && s.payload() != null) {
				String ref = fingerprintPayload(s.payload());
				out.add(ContextShards.ephemeral(s.type(), s.version(), true, Map.of("ref", ref)));
			} else if (Boolean.TRUE.equals(s.stable())) {
				out.add(ContextShards.ephemeral(s.type(), s.version(), true, Map.of("ref", "no-ref")));
			} else {
				out.add(s);
			}
		}
		r.setShards(out);
		return r;
	}

	/** Gera um fingerprint estável (sha-256) do payload serializado em JSON */
	private String fingerprintPayload(Map<String, Object> payload) {
		try {
			byte[] canonical = objectMapper.writeValueAsBytes(payload);
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(canonical);
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			log.warn("Falha ao gerar fingerprint do payload estável: {}", e.toString());
			return "no-ref";
		}
	}

	/** Para shards estáveis, mantém só cabeçalho (sem payload) */
	private PromptRequest stripStableShardPayloadsHeader(PromptRequest r) {
		if (r.getShards() == null || r.getShards().isEmpty())
			return r;

		var out = new ArrayList<ContextShard>(r.getShards().size());
		for (var s : r.getShards()) {
			if (Boolean.TRUE.equals(s.stable())) {
				out.add(ContextShards.ephemeral(s.type(), s.version(), true, null));
			} else {
				out.add(s);
			}
		}
		r.setShards(out);
		return r;
	}

	/** Otimiza as instruções se muito longas */
	private PromptRequest otimizarInstructions(PromptRequest r) {
		if (r.getInstructions() != null && r.getInstructions().length() > 3000) {
			String original = r.getInstructions();
			String truncadas = truncarTextoInteligente(original, 3000);
			r.setInstructions(truncadas);
			log.debug("Instructions truncadas: {} → {} caracteres", original.length(), truncadas.length());
		}
		return r;
	}

	/**
	 * Remove shards por tipo, na ordem definida em erp.ia.shard-removal-order
	 * (CSV), até caber no limite
	 */
	private PromptRequest podarShardsPorPrioridade(PromptRequest r) throws JsonProcessingException {
		List<String> ordem = removalOrder();
		if (ordem.isEmpty() || r.getShards() == null || r.getShards().isEmpty())
			return r;

		for (String tipo : ordem) {
			if (calcularTamanhoPayload(r) <= maxPayloadSize)
				break;

			while (calcularTamanhoPayload(r) > maxPayloadSize) {
				int idx = findLastIndexByType(r.getShards(), tipo);
				if (idx < 0)
					break;

				var nova = new ArrayList<ContextShard>(r.getShards());
				nova.remove(idx);
				r.setShards(nova);
			}
		}
		return r;
	}

	/** Trunca texto de forma inteligente mantendo contexto */
	private String truncarTextoInteligente(String texto, int tamanhoMaximo) {
		if (texto.length() <= tamanhoMaximo)
			return texto;

		int tamanhoInicio = tamanhoMaximo / 2;
		int tamanhoFim = tamanhoMaximo / 4;

		if (tamanhoInicio + tamanhoFim + 50 >= tamanhoMaximo) {
			return texto.substring(0, Math.max(0, tamanhoMaximo - 3)) + "...";
		}

		String inicio = texto.substring(0, tamanhoInicio);
		String fim = texto.substring(texto.length() - tamanhoFim);

		return inicio + "\n\n[... texto truncado para otimizar payload ...]\n\n" + fim;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private int findLastIndexByType(List<ContextShard> lst, String type) {
		for (int i = lst.size() - 1; i >= 0; i--) {
			if (type.equals(lst.get(i).type()))
				return i;
		}
		return -1;
	}

	private List<String> removalOrder() {
		if (isBlank(shardRemovalOrderCsv))
			return List.of();
		String[] arr = shardRemovalOrderCsv.split(",");
		List<String> out = new ArrayList<>(arr.length);
		for (String s : arr) {
			String t = s.trim();
			if (!t.isEmpty())
				out.add(t);
		}
		return out;
	}

}