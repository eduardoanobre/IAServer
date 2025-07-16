package br.com.ia.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpHeaders;

import br.com.shared.model.enums.EnumModeloIA;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OpenAICustoUtil {

	public static BigDecimal calcularCustoPorHeaders(HttpHeaders headers) {
		String modelo = headers.getFirst("openai-model");
		int tokensPrompt = parseIntSafe(headers.getFirst("openai-usage-tokens-prompt"));
		int tokensResposta = parseIntSafe(headers.getFirst("openai-usage-tokens-completion"));

		return calcularCusto(modelo, tokensPrompt, tokensResposta);
	}

	public static BigDecimal calcularCusto(String modelo, int tokensPrompt, int tokensResposta) {
		EnumModeloIA tipoModelo = EnumModeloIA.fromModelo(modelo);
		if (tipoModelo == null)
			return BigDecimal.ZERO;

		BigDecimal promptMil = BigDecimal.valueOf(tokensPrompt).divide(BigDecimal.valueOf(1000), 6,
				RoundingMode.HALF_UP);
		BigDecimal respostaMil = BigDecimal.valueOf(tokensResposta).divide(BigDecimal.valueOf(1000), 6,
				RoundingMode.HALF_UP);

		BigDecimal custoPrompt = promptMil.multiply(tipoModelo.getCustoPromptPorMil());
		BigDecimal custoResposta = respostaMil.multiply(tipoModelo.getCustoRespostaPorMil());

		return custoPrompt.add(custoResposta).setScale(5, RoundingMode.HALF_UP);
	}

	private static int parseIntSafe(String value) {
		try {
			return value != null ? Integer.parseInt(value) : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
