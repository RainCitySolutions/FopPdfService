package com.rcs.pdfsvc;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.lookup.MainMapLookup;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import com.rcs.FileCleanupManager;
import com.rcs.pdf.PDFGenerator;
import com.rcs.pdfsvc.config.AppProperties;
import com.rcs.pdfsvc.config.AppResourceConfig;

public class FopPdfService {

    static HttpServer server;
	static ScheduledExecutorService scheduler;

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
    	System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    	System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");

		AppProperties props = new AppProperties();

		// Set config properties for Log4J
	    MainMapLookup.setMainArguments(new String[] {"logdir", props.getLogDir().toString()});
	    
	    Files.createDirectories(props.getWorkDir());
	    
		scheduler = Executors.newSingleThreadScheduledExecutor();

	    /*
	     * The intent here is to schedule any subdirectories we find in the
	     * work directory for deletion as they may have been left behind from
	     * a previous run.
	     * 
	     * In an environment where there are multiple instances of the PDF
	     * Service running they would be sharing a common working directory.
	     * In this situation we may be scheduling folders for deletion which
	     * are also scheduled within another instance. This means the
	     * deletion process need to be tolerant that a folder may have
	     * already been deleted by time the process gets around to attempting
	     * to delete a folder.
	     */
	    Files.walkFileTree(
	    		props.getWorkDir(), 
	    		EnumSet.noneOf(FileVisitOption.class), 
	    		1, 
	    		new SimpleFileVisitor<Path> () {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (file.getFileName().toString().matches("^pdfGen.*$")) {
					    	FileCleanupManager.getInstance().addEntry(file, 1, TimeUnit.MINUTES);
						}
						return FileVisitResult.CONTINUE;
					}
	    		});

	    FileCleanupManager.getInstance().initializeSchedule(scheduler);

        // create a resource config that scans for JAX-RS resources and providers
        // in com.example package
        final ResourceConfig rc = new AppResourceConfig(props);
        
        // Kickstart the PDF Generator
        PDFGenerator.getInstance();

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        server = GrizzlyHttpServerFactory.createHttpServer((URI)rc.getProperty(AppResourceConfig.BASE_URI), rc);
        


        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                System.out.println("Shutting down server");

                scheduler.shutdown();
                
                if (null != server) {
	                GrizzlyFuture<HttpServer> future = server.shutdown();
	
	                while (!future.isDone()) {
	                    System.out.println("Waiting for server to shutdown");
	                    try {
	                    	Thread.sleep(1000);
	                    }
	                    catch (Exception e) {}
	                }
                }

                System.out.println("Server has finished shutting down");
            }
        }));
    }

}
