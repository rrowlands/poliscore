package ch.poliscore.service;

import java.io.File;

import ch.poliscore.Environment;
import ch.poliscore.bill.Bill;
import ch.poliscore.bill.BillInterpretation;
import io.quarkus.test.Mock;

@Mock
public class TestResourcesBillInterpretationService extends LocalStorageBillInterpretationService {
	@Override
	protected File getLocalStorage()
	{
		return new File(Environment.getDeployedPath(), "../../src/test/resources");
	}
}
