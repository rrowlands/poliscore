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
import us.poliscore.model.Legislator;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.LegislatorInterpretation;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;
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
//		List<String> specificFetch = Arrays.asList("BIL/us/congress/118/s/4120", "BIL/us/congress/118/s/4118", "BIL/us/congress/118/s/4155", "BIL/us/congress/118/s/4194", "BIL/us/congress/118/hr/4510", "BIL/us/congress/118/hr/4507", "BIL/us/congress/118/s/4187", "BIL/us/congress/118/hr/4552", "BIL/us/congress/118/s/4013", "BIL/us/congress/118/s/4069", "BIL/us/congress/118/hr/4447", "BIL/us/congress/118/hr/4498", "BIL/us/congress/118/hr/4494", "BIL/us/congress/118/hr/4789", "BIL/us/congress/118/hr/4775", "BIL/us/congress/118/hr/2103", "BIL/us/congress/118/hr/4605", "BIL/us/congress/118/hr/4608", "BIL/us/congress/118/hr/4664", "BIL/us/congress/118/hr/4665", "BIL/us/congress/118/hr/6700", "BIL/us/congress/118/hr/6719", "BIL/us/congress/118/hr/4128", "BIL/us/congress/118/hr/4196", "BIL/us/congress/118/hr/4061", "BIL/us/congress/118/hr/4066", "BIL/us/congress/118/hr/6933", "BIL/us/congress/118/hr/6970", "BIL/us/congress/118/hr/4319", "BIL/us/congress/118/hr/6976", "BIL/us/congress/118/hr/4349", "BIL/us/congress/118/hr/4365", "BIL/us/congress/118/hr/4366", "BIL/us/congress/118/hr/4367", "BIL/us/congress/118/hr/4368", "BIL/us/congress/118/hr/4336", "BIL/us/congress/118/hr/4394", "BIL/us/congress/118/hr/4370", "BIL/us/congress/118/hr/4276", "BIL/us/congress/118/s/2369", "BIL/us/congress/118/s/2380", "BIL/us/congress/118/hr/2701", "BIL/us/congress/118/hr/2779", "BIL/us/congress/118/s/2309", "BIL/us/congress/118/s/2321", "BIL/us/congress/118/s/2344", "BIL/us/congress/118/s/2333", "BIL/us/congress/118/s/2251", "BIL/us/congress/118/s/2248", "BIL/us/congress/118/s/2230", "BIL/us/congress/118/s/2266", "BIL/us/congress/118/s/2258", "BIL/us/congress/118/s/2280", "BIL/us/congress/118/s/2281", "BIL/us/congress/118/hr/2640", "BIL/us/congress/118/hr/2670", "BIL/us/congress/118/hr/2671", "BIL/us/congress/118/hr/2683", "BIL/us/congress/118/s/2226", "BIL/us/congress/118/s/2122", "BIL/us/congress/118/s/2127", "BIL/us/congress/118/s/2142", "BIL/us/congress/118/s/2132", "BIL/us/congress/118/s/2131", "BIL/us/congress/118/hr/2965", "BIL/us/congress/118/s/2103", "BIL/us/congress/118/s/1", "BIL/us/congress/118/s/2106", "BIL/us/congress/118/s/2003", "BIL/us/congress/118/hr/2811", "BIL/us/congress/118/s/2043", "BIL/us/congress/118/s/2049", "BIL/us/congress/118/hr/2882", "BIL/us/congress/118/s/25", "BIL/us/congress/118/s/51", "BIL/us/congress/118/s/42", "BIL/us/congress/118/s/46", "BIL/us/congress/118/s/74", "BIL/us/congress/118/hr/2336", "BIL/us/congress/118/hr/2335", "BIL/us/congress/118/hr/2369", "BIL/us/congress/118/s/4443", "BIL/us/congress/118/hr/4830", "BIL/us/congress/118/hr/4820", "BIL/us/congress/118/hr/4821", "BIL/us/congress/118/hr/4822", "BIL/us/congress/118/hr/4869", "BIL/us/congress/118/s/4301", "BIL/us/congress/118/s/4367", "BIL/us/congress/118/s/4361", "BIL/us/congress/118/s/4207", "BIL/us/congress/118/s/4226", "BIL/us/congress/118/s/4213", "BIL/us/congress/118/s/4286", "BIL/us/congress/118/hr/2497", "BIL/us/congress/118/hr/929", "BIL/us/congress/118/hr/981", "BIL/us/congress/118/s/1939", "BIL/us/congress/118/s/1933", "BIL/us/congress/118/s/1834", "BIL/us/congress/118/s/1861", "BIL/us/congress/118/s/1893", "BIL/us/congress/118/hr/815", "BIL/us/congress/118/s/1752", "BIL/us/congress/118/s/1742", "BIL/us/congress/118/s/1776", "BIL/us/congress/118/s/1606", "BIL/us/congress/118/s/1655", "BIL/us/congress/118/s/1646", "BIL/us/congress/118/hr/647", "BIL/us/congress/118/s/1518", "BIL/us/congress/118/s/1500", "BIL/us/congress/118/s/1531", "BIL/us/congress/118/s/1536", "BIL/us/congress/118/s/1538", "BIL/us/congress/118/s/1521", "BIL/us/congress/118/s/1558", "BIL/us/congress/118/hr/573", "BIL/us/congress/118/s/1426", "BIL/us/congress/118/s/1456", "BIL/us/congress/118/s/1449", "BIL/us/congress/118/s/3906", "BIL/us/congress/118/s/3960", "BIL/us/congress/118/s/3961", "BIL/us/congress/118/s/1335", "BIL/us/congress/118/s/1339", "BIL/us/congress/118/s/1354", "BIL/us/congress/118/s/1342", "BIL/us/congress/118/s/1341", "BIL/us/congress/118/s/1365", "BIL/us/congress/118/s/1264", "BIL/us/congress/118/s/1289", "BIL/us/congress/118/s/3878", "BIL/us/congress/118/s/1204", "BIL/us/congress/118/s/3867", "BIL/us/congress/118/s/1208", "BIL/us/congress/118/s/3891", "BIL/us/congress/118/s/3886", "BIL/us/congress/118/s/1226", "BIL/us/congress/118/s/1229", "BIL/us/congress/118/s/3888", "BIL/us/congress/118/s/1199", "BIL/us/congress/118/s/3709", "BIL/us/congress/118/s/3725", "BIL/us/congress/118/hjres/96", "BIL/us/congress/118/hr/7494", "BIL/us/congress/118/hr/7540", "BIL/us/congress/118/hr/7543", "BIL/us/congress/118/hr/7571", "BIL/us/congress/118/hr/7875", "BIL/us/congress/118/hr/5258", "BIL/us/congress/118/hr/5088", "BIL/us/congress/118/hr/7739", "BIL/us/congress/118/hr/7053", "BIL/us/congress/118/hr/7095", "BIL/us/congress/118/hr/7113", "BIL/us/congress/118/hr/7383", "BIL/us/congress/118/hr/7364", "BIL/us/congress/118/hr/7377", "BIL/us/congress/118/hr/7429", "BIL/us/congress/118/hr/7468", "BIL/us/congress/118/hr/7439", "BIL/us/congress/118/hr/7273", "BIL/us/congress/118/hr/7240", "BIL/us/congress/118/s/1963", "BIL/us/congress/118/s/1987", "BIL/us/congress/118/hr/7339", "BIL/us/congress/118/hr/7326", "BIL/us/congress/118/hr/5844", "BIL/us/congress/118/hr/5877", "BIL/us/congress/118/hr/5894", "BIL/us/congress/118/hr/5893", "BIL/us/congress/118/hr/3226", "BIL/us/congress/118/hr/5859", "BIL/us/congress/118/hr/5766", "BIL/us/congress/118/hr/5750", "BIL/us/congress/118/hr/3173", "BIL/us/congress/118/hr/3482", "BIL/us/congress/118/hr/5926", "BIL/us/congress/118/hr/5980", "BIL/us/congress/118/hr/5978", "BIL/us/congress/118/hr/5402", "BIL/us/congress/118/hr/5457", "BIL/us/congress/118/hr/7910", "BIL/us/congress/118/hr/7949", "BIL/us/congress/118/hr/7994", "BIL/us/congress/118/hr/5378", "BIL/us/congress/118/hr/5375", "BIL/us/congress/118/hr/5339", "BIL/us/congress/118/hr/5673", "BIL/us/congress/118/hr/3047", "BIL/us/congress/118/hr/3094", "BIL/us/congress/118/hr/5533", "BIL/us/congress/118/hr/5525", "BIL/us/congress/118/s/1034", "BIL/us/congress/118/hr/1402", "BIL/us/congress/118/hr/1473", "BIL/us/congress/118/s/3623", "BIL/us/congress/118/s/1016", "BIL/us/congress/118/s/3580", "BIL/us/congress/118/hr/3935", "BIL/us/congress/118/hr/3943", "BIL/us/congress/118/hr/1335", "BIL/us/congress/118/s/3504", "BIL/us/congress/118/s/3443", "BIL/us/congress/118/sjres/48", "BIL/us/congress/118/hr/1669", "BIL/us/congress/118/s/3415", "BIL/us/congress/118/s/3407", "BIL/us/congress/118/s/3404", "BIL/us/congress/118/s/3430", "BIL/us/congress/118/s/3425", "BIL/us/congress/118/hjres/1", "BIL/us/congress/118/s/3344", "BIL/us/congress/118/s/3367", "BIL/us/congress/118/s/3392", "BIL/us/congress/118/hr/1561", "BIL/us/congress/118/s/3308", "BIL/us/congress/118/s/3306", "BIL/us/congress/118/s/3203", "BIL/us/congress/118/s/3234", "BIL/us/congress/118/hr/3671", "BIL/us/congress/118/hr/1026", "BIL/us/congress/118/hr/1089", "BIL/us/congress/118/hr/1090", "BIL/us/congress/118/s/3142", "BIL/us/congress/118/s/3140", "BIL/us/congress/118/s/3132", "BIL/us/congress/118/s/3130", "BIL/us/congress/118/s/3127", "BIL/us/congress/118/s/3155", "BIL/us/congress/118/hr/3509", "BIL/us/congress/118/s/3198", "BIL/us/congress/118/hr/3549", "BIL/us/congress/118/hr/3560", "BIL/us/congress/118/hr/3565", "BIL/us/congress/118/s/3063", "BIL/us/congress/118/hr/3867", "BIL/us/congress/118/s/3093", "BIL/us/congress/118/hr/207", "BIL/us/congress/118/hr/3700", "BIL/us/congress/118/hr/3734", "BIL/us/congress/118/hr/1126", "BIL/us/congress/118/hr/147", "BIL/us/congress/118/hr/8038", "BIL/us/congress/118/hr/8074", "BIL/us/congress/118/s/680", "BIL/us/congress/118/s/636", "BIL/us/congress/118/s/602", "BIL/us/congress/118/hr/8243", "BIL/us/congress/118/hr/8261", "BIL/us/congress/118/s/388", "BIL/us/congress/118/s/392", "BIL/us/congress/118/s/367", "BIL/us/congress/118/s/2944", "BIL/us/congress/118/hr/2", "BIL/us/congress/118/hr/8108", "BIL/us/congress/118/s/2824", "BIL/us/congress/118/s/2812", "BIL/us/congress/118/s/2841", "BIL/us/congress/118/s/2840", "BIL/us/congress/118/s/2881", "BIL/us/congress/118/hr/1847", "BIL/us/congress/118/hr/1820", "BIL/us/congress/118/hr/1824", "BIL/us/congress/118/s/136", "BIL/us/congress/118/s/105", "BIL/us/congress/118/s/2739", "BIL/us/congress/118/s/2769", "BIL/us/congress/118/hr/1718", "BIL/us/congress/118/hr/1731", "BIL/us/congress/118/s/270", "BIL/us/congress/118/s/2605", "BIL/us/congress/118/s/2625", "BIL/us/congress/118/s/2624", "BIL/us/congress/118/s/2649", "BIL/us/congress/118/s/2679", "BIL/us/congress/118/s/2693", "BIL/us/congress/118/s/2597", "BIL/us/congress/118/s/2587", "BIL/us/congress/118/hr/1988", "BIL/us/congress/118/s/2429", "BIL/us/congress/118/s/2419", "BIL/us/congress/118/s/2443", "BIL/us/congress/118/s/2438", "BIL/us/congress/118/s/2437", "BIL/us/congress/118/s/2462", "BIL/us/congress/118/hr/6369", "BIL/us/congress/118/hr/8812", "BIL/us/congress/118/hr/8818", "BIL/us/congress/118/hr/6216", "BIL/us/congress/118/hr/6544", "BIL/us/congress/118/hr/6411", "BIL/us/congress/118/hr/6448", "BIL/us/congress/118/hr/6494", "BIL/us/congress/118/hr/6478", "BIL/us/congress/118/hr/8467", "BIL/us/congress/118/hr/8398", "BIL/us/congress/118/hr/8371", "BIL/us/congress/118/hr/8381", "BIL/us/congress/118/hr/6034", "BIL/us/congress/118/s/883", "BIL/us/congress/118/s/873", "BIL/us/congress/118/s/870", "BIL/us/congress/118/s/827", "BIL/us/congress/118/hr/8752", "BIL/us/congress/118/hr/8771", "BIL/us/congress/118/hr/8773", "BIL/us/congress/118/hr/8774", "BIL/us/congress/118/hr/8580", "BIL/us/congress/118/s/947", "BIL/us/congress/118/s/930", "BIL/us/congress/118/hr/6028", "BIL/us/congress/118/s/919");
		
		for (Bill b : memService.query(Bill.class).stream()
//				.filter(b -> specificFetch.contains(b.getId()))
				.sorted(Comparator.comparing(Bill::getIntroducedDate).reversed())
				.toList()) {
			
			if (!billInterpreter.isInterpreted(b.getId()) && s3.exists(BillText.generateId(b.getId()), BillText.class)) {
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
		        				
			        			createRequest(oid, BillInterpretationService.statsPrompt, slice.getText());
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
		            		
		            		val oid = BillInterpretation.generateId(b.getId(), null);
		            		
		            		if (s3.exists(oid, BillInterpretation.class)) { continue; }
		            		
			    			createRequest(oid, BillInterpretationService.summaryPrompt, billStats.getExplanation());
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
