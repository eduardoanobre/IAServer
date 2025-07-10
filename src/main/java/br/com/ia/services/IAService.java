package br.com.ia.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.ia.exceptions.IAException;
import br.com.ia.model.EnumIA;
import br.com.ia.services.ias.ChatgptService;
import br.com.ia.services.ias.GeminiService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IAService {

	private final ChatgptService chatgptService;
	private final GeminiService geminiService;

	public String consultarIA(String apiKey, String mensagemUsuario, EnumIA ia) throws IAException {
		if (ia == null) {
			throw new IAException("Tipo de IA não informado.");
		}

		try {
			return switch (ia) {
			case CHATGPT -> chatgptService.consultar(apiKey, mensagemUsuario);
			case GEMINI -> geminiService.consultar(apiKey, mensagemUsuario);
			default -> throw new IAException("Integração com " + ia.getDescricao() + " ainda não implementada.");
			};
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IAException("Thread interrompida durante a chamada à IA: " + ia);
		} catch (Exception e) {
			throw new IAException("Erro ao consultar IA: " + ia);
		}
	}

	public boolean isDisponivel(EnumIA ia) {
		try {
			return switch (ia) {
			case CHATGPT -> testarConexao("https://api.openai.com/v1/models");
			case GEMINI -> testarConexao("https://generativelanguage.googleapis.com/v1beta/models");
			default -> false;
			};
		} catch (Exception e) { // NOSONAR
			return false;
		}
	}

	public String resumirTexto(String apiKey, String texto, EnumIA ia) throws IAException {
		String prompt = "Resuma o seguinte texto de forma clara e objetiva:\n\n" + texto;
		return consultarIA(apiKey, prompt, ia);
	}

	public List<String> sugerirTarefas(String apiKey, String contextoProjeto, EnumIA ia) throws IAException {
		String prompt = "Com base no contexto abaixo, liste 3 tarefas importantes para o projeto:\n\n"
				+ contextoProjeto;
		String resposta = consultarIA(apiKey, prompt, ia);
		return Arrays.stream(resposta.split("\n")).map(String::trim).filter(l -> !l.isBlank()).toList();
	}

	public String montarPromptContextual(String contexto, String objetivo) {
		return "Contexto:\n" + contexto + "\n\n" + "Objetivo:\n" + objetivo + "\n\n"
				+ "Responda de forma direta e prática.";
	}

	private boolean testarConexao(String url) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5))
				.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
		var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
		return response.statusCode() < 400;
	}

}
