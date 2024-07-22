package us.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class IpGeolocationResponse {
	private String ip;
    private String continent_code;
    private String continent_name;
    private String country_code2;
    private String country_code3;
    private String country_name;
    private String country_name_official;
    private String country_capital;
    private String state_prov;
    private String state_code;
    private String district;
    private String city;
    private String zipcode;
    private String latitude;
    private String longitude;
    private boolean is_eu;
    private String calling_code;
    private String country_tld;
    private String languages;
    private String country_flag;
    private String geoname_id;
    private String isp;
    private String connection_type;
    private String organization;
    private String country_emoji;
    private Currency currency;
    private TimeZone time_zone;
	
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
	public static class Currency{
	    private String code;
	    private String name;
	    private String symbol;
	}

	@Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
	public static class DstEnd{
	    private String utc_time;
	    private String duration;
	    private boolean gap;
	    private String dateTimeAfter;
	    private String dateTimeBefore;
	    private boolean overlap;
	}

	@Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
	public static class DstStart{
	    private String utc_time;
	    private String duration;
	    private boolean gap;
	    private String dateTimeAfter;
	    private String dateTimeBefore;
	    private boolean overlap;
	}

	@Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
	public static class TimeZone{
	    private String name;
	    private int offset;
	    private int offset_with_dst;
	    private String current_time;
	    private double current_time_unix;
	    private boolean is_dst;
	    private int dst_savings;
	    private boolean dst_exists;
	    private DstStart dst_start;
	    private DstEnd dst_end;
	}
}
