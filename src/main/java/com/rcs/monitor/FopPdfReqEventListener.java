package com.rcs.monitor;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

public class FopPdfReqEventListener
	implements RequestEventListener
{
	private final Logger logger; 
	private final int requestNumber;
//	private final long startTime;
	private long methodStartTime;
	 
	public FopPdfReqEventListener(int requestNumber) {
		logger = LogManager.getLogger(FopPdfReqEventListener.class);

		this.requestNumber = requestNumber;
//		startTime = System.currentTimeMillis();
	}
	 
	@Override
	public void onEvent(RequestEvent event) {
		switch (event.getType()) {
			case RESOURCE_METHOD_START:
				logger.info("Request {}: {} {} starting",
						requestNumber,
						event.getUriInfo().getMatchedResourceMethod().getHttpMethod(),
						event.getUriInfo().getRequestUri().getPath());
				methodStartTime = System.currentTimeMillis();
				break;
			case RESOURCE_METHOD_FINISHED:
				logger.info("Request {}: {} {} finished.  Processing time {} ms.",
						requestNumber,
						event.getUriInfo().getMatchedResourceMethod().getHttpMethod(),
						event.getUriInfo().getRequestUri().getPath(),
						(System.currentTimeMillis() - methodStartTime));
				break;
				
			case FINISHED:
/*				
				logger.info("Request {}: finished. Processing time {} ms.",
						requestNumber,
						(System.currentTimeMillis() - startTime));
*/						
				break;

			case ON_EXCEPTION:
				if (event.getException() instanceof jakarta.ws.rs.NotFoundException) {
					logger.info("Request for unsupported URI: {} {}",
							event.getContainerRequest().getMethod(),
							event.getUriInfo().getRequestUri().getPath());
				}
				else {
					if (logger.isErrorEnabled()) {
						StringWriter strWriter = new StringWriter();
						event.getException().printStackTrace(new PrintWriter(strWriter));
		
						logger.error("Exception handling request: {} {}\n{}",
								event.getContainerRequest().getMethod(),
								event.getUriInfo().getRequestUri().getPath(),
								strWriter.toString());
					}
				}
				break;

			case EXCEPTION_MAPPER_FOUND:
			case EXCEPTION_MAPPING_FINISHED:
			case LOCATOR_MATCHED:
			case MATCHING_START:
			case REQUEST_MATCHED:
			case REQUEST_FILTERED:
			case RESP_FILTERS_START:
			case RESP_FILTERS_FINISHED:
			case START:
			case SUBRESOURCE_LOCATED:
			default:
//				logger.info("Request event {}",  event.getType().toString());
				break;
		}
	}
}
