package ch.poliscore.service;

import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.model.Legislator;
import ch.poliscore.view.USCLegislatorView;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;

@ApplicationScoped
public class LegislatorService {
	
	@Inject
	private PersistenceServiceIF pServ;
	
	@SneakyThrows
	public void importLegislators()
	{
		int count = 0;
		
		ObjectMapper mapper = new ObjectMapper();
		JsonNode jn = mapper.readTree(LegislatorService.class.getResourceAsStream("/legislators-current.json"));
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
	
	public Legislator getById(String id)
	{
		return pServ.retrieve(id, Legislator.class);
	}

	public void persist(Legislator leg)
	{
		pServ.store(leg);
	}
	
}
