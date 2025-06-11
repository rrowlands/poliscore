package us.poliscore.service;

import java.io.File;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
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

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.bill.BillSlice;

@ApplicationScoped
public class OpenAIService {
	
	public static final String PROVIDER = "openai";
	
	public static final String MODEL = "gpt-4.1";
	
	public static final int PROMPT_VERSION = 0;
	
	public static int MAX_REQUEST_LENGTH = 3500000;
	public static int MAX_GPT4o_REQUEST_LENGTH = 490000; // GPT-4o context window in tokens is 128,000, which is 500k string length.
	
	public static final int MAX_OUTPUT_TOKENS = 3000;
	
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
	
	public String chat(String systemMsg, String userMsg) { return this.chat(systemMsg, userMsg, null); }
	
	@SneakyThrows
	public String chat(String systemMsg, String userMsg, String model)
    {
		if (userMsg.length() > OpenAIService.MAX_REQUEST_LENGTH) {
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
    			.maxTokens(MAX_OUTPUT_TOKENS)
    	        .model(StringUtils.defaultIfEmpty(model, MODEL))
    	        .build();
    	
    	System.out.println("Sending request to open ai with message size " + userMsg.length());
    	
    	ChatCompletionResult result = service.createChatCompletion(completionRequest);
    	
    	ChatCompletionChoice choice = result.getChoices().get(0);
    	
    	String out = choice.getMessage().getContent();
    	
    	if (!"stop".equals(choice.getFinishReason()))
    	{
    		out += ". FINISH_REASON: " + choice.getFinishReason();
    	}
    	
    	nextCallTime = LocalDateTime.now().plusSeconds(Math.round(((double)userMsg.length() / (double)OpenAIService.MAX_REQUEST_LENGTH) * (double)WAIT_BETWEEN_CALLS)).plusSeconds(2);
    	
    	return out;
    }
	
	/**
	 * Submits a batch of files, awaits their processing, and then downloads the results.
	 */
	@SneakyThrows
	public List<File> processBatch(List<File> files) {
		if (files.size() == 1 && Files.lines(files.get(0).toPath()).count() <= 3) return processBatchImmediately(files);
		
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
				
				RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
				    .handle(SocketTimeoutException.class)
				    .withBackoff(1, 8, ChronoUnit.SECONDS)
				    .withMaxRetries(3)
				    .onRetry(e -> Log.warn("Retrying due to timeout..."))
				    .onFailure(e -> Log.error("Retries exhausted", e.getException()))
				    .build();

				Batch b2 = Failsafe.with(retryPolicy).get(() -> service.retrieveBatch(b.getId()));
				
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
	
	/**
	 * Processes a batch of jsonl files immediately without submitting to OpenAI's batch API.
	 * 
	 * For each file:
	 * - Reads each JSONL line containing a chat completion request format.
	 * - Extracts the system and user messages, as well as the target model (if specified).
	 * - Invokes the OpenAI chat completion API for each line individually.
	 * - Saves all responses to a corresponding output file in OpenAI batch response format.
	 * 
	 * Returns a list of the output files containing the responses.
	 *
	 * @param files list of jsonl files containing batch chat completion requests
	 * @return list of output files containing responses for each input file
	 */
	@SneakyThrows
	public List<File> processBatchImmediately(List<File> files) {
		List<File> responseFiles = new ArrayList<>();

		for (File jsonlFile : files) {
			Log.info("Processing immediate batch for file: " + jsonlFile.getAbsolutePath());

			List<String> lines = FileUtils.readLines(jsonlFile, "UTF-8");
			List<String> outputLines = new ArrayList<>();

			for (String line : lines) {
				if (StringUtils.isBlank(line)) {
					continue;
				}

				val objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
				val node = objectMapper.readTree(line);
				val body = node.get("body");

				if (body == null || !body.has("messages")) {
					throw new IllegalArgumentException("Missing 'body' or 'messages' in line: " + line);
				}

				String model = body.has("model") ? body.get("model").asText() : null;
				String systemMsg = null;
				String userMsg = null;

				for (val msgNode : body.get("messages")) {
					String role = msgNode.get("role").asText();
					String content = msgNode.get("content").asText();

					if ("system".equals(role)) {
						systemMsg = content;
					} else if ("user".equals(role)) {
						userMsg = content;
					}

					if (systemMsg != null && userMsg != null) {
						break;
					}
				}

				if (systemMsg == null || userMsg == null) {
					throw new IllegalArgumentException("Expected at least one system and one user message in: " + line);
				}

				val customId = node.path("custom_id").asText(null);

				String assistantResponse = chat(systemMsg, userMsg, model);

				// Build the "body" part (chat completion result)
				val responseNode = objectMapper.createObjectNode();
				responseNode.put("id", "chatcmpl-" + java.util.UUID.randomUUID());
				responseNode.put("object", "chat.completion");
				responseNode.put("created", System.currentTimeMillis() / 1000);
				responseNode.put("model", StringUtils.defaultIfEmpty(model, MODEL));

				val choicesArray = objectMapper.createArrayNode();
				val choice = objectMapper.createObjectNode();
				choice.put("index", 0);

				val message = objectMapper.createObjectNode();
				message.put("role", "assistant");
				message.put("content", assistantResponse);
				choice.set("message", message);
				choice.put("finish_reason", "stop");
				choicesArray.add(choice);
				responseNode.set("choices", choicesArray);

				val usageNode = objectMapper.createObjectNode();
				usageNode.put("prompt_tokens", 0);
				usageNode.put("completion_tokens", 0);
				usageNode.put("total_tokens", 0);
				responseNode.set("usage", usageNode);

				// Wrap response inside OpenAI batch envelope format
				val responseEnvelope = objectMapper.createObjectNode();
				responseEnvelope.put("status_code", 200);
				responseEnvelope.set("body", responseNode);

				val out = objectMapper.createObjectNode();
				if (customId != null) {
					out.put("custom_id", customId);
				}
				out.set("response", responseEnvelope);

				outputLines.add(out.toString());
			}

			File outputFile = new File(jsonlFile.getParentFile(), jsonlFile.getName() + ".out.jsonl");
			FileUtils.writeLines(outputFile, outputLines);

			Log.info("Wrote responses to file: " + outputFile.getAbsolutePath());
			responseFiles.add(outputFile);
		}

		return responseFiles;
	}



}
