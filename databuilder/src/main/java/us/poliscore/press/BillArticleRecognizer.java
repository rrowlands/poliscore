package us.poliscore.press;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.Data;
import lombok.experimental.UtilityClass;
import us.poliscore.model.LegislativeChamber;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator.LegislatorName;

/**
 * Utility class for recognizing how likely an article is about a given bill,
 * considering both article text and source URL. Returns a normalized confidence score [0..1].
 */
@UtilityClass
public class BillArticleRecognizer {

    // Primary date formats to check
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("MMMM d, yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    // BillType tokens
    private static final Map<BillType, List<String>> TYPE_TOKENS = new EnumMap<>(BillType.class);
    static {
        TYPE_TOKENS.put(BillType.HR,    Arrays.asList("hr", "h.r.", "house bill"));
        TYPE_TOKENS.put(BillType.S,     Arrays.asList("s", "s.", "senate bill"));
        TYPE_TOKENS.put(BillType.SCONRES, Collections.singletonList("s.con.res."));
        TYPE_TOKENS.put(BillType.HRES,  Collections.singletonList("h.res."));
        TYPE_TOKENS.put(BillType.HCONRES,Collections.singletonList("h.con.res."));
        TYPE_TOKENS.put(BillType.SJRES, Collections.singletonList("s.j.res."));
        TYPE_TOKENS.put(BillType.SRES,  Collections.singletonList("s.res."));
        TYPE_TOKENS.put(BillType.HJRES, Collections.singletonList("h.j.res."));
    }

    // Chamber tokens
    private static final Map<LegislativeChamber, List<String>> CHAMBER_TOKENS = new EnumMap<>(LegislativeChamber.class);
    static {
        CHAMBER_TOKENS.put(LegislativeChamber.HOUSE, Arrays.asList(
            "house of representatives", "u.s. house", "us house", "house"));
        CHAMBER_TOKENS.put(LegislativeChamber.SENATE, Arrays.asList(
            "senate", "u.s. senate", "us senate"));
    }

    // Political context tokens
    private static final Map<String, Float> POLITICAL_TOKENS = new LinkedHashMap<>();
    static {
        POLITICAL_TOKENS.put("bill", 0.3f);
        POLITICAL_TOKENS.put("law", 0.2f);
        POLITICAL_TOKENS.put("legislation", 0.2f);
        POLITICAL_TOKENS.put("amendment", 0.1f);
        POLITICAL_TOKENS.put("committee", 0.1f);
        POLITICAL_TOKENS.put("vote", 0.1f);
        POLITICAL_TOKENS.put("congress", 0.1f);
        POLITICAL_TOKENS.put("senator", 0.1f);
        POLITICAL_TOKENS.put("representative", 0.1f);
    }

    // Namespace tokens with weights (extend for state namespaces)
    private static final Map<LegislativeNamespace, Map<String, Float>> NAMESPACE_TOKENS = new EnumMap<>(LegislativeNamespace.class);
    static {
        Map<String, Float> federal = new HashMap<>();
        federal.put("us congress", 1.0f);
        federal.put("u.s. congress", 1.0f);
        federal.put("congress", 0.8f);
        federal.put("federal", 0.5f);
        NAMESPACE_TOKENS.put(LegislativeNamespace.US_CONGRESS, federal);
    }

    // Known federal site indicators
    private static final List<String> FEDERAL_SITES = Arrays.asList(
        "congress.gov", "govinfo.gov", "federalregister.gov", "whitehouse.gov"
    );

    // Abstraction for state-level information
    @Data
    private static class StateInfo {
        private final String name;
        private final List<String> siteIndicators;
    }

    // Complete list of StateInfo entries for all 50 U.S. states
    private static final List<StateInfo> STATE_INFOS = Arrays.asList(
        new StateInfo("alabama", Arrays.asList("legislature.state.al.us", "al.com", "bhamnow.com", "montgomeryadvertiser.com")),
        new StateInfo("alaska", Arrays.asList("legis.state.ak.us", "juneauempire.com", "alaskapublic.org", "adn.com")),
        new StateInfo("arizona", Arrays.asList("azleg.gov", "azcentral.com", "tucson.com")),
        new StateInfo("arkansas", Arrays.asList("arkleg.state.ar.us", "arkansasonline.com", "thv11.com")),
        new StateInfo("california", Arrays.asList("leginfo.legislature.ca.gov", "latimes.com", "sacbee.com", "sfchronicle.com", "mercurynews.com", "sandiegouniontribune.com")),
        new StateInfo("colorado", Arrays.asList("leg.colorado.gov", "coloradopolitics.com", "denverpost.com", "sentinelcolorado.com", "westword.com", "coloradosun.com")),
        new StateInfo("connecticut", Arrays.asList("cga.ct.gov", "courant.com", "ctmirror.org", "nhregister.com")),
        new StateInfo("delaware", Arrays.asList("legis.delaware.gov", "delawareonline.com", "capegazette.com")),
        new StateInfo("florida", Arrays.asList("leg.state.fl.us", "tampabay.com", "orlandosentinel.com", "miamiherald.com", "sun-sentinel.com")),
        new StateInfo("georgia", Arrays.asList("legis.ga.gov", "ajc.com", "onlineathens.com", "savannahnow.com")),
        new StateInfo("hawaii", Arrays.asList("capitol.hawaii.gov", "staradvertiser.com", "hawaiitribune-herald.com")),
        new StateInfo("idaho", Arrays.asList("legislature.idaho.gov", "idahostatesman.com", "postregister.com")),
        new StateInfo("illinois", Arrays.asList("ilga.gov", "chicagotribune.com", "chicago.suntimes.com", "dailyherald.com")),
        new StateInfo("indiana", Arrays.asList("iga.in.gov", "indystar.com", "journalgazette.net")),
        new StateInfo("iowa", Arrays.asList("legis.iowa.gov", "desmoinesregister.com", "siouxcityjournal.com")),
        new StateInfo("kansas", Arrays.asList("kslegislature.org", "kansascity.com")),
        new StateInfo("kentucky", Arrays.asList("legislature.ky.gov", "kentucky.com", "bgdailynews.com")),
        new StateInfo("louisiana", Arrays.asList("legis.state.la.us", "theadvocate.com", "nola.com")),
        new StateInfo("maine", Arrays.asList("legislature.maine.gov", "pressherald.com", "bangordailynews.com")),
        new StateInfo("maryland", Arrays.asList("mgaleg.maryland.gov", "baltimoresun.com", "capitalgazette.com")),
        new StateInfo("massachusetts", Arrays.asList("malegislature.gov", "bostonglobe.com", "bostonherald.com")),
        new StateInfo("michigan", Arrays.asList("legislature.mi.gov", "mlive.com", "detroitnews.com", "freep.com")),
        new StateInfo("minnesota", Arrays.asList("leg.state.mn.us", "startribune.com", "pioneerpress.com")),
        new StateInfo("mississippi", Arrays.asList("legislature.ms.gov", "clarionledger.com", "sunherald.com")),
        new StateInfo("missouri", Arrays.asList("house.mo.gov", "stltoday.com", "kansascity.com")),
        new StateInfo("montana", Arrays.asList("leg.mt.gov", "missoulian.com", "billingsgazette.com")),
        new StateInfo("nebraska", Arrays.asList("nebraskalegislature.gov", "omaha.com", "journalstar.com")),
        new StateInfo("nevada", Arrays.asList("leg.state.nv.us", "reviewjournal.com", "rgj.com")),
        new StateInfo("new hampshire", Arrays.asList("gencourt.state.nh.us", "unionleader.com", "fosters.com")),
        new StateInfo("new jersey", Arrays.asList("njleg.state.nj.us", "nj.com", "app.com")),
        new StateInfo("new mexico", Arrays.asList("nmlegis.gov", "abqjournal.com", "santafenewmexican.com")),
        new StateInfo("new york", Arrays.asList("nyassembly.gov", "nytimes.com", "buffalonews.com", "newsday.com")),
        new StateInfo("north carolina", Arrays.asList("ncleg.gov", "newsobserver.com", "charlotteobserver.com")),
        new StateInfo("north dakota", Arrays.asList("legis.nd.gov", "inforum.com", "bismarcktribune.com")),
        new StateInfo("ohio", Arrays.asList("legislature.ohio.gov", "dispatch.com", "cleveland.com")),
        new StateInfo("oklahoma", Arrays.asList("oklegislature.gov", "oklahoman.com", "tulsaworld.com")),
        new StateInfo("oregon", Arrays.asList("oregonlegislature.gov", "oregonlive.com", "portlandtribune.com")),
        new StateInfo("pennsylvania", Arrays.asList("legis.state.pa.us", "philly.com", "inquirer.com")),
        new StateInfo("rhode island", Arrays.asList("rilegislature.gov", "providencejournal.com")),
        new StateInfo("south carolina", Arrays.asList("scstatehouse.gov", "thestate.com", "postandcourier.com")),
        new StateInfo("south dakota", Arrays.asList("sdlegislature.gov", "argusleader.com", "rapidcityjournal.com")),
        new StateInfo("tennessee", Arrays.asList("capitol.tn.gov", "tennessean.com", "knoxnews.com")),
        new StateInfo("texas", Arrays.asList("capitol.texas.gov", "houstonchronicle.com", "dallasnews.com")),
        new StateInfo("utah", Arrays.asList("le.utah.gov", "sltrib.com", "deseretnews.com")),
        new StateInfo("vermont", Arrays.asList("legislature.vermont.gov", "rutlandherald.com", "burlingtonfreepress.com")),
        new StateInfo("virginia", Arrays.asList("virginiageneralassembly.gov", "richmond.com", "dailyprogress.com")),
        new StateInfo("washington", Arrays.asList("leg.wa.gov", "seattletimes.com", "seattlepi.com")),
        new StateInfo("west virginia", Arrays.asList("wvlegislature.gov", "wvgazettemail.com", "charlestondailymail.com")),
        new StateInfo("wisconsin", Arrays.asList("legis.wisconsin.gov", "jsonline.com", "journaltimes.com")),
        new StateInfo("wyoming", Arrays.asList("wyoleg.gov", "cowboystatedaily.com", "trib.com"))
    );

    /**
     * Computes a confidence score [0..1] that the given article text and URL refer to the provided bill.
     */
    public float recognize(Bill bill, String article, String url) {
        String text = article.toLowerCase(Locale.ROOT);

        float idScore        = scoreTypeNumber(bill, text);
        float chamberScore   = scoreChamber(bill, text);
        float sessionScore   = scoreSession(bill, text);
        float polScore       = scorePolitical(text);
        float sponsorScore   = scoreSponsor(bill, text);
        float cosponsorScore = scoreCosponsors(bill, text);
        float nameScore      = scoreName(bill, text);
        float nsScore        = scoreNamespace(bill, text);
        float dateScore      = scoreDate(bill, text);
        float urlScore       = scoreUrl(bill, url);

        // Tunable weights
        final float W_ID        = 0.30f;
        final float W_CHAMBER   = 0.05f;
        final float W_SESSION   = 0.05f;
        final float W_POLITICAL = 0.10f;
        final float W_SPONSOR   = 0.15f;
        final float W_COSPONSOR = 0.10f;
        final float W_NAME      = 0.10f;
        final float W_NAMESPACE = 0.10f;
        final float W_DATE      = 0.15f;
        final float W_URL       = 0.05f;

        float score = idScore * W_ID
                    + chamberScore * W_CHAMBER
                    + sessionScore * W_SESSION
                    + polScore * W_POLITICAL
                    + sponsorScore * W_SPONSOR
                    + cosponsorScore * W_COSPONSOR
                    + nameScore * W_NAME
                    + nsScore * W_NAMESPACE
                    + dateScore * W_DATE
                    + urlScore * W_URL;

        return Math.min(1f, Math.max(0f, score));
    }

    private float scoreTypeNumber(Bill bill, String text) {
        int number = bill.getNumber();
        BillType type = bill.getType();
        return TYPE_TOKENS.getOrDefault(type, Collections.emptyList())
                .stream()
                .anyMatch(tok -> Pattern.compile("\\b" + Pattern.quote(tok) + "\\W*" + number + "\\b",
                        Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .find())
            ? 1f
            : 0f;
    }

    private float scoreChamber(Bill bill, String text) {
        LegislativeChamber cham = BillType.getOriginatingChamber(bill.getType());
        return CHAMBER_TOKENS.getOrDefault(cham, Collections.emptyList())
                .stream()
                .anyMatch(text::contains)
            ? 1f
            : 0f;
    }

    private float scoreSession(Bill bill, String text) {
        String pat = bill.getSession() + "(st|nd|rd|th) congress";
        return Pattern.compile(pat, Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()
            ? 1f
            : 0f;
    }

    private float scorePolitical(String text) {
        float sumW = 0f, matchW = 0f;
        for (var e : POLITICAL_TOKENS.entrySet()) {
            sumW += Math.abs(e.getValue());
            if (text.contains(e.getKey())) {
                matchW += e.getValue();
            }
        }
        return sumW == 0f ? 0f : Math.min(1f, Math.max(0f, matchW / sumW));
    }

    private float scoreSponsor(Bill bill, String text) {
        LegislatorName n = bill.getSponsor().getName();
        float full  = contains(text, n.getOfficial_full()) ? 1f : 0f;
        float combo = contains(text, n.getFirst() + " " + n.getLast()) ? 0.5f : 0f;
        float first = contains(text, n.getFirst()) ? 0.2f : 0f;
        float last  = contains(text, n.getLast()) ? 0.2f : 0f;
        return Math.min(1f, full + combo + Math.max(first, last));
    }

    private float scoreCosponsors(Bill bill, String text) {
        var cos = bill.getCosponsors();
        if (cos == null || cos.isEmpty()) return 0f;
        long total = cos.size();
        long matched = cos.stream().filter(sp -> {
            LegislatorName n = sp.getName();
            boolean okFull  = contains(text, n.getOfficial_full());
            boolean okParts = contains(text, n.getFirst()) && contains(text, n.getLast());
            return okFull || okParts;
        }).count();
        return (float) matched / total;
    }

    private float scoreName(Bill bill, String text) {
        String name = bill.getName();
        return (name != null && contains(text, name)) ? 1f : 0f;
    }

    private float scoreNamespace(Bill bill, String text) {
        var tokens = NAMESPACE_TOKENS.get(bill.getNamespace());
        if (tokens == null) return 0f;
        float sumW = 0f, matchW = 0f;
        for (var e : tokens.entrySet()) {
            sumW += Math.abs(e.getValue());
            if (text.contains(e.getKey())) {
                matchW += e.getValue();
            }
        }
        return sumW == 0f
            ? 0f
            : Math.min(1f, Math.max(0f, matchW / sumW));
    }

    private float scoreDate(Bill bill, String text) {
        LocalDate dt = bill.getIntroducedDate();
        if (dt == null) return 0f;
        for (var fmt : DATE_FORMATTERS) {
            if (text.contains(dt.format(fmt).toLowerCase(Locale.ROOT))) {
                return 1f;
            }
        }
        return 0f;
    }

    /**
     * Scores URL-based signals: federal vs. state-level.
     */
    private float scoreUrl(Bill bill, String url) {
        String u = url.toLowerCase(Locale.ROOT);
        // Federal site override
        for (String site : FEDERAL_SITES) {
            if (u.contains(site)) {
                return bill.getNamespace() == LegislativeNamespace.US_CONGRESS ? 1f : 0f;
            }
        }
        // State site indicators
        for (StateInfo state : STATE_INFOS) {
            for (String indicator : state.getSiteIndicators()) {
                if (u.contains(indicator)) {
                    return bill.getNamespace() == LegislativeNamespace.US_CONGRESS ? 0f : 1f;
                }
            }
        }
        // neutral
        return 0.5f;
    }

    private boolean contains(String text, String term) {
        return term != null && !term.isEmpty() && text.contains(term.toLowerCase(Locale.ROOT));
    }
}

