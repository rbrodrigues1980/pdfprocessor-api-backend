package br.com.verticelabs.pdfprocessor.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Tesseract text extraction from images.
 */
@Configuration
@Slf4j
public class TextExtractionConfig {

    @Value("${text-extraction.tesseract.datapath:${ocr.tesseract.datapath:}}")
    private String tesseractDataPath;

    @Value("${text-extraction.tesseract.language:${ocr.tesseract.language:por}}")
    private String language;

    @Value("${text-extraction.tesseract.dpi:${ocr.tesseract.dpi:300}}")
    private int dpi;

    @PostConstruct
    public void init() {
        // Set TESSDATA_PREFIX environment variable for Tesseract to find data files
        if (tesseractDataPath != null && !tesseractDataPath.isEmpty()) {
            System.setProperty("TESSDATA_PREFIX", tesseractDataPath);
            log.info("Tesseract TESSDATA_PREFIX set to: {}", tesseractDataPath);
        }
    }

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();

        // Set data path if configured
        if (tesseractDataPath != null && !tesseractDataPath.isEmpty()) {
            tesseract.setDatapath(tesseractDataPath);
            log.info("Tesseract datapath configured: {}", tesseractDataPath);
        }

        // Set language (default: Portuguese)
        tesseract.setLanguage(language);
        log.info("Tesseract language set to: {}", language);

        // Set page segmentation mode (PSM 3 = Fully automatic page segmentation)
        tesseract.setPageSegMode(3);

        // Set Engine Mode (OEM 3 = Default, based on what is available)
        tesseract.setOcrEngineMode(3);

        log.info("Tesseract text extraction configured successfully");
        return tesseract;
    }
}

