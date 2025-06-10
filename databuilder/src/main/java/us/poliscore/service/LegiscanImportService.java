package us.poliscore.service;

import java.time.LocalDate;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.utils.StringUtils;
import us.poliscore.PoliscoreUtil;
import us.poliscore.legiscan.service.LegiscanService;
import us.poliscore.legiscan.view.LegiscanBillView;
import us.poliscore.legiscan.view.LegiscanPeopleView;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillCosponsor;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillSponsor;
import us.poliscore.service.storage.MemoryObjectService;

public class LegiscanImportService {
	
	@Inject
	private MemoryObjectService memService;
	
	private LegiscanService legiscan;
	
	@SneakyThrows
	public void importBills() {
		if (memService.query(Bill.class).size() > 0) return;
		
		long totalBills = 0;
		
		var masterList = legiscan.getMasterList(-1);  // TODO : sessionId
		
		for (var summary : masterList.getBills().values())
		{
			var bill = legiscan.getBill(summary.getBillId());
			
			importBill(bill);
		}
		
//		for (File fCongress : Arrays.asList(PoliscoreUtil.USC_DATA.listFiles()).stream()
//				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
//				.sorted((a,b) -> a.getName().compareTo(b.getName()))
//				.collect(Collectors.toList()))
//		{
//			if (!Integer.valueOf(PoliscoreUtil.CURRENT_SESSION.getNumber()).equals(Integer.valueOf(fCongress.getName()))) continue;
//			
//			val session = CongressionalSession.of(Integer.valueOf(fCongress.getName()));
//			
//			Log.info("Processing " + fCongress.getName() + " congress");
//			
//			for (val bt : BillService.PROCESS_BILL_TYPE)
//			{
//				Log.info("Processing bill types " + bt + " congress");
//				
//				for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "bills/" + bt), f -> f.getName().equals("data.json")))
//				{
//					try (var fos = new FileInputStream(data))
//					{
//						importUscBill(fos, String.valueOf(session.getNumber()));
//						totalBills++;
//					}
//				}
//			}
//		}
		
		Log.info("Imported " + totalBills + " bills");
	}
	
//	protected void importBill(LegiscanBillView view) {
//		val bill = new Bill();
//    	bill.setName(view.getTitle());
//    	bill.setSession(Integer.parseInt(view.getCongress()));
//    	bill.setType(BillType.valueOf(view.getBill_type().toUpperCase()));
//    	bill.setNumber(Integer.parseInt(view.getNumber()));
//    	bill.setStatus(buildStatus(view));
//    	bill.setIntroducedDate(view.getIntroduced_at());
//    	bill.setSponsor(view.getSponsor() == null ? null : view.getSponsor().convert(session, memService));
//    	bill.setCosponsors(view.getCosponsors().stream().map(s -> s.convert(session, memService)).collect(Collectors.toList()));
//    	bill.setLastActionDate(view.getLastActionDate());
//    	
//    	if (view.getSponsor() != null && !StringUtils.isBlank(view.getSponsor().getBioguide_id()))
//    	{
//			val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), view.getSponsor().getBioguide_id()));
//			
//			if (leg.isPresent()) {
//				LegislatorBillSponsor interaction = new LegislatorBillSponsor();
//				interaction.setLegId(leg.get().getId());
//				interaction.setBillId(bill.getId());
//				interaction.setDate(view.getIntroduced_at());
//				interaction.setBillName(bill.getName());
//				leg.get().addBillInteraction(interaction);
//				
//				memService.put(leg.get());
//			}
//    	}
//    	
//    	view.getCosponsors().stream().filter(cs -> view.getSponsor() == null || !view.getSponsor().getBioguide_id().equals(cs.getBioguide_id())).forEach(cs -> {
//    		if (!StringUtils.isBlank(cs.getBioguide_id())) {
//	    		val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), cs.getBioguide_id()));
//				
//	    		if (leg.isPresent()) {
//					LegislatorBillCosponsor interaction = new LegislatorBillCosponsor();
//					interaction.setLegId(leg.get().getId());
//					interaction.setBillId(bill.getId());
//					interaction.setDate(view.getIntroduced_at());
//					interaction.setBillName(bill.getName());
//					leg.get().addBillInteraction(interaction);
//					
//					memService.put(leg.get());
//	    		}
//    		}
//    	});
//    	
//    	memService.put(bill);
//	}
	
	protected void importBill(LegiscanBillView view) {
	    val bill = new Bill();

	    // Basic info
	    bill.setName(view.getTitle());
	    bill.setSession(Integer.parseInt(view.getSession().getCongress())); // TODO : Support other sessions
	    bill.setType(BillType.valueOf(view.getBillType().toUpperCase())); // TODO : Double check
	    bill.setNumber(Integer.parseInt(view.getBillNumber().replaceAll("[^0-9]", ""))); // Handles "HB123" style

	    // Status
	    bill.setStatus(buildStatus(view));
	    bill.setIntroducedDate(PoliscoreUtil.parseDate(view.getReferralDate(), view.getStatusDate()));
	    bill.setLastActionDate(PoliscoreUtil.parseDate(view.getStatusDate()));

	    // Sponsor
	    val sponsor = view.getSponsors().isEmpty() ? null : view.getSponsors().get(0);
	    bill.setSponsor(sponsor == null ? null : sponsor.convert(session, memService));

	    // Cosponsors (skip sponsor duplication)
	    val sponsorBioId = sponsor == null ? null : sponsor.getBioguide_id();
	    val cosponsors = view.getSponsors().stream()
	        .filter(s -> sponsorBioId == null || !s.getBioguide_id().equals(sponsorBioId))
	        .map(s -> s.convert(session, memService))
	        .collect(Collectors.toList());
	    bill.setCosponsors(cosponsors);

	    // Create sponsor interaction
	    if (sponsor != null && !StringUtils.isBlank(sponsorBioId)) {
	        val leg = lService.getById(Legislator.generateId(
	            LegislativeNamespace.US_CONGRESS,
	            PoliscoreUtil.CURRENT_SESSION.getNumber(),
	            sponsorBioId
	        ));

	        if (leg.isPresent()) {
	            val interaction = new LegislatorBillSponsor();
	            interaction.setLegId(leg.get().getId());
	            interaction.setBillId(bill.getId());
	            interaction.setDate(bill.getIntroducedDate());
	            interaction.setBillName(bill.getName());
	            leg.get().addBillInteraction(interaction);
	            memService.put(leg.get());
	        }
	    }

	    // Create cosponsor interactions
	    view.getSponsors().stream()
	        .filter(cs -> sponsorBioId == null || !cs.getBioguide_id().equals(sponsorBioId))
	        .filter(cs -> !StringUtils.isBlank(cs.getBioguide_id()))
	        .forEach(cs -> {
	            val leg = lService.getById(Legislator.generateId(
	                LegislativeNamespace.US_CONGRESS,
	                PoliscoreUtil.CURRENT_SESSION.getNumber(),
	                cs.getBioguide_id()
	            ));

	            if (leg.isPresent()) {
	                val interaction = new LegislatorBillCosponsor();
	                interaction.setLegId(leg.get().getId());
	                interaction.setBillId(bill.getId());
	                interaction.setDate(bill.getIntroducedDate());
	                interaction.setBillName(bill.getName());
	                leg.get().addBillInteraction(interaction);
	                memService.put(leg.get());
	            }
	        });

	    // Persist the bill
	    memService.put(bill);
	}

//	protected void importLegislator(LegiscanPeopleView view) {
//		Legislator leg = new Legislator();
//		leg.setName(view.getName().convert());
//		leg.setBioguideId(view.getId().getBioguide());
//		leg.setThomasId(view.getId().getThomas());
//		leg.setLisId(view.getId().getLis());
//		leg.setWikidataId(view.getId().getWikidata());
//		leg.setBirthday(view.getBio().getBirthday());
//		leg.setTerms(view.getTerms().stream().map(t -> t.convert()).collect(Collectors.toCollection(LegislatorLegislativeTermSortedSet::new)));
//		
//		if (leg.isMemberOfSession(PoliscoreUtil.CURRENT_SESSION))
//		{
//			leg.setSession(PoliscoreUtil.CURRENT_SESSION.getNumber());
//			
//			memService.put(leg);
//		}
//	}
	
	protected void importLegislator(LegiscanPeopleView view) {
	    if (view == null || StringUtils.isBlank(view.getName())) return;

	    val leg = new Legislator();

	    // Build and set name
	    val name = new Legislator.LegislatorName();
	    name.setFirst(view.getFirstName());
	    name.setLast(view.getLastName());
	    name.setOfficial_full(view.getName());
	    leg.setName(name);

	    // Set various IDs
	    leg.setBioguideId(view.getBioguideId());
	    leg.setLisId(view.getLisId());

	    // Set birthday with fallback
	    LocalDate birthday = PoliscoreUtil.parseDate(view.getBio() != null ? view.getBio().getBirthday() : null);
	    leg.setBirthday(birthday);

	    // TODO : Legiscan doesn't have a 'terms' concept
	    // Convert terms and attach
//	    Legislator.LegislatorLegislativeTermSortedSet terms = new Legislator.LegislatorLegislativeTermSortedSet();
//	    if (view.getTerms() != null) {
//	        view.getTerms().forEach(term -> {
//	            val converted = term.convert(); // should return a Legislator.LegislativeTerm
//	            if (converted != null) {
//	                terms.add(converted);
//	            }
//	        });
//	    }
//	    leg.setTerms(terms);

	    // If active in current session, add to that session
	    if (leg.isMemberOfSession(PoliscoreUtil.CURRENT_SESSION)) {
	        leg.setSession(PoliscoreUtil.CURRENT_SESSION.getNumber());
	        memService.put(leg);
	    }
	}

	
}
