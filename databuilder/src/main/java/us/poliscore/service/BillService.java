package us.poliscore.service;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.utils.StringUtils;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.LegislativeChamber;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillIssueStat;
import us.poliscore.model.bill.BillStatus;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillCosponsor;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillSponsor;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;
import us.poliscore.view.USCBillView;

@ApplicationScoped
@Priority(4)
public class BillService {
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private MemoryObjectService memService;
	
	@Inject
	protected LegislatorService lService;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	@SneakyThrows
	public void importUscBills() {
		if (memService.query(Bill.class).size() > 0) return;
		
		long totalBills = 0;
		
		for (File fCongress : Arrays.asList(PoliscoreUtil.USC_DATA.listFiles()).stream()
				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
				.sorted((a,b) -> a.getName().compareTo(b.getName()))
				.collect(Collectors.toList()))
		{
			if (!Integer.valueOf(PoliscoreUtil.CURRENT_SESSION.getNumber()).equals(Integer.valueOf(fCongress.getName()))) continue;
			
			val session = CongressionalSession.of(Integer.valueOf(fCongress.getName()));
			
			Log.info("Processing " + fCongress.getName() + " congress");
			
			for (val bt : PROCESS_BILL_TYPE)
			{
				Log.info("Processing bill types " + bt + " congress");
				
				for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "bills/" + bt), f -> f.getName().equals("data.json")))
				{
					try (var fos = new FileInputStream(data))
					{
						importUscBill(fos, String.valueOf(session.getNumber()));
						totalBills++;
					}
				}
			}
		}
		
		Log.info("Imported " + totalBills + " bills");
	}
	
	@SneakyThrows
	protected void importUscBill(FileInputStream fos, String session) {
		val view = PoliscoreUtil.getObjectMapper().readValue(fos, USCBillView.class);
		
//		String text = fetchBillText(view.getUrl());
    	
    	val bill = new Bill();
//    	bill.setText(text);
    	bill.setName(view.getBillName());
    	bill.setSession(Integer.parseInt(view.getCongress()));
    	bill.setType(BillType.valueOf(view.getBill_type().toUpperCase()));
    	bill.setNumber(Integer.parseInt(view.getNumber()));
    	bill.setStatus(buildStatus(view));
//    	bill.setStatusUrl(view.getUrl());
    	bill.setIntroducedDate(view.getIntroduced_at());
    	bill.setSponsor(view.getSponsor() == null ? null : view.getSponsor().convert(session, memService));
    	bill.setCosponsors(view.getCosponsors().stream().map(s -> s.convert(session, memService)).collect(Collectors.toList()));
    	
    	if (view.getSponsor() != null && !StringUtils.isBlank(view.getSponsor().getBioguide_id()))
    	{
			val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), view.getSponsor().getBioguide_id()));
			
			if (leg.isPresent()) {
				LegislatorBillSponsor interaction = new LegislatorBillSponsor();
				interaction.setLegId(leg.get().getId());
				interaction.setBillId(bill.getId());
				interaction.setDate(view.getIntroduced_at());
				interaction.setBillName(bill.getName());
				leg.get().addBillInteraction(interaction);
				
				memService.put(leg.get());
			}
    	}
    	
    	view.getCosponsors().stream().filter(cs -> view.getSponsor() == null || !view.getSponsor().getBioguide_id().equals(cs.getBioguide_id())).forEach(cs -> {
    		if (!StringUtils.isBlank(cs.getBioguide_id())) {
	    		val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), cs.getBioguide_id()));
				
	    		if (leg.isPresent()) {
					LegislatorBillCosponsor interaction = new LegislatorBillCosponsor();
					interaction.setLegId(leg.get().getId());
					interaction.setBillId(bill.getId());
					interaction.setDate(view.getIntroduced_at());
					interaction.setBillName(bill.getName());
					leg.get().addBillInteraction(interaction);
					
					memService.put(leg.get());
	    		}
    		}
    	});
    	
    	memService.put(bill);
	}
	
	public BillStatus buildStatus(USCBillView view) {
	    BillStatus status = new BillStatus();
	    status.setSourceStatus(view.getStatus());
	    
	    final LegislativeChamber chamber = BillType.getOriginatingChamber(BillType.valueOf(view.getBill_type().toUpperCase()));
	    final String stat = view.getStatus().toUpperCase();
	    final boolean sessionOver = CongressionalSession.of(Integer.parseInt(view.getCongress())).isOver();

	    if (stat.equals("INTRODUCED")) {
	        status.setDescription("Introduced in the " + chamber.getName());
	        status.setProgress(0.0f);
	    }
	    else if (stat.equals("REFERRED")) {
	        status.setDescription((sessionOver ? "Died in" : "Referred to") + " Committee");
	        status.setProgress(0.1f);
	    }
	    else if (stat.equals("REPORTED")) {
	        status.setDescription(sessionOver ? ("Expired Awaiting " + chamber.getName() + " Debate") : ("Awaiting " + chamber.getName() + " Debate"));
	        status.setProgress(0.2f);
	    }
	    else if (stat.equals("PROV_KILL:SUSPENSIONFAILED")) {
	        status.setDescription("Provisionally Killed (suspension of rules)");
	        status.setProgress(0.3f);
	    }
	    else if (stat.equals("PROV_KILL:CLOTUREFAILED")) {
	        status.setDescription("Provisionally Killed (filibustered)");
	        status.setProgress(0.3f);
	    }
	    else if (stat.startsWith("FAIL:ORIGINATING")) {
	        // e.g., FAIL:ORIGINATING:HOUSE or FAIL:ORIGINATING:SENATE
	        status.setDescription("Failed " + chamber.getName() + " Vote");
	        status.setProgress(0.3f);
	    }
	    else if (stat.equals("PASSED:SIMPLERES")) {
	        status.setDescription("Simple Resolution Passed in the " + chamber.getName());
	        // For simple resolutions, passing is the end of the road
	        status.setProgress(1.0f);
	    }
	    else if (stat.equals("PASSED:CONSTAMEND")) {
	        status.setDescription("Constitutional Amendment Passed by Both Chambers");
	        // After passing both chambers, it goes to the states, so from Congress's perspective it’s “final”
	        status.setProgress(1.0f);
	    }
	    else if (stat.equals("PASS_OVER:HOUSE")) {
	        status.setDescription("Passed in House, " + (sessionOver ? "Died in " : "Sent to") + " Senate");
	        status.setProgress(0.4f);
	    }
	    else if (stat.equals("PASS_OVER:SENATE")) {
	        status.setDescription("Passed in Senate, " + (sessionOver ? "Died in " : "Sent to") + " House");
	        status.setProgress(0.4f);
	    }
	    else if (stat.equals("PASSED:CONCURRENTRES")) {
	        status.setDescription("Concurrent Resolution Passed by Both Chambers");
	        status.setProgress(1.0f);
	    }
	    else if (stat.equals("FAIL:SECOND:HOUSE")) {
	        status.setDescription("Failed in Second Chamber (House)");
	        status.setProgress(0.5f);
	    }
	    else if (stat.equals("FAIL:SECOND:SENATE")) {
	        status.setDescription("Failed in Second Chamber (Senate)");
	        status.setProgress(0.5f);
	    }
	    else if (stat.equals("PASS_BACK:HOUSE")) {
	        status.setDescription("Bill Passed with Changes, Returning to House");
	        status.setProgress(0.5f);
	    }
	    else if (stat.equals("PASS_BACK:SENATE")) {
	        status.setDescription("Bill Passed with Changes, Returning to Senate");
	        status.setProgress(0.5f);
	    }
	    else if (stat.equals("PROV_KILL:PINGPONGFAIL")) {
	        status.setDescription("Ping-Pong Negotiations Failed (Provisional Kill)");
	        status.setProgress(0.5f);
	    }
	    else if (stat.equals("PASSED:BILL")) {
	        status.setDescription("Bill Passed Both Chambers, " + (sessionOver ? "Killed by " : "Sent to") + " the President");
	        status.setProgress(0.8f);
	    }
	    else if (stat.equals("CONFERENCE:PASSED:HOUSE")) {
	        status.setDescription("Conference Report Passed in House, Awaiting Senate");
	        status.setProgress(0.6f);
	    }
	    else if (stat.equals("CONFERENCE:PASSED:SENATE")) {
	        status.setDescription("Conference Report Passed in Senate, Awaiting House");
	        status.setProgress(0.6f);
	    }
	    else if (stat.equals("ENACTED:SIGNED")) {
	        status.setDescription("Law");
	        status.setProgress(1.0f);
	    }
	    else if (stat.equals("PROV_KILL:VETO")) {
	        status.setDescription("Provisionally Killed by Veto (Override Possible)");
	        status.setProgress(0.7f);
	    }
	    else if (stat.equals("VETOED:POCKET")) {
	        status.setDescription("Pocket Veto - Bill is Dead");
	        status.setProgress(0.8f);
	    }
	    else if (stat.equals("VETOED:OVERRIDE_FAIL_ORIGINATING:HOUSE")) {
	        status.setDescription("Veto Override Failed in House (Originating Chamber)");
	        status.setProgress(0.0f);
	    }
	    else if (stat.equals("VETOED:OVERRIDE_FAIL_ORIGINATING:SENATE")) {
	        status.setDescription("Veto Override Failed in Senate (Originating Chamber)");
	        status.setProgress(0.0f);
	    }
	    else if (stat.equals("VETOED:OVERRIDE_PASS_OVER:HOUSE")) {
	        status.setDescription("Veto Override Passed in House, " + (sessionOver ? "Died in " : "Sent to") + " Senate");
	        status.setProgress(0.8f);
	    }
	    else if (stat.equals("VETOED:OVERRIDE_PASS_OVER:SENATE")) {
	        status.setDescription("Veto Override Passed in Senate, " + (sessionOver ? "Died in " : "Sent to") + " House");
	        status.setProgress(0.8f);
	    }
	    else if (stat.equals("VETOED:OVERRIDE_FAIL_SECOND:HOUSE")) {
	        status.setDescription("Veto Override Passed in Senate but Failed in House");
	        status.setProgress(0.0f);
	    }
	    else if (stat.equals("VETOED:OVERRIDE_FAIL_SECOND:SENATE")) {
	        status.setDescription("Veto Override Passed in House but Failed in Senate");
	        status.setProgress(0.0f);
	    }
	    else if (stat.equals("ENACTED:VETO_OVERRIDE")) {
	        status.setDescription("Law");
	        status.setProgress(1.0f);
	    }
	    else if (stat.equals("ENACTED:TENDAYRULE")) {
	        status.setDescription("Law");
	        status.setProgress(1.0f);
	    }
	    else {
	    	throw new UnsupportedOperationException("Unsupported status: " + stat);
	    }

	    return status;
	}

	public void ddbPersist(Bill b, BillInterpretation interp)
	{
		b.setInterpretation(interp);
		ddb.put(b);
		
		for(TrackedIssue issue : TrackedIssue.values()) {
			ddb.put(new BillIssueStat(issue, b.getImpact(issue), b));
		}
	}
	
	@SneakyThrows
	public void generateBillWebappIndex() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/resources/bills.index");
		
		val data = memService.queryAll(Bill.class).stream()
			.filter(b -> PoliscoreUtil.SUPPORTED_CONGRESSES.stream().anyMatch(s -> b.isIntroducedInSession(s)) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
			.map(b -> {
				// The bill name can come from the interpretation so we have to fetch it.
				b.setInterpretation(s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class).orElseThrow());
				
				return Arrays.asList(b.getId(), b.getName());
			})
			.sorted((a,b) -> a.get(1).compareTo(b.get(1)))
			.toList();
		
		Log.info("Generated a bill 'index' of size " + data.size());
		
		FileUtils.write(out, PoliscoreUtil.getObjectMapper().writeValueAsString(data), "UTF-8");
	}
	
	@SneakyThrows
	public void dumbAllBills() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/resources/allbills.dump");
		
		val data = memService.query(Bill.class).stream()
			.filter(b -> s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
			.sorted((a,b) -> a.getName().compareTo(b.getName()))
			.toList();
		
		data.forEach(b -> {
			val interp = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class).get();
			interp.setLongExplain("");
			interp.setShortExplain(interp.getShortExplain().substring(0, Math.min(600, interp.getShortExplain().length())));
			b.setInterpretation(interp);
		});
		
		FileUtils.write(out, PoliscoreUtil.getObjectMapper().writeValueAsString(data), "UTF-8");
	}
	
//	protected List<String> parseBillSponsors(String text)
//	{
//		return Jsoup.parse(text).select("bill form action action-desc sponsor,cosponsor").stream().map(e -> e.text()).collect(Collectors.toList());
//	}
    
    protected String generateBillName(String url)
    {
    	return generateBillName(url, -1);
    }
    
    @SneakyThrows
    protected String generateBillName(String url, int sliceIndex)
    {
		URI uri = new URI(url);
		String path = uri.getPath();
		String billName = path.substring(path.lastIndexOf('/') + 1);
		
		if (billName.contains("."))
		{
			billName = billName.substring(0, billName.lastIndexOf("."));
		}
		
		if (billName.contains("BILLS-"))
		{
			billName = billName.replace("BILLS-", "");
		}
		
		if (sliceIndex != -1)
		{
			billName += "-" + String.valueOf(sliceIndex);
		}

		return billName;
    }
    
    @SneakyThrows
	public Optional<BillText> getBillText(Bill bill)
	{
//		val parent = new File(PoliscoreUtil.APP_DATA, "bill-text/" + bill.getCongress() + "/" + bill.getType());
//		
//		val text = Arrays.asList(parent.listFiles()).stream()
//				.filter(f -> f.getName().contains(bill.getCongress() + bill.getType().getName().toLowerCase() + bill.getNumber()))
//				.sorted((a,b) -> BillTextPublishVersion.parseFromBillTextName(a.getName()).billMaturityCompareTo(BillTextPublishVersion.parseFromBillTextName(b.getName())))
//				.findFirst();
//		
//		if (text.isPresent())
//		{
//			return Optional.of(PoliscoreUtil.getObjectMapper().readValue(FileUtils.readFileToString(text.get(), "UTF-8"), BillText.class));
//		}
//		else
//		{
//			return Optional.empty();
//		}
    	
    	return s3.get(BillText.generateId(bill.getId()), BillText.class);
	}
    
    public boolean hasBillText(Bill bill)
    {
    	return s3.exists(BillText.generateId(bill.getId()), BillText.class);
    }
    
    public Optional<Bill> getById(String id)
	{
		return memService.get(id, Bill.class);
	}
}
