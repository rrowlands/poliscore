package us.poliscore.service;

import us.poliscore.model.Bill;
import us.poliscore.model.BillInterpretation;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(4)
public class BillProcessingService {

	@Inject
	protected BillService billService;
	
	@Inject
	protected BillInterpretationService interpretationService;
	
//    public BillInterpretation process(String url) {
//    	Bill bill = billService.fetchBill(url);
//    	
//    	var interp = interpretationService.interpret(bill);
//    	
//    	return interp;
//    }
}
