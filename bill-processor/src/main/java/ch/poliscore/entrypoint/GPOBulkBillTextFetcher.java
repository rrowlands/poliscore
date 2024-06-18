package ch.poliscore.entrypoint;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;

import ch.poliscore.PoliscoreUtil;
import ch.poliscore.interpretation.BillTextPublishVersion;
import ch.poliscore.interpretation.BillType;
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
import net.lingala.zip4j.ZipFile;
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
	
	public static int[] FETCH_SESSION = new int[] { 1, 2 };
	
	public static List<String> FETCH_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	@Inject
	private S3PersistenceService s3;
	
	@SneakyThrows
	protected void process()
	{
		val store = new File(PoliscoreUtil.APP_DATA, "bill-text");
		FileUtils.deleteQuietly(store);
		store.mkdirs();
		
		for (int congress : PoliscoreUtil.SUPPORTED_CONGRESSES)
		{
			val congressStore = new File(store, String.valueOf(congress));
			congressStore.mkdir();
			
			
			for (String billType : FETCH_BILL_TYPE)
			{
				val typeStore = new File(congressStore, String.valueOf(billType));
				typeStore.mkdir();
				
				// Download and unzip
				for (int session : FETCH_SESSION)
				{
					val url = URL_TEMPLATE.replaceAll("\\{\\{congress\\}\\}", String.valueOf(congress))
								.replaceAll("\\{\\{session\\}\\}", String.valueOf(session))
								.replaceAll("\\{\\{type\\}\\}", String.valueOf(billType));
					
					val zip = new File(typeStore, congress + "-" + billType + ".zip");
					
					Log.info("Downloading " + url + " to " + zip.getAbsolutePath());
					IOUtils.copy(new URL(url).openStream(), new FileOutputStream(zip));
					
					Log.info("Extracting " + zip.getAbsolutePath() + " to " + typeStore.getAbsolutePath());
					new ZipFile(zip).extractAll(typeStore.getAbsolutePath());
					
					FileUtils.delete(zip);
				}
				
				// Upload to S3
				Set<String> processedBills = new HashSet<String>();
				for (File f : PoliscoreUtil.allFilesWhere(typeStore, f -> f.getName().endsWith(".xml")).stream()
						.sorted(Comparator.comparing(File::getName).thenComparing((a,b) -> BillTextPublishVersion.parseFromBillTextName(a.getName()).billMaturityCompareTo(BillTextPublishVersion.parseFromBillTextName(b.getName()))))
						.collect(Collectors.toList()))
				{
					String number = f.getName().replace("BILLS-" + congress + billType, "").replaceAll("\\D", "");
					val billId = Bill.generateId(congress, BillType.valueOf(billType.toUpperCase()), Integer.parseInt(number));
					
					if (!processedBills.contains(billId) && !s3.exists(billId, BillText.class))
					{
						val date = parseDate(f);
						
						BillText bt = new BillText(billId, FileUtils.readFileToString(f, "UTF-8"), date);
						s3.store(bt);
						
						processedBills.add(billId);
					}
				}
			}
		}
		
		Log.info("Downloaded all bill text!");
	}
	
	@SneakyThrows
	protected LocalDate parseDate(File f)
	{
		val text = Jsoup.parse(f, "UTF-8").select("bill dublinCore dc|date").text();
		
		if (StringUtils.isBlank(text)) return null;
		
		return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
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
