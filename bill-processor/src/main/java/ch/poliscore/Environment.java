package ch.poliscore;

import java.io.File;
import java.net.URL;

public class Environment {
	private static File deployPath = null;
	
	/**
	 * Calculates and returns the deployed path of the currently running application. If we are deployed inside a container, this will return $CATALINA_HOME/webapps/$CONTEXT_PATH
	 *	 as a resolved absolute path. If we are running inside a jar, this will return the directory that contains the running jar.
	 * 
	 * @return An absolute file path of the deployed application path.
	 */
	public static File getDeployedPath()
	{
		if (deployPath != null)
		{
			return deployPath;
		}
		
		String sDeployPath;
		
		URL rootPath = Environment.class.getResource("/");
		if (rootPath != null && !rootPath.getPath().equals(""))
		{
			sDeployPath = rootPath.getPath();
		}
		else
		{
			// If our code lives inside a jar, getResource will return null
			String path = (new Environment()).getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
			
			if (path.endsWith(".jar") || path.endsWith(".war") || path.endsWith(".class"))
			{
				path = new File(path).getParent();
			}
			
			sDeployPath = path.replace((new Environment()).getClass().getPackage().getName().replace(".", "/"), "");
		}
		
		if (sDeployPath.endsWith("/"))
		{
			sDeployPath = sDeployPath.substring(0, sDeployPath.length() - 1);
		}
		
		if (sDeployPath.endsWith("WEB-INF/classes"))
		{
			sDeployPath = sDeployPath.replace("WEB-INF/classes", "");
		}

		// getPath returns spaces as %20 for some reason
		sDeployPath = sDeployPath.replace("%20", " ");
		
		deployPath = new File(sDeployPath);
		return deployPath;
	}
}
