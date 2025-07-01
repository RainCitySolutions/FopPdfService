package com.rcs.pdf.fop;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.render.intermediate.IFContext;
import org.apache.fop.render.intermediate.IFDocumentHandler;
import org.apache.fop.render.pdf.PDFDocumentHandlerMaker;

public class CustomPDFDocumentHandlerMaker extends PDFDocumentHandlerMaker
{
    @Override
    public IFDocumentHandler makeIFDocumentHandler(IFContext ifContext)
    {
        CustomPDFDocumentHandler handler = new CustomPDFDocumentHandler(ifContext);
        FOUserAgent ua = ifContext.getUserAgent();
        
        if (ua.isAccessibilityEnabled()) {
            ua.setStructureTreeEventHandler(handler.getStructureTreeEventHandler());
        }

        return handler;
    }
}
