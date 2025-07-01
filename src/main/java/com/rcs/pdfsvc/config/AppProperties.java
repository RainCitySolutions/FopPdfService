package com.rcs.pdfsvc.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class AppProperties
{
	public static final String PROPERTIES_FILE_NAME = "PropertyFilename";
	public static final String USE_SSL = "FopPdfSvc-UseSSL";
	public static final String HOSTNAME = "FopPdfSvc-Hostname";
	public static final String PORT = "FopPdfSvc-Port";
	public static final String APP_URL = "FopPdfSvc-AppUrl";
	public static final String LOGDIR = "FopPdfSvc-LogDir";
	public static final String WORKDIR = "FopPdfSvc-WorkDir";
	
	private static final List<String> knownProperties = Arrays.asList(
			USE_SSL,
			HOSTNAME,
			PORT,
			APP_URL,
			LOGDIR,
			WORKDIR
			); 
	
	private Map<String,Object> properties = new HashMap<>();
	

	private InputStream getPropertyFileStream (String propFile) {
		InputStream is = null;

		is = AppProperties.class.getClassLoader().getResourceAsStream(propFile);
		if (null == is) {
			try {
				is = new FileInputStream(propFile);
			}
			catch (FileNotFoundException fnfe) {
				// Just return null
			}
		}

		return is;
	}

	public AppProperties() throws IOException
	{
		String propertiesFile = System.getProperty(PROPERTIES_FILE_NAME, "default-config.properties");

		try (InputStream inStrm = getPropertyFileStream(propertiesFile)) {

			if (null != inStrm) {
				final Properties fileProps = new Properties();

				//load a properties file from class path
				fileProps.load(inStrm);
				
				knownProperties.forEach(prop -> {
					// Check if there is a system property defined, highest priority
					String propStr = System.getProperty(prop);
					if (null != propStr) {
						fileProps.put(prop, propStr);
					}
					else {
						// Check if there is an environment property defined, second highest priority
						propStr = System.getenv(prop);
						if (null != propStr) {
							fileProps.put(prop, propStr);
						}
					}
					initializeProperty(prop, fileProps.getProperty(prop));
				});
			}
			else {
				throw new FileNotFoundException ("Unable to load properties file: " + propertiesFile);
			}
		} catch (IOException ex) {
			System.out.println("Exception reading properties file: " + ex.getMessage());	// NOSONAR
			throw ex;
		}
	}

	private void initializeProperty(String prop, String value)
	{
		switch (prop) {
			case USE_SSL:
				initializeSSL(prop, value);
				break;
				
			case HOSTNAME:
				initializeHost(prop, value);
				break;
				
			case PORT:
				initializePort(prop, value);
				break;
				
			case APP_URL:
				initializeAppUrl(prop, value);
				break;
				
			case LOGDIR, WORKDIR:
				initializeDir(prop, value);
				break;
				
			default:
				throw new IllegalArgumentException("Unrecognized property: " + prop + " : " + value);
		}
	}

	private void initializeSSL(final String prop, final String value) {
		switch (value.trim().toLowerCase()) {
			case "0", "no", "false":
				properties.put(prop, Boolean.FALSE);
				break;
	
			case "1", "yes", "true":
				properties.put(prop, Boolean.TRUE);
				break;
				
			default:
				throw new IllegalArgumentException("Invalid value for " + prop);
		}
	}

	private void initializeHost(final String prop, final String value) {
		String hostname = null;

		if (null != value && !value.contains("/")) {
		    try {
		        // WORKAROUND: add any scheme and port to make the resulting URI valid
		        hostname = new URI("my://userinfo@" + value + ":80").getHost();
		    } catch (URISyntaxException e) {
		    	// Just return null
		    }
		}
		
		if (null == hostname) {
			throw new IllegalArgumentException("Invalid host name : " + value);
		}
		else {
			properties.put(prop, hostname);
		}
	}

	private void initializePort(final String prop, final String value) {
		Integer port = null;

		try {
			if (null != value && !value.trim().isEmpty()) {
				int intPort = Integer.parseInt(value);

				if (intPort > 0 && intPort <=65535) {
					port = Integer.valueOf(intPort);
				}
			}
		}
		catch (NumberFormatException e) {
			// Just return null
		}
		
		if (null == port) {
			throw new IllegalArgumentException("Invalid port specified : " + value);
		}
		else {
			properties.put(prop, port);
		}
	}
	
	private void initializeAppUrl(final String prop, final String value) {
		String urlPath = null;

		if (null != value && value.startsWith("/")) {
		    try {
		        // WORKAROUND: add any scheme and port to make the resulting URI valid
		    	urlPath = new URI("my://userinfo@localhost:80" + value).getPath();
		    	if (!urlPath.endsWith("/")) {
		    		urlPath += "/";
		    	}
		    } catch (URISyntaxException e) {
		    	// Just return null
		    }
		}
		
		if (null == urlPath) {
			throw new IllegalArgumentException("Invalid URL path : " + value);
		}
		else {
			properties.put(prop, urlPath);
		}
	}

	private void initializeDir(final String prop, final String value) {
		if (null != value && !value.trim().isEmpty()) {
			try {
				Path path = FileSystems.getDefault().getPath(value);
				properties.put(prop, path);
			}
			catch (InvalidPathException ipe) {
				// Just return null
			}
		}

		if (null == properties.get(prop)) {
			throw new IllegalArgumentException("Invalid path specified for " + prop + " : " + value);
		}
	}

	/*
	 * We use a method instead of a member variable because logging can't be
	 * initialized until after the properties have been loaded.
	 */
	private Logger getLogger() {
		return LogManager.getLogger(AppProperties.class.getName());
	}

	private String getScheme () {
		StringBuilder scheme = new StringBuilder("http");
		
		if (Boolean.TRUE.equals(properties.get(USE_SSL))) {
			scheme.append("s");
		}
		
		return scheme.toString();
	}
	
	/************************************************************************
	 *	Package private methods 
	 ***********************************************************************/
	String getHostname() {
		return (String)properties.get(HOSTNAME);
	}
	
	int getPort () {
		return (Integer)properties.get(PORT);
	}
	
	String getPath() {
		return (String)properties.get(APP_URL);
	}

	boolean isUseSsl() {
		return (Boolean)properties.get(USE_SSL);
	}

	/************************************************************************
	 *	Public methods 
	 ***********************************************************************/
	public Path getWorkDir() {
		return (Path)properties.get(WORKDIR);
	}
	
	public Path getLogDir() {
		return (Path)properties.get(LOGDIR);
	}

	public URI getBaseUri() {
		URI uri = null;

		try {
			uri = new URI(getScheme(), null, getHostname(), getPort(), getPath(), null, null);
		} catch (URISyntaxException e) {
			getLogger().error("Issue creating server URI: ", e);
		}

		return uri;
	}
}
