package us.poliscore;

import java.io.IOException;
import java.util.ArrayList;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillText;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;

@QuarkusMain(name="DataCleaner")
public class DataCleaner implements QuarkusApplication {
	
	@Inject private LegislatorService legService;
	
	@Inject private BillService billService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject private MemoryObjectService memService;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
//		rollCallService.importUscVotes();
		
		val badInterps = new ArrayList<String>();
		val badExplanations = new ArrayList<String>();
		
		s3.optimizeExists(BillText.class);
		
		for (Bill b : memService.query(Bill.class)) {
			val op = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class);
			
			if (!op.isPresent()) {
				if (s3.exists(BillText.generateId(b.getId()), BillText.class)) {
					badInterps.add(b.getId());
				}
				
				continue;
			}
			
			val interp = op.get();
			
			if (interp.getIssueStats() == null || !interp.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety)) {
				badInterps.add(b.getId());
				continue;
			}
			
			val summaryHeaders = new String[] { "summary of the predicted impact to society and why", "summary of the predicted impact to society", "summary of the bill and predicted impact to society and why", "summary of the bill and predicted impact to society", "summary of the bill and its predicted impact to society and why", "summary of the bill and its predicted impact to society", "Summary of the bill's predicted impact to society and why", "Summary of the bill's predicted impact to society", "summary of predicted impact to society and why", "summary of predicted impact to society", "summary of the impact to society", "summary of impact to society", "summary report", "summary of the impact", "summary of impact", "summary", "explanation" };
			val summaryHeaderRegex = " *#*\\** *(" + String.join("|", summaryHeaders) + ") *#*\\** *:? *#*\\** *";
			if (interp.getLongExplain().matches("(?i)^" + summaryHeaderRegex + ".*$")) {
//				interp.getIssueStats().setExplanation(interp.getIssueStats().getExplanation().replaceFirst("(?i)" + summaryHeaderRegex, ""));
				badExplanations.add(b.getId());
				
//				s3.put(interp);
//				
//				b.setInterpretation(interp);
//				ddb.put(b);
			}
		}
		
		System.out.println(String.join(", ", badInterps.stream().map(id -> "\"" + id + "\"").toList()));
		System.out.println(String.join(", ", badExplanations.stream().map(id -> "\"" + id + "\"").toList()));
		
		System.out.println("Program complete.");
	}
	
	private String getTestString() {
		return "Agriculture and Food: N/A\\n\\nEducation: N/A\\n\\nTransportation: N/A\\n\\nEconomics and Commerce: +60\\n(Explanation: The bill facilitates wealth-building for low- and moderate-income households through homeownership, potentially increasing economic stability and consumer spending.)\\n\\nForeign Relations: N/A\\n\\nSocial Equity: +80\\n(Explanation: The bill aims to provide home loans to first-time, first-generation homebuyers, aiming to reduce housing inequality and offering new opportunities to historically marginalized groups.)\\n\\nGovernment Efficiency and Management: +40\\n(Explanation: The bill allocates management of funds and program implementation to existing agencies (HUD and USDA), which could benefit from their established infrastructure and expertise, though it does introduce layered regulations.)\\n\\nHealthcare: N/A\\n\\nHousing: +90\\n(Explanation: By providing affordable home loans with targeted funds and outreach, the bill directly addresses housing access issues for low- and moderate-income families.)\\n\\nEnergy: N/A\\n\\nTechnology: N/A\\n\\nImmigration: N/A\\n\\nNational Defense: N/A\\n\\nCrime and Law Enforcement: N/A\\n\\nWildlife and Forest Management: N/A\\n\\nPublic Lands and Natural Resources: N/A\\n\\nEnvironmental Management and Climate Change: N/A\\n\\nOverall Benefit to Society: +70\\n\\nSummary: The Low-Income First-Time Homebuyers Act of 2023 (H.R. 4573) aims to provide affordable and sustainable home loans to low- and moderate-income first-time, first-generation homebuyers through the LIFT Home Program. Establishing LIFT HOME funds in loan guarantee agencies and outlining the management of these funds, the bill sets a structured approach to expand homeownership among historically disadvantaged groups, fostering economic stability and increasing social equity. By leveraging the Department of Housing and Urban Development (HUD) and Department of Agriculture (USDA) to administer the program, it utilizes existing government resources efficiently. The expected impact includes heightened housing accessibility and reduced economic disparity.\"}, \"logprobs\": null, \"finish_reason\": \"stop\"}], \"usage\": {\"prompt_tokens\": 7109, \"completion_tokens\": 384, \"total_tokens\": 7493}, \"system_fingerprint\": \"fp_d576307f90\"}}, \"error\": null}\n"
				+ "{\"id\": \"batch_req_kOymh27bhS4MhRjQkDDzHDIs\", \"custom_id\": \"BIT/us/congress/118/hr/4580\", \"response\": {\"status_code\": 200, \"request_id\": \"e32f839e00d81b5cc9eff68f1b0c4a42\", \"body\": {\"id\": \"chatcmpl-9j5PavhbgHwtme3Kn4hXPGecsPMKs\", \"object\": \"chat.completion\", \"created\": 1720532590, \"model\": \"gpt-4o-2024-05-13\", \"choices\": [{\"index\": 0, \"message\": {\"role\": \"assistant\", \"content\": \"Agriculture and Food: 0\\nEducation: +20\\nTransportation: 0\\nEconomics and Commerce: +30\\nForeign Relations: N/A\\nSocial Equity: +70\\nGovernment Efficiency and Management: +10\\nHealthcare: N/A\\nHousing: 0\\nEnergy: 0\\nTechnology: 0\\nImmigration: N/A\\nNational Defense: N/A\\nCrime and Law Enforcement: +10\\nWildlife and Forest Management: +60\\nPublic Lands and Natural Resources: +80\\nEnvironmental Management and Climate Change: +70\\nOverall Benefit to Society: +60\\n\\nThe \\\"Baaj Nwaavjo I\\u2019tah Kukveni Grand Canyon National Monument Act\\\" (95 HR 4580 IH) proposes designating a Grand Canyon area as a national monument to protect its significant ecological, cultural, and recreational resources. The bill emphasizes the cultural ties and stewardship of regional Indian Tribes, calling for their integral involvement in land management and conservation efforts (Section 2, paragraphs 2-5). This will enhance social equity by recognizing and incorporating Native knowledge and rights. The bill also highlights the importance of the area\\u2019s biodiversity, climate, and recreational value, stressing the protection of wildlife corridors and habitats, which benefits environmental sustainability (Section 4(b) and 4(c)). The proposed measures to exclude mining operations (Section 6) further bolster environmental protection. The potential for expanded recreational activities (Section 5(c) and 5(m)) could drive local sustainable economic development. The public\\u2019s access to education about the historical, scientific, and cultural significance of the region may also improve. Overall, while the bill enhances conservation and cultural recognition, its economic and educational impacts are supplementary.";
	}
	
	public static void main(String[] args) {
		Quarkus.run(DataCleaner.class, args);
	}
	
	@Override
	public int run(String... args) throws Exception {
	  process();
	  
	  Quarkus.waitForExit();
	  return 0;
	}
}
