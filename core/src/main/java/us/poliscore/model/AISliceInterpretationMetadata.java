package us.poliscore.model;

import lombok.Data;
import us.poliscore.model.bill.BillSlice;

@Data
public class AISliceInterpretationMetadata extends AIInterpretationMetadata {
	
	protected int sectionStart;
	
	protected int sectionEnd;
	
	protected int sliceIndex;
	
	public static AISliceInterpretationMetadata construct(String provider, String model, int version, BillSlice slice)
	{
		AISliceInterpretationMetadata meta = new AISliceInterpretationMetadata();
		meta.setProvider(provider);
		meta.setModel(model);
		meta.setPromptVersion(version);
		meta.setSectionStart(slice.getSectionStart());
		meta.setSectionEnd(slice.getSectionEnd());
		meta.setSliceIndex(slice.getSliceIndex());
		return meta;
	}
	
}
