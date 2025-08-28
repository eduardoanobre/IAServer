package br.com.ia.messaging;

public enum MessageType {
    IA_REQUEST,           // Requisição válida de IA a processar
    IA_RESPONSE_LOOP,     // Envelope de resposta detectado (anti-loop)
    STARTUP_TEST,         // Teste de subida de aplicação
    CONNECTION_TEST,      // Teste de conexão
    PROCESSING_RESPONSE,  // Resposta interna (não deve ser reprocessada)
    DUPLICATE_MESSAGE,    // Mensagem duplicada
    UNKNOWN               // Formato desconhecido
}
