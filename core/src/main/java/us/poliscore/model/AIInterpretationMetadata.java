package us.poliscore.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
@AllArgsConstructor
@NoArgsConstructor
public class AIInterpretationMetadata extends InterpretationMetadata {
	
	@NonNull
	protected String provider;
	
	@NonNull
	protected String model;
	
	@NonNull
	protected int promptVersion;
	
	@NonNull
	protected LocalDate date;
	
	protected List<AISliceInterpretationMetadata> slices = new ArrayList<AISliceInterpretationMetadata>();
	
	public static AIInterpretationMetadata construct(String provider, String model, int promptVersion)
	{
		AIInterpretationMetadata meta = new AIInterpretationMetadata();
		meta.setProvider(provider);
		meta.setModel(model);
		meta.setPromptVersion(promptVersion);
		meta.setDate(LocalDate.now());
		return meta;
	}
	
}
