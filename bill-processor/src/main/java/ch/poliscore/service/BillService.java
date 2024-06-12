package ch.poliscore.service;

import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.model.Bill;
import ch.poliscore.model.Legislator;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillCosponsor;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillSponsor;
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
	private PersistenceServiceIF pServ;
	
	@Inject
	protected LegislatorService lService;
	
	@SneakyThrows
	public void importUscData(FileInputStream fos) {
		val view = new ObjectMapper().readValue(fos, USCBillView.class);
		
//		String text = fetchBillText(view.getUrl());
    	
    	val bill = new Bill();
//    	bill.setText(text);
    	bill.setName(view.getShort_title());
    	bill.setStatusUrl(view.getUrl());
    	bill.setId(view.getBill_id());
    	bill.setType(view.getBill_type());
    	bill.setLastUpdated(view.getUpdated_at());
    	bill.setSponsor(view.getSponsor());
    	bill.setCosponsors(view.getCosponsors());
    	
    	if (view.getSponsor() != null && !StringUtils.isBlank(view.getSponsor().getBioguide_id()))
    	{
			Legislator leg = lService.getById(view.getSponsor().getBioguide_id());
			
			LegislatorBillSponsor interaction = new LegislatorBillSponsor();
			interaction.setBillId(bill.getId());
			interaction.setDate(view.getUpdated_at());
			leg.addBillInteraction(interaction);
			
			lService.persist(leg);
    	}
    	
    	view.getCosponsors().forEach(cs -> {
    		if (!StringUtils.isBlank(cs.getBioguide_id())) {
	    		Legislator leg = lService.getById(cs.getBioguide_id());
				
				LegislatorBillCosponsor interaction = new LegislatorBillCosponsor();
				interaction.setBillId(bill.getId());
				interaction.setDate(view.getUpdated_at());
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
    protected String fetchBillText(String url)
    {
    	String billText = IOUtils.toString(new URL(url), "UTF-8");
    	
    	System.out.println("Fetched bill text from url [" + url + "].");
    	
    	return billText;
    }
    
    protected void archiveBill(Bill bill)
    {
    	pServ.store(bill);
    }
}
