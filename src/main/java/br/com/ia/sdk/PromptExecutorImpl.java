package br.com.ia.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;
import br.com.ia.model.RequestProvider;
import br.com.ia.sdk.context.ContextShard;
import br.com.ia.sdk.context.ContextShardDTOs;
import br.com.ia.sdk.context.ContextShards;
import br.com.ia.sdk.transport.PromptRequestPayload;
import br.com.ia.services.PendingIaRequestStore;
import br.com.ia.utils.CacheKeys;
import br.com.ia.utils.IAUtils;
import br.com.shared.exception.IAException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptExecutorImpl implements PromptExecutor {

	private final StreamBridge bridge;
	private final RequestProvider provider;
	private final PendingIaRequestStore pending;
	private final ObjectMapper objectMapper;

	@Value("${ia.requests.topic:processIa-in-0}")
	private String topic;

	@Value("${erp.ia.reply-timeout-ms:30000}")
	private long timeoutMs;

	@Value("${erp.ia.max-payload-size:30000}")
	private int maxPayloadSize;

	@Value("${erp.ia.max-shard-text-length:2000}")
	private int maxShardTextLength;

	@Value("${erp.ia.max-prompt-length:5000}")
	private int maxPromptLength;

	/** true => variante "com ref" (fingerprint). false => header-only (padrão). */
	@Value("${erp.ia.stable-shard-ref-enabled:false}")
	private boolean stableShardRefEnabled;

	/**
	 * Ordem de remocao de shards por tipo (CSV). Ex.:
	 * "projeto_participantes,projeto_descricao,projeto_escopo"
	 */
	@Value("${erp.ia.shard-removal-order:}")
	private String shardRemovalOrderCsv;

	@Override
	public IaResponse executaPrompt(PromptRequest r) throws IAException {
		preValidacoes(r);

		// 1) Otimiza e valida o payload (via wrapper serializável)
		r = otimizarEValidar(r);

		// 2) Monta opções para o provider
		Map<String, Object> opts = criarOpcoesRequest(r);
		IaRequest iaReq = provider.getRequest(r.getPrompt(), r.getChatId(), r.getApiKey(), opts);

		// 3) Validação final do IaRequest serializado (tamanho/JSON)
		validarIaRequestFinal(iaReq);

		// 4) Envia e aguarda resposta
		var future = pending.create(r.getChatId());
		Message<IaRequest> msg = MessageBuilder.withPayload(iaReq).setHeader("chatId", r.getChatId())
				.setHeader(KafkaHeaders.KEY, r.getChatId()).build();

		bridge.send(topic, msg);

		return IAUtils.aguardarRespostaIA(r.getChatId(), future, pending, Duration.ofMillis(timeoutMs), true);
	}

	/** Cria o mapa de opções para o request */
	private Map<String, Object> criarOpcoesRequest(PromptRequest r) {
		Map<String, Object> opts = new HashMap<>();
		opts.put("model", r.getModel());

		if (!isBlank(r.getInstructions())) {
			opts.put("instructions", r.getInstructions());
		}
		if (r.getText() != null) {
			opts.put("text", r.getText());
		}
		if (r.getMaxOutputTokens() != null) {
			opts.put("max_output_tokens", r.getMaxOutputTokens());
		}

		// temperatura (0..100 -> 0..2)
		double temp = Temperature.fromPercent(r.getTemperaturePercent(), Temperature.DEFAULT);
		opts.put("temperature", temp);

		// segurança/telemetria
		opts.put("safety_identifier", r.getChatId());

		// Context shards processados (sem duplicar)
		processarContextShards(r, opts);

		// Cache key
		adicionarCacheKey(r, opts);

		// Metadata
		opts.put("metadata", Map.of("chatId", r.getChatId(), "moduleKey", obterModuleKey(r)));

		return opts;
	}

	/** Processa e adiciona context shards às opções (UMA entrada por shard) */
	private void processarContextShards(PromptRequest r, Map<String, Object> opts) {
		if (r.getContextShards() == null || r.getContextShards().isEmpty())
			return;

		var wire = new ArrayList<Map<String, Object>>();
		for (ContextShard s : r.getContextShards()) {
			Map<String, Object> item = new HashMap<>();
			item.put(ContextShards.TYPE, s.type());
			item.put(ContextShards.VERSION, s.version());
			item.put("stable", s.stable());
			if (s.payload() != null) {
				item.put("payload", s.payload());
			}
			wire.add(item);
		}
		opts.put("context_shards", wire);
	}

	/** Adiciona chave de cache às opções */
	private void adicionarCacheKey(PromptRequest r, Map<String, Object> opts) {
		String moduleKey = obterModuleKey(r);
		Integer rulesV = r.getVersaoRegrasModulo() == null ? 1 : r.getVersaoRegrasModulo();
		Integer schemaV = r.getVersaoSchema() == null ? 1 : r.getVersaoSchema();

		var shardList = new ArrayList<Map<String, Object>>();
		if (r.getContextShards() != null) {
			for (var s : r.getContextShards()) {
				shardList.add(Map.of(ContextShards.TYPE, s.type(), ContextShards.VERSION, s.version()));
			}
		}

		String cacheKey = CacheKeys.compose(r.getChatId(), moduleKey, rulesV, schemaV, shardList, r.getCacheFacet());
		opts.put("prompt_cache_key", cacheKey);
	}

	/** Obtém a chave do módulo */
	private String obterModuleKey(PromptRequest r) {
		return (r.getModuleKey() == null || r.getModuleKey().isBlank()) ? "generic" : r.getModuleKey();
	}

	/** Cria uma cópia do PromptRequest para modificação */
	private PromptRequest criarCopiaRequest(PromptRequest original) {
		PromptRequest copia = new PromptRequest();
		copia.setChatId(original.getChatId());
		copia.setPrompt(original.getPrompt());
		copia.setApiKey(original.getApiKey());
		copia.setModel(original.getModel());
		copia.setInstructions(original.getInstructions());
		copia.setText(original.getText());
		copia.setMaxOutputTokens(original.getMaxOutputTokens());
		copia.setTemperaturePercent(original.getTemperaturePercent());
		copia.setModuleKey(original.getModuleKey());
		copia.setVersaoRegrasModulo(original.getVersaoRegrasModulo());
		copia.setVersaoSchema(original.getVersaoSchema());
		copia.setCacheFacet(original.getCacheFacet());
		copia.setVersaoInstrucaoProjeto(original.getVersaoInstrucaoProjeto());

		// Copiar context shards (referência, não deep copy)
		if (original.getContextShards() != null) {
			copia.setContextShards(new ArrayList<>(original.getContextShards()));
		}
		return copia;
	}

	/** Otimiza o prompt truncando se necessário */
	private PromptRequest otimizarPrompt(PromptRequest r) {
		if (r.getPrompt() != null && r.getPrompt().length() > maxPromptLength) {
			String promptOriginal = r.getPrompt();
			String promptTruncado = truncarTextoInteligente(promptOriginal, maxPromptLength);
			r.setPrompt(promptTruncado);
			log.debug("Prompt truncado: {} → {} caracteres", promptOriginal.length(), promptTruncado.length());
		}
		return r;
	}

	/** Otimiza context shards removendo ou truncando conteúdo (domínio) */
	private PromptRequest otimizarContextShards(PromptRequest r) {
		if (r.getContextShards() == null || r.getContextShards().isEmpty())
			return r;

		List<ContextShard> shards = r.getContextShards();
		List<ContextShard> shardsOtimizados = new ArrayList<>(shards.size());

		for (ContextShard shard : shards) {
			ContextShard shardOtimizado = otimizarShard(shard);
			if (shardOtimizado != null)
				shardsOtimizados.add(shardOtimizado);
		}

		r.setContextShards(shardsOtimizados);
		log.debug("Context shards otimizados: {} → {} shards", shards.size(), shardsOtimizados.size());
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

	/** Reduz o número máximo de tokens de saída */
	private PromptRequest reduzirMaxOutputTokens(PromptRequest r) {
		if (r.getMaxOutputTokens() != null && r.getMaxOutputTokens() > 800) {
			Integer original = r.getMaxOutputTokens();
			r.setMaxOutputTokens(800);
			log.debug("Max output tokens reduzido: {} → {}", original, 800);
		}
		return r;
	}

	/** Validação final do IaRequest serializado */
	private void validarIaRequestFinal(IaRequest iaReq) throws IAException {
		try {
			String json = objectMapper.writeValueAsString(iaReq);
			int tamanho = json.getBytes(StandardCharsets.UTF_8).length;

			if (tamanho > maxPayloadSize) {
				throw new IAException(
						String.format("IaRequest final muito grande: %d bytes > %d bytes", tamanho, maxPayloadSize));
			}
			log.debug("IaRequest final validado: {} bytes", tamanho);
		} catch (JsonProcessingException e) {
			throw new IAException("Erro ao serializar IaRequest final: " + e.getMessage(), e);
		}
	}

	private void preValidacoes(PromptRequest r) throws IAException {
		if (r == null)
			throw new IAException("PromptRequest nulo");
		if (isBlank(r.getChatId()))
			throw new IAException("chatId obrigatório");
		if (isBlank(r.getPrompt()))
			throw new IAException("prompt obrigatório");
		if (isBlank(r.getApiKey()))
			throw new IAException("apiKey obrigatório");
		if (isBlank(r.getModel()))
			throw new IAException("model obrigatório");
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	// ===================== NOVO PIPELINE DE OTIMIZAÇÃO =====================

	/** Constrói o wrapper serializável para estimar tamanho com segurança */
	private PromptRequestPayload buildPayload(PromptRequest req) {
		// Converte o domínio para DTOs estáveis SOMENTE aqui (fronteira)
		final var shardDTOs = ContextShardDTOs.copyOf(req.getContextShards());
		return new PromptRequestPayload(req.getChatId(), req.getModel(), req.getPrompt(), shardDTOs,
				req.getMaxOutputTokens(), req.getTemperaturePercent());
	}

	/** Calcula o tamanho aproximado do payload (via wrapper serializável) */
	private int calcularTamanhoPayload(PromptRequest r) throws JsonProcessingException {
		var payload = buildPayload(r);
		byte[] json = objectMapper.writeValueAsBytes(payload);
		return json.length;
	}

	/** Valida e otimiza o payload (parando assim que ficar dentro do limite) */
	private PromptRequest otimizarEValidar(PromptRequest r) throws IAException {
		try {
			int atual = calcularTamanhoPayload(r);
			if (atual <= maxPayloadSize) {
				log.debug("Payload dentro do limite: {} bytes", atual);
				return r;
			}

			log.warn("Payload excede limite: {} bytes > {} bytes. Iniciando otimização...", atual, maxPayloadSize);
			PromptRequest x = criarCopiaRequest(r);

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

			// 5) reduzir tokens de saída
			x = reduzirMaxOutputTokens(x);
			if (calcularTamanhoPayload(x) <= maxPayloadSize)
				return x;

			// 6) poda por prioridade (se configurada)
			x = podarShardsPorPrioridade(x);
			if (calcularTamanhoPayload(x) <= maxPayloadSize)
				return x;

			int t = calcularTamanhoPayload(x);
			throw new IAException(
					"Payload muito grande mesmo após otimização: %d bytes > %d bytes".formatted(t, maxPayloadSize));

		} catch (JsonProcessingException e) {
			throw new IAException("Erro ao serializar payload para validação: " + e.getMessage(), e);
		}
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

	// ---------------- VARIANTE 1: header-only (sem payload) ----------------
	/** Para shards estáveis, mantém só cabeçalho (sem payload) */
	private PromptRequest stripStableShardPayloadsHeader(PromptRequest r) {
		if (r.getContextShards() == null || r.getContextShards().isEmpty())
			return r;

		var out = new ArrayList<ContextShard>(r.getContextShards().size());
		for (var s : r.getContextShards()) {
			if (Boolean.TRUE.equals(s.stable())) {
				out.add(ContextShards.ephemeral(s.type(), s.version(), true, null));
			} else {
				out.add(s);
			}
		}
		r.setContextShards(out);
		return r;
	}

	// ---------------- VARIANTE 2: com ref (fingerprint SHA-256) -----------
	/**
	 * Para shards estáveis, envia {"ref": "<sha256>"} em vez do payload completo
	 */
	private PromptRequest stripStableShardPayloadsWithRef(PromptRequest r) {
		if (r.getContextShards() == null || r.getContextShards().isEmpty())
			return r;

		var out = new ArrayList<ContextShard>(r.getContextShards().size());
		for (var s : r.getContextShards()) {
			if (Boolean.TRUE.equals(s.stable()) && s.payload() != null) {
				String ref = fingerprintPayload(s.payload());
				out.add(ContextShards.ephemeral(s.type(), s.version(), true, Map.of("ref", ref)));
			} else if (Boolean.TRUE.equals(s.stable())) {
				out.add(ContextShards.ephemeral(s.type(), s.version(), true, Map.of("ref", "no-ref")));
			} else {
				out.add(s);
			}
		}
		r.setContextShards(out);
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

	// ---------------- Poda por prioridade (opcional) ----------------------
	/**
	 * Remove shards por tipo, na ordem definida em erp.ia.shard-removal-order
	 * (CSV), até caber no limite
	 */
	private PromptRequest podarShardsPorPrioridade(PromptRequest r) throws JsonProcessingException {
		List<String> ordem = removalOrder();
		if (ordem.isEmpty() || r.getContextShards() == null || r.getContextShards().isEmpty())
			return r;

		for (String tipo : ordem) {
			if (calcularTamanhoPayload(r) <= maxPayloadSize)
				break;

			while (calcularTamanhoPayload(r) > maxPayloadSize) {
				int idx = findLastIndexByType(r.getContextShards(), tipo);
				if (idx < 0)
					break;

				var nova = new ArrayList<ContextShard>(r.getContextShards());
				nova.remove(idx);
				r.setContextShards(nova);
			}
		}
		return r;
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
