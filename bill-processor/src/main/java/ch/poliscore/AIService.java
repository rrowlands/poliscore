package ch.poliscore;

import java.time.Duration;
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

@ApplicationScoped
public class AIService {
	@Inject
    SecretService secret;
	
	public String Chat(String systemMsg, String userMsg)
    {
		/*
		OpenAiService service = new OpenAiService(secret.getSecret(), Duration.ofSeconds(600));
    	
    	List<ChatMessage> msgs = new ArrayList<ChatMessage>();
    	msgs.add(new SystemMessage(systemMsg));
    	msgs.add(new UserMessage(userMsg));
    	
    	ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
    			.messages(msgs)
    			.n(1)
    			.maxTokens(500)
    	        .model("gpt-4o")
    	        .build();
    	
    	ChatCompletionResult result = service.createChatCompletion(completionRequest);
    	
    	ChatCompletionChoice choice = result.getChoices().get(0);
    	
    	String out = choice.getMessage().getContent();
    	
    	if (!"stop".equals(choice.getFinishReason()))
    	{
    		out += ". FINISH_REASON: " + choice.getFinishReason();
    	}
    	
    	return out;
    	*/
		
		IssueStats stats = new IssueStats();
		for (TrackedIssue issue : TrackedIssue.values())
		{
			stats.addStat(issue, 1);
		}
		stats.explanation = "Test Explanation 1.";
		return stats.toString();
    }
}
