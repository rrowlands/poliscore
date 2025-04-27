package us.poliscore.press;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.Bill.BillSponsor;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator.LegislatorName;

public class BillArticleRecognizerTest {

    // Helper to construct a sample Bill
    private Bill makeSampleBill() {
        Bill bill = new Bill();
        bill.setNamespace(LegislativeNamespace.US_CONGRESS);
        bill.setSession(118);
        bill.setType(BillType.HR);
        bill.setNumber(123);
        bill.setName("Infrastructure Improvement Act");

        // Sponsor
        LegislatorName sponsorName = new LegislatorName();
        sponsorName.setFirst("Jane");
        sponsorName.setLast("Doe");
        sponsorName.setOfficial_full("Jane A. Doe");
        BillSponsor sponsor = new BillSponsor();
        sponsor.setName(sponsorName);
        bill.setSponsor(sponsor);

        // Cosponsors
        LegislatorName cos1Name = new LegislatorName();
        cos1Name.setFirst("John");
        cos1Name.setLast("Smith");
        cos1Name.setOfficial_full("John B. Smith");
        BillSponsor cos1 = new BillSponsor();
        cos1.setName(cos1Name);

        LegislatorName cos2Name = new LegislatorName();
        cos2Name.setFirst("Alice");
        cos2Name.setLast("Jones");
        cos2Name.setOfficial_full("Alice C. Jones");
        BillSponsor cos2 = new BillSponsor();
        cos2.setName(cos2Name);

        bill.setCosponsors(Arrays.asList(cos1, cos2));
        bill.setIntroducedDate(LocalDate.of(2024, 1, 5));
        return bill;
    }

    @Test
    @DisplayName("Full match with federal URL yields confidence of 1.0")
    public void testFullMatchFederalUrl() {
        Bill bill = makeSampleBill();
        String article = String.join(" ",
            "The U.S. House of Representatives passed H.R. 123,",
            "the Infrastructure Improvement Act, sponsored by Jane A. Doe,",
            "with cosponsors John B. Smith and Alice C. Jones.",
            "During the 118th Congress, this landmark bill introduced on January 5, 2024,",
            "received bipartisan support in federal committee votes.");
        String url = "https://www.congress.gov/bill/118th-congress/house-bill/123";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertEquals(1.0f, score, 1e-6f, "Expect perfect match to score 1.0");
    }

    @Test
    @DisplayName("Full match but state URL penalizes federal bill")
    public void testFullMatchStateUrlPenalized() {
        Bill bill = makeSampleBill();
        String article = String.join(" ",
            "The U.S. House of Representatives passed H.R. 123,",
            "the Infrastructure Improvement Act, sponsored by Jane A. Doe,",
            "with cosponsors John B. Smith and Alice C. Jones.",
            "During the 118th Congress, this landmark bill introduced on January 5, 2024,",
            "received bipartisan support in federal committee votes.");
        String url = "https://www.denverpost.com/2024/03/01/hr-123-infrastructure-act/";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertEquals(0.99324024f, score, 1e-6f, "State URL should reduce score by URL weight");
    }

    @Test
    @DisplayName("Non-political article yields zero confidence regardless of URL")
    public void testIrrelevantArticle() {
        Bill bill = makeSampleBill();
        String article = "Cute puppies playing in the park with colorful toys.";
        String url = "https://www.denverpost.com/2024/03/01/kittens-play/";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertEquals(0.0f, score, 1e-6f, "Irrelevant article should score 0");
    }

    @Test
    @DisplayName("Article about chickens yields zero confidence")
    public void testChickenArticle() {
        Bill bill = makeSampleBill();
        String article = "Chickens cluck and roam the barnyard all day while farmers collect fresh eggs.";
        String url = "https://www.chickenweekly.com/news/chicken-pecking-issues";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertTrue(score < 0.1f, "Article about chickens scored too high.");
    }

    @Test
    @DisplayName("Type and number only yields ID weight")
    public void testTypeNumberOnly() {
        Bill bill = makeSampleBill();
        String article = "Details on H.R. 123 were released today.";
        String url = "https://www.congress.gov/bill/118th-congress/house-bill/123";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertEquals(0.35f, score, 1e-6f, "Only ID and federal URL should sum to 0.35");
    }

    @Test
    @DisplayName("Sponsor-only mention with political term")
    public void testSponsorOnlyWithPolitical() {
        Bill bill = makeSampleBill();
        String article = "Representative Jane Doe discussed upcoming legislation in committee.";
        String url = "https://www.congress.gov/";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertEquals(0.18576923f, score, 1e-6f, "Sponsor + political + URL weights should match implementation");
    }

    @Test
    @DisplayName("Date-only match yields date and URL weight")
    public void testDateOnlyFormats() {
        Bill bill = makeSampleBill();
        String[] variants = {
            "Introduced on Jan 5, 2024 in a brief hearing.",
            "Introduced on 1/5/2024 by the panel.",
            "Introduced on 2024-01-05 during debate."
        };
        for (String art : variants) {
            String url = "https://www.congress.gov/";
            float score = BillArticleRecognizer.recognize(bill, art, url);
            assertEquals(0.20f, score, 1e-6f, "Date-only + URL should score 0.20");
        }
    }

    @Test
    @DisplayName("Cosponsor partial match yields fractional cosponsor weight + URL")
    public void testCosponsorPartialMatch() {
        Bill bill = makeSampleBill();
        String article = "John Smith is rallying support for House Bill 123.";
        String url = "https://www.congress.gov/";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertEquals(0.47307697f, score, 1e-6f, "Partial cosponsor + ID + political + chamber + URL weights should match implementation");
    }

    @Test
    @DisplayName("Federal site in URL always boosts to 1 for URL component")
    public void testUrlFederalOverrides() {
        Bill bill = makeSampleBill();
        String article = "Some random text.";
        String url = "https://federalregister.gov/documents/2024/05/01/notice";

        float score = BillArticleRecognizer.recognize(bill, article, url);
        assertEquals(0.05f, score, 1e-6f, "Only federal URL weight should count");
    }
}

