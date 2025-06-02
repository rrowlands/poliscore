package us.poliscore.service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.utils.StringUtils;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.LegislativeChamber;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillStatus;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillCosponsor;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillSponsor;
import us.poliscore.service.storage.MemoryObjectService;
import us.poliscore.view.USCBillView;

@ApplicationScoped
public class USCService {
	
	@Inject
	private MemoryObjectService memService;
	
	@Inject
	protected LegislatorService lService;
	
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
			
			for (val bt : BillService.PROCESS_BILL_TYPE)
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
    	bill.setLastActionDate(view.getLastActionDate());
    	
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
}
