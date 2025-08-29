package br.com.ia.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class SafeJsonExtractor {
	private static final ObjectMapper mapper = new ObjectMapper();

	@Data
	public static class ProcessingResult {
		private String id;
		private String erro;
		private String resumo;
		private List<AcaoItem> acoes;

		// Construtores, getters e setters
		public ProcessingResult() {
			this.acoes = new ArrayList<>();
		}
	}

	@Data
	public static class AcaoItem {
		private String metodo;
		private JsonNode dados;
	}

	/**
	 * Extração segura com validação completa - Refatorado para reduzir complexidade
	 * cognitiva
	 */
	public static Optional<ProcessingResult> extractSafely(String jsonString) {
		try {
			if (!isValidInput(jsonString)) {
				return Optional.empty();
			}

			JsonNode rootNode = mapper.readTree(jsonString);

			if (!hasRequiredId(rootNode)) {
				return Optional.empty();
			}

			ProcessingResult result = buildProcessingResult(rootNode);
			return Optional.of(result);

		} catch (JsonProcessingException e) {
			log.error("Erro ao processar JSON: " + e.getMessage());
			return Optional.empty();
		} catch (Exception e) {
			log.error("Erro inesperado: " + e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Valida se a entrada é válida
	 */
	private static boolean isValidInput(String jsonString) {
		return jsonString != null && !jsonString.trim().isEmpty();
	}

	/**
	 * Verifica se o nó raiz contém o ID obrigatório
	 */
	private static boolean hasRequiredId(JsonNode rootNode) {
		return rootNode.has("id") && !rootNode.get("id").isNull();
	}

	/**
	 * Constrói o objeto ProcessingResult a partir do nó JSON
	 */
	private static ProcessingResult buildProcessingResult(JsonNode rootNode) {
		ProcessingResult result = new ProcessingResult();

		result.setId(rootNode.get("id").asText());
		result.setErro(extractStringField(rootNode, "erro"));
		result.setResumo(extractStringField(rootNode, "resumo"));
		result.setAcoes(extractAcoesList(rootNode));

		return result;
	}

	/**
	 * Extrai um campo string de forma segura
	 */
	private static String extractStringField(JsonNode rootNode, String fieldName) {
		JsonNode fieldNode = rootNode.get(fieldName);
		return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
	}

	/**
	 * Extrai a lista de ações de forma segura
	 */
	private static List<AcaoItem> extractAcoesList(JsonNode rootNode) {
		JsonNode acoesNode = rootNode.get("acoes");
		if (acoesNode == null || !acoesNode.isArray()) {
			return new ArrayList<>();
		}

		List<AcaoItem> acoes = new ArrayList<>();
		for (JsonNode acaoNode : acoesNode) {
			acoes.add(createAcaoItem(acaoNode));
		}
		return acoes;
	}

	/**
	 * Cria um item de ação a partir de um nó JSON
	 */
	private static AcaoItem createAcaoItem(JsonNode acaoNode) {
		AcaoItem acao = new AcaoItem();

		JsonNode metodoNode = acaoNode.get("metodo");
		if (metodoNode != null && !metodoNode.isNull()) {
			acao.setMetodo(metodoNode.asText());
		}

		JsonNode dadosNode = acaoNode.get("dados");
		if (dadosNode != null) {
			acao.setDados(dadosNode);
		}

		return acao;
	}

	/**
	 * Método utilitário para validação rápida
	 */
	public static boolean isValidFormat(String jsonString) {
		return extractSafely(jsonString).isPresent();
	}

	/**
	 * Extração com fallbacks inteligentes
	 */
	public static ProcessingResult extractWithDefaults(String jsonString) {
		return extractSafely(jsonString).orElseGet(() -> {
			ProcessingResult fallback = new ProcessingResult();
			fallback.setId("UNKNOWN");
			fallback.setErro("Falha na extração do JSON");
			fallback.setResumo("Dados inválidos ou corrompidos");
			return fallback;
		});
	}
}