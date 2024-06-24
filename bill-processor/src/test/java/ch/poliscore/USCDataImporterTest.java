package ch.poliscore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import ch.poliscore.service.BillService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

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
