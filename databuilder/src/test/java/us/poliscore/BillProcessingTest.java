package us.poliscore;

import jakarta.inject.Inject;
import us.poliscore.service.BillProcessingService;

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
