package net.jmhering.webprintinggui.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class PDFHandler {
    private static Logger logger = LoggerFactory.getLogger("net.jmhering.webprintinggui");

    public static byte[] splitDocument(InputStream inputStream, int from, int to) throws IOException {
        PDDocument pdDocument = Loader.loadPDF(inputStream);

        if (to < 0) {
            to = pdDocument.getPages().getCount();
        }

        Splitter splitter = new Splitter();
        splitter.setStartPage(from);
        splitter.setEndPage(to);
        List<PDDocument> pages = splitter.split(pdDocument);
        PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();

        PDDocument newDocument = null;
        boolean isFirstPage = true;

        for (PDDocument page: pages) {
            if (isFirstPage) {
                newDocument = page;
                isFirstPage = false;
                continue;
            }
            pdfMergerUtility.appendDocument(newDocument, page);
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        assert newDocument != null;

        newDocument.save(byteArrayOutputStream);
        logger.info("New document has {} pages", newDocument.getPages().getCount());

        return byteArrayOutputStream.toByteArray();
    }
}
