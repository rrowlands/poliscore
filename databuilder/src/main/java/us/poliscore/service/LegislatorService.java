package us.poliscore.service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate; // Added import
import java.util.List; // Already present
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession; // Added import
import us.poliscore.model.LegislativeChamber; // Added import
import us.poliscore.model.LegislativeNamespace; // Added import
import us.poliscore.model.Party; // Added import
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorLegislativeTermSortedSet;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.model.legislator.LegislatorIssueStat;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.MemoryObjectService;
// import us.poliscore.view.USCLegislatorView; // Commented out as it's related to old logic
import us.poliscore.view.legiscan.LegiscanLegislatorView; // Added import

@ApplicationScoped
public class LegislatorService {
	
	@Inject
	private MemoryObjectService memService;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	@SneakyThrows
	public void importLegislators(List<LegiscanLegislatorView> legislators)
	{
		if (legislators == null || legislators.isEmpty()) {
			Log.info("No legislators to import from Legiscan.");
			return;
		}
		Log.info("Importing " + legislators.size() + " legislators from Legiscan for session " + PoliscoreUtil.currentSessionNumber + " in namespace " + PoliscoreUtil.currentNamespace);
		int count = 0;
		for (LegiscanLegislatorView view : legislators) {
			Legislator leg = new Legislator();
			leg.setLegiscanId(view.getLegis_id());
			leg.setBioguideId(view.getBioguide_id()); // May be null

			Legislator.LegislatorName name = new Legislator.LegislatorName();
			name.setFirst(view.getFirst_name());
			name.setLast(view.getLast_name());
			name.setOfficial_full(view.getFirst_name() + " " + view.getLast_name());
			leg.setName(name);

			// Placeholder for birthday as LegiscanLegislatorView does not have getBirthday()
			leg.setBirthday(LocalDate.of(1970, 1, 1));

			leg.setSession(PoliscoreUtil.currentSessionNumber);

			Legislator.LegislatorLegislativeTermSortedSet terms = new Legislator.LegislatorLegislativeTermSortedSet();
			Legislator.LegislativeTerm term = new Legislator.LegislativeTerm();

			if (PoliscoreUtil.currentNamespace == LegislativeNamespace.US_CONGRESS && PoliscoreUtil.currentSessionNumber != null) {
				CongressionalSession cs = CongressionalSession.of(PoliscoreUtil.currentSessionNumber);
				term.setStartDate(cs.getStartDate());
				term.setEndDate(cs.getEndDate());
			} else {
				// Placeholder for state session dates
				term.setStartDate(LocalDate.of(PoliscoreUtil.currentSessionNumber != null ? PoliscoreUtil.currentSessionNumber : LocalDate.now().getYear(), 1, 1));
				term.setEndDate(LocalDate.of(PoliscoreUtil.currentSessionNumber != null ? PoliscoreUtil.currentSessionNumber : LocalDate.now().getYear(), 12, 31));
			}
			term.setState(view.getState());
			try {
				if (view.getDistrict() != null && !view.getDistrict().isEmpty() && !view.getDistrict().equalsIgnoreCase("null")) {
					term.setDistrict(Integer.parseInt(view.getDistrict()));
				}
			} catch (NumberFormatException e) {
				Log.warn("Could not parse district for legislator " + view.getLegis_id() + ": " + view.getDistrict());
			}
			term.setParty(Party.fromName(view.getParty()));

			if (PoliscoreUtil.currentNamespace == LegislativeNamespace.US_CONGRESS) {
				term.setChamber(term.getDistrict() != null ? LegislativeChamber.HOUSE : LegislativeChamber.SENATE);
			} else {
				term.setChamber(LegislativeChamber.UNICAMERAL); // Placeholder for states
			}
			terms.add(term);
			leg.setTerms(terms);

			leg.setPhotoUrl(view.getPhoto_url()); // Set the photoUrl

			memService.put(leg);
			count++;
		}
		Log.info("Successfully imported " + count + " legislators into memory.");
	}

	// private void importUSCJson(String file) throws IOException, JsonProcessingException { // Old logic
	// 	int count = 0;
		
	// 	ObjectMapper mapper = PoliscoreUtil.getObjectMapper();
	// 	JsonNode jn = mapper.readTree(LegislatorService.class.getResourceAsStream(file));
	// 	Iterator<JsonNode> it = jn.elements();
	// 	while (it.hasNext())
	// 	{
	// 		USCLegislatorView view = mapper.treeToValue(it.next(), USCLegislatorView.class);
			
	// 		Legislator leg = new Legislator();
	// 		leg.setName(view.getName().convert());
	// 		leg.setBioguideId(view.getId().getBioguide());
	// 		leg.setThomasId(view.getId().getThomas());
	// 		leg.setLisId(view.getId().getLis());
	// 		leg.setWikidataId(view.getId().getWikidata());
	// 		leg.setBirthday(view.getBio().getBirthday());
	// 		leg.setTerms(view.getTerms().stream().map(t -> t.convert()).collect(Collectors.toCollection(LegislatorLegislativeTermSortedSet::new)));
			
	// 		if (leg.isMemberOfSession(PoliscoreUtil.currentSessionNumber)) // Updated to use currentSessionNumber
	// 		{
	// 			leg.setSession(PoliscoreUtil.currentSessionNumber); // Updated to use currentSessionNumber
				
	// 			memService.put(leg);
	// 			count++;
	// 		}
	// 	}
		
	// 	Log.info("Imported " + count + " politicians");
	// }
	
	public Optional<Legislator> getById(String id)
	{
		return memService.get(id, Legislator.class);
	}
	
	public void ddbPersist(Legislator leg, LegislatorInterpretation interp)
	{
		leg.setInterpretation(interp);
		ddb.put(leg);
		
		if (legInterp.meetsInterpretationPrereqs(leg))
		{
			for(TrackedIssue issue : TrackedIssue.values()) {
				ddb.put(new LegislatorIssueStat(issue, leg.getImpact(issue), leg));
			}
		}
	}

	@SneakyThrows
	public void generateLegislatorWebappIndex() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/resources/legislators.index");
		
		val uniqueSet = new HashMap<String, Legislator>();
		
		memService.queryAll(Legislator.class).stream()
			// .filter(l -> PoliscoreUtil.SUPPORTED_CONGRESSES.stream().anyMatch(s -> l.isMemberOfSession(s))) // Old filter
			.filter(l -> l.getSession() != null && l.getSession().equals(PoliscoreUtil.currentSessionNumber)) // New filter
			.forEach(l -> {
				// Use Legiscan ID for states if Bioguide ID is null, otherwise Bioguide ID for US Congress
				String keyId = (PoliscoreUtil.currentNamespace != LegislativeNamespace.US_CONGRESS && l.getLegiscanId() != null) ?
								 l.getLegiscanId() : l.getBioguideId();
				if (keyId == null) return; // Skip if no suitable ID

				if (!uniqueSet.containsKey(keyId) ||
					(uniqueSet.containsKey(keyId) && uniqueSet.get(keyId).getSession() < l.getSession())) {
					uniqueSet.put(keyId, l);
				}
			});
		
		val data = uniqueSet.values().stream()
			.map(l -> Arrays.asList(l.getId(),l.getName().getOfficial_full()))
			.sorted((a,b) -> a.get(1).compareTo(b.get(1)))
			.toList();
		
		FileUtils.write(out, PoliscoreUtil.getObjectMapper().writeValueAsString(data), "UTF-8");
	}
}
