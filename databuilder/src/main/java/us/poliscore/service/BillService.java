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
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillCosponsor;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillSponsor;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;
import us.poliscore.service.storage.S3PersistenceService;
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
			if (!PoliscoreUtil.SUPPORTED_CONGRESSES.contains(Integer.valueOf(fCongress.getName()))) continue;
			
			Log.info("Processing " + fCongress.getName() + " congress");
			
			for (val bt : PROCESS_BILL_TYPE)
			{
				Log.info("Processing bill types " + bt + " congress");
				
				for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "bills/" + bt), f -> f.getName().equals("data.json")))
				{
					try (var fos = new FileInputStream(data))
					{
						importUscBill(fos);
						totalBills++;
					}
				}
			}
		}
		
		Log.info("Imported " + totalBills + " bills");
	}
	
	@SneakyThrows
	protected void importUscBill(FileInputStream fos) {
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
			val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, view.getSponsor().getBioguide_id()));
			
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
	    		val leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, cs.getBioguide_id()));
				
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
    	
    	archiveBill(bill);
	}
	
	@SneakyThrows
	public void generateBillWebappIndex() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/resources/bills.index");
		
		val data = memService.query(Bill.class).stream()
			.filter(b -> b.isIntroducedInSession(CongressionalSession.S118) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
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
			.filter(b -> b.isIntroducedInSession(CongressionalSession.S118) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
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
    
    protected void archiveBill(Bill bill)
    {
    	memService.put(bill);
    }
}
