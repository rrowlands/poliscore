package us.poliscore.service;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Legislator;
import us.poliscore.service.storage.MemoryPersistenceService;
import us.poliscore.view.USCLegislatorView;

@ApplicationScoped
public class LegislatorService {
	
	@Inject
	private MemoryPersistenceService pServ;
	
	@SneakyThrows
	public void importLegislators()
	{
		importUSCJson("/legislators-current.json");
		importUSCJson("/legislators-historical.json");
	}

	private void importUSCJson(String file) throws IOException, JsonProcessingException {
		int count = 0;
		
		ObjectMapper mapper = PoliscoreUtil.getObjectMapper();
		JsonNode jn = mapper.readTree(LegislatorService.class.getResourceAsStream(file));
		Iterator<JsonNode> it = jn.elements();
		while (it.hasNext())
		{
			USCLegislatorView view = mapper.treeToValue(it.next(), USCLegislatorView.class);
			
			Legislator leg = new Legislator();
			leg.setName(view.getName().convert());
			leg.setBioguideId(view.getId().getBioguide());
			leg.setThomasId(view.getId().getThomas());
			leg.setWikidataId(view.getId().getWikidata());
			
			pServ.store(leg);
			count++;
		}
		
		Log.info("Imported " + count + " politicians");
	}
	
	public Optional<Legislator> getById(String id)
	{
		return pServ.retrieve(id, Legislator.class);
	}

	public void persist(Legislator leg)
	{
		pServ.store(leg);
	}
	
}
