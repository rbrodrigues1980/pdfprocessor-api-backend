package br.com.verticelabs.pdfprocessor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PdfProcessorApplication

fun main(args: Array<String>) {
    runApplication<PdfProcessorApplication>(*args)
}
