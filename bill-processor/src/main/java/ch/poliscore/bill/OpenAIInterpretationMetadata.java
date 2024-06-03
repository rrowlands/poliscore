package ch.poliscore.bill;

import ch.poliscore.service.OpenAIService;
import lombok.Data;

@Data
public class OpenAIInterpretationMetadata extends BillInterpretationMetadata {
	protected String model;
	
	protected int promptVersion;
	
	public static OpenAIInterpretationMetadata construct()
	{
		OpenAIInterpretationMetadata meta = new OpenAIInterpretationMetadata();
		meta.setModel(OpenAIService.MODEL);
		meta.setPromptVersion(OpenAIService.PROMPT_VERSION);
		return meta;
	}
}
