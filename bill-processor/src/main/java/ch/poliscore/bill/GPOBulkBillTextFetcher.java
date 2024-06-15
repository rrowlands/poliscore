package ch.poliscore.bill;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import ch.poliscore.PoliscoreUtil;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import lombok.SneakyThrows;
import lombok.val;
import net.lingala.zip4j.ZipFile;

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
	
	@SneakyThrows
	protected void process()
	{
		val store = new File(PoliscoreUtil.APP_DATA, "bill-text");
		store.mkdirs();
		
		for (int congress : FETCH_CONGRESS)
		{
			val congressStore = new File(store, String.valueOf(congress));
			congressStore.mkdir();
			
			for (int session : FETCH_SESSION)
			{
				for (String billType : FETCH_BILL_TYPE)
				{
					val typeStore = new File(congressStore, String.valueOf(billType));
					typeStore.mkdir();
					
					val doneMarker = new File(typeStore, "done.marker");
					
					if (!doneMarker.exists())
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
						
						FileUtils.write(doneMarker, "done!");
					}
				}
			}
		}
		
		Log.info("Downloaded all bill text!");
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
