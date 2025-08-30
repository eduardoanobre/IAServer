
package br.com.ia.instructions;

import static br.com.ia.sdk.context.ContextShards.stable;

import java.util.Map;

import br.com.ia.sdk.ShardTypes;
import br.com.ia.sdk.context.ContextShard;
import br.com.ia.sdk.context.ContextShards;
import lombok.experimental.UtilityClass;

/**
 * Instruções gerais do sistema IAServer para todos os módulos.
 * <p>
 * Este prompt contém as regras invariantes que se aplicam a todos os módulos
 * que utilizam o IAServer. O formato de resposta JSON é padronizado e uniforme
 * em todos os módulos - apenas os métodos específicos nas "ações" variam
 * conforme o domínio de cada módulo.
 * </p>
 * 
 * <h3>Padronização entre módulos:</h3>
 * <ul>
 * <li><strong>Estrutura JSON:</strong> Sempre igual (correlation_id, chat_id,
 * erro, resumo, acoes)</li>
 * <li><strong>Campos obrigatórios:</strong> correlation_id e chat_id são
 * invariantes</li>
 * <li><strong>Tratamento de erro:</strong> Padrão uniforme em todos os
 * módulos</li>
 * <li><strong>Ações específicas:</strong> Variam por módulo (workspace,
 * marketing, etc.)</li>
 * </ul>
 * 
 * @author Sistema IAServer
 * @version 1.0
 * @since 1.0
 */
@UtilityClass
public final class IAServerInstructions {

	/**
	 * Prompt de SISTEMA com regras invariantes do IAServer.
	 * <p>
	 * Este prompt define as regras que se aplicam a TODOS os módulos que utilizam o
	 * sistema IAServer. O formato de resposta JSON é padronizado e consistente
	 * entre diferentes domínios (workspace, marketing, vendas, etc.).
	 * </p>
	 * 
	 * <h4>Importante:</h4>
	 * <ul>
	 * <li>O JSON de retorno é SEMPRE o mesmo formato base</li>
	 * <li>Apenas os métodos nas "ações" são específicos de cada módulo</li>
	 * <li>Os schemas específicos definem quais métodos estão disponíveis</li>
	 * <li>Identificadores (correlation_id, chat_id) são sempre obrigatórios</li>
	 * </ul>
	 */
	public static final String PROMPT = """
			# ⚙️ IAServer — Sistema de Processamento Estruturado Universal

			## 1. Modo Operacional
			Você é um agente especializado em processamento estruturado que:
			- Processa exclusivamente dados conforme o JSON Schema fornecido
			- Opera de forma determinística e previsível
			- Não inventa propriedades, métodos ou estruturas não definidas no schema
			- Falha de forma controlada quando encontra inconsistências
			- **IMPORTANTE:** Usa sempre o mesmo formato de JSON, independente do módulo

			## 2. Formato de Saída UNIVERSAL
			**TODOS OS MÓDULOS DEVEM RESPONDER COM ESTA ESTRUTURA JSON:**

			```json
			{
			  "correlation_id": "<valor_recebido_correlation_id>",
			  "chat_id": "<valor_recebido_chat_id>",
			  "erro": null,
			  "resumo": "Descrição clara do resultado da operação",
			  "acoes": [
			    {
			      "metodo": "nome_metodo_especifico_do_modulo",
			      "dados": { /* estrutura definida no schema do módulo */ }
			    }
			  ]
			}
			```

			**Em caso de erro (PADRÃO UNIVERSAL):**
			```json
			{
			  "correlation_id": "<valor_recebido_correlation_id>",
			  "chat_id": "<valor_recebido_chat_id>",
			  "erro": "Descrição específica do erro encontrado",
			  "resumo": null,
			  "acoes": []
			}
			```

			## 3. Regras de Identificação (INVARIANTES)
			- O campo `correlation_id` é **OBRIGATÓRIO** e deve ser replicado exatamente como recebido
			- O campo `chat_id` é **OBRIGATÓRIO** e deve ser replicado exatamente como recebido
			- Estes IDs vinculam a resposta ao log de origem no sistema
			- **CRÍTICO:** IDs incorretos impedem a persistência no banco de dados

			## 4. Estrutura Universal vs Específica
			**UNIVERSAL (igual para todos os módulos):**
			- Campos: correlation_id, chat_id, erro, resumo, acoes
			- Tratamento de erros padronizado
			- Validações de IDs obrigatórias
			- Formato de data ISO-8601

			**ESPECÍFICO DE CADA MÓDULO:**
			- Nomes dos métodos nas ações (ex: "criarProjeto", "enviarEmail", "gerarRelatorio")
			- Estrutura dos dados dentro de cada ação
			- Validações específicas do domínio
			- Enums e tipos específicos

			## 5. Padrões de Qualidade (UNIVERSAIS)
			**Idioma:** Português brasileiro, claro e objetivo
			**Datas:** Formato ISO-8601 (`yyyy-MM-dd'T'HH:mm:ss`)
			**HTML:** Autocontido, sem dependências externas quando aplicável
			**Validações temporais (quando aplicável):**
			- `novaDataInicio ≤ novaDataFim`
			- `novaDataInicio ≥ data_atual`
			- `novaDataFim ≥ data_atual`

			## 6. Segurança e Eficiência (UNIVERSAIS)
			- **Nunca** exponha credenciais ou dados sensíveis
			- Seja conciso para otimizar uso de tokens
			- Em situações ambíguas, prefira retornar erro a gerar dados incorretos
			- Evite redundâncias entre campos `resumo` e campos específicos com HTML

			## 7. Hierarquia de Regras (UNIVERSAL)
			**Ordem de precedência em conflitos:**
			1. JSON Schema específico do módulo (mais alto)
			2. Regras gerais deste prompt universal
			3. Instruções específicas do módulo/contexto (mais baixo)

			## 8. Cenários de Erro Universais
			Retorne objeto com `erro` preenchido quando:
			- correlation_id ou chat_id ausentes ou inválidos
			- Dados obrigatórios ausentes conforme schema
			- Violação de políticas de segurança
			- Schema não pode ser atendido com os dados fornecidos
			- Contradições irreconciliáveis nos parâmetros
			- Métodos solicitados não existem no schema do módulo

			## 9. Observação Importante sobre Módulos
			- **Workspace:** Métodos como criarSprint, criarTarefa, editarTarefa
			- **Marketing:** Métodos como criarCampanha, enviarEmail, gerarRelatorio
			- **Vendas:** Métodos como criarOportunidade, atualizarPipeline
			- **Todos seguem o mesmo padrão JSON base**
			- **Apenas os métodos nas ações diferem entre módulos**
			""";

	/**
	 * Retorna o shard de contexto com as instruções gerais do IAServer.
	 * <p>
	 * Este shard deve ser incluído em TODOS os prompts enviados ao IAServer,
	 * independente do módulo específico. Garante consistência no formato de
	 * resposta e comportamento entre diferentes domínios.
	 * </p>
	 * 
	 * @return ContextShard estável com as regras gerais do sistema
	 */
	public static ContextShard getShard() {
		return stable(ShardTypes.INSTRUCAO, 1,
				Map.of(ContextShards.TITULO, "Regras Gerais do IAServer", ContextShards.TEXTO, PROMPT));
	}

	/**
	 * Versão das instruções gerais para controle de compatibilidade.
	 */
	public static final int VERSION = 1;

	/**
	 * Retorna informações sobre as instruções gerais. Útil para diagnóstico e
	 * validação de configuração.
	 * 
	 * @return Map com metadados das instruções
	 */
	public static Map<String, Object> getDiagnosticInfo() {
		return Map.of("version", VERSION, "component", "IAServerInstructions", "scope", "universal", "applies_to",
				"todos_os_modulos", "required_fields", java.util.List.of("correlation_id", "chat_id", "erro", "acoes"),
				"optional_fields", java.util.List.of("resumo"), "prompt_size", PROMPT.length());
	}
}