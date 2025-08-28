package br.com.ia.model.enums;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public enum ModeloIA {

    // ===== GPT-5 =====
    GPT_5("gpt-5", ModelKind.TEXT, bd("0.00125"), bd("0.000125"), bd("0.01")),
    GPT_5_MINI("gpt-5-mini", ModelKind.TEXT, bd("0.00025"), bd("0.000025"), bd("0.002")),
    GPT_5_NANO("gpt-5-nano", ModelKind.TEXT, bd("0.00005"), bd("0.000005"), bd("0.0004")),

    // ===== GPT-4o =====
    GPT_4O("gpt-4o", ModelKind.TEXT, bd("0.005"), bd("0.0025"), bd("0.02")),
    GPT_4O_MINI("gpt-4o-mini", ModelKind.TEXT, bd("0.0006"), bd("0.0003"), bd("0.0024")),

    // ===== GPT-4.1 =====
    GPT_41("gpt-4.1", ModelKind.TEXT, bd("0.003"), bd("0.00075"), bd("0.012")),
    GPT_41_MINI("gpt-4.1-mini", ModelKind.TEXT, bd("0.0008"), bd("0.0002"), bd("0.0032")),
    GPT_41_NANO("gpt-4.1-nano", ModelKind.TEXT, bd("0.0002"), bd("0.00005"), bd("0.0008")),

    // ===== o-models =====
    O4_MINI("o4-mini", ModelKind.TEXT, bd("0.004"), bd("0.001"), bd("0.016")),
    O3("o3", ModelKind.TEXT, bd("0.002"), bd("0.0005"), bd("0.008")),

    // ===== Embeddings =====
    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small", ModelKind.EMBEDDING, bd("0.00002"), BigDecimal.ZERO, BigDecimal.ZERO),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large", ModelKind.EMBEDDING, bd("0.00013"), BigDecimal.ZERO, BigDecimal.ZERO);

    private final String key;
    private final ModelKind kind;
    private final BigDecimal inputPer1k;
    private final BigDecimal cachedInputPer1k;
    private final BigDecimal outputPer1k;

    ModeloIA(String key, ModelKind kind, BigDecimal inputPer1k, BigDecimal cachedInputPer1k, BigDecimal outputPer1k) {
        this.key = key;
        this.kind = kind;
        this.inputPer1k = inputPer1k;
        this.cachedInputPer1k = cachedInputPer1k;
        this.outputPer1k = outputPer1k;
    }

    // ===== Getters =====
    public String key() { return key; }
    public ModelKind kind() { return kind; }
    public BigDecimal inputPer1k() { return inputPer1k; }
    public BigDecimal cachedInputPer1k() { return cachedInputPer1k; }
    public BigDecimal outputPer1k() { return outputPer1k; }

    // ===== API Pública =====
    public static BigDecimal promptPricePer1k(String model) {
        ModeloIA p = from(model);
        return p != null ? p.inputPer1k : BigDecimal.ZERO;
    }

    public static BigDecimal cachedPromptPricePer1k(String model) {
        ModeloIA p = from(model);
        return p != null ? p.cachedInputPer1k : BigDecimal.ZERO;
    }

    public static BigDecimal outputPricePer1k(String model) {
        ModeloIA p = from(model);
        return p != null ? p.outputPer1k : BigDecimal.ZERO;
    }

    public static BigDecimal embeddingPricePer1k(String model) {
        ModeloIA p = from(model);
        return (p != null && p.kind == ModelKind.EMBEDDING) ? p.inputPer1k : BigDecimal.ZERO;
    }

    // ===== Normalização e Aliases =====
    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Pattern DATE_SUFFIX = Pattern.compile("-(20\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])(?!\\S)");

    static {
        // “latest” e aliases comuns
        ALIASES.put("gpt-5-latest", "gpt-5");
        ALIASES.put("gpt-4o-latest", "gpt-4o");
        ALIASES.put("gpt-4.1-latest", "gpt-4.1");
        ALIASES.put("o4-mini-latest", "o4-mini");
        ALIASES.put("gpt-5-chat", "gpt-5");
        ALIASES.put("gpt5", "gpt-5");
        ALIASES.put("gpt4o", "gpt-4o");
        ALIASES.put("te3-small", "text-embedding-3-small");
        ALIASES.put("te3-large", "text-embedding-3-large");
    }

    /** Busca por modelo, aceitando aliases e variantes com data. */
    public static ModeloIA from(String modelName) {
        if (modelName == null) return null;
        String m = modelName.toLowerCase(Locale.ROOT).trim();

        // Aliases
        String alias = ALIASES.get(m);
        if (alias != null) m = alias;

        // Remove sufixo de data (ex.: "-2025-08-01")
        m = DATE_SUFFIX.matcher(m).replaceFirst("");

        for (ModeloIA p : values()) {
            if (m.startsWith(p.key)) return p;
        }
        return null;
    }

    // ===== Helpers =====
    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    /** Permite registrar aliases customizados em runtime */
    public static void registerAlias(String alias, String canonicalKey) {
        if (alias != null && canonicalKey != null) {
            ALIASES.put(alias.toLowerCase(Locale.ROOT).trim(), canonicalKey.toLowerCase(Locale.ROOT).trim());
        }
    }
}
