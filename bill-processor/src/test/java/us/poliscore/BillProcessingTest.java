package us.poliscore;

import org.junit.jupiter.api.Test;

import us.poliscore.model.BillInterpretation;
import us.poliscore.service.BillProcessingService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.constraint.Assert;
import jakarta.inject.Inject;

//@QuarkusTest
public class BillProcessingTest
{
	@Inject
	private BillProcessingService processingService;
	
//	@Test
    public void testLargeBill() throws Exception {
//		BillInterpretation bi = processingService.process(TestUtils.C115HR806);
//		
//		Assert.assertNotNull(bi);
    }
}
