package us.poliscore.service;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.IpLocationMapping;
import us.poliscore.model.IpStackResponse;
import us.poliscore.service.storage.CachedDynamoDbService;

@ApplicationScoped
public class IpGeolocationService {
	
	@Inject
	private SecretService secrets;
	
	@Inject
	private CachedDynamoDbService ddb;
	
	@SneakyThrows
	public Optional<String> locateIp(String ip) {
		val mapping = ddb.get(ip, IpLocationMapping.class);
		if (mapping.isPresent()) { return Optional.ofNullable(mapping.get().getLocation()); }
		
		val url = new URI("http://api.ipstack.com/" + ip + "?access_key=" + secrets.getIpStackKey() + "&format=1").toURL();
		
		String json = IOUtils.toString(url.openStream(), "UTF-8");
		
		val resp = PoliscoreUtil.getObjectMapper().readValue(json, IpStackResponse.class);
		
		Optional<String> result;
		
		if ("United States".equals(resp.getCountry_name())) {
			result = Optional.of(resp.getRegion_code());
		} else {
			result = Optional.empty();
		}
		
		ddb.put(new IpLocationMapping(ip, result.orElse(null)));
		
		return result;
	}
	
}
