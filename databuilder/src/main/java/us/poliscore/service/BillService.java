package us.poliscore.service;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.time.LocalDate; // Added for parsing dates
import java.time.format.DateTimeFormatter;
import java.util.ArrayList; // Added for cosponsors list
import java.util.Arrays;
import java.util.List; // Already present
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
import us.poliscore.model.InterpretationOrigin;
import us.poliscore.model.LegislativeChamber;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Persistable;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillIssueStat;
import us.poliscore.model.bill.BillStatus;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorName; // Added for BillSponsor
// import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillCosponsor; // Not directly used in new logic
// import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillSponsor; // Not directly used in new logic
import us.poliscore.model.press.PressInterpretation;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;
// import us.poliscore.view.USCBillView; // Commented out as it's related to old logic
import us.poliscore.view.legiscan.LegiscanBillView; // Added import

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
	public void importBills(List<LegiscanBillView> legiscanBills) {
		if (legiscanBills == null || legiscanBills.isEmpty()) {
			Log.info("No bills to import from Legiscan.");
			return;
		}
		Log.info("Importing " + legiscanBills.size() + " bills from Legiscan for session " + PoliscoreUtil.currentSessionNumber + " in namespace " + PoliscoreUtil.currentNamespace);
		int importedCount = 0;

		for (LegiscanBillView view : legiscanBills) {
			try {
				Bill bill = new Bill();
				bill.setLegiscanBillId(view.getBill_id());
				bill.setNamespace(PoliscoreUtil.currentNamespace);
				bill.setSession(PoliscoreUtil.currentSessionNumber);

				BillType type = determineBillType(view.getNumber(), PoliscoreUtil.currentNamespace);
				int number = extractBillNumber(view.getNumber(), type);
				bill.setType(type);
				bill.setNumber(number);

				bill.setName(view.getTitle());

				// Assuming last_action_date is a suitable proxy for introduced_date and is in YYYY-MM-DD
				if (view.getLast_action_date() != null && !view.getLast_action_date().isEmpty()) {
					try {
						LocalDate date = LocalDate.parse(view.getLast_action_date());
						bill.setIntroducedDate(date);
						bill.setLastActionDate(date);
					} catch (Exception e) {
						Log.warn("Could not parse date for bill " + view.getBill_id() + ": " + view.getLast_action_date(), e);
					}
				}

				bill.setStatus(buildStatusFromLegiscan(view, PoliscoreUtil.currentNamespace));
				
				// Sponsors
				if (view.getSponsors() != null && !view.getSponsors().isEmpty()) {
					String sponsorLegiscanId = view.getSponsors().get(0);
					// This lookup can be inefficient. Consider optimizing if performance is an issue.
					Optional<Legislator> sponsorOpt = memService.queryAll(Legislator.class).stream()
						.filter(l -> PoliscoreUtil.currentNamespace.equals(l.getNamespace()) && 
									 PoliscoreUtil.currentSessionNumber.equals(l.getSession()) &&
									 sponsorLegiscanId.equals(l.getLegiscanId()))
						.findFirst();
					if (sponsorOpt.isPresent()) {
						Legislator sponsorLeg = sponsorOpt.get();
						Bill.BillSponsor billSponsor = new Bill.BillSponsor(sponsorLeg.getId(), sponsorLeg.getName());
						bill.setSponsor(billSponsor);
					} else {
						Log.warn("Could not find sponsor legislator with Legiscan ID: " + sponsorLegiscanId + " for bill " + view.getBill_id());
					}
				}

				// Cosponsors
				if (view.getCosponsors() != null && !view.getCosponsors().isEmpty()) {
					List<Bill.BillSponsor> cosponsorsList = new ArrayList<>();
					for (String cosponsorLegiscanId : view.getCosponsors()) {
						Optional<Legislator> cosponsorOpt = memService.queryAll(Legislator.class).stream()
							.filter(l -> PoliscoreUtil.currentNamespace.equals(l.getNamespace()) && 
										 PoliscoreUtil.currentSessionNumber.equals(l.getSession()) &&
										 cosponsorLegiscanId.equals(l.getLegiscanId()))
							.findFirst();
						if (cosponsorOpt.isPresent()) {
							Legislator cosponsorLeg = cosponsorOpt.get();
							cosponsorsList.add(new Bill.BillSponsor(cosponsorLeg.getId(), cosponsorLeg.getName()));
						} else {
							Log.warn("Could not find cosponsor legislator with Legiscan ID: " + cosponsorLegiscanId + " for bill " + view.getBill_id());
						}
					}
					bill.setCosponsors(cosponsorsList);
				}
				
				bill.setBillPdfUrl(view.getText_url()); // Assuming LegiscanBillView.text_url exists and is the PDF link

				memService.put(bill);
				importedCount++;
			} catch (Exception e) {
				Log.error("Failed to import bill: " + view.getBill_id() + " - " + view.getTitle(), e);
			}
		}
		Log.info("Successfully imported " + importedCount + " bills into memory.");
	}

	private BillType determineBillType(String legiscanBillNumber, LegislativeNamespace namespace) {
		if (StringUtils.isBlank(legiscanBillNumber)) return BillType.BILL; // Default

		String numUpper = legiscanBillNumber.toUpperCase().replaceAll("[^A-Z]", ""); // Get only letters

		if (namespace == LegislativeNamespace.US_CONGRESS) {
			if (numUpper.startsWith("HR")) return BillType.HR; // House Bill
			if (numUpper.startsWith("S")) return BillType.S;   // Senate Bill
			if (numUpper.startsWith("HRES")) return BillType.HRES;
			if (numUpper.startsWith("SRES")) return BillType.SRES;
			if (numUpper.startsWith("HJRES")) return BillType.HJRES;
			if (numUpper.startsWith("SJRES")) return BillType.SJRES;
			if (numUpper.startsWith("HCONRES")) return BillType.HCONRES;
			if (numUpper.startsWith("SCONRES")) return BillType.SCONRES;
			Log.warn("Unknown US_CONGRESS bill type for: " + legiscanBillNumber);
			return BillType.BILL; // Default for unmapped US Congress types
		} else { // State namespaces
			if (numUpper.startsWith("AB")) return BillType.BILL; // Assembly Bill
			if (numUpper.startsWith("SB")) return BillType.BILL; // Senate Bill
			if (numUpper.startsWith("HB")) return BillType.BILL; // House Bill
			// Resolutions
			if (numUpper.contains("RES") || numUpper.startsWith("SR") || numUpper.startsWith("HR")) return BillType.RESOLUTION;
			// Add more state-specific mappings if needed
			Log.debug("Defaulting to BILL for state bill number: " + legiscanBillNumber);
			return BillType.BILL; // Default for states
		}
	}

	private int extractBillNumber(String legiscanBillNumber, BillType determinedType) {
		if (StringUtils.isBlank(legiscanBillNumber)) return 0;
		String numericPart = legiscanBillNumber.replaceAll("[^0-9]", ""); // Get only numbers
		try {
			return Integer.parseInt(numericPart);
		} catch (NumberFormatException e) {
			Log.warn("Could not parse bill number from: " + legiscanBillNumber + " (type: " + determinedType.getName() + ")", e);
			return 0;
		}
	}

	private BillStatus buildStatusFromLegiscan(LegiscanBillView view, LegislativeNamespace namespace) {
		BillStatus status = new BillStatus();
		status.setSourceStatus(view.getStatus() + (view.getLast_action() != null ? " - " + view.getLast_action() : ""));
		status.setDescription(view.getLast_action() != null ? view.getLast_action() : view.getStatus());

		String statusLower = view.getStatus().toLowerCase();
		if (statusLower.contains("passed") || statusLower.contains("enacted") || statusLower.contains("signed") || statusLower.contains("adopted")) {
			status.setProgress(1.0f);
		} else if (statusLower.contains("failed") || statusLower.contains("vetoed")) {
			status.setProgress(0.1f); // Died
		} else {
			status.setProgress(0.05f); // Introduced/Pending
		}
		// This is a very basic placeholder. Real status mapping would be more complex.
		return status;
	}
	
	@SneakyThrows
	// protected void importUscBill(FileInputStream fos, String session) { // Old method
	protected void importSingleBill_Old(FileInputStream fos, String session) { // Renamed
		// val view = PoliscoreUtil.getObjectMapper().readValue(fos, USCBillView.class);
		
//		String text = fetchBillText(view.getUrl());
    	
    	// val bill = new Bill(); // Old logic
//    	bill.setText(text);
    	// bill.setName(view.getBillName());
    	// bill.setSession(Integer.parseInt(view.getCongress()));
    	// bill.setType(BillType.valueOf(view.getBill_type().toUpperCase()));
    	// bill.setNumber(Integer.parseInt(view.getNumber()));
    	// bill.setStatus(buildStatus(view));
//    	bill.setStatusUrl(view.getUrl());
    	// bill.setIntroducedDate(view.getIntroduced_at());
    	// bill.setSponsor(view.getSponsor() == null ? null : view.getSponsor().convert(session, memService));
    	// bill.setCosponsors(view.getCosponsors().stream().map(s -> s.convert(session, memService)).collect(Collectors.toList()));
    	// bill.setLastActionDate(view.getLastActionDate());
    	
    	// if (view.getSponsor() != null && !StringUtils.isBlank(view.getSponsor().getBioguide_id())) // Old logic
    	// {
			// val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.currentSessionNumber, view.getSponsor().getBioguide_id())); // Updated
			
			// if (leg.isPresent()) {
				// LegislatorBillSponsor interaction = new LegislatorBillSponsor();
				// interaction.setLegId(leg.get().getId());
				// interaction.setBillId(bill.getId());
				// interaction.setDate(view.getIntroduced_at());
				// interaction.setBillName(bill.getName());
				// leg.get().addBillInteraction(interaction);
				
				// memService.put(leg.get());
			// }
    	// }
    	
    	// view.getCosponsors().stream().filter(cs -> view.getSponsor() == null || !view.getSponsor().getBioguide_id().equals(cs.getBioguide_id())).forEach(cs -> { // Old logic
    		// if (!StringUtils.isBlank(cs.getBioguide_id())) {
	    		// val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.currentSessionNumber, cs.getBioguide_id())); // Updated
				
	    		// if (leg.isPresent()) {
					// LegislatorBillCosponsor interaction = new LegislatorBillCosponsor();
					// interaction.setLegId(leg.get().getId());
					// interaction.setBillId(bill.getId());
					// interaction.setDate(view.getIntroduced_at());
					// interaction.setBillName(bill.getName());
					// leg.get().addBillInteraction(interaction);
					
					// memService.put(leg.get());
	    		// }
    		// }
    	// });
    	
    	// memService.put(bill); // Old logic
	}
	
	// public BillStatus buildStatus(USCBillView view) { // Old logic, uses USCBillView
	    // BillStatus status = new BillStatus();
	    // status.setSourceStatus(view.getStatus());
	    
	    // final LegislativeChamber chamber = BillType.getOriginatingChamber(BillType.valueOf(view.getBill_type().toUpperCase()));
	    // final String stat = view.getStatus().toUpperCase();
	    // final boolean sessionOver = CongressionalSession.of(Integer.parseInt(view.getCongress())).isOver();

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
	
	public void populatePressInterps(BillInterpretation interp)
	{
		var pressInterps = s3.query(PressInterpretation.class, Persistable.getClassStorageBucket(PressInterpretation.class), interp.getBillId().replace(Bill.ID_CLASS_PREFIX + "/", ""));
		
//		pressInterps = pressInterps.stream().filter(i -> i.getBillId().equals(interp.getBillId()) && !InterpretationOrigin.POLISCORE.equals(i.getOrigin()) && !i.isNoInterp()).collect(Collectors.toList());
		
		pressInterps = pressInterps.stream()
			    .filter(i -> {
			        try {
			            return i.getBillId().equals(interp.getBillId())
			                && !InterpretationOrigin.POLISCORE.equals(i.getOrigin())
			                && !i.isNoInterp();
			        } catch (Exception e) {
			            Log.warn("Skipping press interpretation due to error: " + i, e);
			            return false; // skip this item if it errors
			        }
			    })
			    .collect(Collectors.toList());

		
		interp.setPressInterps(pressInterps);
	}

	public void ddbPersist(Bill b, BillInterpretation interp)
	{
		populatePressInterps(interp);
		b.setInterpretation(interp);
		ddb.put(b);
		
		for(TrackedIssue issue : TrackedIssue.values()) {
			ddb.put(new BillIssueStat(issue, b.getImpact(issue), b));
		}
	}
	
	public List<PressInterpretation> getAllPressInterps(String billId)
	{
		var pressInterps = s3.query(PressInterpretation.class, Persistable.getClassStorageBucket(PressInterpretation.class), billId.replace(Bill.ID_CLASS_PREFIX + "/", ""));
		
		pressInterps = pressInterps.stream().filter(i -> !InterpretationOrigin.POLISCORE.equals(i.getOrigin())).collect(Collectors.toList());
		
		return pressInterps;
	}
	
//	@SneakyThrows
//	public void saveDatabaseBillIndex() {
//		DateTimeFormatter usFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy");
//		
//		val data = memService.queryAll(Bill.class).stream()
//			.filter(b -> b.isIntroducedInSession(PoliscoreUtil.CURRENT_SESSION))
//			.map(b -> {
//				return Arrays.asList(b.getId(), b.getLastUpdateDate().format(usFormat));
//			})
//			.sorted((a,b) -> a.get(0).compareTo(b.get(0)))
//			.toList();
//		
//		
//	}
	
	@SneakyThrows
	public void generateBillWebappIndex() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/resources/bills.index");
		
		DateTimeFormatter usFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy"); // Keep for date formatting if needed
		
		val data = memService.queryAll(Bill.class).stream()
			.filter(b -> b.getNamespace() == PoliscoreUtil.currentNamespace && 
						 b.getSession().equals(PoliscoreUtil.currentSessionNumber) && 
						 s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
			.map(b -> {
				// The bill name can come from the interpretation so we have to fetch it.
				b.setInterpretation(s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class).orElseThrow());
				
				// The filter already ensures namespace and session match, so this check might be redundant
				// but kept for safety or if the filter changes.
				if (b.getNamespace() == PoliscoreUtil.currentNamespace && b.getSession().equals(PoliscoreUtil.currentSessionNumber)) {
					return Arrays.asList(b.getId(), b.getName());
				} else {
					// This case should ideally not be hit if the filter is correct.
					// If PoliscoreUtil.SUPPORTED_CONGRESSES is used, this logic for different sessions might be relevant.
					return Arrays.asList(b.getId(), b.getName() + " (" + (b.getIntroducedDate() != null ? b.getIntroducedDate().format(usFormat) : "N/A") + ")");
				}
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
