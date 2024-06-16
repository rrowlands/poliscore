package ch.poliscore.model;

import java.util.HashSet;
import java.util.Set;

import ch.poliscore.view.USCLegislatorView;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@DynamoDbBean
public class Legislator implements Persistable {
	
	protected USCLegislatorView.USCLegislatorName name;
	
	protected String bioguideId;
	
	protected String thomasId;
	
	protected String wikidataId;
	
	protected LegislatorInterpretation interpretation;
	
	protected Set<LegislatorBillInteration> interactions = new HashSet<LegislatorBillInteration>();
	
	@DynamoDbPartitionKey
	public String getId()
	{
		if (bioguideId != null) return getBioguideId();
		if (thomasId != null) return getThomasId();
		return null;
	}
	
	public void addBillInteraction(LegislatorBillInteration incoming)
	{
		interactions.removeIf(existing -> incoming.supercedes(existing));
		interactions.add(incoming);
	}
	
}
