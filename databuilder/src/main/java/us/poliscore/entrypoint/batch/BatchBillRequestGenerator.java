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
import us.poliscore.ai.BatchBillRequest;
import us.poliscore.ai.BatchBillRequest.BatchBillBody;
import us.poliscore.ai.BatchBillRequest.BatchBillMessage;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.Legislator;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillType;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.CachedS3Service;
import us.poliscore.service.storage.DynamoDbPersistenceService;
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
	private S3PersistenceService s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private BillInterpretationService billInterpreter;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	private long tokenLen = 0;
	
	private List<BatchBillRequest> requests = new ArrayList<BatchBillRequest>();
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(BatchBillRequestGenerator.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		
		long totalBills = 0;
		long totalVotes = 0;
		
		for (File fCongress : Arrays.asList(PoliscoreUtil.USC_DATA.listFiles()).stream()
				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
				.sorted((a,b) -> a.getName().compareTo(b.getName()))
				.collect(Collectors.toList()))
		{
			if (!PoliscoreUtil.SUPPORTED_CONGRESSES.contains(Integer.valueOf(fCongress.getName()))) continue;
			
			Log.info("Processing " + fCongress.getName() + " congress");
			
			for (val bt : PROCESS_BILL_TYPE)
			{
				Log.info("Processing bill types " + bt + " congress");
				
				for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "bills/" + bt), f -> f.getName().equals("data.json")))
				{
					try (var fos = new FileInputStream(data))
					{
						billService.importUscData(fos);
						totalBills++;
					}
				}
				Log.info("Imported " + totalBills + " bills");
			}
			
			int skipped = 0;
			for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "votes"), f -> f.getName().equals("data.json")))
			{
				try (var fos = new FileInputStream(data))
				{
					if (rollCallService.importUscData(fos))
						totalVotes++;
					else
						skipped++;
				}
			}
			Log.info("Imported " + totalVotes + " votes. Skipped " + skipped);
		}
		
		int block = 1;
		
		for (Bill b : memService.query(Bill.class).stream()
				.sorted(Comparator.comparing(Bill::getIntroducedDate).reversed())
				.toList()) {
			val billText = billService.getBillText(b).orElse(null);
			
			if (billText != null && !billInterpreter.isInterpreted(b.getId())) {
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
		    			
		    			requests.add(new BatchBillRequest(
		    					BillInterpretation.generateId(b.getId(), null),
		    					new BatchBillBody(messages)
		    			));
		    		} else {
		    			val sliceInterps = new ArrayList<BillInterpretation>();
		    			
		        		for (int i = 0; i < slices.size(); ++i)
		        		{
		        			BillSlice slice = slices.get(i);
		        			
		        			Optional<BillInterpretation> sliceInterp = s3.get(BillInterpretation.generateId(b.getId(), i), BillInterpretation.class);
		        			
		        			if (sliceInterp.isEmpty()) {
			        			createRequest(BillInterpretation.generateId(b.getId(), slice.getSliceIndex()), BillInterpretationService.statsPrompt, slice.getText());
		        			} else {
		        				sliceInterps.add(sliceInterp.get());
		        			}
		        		}
		        		
		        		if (sliceInterps.size() == slices.size()) {
		        			IssueStats billStats = new IssueStats();
		            		
		            		for (int i = 0; i < slices.size(); ++i)
		            		{
		            			billStats = billStats.sum(sliceInterps.get(i).getIssueStats());
		            		}
		            		
		            		billStats = billStats.divideByTotalSummed();
		            		
//		            		var bi = getOrCreateAggregateInterpretation(bill, billStats, sliceInterps);
		            		
			    			createRequest(BillInterpretation.generateId(b.getId(), null), BillInterpretationService.summaryPrompt, billStats.getExplanation());
		        		}
		    		}
		    	}
				else {
	    			createRequest(BillInterpretation.generateId(b.getId(), null), BillInterpretationService.statsPrompt, b.getText().getXml());
				}
				
				if (tokenLen >= TOKEN_BLOCK_SIZE) {
					writeBlock(requests, block++);
					
					requests = new ArrayList<BatchBillRequest>();
					tokenLen = 0;
				}
			}
		};
		
		writeBlock(requests, block++);
		
		System.out.println("Program complete.");
	}
	
	private void createRequest(String oid, String sysMsg, String userMsg) {
		List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
		messages.add(new BatchBillMessage("system", sysMsg));
		messages.add(new BatchBillMessage("user", userMsg));
		
		requests.add(new BatchBillRequest(
				oid,
				new BatchBillBody(messages)
		));
		
		tokenLen += (userMsg.length() / 4);
	}

	private void writeBlock(List<BatchBillRequest> requests, int block) throws IOException {
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
