package us.poliscore.service;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.SystemMessage;
import com.theokanning.openai.completion.chat.UserMessage;
import com.theokanning.openai.service.OpenAiService;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.parsing.BillSlicer;

@ApplicationScoped
public class OpenAIService {
	
	public static final String PROVIDER = "openai";
	
	public static final String MODEL = "gpt-4.1";
	
	public static final int PROMPT_VERSION = 0;
	
	// GPT-4o context window in tokens is 128,000
	public static final int MAX_TOKENS = 3000;
	
	public static int MAX_SECTION_LENGTH = 3500000;
	
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
		if (userMsg.length() > OpenAIService.MAX_SECTION_LENGTH) {
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
    	
    	nextCallTime = LocalDateTime.now().plusSeconds(Math.round(((double)userMsg.length() / (double)OpenAIService.MAX_SECTION_LENGTH) * (double)WAIT_BETWEEN_CALLS)).plusSeconds(2);
    	
    	return out;
    }
	
	/**
	 * Submits a batch of files, awaits their processing, and then downloads the results.
	 */
	@SneakyThrows
	public List<File> processBatch(List<File> files) {
		OpenAiService service = new OpenAiService(secret.getOpenAISecret(), Duration.ofSeconds(600));
		
		final List<Batch> batches = new ArrayList<Batch>();
		final List<File> responseFiles = new ArrayList<File>();
		
		for (File f : files) {
			Log.info("Sending request batch file to OpenAI [" + f.getAbsolutePath() + "]");
			
			val f2 = service.uploadFile("batch", f.getAbsolutePath());
			
			batches.add(service.createBatch(BatchRequest.builder()
					.inputFileId(f2.getId())
					.endpoint("/v1/chat/completions")
					.completionWindow("24h")
					.build()));
		}
		
		Log.info("Awaiting OpenAI to process our batch files (this will take a while)...");
		
		while (batches.size() > 0) {
			Thread.sleep(Duration.ofMinutes(1));
			
			Iterator<Batch> it = batches.iterator();
			
			while (it.hasNext()) {
				val b = it.next();
				val b2 = service.retrieveBatch(b.getId());
				
				if (b2.getStatus().equals("completed") && StringUtils.isNotEmpty(b2.getOutputFileId())) {
					val body = service.retrieveFileContent(b2.getOutputFileId());
					
					val f = new File(Environment.getDeployedPath(), b2.getOutputFileId() + ".jsonl");
					FileUtils.writeByteArrayToFile(f, body.bytes());
					responseFiles.add(f);
					
					it.remove();
					
					Log.info("Batch file successfully processed by OpenAI [" + f.getAbsolutePath() + "].");
				}
			}
		}
		
		return responseFiles;
	}
}
