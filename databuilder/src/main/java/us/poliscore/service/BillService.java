package us.poliscore.service;

import java.io.File;
import java.net.URI;
import java.time.format.DateTimeFormatter;
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
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.InterpretationOrigin;
import us.poliscore.model.LegislativeChamber;
import us.poliscore.model.Persistable;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillIssueStat;
import us.poliscore.model.bill.BillStatus;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.press.PressInterpretation;
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
	
	@Inject
	private USCService usc;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public void importBills()
	{
		usc.importUscBills();
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
		
		DateTimeFormatter usFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy");
		
		val data = memService.queryAll(Bill.class).stream()
			.filter(b -> PoliscoreUtil.SUPPORTED_CONGRESSES.stream().anyMatch(s -> b.isIntroducedInSession(s)) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
			.map(b -> {
				// The bill name can come from the interpretation so we have to fetch it.
				b.setInterpretation(s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class).orElseThrow());
				
				if (b.isIntroducedInSession(PoliscoreUtil.CURRENT_SESSION)) {
					return Arrays.asList(b.getId(), b.getName());
				} else {
					return Arrays.asList(b.getId(), b.getName() + " (" + b.getIntroducedDate().format(usFormat) + ")");
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
