package ch.poliscore.model;

import java.util.HashMap;
import java.util.Map;

import ch.poliscore.legislator.VotingData;
import ch.poliscore.view.USCLegislatorView;
import lombok.Data;

@Data
public class Legislator implements Persistable {
	
	protected USCLegislatorView.USCLegislatorName name;
	
	protected String bioguideId;
	
	protected String thomasId;
	
	protected String wikidataId;
	
	protected Map<String, VotingData> votingData = new HashMap<String, VotingData>();
	
	public String getId()
	{
		if (bioguideId != null) return bioguideId;
		if (thomasId != null) return thomasId;
		return null;
	}
	
	public void addVotingData(VotingData data)
	{
		if (!votingData.containsKey(data.getBillId()))
		{
			votingData.put(data.getBillId(), data);
		}
		else
		{
			var existing = votingData.get(data.getBillId());
			
			if (data.getDate().after(existing.getDate()))
			{
				votingData.put(data.getBillId(), data);
			}
		}
	}
	
}
