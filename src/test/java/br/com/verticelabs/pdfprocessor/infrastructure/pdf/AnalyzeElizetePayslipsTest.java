package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AnalyzeElizetePayslipsTest {

    @Test
    public void analyzeFiles() throws Exception {
        Path pdfDir = Path.of("docs/pdf");
        Path logFile = Path.of("docs/pdf/analysis_output.txt");
        
        try (PrintWriter out = new PrintWriter(new FileWriter(logFile.toFile()))) {
            out.println("=== ANALYZING ELIZETE PAYSLIPS ===");
            
            File[] files = pdfDir.toFile().listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files == null || files.length == 0) {
                out.println("No PDF files found in " + pdfDir.toAbsolutePath());
                return;
            }

            PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
            DocumentTypeDetectionServiceImpl detector = new DocumentTypeDetectionServiceImpl();

            for (File file : files) {
                out.println("\n--------------------------------------------------");
                out.println("FILE: " + file.getName());
                out.println("Size: " + file.length() + " bytes");

                String text;
                try (PDDocument doc = Loader.loadPDF(file)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    text = stripper.getText(doc);
                }

                out.println("Text length: " + text.length() + " chars");
                out.println("Sample text (first 500 chars):");
                out.println(text.substring(0, Math.min(text.length(), 500)));

                DocumentType detectedType = detector.detectType(text).block();
                out.println("Detected type: " + detectedType);

                // Attempt FUNCEF parsing
                try {
                    List<PdfLineParser.ParsedLine> funcefParsed = parser.parseLinesFuncef(text, "2016/01");
                    out.println("FUNCEF parsed lines: " + funcefParsed.size());
                    for (PdfLineParser.ParsedLine line : funcefParsed) {
                        out.println("  FUNCEF Match -> Code: " + line.getCodigo() + ", Desc: " + line.getDescricao() + ", Val: " + line.getValorStr() + ", Ref: " + line.getReferencia());
                    }
                } catch (Exception e) {
                    out.println("FUNCEF parsing error: " + e.getMessage());
                }

                // Attempt CAIXA parsing
                try {
                    List<PdfLineParser.ParsedLine> caixaParsed = parser.parseLines(text, DocumentType.CAIXA);
                    out.println("CAIXA parsed lines: " + caixaParsed.size());
                    for (PdfLineParser.ParsedLine line : caixaParsed) {
                        out.println("  CAIXA Match -> Code: " + line.getCodigo() + ", Desc: " + line.getDescricao() + ", Val: " + line.getValorStr() + ", Ref: " + line.getReferencia());
                    }
                } catch (Exception e) {
                    out.println("CAIXA parsing error: " + e.getMessage());
                }

                // Save full text to a txt file in docs/pdf/ for detailed analysis
                Path txtOut = Path.of("docs/pdf/" + file.getName().replace(".pdf", "_text.txt"));
                Files.writeString(txtOut, text);
                out.println("Saved extracted text to " + txtOut.toAbsolutePath());
            }
        }
        System.out.println("Analysis output saved to " + logFile.toAbsolutePath());
    }
}
