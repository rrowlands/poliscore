package us.poliscore.entrypoint.batch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

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
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
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
@QuarkusMain(name="BatchSessionStatsRequestGenerator")
public class BatchSessionStatsRequestGenerator implements QuarkusApplication
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
	
	private List<BatchOpenAIRequest> requests = new ArrayList<BatchOpenAIRequest>();
	
	public static void main(String[] args) {
		Quarkus.run(BatchSessionStatsRequestGenerator.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		int block = 1;
		
//		s3.optimizeExists(LegislatorInterpretation.class);
		
		for (Legislator l : memService.query(Legislator.class).stream()
				.filter(l -> 
					l.getInteractions().size() > 0
//					&& (l.getBioguideId().equals("F000476") || l.getBioguideId().equals("O000172"))
					&& (!CHECK_S3_EXISTS || !s3.exists(LegislatorInterpretation.generateId(l.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()), LegislatorInterpretation.class))
				)
				.sorted(Comparator.comparing(Legislator::getDate).reversed())
//				.limit(2)
				.toList()) {
			interpret(l);
			
			if (tokenLen >= TOKEN_BLOCK_SIZE) {
				writeBlock(requests, block++);
				
				requests = new ArrayList<BatchOpenAIRequest>();
				tokenLen = 0;
			}
		};
		
		writeBlock(requests, block++);
		
		System.out.println("Program complete.");
	}
	
	protected void interpret(Legislator leg)
	{
		legInterp.updateInteractionsInterp(leg);
		
		DoubleIssueStats stats = legInterp.calculateAgregateInteractionStats(leg);
		
		val topInteractions = legInterp.calculateTopInteractions(leg);
		
		List<String> billMsgs = new ArrayList<String>();
		Set<String> includedBills = new HashSet<String>();
		for (int i = 0; i < 100; ++i) {
			for (val issue : topInteractions.keySet()) {
				if (topInteractions.get(issue).size() > i && !includedBills.contains(topInteractions.get(issue).get(i).getBillId())) {
					val interact = topInteractions.get(issue).get(i);
					val billMsg = interact.describe() + " \"" + interact.getBillName() + "\": " + interact.getShortExplain();
					if ( (String.join("\n", billMsgs) + "\n" + billMsg).length() < BillSlicer.MAX_SECTION_LENGTH ) {
						billMsgs.add(billMsg);
						includedBills.add(interact.getBillId());
					} else {
						break;
					}
				}
			}
		}
		
		createRequest(LegislatorInterpretation.generateId(leg.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()), LegislatorInterpretationService.getAiPrompt(leg, stats.toIssueStats()), String.join("\n", billMsgs));
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

	private void writeBlock(List<BatchOpenAIRequest> requests, int block) throws IOException {
		File f = new File(Environment.getDeployedPath(), "openapi-legislators-bulk-" + block + ".jsonl");
		
		val mapper = PoliscoreUtil.getObjectMapper();
		val s = requests.stream().map(r -> {
			try {
				return mapper.writeValueAsString(r);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}).toList();
		
		FileUtils.write(f, String.join("\n", s), "UTF-8");
		
		System.out.println("Successfully wrote " + requests.size() + " requests to " + f.getAbsolutePath());
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
