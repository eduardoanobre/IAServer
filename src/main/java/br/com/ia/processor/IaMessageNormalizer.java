package br.com.ia.processor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import br.com.ia.model.IaRequest;
import br.com.ia.sdk.Base64MessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável por normalizar mensagens de entrada em formato Map.
 * <p>
 * Este normalizador processa diferentes tipos de entrada (ConsumerRecord, byte[], 
 * String, Map, objetos complexos) e os converte em um formato padrão {@code Map<String, Object>}
 * para processamento uniforme pelo sistema IAServer.
 * </p>
 * 
 * <h3>Tipos de entrada suportados:</h3>
 * <ul>
 *   <li><strong>ConsumerRecord:</strong> Extrai o valor da mensagem Kafka</li>
 *   <li><strong>byte[]:</strong> Converte para String UTF-8</li>
 *   <li><strong>Map:</strong> Retorna diretamente após cast seguro</li>
 *   <li><strong>String:</strong> Processa JSON, Base64 ou strings com escape</li>
 *   <li><strong>Objetos:</strong> Serializa via Jackson ObjectMapper</li>
 * </ul>
 * 
 * <h3>Estratégias de processamento:</h3>
 * <ol>
 *   <li>Detecção e parsing de JSON válido</li>
 *   <li>Decodificação de mensagens Base64</li>
 *   <li>Processamento de strings com escape</li>
 *   <li>Fallback para serialização via ObjectMapper</li>
 * </ol>
 * 
 * @author Sistema IAServer
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IaMessageNormalizer {

    /**
     * ObjectMapper para serialização/deserialização JSON.
     * Configurado via Spring Boot com configurações padrão do projeto.
     */
    private final ObjectMapper mapper;

    /**
     * Wrapper para processamento de mensagens codificadas em Base64.
     * Responsável por detectar e decodificar mensagens Base64 válidas.
     */
    private final Base64MessageWrapper messageWrapper;

    /**
     * TypeReference para conversão type-safe de Map com ObjectMapper.
     * Evita warnings de unchecked cast em operações de deserialização.
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = 
        new TypeReference<Map<String, Object>>() {};

    /**
     * Normaliza qualquer tipo de entrada para um Map padronizado.
     * <p>
     * Aplica uma série de estratégias de normalização baseadas no tipo da entrada,
     * garantindo que o resultado seja sempre um {@code Map<String, Object>} válido
     * ou null em caso de erro irrecuperável.
     * </p>
     * 
     * <h4>Fluxo de processamento:</h4>
     * <ol>
     *   <li>Extrai valor de ConsumerRecord se necessário</li>
     *   <li>Converte byte[] para String UTF-8</li>
     *   <li>Retorna Map diretamente se já for Map</li>
     *   <li>Processa String com estratégias específicas</li>
     *   <li>Fallback para serialização via ObjectMapper</li>
     * </ol>
     * 
     * @param input Objeto de entrada de qualquer tipo suportado
     * @return Map normalizado ou null se normalização falhar
     * 
     * @implNote Este método nunca lança exceções, sempre retorna null em caso de erro
     * @see #normalizeString(String) para detalhes do processamento de strings
     */
    public Map<String, Object> normalize(Object input) {
        if (input == null) {
            log.debug("[NORMALIZER] Input is null, returning empty map");
            return Collections.emptyMap();
        }

        try {
            Object processedInput = extractFromKafkaRecord(input);
//            
//        if(input instanceof ConsumerRecord req) {
//            	((IaRequest) input)
//            }
//            
            
            
            processedInput = convertBytesToString(processedInput);

            // Fast path para Map já válido
            if (processedInput instanceof Map<?, ?> map) {
                log.debug("[NORMALIZER] Input is Map with {} keys", map.size());
                return castToStringObjectMap(map);
            }
            
    

            // Processamento de String com estratégias específicas
            if (processedInput instanceof String stringInput) {
                return normalizeString(stringInput.trim());
            }

            // Fallback: conversão via ObjectMapper
            log.debug("[NORMALIZER] Converting {} to Map via ObjectMapper", 
                     processedInput.getClass().getSimpleName());
            return mapper.convertValue(processedInput, MAP_TYPE_REF);

        } catch (Exception e) {
            log.error("[NORMALIZER] Normalization failed for input type {}: {}", 
                     input.getClass().getSimpleName(), e.getMessage(), e);
            return null; // NOSONAR
        }
    }

    /**
     * Extrai valor de ConsumerRecord do Kafka se aplicável.
     * 
     * @param input Objeto de entrada
     * @return Valor extraído ou o próprio input se não for ConsumerRecord
     */
    private Object extractFromKafkaRecord(Object input) {
        if (input instanceof ConsumerRecord<?, ?> rec) {
            log.debug("[NORMALIZER] Extracting value from ConsumerRecord");
            return rec.value();
        }
        return input;
    }

    /**
     * Converte array de bytes para String UTF-8 se aplicável.
     * 
     * @param input Objeto de entrada
     * @return String convertida ou o próprio input se não for byte[]
     */
    private Object convertBytesToString(Object input) {
        if (input instanceof byte[] bytes) {
            String converted = new String(bytes, StandardCharsets.UTF_8);
            log.debug("[NORMALIZER] Converted byte[{}] to String[{}]", bytes.length, converted.length());
            return converted;
        }
        return input;
    }

    /**
     * Cast seguro de Map genérico para Map<String, Object>.
     * 
     * @param map Map de entrada com tipos genéricos
     * @return Map tipado como String, Object
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        // Validação básica das chaves
        boolean hasNonStringKeys = map.keySet().stream()
            .anyMatch(key -> key != null && !(key instanceof String));
        
        if (hasNonStringKeys) {
            log.warn("[NORMALIZER] Map contains non-String keys, converting via ObjectMapper");
            return mapper.convertValue(map, MAP_TYPE_REF);
        }
        
        return (Map<String, Object>) map;
    }

    /**
     * Normaliza strings aplicando estratégias específicas.
     * <p>
     * Processa strings em ordem de prioridade:
     * </p>
     * <ol>
     *   <li><strong>JSON:</strong> Detecta e parseia JSON válido</li>
     *   <li><strong>Base64:</strong> Decodifica mensagens Base64</li>
     *   <li><strong>Quoted:</strong> Remove aspas e processa escapes</li>
     * </ol>
     * 
     * @param input String de entrada já trimada
     * @return Map normalizado ou null se todas as estratégias falharem
     * 
     * @implNote Strings vazias retornam Map vazio, não null
     */
    private Map<String, Object> normalizeString(String input) {
        if (input.isEmpty()) {
            log.debug("[NORMALIZER] Empty string input, returning empty map");
            return Collections.emptyMap();
        }

        // Estratégia 1: JSON parsing
        if (isJsonLike(input)) {
            Map<String, Object> jsonResult = tryParseJson(input);
            if (jsonResult != null) {
                log.debug("[NORMALIZER] Successfully parsed JSON string");
                return jsonResult;
            }
        }

        // Estratégia 2: Base64 decoding
        if (messageWrapper.isBase64(input)) {
            Map<String, Object> base64Result = messageWrapper.unwrapFromBase64(input);
            if (base64Result != null) {
                log.debug("[NORMALIZER] Successfully decoded Base64 message");
                return base64Result;
            }
        }

        // Estratégia 3: Quoted string processing
        if (isQuotedString(input)) {
            String unquoted = removeQuotesAndUnescape(input);
            log.debug("[NORMALIZER] Processing quoted string, recursing");
            return normalizeString(unquoted);
        }

        // Nenhuma estratégia funcionou
        log.warn("[NORMALIZER] Failed to normalize string input: {}", 
                input.length() > 100 ? input.substring(0, 100) + "..." : input);
        return null; // NOSONAR
    }

    /**
     * Verifica se a string parece ser JSON válido.
     * <p>
     * Detecta padrões básicos de JSON (objeto ou array) baseado nos
     * caracteres de início e fim.
     * </p>
     * 
     * @param input String a ser verificada
     * @return true se parecer JSON válido
     */
    private boolean isJsonLike(String input) {
        return (input.startsWith("{") && input.endsWith("}")) || 
               (input.startsWith("[") && input.endsWith("]"));
    }

    /**
     * Verifica se a string está entre aspas (simples ou duplas).
     * 
     * @param input String a ser verificada
     * @return true se estiver entre aspas correspondentes
     */
    private boolean isQuotedString(String input) {
        return input.length() >= 2 && (
            (input.startsWith("\"") && input.endsWith("\"")) ||
            (input.startsWith("'") && input.endsWith("'"))
        );
    }

    /**
     * Remove aspas e processa caracteres de escape.
     * <p>
     * Remove o primeiro e último caractere (aspas) e converte
     * sequências de escape comuns para seus caracteres reais.
     * </p>
     * 
     * @param quotedString String entre aspas
     * @return String sem aspas com escapes processados
     */
    private String removeQuotesAndUnescape(String quotedString) {
        String content = quotedString.substring(1, quotedString.length() - 1);
        
        return content
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\\", "\\");
    }

    /**
     * Tenta fazer parsing de JSON de forma segura.
     * <p>
     * Utiliza o ObjectMapper configurado para deserializar a string JSON
     * em um Map. Em caso de erro, retorna null sem propagar exceção.
     * </p>
     * 
     * @param jsonString String contendo JSON
     * @return Map deserializado ou null se parsing falhar
     */
    private Map<String, Object> tryParseJson(String jsonString) {
        try {
            Map<String, Object> result = mapper.readValue(jsonString, MAP_TYPE_REF);
            return Objects.requireNonNullElse(result, Collections.emptyMap());
        } catch (Exception e) {
            log.debug("[NORMALIZER] JSON parsing failed: {}", e.getMessage());
            return null; // NOSONAR
        }
    }

    /**
     * Verifica se o normalizador está configurado corretamente.
     * Método de diagnóstico para validação de configuração.
     * 
     * @return true se todos os componentes necessários estão disponíveis
     */
    public boolean isHealthy() {
        boolean healthy = mapper != null && messageWrapper != null;
        if (!healthy) {
            log.error("[NORMALIZER] Health check failed - missing dependencies");
        }
        return healthy;
    }

    /**
     * Retorna estatísticas básicas sobre o estado do normalizador.
     * Útil para monitoramento e debug.
     * 
     * @return Map com informações de diagnóstico
     */
    public Map<String, Object> getDiagnosticInfo() {
        return Map.of(
            "mapperConfigured", mapper != null,
            "wrapperConfigured", messageWrapper != null,
            "healthy", isHealthy(),
            "component", "IaMessageNormalizer",
            "version", "1.0"
        );
    }
}