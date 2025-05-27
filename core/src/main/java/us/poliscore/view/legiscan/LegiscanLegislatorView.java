package us.poliscore.view.legiscan;

public class LegiscanLegislatorView {

    private String legis_id;
    private String bioguide_id;
    private String first_name;
    private String last_name;
    private String party;
    private String district;
    private String state;
    private String photo_url;

    // Getters
    public String getLegis_id() {
        return legis_id;
    }

    public String getBioguide_id() {
        return bioguide_id;
    }

    public String getFirst_name() {
        return first_name;
    }

    public String getLast_name() {
        return last_name;
    }

    public String getParty() {
        return party;
    }

    public String getDistrict() {
        return district;
    }

    public String getState() {
        return state;
    }

    public String getPhoto_url() {
        return photo_url;
    }

    // Setters
    public void setLegis_id(String legis_id) {
        this.legis_id = legis_id;
    }

    public void setBioguide_id(String bioguide_id) {
        this.bioguide_id = bioguide_id;
    }

    public void setFirst_name(String first_name) {
        this.first_name = first_name;
    }

    public void setLast_name(String last_name) {
        this.last_name = last_name;
    }

    public void setParty(String party) {
        this.party = party;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setPhoto_url(String photo_url) {
        this.photo_url = photo_url;
    }
}
