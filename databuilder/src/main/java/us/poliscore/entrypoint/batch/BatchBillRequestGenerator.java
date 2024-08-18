package us.poliscore.entrypoint.batch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
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
import us.poliscore.MissingBillTextException;
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIRequest;
import us.poliscore.ai.BatchOpenAIRequest.BatchOpenAIBody;
import us.poliscore.ai.BatchOpenAIRequest.BatchBillMessage;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionSet;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.CachedS3Service;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;
import us.poliscore.service.storage.S3PersistenceService;

/**
 * This bulk importer is designed to import a full dataset built with the github.com/unitedstates/congress toolkit 
 */
@QuarkusMain(name="BatchBillRequestGenerator")
public class BatchBillRequestGenerator implements QuarkusApplication
{
	public static final long TOKEN_BLOCK_SIZE = 30000000;
	
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private BillInterpretationService billInterpreter;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	private long tokenLen = 0;
	
	private List<BatchOpenAIRequest> requests = new ArrayList<BatchOpenAIRequest>();
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(BatchBillRequestGenerator.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		
		int block = 1;
		
		s3.optimizeExists(BillInterpretation.class);
		s3.optimizeExists(BillText.class);
		
//		List<String> specificFetch = Arrays.asList("BIL/us/congress/118/hr/647", "BIL/us/congress/118/hr/2670", "BIL/us/congress/118/hr/2497");
		
		for (Bill b : memService.query(Bill.class).stream()
//				.filter(b -> specificFetch.contains(b.getId()))
//				.filter(b -> !billInterpreter.isInterpreted(b.getId()))
				.filter(b -> s3.exists(BillText.generateId(b.getId()), BillText.class))
				.sorted(Comparator.comparing(Bill::getIntroducedDate).reversed())
				.limit(10)
				.toList()) {
			
			val billText = billService.getBillText(b).orElse(null);
			b.setText(billText);
			
			if (billText.getXml().length() >= BillSlicer.MAX_SECTION_LENGTH)
	    	{
	    		List<BillSlice> slices = new XMLBillSlicer().slice(b, b.getText(), BillSlicer.MAX_SECTION_LENGTH);
	    		
	    		if (slices.size() == 0) throw new UnsupportedOperationException("Slicer returned zero slices?");
	    		else if (slices.size() == 1) {
	    			b.getText().setXml(slices.get(0).getText());
	    			
	    			List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
					messages.add(new BatchBillMessage("system", BillInterpretationService.statsPrompt));
	    			messages.add(new BatchBillMessage("user", b.getText().getXml()));
	    			
	    			requests.add(new BatchOpenAIRequest(
	    					BillInterpretation.generateId(b.getId(), null),
	    					new BatchOpenAIBody(messages)
	    			));
	    		} else {
	    			val sliceInterps = new ArrayList<BillInterpretation>();
	    			
	        		for (int i = 0; i < slices.size(); ++i)
	        		{
	        			BillSlice slice = slices.get(i);
	        			
	        			Optional<BillInterpretation> sliceInterp = s3.get(BillInterpretation.generateId(b.getId(), i), BillInterpretation.class);
	        			
	        			if (sliceInterp.isEmpty()) {
	        				val oid = BillInterpretation.generateId(b.getId(), slice.getSliceIndex());
	        				
	        				if (s3.exists(oid, BillInterpretation.class)) { continue; }
	        				
		        			createRequest(oid, BillInterpretationService.slicePrompt, slice.getText());
	        			} else {
	        				sliceInterps.add(sliceInterp.get());
	        			}
	        		}
	        		
	        		if (sliceInterps.size() == slices.size()) {
	        			IssueStats billStats = new IssueStats();
	        			List<String> summaries = new ArrayList<String>();
	            		
	            		for (int i = 0; i < slices.size(); ++i)
	            		{
	            			billStats = billStats.sum(sliceInterps.get(i).getIssueStats());
	            			summaries.add(sliceInterps.get(i).getShortExplain());
	            		}
	            		
	            		billStats = billStats.divideByTotalSummed();
	            		
	            		val oid = BillInterpretation.generateId(b.getId(), null);
	            		
	            		if (s3.exists(oid, BillInterpretation.class)) { continue; }
	            		
		    			createRequest(oid, BillInterpretationService.aggregatePrompt, String.join("\n", summaries));
	        		}
	    		}
	    	}
			else {
    			createRequest(BillInterpretation.generateId(b.getId(), null), BillInterpretationService.statsPrompt, b.getText().getXml());
			}
			
			if (tokenLen >= TOKEN_BLOCK_SIZE) {
				writeBlock(requests, block++);
				
				requests = new ArrayList<BatchOpenAIRequest>();
				tokenLen = 0;
			}
		};
		
		writeBlock(requests, block++);
		
		System.out.println("Program complete.");
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
		File f = new File(Environment.getDeployedPath(), "openapi-bills-bulk-" + block + ".jsonl");
		
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
