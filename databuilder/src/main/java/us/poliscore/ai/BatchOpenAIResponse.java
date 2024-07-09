package us.poliscore.ai;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@RegisterForReflection
@AllArgsConstructor
@NoArgsConstructor
public class BatchOpenAIResponse {
	
	protected String id;
	
	protected String custom_id;
	
	protected String error;
	
	protected Response response;
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Response {
		protected int status_code;
		
		protected String request_id;
		
		protected Body body;
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
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
	public static class Message {
		protected String role;
		
		protected String content;
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Usage {
		protected int prompt_tokens;
		
		protected int completion_tokens;
		
		protected int total_tokens;
	}
}
