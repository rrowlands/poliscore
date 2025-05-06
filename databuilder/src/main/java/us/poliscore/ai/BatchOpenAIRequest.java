package us.poliscore.ai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import us.poliscore.model.InterpretationOrigin;
import us.poliscore.service.OpenAIService;

@Data
@RegisterForReflection
@AllArgsConstructor
@NoArgsConstructor
public class BatchOpenAIRequest {
	// {"custom_id": "request-1", "method": "POST", "url": "/v1/chat/completions", "body": {"model": "gpt-3.5-turbo-0125", "messages": [{"role": "system", "content": "You are a helpful assistant."},{"role": "user", "content": "Hello world!"}],"max_tokens": 1000}}
	
	private String custom_id;
	
	private String method = "POST";
	
	private String url = "/v1/chat/completions";
	
	private BatchOpenAIBody body;
	
	@SneakyThrows
	public BatchOpenAIRequest(CustomData data, BatchOpenAIBody body) {
		this.custom_id = customDataToCustomId(data);
		this.body = body;
	}
	
	public BatchOpenAIRequest(String id, BatchOpenAIBody body) {
		this.custom_id = id;
		this.body = body;
	}
	
	/**
	 * OpenAI's custom_id field has a max length of 512 so we'll occasionally need to truncate things here.  
	 * 
	 * @param data
	 * @return
	 */
	@SneakyThrows
	public static String customDataToCustomId(CustomData data) {
	    ObjectMapper mapper = new ObjectMapper();
	    String json = mapper.writeValueAsString(data);

	    if (json.length() <= 512) return json;

	    if (data instanceof CustomOriginData) {
	        var origin = ((CustomOriginData) data).getOrigin();

	        String title = origin.getTitle();
	        String url = origin.getUrl();

	        // Prefer truncating title unless title is very short
	        boolean preferTitle = title != null && (title.length() >= 20 || url == null);

	        while (true) {
	            json = mapper.writeValueAsString(data);
	            if (json.length() <= 512) break;

	            if (preferTitle && title != null && title.length() > 2) {
	                title = title.substring(0, title.length() - 3) + "..";
	                origin.setTitle(title);
	            } else if (url != null && url.length() > 2) {
	                url = url.substring(0, url.length() - 3) + "..";
	                origin.setUrl(url);
	            } else if (!preferTitle && title != null && title.length() > 2) {
	                title = title.substring(0, title.length() - 3) + "..";
	                origin.setTitle(title);
	            } else {
	                break; // cannot truncate further
	            }
	        }
	    }

	    return mapper.writeValueAsString(data);
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
