package ch.poliscore.entrypoint;

import java.util.Map;

import ch.poliscore.model.BillInterpretation;
import ch.poliscore.service.BillProcessingService;
import io.quarkus.funqy.Funq;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class Lambda {

    @Inject
    BillProcessingService bill;

    @Funq
    public BillInterpretation processBill(Map<String, String> map) {
//    	String url = "https://www.congress.gov/118/bills/hr3935/BILLS-118hr3935eas.xml"; // huge bill - air transport
//    	String url = "https://www.congress.gov/115/bills/hr806/BILLS-115hr806rfs.xml"; // small bill - trump epa rollback
    	
//    	BillInterpretation result = bill.process(map.get("url"));
//    	return result;
    	
    	return null; // TODO
    }
}
