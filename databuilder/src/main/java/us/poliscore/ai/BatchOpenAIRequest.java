package us.poliscore.ai;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import us.poliscore.model.InterpretationOrigin;
import us.poliscore.service.OpenAIService;

@Data
@RegisterForReflection
@AllArgsConstructor
@NoArgsConstructor
public class BatchOpenAIRequest {
	// {"custom_id": "request-1", "method": "POST", "url": "/v1/chat/completions", "body": {"model": "gpt-3.5-turbo-0125", "messages": [{"role": "system", "content": "You are a helpful assistant."},{"role": "user", "content": "Hello world!"}],"max_tokens": 1000}}
	
	private CustomData custom_id;
	
	private String method = "POST";
	
	private String url = "/v1/chat/completions";
	
	private BatchOpenAIBody body;
	
	public BatchOpenAIRequest(CustomData id, BatchOpenAIBody body) {
		this.custom_id = id;
		this.body = body;
	}
	
	public BatchOpenAIRequest(String id, BatchOpenAIBody body) {
		this.custom_id = new CustomData(id);
		this.body = body;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CustomData
	{
		protected String oid;
	}
	
	@Data
	@EqualsAndHashCode(callSuper = true)
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CustomOriginData extends CustomData
	{
		protected InterpretationOrigin origin;
		
		public CustomOriginData(InterpretationOrigin origin, String oid) { this.origin = origin; this.oid = oid; }
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	public static class BatchOpenAIBody {
		private String model = OpenAIService.MODEL;
		
		private List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
		
		private int max_tokens = OpenAIService.MAX_TOKENS;
		
		private float temperature = 0.0f;
		
		public BatchOpenAIBody(List<BatchBillMessage> messages) {
			this.messages = messages;
		}
		
		public BatchOpenAIBody(List<BatchBillMessage> messages, String model) {
			this.messages = messages;
			this.model = model;
		}
	}
	
	@Data
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	public static class BatchBillMessage {
		private String role;
		
		private String content;
	}
}
