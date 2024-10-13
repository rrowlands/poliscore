package us.poliscore.entrypoint.batch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIResponse;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.IssueStats;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Party;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillInterpretationParser;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.model.session.SessionInterpretation;
import us.poliscore.model.session.SessionInterpretation.PartyInterpretation;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.OpenAIService;
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
//	public static final String INPUT = "/Users/rrowlands/Downloads/batch_670b2fe22c04819084faed2a01ea772a_output.jsonl";
	
//	 All Legislators (August 21st)
	public static final String INPUT = "/Users/rrowlands/Downloads/batch_P8Wsivj5pgknA2QPVrK9KZJI_output.jsonl";
	
	// All Legislators (Aug 5th) 
//	public static final String INPUT = "/Users/rrowlands/Downloads/batch_tUs6UH4XIsYDBjIhbX4Ni9Sq_output.jsonl";
	
//	public static final String INPUT = "/Users/rrowlands/dev/projects/poliscore/databuilder/target/unprocessed.jsonl";
	
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
	
	private Set<String> importedBills = new HashSet<String>();
	
	public static void main(String[] args) {
		Quarkus.run(BatchOpenAIResponseImporter.class, args);
	}
	
	@SneakyThrows
	public void process(File input)
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		System.out.println("Importing " + input.getAbsolutePath());
		
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
		
		if (erroredLines.size() > 0) {
			File f = new File(Environment.getDeployedPath(), "unprocessed.jsonl");
			FileUtils.write(f, String.join("\n", erroredLines), "UTF-8");
			System.out.println("Encountered errors on " + erroredLines.size() + " lines. Printed them to " + f.getAbsolutePath());
		}
		
		// These indexes can't be created here because they might not have all the required data
//		legService.generateLegislatorWebappIndex();
//		billService.generateBillWebappIndex();
		
		System.out.println("Program complete.");
	}
	
	private void importLegislator(final BatchOpenAIResponse resp) {
//		if (!resp.getCustom_id().contains("D000197")) return;
		
		val leg = memService.get(resp.getCustom_id().replace(LegislatorInterpretation.ID_CLASS_PREFIX, Legislator.ID_CLASS_PREFIX), Legislator.class).orElseThrow();
		
//		if (ddb.exists(leg.getId(), Legislator.class)) return;
		
		for (val i : legInterp.getInteractionsForInterpretation(leg))
		{
			val interp = s3.get(BillInterpretation.generateId(i.getBillId(), null), BillInterpretation.class);
			
			if (interp.isPresent()) {
				i.setIssueStats(interp.get().getIssueStats());
				
				val bill = memService.get(i.getBillId(), Bill.class).orElseThrow();
				bill.setInterpretation(interp.get());
				i.setBillName(bill.getName());
//				ddb.put(i);
				
//				if (!importedBills.contains(i.getBillId())) {
//					ddb.put(bill);
//					importedBills.add(bill.getId());
//				}
			}
		}
		
		DoubleIssueStats doubleStats = new DoubleIssueStats();
		
		val interacts = new LegislatorBillInteractionSet();
		for (val interact : legInterp.getInteractionsForInterpretation(leg))
		{
			if (interact.getIssueStats() != null)
			{
				val weightedStats = interact.getIssueStats().multiply(interact.getJudgementWeight());
				doubleStats = doubleStats.sum(weightedStats, Math.abs(interact.getJudgementWeight()));
				
				interacts.add(interact);
			}
		}
		
		doubleStats = doubleStats.divideByTotalSummed();
		IssueStats stats = doubleStats.toIssueStats();
		
		val interp = new LegislatorInterpretation(OpenAIService.metadata(), leg, stats);
		interp.setHash(legInterp.calculateInterpHashCode(leg));
		
		val interpText = resp.getResponse().getBody().getChoices().get(0).getMessage().getContent();
//		new LegislatorInterpretationParser(interp).parse(interpText);
		interp.setLongExplain(interpText);
		
		interp.setIssueStats(stats);
		
		if (interp.getIssueStats() == null || !interp.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety) || StringUtils.isBlank(interp.getLongExplain())) {
			throw new RuntimeException("Unable to parse valid issue stats for legislator " + leg.getId());
		}
		
		s3.put(interp);
		
		// 1100 seems to be about an upper limit for a single ddb page
		leg.setInteractions(interacts.stream().sorted((a,b) -> a.getDate().compareTo(b.getDate())).limit(1100).collect(Collectors.toCollection(LegislatorBillInteractionSet::new)));
//		leg.setInteractions(interacts);
		
		leg.setInterpretation(interp);
		
		ddb.put(leg);
	}
	
	private void importParty(final BatchOpenAIResponse resp) {
		val interp = ddb.get(SessionInterpretation.generateId(PoliscoreUtil.SESSION.getNumber()), SessionInterpretation.class).get();
		
		val interpText = resp.getResponse().getBody().getChoices().get(0).getMessage().getContent();
		
		PartyInterpretation partyInterp;
		
		if (resp.getCustom_id().contains(Party.DEMOCRAT.name())) {
			partyInterp = interp.getDemocrat();
		} else if (resp.getCustom_id().contains(Party.REPUBLICAN.name())) {
			partyInterp = interp.getRepublican();
		} else if (resp.getCustom_id().contains(Party.INDEPENDENT.name())) {
			partyInterp = interp.getIndependent();
		} else {
			throw new UnsupportedOperationException();
		}
		
		partyInterp.setLongExplain(interpText);
		linkPartyBills(partyInterp);
		
		interp.setMetadata(OpenAIService.metadata());
		
		ddb.put(interp);
	}
	
	private void linkPartyBills(PartyInterpretation interp) {
		try
		{
			var exp = interp.getLongExplain();
			
			for (val bill : memService.query(Bill.class)) {
				val bi = s3.get(BillInterpretation.generateId(bill.getId(), null), BillInterpretation.class);
				
				if (bi.isPresent()) {
					bill.setInterpretation(bi.get());
					val id = bill.getId();
					var billName = bill.getName();
					
					val url = "/bill" + id.replace(Bill.ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace(), "");
					
					if (billName.endsWith(".")) billName = billName.substring(0, billName.length() - 1);
					billName = billName.strip();
					if (billName.endsWith("."))
						billName = billName.substring(0, billName.length() - 1);
					
					val billId = Bill.billTypeFromId(id).getName() + "-" + Bill.billNumberFromId(id);
					
					val billMatchPattern = "(" + Pattern.quote(billName) + "|" + Pattern.quote(billId) + ")[^\\d]";
					
					Pattern pattern = Pattern.compile("(?i)" + billMatchPattern + "", Pattern.CASE_INSENSITIVE);
				    Matcher matcher = pattern.matcher(exp);
				    while (matcher.find()) {
				    	exp = exp.replaceFirst(matcher.group(1), "<a href=\"" + url + "\" >" + billName + "</a>");
				    }
				}
			}
			
			interp.setLongExplain(exp);
		} catch (Throwable t) {
			Log.error(t);
		}
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
		
		new BillInterpretationParser(bi).parse(resp.getResponse().getBody().getChoices().get(0).getMessage().getContent());
		
		if (!bi.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety)) {
			if (sliceIndex != null) {throw new RuntimeException("Did not find OverallBenefitToSociety stat on interpretation");  }
			
			val billText = s3.get(BillText.generateId(bill.getId()), BillText.class).orElseThrow();
			
			List<BillSlice> slices = new XMLBillSlicer().slice(bill, billText, BillSlicer.MAX_SECTION_LENGTH);
			
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
			bill.setInterpretation(bi);
			ddb.put(bill);
			
			importedBills.add(billId);
		}
	}
	
	@Override
    public int run(String... args) throws Exception {
        process(new File(INPUT));
        
        Quarkus.waitForExit();
        return 0;
    }
}
