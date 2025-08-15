package br.com.ia.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.shared.annotations.Comment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload com o resultado do processamento de IA via Kafka (Responses API).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IaResponse {

	@Comment("ID único do chat (UUID). Mesmo valor enviado na requisição.")
	private String chatId;

	@Comment("Conteúdo retornado pela IA (JSON conforme schema instruído).")
	private String resposta;

	private BigDecimal custo;
	private String modelo;

	// usage.input_tokens e usage.output_tokens
	private int tokensPrompt;
	private int tokensResposta;

	// Campos para controle de sucesso/erro
	private boolean success = true;
	private String errorMessage;
	private String errorCode;

	/**
	 * Verifica se a resposta foi bem-sucedida
	 * 
	 * @return true se sucesso, false se houve erro
	 */
	public boolean isSuccess() {
		return success && resposta != null && !resposta.trim().isEmpty();
	}

	/**
	 * Marca a resposta como erro
	 * 
	 * @param errorMessage Mensagem de erro
	 * @param errorCode    Codigo do erro (opcional)
	 */
	public void markAsError(String errorMessage, String errorCode) {
		this.success = false;
		this.errorMessage = errorMessage;
		this.errorCode = errorCode;
	}

	/**
	 * Marca a resposta como erro (sem codigo)
	 * 
	 * @param errorMessage Mensagem de erro
	 */
	public void markAsError(String errorMessage) {
		markAsError(errorMessage, null);
	}

	/**
	 * Verifica se houve erro
	 * 
	 * @return true se houve erro, false caso contrario
	 */
	public boolean hasError() {
		return !success;
	}

	/**
	 * Cria uma resposta de sucesso
	 */
	public static IaResponse success(String chatId, String resposta, BigDecimal custo, String modelo, int tokensPrompt,
			int tokensResposta) {
		IaResponse response = new IaResponse();
		response.setChatId(chatId);
		response.setResposta(resposta);
		response.setCusto(custo);
		response.setModelo(modelo);
		response.setTokensPrompt(tokensPrompt);
		response.setTokensResposta(tokensResposta);
		response.setSuccess(true);
		return response;
	}

	/**
	 * Cria uma resposta de erro
	 */
	public static IaResponse error(String chatId, String errorMessage, String errorCode) {
		IaResponse response = new IaResponse();
		response.setChatId(chatId);
		response.setResposta(errorMessage);
		response.setCusto(BigDecimal.ZERO);
		response.setModelo("erro");
		response.setTokensPrompt(0);
		response.setTokensResposta(0);
		response.markAsError(errorMessage, errorCode);
		return response;
	}

	/**
	 * Cria uma resposta de erro (sem código)
	 */
	public static IaResponse error(String chatId, String errorMessage) {
		return error(chatId, errorMessage, null);
	}
}