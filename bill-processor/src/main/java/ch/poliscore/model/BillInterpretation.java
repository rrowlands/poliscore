package ch.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.IssueStats;
import ch.poliscore.bill.OpenAIInterpretationMetadata;
import ch.poliscore.bill.OpenAISliceInterpretationMetadata;
import lombok.Data;

@Data
public class BillInterpretation implements Persistable
{
	@JsonIgnore
	protected transient Bill bill;
	
	@JsonIgnore
	protected IssueStats issueStats = null;
	
	protected String id;
	
	protected String billUrl;
	
	/**
	 * The actual interpretation text of the bill or bill slice, as produced by AI.
	 */
	protected String text;
	
	protected OpenAIInterpretationMetadata metadata;
	
	public BillInterpretation()
	{
		
	}
	
	public BillInterpretation(OpenAIInterpretationMetadata metadata, Bill bill, String text)
	{
		this.metadata = metadata;
		this.bill = bill;
		this.billUrl = bill.getUrl();
		this.text = text;
		this.issueStats = IssueStats.parse(text);
	}
	
	public void setText(String text)
	{
		this.text = text;
		this.issueStats = IssueStats.parse(text);
	}
	
	public void setBill(Bill bill)
	{
		this.bill = bill;
		billUrl = bill.getUrl();
	}
	
	@JsonIgnore
	public String getName()
	{
		if (metadata instanceof OpenAISliceInterpretationMetadata)
		{
			return bill.getName() + "-" + ((OpenAISliceInterpretationMetadata)metadata).getSliceIndex();
		}
		else
		{
			return bill.getName();
		}
	}
}
