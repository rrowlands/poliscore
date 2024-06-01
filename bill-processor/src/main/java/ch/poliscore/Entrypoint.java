package ch.poliscore;

import java.util.Map;

import ch.poliscore.service.BillProcessingService;
import io.quarkus.funqy.Funq;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class Entrypoint {

    @Inject
    BillProcessingService bill;

    @Funq
    public IssueStats processBill(Map<String, String> map) {
//    	String url = "https://www.congress.gov/118/bills/hr3935/BILLS-118hr3935eas.xml"; // huge bill - air transport
//    	String url = "https://www.congress.gov/115/bills/hr806/BILLS-115hr806rfs.xml"; // small bill - trump epa rollback
    	
    	IssueStats result = bill.process(map.get("url"));
    	return result;
    }
}
