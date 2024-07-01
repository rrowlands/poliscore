package us.poliscore.entrypoint;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.time.LocalDate;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import us.poliscore.model.Legislator;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.storage.MemoryPersistenceService;

/**
 * Fetches images from congress.gov for all the legislators and uploads them to our S3 repository.
 */
@QuarkusMain(name="S3ImageDatabaseBuilder")
public class S3ImageDatabaseBuilder implements QuarkusApplication {
	
	private static final String BUCKET_NAME = "poliscore-prod-public";
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private MemoryPersistenceService memService;
	
	private S3Client client;
	
	@SneakyThrows
	protected void process() throws IOException
	{
		legService.importLegislators();
		
		int success = 0;
		int skipped = 0;
		
		val legs = memService.query(Legislator.class).stream().filter(l -> l.getBirthday() == null || l.getBirthday().isAfter(LocalDate.of(1900,1,1))).toList();
		
		Log.info("About to fetch " + legs.size() + " legislator images from congress.gov.");
		
		for (Legislator leg : legs)	{
			try
			{
				if (exists(leg))
				{
					Log.info("Image for legislator " + leg.getId() + " already exists on S3. Skipping...");
					continue;
				}
				
				byte[] bytes = getImage(leg);
				
				if (!isJPEG(bytes)) {
					Log.warn("congress.gov sent invalid bytes for " + leg.getName().getOfficial_full() + " " + leg.getBioguideId() + ". skipping...");
					skipped++;
					continue;
				} else {
					upload(bytes, leg.getId() + ".jpg");
					success++;
				}
				
//				Thread.sleep(2000); // Don't hammer their servers
			}
			catch (IOException e)
			{
				Log.warn("Could not find image for congressman " + leg.getName().getOfficial_full() + " " + leg.getBioguideId());
				e.printStackTrace();
			}
		}
		
		Log.info("Successfully imported " + success + " images. Skipped " + skipped);
	}
	
	@SneakyThrows
	private static Boolean isJPEG(byte[] bytes) throws Exception {
	    @Cleanup DataInputStream ins = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)));
	    
	    return ins.readInt() == 0xffd8ffe0;
	}
	
	private boolean exists(Legislator leg)
	{
		try
		{
			val resp = getClient().headObject(HeadObjectRequest.builder()
					.bucket(BUCKET_NAME)
					.key(leg.getId() + ".jpg")
					.build());
			
			return true;
		}
		catch (NoSuchKeyException ex)
		{
			return false;
		}
	}
	
	@SneakyThrows
	private byte[] getImage(Legislator leg) throws IOException, InterruptedException
	{
		val url = "https://www.congress.gov/img/member/" + leg.getBioguideId().toLowerCase() + "_200.jpg";
		
		String keyPassphrase = "changeit";

		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(S3ImageDatabaseBuilder.class.getResourceAsStream("keystore"), keyPassphrase.toCharArray());

		SSLContext sslContext = SSLContexts.custom()
		        .loadKeyMaterial(keyStore, null)
		        .build();

		CloseableHttpClient httpClient = HttpClients.custom().setSSLContext(sslContext).build();
		
		// Congress.gov is pretty picky about some of these headers. Without them you can get back an html file or just gibberish
		val get = new HttpGet(url);
		get.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:126.0) Gecko/20100101 Firefox/126.0");
		get.addHeader("Accept", "image/avif,image/webp,*/*");
		get.addHeader("Sec-Fetch-Dest", "image");
		
		HttpResponse resp = httpClient.execute(get);
		return IOUtils.toByteArray(resp.getEntity().getContent());
	}
	
	@SneakyThrows
	private void upload(byte[] image, String key)
	{
        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build();

        getClient().putObject(putOb, RequestBody.fromBytes(image));
        
        Log.info("Uploaded to S3 " + key);
	}
	
	private S3Client getClient()
	{
		if (client == null)
		{
			client = S3Client.builder()
	                .build();
		}
		
		return client;
	}
	
	public static void main(String[] args) {
		Quarkus.run(S3ImageDatabaseBuilder.class, args);
	}

	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
	
}
