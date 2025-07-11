# 📄 IA Server

Projeto Java para centralização das chamadas a serviços de IA (ChatGPT, Gemini, etc.), orquestrado por um módulo Kafka.

---

## 🎯 Objetivo

* Centralizar toda a comunicação com diferentes provedores de IA.
* Evitar duplicação de bibliotecas e configurações de IA em múltiplos módulos.
* Facilitar manutenção, evoluções e correções futuras.
* Desacoplar o consumo de IA do restante da aplicação usando Kafka como barramento de mensagens.

---

## 🏗️ Arquitetura

1. **Módulo IA Server** (biblioteca):

   * Expõe um `IAClient` que faz chamadas HTTP à API de IA configurada.
   * Não carrega propriedades de IA no `application.yml`; recebe `apiKey`, `model`, `messages`, etc. diretamente no payload.
2. **Fluxo Kafka**:

   * `IaProcessor` consome mensagens `IaRequest` de um tópico (`ia.requests`).
   * Constrói e envia um `ChatCompletionRequest` ao `IAClient`.
   * Publica a resposta `IaResponse` em outro tópico (`ia.responses`).
3. **Módulos ERP9i**:

   * Todos dependem do JAR do IA Server.
   * Publicam solicitações de IA no Kafka e consomem respostas, ou chamam `IAClient` diretamente para uso síncrono.

---

## 🚀 Como usar

### 1. Adicione a dependência Maven

No `pom.xml` de cada microserviço:

```xml
<dependency>
  <groupId>br.com.ia</groupId>
  <artifactId>iaserver</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 2. Configure o barramento Kafka e Resilience4j

Em `src/main/resources/application.yml` (ou `.properties`):

```yaml
spring:
  application:
    name: ia-processor

spring:
  cloud:
    stream:
      bindings:
        processIa-in-0:
          destination: ia.requests
          group: ia-processor
        processIa-out-0:
          destination: ia.responses
      kafka:
        binder:
          brokers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

resilience4j:
  circuitbreaker:
    instances:
      iaClient:
        registerHealthIndicator: true
        slidingWindowSize: 20
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  retry:
    instances:
      iaClientRetry:
        maxAttempts: 3
        waitDuration: 2s
```

> **Observação:** Mesmo que as configurações de IA (URL, API key, model) não fiquem no `application.yml`, você ainda precisa desse arquivo para definir o broker Kafka, bindings e Resilience4j.

### 3. Enviando uma solicitação de IA

Em qualquer serviço (por exemplo, MarketingService):

```java

	private final IAClient iaClient;
	
	String prompt = String.format(
	    "Você é um especialista em marketing. Crie um título curto e impactante para '%s' voltado para '%s'.",
	    produto, publicoAlvo);
	
	ChatCompletionRequest req = ChatCompletionRequest.builder()
	    .provider("openai")            // ex.: "openai" ou "gemini"
	    .apiKey("sk-teste")            // opcional, override da chave
	    .model("gpt-4o-mini")          // opcional, override do modelo
	    .messages(List.of(
	        new ChatMessage("system", "Você é um assistente de marketing."),
	        new ChatMessage("user", prompt)
	    ))
	    .temperature(0.8)
	    .maxTokens(30)
	    .build();
	
	String resposta = iaClient.call(req);
```

Ou, no fluxo Kafka, publique um `IaRequest` contendo um map `options` com as mesmas chaves (`api_key`, `model`, `temperature`, `max_tokens`).

---

## ⚙️ Casos de uso

* **Geração de copy**: títulos, descrições, e-mails de marketing.
* **Atendimento automatizado**: chats e assistentes virtuais.
* **Análise e sumarização**: relatórios, insights de vendas.

---

## 📌 Observações

* O design desacopla completamente a lógica de IA do código de negócio usando Kafka.
* Suporta múltiplos provedores (OpenAI, Gemini, etc.) apenas trocando o campo `provider` no request.
* Resiliência garantida por Resilience4j (circuit breaker + retry).

---

## 📝 Autor

Projeto interno da equipe de desenvolvimento da 9i Sistemas.

---

## 📄 Licença

MIT © 9i Sistemas
