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
import java.util.Hashtable;
import java.util.List;
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
	
	private Hashtable<String,Object> properties = new Hashtable<String, Object>();
	

	private InputStream getPropertyFileStream (String propFile) {
		InputStream is = null;

		is = AppProperties.class.getClassLoader().getResourceAsStream(propFile);
		if (null == is) {
			try {
				is = new FileInputStream(propFile);
			}
			catch (FileNotFoundException fnfe) {}
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
			System.out.println("Exception reading properties file: " + ex.getMessage());
			throw ex;
		}
	}

	private void initializeProperty(String prop, String value)
	{
		switch (prop) {
			case USE_SSL:
				switch (value.trim().toLowerCase()) {
					case "0":
					case "no":
					case "false":
						properties.put(prop, Boolean.FALSE);
						break;

					case "1":
					case "yes":
					case "true":
						properties.put(prop, Boolean.TRUE);
						break;
						
					default:
						throw new IllegalArgumentException("Invalid value for " + prop);
				}
				break;
				
			case HOSTNAME:
				String hostname = null;

				if (null != value && !value.contains("/")) {
				    try {
				        // WORKAROUND: add any scheme and port to make the resulting URI valid
				        hostname = new URI("my://userinfo@" + value + ":80").getHost();
				    } catch (URISyntaxException e) { }
				}
				
				if (null == hostname) {
					throw new IllegalArgumentException("Invalid host name : " + value);
				}
				else {
					properties.put(prop, hostname);
				}
				break;
				
			case PORT:
				Integer port = null;

				try {
					if (null != value && value.trim().length() > 0) {
						int intPort = Integer.valueOf(value);
						if (intPort > 0 && intPort <=65535) {
							port = Integer.valueOf(intPort);
						}
					}
				}
				catch (NumberFormatException e) {}
				
				if (null == port) {
					throw new IllegalArgumentException("Invalid port specified : " + value);
				}
				else {
					properties.put(prop, port);
				}
				break;
				
			case APP_URL:
				String urlPath = null;

				if (null != value && value.startsWith("/")) {
				    try {
				        // WORKAROUND: add any scheme and port to make the resulting URI valid
				    	urlPath = new URI("my://userinfo@localhost:80" + value).getPath();
				    	if (!urlPath.endsWith("/")) {
				    		urlPath += "/";
				    	}
				    } catch (URISyntaxException e) { }
				}
				
				if (null == urlPath) {
					throw new IllegalArgumentException("Invalid URL path : " + value);
				}
				else {
					properties.put(prop, urlPath);
				}
				break;
				
			case LOGDIR:
			case WORKDIR:
				if (null != value && value.trim().length() > 0) {
					try {
						Path path = FileSystems.getDefault().getPath(value);
						properties.put(prop, path);
					}
					catch (InvalidPathException ipe) {}
				}
				if (null == properties.get(prop)) {
					throw new IllegalArgumentException("Invalid path specified for " + prop + " : " + value);
				}
				break;
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
		
		if ((Boolean)properties.get(USE_SSL)) {
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
