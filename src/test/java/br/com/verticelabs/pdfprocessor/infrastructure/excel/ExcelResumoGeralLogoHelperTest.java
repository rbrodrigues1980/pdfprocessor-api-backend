package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelResumoGeralLogoHelperTest {

    private final ExcelResumoGeralLogoHelper logoHelper = new ExcelResumoGeralLogoHelper();

    @Test
    void inserirLogoNaCelulaMescladaG1H8_incluiImagemNoWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Resumo Geral");
            for (int r = 0; r <= ExcelResumoGeralLogoHelper.LOGO_LAST_ROW; r++) {
                sheet.createRow(r);
            }
            sheet.setColumnWidth(ExcelResumoGeralLogoHelper.LOGO_FIRST_COL, 15 * 256);
            sheet.setColumnWidth(ExcelResumoGeralLogoHelper.LOGO_LAST_COL, 40 * 256);

            logoHelper.prepararAreaLogo(sheet);
            logoHelper.inserirLogoNaCelula(sheet, workbook, 15 * 256, 40 * 256);

            assertTrue(workbook.getAllPictures().size() >= 1, "Workbook deve conter a imagem do logo");
            assertEquals(1, sheet.getNumMergedRegions(), "Deve existir merge G1:H8");
            CellRangeAddress merge = sheet.getMergedRegion(0);
            assertEquals(0, merge.getFirstRow());
            assertEquals(7, merge.getLastRow());
            assertEquals(6, merge.getFirstColumn());
            assertEquals(7, merge.getLastColumn());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            assertTrue(out.size() > 5000, "XLSX gerado deve conter bytes da imagem");
        }
    }
}
