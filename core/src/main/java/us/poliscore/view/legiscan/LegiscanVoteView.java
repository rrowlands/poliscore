package us.poliscore.view.legiscan;

import java.util.Map;

public class LegiscanVoteView {

    private String roll_call_id;
    private String bill_id;
    private String date;
    private String description;
    private Map<String, String> votes;

    // Getters
    public String getRoll_call_id() {
        return roll_call_id;
    }

    public String getBill_id() {
        return bill_id;
    }

    public String getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getVotes() {
        return votes;
    }

    // Setters
    public void setRoll_call_id(String roll_call_id) {
        this.roll_call_id = roll_call_id;
    }

    public void setBill_id(String bill_id) {
        this.bill_id = bill_id;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setVotes(Map<String, String> votes) {
        this.votes = votes;
    }
}
