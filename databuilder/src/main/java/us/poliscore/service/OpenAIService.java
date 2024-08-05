package us.poliscore.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

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
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.parsing.BillSlicer;

@ApplicationScoped
public class OpenAIService {
	
	public static final String PROVIDER = "openai";
	
	public static final String MODEL = "gpt-4o";
	
	public static final int PROMPT_VERSION = 0;
	
	public static final int MAX_TOKENS = 3000;
	
	public static final int WAIT_BETWEEN_CALLS = 60; // in seconds
	
	@Inject
    protected SecretService secret;
	
	protected LocalDateTime nextCallTime = null;
	
	public static AIInterpretationMetadata metadata()
	{
		return AIInterpretationMetadata.construct(PROVIDER, MODEL, PROMPT_VERSION);
	}
	
	public static AIInterpretationMetadata metadata(BillSlice slice)
	{
		return AISliceInterpretationMetadata.construct(PROVIDER, MODEL, PROMPT_VERSION, slice);
	}
	
	@SneakyThrows
	public String chat(String systemMsg, String userMsg)
    {
		if (userMsg.length() > BillSlicer.MAX_SECTION_LENGTH) {
			throw new IndexOutOfBoundsException();
		}
		if (StringUtils.isEmpty(systemMsg) || StringUtils.isEmpty(userMsg)) {
			throw new IllegalArgumentException();
		}
		
		if (nextCallTime != null && ChronoUnit.SECONDS.between(LocalDateTime.now(), nextCallTime) > 0)
		{
			Thread.sleep(ChronoUnit.SECONDS.between(LocalDateTime.now(), nextCallTime) * 1000);
		}
		
		OpenAiService service = new OpenAiService(secret.getOpenAISecret(), Duration.ofSeconds(600));
    	
    	List<ChatMessage> msgs = new ArrayList<ChatMessage>();
    	msgs.add(new SystemMessage(systemMsg));
    	msgs.add(new UserMessage(userMsg));
    	
    	ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
    			.messages(msgs)
    			.n(1)
    			.temperature(0.0d) // We don't want randomness. Give us predictability and accuracy
    			.maxTokens(MAX_TOKENS)
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
    	
    	nextCallTime = LocalDateTime.now().plusSeconds(Math.round(((double)userMsg.length() / (double)BillSlicer.MAX_SECTION_LENGTH) * (double)WAIT_BETWEEN_CALLS)).plusSeconds(2);
    	
    	return out;
    }
}
