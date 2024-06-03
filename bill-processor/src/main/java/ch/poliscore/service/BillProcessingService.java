package ch.poliscore.service;

import ch.poliscore.bill.Bill;
import ch.poliscore.bill.BillInterpretation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BillProcessingService {

	@Inject
	protected BillService billService;
	
	@Inject
	protected BillInterpretationService interpretationService;
	
    public BillInterpretation process(String url) {
    	Bill bill = billService.fetchBill(url);
    	
    	var interp = interpretationService.interpret(bill);
    	
    	return interp;
    }
}
