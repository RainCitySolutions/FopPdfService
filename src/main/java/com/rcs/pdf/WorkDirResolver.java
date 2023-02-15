package com.rcs.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;

public class WorkDirResolver
	implements ResourceResolver, URIResolver
{
	private Logger logger;
	protected Path tempDirPath;
	private ResourceResolver parentResourceResolver;
	private URIResolver parentURIResolver;

	protected WorkDirResolver() {
		logger = LogManager.getLogger(this.getClass());
		parentResourceResolver = ResourceResolverFactory.createDefaultResourceResolver();
	}

	public WorkDirResolver (Path path)
	{
		this();

		tempDirPath = path;
	}

	public void setURIResolver(URIResolver resolver) {
		parentURIResolver = resolver;
	}

	public Source resolve(String href, String base)
		throws TransformerException
	{
		Source src = null;
		
		Path file = tempDirPath.resolve(href);
		
		InputStream is = null;
		try {
			is = Files.newInputStream(file);
		} catch (NoSuchFileException nsfe) {
			throw new TransformerException("File not found: " + Path.of(nsfe.getFile()).getFileName().toString());
		}
		catch (IOException e) {
			logger.catching(Level.ERROR, e);
		}

		if (null != is) {
			src = new StreamSource(is);
		}
		else {
			if (null != parentURIResolver) {
				src = parentURIResolver.resolve(href, base);
			}
		}
			
		return src;
	}

	@Override
	public Resource getResource(URI uri) throws IOException {
		Resource rsrc = null;
		try {
			rsrc = parentResourceResolver.getResource(uri);
		}
		catch (IOException e) {
			Path path = Paths.get(uri);
			
			String filename = path.getFileName().toString();
			
			try {
				Source src = resolve(filename, "");

				if (null != src) {
					rsrc = new Resource(((StreamSource)src).getInputStream());
				}
			} catch (TransformerException te) {
				logger.catching(Level.ERROR, te);
			}
		}
		
		return rsrc;
	}

	@Override
	public OutputStream getOutputStream(URI uri) throws IOException {
		return parentResourceResolver.getOutputStream(uri);
	}
}
