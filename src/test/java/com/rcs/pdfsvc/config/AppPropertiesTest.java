package com.rcs.pdfsvc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


public class AppPropertiesTest
{
	private static boolean DEFAULT_USE_SSL = false;	
	private static String DEFAULT_HOSTNAME = "TestHostnameValue";
	private static int DEFAULT_PORT = 65432;
	private static String DEFAULT_PATH = "/TestPathValue/";
	private static String DEFAULT_WORKDIR = File.separator + "TestWorkDirValue";
	private static String DEFAULT_LOGDIR = File.separator + "TestLogDirValue";

	private static String OVERRIDE_HOSTNAME = "OverrideHostname";
	
	private static Path propFile = null;

	@BeforeAll
	public static void setupClass() throws IOException {
		/*
		 * Create a temporary properties file to use for testing.
		 * This guarantees the contents over using a static file. 
		 */
		propFile = Files.createTempFile("TestProperties", ".properties");
		Properties props = new Properties();
		
		props.put(AppProperties.HOSTNAME, DEFAULT_HOSTNAME);
		props.put(AppProperties.APP_URL, DEFAULT_PATH);
		props.put(AppProperties.PORT, DEFAULT_PORT);
		props.put(AppProperties.WORKDIR, DEFAULT_WORKDIR);
		props.put(AppProperties.LOGDIR, DEFAULT_LOGDIR);

		props.put(AppProperties.USE_SSL, Boolean.valueOf(DEFAULT_USE_SSL).toString());
		props.put(AppProperties.PORT, Integer.toString(DEFAULT_PORT));
		
		try (OutputStream os = new FileOutputStream(propFile.toFile())) {
			props.store(os, null);
		}
		catch (IOException ioe) {
			fail("Problem creating the temporary properties file: " + ioe.getMessage());
		}
	}
	
	@AfterAll
	public static void cleanupClass() throws IOException {
		Files.delete(propFile);
	}
	
	@BeforeEach
	public void setup() {
		// By default use our temporary properties file
		System.setProperty(AppProperties.PROPERTIES_FILE_NAME, propFile.toAbsolutePath().toString());
	}

    private Map<String, String> sysPropBackupMap = new HashMap<String, String>();

    private void setSystemProperty(String property, String value) {
    	sysPropBackupMap.put(property, System.setProperty(property, value));
    }

    @AfterEach
    public void cleanup() {
    	for (Entry<String, String> entry : sysPropBackupMap.entrySet()) {
    		if (null == entry.getValue()) {
    			System.clearProperty(entry.getKey());
    		}
    		else {
    			System.setProperty(entry.getKey(), entry.getValue());
    		}
    	}
    	sysPropBackupMap.clear();
    }
    
    /**
     * Validate that an invalid file results in a FileNotFound exception.
     *  
     * @throws IOException
     */
	@Test
	public void badPropertyFileTest () throws IOException {
		assertThrows(FileNotFoundException.class, () -> {
			setSystemProperty(AppProperties.PROPERTIES_FILE_NAME, "bad-configX.properties");
		
			new AppProperties();
		});
	}

	/**
	 * Validate that all the properties we put in the property file were
	 * loaded correctly.
	 */
	@Test
	public void loadAllPropertiesTest () {
		try {
			AppProperties props = new AppProperties();
			
			assertEquals(DEFAULT_USE_SSL, props.isUseSsl(), "UseSsl not read from properties file correctly");
			assertEquals(DEFAULT_HOSTNAME, props.getHostname(), "Hostname not read from properties file correctly");
			assertEquals(DEFAULT_PORT, props.getPort(), "Port not read from properties file correctly");
			assertEquals(DEFAULT_PATH, props.getPath(), "Path not read from properties file correctly");
			assertEquals(DEFAULT_WORKDIR, props.getWorkDir().toString(), "WorkDir not read from properties file correctly");
			assertEquals(DEFAULT_LOGDIR, props.getLogDir().toString(), "LogDir not read from properties file correctly");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Allow the class to use it's default properties file.
	 * 
	 * Validate that the host name doesn't match any of our test host names.
	 */
	@Test
	public void useDefaultPropertiesFileTest () {
		try {
			AppProperties props = new AppProperties();
			
			assertEquals(DEFAULT_HOSTNAME, props.getHostname(), "Hostname read from properties file should have been as expected");
			assertNotEquals(OVERRIDE_HOSTNAME, props.getHostname(), "Hostname read from properties file should have been unexpected");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that defining a system/VM property overrides what is in the
	 * properties file.
	 */
	@Test
	public void overrideProperty_systemPropertyTest () {
		setSystemProperty(AppProperties.HOSTNAME, OVERRIDE_HOSTNAME);

		try {
			AppProperties props = new AppProperties();
			
			assertEquals(OVERRIDE_HOSTNAME, props.getHostname(), "Property in file was not overrided by system property");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that defining an environment property overrides what is in the
	 * properties file.
	 */
	@Test
	@Disabled
	public void overrideProperty_environmnetPropertyTest () {
		Mockito.when(System.getenv(AppProperties.HOSTNAME)).thenReturn(OVERRIDE_HOSTNAME);

		try {
			AppProperties props = new AppProperties();
			
			assertEquals("Property in file was not overrided by system property", OVERRIDE_HOSTNAME, props.getHostname());
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that when a system and an environment property are defined,
	 * the system property is used.
	 */
	@Test
	@Disabled
	public void overrideProperty_envAndSysPropertyTest () {
		setSystemProperty(AppProperties.HOSTNAME, "sys"+OVERRIDE_HOSTNAME);
		Mockito.when(System.getenv(AppProperties.HOSTNAME)).thenReturn("env"+OVERRIDE_HOSTNAME);

		try {
			AppProperties props = new AppProperties();
			
			assertEquals("Property in file was not overrided by system property", "sys"+OVERRIDE_HOSTNAME, props.getHostname());
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that SSL is disabled when the value 0 is used.
	 */
	@Test
	public void useSsl_zeroTest () {
		setSystemProperty(AppProperties.USE_SSL, "0");

		try {
			AppProperties props = new AppProperties();
			
			assertFalse(props.isUseSsl(), "SSL incorrect when set to 0, zero");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that SSL is disabled when the value "no" is used.
	 */
	@Test
	public void useSsl_noTest () {
		setSystemProperty(AppProperties.USE_SSL, "no");

		try {
			AppProperties props = new AppProperties();
			
			assertFalse(props.isUseSsl(), "SSL incorrect when set to \"no\"");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that SSL is disabled when the value "false" is used.
	 */
	@Test
	public void useSsl_falseTest () {
		setSystemProperty(AppProperties.USE_SSL, "FalSe");

		try {
			AppProperties props = new AppProperties();
			
			assertFalse(props.isUseSsl(), "SSL incorrect when set to \"false\"");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	
	/**
	 * Validate that SSL is enabled when the value 1 is used.
	 */
	@Test
	public void useSsl_oneTest () {
		setSystemProperty(AppProperties.USE_SSL, "1");

		try {
			AppProperties props = new AppProperties();
			
			assertTrue(props.isUseSsl(), "SSL incorrect when set to 1");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that SSL is disabled when the value "yes" is used.
	 */
	@Test
	public void useSsl_yesTest () {
		setSystemProperty(AppProperties.USE_SSL, "yes");

		try {
			AppProperties props = new AppProperties();
			
			assertTrue(props.isUseSsl(), "SSL incorrect when set to \"yes\"");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that SSL is disabled when the value "true" is used.
	 */
	@Test
	public void useSsl_trueTest () {
		setSystemProperty(AppProperties.USE_SSL, "tRuE");

		try {
			AppProperties props = new AppProperties();
			
			assertTrue(props.isUseSsl(), "SSL incorrect when set to \"true\"");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that an IllegalArgumentException is thrown when an invalid
	 * hostname is used.
	 * 
	 * @throws IOException
	 */
	@Test
	public void hostname_invalidNameTest () throws IOException {
		assertThrows(IllegalArgumentException.class, () -> {
			setSystemProperty(AppProperties.HOSTNAME, "some=invalid#hostname");

			new AppProperties();
		});
	}


	/**
	 * Validate that an IllegalArgumentException is thrown when an negative
	 * number is used for port.
	 * 
	 * @throws IOException
	 */
	@Test
	public void port_negativeNumberTest () throws IOException {
		assertThrows(IllegalArgumentException.class, () -> {
			setSystemProperty(AppProperties.PORT, "-432");

			new AppProperties();
		});
	}

	/**
	 * Validate that an IllegalArgumentException is thrown when too large a
	 * number is used for port.
	 * 
	 * @throws IOException
	 */
	@Test
	public void port_numberTooBigTest () throws IOException {
		assertThrows(IllegalArgumentException.class, () -> {
			setSystemProperty(AppProperties.PORT, "98765");

			new AppProperties();
		});
	}

	/**
	 * Validate that an IllegalArgumentException is thrown when an text
	 * string is used for port.
	 * 
	 * @throws IOException
	 */
	@Test
	public void port_textStringTest () throws IOException {
		assertThrows(IllegalArgumentException.class, () -> {
			setSystemProperty(AppProperties.PORT, "InvalidPort");

			new AppProperties();
		});
	}

	/**
	 * Validate that an IllegalArgumentException is thrown when the URL does
	 * not start with a slash.
	 * 
	 * @throws IOException
	 */
	@Test
	public void appUrl_noSlashTest () throws IOException {
		assertThrows(IllegalArgumentException.class, () -> {
			setSystemProperty(AppProperties.APP_URL, "InvalidPath");

			new AppProperties();
		});
	}

	/**
	 * Validate that the URL ends up with a trailing slash
	 */
	@Test
	public void appURl_trailingSlashTest () {
		setSystemProperty(AppProperties.APP_URL, "/NoSlashPath");

		try {
			AppProperties props = new AppProperties();
			
			assertTrue(props.getPath().endsWith("/"), "URI path missing trailing slash");
		}
		catch (Exception e) {
			fail("Unexpected exception : " + e.toString());
		}
	}

	/**
	 * Validate that an IllegalArgumentException is thrown when the work dir
	 * is empty.
	 * 
	 * @throws IOException
	 */
	@Test
	public void workDir_emptyTest () throws IOException {
		assertThrows(IllegalArgumentException.class, () -> {
			setSystemProperty(AppProperties.WORKDIR, "");

			new AppProperties();
		});
	}

	/**
	 * Validate that an IllegalArgumentException is thrown when the log dir
	 * is empty.
	 * 
	 * @throws IOException
	 */
	@Test
	public void logDir_emptyTest () throws IOException {
		assertThrows(IllegalArgumentException.class, () -> {
			setSystemProperty(AppProperties.LOGDIR, "");

			new AppProperties();
		});
	}
}
