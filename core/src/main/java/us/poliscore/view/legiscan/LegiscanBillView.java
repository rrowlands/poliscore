package us.poliscore.view.legiscan;

import java.util.List;

public class LegiscanBillView {

    private String bill_id;
    private String number;
    private String title;
    private String description;
    private String status;
    private String last_action_date;
    private String last_action;
    private List<String> sponsors;
    private List<String> cosponsors;
    private String bill_text_id;

    // Getters
    public String getBill_id() {
        return bill_id;
    }

    public String getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getLast_action_date() {
        return last_action_date;
    }

    public String getLast_action() {
        return last_action;
    }

    public List<String> getSponsors() {
        return sponsors;
    }

    public List<String> getCosponsors() {
        return cosponsors;
    }

    public String getBill_text_id() {
        return bill_text_id;
    }

    // Setters
    public void setBill_id(String bill_id) {
        this.bill_id = bill_id;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setLast_action_date(String last_action_date) {
        this.last_action_date = last_action_date;
    }

    public void setLast_action(String last_action) {
        this.last_action = last_action;
    }

    public void setSponsors(List<String> sponsors) {
        this.sponsors = sponsors;
    }

    public void setCosponsors(List<String> cosponsors) {
        this.cosponsors = cosponsors;
    }

    public void setBill_text_id(String bill_text_id) {
        this.bill_text_id = bill_text_id;
    }
}
