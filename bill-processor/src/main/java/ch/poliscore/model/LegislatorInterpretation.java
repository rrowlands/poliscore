package ch.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.interpretation.OpenAIInterpretationMetadata;
import lombok.Data;

@Data
public class LegislatorInterpretation implements Persistable
{
	@JsonIgnore
	protected transient Legislator legislator;
	
	protected IssueStats issueStats;
	
	protected String legislatorId;
	
	protected OpenAIInterpretationMetadata metadata;
	
	public LegislatorInterpretation()
	{
		
	}
	
	public LegislatorInterpretation(OpenAIInterpretationMetadata metadata, Legislator legislator, IssueStats stats)
	{
		this.metadata = metadata;
		this.legislator = legislator;
		this.legislatorId = legislator.getId();
		this.issueStats = stats;
	}
	
	public void setLegislator(Legislator legislator)
	{
		this.legislator = legislator;
		legislatorId = legislator.getId();
	}
	
	@JsonIgnore
	public String getId()
	{
		return legislatorId;
	}
}
