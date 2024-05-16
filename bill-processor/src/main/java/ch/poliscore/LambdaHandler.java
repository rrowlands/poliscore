package ch.poliscore;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import ch.poliscore.bill.BillProcessingService;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("poliscore")
public class LambdaHandler implements RequestHandler<InputObject, String> {

    @Inject
    BillProcessingService bill;

    @Override
    public String handleRequest(InputObject input, Context context) {
    	String result = bill.process("https://www.congress.gov/115/bills/hr806/BILLS-115hr806rfs.xml").toString();
    	System.out.println(result);
    	return result;
    }
}
