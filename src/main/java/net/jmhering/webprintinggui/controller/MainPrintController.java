package net.jmhering.webprintinggui.controller;

import com.hp.jipp.encoding.IppPacket;
import com.hp.jipp.trans.IppPacketData;
import net.jmhering.webprintinggui.pdf.PDFHandler;
import net.jmhering.webprintinggui.transport.HttpIppClientTransport;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.hp.jipp.model.Types.documentFormat;
import static com.hp.jipp.model.Types.sides;
import static com.hp.jipp.model.Types.copies;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

@Controller
public class MainPrintController {
    private static Logger logger = LoggerFactory.getLogger("net.jmhering.webprintinggui");

    @Value("${gui.title}")
    private String guiTitle;
    @Value("${ipp.uri}")
    private String printerURI;

    @GetMapping("/")
    public String mainPage(Model model) {
        model.addAttribute("title", guiTitle);
        return "main";
    }

    @GetMapping("/info")
    public String handleInfo(Model model) throws IOException, URISyntaxException {
        URI printer = new URI(printerURI);
        HttpIppClientTransport httpIppClientTransport = new HttpIppClientTransport();
        IppPacket requestInfos = IppPacket.getPrinterAttributes(printer).build();
        IppPacketData infos = httpIppClientTransport.sendData(printer, new IppPacketData(requestInfos));
        logger.info(infos.toString());
        model.addAttribute("infos", infos.getPacket().prettyPrint(100, "  "));

        return "info";
    }

    @PostMapping("/print")
    public String handlePrint(@RequestParam("file") MultipartFile file, @RequestParam(value = "pages_from", required = false) Integer from, @RequestParam(value = "pages_to", required = false) Integer to, @RequestParam(value = "duplex", required = false) String duplexString, @RequestParam(value = "copies_number", required = false) Integer numberOfCopies, RedirectAttributes redirectAttributes) throws URISyntaxException, IOException {
        boolean duplex = !duplexString.equals("einseitig");

        redirectAttributes.addFlashAttribute("message",
                "Printing: " + file.getOriginalFilename() + "!");
        URI printer = new URI(printerURI);
        HttpIppClientTransport httpIppClientTransport = new HttpIppClientTransport();

        IppPacket.Builder printRequestBuilder = IppPacket.printJob(printer)
                .putOperationAttributes(documentFormat.of("application/pdf"));

        if (duplex) {
            logger.info("Duplex enabled");
            printRequestBuilder.putJobAttributes(sides.of("two-sided-long-edge"));
        }

        if (numberOfCopies != null && numberOfCopies > 1) {
            printRequestBuilder.putJobAttributes(copies.of(numberOfCopies));
        }

        IppPacket printRequest = printRequestBuilder.build();

        InputStream packetData = file.getInputStream();

        if (from != null || to != null) {
            int fromParam = 0;
            int toParam = -1;

            if (from != null)
                fromParam = from;

            if (to != null)
                toParam = to;

            logger.info("Print range from {} to {}", fromParam, toParam);

            byte[] data = PDFHandler.splitDocument(file.getInputStream(), fromParam, toParam);
            packetData = new ByteArrayInputStream(data);
        }

        logger.info("Printing document");
        IppPacketData result = httpIppClientTransport.sendData(printer, new IppPacketData(printRequest, packetData));
        if (result.getPacket().getCode() != 0) {
            throw new IOException("Unable to print: " + result.getPacket());
        }

        return "redirect:/";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception exception, Model model) {
        model.addAttribute("error_class", exception.getClass().getCanonicalName());
        model.addAttribute("error_message", exception.getMessage());
        model.addAttribute("stacktrace", ExceptionUtils.getMessage(exception));
        return "error";
    }
}
