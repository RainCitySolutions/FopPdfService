package com.rcs.pdf.fop;

import java.awt.FontFormatException;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.io.InternalResourceResolver;
import org.apache.fop.fonts.EmbedFontInfo;
import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.EncodingMode;
import org.apache.fop.fonts.Font;
import org.apache.fop.fonts.FontCollection;
import org.apache.fop.fonts.FontTriplet;
import org.apache.fop.fonts.FontUris;
import org.apache.fop.render.RendererConfig.RendererConfigParser;
import org.apache.fop.render.pdf.PDFRendererConfigurator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.rcs.pdf.ThreadWorkDirResolver;


public class CustomPDFRendererConfigurator
	extends PDFRendererConfigurator
{
	private Logger logger;

    public CustomPDFRendererConfigurator(FOUserAgent userAgent, RendererConfigParser rendererConfigParser) {
        super(userAgent, rendererConfigParser);
        
        logger = LogManager.getLogger(CustomPDFRendererConfigurator.class);
    }

    @Override
    protected FontCollection getCustomFontCollection(InternalResourceResolver resolver, String mimeType)
            throws FOPException {

        List<EmbedFontInfo> fontList = new ArrayList<>();

        Path startDir = ThreadWorkDirResolver.getWorkDir();
        String pattern = "glob:*.ttf"; // match all .ttf files
        
        try {
            List<Path> matchingFiles = findFilesMatchingPattern(startDir, pattern);

            for (Path file : matchingFiles) {
            	addFont(file, fontList);
            }
        } catch (IOException ioe) {
        	logger.warn("IOException searching for font files", ioe);
        }

        return createCollectionFromFontList(resolver, fontList);
    }

    private void addFont(Path filename, List<EmbedFontInfo> fontList)
    {
    	try {
	        java.awt.Font awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, filename.toFile());
	        
	        Map<TextAttribute,?> fontAttrs = awtFont.getAttributes();
	    	
		    FontUris fontUris = new FontUris(filename.toUri(), null);
		    List<FontTriplet> triplets = new ArrayList<>();
		    
		    triplets.add(
		    		new FontTriplet(
		    				fontAttrs.get(TextAttribute.FAMILY).toString(),
		    				Font.STYLE_NORMAL,
		    				Font.WEIGHT_NORMAL
		    				)
		    		);
		
		    EmbedFontInfo fontInfo = new EmbedFontInfo(fontUris, false, false, triplets, null, EncodingMode.AUTO, EmbeddingMode.AUTO, false, false, false, false);
		
		    fontList.add(fontInfo);
    	} catch (IOException ioe) {
        	logger.warn("Exception reading font file", ioe);
    	} catch (FontFormatException ffe) {
        	logger.warn("Exception reading font file", ffe);
		}
    }

    private List<Path> findFilesMatchingPattern(Path startDir, String pattern) throws IOException {
        List<Path> matchingFiles = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);

        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matcher.matches(file.getFileName())) {
                    matchingFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Optionally, skip certain directories here
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // Handle errors during file access
                return FileVisitResult.CONTINUE;
            }
        });
        return matchingFiles;
    }
}
