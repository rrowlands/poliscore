package us.poliscore.model;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IpStackResponse {
	
	public String ip;
    public String type;
    public String continent_code;
    public String continent_name;
    public String country_code;
    public String country_name;
    public String region_code;
    public String region_name;
    public String city;
    public String zip;
    public double latitude;
    public double longitude;
    public Location location;
	
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
	public static class Language{
	    public String code;
	    public String name;
	    @JsonProperty("native") 
	    public String natve;
	}

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
	public static class Location{
	    public int geoname_id;
	    public String capital;
	    public ArrayList<Language> languages;
	    public String country_flag;
	    public String country_flag_emoji;
	    public String country_flag_emoji_unicode;
	    public String calling_code;
	    public boolean is_eu;
	}

}
