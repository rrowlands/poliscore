package us.poliscore.entrypoint.batch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIResponse;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.Legislator;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillVote;
import us.poliscore.model.LegislatorInterpretation;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.VoteStatus;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.OpenAIService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.CachedDynamoDbService;
import us.poliscore.service.storage.CachedS3Service;
import us.poliscore.service.storage.MemoryPersistenceService;

/**
 * This bulk importer is designed to import a response from the open ai api.
 */
@QuarkusMain(name="BatchOpenAIResponseImporter")
public class BatchOpenAIResponseImporter implements QuarkusApplication
{
//	public static final String INPUT = "/Users/rrowlands/Downloads/batch_EE9hU5FbmkmIfMWfhwRboF1t_output.jsonl";
	
	public static final String INPUT = "/Users/rrowlands/dev/projects/poliscore/databuilder/target/unprocessed.jsonl";
	
	@Inject
	private CachedDynamoDbService ddb;
	
	@Inject
	private CachedS3Service s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private MemoryPersistenceService memService;
	
	private Set<String> importedBills = new HashSet<String>();
	
	public static void main(String[] args) {
		Quarkus.run(BatchOpenAIResponseImporter.class, args);
	}
	
	@SneakyThrows
	protected void process()
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		@Cleanup BufferedReader reader = new BufferedReader(new FileReader(INPUT));
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
		
		if (erroredLines.size() > 0) {
			File f = new File(Environment.getDeployedPath(), "unprocessed.jsonl");
			FileUtils.write(f, String.join("\n", erroredLines), "UTF-8");
			System.out.println("Encountered errors on " + erroredLines.size() + " lines. Printed them to " + f.getAbsolutePath());
		}
		
		legService.generateLegislatorWebappIndex();
		billService.generateBillWebappIndex();
		
		System.out.println("Program complete.");
	}
	
	private void importLegislator(final BatchOpenAIResponse resp) {
//		if (!resp.getCustom_id().contains("N000147")) return;
		
		val leg = memService.get(resp.getCustom_id().replace(LegislatorInterpretation.ID_CLASS_PREFIX, Legislator.ID_CLASS_PREFIX), Legislator.class).orElseThrow();
		
//		if (ddb.exists(leg.getId(), Legislator.class)) return;
		
		for (val i : legInterp.getInteractionsForInterpretation(leg))
		{
			val interp = s3.get(BillInterpretation.generateId(i.getBillId(), null), BillInterpretation.class);
			
			if (interp.isPresent()) {
				i.setIssueStats(interp.get().getIssueStats());
				
				if (!importedBills.contains(i.getBillId())) {
					val bill = memService.get(i.getBillId(), Bill.class).orElseThrow();
					bill.setInterpretation(interp.get());
					ddb.put(bill);
					importedBills.add(bill.getId());
				}
			}
		}
		
		IssueStats stats = new IssueStats();
		
		val interacts = new LegislatorBillInteractionSet();
		for (val interact : legInterp.getInteractionsForInterpretation(leg))
		{
			if (interact.getIssueStats() != null)
			{
				val weightedStats = interact.getIssueStats().multiply(interact.getJudgementWeight());
				stats = stats.sum(weightedStats, Math.abs(interact.getJudgementWeight()));
				
				interacts.add(interact);
			}
		}
		
		stats = stats.divideByTotalSummed();
		
		val interpText = resp.getResponse().getBody().getChoices().get(0).getMessage().getContent();
		stats.setExplanation(interpText);
		
		val interp = new LegislatorInterpretation(OpenAIService.metadata(), leg, stats);
		interp.setHash(legInterp.calculateInterpHashCode(leg));
		s3.put(interp);
		
		// 1200 seems to be about an upper limit for a single ddb page
		leg.setInteractions(interacts.stream().sorted((a,b) -> a.getDate().compareTo(b.getDate())).limit(1200).collect(Collectors.toCollection(LegislatorBillInteractionSet::new)));
//		leg.setInteractions(interacts);
		
		leg.setInterpretation(interp);
		
		ddb.put(leg);
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
			
			List<BillSlice> slices = new XMLBillSlicer().slice(bill, billText, BillSlicer.MAX_SECTION_LENGTH);
			
			bi.setMetadata(OpenAIService.metadata(slices.get(sliceIndex)));
			bi.setId(BillInterpretation.generateId(billId, sliceIndex));
		}
		
		val respStats = IssueStats.parse(resp.getResponse().getBody().getChoices().get(0).getMessage().getContent());
		
		if (!respStats.hasStat(TrackedIssue.OverallBenefitToSociety)) {
			if (sliceIndex != null) {throw new RuntimeException("Did not find OverallBenefitToSociety stat on interpretation");  }
			
			val billText = s3.get(BillText.generateId(bill.getId()), BillText.class).orElseThrow();
			
			List<BillSlice> slices = new XMLBillSlicer().slice(bill, billText, BillSlicer.MAX_SECTION_LENGTH);
			
			if (slices.size() <= 1) { throw new RuntimeException("Expected multiple slices on [" + billId + "] since OpenAI did not include benefit to society issue stat"); }
			
			IssueStats billStats = new IssueStats();
			List<BillInterpretation> sliceInterps = new ArrayList<BillInterpretation>();
			
			for (int i = 0; i < slices.size(); ++i) {
				val sliceInterp = s3.get(resp.getCustom_id() + "-" + i, BillInterpretation.class).orElseThrow();
				
				billStats = billStats.sum(sliceInterp.getIssueStats());
				sliceInterps.add(sliceInterp);
			}
			
			bi.setIssueStats(billStats.divideByTotalSummed());
			bi.setSliceInterpretations(sliceInterps);
			bi.getIssueStats().setExplanation(resp.getResponse().getBody().getChoices().get(0).getMessage().getContent());
		} else {
			bi.setIssueStats(respStats);
		}
		
		s3.put(bi);
		
		bill.setInterpretation(bi);
		ddb.put(bill);
		
		importedBills.add(billId);
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
