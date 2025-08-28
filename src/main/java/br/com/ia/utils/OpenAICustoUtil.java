package br.com.ia.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.ia.model.enums.ModeloIA;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OpenAICustoUtil {

	public static BigDecimal calcularCusto(String modelo, int tokensPrompt, int tokensResposta) {
		BigDecimal promptMil = BigDecimal.valueOf(tokensPrompt).divide(BigDecimal.valueOf(1000), 6,
				RoundingMode.HALF_UP);
		BigDecimal respostaMil = BigDecimal.valueOf(tokensResposta).divide(BigDecimal.valueOf(1000), 6,
				RoundingMode.HALF_UP);

		BigDecimal custoPrompt = promptMil.multiply(ModeloIA.promptPricePer1k(modelo));
		BigDecimal custoResposta = respostaMil.multiply(ModeloIA.outputPricePer1k(modelo));

		return custoPrompt.add(custoResposta).setScale(5, RoundingMode.HALF_UP);
	}

	public static BigDecimal calcularCustoPorUsage(String model, int inputTokens, int outputTokens) {
		return calcularCusto(model, inputTokens, outputTokens);
	}
}
