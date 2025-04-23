package us.poliscore.entrypoint;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.LocalDate;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.jsoup.Jsoup;

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
import us.poliscore.model.legislator.Legislator;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.storage.MemoryObjectService;

/**
 * Fetches images from congress.gov for all the legislators and uploads them to our S3 repository.
 * 
 * TODO : Legislators that do not have photos on congress.gov might have photos on bioguide.congress.gov. For example:
 * https://bioguide.congress.gov/search/bio/L000592
 * 
 * We could (or should?) fetch these from here:
 * https://github.com/unitedstates/images
 */
@QuarkusMain(name="S3ImageDatabaseBuilder")
public class S3ImageDatabaseBuilder implements QuarkusApplication {
	
	private static final String BUCKET_NAME = "poliscore-prod-public";
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private MemoryObjectService memService;
	
	private S3Client client;
	
	private int lastWaitMs = -1;
	private int lastWaitMs2 = -1;
	
	@SneakyThrows
	protected void process() throws IOException
	{
		legService.importLegislators();
		
		int success = 0;
		int skipped = 0;
		
		Log.info("Building list of legislators to fetch. This will take a minute...");
		
		val legs = memService.query(Legislator.class).stream()
				.filter(l -> l.getBirthday() == null || l.getBirthday().isAfter(LocalDate.of(1900,1,1)))
				.filter(l -> l.getTerms().size() > 0 && l.getTerms().last().getStartDate().isAfter(LocalDate.of(1990,1,1)))
				.filter(l -> !exists(l))
				.toList();
		
		Log.info("About to fetch " + legs.size() + " legislator images from congress.gov.");
		
		for (Legislator leg : legs)	{
			try
			{
				Optional<byte[]> bytes = getImage(leg);
				
				if (bytes.isEmpty()) { skipped++; continue; }
				
				upload(bytes.get(), leg.getId() + ".jpg");
				success++;
			}
			catch (Throwable t)
			{
				Log.warn("Could not find image for congressman " + leg.getName().getOfficial_full() + " " + leg.getBioguideId());
				t.printStackTrace();
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
	
	/**
	 * The legislator images on congress.gov do not follow a consistent pattern. The most consistent pattern seems to be something like: 
	 * 		https://www.congress.gov/img/member/" + leg.getBioguideId().toLowerCase() + "_200.jpg
	 * 
	 * And this actually works for about 90% or 95% of legislators. The rest of the legislators follow inconsistent naming conventions,
	 * for example John Peterson (P000263)'s image url is /img/member/h_peterson_john_20073196577_200.jpg.
	 * 
	 * This algorithm's job is to fetch the legislator's member page (at congress.gov/member), and then find the photo url on that page
	 * and then return that url.
	 * 
	 * @param leg
	 * @return
	 */
	@SneakyThrows
	private String scrapeImageUrlFromMemberPage(Legislator leg) {
	    val fallback = "https://www.congress.gov/img/member/" + leg.getBioguideId().toLowerCase() + "_200.jpg";

	    val memberUrl = "https://www.congress.gov/member/"
	        + leg.getName().getFirst().toLowerCase().replace(" ", "-")
	        + "-"
	        + leg.getName().getLast().toLowerCase().replace(" ", "-")
	        + "/"
	        + leg.getBioguideId();

	    // Reuse the exact SSL setup as before
	    KeyStore keyStore = KeyStore.getInstance("PKCS12");
	    keyStore.load(S3ImageDatabaseBuilder.class.getResourceAsStream("keystore"), "changeit".toCharArray());

	    SSLContext sslContext = SSLContexts.custom()
	        .loadKeyMaterial(keyStore, null)
	        .build();

	    CloseableHttpClient httpClient = HttpClients.custom()
	        .setSSLContext(sslContext)
	        .build();

	    val get = new HttpGet(memberUrl);
	    get.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:126.0) Gecko/20100101 Firefox/126.0");
	    get.addHeader("Accept", "text/html");

	    HttpResponse resp = httpClient.execute(get);
	    int status = resp.getStatusLine().getStatusCode();
	    @Cleanup InputStream is = resp.getEntity().getContent();

	    if (status >= 400) {
	        Log.warn("[" + leg.getBioguideId() + "] Failed to fetch member page: HTTP " + status);
	        return fallback;
	    }

	    val html = IOUtils.toString(is, "UTF-8");
	    val img = Jsoup.parse(html).selectFirst(".overview-member-column-picture > img");

	    if (img == null) {
	        return fallback;
	    } else {
	        return "https://www.congress.gov" + img.attr("src");
	    }
	}

	
	@SneakyThrows
	private Optional<byte[]> getImage(Legislator leg) {
	    String url = scrapeImageUrlFromMemberPage(leg);

	    final int MAX_RETRIES = 5;
	    int attempt = 0;
	    int backoffMs = 2000;

	    while (attempt < MAX_RETRIES) {
	        attempt++;

	        // Re-create HTTP client + SSL context every time (same as before)
	        KeyStore keyStore = KeyStore.getInstance("PKCS12");
	        keyStore.load(S3ImageDatabaseBuilder.class.getResourceAsStream("keystore"), "changeit".toCharArray());

	        SSLContext sslContext = SSLContexts.custom()
	            .loadKeyMaterial(keyStore, null)
	            .build();

	        CloseableHttpClient httpClient = HttpClients.custom()
	            .setSSLContext(sslContext)
	            .build();

	        val get = new HttpGet(url);
	        get.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:126.0) Gecko/20100101 Firefox/126.0");
	        get.addHeader("Accept", "image/avif,image/webp,*/*");
	        get.addHeader("Sec-Fetch-Dest", "image");

	        HttpResponse resp = httpClient.execute(get);
	        int status = resp.getStatusLine().getStatusCode();

	        @Cleanup InputStream is = resp.getEntity().getContent();

	        if (status == 429 || status == 403) {
	            Log.warn("[" + leg.getBioguideId() + "] Received " + status + " (rate limit). Waiting " + backoffMs + "ms...");
	            Thread.sleep(backoffMs);
	            backoffMs = Math.min(backoffMs * 2, 60000); // max 1 minute
	            continue;
	        }

	        if (status >= 400) {
	            val body = IOUtils.toString(is, "UTF-8");
	            Log.warn("[" + leg.getBioguideId() + "] HTTP " + status + ": " + body.substring(0, Math.min(body.length(), 300)));
	            return Optional.empty(); // don't retry 404s, 500s, etc.
	        }

	        byte[] image = IOUtils.toByteArray(is);
	        if (!isJPEG(image)) {
	            Log.warn("[" + leg.getBioguideId() + "] Invalid image data. Waiting " + backoffMs + "ms...");
	            Thread.sleep(backoffMs);
	            backoffMs = Math.min(backoffMs * 2, 60000);
	            continue;
	        }

	        return Optional.of(image);
	    }

	    Log.warn("[" + leg.getBioguideId() + "] Exceeded retry limit.");
	    return Optional.empty();
	}

	
//	@SneakyThrows
//	private Optional<byte[]> getImage(Legislator leg) throws IOException, InterruptedException
//	{
//		val url = scrapeImageUrlFromMemberPage(leg);
//		
//		val op = urlFetch(leg, url);
//		
//		if (op.isPresent() && !isJPEG(op.get())) {
//			if (lastWaitMs2 == -1) lastWaitMs2 = 2000;
//			else lastWaitMs2 = lastWaitMs2 * 2;
//			
//			if (lastWaitMs2 > 20000) {
//				Log.warn("Too many requests when fetching " + leg.getBioguideId());
//				lastWaitMs2 = -1;
//				return Optional.empty();
//			}
//			Log.warn("congress.gov sent invalid bytes for " + leg.getName().getOfficial_full() + " " + leg.getBioguideId() + ". Retrying in " + lastWaitMs2 + " miliseconds");
//			Thread.sleep(lastWaitMs2);
//			
//			return getImage(leg);
//		}
//		
//		return op;
//	}
//	
//	@SneakyThrows
//	private Optional<byte[]> urlFetch(Legislator leg, String url) {
//		String keyPassphrase = "changeit";
//
//		KeyStore keyStore = KeyStore.getInstance("PKCS12");
//		keyStore.load(S3ImageDatabaseBuilder.class.getResourceAsStream("keystore"), keyPassphrase.toCharArray());
//
//		SSLContext sslContext = SSLContexts.custom()
//		        .loadKeyMaterial(keyStore, null)
//		        .build();
//
//		CloseableHttpClient httpClient = HttpClients.custom().setSSLContext(sslContext).build();
//		
//		// Congress.gov is pretty picky about some of these headers. Without them you can get back an html file or just gibberish
//		val get = new HttpGet(url);
//		get.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:126.0) Gecko/20100101 Firefox/126.0");
//		get.addHeader("Accept", "image/avif,image/webp,*/*");
//		get.addHeader("Sec-Fetch-Dest", "image");
//		
//		HttpResponse resp = httpClient.execute(get);
//		@Cleanup InputStream is = resp.getEntity().getContent();
//		
//		val statusCode = resp.getStatusLine().getStatusCode();
//		
//		if (statusCode == 429 || statusCode == 403) // Too many requests. Backoff and try again
//		{
//			if (lastWaitMs == -1) lastWaitMs = 2000;
//			else lastWaitMs = lastWaitMs * 2;
//			
//			if (lastWaitMs > 300000) {
//				Log.warn("Too many requests when fetching " + leg.getBioguideId());
//				lastWaitMs = -1;
//				return Optional.empty();
//			}
//			
//			Log.warn("[" + leg.getBioguideId() + " " + leg.getName().getOfficial_full() + "] Received " + statusCode + ". Will wait " + lastWaitMs + " miliseconds and try again.");
//			
//			Thread.sleep(lastWaitMs);
//			
//			return urlFetch(leg, url);
//		}
//		
//		lastWaitMs = -1;
//		
//		if (statusCode >= 400) {
//			val body = IOUtils.toString(is, "UTF-8");
//			Log.warn("[" + leg.getBioguideId() + " " + leg.getName().getOfficial_full() + "] Received response code " + resp.getStatusLine().getStatusCode() + " and body " + body.substring(0, Math.min(body.length(), 300)));
//			
//			return Optional.empty();
//		}
//		else
//		{
//			return Optional.of(IOUtils.toByteArray(is));
//		}
//	}
	
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
