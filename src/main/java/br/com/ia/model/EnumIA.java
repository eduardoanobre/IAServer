package br.com.ia.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import br.com.shared.model.enums.EnumDTO;

public enum EnumIA {

    CHATGPT(1, "ChatGPT (OpenAI)", "https://platform.openai.com"),
    CLAUDE(2, "Claude (Anthropic)", "https://www.anthropic.com"),
    GEMINI(3, "Gemini (Google)", "https://ai.google.dev"),
    MISTRAL(4, "Mistral", "https://mistral.ai"),
    LLAMA(5, "LLaMA (Meta)", "https://ai.meta.com/llama");

	private final Integer id;
	private final String descricao;
	private final String url;

	private EnumIA(final Integer id, final String descricao, final String url) {
		this.id = id;
		this.descricao = descricao;
		this.url = url;
	}

	public static EnumIA obter(final Integer id) {
		return Arrays.stream(values()).filter(e -> Objects.equals(e.id, id)).findFirst().orElse(CHATGPT);
	}

	public static List<EnumIA> obterTodos() {
		return Arrays.asList(values());
	}

	public static EnumDTO[] obterDTO() {
		return Arrays.stream(values())
				.map(s -> new EnumDTO(s.getId(), s.getDescricao(), s.getDescricao(), s.getDescricao()))
				.toArray(EnumDTO[]::new);
	}

	// getters and setters

	public Integer getId() {
		return id;
	}

	public String getDescricao() {
		return descricao;
	}

	public String getUrl() {
		return url;
	}

}
