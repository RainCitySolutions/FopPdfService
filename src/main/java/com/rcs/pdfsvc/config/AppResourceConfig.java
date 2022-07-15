package com.rcs.pdfsvc.config;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

import com.rcs.monitor.FopPdfAppEventListener;
import jakarta.ws.rs.ApplicationPath;

//@ApplicationPath("webapi")
@ApplicationPath("/")
public class AppResourceConfig
	extends ResourceConfig
{
    public static final String BASE_URI = "BaseUri";
    public static final String WORK_DIR = "WorkDir";

	
	public AppResourceConfig(AppProperties props)
	{
		setApplicationName("HSIR PDF Service");

		packages("com.rcs.pdfsvc.resource");

		register(FopPdfAppEventListener.class);
		register(new LoggingFeature(Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
				Level.INFO, LoggingFeature.Verbosity.PAYLOAD_ANY, 10000));
		register(MultiPartFeature.class);

		this.property(ServerProperties.WADL_FEATURE_DISABLE, true);

		this.property(BASE_URI, props.getBaseUri());
		this.property(WORK_DIR, props.getWorkDir());
	}
}
