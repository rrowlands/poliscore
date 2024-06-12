package ch.poliscore.model;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.view.USCLegislatorView;
import lombok.Data;

@Data
public class Legislator implements Persistable {
	
	protected USCLegislatorView.USCLegislatorName name;
	
	protected String bioguideId;
	
	protected String thomasId;
	
	protected String wikidataId;
	
	protected Set<LegislatorBillInteration> interactions = new HashSet<LegislatorBillInteration>();
	
	@JsonIgnore
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
