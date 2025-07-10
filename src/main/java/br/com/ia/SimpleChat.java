package br.com.ia;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.SystemMessage;
import com.theokanning.openai.service.OpenAiService;

public class SimpleChat {

	public static void main(String[] args) {

		//api-key get from environment variable OPENAI_API_KEY
		
	//	https://github.com/Lambdua/openai4j/tree/main/example/src/main/java/example
		
		  OpenAiService service = new OpenAiService(Duration.ofSeconds(30));
		  List<ChatMessage> messages = new ArrayList<>();
		  ChatMessage systemMessage = new SystemMessage("You are a cute cat and will speak as such.");
		  messages.add(systemMessage);
		  ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
		          .model("gpt-4o-mini")
		          .messages(messages)
		          .n(1)
		          .maxTokens(50)
		          .build();
		  ChatCompletionResult chatCompletion = service.createChatCompletion(chatCompletionRequest);
		  System.out.println(chatCompletion.getChoices().get(0).getMessage().getContent());

	}

}
