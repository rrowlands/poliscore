package ch.poliscore.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
	
	@JsonIgnore
	public String getId()
	{
		if (bioguideId != null) return getBioguideId();
		if (thomasId != null) return getThomasId();
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
