package br.com.ia.utils;

import java.math.BigDecimal;
import java.util.Map;

public final class ModelPricing {

    private static final String GPT_5_NANO = "gpt-5-nano";
	private static final String GPT_5_MINI = "gpt-5-mini";
	private static final String GPT_5 = "gpt-5";

	private ModelPricing() {}

    // USD por 1k tokens
    private static final Map<String, BigDecimal> INPUT_PER_1K = Map.of(
        GPT_5,      new BigDecimal("1.25"),
        GPT_5_MINI, new BigDecimal("0.25"),
        GPT_5_NANO, new BigDecimal("0.05")
    );

    private static final Map<String, BigDecimal> CACHED_INPUT_PER_1K = Map.of(
        GPT_5,      new BigDecimal("0.125"),
        GPT_5_MINI, new BigDecimal("0.025"),
        GPT_5_NANO, new BigDecimal("0.005")
    );

    private static final Map<String, BigDecimal> OUTPUT_PER_1K = Map.of(
        GPT_5,      new BigDecimal("10.00"),
        GPT_5_MINI, new BigDecimal("2.00"),
        GPT_5_NANO, new BigDecimal("0.40")
    );

    public static BigDecimal promptPricePer1k(String model) {
        return INPUT_PER_1K.getOrDefault(normalize(model), BigDecimal.ZERO);
    }

    public static BigDecimal cachedPromptPricePer1k(String model) {
        return CACHED_INPUT_PER_1K.getOrDefault(normalize(model), BigDecimal.ZERO);
    }

    public static BigDecimal outputPricePer1k(String model) {
        return OUTPUT_PER_1K.getOrDefault(normalize(model), BigDecimal.ZERO);
    }

    /** Normaliza variantes como "gpt-5-2025-xx" para a família base. */
    private static String normalize(String model) {
        if (model == null) return "";
        String m = model.toLowerCase();
        if (m.startsWith(GPT_5_MINI)) return GPT_5_MINI;
        if (m.startsWith(GPT_5_NANO)) return GPT_5_NANO;
        if (m.startsWith(GPT_5)) return GPT_5;
        return m;
    }
}
