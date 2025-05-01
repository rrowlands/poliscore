package us.poliscore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import io.quarkus.logging.Log;
import lombok.val;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.session.SessionInterpretation;
import us.poliscore.model.session.SessionInterpretation.PartyInterpretation;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;

public class PartyBillLinker {
	public static void linkPartyBillsSinglePass(PartyInterpretation interp, SessionInterpretation sessionInterp, MemoryObjectService memService, LocalCachedS3Service s3) {
	    try {
	        String exp = interp.getLongExplain();
	        if (exp == null || exp.isEmpty()) {
	            return;  // No text to process
	        }

	        // 1. Retrieve bills
	        List<Bill> bills = new ArrayList<Bill>(memService.query(Bill.class));
	        
	        // 2. (Optional) Attach BillInterpretation to each Bill
	        for (Bill bill : bills) {
	            val bi = s3.get(BillInterpretation.generateId(bill.getId(), null), BillInterpretation.class);
	            bi.ifPresent(bill::setInterpretation);
	        }

	        // 3. Sort bills by length of their official name (descending),
	        //    ensuring longer names match before shorter ones:
	        bills.sort(Comparator.comparingInt((Bill b) -> b.getName().length()).reversed());

	        // 4. Build a single combined pattern of all bill names/IDs.
	        //    We use word boundaries (\b) to avoid partial matches
	        //    (e.g. matching only "Over" in "OverSIGHT").
	        StringBuilder patternBuilder = new StringBuilder();
	        patternBuilder.append("(?iu)\\b("); // (?iu) => case-insensitive + Unicode

	        // We'll map each name/ID (lowercase) -> Bill so we know
	        // which Bill we matched inside the matcher loop.
	        Map<String, Bill> dictionary = new HashMap<>();
	        
	        boolean first = true;
	        for (Bill bill : bills) {
	            String billName = normalizeBillName(bill.getName());
	            String billId   = buildReadableBillId(bill);

	            // Put both forms into a dictionary
	            dictionary.put(billName.toLowerCase(), bill);
	            dictionary.put(billId.toLowerCase(), bill);

	            // Add them to the pattern. We quote them so special regex chars don’t break it.
	            if (!first) {
	                patternBuilder.append("|");
	            }
	            patternBuilder.append(Pattern.quote(billName));
	            patternBuilder.append("|");
	            patternBuilder.append(Pattern.quote(billId));
	            first = false;
	        }
	        patternBuilder.append(")\\b"); // close the big capturing group, then word boundary

	        // 5. Compile the “mega” pattern
	        Pattern combinedPattern = Pattern.compile(patternBuilder.toString(), 
	                                                  Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	        // 6. One-pass replacement: scan from left to right
	        Matcher matcher = combinedPattern.matcher(exp);
	        StringBuffer sb = new StringBuffer();
	        while (matcher.find()) {
	            String matchedText = matcher.group(1); // The actual substring that matched
	            Bill matchedBill   = dictionary.get(matchedText.toLowerCase());
	            
	            if (matchedBill != null) {
	                // Build the actual URL
	                String linkUrl = linkForBill(matchedBill.getId());
	                // We want to display the official Bill name (or your choice)
	                String linkText = normalizeBillName(matchedBill.getName());

	                // Build the HTML anchor
	                String replacement = "<a href=\"" + linkUrl + "\">" + linkText + "</a>";

	                // Safely insert the replacement text
	                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
	            } else {
	                // Fallback: if somehow we didn't find the Bill, just keep original text
	                matcher.appendReplacement(sb, Matcher.quoteReplacement(matchedText));
	            }
	        }
	        // Don’t forget the tail after the last match
	        matcher.appendTail(sb);

	        // 7. The final replaced text
	        interp.setLongExplain(sb.toString());
	    } catch (Throwable t) {
	        Log.error(t);
	    }
	}

	/**
	 * Example helper to remove trailing periods, trim, etc.
	 */
	public static String normalizeBillName(String name) {
	    if (name == null) return "";
	    name = name.trim();
	    while (name.endsWith(".")) {
	        name = name.substring(0, name.length() - 1).trim();
	    }
	    return name;
	}

	/**
	 * Example helper to build something like "H.R.-1234" or "S.-50"
	 * from your Bill ID (depending on how your code is structured).
	 */
	public static String buildReadableBillId(Bill bill) {
	    String id = bill.getId();
	    // For example:
	    val typeName  = Bill.billTypeFromId(id).getName();  // e.g. "H.R." or "S."
	    val billNum   = Bill.billNumberFromId(id);          // e.g. "1234"
	    return typeName + "-" + billNum;
	}

	public static String linkForBill(String id)
	{
		val billSession = Integer.valueOf(id.split("/")[3]);
		val deploymentYear = (billSession - 1) * 2 + 1789 + 1;
		
		return "/" + deploymentYear + "/bill/" + id.substring(StringUtils.ordinalIndexOf(id, "/", 4) + 1);
	}

}
