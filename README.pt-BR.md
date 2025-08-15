# 🤖 AI-Server SDK - Integração Simples com ChatGPT-5

**Idiomas:** [🇺🇸 English](README.md) | [🇧🇷 Português](README.pt-BR.md)

---

## 🎯 **Propósito e Objetivo**

**AI-Server SDK** foi projetado com um único objetivo claro: **integrar o ChatGPT-5 via OpenAI Responses API da forma mais simples possível** enquanto **abstrai toda a complexidade de gerenciamento de cache** para manter custos consistentes e previsíveis.

### **Por que este SDK existe:**

- 🎯 **Integração Simples**: Conecte-se ao ChatGPT-5 com apenas algumas linhas de código
- 💰 **Controle de Custos**: Gerenciamento inteligente de cache mantém os custos da API previsíveis  
- 🚀 **Zero Complexidade**: Não é necessário entender Kafka, serialização ou mensageria
- ⚡ **Pronto para Produção**: Construído para aplicações de nível empresarial desde o primeiro dia
- 🔄 **Otimizado para Stateless**: Projetado especificamente para a natureza stateless do ChatGPT-5

**AI-Server SDK** é um módulo autocontido que funciona como um **SDK interno** para qualquer aplicação Spring Boot. Você simplesmente injeta o `PromptExecutor`, chama um método e obtém sua resposta do ChatGPT-5 - toda otimização de cache acontece automaticamente nos bastidores.

## 🎯 Características Principais

- ✅ **Autocontido**: Gerencia toda a comunicação Kafka internamente
- ✅ **Interface simples**: Apenas `PromptExecutor.executePrompt()`
- ✅ **Otimizado para ChatGPT-5**: Especificamente projetado para a API Responses da OpenAI
- ✅ **Assíncrono**: Usa CompletableFuture para aguardar respostas
- ✅ **Resiliente**: Circuit breakers e políticas de retry integradas
- ✅ **Context Shards**: Gerenciamento avançado de contexto para ChatGPT-5 com cache automático
- ✅ **Otimização de Custos**: Gerenciamento inteligente de cache reduz custos da API OpenAI
- ✅ **Design Stateless**: Perfeito para a arquitetura stateless do ChatGPT-5

---

## 🏗️ Arquitetura

```
Módulo Cliente (ex: ProjectManager)
    ↓ 
PromptExecutor (interface limpa)
    ↓
AI-Server (gerencia Kafka internamente)
    ↓ Kafka ↓
ChatGPT-5 via OpenAI Responses API
    ↑ Kafka ↑  
AI-Server (processa resposta)
    ↑
CompletableFuture (resolve resposta)
    ↑
Módulo Cliente (recebe resultado)
```

---

## 📋 Requisitos

### Versões Necessárias

- **Java**: 17+
- **Spring Boot**: 3.2.3+
- **Apache Kafka**: 3.8.0+ (recomendado)
- **Spring Kafka**: 3.1+
- **Spring Cloud Stream**: 4.1+

### Dependências Maven

```xml
<dependency>
    <groupId>com.github.sua-org</groupId>
    <artifactId>ai-server-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🚀 Guia de Início Rápido

### 1. Adicionar Dependência ao Módulo Cliente

No `pom.xml` do seu módulo:

```xml
<dependencies>
    <!-- AI-Server SDK -->
    <dependency>
        <groupId>com.github.sua-org</groupId>
        <artifactId>ai-server-sdk</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- NÃO adicione dependências Kafka aqui -->
    <!-- O AI-Server gerencia tudo -->
</dependencies>
```

### 2. Configurar Apenas o Básico

No `application.properties` do módulo cliente:

```properties
# ===== Configurações do módulo (SEM Kafka) =====
server.port=8080
spring.application.name=minha-app

# ===== Configurações IA (opcionais) =====
ai.reply-timeout-ms=30000
ai.max-payload-size=300000
```

### 3. Injetar e Usar

```java
@Service
@RequiredArgsConstructor
public class MeuServicoIA {
    
    private final PromptExecutor promptExecutor;
    
    public void executarPromptIA(String chatId, String prompt) throws AIException {
        // Criar request
        PromptRequest request = new PromptRequest();
        request.setChatId(chatId);
        request.setPrompt(prompt);
        request.setApiKey("sua-api-key");
        request.setModel("gpt-5");
        
        // Executar (síncrono)
        AIResponse response = promptExecutor.executePrompt(request);
        
        // Processar resposta
        if (response.isSuccess()) {
            System.out.println("Resposta: " + response.getContent());
        } else {
            System.err.println("Erro: " + response.getErrorMessage());
        }
    }
}
```

---

## 🧠 Entendendo a Integração Stateless com IA

### **IMPORTANTE: Natureza Stateless das APIs de LLM**

Todas as integrações com Large Language Models (LLMs) são **stateless**. Isso significa:

- 🔄 **Cada request é independente**: A IA não lembra conversas anteriores
- 📦 **Contexto completo necessário**: Cada request deve incluir TODAS as informações necessárias
- 🎯 **Sem estado de sessão**: Não há memória de interações passadas

### Por que Context Shards Importam para o ChatGPT-5

Como o ChatGPT-5 é stateless, você precisa enviar contexto completo toda vez. **Context Shards** resolvem isso:

- 📝 **Organizando contexto**: Dividindo informações em pedaços lógicos
- 🏷️ **Habilitando cache**: Shards estáveis são automaticamente cacheados
- 💰 **Reduzindo custos**: Shards cacheados não são enviados repetidamente
- ⚡ **Melhorando performance**: Menos transferência de dados significa respostas mais rápidas

---

## 📖 Exemplo Completo de Implementação

### Cenário: Módulo de Gestão de Projetos

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class GestorProjetoIA {
    
    private final PromptExecutor promptExecutor;
    private final ContextShardService shardService;
    private final ObjectMapper objectMapper;
    
    /**
     * Gera sprints para um projeto usando IA
     * Este exemplo mostra o gerenciamento adequado de shards e design stateless
     */
    public List<Sprint> gerarSprints(Projeto projeto) throws AIException {
        String chatId = projeto.getChatId();
        
        // 1. Criar context shards (contexto de informações)
        List<ContextShard> shards = construirContextShards(projeto);
        
        // 2. Montar prompt
        String prompt = """
            Gere 10 sprints semanais sequenciais para este projeto.
            Considere o escopo e objetivos definidos no contexto.
            Retorne em formato JSON estruturado.
            """;
        
        // 3. Configurar request
        PromptRequest request = new PromptRequest();
        request.setChatId(chatId);
        request.setPrompt(prompt);
        request.setApiKey(projeto.getModeloIA().getApiKey());
        request.setModel(projeto.getModeloIA().getModelo());
        request.setContextShards(shards); // CRÍTICO: Incluir contexto
        request.setMaxOutputTokens(2000);
        request.setTemperaturePercent(30); // 0-100, convertido para 0-2
        
        // 4. Executar
        log.info("Gerando sprints para projeto {} via IA", projeto.getId());
        AIResponse response = promptExecutor.executePrompt(request);
        
        // 5. Processar resposta
        if (!response.isSuccess()) {
            throw new AIException("Falha na IA: " + response.getErrorMessage());
        }
        
        // 6. Converter JSON para objetos
        return converterSprintsJson(response.getContent());
    }
    
    /**
     * Constrói lista completa de context shards (extras + persistidos)
     * Este padrão garante que todo contexto necessário seja enviado para a IA
     */
    private List<ContextShard> construirContextShards(Projeto projeto) {
        var list = new ArrayList<ContextShard>();
        
        // Carregar últimos shards persistidos para este chat
        list.addAll(shardService.loadShards(
            projeto.getChatId(), 
            "PROJECT_INSTRUCTION", 
            "PROJECT_DESCRIPTION",
            "PROJECT_OBJECTIVE", 
            "PROJECT_SCOPE", 
            "PROJECT_PARTICIPANTS"
        ));
        
        return list;
    }
    
    // ... resto da implementação
}
```

---

## 🔧 Implementando a Interface ShardTracked

A interface **ShardTracked** é crucial para gerenciamento automático de cache e redução de custos:

```java
@Data
public class ProjetoResponse implements ShardTracked {

    private Long id;
    private String nome;
    private String nomeGerente;
    private String descricao;
    private String escopo;
    private BigDecimal orcamentoEstimado;
    private String chatId;
    // ... outros campos
    
    private Integer shardVersion;
    private String shardFingerprint;

    @Override
    public ContextShard getContextShard() {
        return ShardUtils.toShard(this);
    }

    @Override
    public String shardType() {
        return "projeto";
    }

    @Override
    public boolean shardStable() {
        return true; // Shards estáveis são cacheados
    }

    /**
     * CRÍTICO: Defina quais campos devem ser incluídos no shard
     * Inclua apenas campos relevantes para o contexto da IA
     */
    @Override
    public List<String> shardFields() {
        return List.of(
            "id", 
            "nome", 
            "nomeGerente", 
            "descricao", 
            "escopo",
            "orcamentoEstimado",
            "chatId"
        );
    }

    @Override
    public Integer getShardVersion() { return shardVersion; }

    @Override
    public void setShardVersion(Integer v) { this.shardVersion = v; }

    @Override
    public String getShardFingerprint() { return shardFingerprint; }

    @Override
    public void setShardFingerprint(String fp) { this.shardFingerprint = fp; }
}
```

### Como ShardTracked Funciona

1. **Seleção de Campos**: `shardFields()` define quais dados vão para a IA
2. **Conversão Automática**: `ShardUtils.toShard(this)` cria o shard
3. **Gerenciamento de Cache**: Shards estáveis são automaticamente cacheados
4. **Redução de Custos**: Shards cacheados não são enviados repetidamente para OpenAI
5. **Controle de Versão**: Rastreia mudanças com fingerprints

---

## ⚙️ Configuração Avançada

### Context Shards (Contexto Inteligente)

Context Shards permitem enviar informações estruturadas para a IA:

```java
// Shard estável (vai para cache da IA)
ContextShard shardProjeto = ContextShards.stable("PROJETO", 1, Map.of(
    "nome", "Sistema CRM",
    "descricao", "Sistema de gestão de relacionamento com cliente",
    "tecnologia", "Java + Spring Boot"
));

// Shard volátil (sempre enviado)
ContextShard shardTarefa = ContextShards.ephemeral("TAREFA", 1, Map.of(
    "id", "T001",
    "titulo", "Implementar autenticação",
    "status", "Em andamento"
));
```

### Configuração do PromptRequest

```java
PromptRequest request = new PromptRequest();

// Básico
request.setChatId("uuid-do-chat");
request.setPrompt("Seu prompt aqui");
request.setApiKey("sk-...");
request.setModel("gpt-5");

// Avançado
request.setInstructions("Você é um especialista em gestão de projetos...");
request.setMaxOutputTokens(1500);
request.setTemperaturePercent(25); // 0-100 (convertido para 0-2)

// Contexto
request.setContextShards(meusShards);
request.setModuleKey("meu-modulo");
request.setModuleRulesVersion(2);
request.setSchemaVersion(1);

// Cache
request.setCacheFacet("planejamento-sprints"); // opcional
```

---

## 🚨 O Que NÃO Fazer

### ❌ NÃO configure Kafka no módulo cliente

```java
// ❌ ERRADO - Não faça isso em módulos clientes
@Configuration
public class KafkaConfig {
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        // Não configure Kafka manualmente
    }
}
```

### ❌ NÃO adicione dependências Kafka

```xml
<!-- ❌ ERRADO - Não adicione em módulos clientes -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-stream-kafka</artifactId>
</dependency>
```

### ❌ NÃO use StreamBridge ou @KafkaListener

```java
// ❌ ERRADO - Não use Kafka diretamente
@Autowired
private StreamBridge streamBridge;

@KafkaListener(topics = "ai.responses")
public void processResponse(AIResponse response) {
    // O AI-Server já gerencia isso
}
```

### ❌ NÃO ignore context shards

```java
// ❌ ERRADO - Enviando sem contexto
PromptRequest request = new PromptRequest();
request.setPrompt("Gere algo");
// Faltando: setContextShards() - A IA não terá contexto adequado!
```

### ❌ NÃO envie todos os campos do objeto para a IA

```java
// ❌ ERRADO - Incluindo campos irrelevantes
@Override
public List<String> shardFields() {
    return List.of(
        "idInterno", "criadoPor", "ultimaModificacao", // Irrelevante para IA
        "dadosCriptografados", "notasPrivadas" // Não deve ser enviado para IA
    );
}
```

---

## 🔧 Como o AI-Server Funciona Internamente

### 1. Recepção do Request
- Módulo cliente chama `promptExecutor.executePrompt(request)`
- AI-Server cria `CompletableFuture` e armazena no `PendingRequestStore`
- Request é enviado via Kafka para o tópico `ai.requests`

### 2. Processamento Externo
- Serviço externo (OpenAI Responses API) processa o request
- Resposta é enviada de volta via Kafka no tópico `ai.responses`

### 3. Resolução da Resposta
- `AIResponseConsumer` recebe resposta via `@KafkaListener`
- Localiza o `CompletableFuture` correspondente usando `chatId`
- Resolve o future com `AIResponse`
- Método original retorna resposta para o módulo cliente

### Fluxo Detalhado

```
Módulo Cliente
    │
    ▼ promptExecutor.executePrompt(request)
AI-Server (PromptExecutorImpl)
    │
    ├─ Cria CompletableFuture
    ├─ Armazena em PendingRequestStore
    ├─ Envia via StreamBridge → Kafka (ai.requests)
    │
    ▼ Aguarda resposta...
    │
Kafka ← ChatGPT-5 via OpenAI Responses API (processa)
    │
    ▼ Resposta enviada → Kafka (ai.responses)
    │
AI-Server (AIResponseConsumer)
    ├─ @KafkaListener recebe resposta
    ├─ Localiza CompletableFuture pelo chatId
    ├─ Resolve future com AIResponse
    │
    ▼ Future.complete(response)
AI-Server (PromptExecutorImpl)
    │
    ▼ return response
Módulo Cliente (recebe resultado)
```

---

## 📦 Kafka - Ambiente de Mensageria

O AI-Server usa **Apache Kafka** como broker de mensagens para comunicação assíncrona com o ChatGPT-5 via OpenAI Responses API.  
O guia abaixo ensina como instalar e configurar o Kafka em um servidor **Ubuntu** de forma simples e sem dependência do ZooKeeper (modo KRaft).

---

## ✅ Requisitos do Kafka

- **Sistema Operacional**: Ubuntu 20.04+ (recomendado Ubuntu Server LTS)
- **Java**: Versão **17 ou superior** instalada  
  ```bash
  java -version
  ```
  Caso não tenha, instale com:
  ```bash
  sudo apt update
  sudo apt install openjdk-17-jdk -y
  ```

---

## 🚀 Instalação do Apache Kafka (sem ZooKeeper)

### 1. Baixar e extrair o Kafka
```bash
cd /opt
sudo wget https://downloads.apache.org/kafka/3.8.0/kafka_2.13-3.8.0.tgz
sudo tar -xvzf kafka_2.13-3.8.0.tgz
sudo mv kafka_2.13-3.8.0 kafka
```

### 2. Criar usuário dedicado
```bash
sudo useradd -m -s /bin/bash kafka
sudo chown -R kafka:kafka /opt/kafka
```

### 3. Configurar o Kafka em modo KRaft (sem ZooKeeper)

Gerar um **cluster-id**:
```bash
/opt/kafka/bin/kafka-storage.sh random-uuid
```

Formatar o storage (substitua `<CLUSTER_ID>` pelo valor gerado):
```bash
/opt/kafka/bin/kafka-storage.sh format -t <CLUSTER_ID> -c /opt/kafka/config/kraft/server.properties
```

### 4. Testar execução manual
```bash
/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties
```

Se subir corretamente, o broker estará rodando em `localhost:9092`.

---

## ⚙️ Configuração como Serviço (Systemd)

### Criar unit file:
```bash
sudo nano /etc/systemd/system/kafka.service
```

Conteúdo:
```ini
[Unit]
Description=Apache Kafka Server
After=network.target

[Service]
User=kafka
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties
ExecStop=/opt/kafka/bin/kafka-server-stop.sh
Restart=on-failure
RestartSec=10
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

Salvar e habilitar:
```bash
sudo systemctl daemon-reexec
sudo systemctl enable kafka
sudo systemctl start kafka
sudo systemctl status kafka
```

---

## 🛠️ Testando a Instalação

### Criar um tópico
```bash
/opt/kafka/bin/kafka-topics.sh --create --topic test-topic --bootstrap-server localhost:9092
```

### Listar tópicos
```bash
/opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Produzir mensagens
```bash
/opt/kafka/bin/kafka-console-producer.sh --topic test-topic --bootstrap-server localhost:9092
```

### Consumir mensagens
```bash
/opt/kafka/bin/kafka-console-consumer.sh --topic test-topic --from-beginning --bootstrap-server localhost:9092
```

---

## 🧩 Integração com Spring Boot (Módulos Cliente)

No `application.properties` do módulo cliente:

```properties
# ===== Configurações básicas (SEM Kafka) =====
server.port=8080
spring.application.name=minha-app

# ===== Database =====
spring.datasource.url=jdbc:mysql://localhost:3306/meudb
spring.datasource.username=root
spring.datasource.password=senha
spring.jpa.hibernate.ddl-auto=update

# ===== Configurações IA (opcionais) =====
ai.reply-timeout-ms=30000
ai.max-payload-size=300000
```

**IMPORTANTE**: Módulos cliente NÃO devem ter configurações Kafka!

---

## 🚨 Erros Comuns e Soluções

### Erro: Kafka sobe e desliga sozinho
- ✅ **Solução**: Verificar logs em `/opt/kafka/logs/`
- ✅ **Causa comum**: Falta de memória ou permissões incorretas
- ✅ **Verificar**: Storage foi formatado corretamente com `cluster-id`

### Erro: ByteArraySerializer cannot convert String
- ✅ **Solução**: Verificar configurações de serialização no AI-Server
- ✅ **Causa**: Configuração incorreta de `key.serializer` e `value.serializer`

### Erro: CompletableFuture não resolve
- ✅ **Solução**: Verificar se `AIResponseConsumer` está funcionando
- ✅ **Causa**: Problema na correlação de `chatId` entre request e response

### Erro: Context Shards muito grandes
- ✅ **Solução**: Aumentar `ai.max-payload-size` ou reduzir dados dos shards
- ✅ **Causa**: Payload JSON excede limite configurado

### Erro: Respostas da IA são inconsistentes
- ✅ **Solução**: Implementar `ShardTracked` adequadamente e enviar contexto completo para ChatGPT-5
- ✅ **Causa**: Shards de contexto faltando devido à natureza stateless do ChatGPT-5

---

## 🔍 Monitoramento e Debug

### Logs Importantes

```properties
# AI-Server - Debug completo
logging.level.com.suaorg.ai=DEBUG
logging.level.org.springframework.kafka=DEBUG
logging.level.org.apache.kafka=INFO

# Módulo Cliente - Apenas IA
logging.level.com.suaorg.meuapp.ai=DEBUG
```

### Verificar Tópicos Kafka

```bash
# Listar tópicos
/opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitorar mensagens de request
/opt/kafka/bin/kafka-console-consumer.sh --topic ai.requests --bootstrap-server localhost:9092

# Monitorar mensagens de response
/opt/kafka/bin/kafka-console-consumer.sh --topic ai.responses --bootstrap-server localhost:9092
```

### Verificar Consumer Groups

```bash
# Listar consumer groups
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Verificar lag do consumer
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group ai-processor --describe
```

---

## 💡 Melhores Práticas para Integração com ChatGPT-5

### 1. **Sempre Envie Contexto Completo**
```java
// ✅ BOM - Contexto completo via shards
request.setContextShards(contextShardsCompletos);

// ❌ RUIM - Contexto incompleto
request.setPrompt("Continue de onde paramos"); // ChatGPT-5 não lembra!
```

### 2. **Implemente ShardTracked Adequadamente**
```java
@Override
public List<String> shardFields() {
    // ✅ BOM - Apenas campos relevantes
    return List.of("id", "nome", "descricao", "status");
    
    // ❌ RUIM - Muitos campos irrelevantes
    // return List.of("id", "criadoEm", "atualizadoEm", "notasInternas", "dadosPrivados");
}
```

### 3. **Use Shards Estáveis para Cache**
```java
@Override
public boolean shardStable() {
    return true; // ✅ Habilita cache automático
}
```

### 4. **Estruture Seus Prompts**
```java
String prompt = """
    Contexto: Sistema de gestão de projetos
    Tarefa: Gerar sprints semanais
    Requisitos:
    - Mínimo 10 sprints
    - Numeração sequencial
    - Formato JSON na resposta
    
    Contexto adicional fornecido via context_shards.
    """;
```

---

## 📌 Resumo da Instalação

### Kafka
- **Versão**: Apache Kafka **3.8.0** (modo KRaft, sem ZooKeeper)
- **Java mínimo**: 17
- **Porta padrão**: `9092`
- **Instalação**: `/opt/kafka`
- **Serviço**: configurado via `systemd`

### AI-Server
- **Interface principal**: `PromptExecutor`
- **Dependência única**: `ai-server-sdk:1.0.0`
- **Configuração**: Apenas no AI-Server (autocontido)
- **Uso**: Injeção simples + método síncrono

### Módulos Cliente
- **Dependência**: Apenas `ai-server-sdk`
- **Configuração**: Mínima (sem Kafka)
- **Código**: Interface limpa via `PromptExecutor`

---

## 🎉 Conclusão

**AI-Server SDK** fornece uma **interface limpa e simples** para integração com serviços de IA, abstraindo toda a complexidade do Kafka e permitindo que módulos clientes foquem apenas na lógica de negócio.

**Benefícios:**
- ✅ **Desacoplamento completo** do Kafka nos módulos cliente
- ✅ **Reutilização** do mesmo SDK em múltiplos módulos
- ✅ **Manutenção centralizada** das configurações Kafka
- ✅ **Interface consistente** para todos os desenvolvedores
- ✅ **Resilência** com circuit breakers e retry automático
- ✅ **Otimização de custos** através de cache inteligente de shards
- ✅ **Design otimizado para ChatGPT-5** para máxima performance

### 🌟 **Conceitos Principais para Lembrar:**

1. **Natureza Stateless**: ChatGPT-5 não lembra conversas anteriores
2. **Context Shards**: Essenciais para fornecer contexto completo ao ChatGPT-5
3. **Interface ShardTracked**: Crítica para gerenciamento automático de cache
4. **Otimização de Custos**: Uso adequado de shards reduz custos da API OpenAI
5. **Contexto Completo**: Sempre envie todas as informações necessárias em cada request para ChatGPT-5

👉 **Para usar**: Adicione a dependência, injete o `PromptExecutor`, implemente `ShardTracked` nos seus DTOs e chame `executePrompt()`. É simples assim!

---

## 📄 Licença

Este projeto está disponível sob a **Licença MIT**, tornando-o livre para uso comercial.

## 🤝 Contribuindo

Contribuições são bem-vindas! Por favor, verifique nossas diretrizes de contribuição no repositório.

## 📞 Suporte

Para dúvidas e suporte, por favor abra uma issue no repositório GitHub.