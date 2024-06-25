package us.poliscore.entrypoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.poliscore.PoliscoreUtil;
import us.poliscore.interpretation.BillType;
import us.poliscore.model.Legislator;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.PersistenceServiceIF;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;

@QuarkusMain(name="BillSizeCalculator")
public class BillSizeCalculator implements QuarkusApplication
{
	public static void main(String[] args) {
		Quarkus.run(BillSizeCalculator.class, args);
	}
	
	protected void process() throws IOException
	{
		long size = 0;
		
		for (File file : PoliscoreUtil.allFilesWhere(new File(PoliscoreUtil.APP_DATA, "bill-text/118"),
				f -> !f.getName().equals("done.txt") && !BillType.getIgnoredBillTypes().stream().map(bt -> f.getName().toLowerCase().contains(bt.getName().toLowerCase())).reduce(false, (a,b) -> a || b)))
		{
			size += FileUtils.readFileToString(file, "UTF-8").length();
		}
		
		Log.info("Size is " + size);
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
