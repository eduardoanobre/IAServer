package br.com.ia.messaging;

public enum MessageType {
    IA_REQUEST,           // Requisição válida de IA a processar
    IA_RESPONSE,          // Envelope de resposta
    STARTUP_TEST,         // Teste de subida de aplicação
    PROCESSED             // Mensagem já processada
}
