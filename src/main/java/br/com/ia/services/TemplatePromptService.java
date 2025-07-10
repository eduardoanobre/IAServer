package br.com.ia.services;

import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.marketing.model.entidades.funil.FunilEtapa;
import br.com.shared.services.IService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TemplatePromptService implements IService {

	public String gerarPrompt(FunilEtapa acao, Map<String, String> parametros) {

		String prompt = ""; //etapa.getTemplatePrompt();
		for (Map.Entry<String, String> entry : parametros.entrySet()) {
			prompt = prompt.replace("[INSERIR " + entry.getKey().toUpperCase() + "]", entry.getValue());
		}

		// Adiciona a estrutura de resposta JSON que o GPT deve seguir
		prompt += "\n\nResponda em formato JSON conforme abaixo:\n{\n";
	//	prompt += "  \"nextAction\": \"" + etapa.getTipo().name().toLowerCase() + "\",\n";
		prompt += "  \"mensagem\": \"[INSERIR MENSAGEM]\",\n";
		prompt += "  \"acaoSeguinteId\": [INSERIR ID],\n";
		prompt += "  \"requiresHumanIntervention\": false,\n";
		prompt += "  \"customData\": {\n";
		prompt += "    \"score\": 85,\n";
		prompt += "    \"feedback\": \"Lead qualificado com base nas respostas.\"\n";
		prompt += "  }\n";
		prompt += "}";

		return prompt;
	}

}
