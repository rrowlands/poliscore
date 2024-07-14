package us.poliscore.service;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.IpStackResponse;

@ApplicationScoped
public class IpGeolocationService {
	
	@Inject
	private SecretService secrets;
	
	@SneakyThrows
	public Optional<String> locateIp(String ip) {
		val url = new URI("http://api.ipstack.com/" + ip + "?access_key=" + secrets.getIpStackKey() + "&format=1").toURL();
		
		String json = IOUtils.toString(url.openStream(), "UTF-8");
		
		val resp = PoliscoreUtil.getObjectMapper().readValue(json, IpStackResponse.class);
		
		if ("United States".equals(resp.getCountry_name())) {
			return Optional.of(resp.getRegion_code());
		} else {
			return Optional.empty();
		}
	}
	
}
