package br.com.ia.sdk.response;

import java.math.BigDecimal;
import java.util.List;

public record RespostaIA(
	Long idRequest,
	String modelo,
	String erro,
	Integer tokensPrompt,
	Integer tokensResposta,
    String resumo,
    BigDecimal custo,
    String errorMessage,
    List<AcaoIA> acoes
) {}