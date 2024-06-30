package us.poliscore.service;

import java.io.FileInputStream;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.utils.StringUtils;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Legislator;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillCosponsor;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillSponsor;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.storage.MemoryPersistenceService;
import us.poliscore.service.storage.S3PersistenceService;
import us.poliscore.view.USCBillView;

@ApplicationScoped
@Priority(4)
public class BillService {
	
	@Inject
	private S3PersistenceService s3;
	
	@Inject
	private MemoryPersistenceService pServ;
	
	@Inject
	protected LegislatorService lService;
	
	@SneakyThrows
	public void importUscData(FileInputStream fos) {
		val view = PoliscoreUtil.getObjectMapper().readValue(fos, USCBillView.class);
		
//		String text = fetchBillText(view.getUrl());
    	
    	val bill = new Bill();
//    	bill.setText(text);
    	bill.setName(view.getBillName());
    	bill.setCongress(Integer.parseInt(view.getCongress()));
    	bill.setType(BillType.valueOf(view.getBill_type().toUpperCase()));
    	bill.setNumber(Integer.parseInt(view.getNumber()));
//    	bill.setStatusUrl(view.getUrl());
    	bill.setIntroducedDate(view.getIntroduced_at());
    	bill.setSponsor(view.getSponsor() == null ? null : view.getSponsor().convert());
    	bill.setCosponsors(view.getCosponsors().stream().map(s -> s.convert()).collect(Collectors.toList()));
    	
    	if (view.getSponsor() != null && !StringUtils.isBlank(view.getSponsor().getBioguide_id()))
    	{
			Legislator leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, view.getSponsor().getBioguide_id())).orElseThrow();
			
			LegislatorBillSponsor interaction = new LegislatorBillSponsor();
			interaction.setBillId(bill.getId());
			interaction.setDate(view.getIntroduced_at());
			interaction.setBillName(bill.getName());
			leg.addBillInteraction(interaction);
			
			lService.persist(leg);
    	}
    	
    	view.getCosponsors().stream().filter(cs -> view.getSponsor() == null || !view.getSponsor().getBioguide_id().equals(cs.getBioguide_id())).forEach(cs -> {
    		if (!StringUtils.isBlank(cs.getBioguide_id())) {
	    		Legislator leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, cs.getBioguide_id())).orElseThrow();
				
				LegislatorBillCosponsor interaction = new LegislatorBillCosponsor();
				interaction.setBillId(bill.getId());
				interaction.setDate(view.getIntroduced_at());
				interaction.setBillName(bill.getName());
				leg.addBillInteraction(interaction);
				
				lService.persist(leg);
    		}
    	});
    	
    	archiveBill(bill);
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
    	
    	return s3.retrieve(BillText.generateId(bill.getId()), BillText.class);
	}
    
    public Optional<Bill> getById(String id)
	{
		return pServ.retrieve(id, Bill.class);
	}
    
    protected void archiveBill(Bill bill)
    {
    	pServ.store(bill);
    }
}
