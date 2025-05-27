package us.poliscore.view.legiscan;

public class LegiscanBillTextView {

    private String doc_id;
    private String bill_id;
    private String mime_type;
    private String text_url;
    private String text_content;

    // Getters
    public String getDoc_id() {
        return doc_id;
    }

    public String getBill_id() {
        return bill_id;
    }

    public String getMime_type() {
        return mime_type;
    }

    public String getText_url() {
        return text_url;
    }

    public String getText_content() {
        return text_content;
    }

    // Setters
    public void setDoc_id(String doc_id) {
        this.doc_id = doc_id;
    }

    public void setBill_id(String bill_id) {
        this.bill_id = bill_id;
    }

    public void setMime_type(String mime_type) {
        this.mime_type = mime_type;
    }

    public void setText_url(String text_url) {
        this.text_url = text_url;
    }

    public void setText_content(String text_content) {
        this.text_content = text_content;
    }
}
