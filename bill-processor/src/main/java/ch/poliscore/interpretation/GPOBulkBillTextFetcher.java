package ch.poliscore.interpretation;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;

import ch.poliscore.PoliscoreUtil;
import ch.poliscore.model.Bill;
import ch.poliscore.model.BillText;
import ch.poliscore.service.storage.S3PersistenceService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Used to fetch bulk bill data from the GPO's bulk bill store. More info at:
 * 
 * https://www.govinfo.gov/bulkdata/BILLS
 * 
 * Accomplishes in a few minutes what takes the USC bill text fetcher weeks to accomplish, however does not support congress before 113
 */
@QuarkusMain(name="GPOBulkBillTextFetcher")
public class GPOBulkBillTextFetcher implements QuarkusApplication {
	
	public static final String URL_TEMPLATE = "https://www.govinfo.gov/bulkdata/BILLS/{{congress}}/{{session}}/{{type}}/BILLS-{{congress}}-{{session}}-{{type}}.zip";
	
	public static int[] FETCH_CONGRESS = new int[] { 113, 114, 115, 116, 117, 118 };
	
	public static int[] FETCH_SESSION = new int[] { 1, 2 };
	
	public static List<String> FETCH_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	@Inject
	private S3PersistenceService s3;
	
	@SneakyThrows
	protected void process()
	{
		val store = new File(PoliscoreUtil.APP_DATA, "bill-text");
		store.mkdirs();
		
		for (int congress : FETCH_CONGRESS)
		{
			val congressStore = new File(store, String.valueOf(congress));
			congressStore.mkdir();
			
			
			for (String billType : FETCH_BILL_TYPE)
			{
				val typeStore = new File(congressStore, String.valueOf(billType));
				typeStore.mkdir();
				
//				for (int session : FETCH_SESSION)
//				{
//					val url = URL_TEMPLATE.replaceAll("\\{\\{congress\\}\\}", String.valueOf(congress))
//								.replaceAll("\\{\\{session\\}\\}", String.valueOf(session))
//								.replaceAll("\\{\\{type\\}\\}", String.valueOf(billType));
//					
//					val zip = new File(typeStore, congress + "-" + billType + ".zip");
//					
//					Log.info("Downloading " + url + " to " + zip.getAbsolutePath());
//					IOUtils.copy(new URL(url).openStream(), new FileOutputStream(zip));
//					
//					Log.info("Extracting " + zip.getAbsolutePath() + " to " + typeStore.getAbsolutePath());
//					new ZipFile(zip).extractAll(typeStore.getAbsolutePath());
//					
//					FileUtils.delete(zip);
//				}
				
				for (File f : PoliscoreUtil.allFilesWhere(typeStore, f -> f.getName().endsWith(".xml")))
				{
					String number = f.getName().replace("BILLS-" + congress + billType, "").replaceAll("\\D", "");
					val billId = Bill.generateId(congress, BillType.valueOf(billType.toUpperCase()), Integer.parseInt(number));
					val date = parseDate(f);
					
					BillText bt = new BillText(billId, FileUtils.readFileToString(f, "UTF-8"), date);
					s3.store(bt);
					
					FileUtils.delete(f);
				}
			}
		}
		
		Log.info("Downloaded all bill text!");
	}
	
	@SneakyThrows
	protected Date parseDate(File f)
	{
		val text = Jsoup.parse(f, "UTF-8").select("bill dublinCore dc|date").text();
		
		if (StringUtils.isBlank(text)) return null;
		
		return new SimpleDateFormat("yyyy-MM-DD").parse(text);
	}
	
	@SneakyThrows
	public Optional<String> getBillText(Bill bill)
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
	
	public static void main(String[] args) {
		Quarkus.run(GPOBulkBillTextFetcher.class, args);
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
	
}
