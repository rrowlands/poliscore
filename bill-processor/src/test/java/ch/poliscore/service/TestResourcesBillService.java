package ch.poliscore.service;

import java.io.File;

import ch.poliscore.Environment;
import io.quarkus.test.Mock;

@Mock
public class TestResourcesBillService extends LocalStorageBillService {
	@Override
	protected File getLocalStorage()
	{
		return new File(Environment.getDeployedPath(), "../../src/test/resources");
	}
}
