package ch.poliscore.model;

import java.util.HashSet;
import java.util.Set;

import ch.poliscore.service.storage.DynamoDBPersistenceService;
import ch.poliscore.view.USCLegislatorView;
import io.quarkus.amazon.dynamodb.enhanced.runtime.NamedDynamoDbTable;
import jakarta.inject.Inject;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@DynamoDbBean
@NoArgsConstructor
public class Legislator implements Persistable {
	
	@NonNull
	protected USCLegislatorView.USCLegislatorName name;
	
	protected String bioguideId;
	
	protected String thomasId;
	
	protected String wikidataId;
	
	protected LegislatorInterpretation interpretation;
	
	@NonNull
	protected Set<LegislatorBillInteration> interactions = new HashSet<LegislatorBillInteration>();
	
	@DynamoDbPartitionKey
	public String getId()
	{
		String namespace = LegislativeNamespace.US_CONGRESS.getNamespace();
		
		if (bioguideId != null) return namespace + "/" + getBioguideId();
		if (thomasId != null) return namespace + "/" + getThomasId();
		
		throw new NullPointerException();
	}
	
	public void addBillInteraction(LegislatorBillInteration incoming)
	{
		interactions.removeIf(existing -> incoming.supercedes(existing));
		interactions.add(incoming);
	}
	
}
