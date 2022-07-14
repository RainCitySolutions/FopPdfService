package com.rcs.monitor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

public class FopPdfAppEventListener
	implements ApplicationEventListener
{
	private Logger logger;
	private volatile int requestCnt = 0;
	
	public FopPdfAppEventListener() {
		logger = LogManager.getLogger(FopPdfAppEventListener.class);
	}

	@Override
	public void onEvent(ApplicationEvent event) {
		String appName = event.getResourceConfig().getApplicationName();

		switch (event.getType()) {
			case INITIALIZATION_START:
				logger.info("{} initialization starting.", appName);
				break;

			case INITIALIZATION_FINISHED:
				logger.info("{} initialization finished.", appName);
				break;

			case DESTROY_FINISHED:
				logger.info("{} destroyed.", appName);
	        break;

			case INITIALIZATION_APP_FINISHED:
				logger.info("{} initialization app finished.", appName);
				break;

			case RELOAD_FINISHED:
				logger.info("{} reloaded.", appName);
				break;

			default:
				break;
		}
	}
	
	@Override
	public RequestEventListener onRequest(RequestEvent requestEvent) {
		RequestEventListener listener = null;

		if (!requestEvent.getContainerRequest().getPath(false).matches("^healthcheck\\/?$")) {
			requestCnt++;
		
			// return the listener instance that will handle this request.
			listener = new FopPdfReqEventListener(requestCnt);
		}
		
		return listener;
	}
}
