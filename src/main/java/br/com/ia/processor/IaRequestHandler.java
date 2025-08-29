package br.com.ia.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.model.IaRequest;
import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.ia.processor.SafeJsonExtractor.AcaoItem;
import br.com.ia.processor.SafeJsonExtractor.ProcessingResult;
import br.com.ia.sdk.response.AcaoIA;
import br.com.ia.sdk.response.RespostaIA;
import br.com.ia.services.client.responses.ResponsesClient;
import br.com.ia.utils.OpenAICustoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IaRequestHandler {

	private static final double DEFAULT_TEMP = 0.3;

	private final ResponsesClient responsesClient;
	private final ObjectMapper mapper;

	public RespostaIA handle(Map<String, Object> payload) {
		IaRequest req = mapper.convertValue(payload, IaRequest.class);
		String id = req.getRequestId().toString();

		try {
			Map<String, Object> opts = req.getOptions() != null ? req.getOptions() : Map.of();
			validaOpcoes(opts);
			Double temperature = normalizeTemperature(opts);
			Integer maxOutputTokens = obterMaxOutputTokens(opts);
			String instructions = (String) opts.getOrDefault("instructions", null);
			String model = obterModelo(opts);

			List<ResponsesRequest.ContentBlock> blocks = buildBlocksFromContextShards(opts);
			blocks.add(ResponsesRequest.ContentBlock.builder().type("input_text").text(req.getPrompt()).build());

			var input = List.of(ResponsesRequest.InputItem.builder().role("user").content(blocks).build());

			var responsesReq = new ResponsesRequest();
			responsesReq.setInput(input);
			responsesReq.setMaxOutputTokens(maxOutputTokens);
			responsesReq.setModel(model);
			responsesReq.setTemperature(temperature);
			responsesReq.setPromptCacheKey(req.getChatId());
			responsesReq.setSafetyIdentifier(id);
			responsesReq.setMetadata(Map.of("correlation_id", id, "idChat", req.getChatId()));
			responsesReq.setInstructions(instructions);
			responsesReq.setStore(true);

			log.info("[IaRequestHandler] Calling external AI API");
			ResponsesResponse res = responsesClient.createResponse(obterApiKey(opts), responsesReq);

			String resposta = extractText(res);

			int tokensPrompt = getInt(res.getUsage(), "input_tokens");
			int tokensResposta = getInt(res.getUsage(), "output_tokens");
			BigDecimal custo = OpenAICustoUtil.calcularCustoPorUsage(res.getModel(), tokensPrompt, tokensResposta);
			br.com.ia.model.enums.ModeloIA modelo = br.com.ia.model.enums.ModeloIA.from(res.getModel());
			String erro = null;
			String resumo = null;
			List<AcaoIA> acoes = null;

			Optional<ProcessingResult> result = SafeJsonExtractor.extractSafely(resposta);

			if (result.isPresent()) {
				ProcessingResult data = result.get();
				resumo = data.getResumo();
				erro = data.getErro();

				acoes = new ArrayList<>();
				for (AcaoItem acao : data.getAcoes()) {
					List<Object> dadosConvertidos = convertJsonNodeToList(acao.getDados());
					AcaoIA a = new AcaoIA(acao.getMetodo(), dadosConvertidos);
					acoes.add(a);
				}
			}

			return new RespostaIA(res.getId(), modelo, erro, tokensPrompt, tokensResposta, resumo, custo, resposta,
					acoes);
		} catch (Exception e) {
			log.error("[IaRequestHandler] Error processing {}: {}", id, e.getMessage(), e);

			return new RespostaIA(id, null, e.getMessage(), 0, 0, id, null, id, null);
		}
	}

	@SuppressWarnings("unchecked")
	private List<Object> convertJsonNodeToList(JsonNode dadosNode) {
		List<Object> dados = new ArrayList<>();

		if (dadosNode == null || dadosNode.isNull()) {
			return dados; // Lista vazia
		}

		try {
			// Se for um objeto, converter para Map
			if (dadosNode.isObject()) {
				Map<String, Object> map = mapper.convertValue(dadosNode, Map.class);
				dados.add(map);
			}
			// Se for um array, converter para List
			else if (dadosNode.isArray()) {
				List<Object> list = mapper.convertValue(dadosNode, List.class);
				dados.addAll(list);
			}
			// Se for um valor primitivo
			else {
				dados.add(mapper.convertValue(dadosNode, Object.class));
			}
		} catch (Exception e) {
			log.error("Erro ao converter dados: " + e.getMessage());
			// Retorna lista vazia em caso de erro
		}

		return dados;
	}

	// -----------------
	// Helpers internos
	// -----------------
	private static double normalizeTemperature(Map<String, Object> opts) {
		Object t = opts.get("temperature");
		if (t == null)
			return DEFAULT_TEMP;

		double v;
		if (t instanceof Number n)
			v = n.doubleValue();
		else {
			try {
				v = Double.parseDouble(String.valueOf(t).trim());
			} catch (Exception e) {
				return DEFAULT_TEMP;
			}
		}

		if (!Double.isFinite(v))
			return DEFAULT_TEMP;
		v = Math.max(0.0, Math.min(100.0, v));
		double scaled = (v / 100.0) * 2.0;
		return Math.round(scaled * 1000.0) / 1000.0;
	}

	private static int getInt(Map<String, Object> usage, String key) {
		if (usage == null || !usage.containsKey(key) || usage.get(key) == null)
			return 0;
		return Integer.parseInt(String.valueOf(usage.get(key)));
	}

	private static String extractText(ResponsesResponse res) {
		if (res.getOutput() != null && !res.getOutput().isEmpty() && res.getOutput().get(0).getContent() != null
				&& !res.getOutput().get(0).getContent().isEmpty()
				&& res.getOutput().get(0).getContent().get(0) != null) {
			var first = res.getOutput().get(0).getContent().get(0);
			return first.getText() != null ? first.getText() : "(no textual output)";
		}
		return "(no textual output)";
	}

	private List<ResponsesRequest.ContentBlock> buildBlocksFromContextShards(Map<String, Object> opts) {
		Object rawShards = opts.get("context_shards");
		if (!(rawShards instanceof List<?> l) || l.isEmpty()) {
			return new ArrayList<>();
		}

		List<Map<String, Object>> shardList = new ArrayList<>();
		for (Object o : l) {
			if (o instanceof Map<?, ?> m) {
				@SuppressWarnings("unchecked")
				Map<String, Object> mm = (Map<String, Object>) m;
				shardList.add(mm);
			}
		}

		shardList.sort(Comparator.<Map<String, Object>, Boolean>comparing(s -> !Boolean.TRUE.equals(s.get("stable")))
				.thenComparing(s -> String.valueOf(s.getOrDefault("type", "")))
				.thenComparingInt(s -> parseIntSafe(s.get("version"))));

		List<ResponsesRequest.ContentBlock> blocks = new ArrayList<>();
		for (Map<String, Object> s : shardList) {
			String type = String.valueOf(s.getOrDefault("type", ""));
			int version = parseIntSafe(s.get("version"));
			boolean stable = Boolean.TRUE.equals(s.get("stable"));
			Object payload = s.get("payload");

			String header = "### CTX:%s v%d%s".formatted(type, version, stable ? " (stable)" : "");
			String jsonPayload;
			try {
				jsonPayload = mapper.writeValueAsString(payload == null ? Map.of() : payload);
			} catch (Exception e) {
				jsonPayload = "{\"error\":\"failed to serialize shard %s\"}".formatted(type);
			}

			blocks.add(ResponsesRequest.ContentBlock.builder().type("input_text").text(header + "\n" + jsonPayload)
					.build());
		}
		return blocks;
	}

	private static int parseIntSafe(Object v) {
		if (v == null)
			return 0;
		try {
			return Integer.parseInt(String.valueOf(v));
		} catch (Exception e) {
			return 0;
		}
	}

	private String obterApiKey(Map<String, Object> opts) {
		String apiKey = (String) opts.getOrDefault("api_key", null);

		if (apiKey == null) {
			throw new IllegalArgumentException("api_key absent in options.");
		}

		return apiKey;
	}

	private Integer obterMaxOutputTokens(Map<String, Object> opts) {
		return opts.containsKey("max_output_tokens") ? Integer.valueOf(String.valueOf(opts.get("max_output_tokens")))
				: null;
	}

	private String obterModelo(Map<String, Object> opts) {
		var modelo = (String) opts.getOrDefault("model", null);
		if (modelo == null) {
			throw new IllegalArgumentException("model absent in options.");
		}
		return modelo;
	}

	private void validaOpcoes(Map<String, Object> opts) {
		if (opts == null) {
			throw new IllegalArgumentException("opções inválidass");
		}
	}
}
