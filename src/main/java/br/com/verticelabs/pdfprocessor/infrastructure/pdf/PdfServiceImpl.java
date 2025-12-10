package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class PdfServiceImpl implements PdfService {

    @Override
    public Mono<String> extractText(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = Loader.loadPDF(readAllBytes(inputStream))) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Map<String, String>> extractMetadata(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            BodyContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            ParseContext pcontext = new ParseContext();
            PDFParser pdfparser = new PDFParser();

            try (InputStream is = new ByteArrayInputStream(readAllBytes(inputStream))) {
                pdfparser.parse(is, handler, metadata, pcontext);
            }

            Map<String, String> metaMap = new HashMap<>();
            for (String name : metadata.names()) {
                metaMap.put(name, metadata.get(name));
            }
            return metaMap;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> extractTextFromPage(InputStream inputStream, int pageNumber) {
        return Mono.fromCallable(() -> {
            byte[] bytes = readAllBytes(inputStream);
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                return stripper.getText(document);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> getTotalPages(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            byte[] bytes = readAllBytes(inputStream);
            try (PDDocument document = Loader.loadPDF(bytes)) {
                return document.getNumberOfPages();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        return inputStream.readAllBytes();
    }
}
