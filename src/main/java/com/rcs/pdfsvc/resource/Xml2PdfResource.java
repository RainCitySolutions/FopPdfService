package com.rcs.pdfsvc.resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import com.rcs.FileCleanupManager;
import com.rcs.pdf.PDFGenerator;
import com.rcs.pdf.WorkDirResolver;
import com.rcs.pdfsvc.config.AppResourceConfig;


@Path("xml2pdf")
public class Xml2PdfResource {
	private static final String LOG_FILE = "pdfGen.log";

	private Logger logger;
	
	@Context
	private Configuration config;

	public Xml2PdfResource () {
		logger = LogManager.getLogger(Xml2PdfResource.class.getName());
	}

	private java.nio.file.Path getWorkDir() {
		// grab our context and create a temporary directory for storing our work
		java.nio.file.Path workDir = (java.nio.file.Path)config.getProperty(AppResourceConfig.WORK_DIR);

    	try {
			Files.createDirectories(workDir);
		} catch (IOException e) {
			logger.error("Unable to create context work folder", e);
		}

    	return workDir;
	}

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response convertToPdf(
    		@FormDataParam("files[]") List<FormDataBodyPart> bodyParts,
    		@FormDataParam("files[]") List<FormDataContentDisposition> fileDispositions,
    		@FormDataParam("fofile") String foFile,
    		@FormDataParam("xmlfile") String xmlFile,
    		@FormDataParam("xsltfile") String xsltFile,
    		@FormDataParam("pdffile") String pdfFile,
    		@FormDataParam("loglevel") String logLevel,
    		@Context UriInfo uriInfo)
    {
    	Response resp;

		logger.traceEntry();

    	logger.info("Received request to create PDF: {}",  pdfFile);
    	
    	// Check if a PDF filename, and a source file or files were provided
    	if (null != pdfFile && null != bodyParts && null != fileDispositions) {
    		Set<String> filenames = new HashSet<String>();
    		
    		// Create a set of filenames that were sent
    		for (FormDataContentDisposition contentDisp : fileDispositions) {
    			filenames.add(contentDisp.getFileName());
    		}
    		
    		// check if the filename(s) specified were in the set provided
    		if ( (null != foFile && filenames.contains(foFile)) ||
	    		 (null != xmlFile && filenames.contains(xmlFile) &&
	    		  null != xsltFile && filenames.contains(xsltFile)) )
	    	{
				try {
					// create a temporary directory in the work directory for storing our files
	    			java.nio.file.Path tempDir = Files.createTempDirectory(getWorkDir(), "pdfGen");

	    			FileCleanupManager.getInstance().addEntry(tempDir, 15, TimeUnit.MINUTES);
	    			
	    			// Save the uploaded files to the temporary directory
	    			for (FormDataBodyPart bodyPart : bodyParts) {
		    			BodyPartEntity bodyPartEntity = (BodyPartEntity) bodyPart.getEntity();
		    			String fileName = bodyPart.getContentDisposition().getFileName();
		
		    			saveFile(bodyPartEntity.getInputStream(), tempDir.resolve(fileName));
		    		}

	    			// Create a log file for the transformation results
		    		java.nio.file.Path tmpLogFile = tempDir.resolve(LOG_FILE);

		    		// Initialize the PDF Generator
//		    		PDFGenerator pdfGen = new PDFGenerator(new WorkDirResolver(tempDir), tmpLogFile, Level.toLevel(logLevel));
		    		PDFGenerator pdfGen = PDFGenerator.getInstance();
		    		
		    		if (null != foFile) {
		    			pdfGen.generateFromFo(tempDir.resolve(foFile), tempDir.resolve(pdfFile), new WorkDirResolver(tempDir), tmpLogFile, Level.toLevel(logLevel));
		    		}
		    		else {
		    			pdfGen.generateFromXml(tempDir.resolve(xmlFile), tempDir.resolve(xsltFile), tempDir.resolve(pdfFile), new WorkDirResolver(tempDir), tmpLogFile, Level.toLevel(logLevel));
		    		}
		    		
		    	    rewritePdf(tempDir.resolve(pdfFile).toFile());
		    		
		    		// Return the location of the PDF file and the contents of the log file as the response body
		    		UriBuilder uriBldr =
		    				UriBuilder.fromUri(uriInfo.getRequestUri())
		    						  .path(tempDir.getFileName().toString())
		    						  .path(pdfFile);
		    		
		    		URI pdfUri = uriBldr.build();
		    		
		    		logger.info("Successfully created PDF: {}",  pdfUri.toString());

		    		resp = Response
		    				.created(pdfUri)
		    				.entity(Files.exists(tmpLogFile) ? new String(Files.readAllBytes(tmpLogFile)) : "Log not available")
		    				.build();
				} catch (IOException e) {
		    		logger.catching(e);
					resp = Response.serverError().build();
				}
	    	}
        	else {
        		logger.info("Missing input file(s) or file(s) specfied were not provided");
        		resp = Response.status(Status.BAD_REQUEST).entity("Missing required parameters: missing filename or filename referenced isn't in the set of files provided").build();
        	}
    	}
    	else {
    		logger.info("Missing parameters required to execute task");
    		resp = Response.status(Status.BAD_REQUEST).entity("Missing required parameters: \"pdffile\", \"files\"").build();
    	}

		logger.traceExit();

		return resp;
    }

    private void saveFile(InputStream file, java.nio.file.Path filePath) {
		try {
			Files.copy(file, filePath);
		} catch (IOException ie) {
			logger.catching(Level.ERROR, ie);
		}
	}
    

    /**
     * Creates a new File object using the source file and the new extension.
     * 
     * @param srcFile A File object
     * @param newExtension The new extension to use for the file
     * 
     * @return A new file object with the new extension
     */
    private File changeExtension(File srcFile, String newExtension) {
    	int i = srcFile.getName().lastIndexOf('.');
    	String name = srcFile.getName().substring(0,i);

    	return new File(srcFile.getParent() + "/" + name + newExtension);
    }
    
    /**
     * Rewrites a PDF file to optimize the size of the PDF file.
     * 
     * @param pdfFile The PDF file to be optimized. 
     */
    private void rewritePdf(File pdfFile) {
    	logger.traceEntry();

    	File orgPdfFile = changeExtension(pdfFile, ".org.pdf");
    	pdfFile.renameTo(orgPdfFile);

    	try {
			PdfWriter writer = new PdfWriter(pdfFile);
			writer.setSmartMode(true);
			try (PdfDocument pdfDoc = new PdfDocument(writer) ) {
				pdfDoc.initializeOutlines();
				try (PdfDocument orgPdfDoc = new PdfDocument(new PdfReader(orgPdfFile))) {
					orgPdfDoc.copyPagesTo(1, orgPdfDoc.getNumberOfPages(), pdfDoc);
				}
				catch (IOException ioe) {
					logger.catching(Level.ERROR, ioe);
				}
			}
			catch (Exception e) {
				logger.catching(Level.ERROR, e);
			}
		} catch (FileNotFoundException fnfe) {
			logger.catching(Level.ERROR, fnfe);
		}

		logger.traceExit();
    }

    /**
     * Method handling HTTP GET request for a particular PDF file.
     * 
     * @param id An identifier which makes up the unique part of a temporary
     * 			 filename created previously
     * @param pdfFilename The name to use for the PDF file returned. 
     *
     * @return Response A {@link}jakarta.ws.rs.core.Response object. Will either
     *         be a 404, Not Found response or 200, OK with the PDF file.
     */
    @GET
    @Path("{id}/{pdffile}")
    @Produces("application/pdf")
    public Response fetchPdf(
    		@PathParam("id") String id,
    		@PathParam("pdffile") String pdfFilename)
    {
    	Response resp;
    	
		logger.traceEntry();
    	
		logger.info("Received fetch request for {}/{}", id, pdfFilename);
    	
    	// Construct the path to the PDF file
    	java.nio.file.Path filePath = getWorkDir().resolve(id).resolve(pdfFilename);

    	// Check if the PDF file exists
		if (Files.exists(filePath)) {
			try {
				// Grab the PDF and stream it back to the caller
				InputStream inStrm = Files.newInputStream(filePath);
				resp =
					Response
						.ok(inStrm)
						.type("application/pdf")
						.header("Content-Disposition", "filename="+pdfFilename)
						.build();
				logger.info("Returning {}", pdfFilename);
			}
			catch (IOException e) {
				logger.info("Unable to read {}/{}", id, pdfFilename);				
				resp = Response.status(Status.NOT_FOUND).build();
			}
		}
		else {
			logger.info("File {}/{} does not exist", id, pdfFilename);				
			resp = Response.status(Status.NOT_FOUND).build();
		}

		logger.traceExit();

		return resp;
    }
}
