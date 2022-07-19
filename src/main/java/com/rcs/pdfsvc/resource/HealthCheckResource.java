package com.rcs.pdfsvc.resource;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;


@Path("healthcheck")
public class HealthCheckResource {
	private static final int megabytes_in_bytes = 1024 * 1024;

    /**
     * Method handling HTTP GET request. Used to check if service is active.
     * 
     * @return Response A {@link}jakarta.ws.rs.core.Response object, always 200, OK.
     */
    @GET
    public Response getCheck()
    {
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

		long xmx = memoryBean.getHeapMemoryUsage().getMax() / megabytes_in_bytes;
		long xms = memoryBean.getHeapMemoryUsage().getInit() / megabytes_in_bytes;
		long used = memoryBean.getHeapMemoryUsage().getUsed() / megabytes_in_bytes;

		return Response.ok().entity(
				String.format("Health Check -- <br>Initial Memory (Xms) : %d MB,<br>Used Memory : %d MB,<br>Max Memory (Xmx) : %d MB", xms, used, xmx)
				).build();
    }
}
