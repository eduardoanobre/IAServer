# 🤖 AI-Server SDK - ChatGPT-5 Integration Made Simple

[🇧🇷 Português](README.pt-BR.md) | [🇺🇸 English](README.md)

## 🎯 **Purpose & Objective**

**AI-Server SDK** is designed with a single, clear objective: **integrate ChatGPT-5 via OpenAI's Responses API in the simplest way possible** while **abstracting all cache management complexity** to maintain consistent and predictable costs.

### **Why This SDK Exists:**

- 🎯 **Simple Integration**: Connect to ChatGPT-5 with just a few lines of code
- 💰 **Cost Control**: Intelligent cache management keeps API costs predictable  
- 🚀 **Zero Complexity**: No need to understand Kafka, serialization, or messaging
- ⚡ **Production Ready**: Built for enterprise-grade applications from day one
- 🔄 **Stateless Optimized**: Designed specifically for ChatGPT-5's stateless nature

**AI-Server SDK** is a self-contained module that works as an **internal SDK** for any Spring Boot application. You simply inject `PromptExecutor`, call a method, and get your ChatGPT-5 response - all cache optimization happens automatically behind the scenes.

## 🎯 Key Features

- ✅ **Self-contained**: Manages all Kafka communication internally
- ✅ **Simple interface**: Just `PromptExecutor.executePrompt()`
- ✅ **ChatGPT-5 Optimized**: Specifically designed for OpenAI's Responses API
- ✅ **Asynchronous**: Uses CompletableFuture to await responses
- ✅ **Resilient**: Circuit breakers and retry policies integrated
- ✅ **Context Shards**: Advanced context management for ChatGPT-5 with automatic caching
- ✅ **Cost Optimization**: Intelligent cache management reduces OpenAI API costs
- ✅ **Stateless Design**: Perfect for ChatGPT-5's stateless architecture

---

## 🏗️ Architecture

```
Client Module (ex: ProjectManager)
    ↓ 
PromptExecutor (clean interface)
    ↓
AI-Server (manages Kafka internally)
    ↓ Kafka ↓
ChatGPT-5 via OpenAI Responses API
    ↑ Kafka ↑  
AI-Server (processes response)
    ↑
CompletableFuture (resolves response)
    ↑
Client Module (receives result)
```

---

## 📋 Requirements

### Required Versions

- **Java**: 17+
- **Spring Boot**: 3.2.3+
- **Apache Kafka**: 3.8.0+ (recommended)
- **Spring Kafka**: 3.1+
- **Spring Cloud Stream**: 4.1+

### Maven Dependencies

```xml
<dependency>
    <groupId>com.github.your-org</groupId>
    <artifactId>ai-server-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🚀 Quick Start Guide

### 1. Add Dependency to Client Module

In your module's `pom.xml`:

```xml
<dependencies>
    <!-- AI-Server SDK -->
    <dependency>
        <groupId>com.github.your-org</groupId>
        <artifactId>ai-server-sdk</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- DO NOT add Kafka dependencies here -->
    <!-- AI-Server manages everything -->
</dependencies>
```

### 2. Configure Only the Basics

In your client module's `application.properties`:

```properties
# ===== Module configurations (NO Kafka) =====
server.port=8080
spring.application.name=my-app

# ===== AI configurations (optional) =====
ai.reply-timeout-ms=30000
ai.max-payload-size=300000
```

### 3. Inject and Use

```java
@Service
@RequiredArgsConstructor
public class MyAIService {
    
    private final PromptExecutor promptExecutor;
    
    public void executeAIPrompt(String chatId, String prompt) throws AIException {
        // Create request
        PromptRequest request = new PromptRequest();
        request.setChatId(chatId);
        request.setPrompt(prompt);
        request.setApiKey("your-api-key");
        request.setModel("gpt-5");
        
        // Execute (synchronous)
        AIResponse response = promptExecutor.executePrompt(request);
        
        // Process response
        if (response.isSuccess()) {
            System.out.println("Response: " + response.getContent());
        } else {
            System.err.println("Error: " + response.getErrorMessage());
        }
    }
}
```

---

## 🧠 Understanding Stateless AI Integration

### **IMPORTANT: Stateless Nature of LLM APIs**

All integrations with Large Language Models (LLMs) are **stateless**. This means:

- 🔄 **Each request is independent**: The AI doesn't remember previous conversations
- 📦 **Complete context required**: Every request must include ALL necessary information
- 🎯 **No session state**: No memory of past interactions

### Why Context Shards Matter for ChatGPT-5

Since ChatGPT-5 is stateless, you need to send complete context every time. **Context Shards** solve this by:

- 📝 **Organizing context**: Breaking information into logical pieces
- 🏷️ **Enabling caching**: Stable shards get cached automatically
- 💰 **Reducing costs**: Cached shards aren't sent repeatedly
- ⚡ **Improving performance**: Less data transfer means faster responses

---

## 📖 Complete Implementation Example

### Scenario: Project Management Module

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectAIManager {
    
    private final PromptExecutor promptExecutor;
    private final ContextShardService shardService;
    private final ObjectMapper objectMapper;
    
    /**
     * Generates sprints for a project using AI
     * This example shows proper shard management and stateless design
     */
    public List<Sprint> generateSprints(Project project) throws AIException {
        String chatId = project.getChatId();
        
        // 1. Create context shards (information context)
        List<ContextShard> shards = buildContextShards(project);
        
        // 2. Build prompt
        String prompt = """
            Generate 10 sequential weekly sprints for this project.
            Consider the scope and objectives defined in the context.
            Return in structured JSON format.
            """;
        
        // 3. Configure request
        PromptRequest request = new PromptRequest();
        request.setChatId(chatId);
        request.setPrompt(prompt);
        request.setApiKey(project.getAIModel().getApiKey());
        request.setModel(project.getAIModel().getModel());
        request.setContextShards(shards); // CRITICAL: Include context
        request.setMaxOutputTokens(2000);
        request.setTemperaturePercent(30); // 0-100, converted to 0-2
        
        // 4. Execute
        log.info("Generating sprints for project {} via AI", project.getId());
        AIResponse response = promptExecutor.executePrompt(request);
        
        // 5. Process response
        if (!response.isSuccess()) {
            throw new AIException("AI failure: " + response.getErrorMessage());
        }
        
        // 6. Convert JSON to objects
        return convertSprintsJson(response.getContent());
    }
    
    /**
     * Builds complete context shard list (extras + persisted ones)
     * This pattern ensures all necessary context is sent to the AI
     */
    private List<ContextShard> buildContextShards(Project project) {
        var list = new ArrayList<ContextShard>();
        
        // Load latest persisted shards for this chat
        list.addAll(shardService.loadShards(
            project.getChatId(), 
            "PROJECT_INSTRUCTION", 
            "PROJECT_DESCRIPTION",
            "PROJECT_OBJECTIVE", 
            "PROJECT_SCOPE", 
            "PROJECT_PARTICIPANTS"
        ));
        
        return list;
    }
    
    // ... rest of implementation
}
```

---

## 🔧 Implementing ShardTracked Interface

The **ShardTracked** interface is crucial for automatic cache management and cost reduction:

```java
@Data
public class ProjectResponse implements ShardTracked {

    private Long id;
    private String name;
    private String managerName;
    private String description;
    private String scope;
    private BigDecimal estimatedBudget;
    private String chatId;
    // ... other fields
    
    private Integer shardVersion;
    private String shardFingerprint;

    @Override
    public ContextShard getContextShard() {
        return ShardUtils.toShard(this);
    }

    @Override
    public String shardType() {
        return "project";
    }

    @Override
    public boolean shardStable() {
        return true; // Stable shards get cached
    }

    /**
     * CRITICAL: Define which fields should be included in the shard
     * Only include fields that are relevant for AI context
     */
    @Override
    public List<String> shardFields() {
        return List.of(
            "id", 
            "name", 
            "managerName", 
            "description", 
            "scope",
            "estimatedBudget",
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

### How ShardTracked Works

1. **Field Selection**: `shardFields()` defines which data goes to AI
2. **Automatic Conversion**: `ShardUtils.toShard(this)` creates the shard
3. **Cache Management**: Stable shards get cached automatically
4. **Cost Reduction**: Cached shards aren't sent repeatedly to OpenAI
5. **Version Control**: Track changes with fingerprints

---

## ⚙️ Advanced Configuration

### Context Shards (Intelligent Context)

Context Shards allow sending structured information to AI:

```java
// Stable shard (goes to AI cache)
ContextShard projectShard = ContextShards.stable("PROJECT", 1, Map.of(
    "name", "CRM System",
    "description", "Customer relationship management system",
    "technology", "Java + Spring Boot"
));

// Volatile shard (always sent)
ContextShard taskShard = ContextShards.ephemeral("TASK", 1, Map.of(
    "id", "T001",
    "title", "Implement authentication",
    "status", "In progress"
));
```

### PromptRequest Configuration

```java
PromptRequest request = new PromptRequest();

// Basic
request.setChatId("chat-uuid");
request.setPrompt("Your prompt here");
request.setApiKey("sk-...");
        request.setModel("gpt-5");

// Advanced
request.setInstructions("You are a project management expert...");
request.setMaxOutputTokens(1500);
request.setTemperaturePercent(25); // 0-100 (converted to 0-2)

// Context
request.setContextShards(myShards);
request.setModuleKey("my-module");
request.setModuleRulesVersion(2);
request.setSchemaVersion(1);

// Cache
request.setCacheFacet("sprint-planning"); // optional
```

---

## 🚨 What NOT to Do

### ❌ DON'T configure Kafka in client modules

```java
// ❌ WRONG - Don't do this in client modules
@Configuration
public class KafkaConfig {
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        // Don't configure Kafka manually
    }
}
```

### ❌ DON'T add Kafka dependencies

```xml
<!-- ❌ WRONG - Don't add to client modules -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-stream-kafka</artifactId>
</dependency>
```

### ❌ DON'T use StreamBridge or @KafkaListener

```java
// ❌ WRONG - Don't use Kafka directly
@Autowired
private StreamBridge streamBridge;

@KafkaListener(topics = "ai.responses")
public void processResponse(AIResponse response) {
    // AI-Server already manages this
}
```

### ❌ DON'T ignore context shards

```java
// ❌ WRONG - Sending without context
PromptRequest request = new PromptRequest();
request.setPrompt("Generate something");
// Missing: setContextShards() - AI won't have proper context!
```

### ❌ DON'T send all object fields to AI

```java
// ❌ WRONG - Including irrelevant fields
@Override
public List<String> shardFields() {
    return List.of(
        "internalId", "createdBy", "lastModified", // Irrelevant for AI
        "encryptedData", "privateNotes" // Should not be sent to AI
    );
}
```

---

## 🔧 How AI-Server Works Internally

### 1. Request Reception
- Client module calls `promptExecutor.executePrompt(request)`
- AI-Server creates `CompletableFuture` and stores in `PendingRequestStore`
- Request is sent via Kafka to `ai.requests` topic

### 2. External Processing
- External service (OpenAI Responses API) processes the request
- Response is sent back via Kafka to `ai.responses` topic

### 3. Response Resolution
- `AIResponseConsumer` receives response via `@KafkaListener`
- Locates corresponding `CompletableFuture` using `chatId`
- Resolves future with `AIResponse`
- Original method returns response to client module

### Detailed Flow

```
Client Module
    │
    ▼ promptExecutor.executePrompt(request)
AI-Server (PromptExecutorImpl)
    │
    ├─ Creates CompletableFuture
    ├─ Stores in PendingRequestStore
    ├─ Sends via StreamBridge → Kafka (ai.requests)
    │
    ▼ Awaits response...
    │
Kafka ← ChatGPT-5 via OpenAI Responses API (processes)
    │
    ▼ Response sent → Kafka (ai.responses)
    │
AI-Server (AIResponseConsumer)
    ├─ @KafkaListener receives response
    ├─ Locates CompletableFuture by chatId
    ├─ Resolves future with AIResponse
    │
    ▼ Future.complete(response)
AI-Server (PromptExecutorImpl)
    │
    ▼ return response
Client Module (receives result)
```

---

## 📦 Kafka - Messaging Environment Setup

AI-Server uses **Apache Kafka** as message broker for asynchronous communication with ChatGPT-5 via OpenAI's Responses API.  
The guide below teaches how to install and configure Kafka on an **Ubuntu** server simply and without ZooKeeper dependency (KRaft mode).

---

## ✅ Kafka Requirements

- **Operating System**: Ubuntu 20.04+ (recommended Ubuntu Server LTS)
- **Java**: Version **17 or higher** installed  
  ```bash
  java -version
  ```
  If not installed:
  ```bash
  sudo apt update
  sudo apt install openjdk-17-jdk -y
  ```

---

## 🚀 Apache Kafka Installation (without ZooKeeper)

### 1. Download and extract Kafka
```bash
cd /opt
sudo wget https://downloads.apache.org/kafka/3.8.0/kafka_2.13-3.8.0.tgz
sudo tar -xvzf kafka_2.13-3.8.0.tgz
sudo mv kafka_2.13-3.8.0 kafka
```

### 2. Create dedicated user
```bash
sudo useradd -m -s /bin/bash kafka
sudo chown -R kafka:kafka /opt/kafka
```

### 3. Configure Kafka in KRaft mode (without ZooKeeper)

Generate **cluster-id**:
```bash
/opt/kafka/bin/kafka-storage.sh random-uuid
```

Format storage (replace `<CLUSTER_ID>` with generated value):
```bash
/opt/kafka/bin/kafka-storage.sh format -t <CLUSTER_ID> -c /opt/kafka/config/kraft/server.properties
```

### 4. Test manual execution
```bash
/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties
```

If it starts correctly, the broker will be running on `localhost:9092`.

---

## ⚙️ Configuration as Service (Systemd)

### Create unit file:
```bash
sudo nano /etc/systemd/system/kafka.service
```

Content:
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

Save and enable:
```bash
sudo systemctl daemon-reexec
sudo systemctl enable kafka
sudo systemctl start kafka
sudo systemctl status kafka
```

---

## 🛠️ Testing Installation

### Create a topic
```bash
/opt/kafka/bin/kafka-topics.sh --create --topic test-topic --bootstrap-server localhost:9092
```

### List topics
```bash
/opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Produce messages
```bash
/opt/kafka/bin/kafka-console-producer.sh --topic test-topic --bootstrap-server localhost:9092
```

### Consume messages
```bash
/opt/kafka/bin/kafka-console-consumer.sh --topic test-topic --from-beginning --bootstrap-server localhost:9092
```

## 🧩 Spring Boot Integration (Client Modules)

In client module's `application.properties`:

```properties
# ===== Basic configurations (NO Kafka) =====
server.port=8080
spring.application.name=my-app

# ===== Database =====
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

# ===== AI configurations (optional) =====
ai.reply-timeout-ms=30000
ai.max-payload-size=300000
```

**IMPORTANT**: Client modules should NOT have Kafka configurations!

---

## 🚨 Common Errors and Solutions

### Error: Kafka starts and shuts down by itself
- ✅ **Solution**: Check logs in `/opt/kafka/logs/`
- ✅ **Common cause**: Lack of memory or incorrect permissions
- ✅ **Verify**: Storage was formatted correctly with `cluster-id`

### Error: ByteArraySerializer cannot convert String
- ✅ **Solution**: Check serialization configurations in AI-Server
- ✅ **Cause**: Incorrect configuration of `key.serializer` and `value.serializer`

### Error: CompletableFuture doesn't resolve
- ✅ **Solution**: Check if `AIResponseConsumer` is working
- ✅ **Cause**: Problem in `chatId` correlation between request and response

### Error: Context Shards too large
- ✅ **Solution**: Increase `ai.max-payload-size` or reduce shard data
- ✅ **Cause**: JSON payload exceeds configured limit

### Error: AI responses are inconsistent
- ✅ **Solution**: Implement `ShardTracked` properly and send complete context to ChatGPT-5
- ✅ **Cause**: Missing context shards due to stateless nature of ChatGPT-5

---

## 🔍 Monitoring and Debug

### Important Logs

```properties
# AI-Server - Complete debug
logging.level.com.yourorg.ai=DEBUG
logging.level.org.springframework.kafka=DEBUG
logging.level.org.apache.kafka=INFO

# Client Module - AI only
logging.level.com.yourorg.myapp.ai=DEBUG
```

### Check Kafka Topics

```bash
# List topics
/opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor request messages
/opt/kafka/bin/kafka-console-consumer.sh --topic ai.requests --bootstrap-server localhost:9092

# Monitor response messages
/opt/kafka/bin/kafka-console-consumer.sh --topic ai.responses --bootstrap-server localhost:9092
```

### Check Consumer Groups

```bash
# List consumer groups
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Check consumer lag
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group ai-processor --describe
```

---

## 💡 Best Practices for ChatGPT-5 Integration

### 1. **Always Send Complete Context**
```java
// ✅ GOOD - Complete context via shards
request.setContextShards(completeContextShards);

// ❌ BAD - Incomplete context
request.setPrompt("Continue from where we left off"); // ChatGPT-5 doesn't remember!
```

### 2. **Implement ShardTracked Properly**
```java
@Override
public List<String> shardFields() {
    // ✅ GOOD - Only relevant fields
    return List.of("id", "name", "description", "status");
    
    // ❌ BAD - Too many irrelevant fields
    // return List.of("id", "createdAt", "updatedAt", "internalNotes", "privateData");
}
```

### 3. **Use Stable Shards for Caching**
```java
@Override
public boolean shardStable() {
    return true; // ✅ Enable automatic caching
}
```

### 4. **Structure Your Prompts**
```java
String prompt = """
    Context: Project management system
    Task: Generate weekly sprints
    Requirements:
    - 10 sprints minimum
    - Sequential numbering
    - JSON format response
    
    Additional context is provided via context_shards.
    """;
```

---

## 📌 Installation Summary

### Kafka
- **Version**: Apache Kafka **3.8.0** (KRaft mode, no ZooKeeper)
- **Minimum Java**: 17
- **Default port**: `9092`
- **Installation**: `/opt/kafka`
- **Service**: configured via `systemd`

### AI-Server
- **Main interface**: `PromptExecutor`
- **Single dependency**: `ai-server-sdk:1.0.0`
- **Configuration**: Only in AI-Server (self-contained)
- **Usage**: Simple injection + synchronous method

### Client Modules
- **Dependency**: Only `ai-server-sdk`
- **Configuration**: Minimal (no Kafka)
- **Code**: Clean interface via `PromptExecutor`

---

## 🎉 Conclusion

**AI-Server SDK** provides a **clean and simple interface** for integration with AI services, abstracting all Kafka complexity and allowing client modules to focus only on business logic.

**Benefits:**
- ✅ **Complete decoupling** from Kafka in client modules
- ✅ **Reusability** of the same SDK across multiple modules
- ✅ **Centralized maintenance** of Kafka configurations
- ✅ **Consistent interface** for all developers
- ✅ **Resilience** with circuit breakers and automatic retry
- ✅ **Cost optimization** through intelligent shard caching
- ✅ **ChatGPT-5 optimized** design for maximum performance

### 🌟 **Key Concepts to Remember:**

1. **Stateless Nature**: ChatGPT-5 doesn't remember previous conversations
2. **Context Shards**: Essential for providing complete context to ChatGPT-5
3. **ShardTracked Interface**: Critical for automatic cache management
4. **Cost Optimization**: Proper shard usage reduces OpenAI API costs
5. **Complete Context**: Always send all necessary information in each request to ChatGPT-5

👉 **To use**: Add the dependency, inject `PromptExecutor`, implement `ShardTracked` on your DTOs, and call `executePrompt()`. It's that simple!

---

## 📄 License

This project is available under the **MIT License**, making it free for commercial use.

## 🤝 Contributing

Contributions are welcome! Please check our contributing guidelines in the repository.

## 📞 Support

For questions and support, please open an issue in the GitHub repository.