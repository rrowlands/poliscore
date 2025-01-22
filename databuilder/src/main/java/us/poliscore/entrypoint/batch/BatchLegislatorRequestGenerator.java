package us.poliscore.entrypoint.batch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIRequest;
import us.poliscore.ai.BatchOpenAIRequest.BatchBillMessage;
import us.poliscore.ai.BatchOpenAIRequest.BatchOpenAIBody;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.MemoryObjectService;
import us.poliscore.service.storage.S3PersistenceService;

/**
 * Generates a bulk request to open ai for all legislators 
 */
@QuarkusMain(name="BatchLegislatorRequestGenerator")
public class BatchLegislatorRequestGenerator implements QuarkusApplication
{
	public static final long TOKEN_BLOCK_SIZE = 30000000;
	
	public static final boolean CHECK_S3_EXISTS = false;
	
	@Inject
	private MemoryObjectService memService;
	
	@Inject
	private S3PersistenceService s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	private long tokenLen = 0;
	
	private long totalRequests = 0;
	
	private List<BatchOpenAIRequest> requests = new ArrayList<BatchOpenAIRequest>();
	
	private List<File> writtenFiles = new ArrayList<File>();
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(BatchLegislatorRequestGenerator.class, args);
	}
	
	public List<File> process() throws IOException
	{
		Log.info("Generating batch request to interpret legislators");
		
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		int block = 1;
		
//		s3.optimizeExists(LegislatorInterpretation.class);
		
		for (Legislator l : memService.query(Legislator.class).stream()
				.filter(l -> 
					l.getInteractions().size() > 0
//					&& (l.getBioguideId().equals("R000614"))
					&& (!CHECK_S3_EXISTS || !s3.exists(LegislatorInterpretation.generateId(l.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()), LegislatorInterpretation.class))
				)
				.sorted(Comparator.comparing(Legislator::getDate).reversed())
//				.limit(1)
				.toList()) {
			interpret(l);
			
			if (tokenLen >= TOKEN_BLOCK_SIZE) {
				writeBlock(block++);
			}
		};
		
		writeBlock(block++);
		
		Log.info("Batch legislator request generator complete. Generated " + totalRequests + " requests.");
		
		return writtenFiles;
	}
	
	protected void interpret(Legislator leg)
	{
		legInterp.updateInteractionsInterp(leg);
		
		DoubleIssueStats stats = legInterp.calculateAgregateInteractionStats(leg);
		
		List<String> billMsgs = new ArrayList<String>();
		Set<String> includedBills = new HashSet<String>();
		
		if (stats.getLetterGrade().equals("F")) {
			// If they got an F we just want to roast them as hard as we can. List their worst bills.
			includeBillsByGrade(leg, billMsgs, includedBills, 20, true);
		} else {
			// Start with top most impactful bills
			billMsgs.add("Legislator's Most Impactful Bills:");
			if (stats.getLetterGrade().equals("A") || stats.getLetterGrade().equals("B"))
				includeBillsByImpact(leg, billMsgs, includedBills, 20, false);
			else if (stats.getLetterGrade().equals("C")) {
				includeBillsByImpact(leg, billMsgs, includedBills, 13, false);
				includeBillsByImpact(leg, billMsgs, includedBills, 7, true);
			} else if (stats.getLetterGrade().equals("D")) {
				includeBillsByImpact(leg, billMsgs, includedBills, 7, false);
				includeBillsByImpact(leg, billMsgs, includedBills, 13, true);
			} else
				includeBillsByImpact(leg, billMsgs, includedBills, 20, true);
		}
			
		
		// Include the top bills which explain the legislator's top scoring issues.
		if (stats.getLetterGrade().equals("A") || stats.getLetterGrade().equals("B"))
			includeBillsByTopIssues(leg, stats, billMsgs, includedBills, 3, false);
		else if (stats.getLetterGrade().equals("C")) {
			includeBillsByTopIssues(leg, stats, billMsgs, includedBills, 2, false);
			includeBillsByTopIssues(leg, stats, billMsgs, includedBills, 1, true);
		} else if (stats.getLetterGrade().equals("D")) {
			includeBillsByTopIssues(leg, stats, billMsgs, includedBills, 1, false);
			includeBillsByTopIssues(leg, stats, billMsgs, includedBills, 2, true);
		} else
			includeBillsByTopIssues(leg, stats, billMsgs, includedBills, 3, true);
		
		if (includedBills.size() == 0)
			return;
		
		createRequest(LegislatorInterpretation.generateId(leg.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()), LegislatorInterpretationService.getAiPrompt(leg, stats.toIssueStats()), String.join("\n", billMsgs));
	}

	private void includeBillsByTopIssues(Legislator leg, DoubleIssueStats stats, List<String> billMsgs, Set<String> includedBills, int amount, boolean ascending) {
		
		var issues = Arrays.asList(TrackedIssue.values()).stream().filter(i -> !i.equals(TrackedIssue.OverallBenefitToSociety));
		
		if (ascending)
			issues = issues.sorted(Comparator.comparingInt(i -> (int)Math.round(stats.getStat(i))));
		else
			issues = issues.sorted(Comparator.comparingInt((TrackedIssue i) -> (int)Math.round(stats.getStat(i))).reversed());
		
		for (val issue : issues.limit(amount).collect(Collectors.toList()))
		{
			billMsgs.add("\nLargest Contributors To \"" + issue.getName() + "\" Score:");
			
			if (stats.getLetterGrade(issue).equals("A") || stats.getLetterGrade(issue).equals("B"))
				includeBillsByIssue(leg, billMsgs, includedBills, issue, 20, false);
			else if (stats.getLetterGrade(issue).equals("C")) {
				includeBillsByIssue(leg, billMsgs, includedBills, issue, 10, false);
				includeBillsByIssue(leg, billMsgs, includedBills, issue, 10, true);
			} else if (stats.getLetterGrade(issue).equals("D")) {
				includeBillsByIssue(leg, billMsgs, includedBills, issue, 13, true);
				includeBillsByIssue(leg, billMsgs, includedBills, issue, 7, false);
			} else
				includeBillsByIssue(leg, billMsgs, includedBills, issue, 20, true);
		}
	}

	private void includeBillsByIssue(final Legislator leg, List<String> billMsgs, Set<String> includedBills, final TrackedIssue issue, int amount, boolean ascending) {
		var interacts = legInterp.getInteractionsForInterpretation(leg).stream().filter(i -> i.getIssueStats() != null && i.getIssueStats().hasStat(issue));
		if (ascending)
			interacts = interacts.sorted(Comparator.comparingInt(i -> Math.round(i.getRating(issue) + i.getStatusProgress()*25f*i.getJudgementWeight())));
//			interacts = interacts.sorted(Comparator.comparingInt(i -> i.getImpact(issue)));
		else
			interacts = interacts.sorted(Comparator.comparingInt((LegislatorBillInteraction i) -> Math.round(i.getRating(issue) + i.getStatusProgress()*25f*i.getJudgementWeight())).reversed());
//			interacts = interacts.sorted(Comparator.comparingInt((LegislatorBillInteraction i) -> i.getImpact(issue)).reversed());
		
		for (val interact : interacts.limit(amount).collect(Collectors.toList()))
		{
			val bill = memService.get(interact.getBillId(), Bill.class).orElseThrow();
			
			String billMsg = "- " + interact.describe() + " \"" + interact.getBillName() + "\" (" + bill.getStatus().getDescription() + "): " + interact.getShortExplain();
			
			if ( (String.join("\n", billMsgs) + "\n" + billMsg).length() < BillSlicer.MAX_SECTION_LENGTH ) {
				billMsgs.add(billMsg);
				includedBills.add(interact.getBillId());
			} else {
				break;
			}
		}
	}
	
	private void includeBillsByGrade(Legislator leg, List<String> billMsgs, Set<String> includedBills, int amount, boolean ascending) {
		billMsgs.add("Legislator's " + (ascending ? "Worst" : "Best") + " Bills:");
		
		var billsByGrade = legInterp.getInteractionsForInterpretation(leg).stream().filter(i -> i.getIssueStats() != null);
		
		if (ascending)
			billsByGrade = billsByGrade.sorted(Comparator.comparingInt(LegislatorBillInteraction::getRating));
		else
			billsByGrade = billsByGrade.sorted(Comparator.comparingInt(LegislatorBillInteraction::getOverallRating).reversed());
		
		for (val interact : billsByGrade.limit(amount).collect(Collectors.toList()))
		{
			val bill = memService.get(interact.getBillId(), Bill.class).orElseThrow();
			val billMsg = "- " + interact.describe() + " \"" + interact.getBillName() + "\" (" + bill.getStatus().getDescription() + "): " + interact.getShortExplain();
			if ( (String.join("\n", billMsgs) + "\n" + billMsg).length() < BillSlicer.MAX_SECTION_LENGTH ) {
				billMsgs.add(billMsg);
				includedBills.add(interact.getBillId());
			} else {
				break;
			}
		}
	}

	private void includeBillsByImpact(Legislator leg, List<String> billMsgs, Set<String> includedBills, int amount, boolean ascending) {
		var billsByImpact = legInterp.getInteractionsForInterpretation(leg).stream().filter(i -> i.getIssueStats() != null);
		
		if (ascending)
			billsByImpact = billsByImpact.sorted(Comparator.comparing(LegislatorBillInteraction::getOverallImpact));
		else
			billsByImpact = billsByImpact.sorted(Comparator.comparing(LegislatorBillInteraction::getOverallImpact).reversed());
		
		for (val interact : billsByImpact.limit(amount).collect(Collectors.toList()))
		{
			val bill = memService.get(interact.getBillId(), Bill.class).orElseThrow();
			val billMsg = "- " + interact.describe() + " \"" + interact.getBillName() + "\" (" + bill.getStatus().getDescription() + "): " + interact.getShortExplain();
			if ( (String.join("\n", billMsgs) + "\n" + billMsg).length() < BillSlicer.MAX_SECTION_LENGTH ) {
				billMsgs.add(billMsg);
				includedBills.add(interact.getBillId());
			} else {
				break;
			}
		}
	}

	private void createRequest(String oid, String sysMsg, String userMsg) {
		List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
		messages.add(new BatchBillMessage("system", sysMsg));
		messages.add(new BatchBillMessage("user", userMsg));
		
		requests.add(new BatchOpenAIRequest(
				oid,
				new BatchOpenAIBody(messages)
		));
		
		tokenLen += (userMsg.length() / 4);
	}

	private void writeBlock(int block) throws IOException {
		if (requests.size() == 0) return;
		
		File f = requestFile(block);
		
		val mapper = PoliscoreUtil.getObjectMapper();
		val s = requests.stream().map(r -> {
			try {
				return mapper.writeValueAsString(r);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}).toList();
		
		FileUtils.write(f, String.join("\n", s), "UTF-8");
		
		totalRequests += requests.size();
		
		Log.info("Successfully wrote " + requests.size() + " requests to " + f.getAbsolutePath());
		
		writtenFiles.add(f);
		
		requests = new ArrayList<BatchOpenAIRequest>();
		tokenLen = 0;
	}
	
	public static File requestFile(int blockNum) {
		return new File(Environment.getDeployedPath(), "openapi-legislators-bulk-" + blockNum + ".jsonl");
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
