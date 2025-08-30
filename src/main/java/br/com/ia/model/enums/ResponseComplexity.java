package br.com.ia.model.enums;

/**
 * Enum que define níveis de complexidade para respostas de IA.
 * <p>
 * Cada nível determina automaticamente o número máximo de tokens apropriado
 * para o tipo de resposta esperada, otimizando custo e qualidade.
 * </p>
 * 
 * <h3>Guia de uso:</h3>
 * <ul>
 *   <li><strong>SIMPLE:</strong> Respostas diretas, confirmações, dados pontuais</li>
 *   <li><strong>STANDARD:</strong> Explicações básicas, análises simples</li>
 *   <li><strong>DETAILED:</strong> Análises completas, relatórios estruturados</li>
 *   <li><strong>COMPREHENSIVE:</strong> Documentação extensa, estudos detalhados</li>
 *   <li><strong>EXTENSIVE:</strong> Relatórios corporativos, análises profundas</li>
 * </ul>
 * 
 */
public enum ResponseComplexity {

    /**
     * Respostas simples e diretas.
     * <p>
     * <strong>Casos de uso:</strong> Confirmações, dados pontuais, respostas sim/não,
     * informações básicas, validações rápidas.
     * </p>
     * <p>
     * <strong>Equivalência:</strong> ~1.500 palavras, 3-4 páginas
     * </p>
     */
    SIMPLE(2000, "Resposta simples e objetiva"),

    /**
     * Explicações padrão com detalhamento moderado.
     * <p>
     * <strong>Casos de uso:</strong> Explicações conceituais, tutoriais básicos,
     * análises introdutórias, resumos executivos.
     * </p>
     * <p>
     * <strong>Equivalência:</strong> ~3.000 palavras, 6-7 páginas
     * </p>
     */
    STANDARD(4000, "Explicação padrão com detalhes moderados"),

    /**
     * Análises detalhadas e estruturadas.
     * <p>
     * <strong>Casos de uso:</strong> Relatórios de análise, documentação técnica,
     * estudos de caso, propostas detalhadas.
     * </p>
     * <p>
     * <strong>Equivalência:</strong> ~6.000 palavras, 12-15 páginas
     * </p>
     */
    DETAILED(8000, "Análise detalhada e estruturada"),

    /**
     * Documentação abrangente e completa.
     * <p>
     * <strong>Casos de uso:</strong> Manuais técnicos, relatórios corporativos,
     * análises de mercado completas, documentação de projetos.
     * </p>
     * <p>
     * <strong>Equivalência:</strong> ~12.000 palavras, 24-30 páginas
     * </p>
     */
    COMPREHENSIVE(16000, "Documentação abrangente e completa"),

    /**
     * Análises extensas e profundas.
     * <p>
     * <strong>Casos de uso:</strong> Pesquisas acadêmicas, relatórios executivos
     * extensos, análises estratégicas profundas, documentação de sistemas complexos.
     * </p>
     * <p>
     * <strong>Equivalência:</strong> ~24.000 palavras, 48-60 páginas
     * </p>
     */
    EXTENSIVE(32000, "Análise extensa e profunda");

    private final int maxTokens;
    private final String description;

    /**
     * Construtor do enum ResponseComplexity.
     * 
     * @param maxTokens Número máximo de tokens para este nível
     * @param description Descrição do tipo de resposta
     */
    ResponseComplexity(int maxTokens, String description) {
        this.maxTokens = maxTokens;
        this.description = description;
    }

    /**
     * Retorna o número máximo de tokens para este nível de complexidade.
     * 
     * @return Limite de tokens configurado
     */
    public int getTokens() {
        return maxTokens;
    }

    /**
     * Retorna a descrição do nível de complexidade.
     * 
     * @return Descrição textual do nível
     */
    public String getDescription() {
        return description;
    }

    /**
     * Calcula o custo estimado para este nível usando um modelo específico.
     * 
     * @param modelo Modelo de IA a ser utilizado
     * @return Custo estimado em USD para uma resposta completa
     * @throws IllegalArgumentException se modelo for null
     */
    public double calculateEstimatedCost(br.com.ia.model.enums.ModeloIA modelo) {
        if (modelo == null) {
            throw new IllegalArgumentException("Modelo não pode ser null");
        }
        
        // Custo = (maxTokens / 1000) × preço por 1k tokens
        return (maxTokens / 1000.0) * modelo.outputPer1k().doubleValue();
    }

    /**
     * Retorna o nível de complexidade mais apropriado baseado no número de tokens desejado.
     * 
     * @param desiredTokens Número de tokens desejado
     * @return Nível de complexidade mais próximo
     */
    public static ResponseComplexity fromDesiredTokens(int desiredTokens) {
        if (desiredTokens <= SIMPLE.maxTokens) return SIMPLE;
        if (desiredTokens <= STANDARD.maxTokens) return STANDARD;
        if (desiredTokens <= DETAILED.maxTokens) return DETAILED;
        if (desiredTokens <= COMPREHENSIVE.maxTokens) return COMPREHENSIVE;
        return EXTENSIVE;
    }

    /**
     * Retorna o nível recomendado baseado no tipo de conteúdo.
     * 
     * @param contentType Tipo de conteúdo esperado
     * @return Nível de complexidade recomendado
     */
    public static ResponseComplexity recommendForContent(String contentType) {
        if (contentType == null) return STANDARD;
        
        String type = contentType.toLowerCase().trim();
        
        if (type.contains("resumo") || type.contains("confirmacao") || type.contains("validacao")) {
            return SIMPLE;
        }
        if (type.contains("relatorio") || type.contains("analise") || type.contains("documento")) {
            return DETAILED;
        }
        if (type.contains("manual") || type.contains("completo") || type.contains("abrangente")) {
            return COMPREHENSIVE;
        }
        if (type.contains("extenso") || type.contains("profundo") || type.contains("pesquisa")) {
            return EXTENSIVE;
        }
        
        return STANDARD; // Padrão para casos não identificados
    }

    /**
     * Retorna uma representação amigável do enum.
     * 
     * @return String no formato "DETAILED (8.000 tokens): Análise detalhada..."
     */
    @Override
    public String toString() {
        return String.format("%s (%,d tokens): %s", 
                           name(), 
                           maxTokens, 
                           description);
    }
}