package ch.poliscore.service;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.PoliscoreUtil;
import ch.poliscore.interpretation.BillTextPublishVersion;
import ch.poliscore.interpretation.BillType;
import ch.poliscore.model.Bill;
import ch.poliscore.model.Legislator;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillCosponsor;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillSponsor;
import ch.poliscore.service.storage.ApplicationDataStoreIF;
import ch.poliscore.view.USCBillView;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.utils.StringUtils;

@ApplicationScoped
@Priority(4)
public class BillService {
	
	@Inject
	private ApplicationDataStoreIF pServ;
	
	@Inject
	protected LegislatorService lService;
	
	@SneakyThrows
	public void importUscData(FileInputStream fos) {
		val view = new ObjectMapper().readValue(fos, USCBillView.class);
		
//		String text = fetchBillText(view.getUrl());
    	
    	val bill = new Bill();
//    	bill.setText(text);
    	bill.setName(view.getShort_title());
    	bill.setCongress(Integer.parseInt(view.getCongress()));
    	bill.setType(BillType.valueOf(view.getBill_type().toUpperCase()));
    	bill.setNumber(Integer.parseInt(view.getNumber()));
    	bill.setStatusUrl(view.getUrl());
    	bill.setIntroducedDate(view.getIntroduced_at());
    	bill.setSponsor(view.getSponsor());
    	bill.setCosponsors(view.getCosponsors());
    	
    	if (view.getSponsor() != null && !StringUtils.isBlank(view.getSponsor().getBioguide_id()))
    	{
			Legislator leg = lService.getById(view.getSponsor().getBioguide_id()).orElseThrow();
			
			LegislatorBillSponsor interaction = new LegislatorBillSponsor();
			interaction.setBillId(bill.getId());
			interaction.setDate(view.getIntroduced_at());
			leg.addBillInteraction(interaction);
			
			lService.persist(leg);
    	}
    	
    	view.getCosponsors().forEach(cs -> {
    		if (!StringUtils.isBlank(cs.getBioguide_id())) {
	    		Legislator leg = lService.getById(cs.getBioguide_id()).orElseThrow();
				
				LegislatorBillCosponsor interaction = new LegislatorBillCosponsor();
				interaction.setBillId(bill.getId());
				interaction.setDate(view.getIntroduced_at());
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
	protected Optional<String> getBillText(Bill bill)
	{
		val parent = new File(PoliscoreUtil.APP_DATA, "bill-text/" + bill.getCongress() + "/" + bill.getType());
		
		val text = Arrays.asList(parent.listFiles()).stream()
				.filter(f -> f.getName().contains(bill.getCongress() + bill.getType().getName().toLowerCase() + bill.getNumber()))
				.sorted((a,b) -> BillTextPublishVersion.parseFromBillTextName(a.getName()).billMaturityCompareTo(BillTextPublishVersion.parseFromBillTextName(b.getName())))
				.findFirst();
		
		if (text.isPresent())
		{
			return Optional.of(FileUtils.readFileToString(text.get(), "UTF-8"));
		}
		else
		{
			return Optional.empty();
		}
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
