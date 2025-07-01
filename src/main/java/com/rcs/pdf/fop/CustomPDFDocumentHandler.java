package com.rcs.pdf.fop;

import org.apache.fop.render.intermediate.IFContext;
import org.apache.fop.render.intermediate.IFDocumentHandlerConfigurator;
import org.apache.fop.render.pdf.PDFDocumentHandler;
import org.apache.fop.render.pdf.PDFRendererConfig.PDFRendererConfigParser;

public class CustomPDFDocumentHandler extends PDFDocumentHandler {

    public CustomPDFDocumentHandler(IFContext context) {
        super(context);
    }

    @Override
    public IFDocumentHandlerConfigurator getConfigurator() {
        return new CustomPDFRendererConfigurator(getUserAgent(), new PDFRendererConfigParser());
    }

}
