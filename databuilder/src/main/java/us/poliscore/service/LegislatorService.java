package us.poliscore.service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorLegislativeTermSortedSet;
import us.poliscore.service.storage.MemoryPersistenceService;
import us.poliscore.view.USCLegislatorView;

@ApplicationScoped
public class LegislatorService {
	
	@Inject
	private MemoryPersistenceService memService;
	
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
			leg.setBirthday(view.getBio().getBirthday());
			leg.setTerms(view.getTerms().stream().map(t -> t.convert()).collect(Collectors.toCollection(LegislatorLegislativeTermSortedSet::new)));
			
			memService.put(leg);
			count++;
		}
		
		Log.info("Imported " + count + " politicians");
	}
	
	public Optional<Legislator> getById(String id)
	{
		return memService.get(id, Legislator.class);
	}

	@SneakyThrows
	public void generateLegislatorWebappIndex() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/resources/legislators.index");
		
		val data = memService.query(Legislator.class).stream()
			.filter(l -> l.isMemberOfSession(CongressionalSession.S118))
			.map(l -> Arrays.asList(l.getBioguideId(),l.getName().getOfficial_full()))
			.sorted((a,b) -> a.get(1).compareTo(b.get(1)))
			.toList();
		
		FileUtils.write(out, PoliscoreUtil.getObjectMapper().writeValueAsString(data), "UTF-8");
	}
}
