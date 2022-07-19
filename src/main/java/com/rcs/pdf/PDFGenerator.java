package com.rcs.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.ConfigurationException;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.apache.fop.events.Event;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.EventListener;
import org.apache.fop.events.model.EventSeverity;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.xalan.transformer.TransformerImpl;
import org.apache.xmlgraphics.util.MimeConstants;


public class PDFGenerator {
	private Logger logger;
	private FopFactory fopFactory;
	private TransformerFactory transformerFactory;

	private class LogEventListener
		implements EventListener
	{
		private Set<String> msgSet = new HashSet<String>();
		private String logFile;
		private Level logLevel;
		private String jobId;
		private Marker pdfGenMarker;

		public LogEventListener (Path logFile, Level logLevel)
		{
			this.logFile = logFile.toAbsolutePath().toString();
			this.logLevel = logLevel;
			this.jobId = logFile.getParent().getFileName().toString();

			pdfGenMarker = MarkerManager.getMarker("FopPdfGen");
		}

		@Override
		public void processEvent(Event event) {
			String msg = EventFormatter.format(event);
			
			if (!msgSet.contains(msg)) {
				msgSet.add(msg);

				EventSeverity severity = event.getSeverity();

				try (final CloseableThreadContext.Instance ctc = CloseableThreadContext
						.put("ROUTINGKEY", "PDFGen-" + jobId)
						.put("jobId", jobId)
						.put("logFile", logFile)
						.put("logLevel", logLevel.toString())) {

			        if (severity == EventSeverity.INFO) {
			        	logger.info(pdfGenMarker, msg);
			        } else if (severity == EventSeverity.WARN) {
			        	logger.warn(pdfGenMarker,msg);
			        } else if (severity == EventSeverity.ERROR) {
			        	logger.error(pdfGenMarker, msg);
			        } else if (severity == EventSeverity.FATAL) {
			        	logger.fatal(pdfGenMarker, msg);
			        } else {
			            assert false;
			        }
				}				
			}
		}
	}
	
	private static PDFGenerator singleton = null;
	
	public static synchronized PDFGenerator getInstance() {
		if (null == singleton) {
			// Create an instance and kickstart the FOP and Transformer factories
			singleton = new PDFGenerator();
			singleton.logger.debug("Kick-starting PDFGenerator");
			try (OutputStream outStrm = new ByteArrayOutputStream()) {
				singleton.fopFactory.newFop(MimeConstants.MIME_PDF, outStrm);
			} catch (FOPException | IOException e) {
				singleton.logger.catching(e);
			}
			try {
				singleton.transformerFactory.newTransformer();
			} catch (TransformerConfigurationException e) {
				singleton.logger.catching(e);
			}
		}
		return singleton;
		
	}

//	private PDFGenerator(WorkDirResolver workDirResolver, Path jobLogFile, Level logLevel) {
	private PDFGenerator() {
		this.logger = LogManager.getLogger(PDFGenerator.class.getName());
		
		// Set the User-Agent for any external references in the XLST
		System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
		
//		initializeFopFactory(workDirResolver);
		initializeFopFactory();
	}

	private void initializeFopFactory ()
	{
		DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
		Configuration cfg;
		try (InputStream cfgStrm = PDFGenerator.class.getResourceAsStream("/fop.xconf")) {
		    cfg = cfgBuilder.build(cfgStrm);

			fopFactory = new FopFactoryBuilder(new File(".").toURI()).setConfiguration(cfg).build();

			transformerFactory = TransformerFactory.newInstance();

		} catch (IOException | ConfigurationException e) {
			logger.catching(Level.ERROR, e);
		}
	}

	/*
	 * private void initializeFopFactory (WorkDirResolver workDirResolver) {
	 * DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
	 * Configuration cfg; try (InputStream cfgStrm =
	 * PDFGenerator.class.getResourceAsStream("/fop.xconf")) { cfg =
	 * cfgBuilder.build(cfgStrm);
	 * 
	 * this.workDirResolver = workDirResolver; fopFactory = new
	 * FopFactoryBuilder(new File(".").toURI(),
	 * workDirResolver).setConfiguration(cfg).build();
	 * 
	 * transformerFactory = TransformerFactory.newInstance();
	 * 
	 * } catch (IOException | ConfigurationException e) {
	 * logger.catching(Level.ERROR, e); } }
	 * 
	 */
	
	private FOUserAgent createFOUserAgent(Path jobLogFile, Level logLevel) {
		logger.traceEntry();

		// Setup output
		FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
		foUserAgent.getEventBroadcaster().addEventListener(new LogEventListener(jobLogFile, logLevel));

		logger.traceExit();

		return foUserAgent;
	}

	public void generateFromFo (final Path foFile, Path pdfFile, final WorkDirResolver workDirResolver, final Path jobLogFile, final Level logLevel)
	{
		logger.traceEntry();

		// Setup output
		try (OutputStream outStrm = Files.newOutputStream(pdfFile))
		{
			FOUserAgent foUserAgent = createFOUserAgent(jobLogFile, logLevel);

			// Construct fop with desired output format
			Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outStrm);

			// Setup XSLT
			Transformer transformer = transformerFactory.newTransformer();
			if (null != transformer) {
				// Set the value of a <param> in the stylesheet
				transformer.setParameter("versionParam", "2.0");
		
				// Setup input for XSLT transformation
				Source src = new StreamSource(Files.newInputStream(foFile));
		
				// Resulting SAX events (the generated FO) must be piped through to FOP
				Result res = new SAXResult(fop.getDefaultHandler());
		
				// Start XSLT transformation and FOP processing
				if (transformer instanceof TransformerImpl) {
					((TransformerImpl)transformer).setDebug(true);
				}
	
				transformer.setURIResolver(workDirResolver);
				transformer.transform(src, res);
			}
		} catch (Exception e) {
			logger.catching(Level.ERROR, e);
			e.printStackTrace(System.err);
		}

		logger.traceExit();
	}


	public void generateFromXml (final Path xmlFile, final Path xsltFile, final Path pdfFile, final WorkDirResolver workDirResolver, final Path jobLogFile, final Level logLevel)
	{
		logger.traceEntry();

		// Setup output
		try (OutputStream outStrm = Files.newOutputStream(pdfFile))
		{
			FOUserAgent foUserAgent = createFOUserAgent(jobLogFile, logLevel);
			
			// Setup XSLT
			try
			{
				// Construct fop with desired output format
				Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outStrm);

				Source xsltSrc = new StreamSource(Files.newInputStream(xsltFile));
				URIResolver orgResolver = transformerFactory.getURIResolver();
				transformerFactory.setURIResolver(workDirResolver);
				Transformer transformer = transformerFactory.newTransformer(xsltSrc);
				if (null != transformer) {
					// Set the value of a <param> in the stylesheet
					transformer.setParameter("versionParam", "2.0");

					// Setup input for XSLT transformation
					Source xmlSrc = new StreamSource(Files.newInputStream(xmlFile));
		
					// Resulting SAX events (the generated FO) must be piped through to FOP
					Result res = new SAXResult(fop.getDefaultHandler());
		
					// Start XSLT transformation and FOP processing
					if (transformer instanceof TransformerImpl) {
						((TransformerImpl)transformer).setDebug(true);
					}

					transformer.setURIResolver(workDirResolver);
					transformer.transform(xmlSrc, res);
				}
				else {
					
				}
				transformerFactory.setURIResolver(orgResolver);
			}
			catch (TransformerException te) {
				logger.catching(Level.ERROR, te);
			}
		} catch (Exception e) {
			logger.catching(Level.ERROR, e);
		}

		logger.traceExit();
	}
}
