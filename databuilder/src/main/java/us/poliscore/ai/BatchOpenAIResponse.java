package us.poliscore.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import us.poliscore.ai.BatchOpenAIRequest.CustomData;
import us.poliscore.ai.BatchOpenAIRequest.CustomOriginData;

@Data
@RegisterForReflection
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchOpenAIResponse {
	
	protected String id;
	
	protected String custom_id;
	
	protected String error;
	
	protected Response response;
	
	protected Usage usage;
	
	@SneakyThrows
    public CustomData getCustomData() {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode root = mapper.readTree(custom_id);

        if (root.has("origin")) {
            return mapper.treeToValue(root, CustomOriginData.class);
        } else {
            return mapper.treeToValue(root, CustomData.class);
        }
    }
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Response {
		protected int status_code;
		
		protected String request_id;
		
		protected Body body;
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Body {
		protected String id;
		
		protected String object;
		
		protected int created;
		
		protected String model;
		
		protected List<Choice> choices;
		
		protected Usage usage;
		
		protected String system_fingerprint;
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Choice {
		protected int index;
		
		protected Message message;
		
		protected String logprobs;
		
		protected String finish_reason;
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Message {
		protected String role;
		
		protected String content;
		
		protected String refusal;
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Usage {
		protected int prompt_tokens;
		
		protected int completion_tokens;
		
		protected int total_tokens;
		
		protected Object completion_tokens_details;
		
		protected Object prompt_tokens_details;
	}
}
