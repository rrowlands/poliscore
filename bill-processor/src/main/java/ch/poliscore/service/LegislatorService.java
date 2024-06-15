package ch.poliscore.service;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.DataNotFoundException;
import ch.poliscore.IssueStats;
import ch.poliscore.model.Legislator;
import ch.poliscore.view.USCLegislatorView;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;

@ApplicationScoped
public class LegislatorService {
	
	@Inject
	private PersistenceServiceIF pServ;
	
	@SneakyThrows
	public void importLegislators()
	{
		importUSCJson("/legislators-current.json");
		importUSCJson("/legislators-historical.json");
	}

	private void importUSCJson(String file) throws IOException, JsonProcessingException {
		int count = 0;
		
		ObjectMapper mapper = new ObjectMapper();
		JsonNode jn = mapper.readTree(LegislatorService.class.getResourceAsStream(file));
		Iterator<JsonNode> it = jn.elements();
		while (it.hasNext())
		{
			USCLegislatorView view = mapper.treeToValue(it.next(), USCLegislatorView.class);
			
			Legislator leg = new Legislator();
			leg.setName(view.getName());
			leg.setBioguideId(view.getId().getBioguide());
			leg.setThomasId(view.getId().getThomas());
			leg.setWikidataId(view.getId().getWikidata());
			
			pServ.store(leg);
			count++;
		}
		
		Log.info("Imported " + count + " politicians");
	}
	
	public Legislator getById(String id) throws DataNotFoundException
	{
		return pServ.retrieve(id, Legislator.class);
	}

	public void persist(Legislator leg)
	{
		pServ.store(leg);
	}

	public void interpret(Legislator leg)
	{
		IssueStats stats = new IssueStats();
		
		for (val interact : leg.getInteractions())
		{
			if (interact.getIssueStats() != null)
			{
				interact.getIssueStats().multiply(interact.getJudgementWeight());
				stats.sum(interact.getIssueStats());
			}
		}
		
		stats.divide(leg.getInteractions().size());
		
		leg.setIssueStats(stats);
	}
	
}
