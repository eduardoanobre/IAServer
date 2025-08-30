package br.com.ia.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.ia.model.enums.ModeloIA;
import br.com.ia.model.enums.ResponseComplexity;
import br.com.ia.sdk.context.ContextShard;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Classe que encapsula uma requisição de prompt para o sistema <b>IAServer</b>.
 * <p>
 * Esta classe representa uma solicitação estruturada que será enviada para o 
 * módulo IAServer, que por sua vez encaminha o prompt para sistemas de IA 
 * como ChatGPT. Contém todas as configurações necessárias para processamento
 * do prompt, incluindo contexto, esquemas de resposta e parâmetros de modelo.
 * </p>
 * 
 * <h3>Exemplo de uso:</h3>
 * <pre>
 * PromptRequest request = new PromptRequest();
 * request.setChatId("UUID");
 * request.setPrompt("Analise os dados fornecidos");
 * request.setModelo(ModeloIA.GPT_4O);
 * request.setModuleKey("workspace.projetos");
 * 
 * // Enviar para IAServer
 * iaServer.processPrompt(request);
 * </pre>
 * 
 * @author Sistema IAServer
 * @version 1.0
 * @since 1.0
 */
@Data
@NoArgsConstructor
public class PromptRequest {

    /**
     * Identificador único da sessão de chat/conversa.
     * Permite manter contexto e continuidade entre múltiplas interações.
     * 
     * @implNote Este ID deve ser consistente durante toda a sessão
     */
    private String chatId;

    /**
     * Texto do prompt principal a ser processado pelo modelo de IA.
     * Contém a instrução ou pergunta que será enviada ao sistema de assistência.
     */
    private String prompt;

    /**
     * Modelo de IA a ser utilizado para processamento do prompt.
     * <p>
     * Utiliza o enum {@link ModeloIA} para garantir tipagem segura e 
     * controle de custos automatizado. Exemplos: {@code ModeloIA.GPT_4O}, 
     * {@code ModeloIA.GPT_5}, {@code ModeloIA.GPT_4O_MINI}.
     * </p>
     * 
     * @see ModeloIA para lista completa de modelos suportados
     */
    private ModeloIA modelo;

    /**
     * Chave de API para autenticação com o serviço de IA.
     * <p>
     * <strong>IMPORTANTE:</strong> Esta informação é sensível e deve ser 
     * tratada com segurança apropriada.
     * </p>
     */
    private String apiKey;

    /**
     * Temperatura para controle de criatividade/aleatoriedade.
     * <p>
     * Valores típicos:
     * <ul>
     *   <li>0 - 100: Percentual</li>
     * </ul>
     * </p>
     */
    private Double temperatura;

    /**
     * Nível de complexidade esperado para a resposta.
     * <p>
     * Define automaticamente o número máximo de tokens apropriado para o tipo
     * de resposta desejada, otimizando custo e garantindo completude.
     * </p>
     * <p>
     * <strong>Níveis disponíveis:</strong>
     * <ul>
     *   <li>{@code SIMPLE}: Respostas diretas (2.000 tokens)</li>
     *   <li>{@code STANDARD}: Explicações padrão (4.000 tokens)</li>
     *   <li>{@code DETAILED}: Análises detalhadas (8.000 tokens) - Padrão</li>
     *   <li>{@code COMPREHENSIVE}: Documentação completa (16.000 tokens)</li>
     *   <li>{@code EXTENSIVE}: Análises profundas (32.000 tokens)</li>
     * </ul>
     * </p>
     * 
     * @see ResponseComplexity para detalhes sobre cada nível
     * @implNote O valor padrão DETAILED oferece bom equilíbrio para prompts extensos
     */
    private ResponseComplexity complexity = ResponseComplexity.DETAILED;

    /**
     * Lista de fragmentos de contexto (shards) que complementam o prompt principal.
     * Permite segmentar e organizar diferentes tipos de informação contextual.
     */
    private List<ContextShard> shards;

    /**
     * Instruções específicas sobre como processar o prompt.
     * Define comportamentos, formatos de resposta e diretrizes especiais.
     */
    private String instructions;

    /**
     * Esquema JSON que define a estrutura esperada da resposta.
     * Utilizado para respostas estruturadas e validação de formato.
     * 
     * @implNote Deve seguir especificação JSON Schema quando utilizado
     */
    private Map<String, Object> schema;

    /**
     * Versão das instruções utilizadas, para controle de compatibilidade.
     * Permite evolução gradual do sistema mantendo retrocompatibilidade.
     */
    private int instructionVersion;

    /**
     * Versão do esquema JSON utilizado.
     * Sincronizado com {@code instructionVersion} para garantir coerência.
     */
    private int schemaVersion;

    /**
     * Faceta de cache para otimização de respostas similares.
     * Permite agrupamento lógico para estratégias de cache mais eficientes.
     * 
     * @implNote Combinado com {@code moduleKey} para criar chaves de cache únicas
     */
    private String cacheFacet;

    // ===== SHARDS NOMEADOS =====
    // Fragmentos de contexto específicos para organização semântica

    /**
     * Shard contendo instruções específicas do módulo/domínio.
     * Define comportamentos particulares para o tipo de processamento solicitado.
     */
    private ContextShard shardInstrucao;

    /**
     * Shard com descrição detalhada do problema ou situação.
     * Fornece contexto descritivo para melhor compreensão da solicitação.
     */
    private ContextShard shardDescricao;

    /**
     * Shard definindo o objetivo específico da requisição.
     * Esclarece qual resultado é esperado do processamento.
     */
    private ContextShard shardObjetivo;

    /**
     * Shard delimitando o escopo e limitações da análise.
     * Define fronteiras e restrições para o processamento.
     */
    private ContextShard shardEscopo;

    /**
     * Shard contendo informações sobre participantes ou stakeholders.
     * Relevante quando o contexto envolve múltiplos atores ou personas.
     */
    private ContextShard shardParticipantes;

    /**
     * Identificador lógico do módulo/vertente (ex.: "workspace.projetos",
     * "marketing.campanhas"). Ajuda a segmentar chave de cache entre módulos.
     * <p>
     * <strong>Recomendado</strong> para otimização de cache e organização lógica.
     * </p>
     * 
     * @implNote Usado em conjunto com {@code cacheFacet} para criar chaves de cache
     */
    private String moduleKey;

    /**
     * Construtor de cópia que cria uma nova instância baseada em outra existente.
     * <p>
     * Realiza uma cópia superficial (shallow copy) dos atributos, onde:
     * <ul>
     *   <li>Valores primitivos e strings são copiados por valor</li>
     *   <li>Collections são recriadas, mas elementos mantêm referências originais</li>
     *   <li>Objetos complexos (shards) mantêm referências originais</li>
     * </ul>
     * </p>
     * 
     * @param src Instância fonte para cópia (não pode ser null)
     * @throws NullPointerException se {@code src} for null
     * 
     * @implNote Esta implementação é otimizada para cenários onde se deseja
     *           modificar configurações sem afetar a instância original
     */
    public PromptRequest(PromptRequest src) {
        Objects.requireNonNull(src, "Instância fonte não pode ser null");
        
        // Cópia de valores primitivos e strings
        this.chatId = src.chatId;
        this.prompt = src.prompt;
        this.modelo = src.modelo;
        this.apiKey = src.apiKey;
        this.temperatura = src.temperatura;
        this.complexity = src.complexity;
        this.instructions = src.instructions;
        this.instructionVersion = src.instructionVersion;
        this.schemaVersion = src.schemaVersion;
        this.cacheFacet = src.cacheFacet;
        this.moduleKey = src.moduleKey;

        // Cópia de collections (nova lista, elementos por referência)
        this.shards = (src.shards != null) ? new ArrayList<>(src.shards) : null;

        // Cópia de mapa (novo mapa, valores por referência)
        this.schema = (src.schema != null) ? new LinkedHashMap<>(src.schema) : null;

        // Cópia de shards nomeados (referências mantidas)
        this.shardInstrucao = src.shardInstrucao;
        this.shardDescricao = src.shardDescricao;
        this.shardObjetivo = src.shardObjetivo;
        this.shardEscopo = src.shardEscopo;
        this.shardParticipantes = src.shardParticipantes;
    }

    /**
     * Adiciona um shard à lista de contextos.
     * Método de conveniência para facilitar a construção fluente da requisição.
     * 
     * @param shard O shard a ser adicionado (não pode ser null)
     * @return Esta instância para permitir method chaining
     * @throws NullPointerException se {@code shard} for null
     */
    public PromptRequest addShard(ContextShard shard) {
        Objects.requireNonNull(shard, "Shard não pode ser null");
        
        if (this.shards == null) {
            this.shards = new ArrayList<>();
        }
        this.shards.add(shard);
        return this;
    }

    /**
     * Retorna a chave textual do modelo para compatibilidade.
     * Útil para integração com APIs que esperam string.
     * 
     * @return Chave do modelo ou null se modelo não definido
     */
    public String getModelKey() {
        return modelo != null ? modelo.key() : null;
    }

    /**
     * Adiciona uma entrada ao schema JSON.
     * Método de conveniência para construção incremental do schema.
     * 
     * @param key Chave da propriedade no schema
     * @param value Valor da propriedade
     * @return Esta instância para permitir method chaining
     * @throws NullPointerException se {@code key} for null
     */
    public PromptRequest addSchemaProperty(String key, Object value) {
        Objects.requireNonNull(key, "Chave do schema não pode ser null");
        
        if (this.schema == null) {
            this.schema = new LinkedHashMap<>();
        }
        this.schema.put(key, value);
        return this;
    }

    /**
     * Verifica se a requisição possui configuração mínima válida.
     * Valida se os campos obrigatórios estão preenchidos adequadamente.
     * 
     * @return true se a requisição possui configuração básica válida
     */
    public boolean isValid() {
        return chatId != null && !chatId.trim().isEmpty()
            && prompt != null && !prompt.trim().isEmpty()
            && modelo != null;
    }

    /**
     * Retorna uma representação textual resumida da requisição.
     * Útil para logging e debug, omitindo informações sensíveis.
     * 
     * @return String representativa da requisição
     */
    @Override
    public String toString() {
        return String.format("PromptRequest{chatId='%s', modelo='%s', moduleKey='%s', promptLength=%d}", 
                           chatId, 
                           modelo != null ? modelo.key() : null, 
                           moduleKey, 
                           prompt != null ? prompt.length() : 0);
    }
}