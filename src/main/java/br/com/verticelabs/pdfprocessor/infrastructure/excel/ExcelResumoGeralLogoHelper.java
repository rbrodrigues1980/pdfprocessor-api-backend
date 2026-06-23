package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Insere o logo Origium na área mesclada G1:H8 do Resumo Geral,
 * centralizado e dimensionado para caber na mesclagem (MOVE_AND_RESIZE).
 */
@Slf4j
@Component
public class ExcelResumoGeralLogoHelper {

    static final String LOGO_CLASSPATH = "excel/origium_logo.png";

    /** Linhas 1–8 (0-based 0–7), colunas G–H (0-based 6–7). */
    public static final int LOGO_FIRST_ROW = 0;
    public static final int LOGO_LAST_ROW = 7;
    public static final int LOGO_FIRST_COL = 6;
    public static final int LOGO_LAST_COL = 7;

    public boolean isCelulaAreaLogo(int row, int col) {
        return row >= LOGO_FIRST_ROW && row <= LOGO_LAST_ROW
                && col >= LOGO_FIRST_COL && col <= LOGO_LAST_COL;
    }

    /**
     * Remove células individuais G/H nas linhas 1–8, mescla G1:H8 e garante célula âncora G1.
     */
    public void prepararAreaLogo(Sheet sheet) {
        for (int r = LOGO_FIRST_ROW; r <= LOGO_LAST_ROW; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Cell cellH = row.getCell(LOGO_LAST_COL);
            if (cellH != null) {
                row.removeCell(cellH);
            }
            if (r > LOGO_FIRST_ROW) {
                Cell cellG = row.getCell(LOGO_FIRST_COL);
                if (cellG != null) {
                    row.removeCell(cellG);
                }
            }
        }

        Row firstRow = sheet.getRow(LOGO_FIRST_ROW);
        if (firstRow == null) {
            firstRow = sheet.createRow(LOGO_FIRST_ROW);
        }
        if (firstRow.getCell(LOGO_FIRST_COL) == null) {
            firstRow.createCell(LOGO_FIRST_COL);
        }

        removerMergeExistente(sheet, LOGO_FIRST_ROW, LOGO_LAST_ROW, LOGO_FIRST_COL, LOGO_LAST_COL);
        sheet.addMergedRegion(new CellRangeAddress(
                LOGO_FIRST_ROW, LOGO_LAST_ROW, LOGO_FIRST_COL, LOGO_LAST_COL));
    }

    /**
     * Insere o logo centralizado na mesclagem G1:H8.
     *
     * @param colWidthG largura da coluna G em unidades POI (15 * 256)
     * @param colWidthH largura da coluna H em unidades POI (40 * 256)
     */
    public void inserirLogoNaCelula(
            XSSFSheet sheet,
            XSSFWorkbook workbook,
            int colWidthG,
            int colWidthH) {
        try (InputStream is = new ClassPathResource(LOGO_CLASSPATH).getInputStream()) {
            byte[] bytes = is.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                log.warn("Logo Origium inválido ou ilegível: {}", LOGO_CLASSPATH);
                return;
            }

            prepararAreaLogo(sheet);

            int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
            int gWidthEmu = Units.columnWidthToEMU(colWidthG);
            int hWidthEmu = Units.columnWidthToEMU(colWidthH);
            int totalWidthEmu = gWidthEmu + hWidthEmu;
            int totalHeightEmu = calcularAlturaAreaEmu(sheet, LOGO_FIRST_ROW, LOGO_LAST_ROW);

            int fitWidthEmu;
            int fitHeightEmu;
            double imageAspect = (double) image.getWidth() / image.getHeight();
            double areaAspect = (double) totalWidthEmu / totalHeightEmu;
            if (imageAspect > areaAspect) {
                fitWidthEmu = totalWidthEmu;
                fitHeightEmu = (int) (totalWidthEmu / imageAspect);
            } else {
                fitHeightEmu = totalHeightEmu;
                fitWidthEmu = (int) (totalHeightEmu * imageAspect);
            }

            int offsetX = Math.max(0, (totalWidthEmu - fitWidthEmu) / 2);
            int offsetY = Math.max(0, (totalHeightEmu - fitHeightEmu) / 2);

            int[] colWidthsEmu = {gWidthEmu, hWidthEmu};
            int[] rowHeightsEmu = obterAlturasLinhasEmu(sheet, LOGO_FIRST_ROW, LOGO_LAST_ROW);

            PosicaoAnchor inicio = converterOffsetParaAnchor(offsetX, offsetY, colWidthsEmu, rowHeightsEmu,
                    LOGO_FIRST_COL, LOGO_FIRST_ROW);
            PosicaoAnchor fim = converterOffsetParaAnchor(offsetX + fitWidthEmu, offsetY + fitHeightEmu,
                    colWidthsEmu, rowHeightsEmu, LOGO_FIRST_COL, LOGO_FIRST_ROW);

            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = new XSSFClientAnchor(
                    inicio.dx(), inicio.dy(), fim.dx(), fim.dy(),
                    inicio.col(), inicio.row(), fim.col(), fim.row());
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            drawing.createPicture(anchor, pictureIdx);

            log.debug("Logo Origium centralizado em G1:H8 ({}x{} EMU na área {}x{})",
                    fitWidthEmu, fitHeightEmu, totalWidthEmu, totalHeightEmu);
        } catch (Exception e) {
            log.warn("Não foi possível inserir logo Origium ({}): {}", LOGO_CLASSPATH, e.getMessage());
        }
    }

    private record PosicaoAnchor(int col, int row, int dx, int dy) {
    }

    private PosicaoAnchor converterOffsetParaAnchor(
            int offsetX, int offsetY,
            int[] colWidthsEmu, int[] rowHeightsEmu,
            int firstCol, int firstRow) {

        int remainingX = offsetX;
        int col = firstCol;
        int dx = 0;
        for (int i = 0; i < colWidthsEmu.length; i++) {
            if (remainingX <= colWidthsEmu[i]) {
                col = firstCol + i;
                dx = remainingX;
                break;
            }
            remainingX -= colWidthsEmu[i];
            col = firstCol + i + 1;
            dx = remainingX;
        }

        int remainingY = offsetY;
        int row = firstRow;
        int dy = 0;
        for (int i = 0; i < rowHeightsEmu.length; i++) {
            if (remainingY <= rowHeightsEmu[i]) {
                row = firstRow + i;
                dy = remainingY;
                break;
            }
            remainingY -= rowHeightsEmu[i];
            row = firstRow + i + 1;
            dy = remainingY;
        }

        return new PosicaoAnchor(col, row, dx, dy);
    }

    private int[] obterAlturasLinhasEmu(Sheet sheet, int firstRow, int lastRow) {
        int[] heights = new int[lastRow - firstRow + 1];
        for (int r = firstRow; r <= lastRow; r++) {
            heights[r - firstRow] = Units.toEMU(obterAlturaLinhaPontos(sheet, r));
        }
        return heights;
    }

    private float obterAlturaLinhaPontos(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row != null && row.getHeightInPoints() > 0) {
            return row.getHeightInPoints();
        }
        return sheet.getDefaultRowHeightInPoints();
    }

    private int calcularAlturaAreaEmu(Sheet sheet, int firstRow, int lastRow) {
        float totalPoints = 0f;
        for (int r = firstRow; r <= lastRow; r++) {
            totalPoints += obterAlturaLinhaPontos(sheet, r);
        }
        return Units.toEMU(totalPoints);
    }

    private void removerMergeExistente(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == firstRow && region.getLastRow() == lastRow
                    && region.getFirstColumn() == firstCol && region.getLastColumn() == lastCol) {
                sheet.removeMergedRegion(i);
            }
        }
    }
}
