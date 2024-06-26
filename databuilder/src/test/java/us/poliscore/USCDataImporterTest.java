package us.poliscore;

import java.io.File;

import jakarta.inject.Inject;
import us.poliscore.service.BillService;

//@QuarkusTest
public class USCDataImporterTest {
	@Inject
	private BillService billService;
	
//	@Test
    public void testRun() throws Exception {
		
		final String path = "/Users/rrowlands/dev/projects/congress/data";
		
		final File dumpParent = new File(path);
		if (!dumpParent.exists()) throw new RuntimeException("Expected parent file argument to exist");
		
//        process(dumpParent);
    }
}
