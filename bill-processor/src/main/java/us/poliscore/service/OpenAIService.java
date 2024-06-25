package us.poliscore.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.SystemMessage;
import com.theokanning.openai.completion.chat.UserMessage;
import com.theokanning.openai.service.OpenAiService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;

@ApplicationScoped
public class OpenAIService {
	public static final String MODEL = "gpt-4o";
	
	public static final int PROMPT_VERSION = 0;
	
	public static final int WAIT_BETWEEN_CALLS = 60; // in seconds
	
	@Inject
    protected SecretService secret;
	
	protected LocalDateTime lastCall = null;
	
	@SneakyThrows
	public String chat(String systemMsg, String userMsg)
    {
		if (lastCall != null && ChronoUnit.SECONDS.between(lastCall, lastCall) < WAIT_BETWEEN_CALLS)
		{
			Thread.sleep(WAIT_BETWEEN_CALLS * 1000);
		}
		
		OpenAiService service = new OpenAiService(secret.getOpenAISecret(), Duration.ofSeconds(600));
    	
    	List<ChatMessage> msgs = new ArrayList<ChatMessage>();
    	msgs.add(new SystemMessage(systemMsg));
    	msgs.add(new UserMessage(userMsg));
    	
    	ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
    			.messages(msgs)
    			.n(1)
    			.temperature(0.0d) // We don't want randomness. Give us predictability and accuracy
    			.maxTokens(1000)
    	        .model(MODEL)
    	        .build();
    	
    	System.out.println("Sending request to open ai with message size " + userMsg.length());
    	
    	ChatCompletionResult result = service.createChatCompletion(completionRequest);
    	
    	ChatCompletionChoice choice = result.getChoices().get(0);
    	
    	String out = choice.getMessage().getContent();
    	
    	if (!"stop".equals(choice.getFinishReason()))
    	{
    		out += ". FINISH_REASON: " + choice.getFinishReason();
    	}
    	
    	lastCall = LocalDateTime.now();
    	
    	return out;
    }
}
