package us.poliscore.entrypoint.batch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PartyBillLinker;
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIResponse;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.Party;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillInterpretationParser;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionList;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.model.legislator.LegislatorInterpretationParser;
import us.poliscore.model.session.SessionInterpretation;
import us.poliscore.model.session.SessionInterpretation.PartyInterpretation;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.OpenAIService;
import us.poliscore.service.PartyInterpretationService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.CachedDynamoDbService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;

/**
 * This bulk importer is designed to import a response from the open ai api.
 */
@QuarkusMain(name="BatchOpenAIResponseImporter")
public class BatchOpenAIResponseImporter implements QuarkusApplication
{
	public static final String INPUT = "/Users/rrowlands/dev/projects/poliscore/databuilder/target/unprocessed.jsonl";
	
//	 All Legislators (August 21st)
//	public static final String INPUT = "/Users/rrowlands/Downloads/batch_P8Wsivj5pgknA2QPVrK9KZJI_output.jsonl";
	
	// All Legislators (Aug 5th) 
//	public static final String INPUT = "/Users/rrowlands/Downloads/batch_tUs6UH4XIsYDBjIhbX4Ni9Sq_output.jsonl";
	
//	public static final String INPUT = "/Users/rrowlands/dev/projects/poliscore/databuilder/target/file-NPxmq8zQKACqaSrTnufd6V.jsonl";
	
	@Inject
	private CachedDynamoDbService ddb;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private MemoryObjectService memService;
	
	@Inject
	private PartyInterpretationService partyService;
	
	private Set<String> importedBills = new HashSet<String>();
	
	private SessionInterpretation sessionInterp = null;
	
	@SneakyThrows
	public void process(File input)
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		Log.info("Importing " + input.getAbsolutePath());
		
		@Cleanup BufferedReader reader = new BufferedReader(new FileReader(input));
		String line = reader.readLine();
		
		val erroredLines = new ArrayList<String>();

		while (line != null) {
			try {
				val resp = PoliscoreUtil.getObjectMapper().readValue(line, BatchOpenAIResponse.class);
				
				if (resp.getError() != null || resp.getResponse().getStatus_code() >= 400) {
					String err = "[" + resp.getResponse().getStatus_code() + "] " + resp.getError();
					throw new RuntimeException(err);
				}
				
				if (resp.getCustom_id().startsWith(BillInterpretation.ID_CLASS_PREFIX)) {
					importBill(resp);
				} else if (resp.getCustom_id().startsWith(LegislatorInterpretation.ID_CLASS_PREFIX)) {
					importLegislator(resp);
				} else if (resp.getCustom_id().startsWith(SessionInterpretation.ID_CLASS_PREFIX)) {
					importParty(resp);
				} else {
					throw new UnsupportedOperationException("Unexpected object type " + resp.getCustom_id());
				}
				
				line = reader.readLine();
			} catch (Throwable t) {
				t.printStackTrace();
				erroredLines.add(line);
				line = reader.readLine();
			}
		}
		
		if (sessionInterp != null) {
			s3.put(sessionInterp);
			ddb.put(sessionInterp);
		}
		
		if (erroredLines.size() > 0) {
			File f = new File(Environment.getDeployedPath(), "unprocessed.jsonl");
			FileUtils.write(f, String.join("\n", erroredLines), "UTF-8");
			throw new RuntimeException("Encountered errors on " + erroredLines.size() + " lines. Printed them to " + f.getAbsolutePath());
		}
		
		Log.info("Successfully imported " + input.getAbsolutePath());
	}
	
	private void importLegislator(final BatchOpenAIResponse resp) {
//		if (!resp.getCustom_id().contains("D000197")) return;
		
		val leg = memService.get(resp.getCustom_id().replace(LegislatorInterpretation.ID_CLASS_PREFIX, Legislator.ID_CLASS_PREFIX), Legislator.class).orElseThrow();
		
//		if (ddb.exists(leg.getId(), Legislator.class)) return;
		
		legInterp.updateInteractionsInterp(leg);
		
		LegislatorInterpretation interp = s3.get(LegislatorInterpretation.generateId(leg.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()), LegislatorInterpretation.class)
				.orElse(new LegislatorInterpretation(leg.getId(), leg.getSession(), OpenAIService.metadata(), null));
		
		interp.setHash(legInterp.calculateInterpHashCode(leg));
		
		DoubleIssueStats stats = legInterp.calculateAgregateInteractionStats(leg);
		interp.setIssueStats(stats.toIssueStats());
		
		val interpText = resp.getResponse().getBody().getChoices().get(0).getMessage().getContent();
		new LegislatorInterpretationParser(interp).parse(interpText);
		
		if (interp.getIssueStats() == null || !interp.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety) || StringUtils.isBlank(interp.getLongExplain())) {
			throw new RuntimeException("Unable to parse valid issue stats for legislator " + leg.getId());
		}
		
		s3.put(interp);
		
		leg.setInteractions(legInterp.getInteractionsForInterpretation(leg).stream()
				.filter(i -> i.getIssueStats() != null)
				.sorted((a,b) -> a.getDate().compareTo(b.getDate())).limit(1100).collect(Collectors.toCollection(LegislatorBillInteractionList::new)));
		
		leg.setInterpretation(interp);
		
		legInterp.calculateImpact(leg);
		
		legService.ddbPersist(leg, interp);
	}
	
	private void importParty(final BatchOpenAIResponse resp) {
		if (sessionInterp == null) {
			sessionInterp = partyService.recalculateStats();
		}
		val interpText = resp.getResponse().getBody().getChoices().get(0).getMessage().getContent();
		
		PartyInterpretation partyInterp;
		
		if (resp.getCustom_id().contains(Party.DEMOCRAT.name())) {
			partyInterp = sessionInterp.getDemocrat();
		} else if (resp.getCustom_id().contains(Party.REPUBLICAN.name())) {
			partyInterp = sessionInterp.getRepublican();
		} else if (resp.getCustom_id().contains(Party.INDEPENDENT.name())) {
			partyInterp = sessionInterp.getIndependent();
		} else {
			throw new UnsupportedOperationException();
		}
		
		partyInterp.setLongExplain(interpText);
		PartyBillLinker.linkPartyBillsSinglePass(partyInterp, sessionInterp, memService, s3);
		
		sessionInterp.setMetadata(OpenAIService.metadata());
	}

	private void importBill(final BatchOpenAIResponse resp) {
		String billId = resp.getCustom_id().replace(BillInterpretation.ID_CLASS_PREFIX, Bill.ID_CLASS_PREFIX);
		
		Integer sliceIndex = null;
		if (billId.contains("-")) {
			sliceIndex = Integer.parseInt(billId.split("-")[1]);
			billId = billId.split("-")[0];
		}
		
		val bill = ddb.get(billId, Bill.class).orElseThrow();
		
		BillInterpretation bi = new BillInterpretation();
		bi.setBill(bill);
		
		if (sliceIndex == null)
		{
			bi.setMetadata(OpenAIService.metadata());
			bi.setId(BillInterpretation.generateId(bill.getId(), null));
		}
		else
		{
			val billText = s3.get(BillText.generateId(bill.getId()), BillText.class).orElseThrow();
			bill.setText(billText);
			
			List<BillSlice> slices = new XMLBillSlicer().slice(bill, billText, OpenAIService.MAX_SECTION_LENGTH);
			
			bi.setMetadata(OpenAIService.metadata(slices.get(sliceIndex)));
			bi.setId(BillInterpretation.generateId(billId, sliceIndex));
		}
		
		new BillInterpretationParser(bi).parse(resp.getResponse().getBody().getChoices().get(0).getMessage().getContent());
		
		if (!bi.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety)) {
			if (sliceIndex != null) {throw new RuntimeException("Did not find OverallBenefitToSociety stat on interpretation");  }
			
			val billText = s3.get(BillText.generateId(bill.getId()), BillText.class).orElseThrow();
			
			List<BillSlice> slices = new XMLBillSlicer().slice(bill, billText, OpenAIService.MAX_SECTION_LENGTH);
			
			if (slices.size() <= 1) { throw new RuntimeException("Expected multiple slices on [" + billId + "] since OpenAI did not include benefit to society issue stat"); }
			
			DoubleIssueStats billStats = new DoubleIssueStats();
			List<BillInterpretation> sliceInterps = new ArrayList<BillInterpretation>();
			
			for (int i = 0; i < slices.size(); ++i) {
				val sliceInterp = s3.get(resp.getCustom_id() + "-" + i, BillInterpretation.class).orElseThrow();
				
				billStats = billStats.sum(sliceInterp.getIssueStats().toDoubleIssueStats());
				sliceInterps.add(sliceInterp);
			}
			
			bi.setIssueStats(billStats.divideByTotalSummed().toIssueStats());
			bi.setSliceInterpretations(sliceInterps);
		}
		
		if (StringUtils.isBlank(bi.getLongExplain()) || (sliceIndex == null && (StringUtils.isBlank(bi.getShortExplain()) || bi.getIssueStats() == null || !bi.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety)))) {
			throw new RuntimeException("Interpretation missing proper stats or explain." + billId);
		}
		
		s3.put(bi);
		
		if (sliceIndex == null) {
			billService.ddbPersist(bill, bi);
			
			importedBills.add(billId);
		}
	}
	
	@Override
    public int run(String... args) throws Exception {
        process(new File(INPUT));
        
        Quarkus.waitForExit();
        return 0;
    }
	
	public static void main(String[] args) {
		Quarkus.run(BatchOpenAIResponseImporter.class, args);
		Quarkus.asyncExit(0);
	}
}
