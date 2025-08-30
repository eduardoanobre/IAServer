package br.com.ia.sdk.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record RespostaIA(
	String id,
	br.com.ia.model.enums.ModeloIA modelo,
	String erro,
	Integer tokensPrompt,
	Integer tokensResposta,
    String resumo,
    BigDecimal custo,
    String resposta,
    List<AcaoIA> acoes
) implements Serializable {}