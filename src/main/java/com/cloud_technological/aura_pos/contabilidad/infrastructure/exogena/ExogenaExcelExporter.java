package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;
import com.cloud_technological.aura_pos.entity.ExogenaColumnaEntity;
import com.cloud_technological.aura_pos.entity.ExogenaFormatoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLoteEntity;
import com.cloud_technological.aura_pos.utils.ExcelAnchoColumnas;

/**
 * Excel de exógena con las columnas que espera el prevalidador DIAN: primero las de
 * identificación del formato (su {@code encabezado}: concepto, tipo y número de
 * documento, DV, apellidos, nombres, razón social, dirección, departamento,
 * municipio, país) y luego sus columnas de valor en orden. Una hoja por formato; el
 * Excel del año trae todos los formatos. Las cuantías menores salen con el NIT
 * genérico 222222222 (tipo de documento 43).
 */
@Component
public class ExogenaExcelExporter {

    public static final String NIT_CUANTIAS_MENORES = "222222222";

    /** Una fila del formato: concepto, tercero (null = cuantías menores) y sus valores. */
    public record Fila(String concepto, TerceroExogenaDto tercero, Map<String, BigDecimal> valores) {
    }

    /**
     * Dirección y municipio DANE del informante (la empresa). La DIAN los pide en la
     * fila de cuantías menores.
     */
    public record Informante(String direccion, String codigoDane) {
    }

    /** Un formato a exportar: una hoja. */
    public record Hoja(ExogenaFormatoEntity formato, List<ExogenaColumnaEntity> columnas, ExogenaLoteEntity lote,
                       List<Fila> filas, Informante informante) {
    }

    /** Columnas con decimales (el resto va redondeado a pesos). */
    private static final List<String> CON_DECIMALES = List.of("PORCENTAJE");

    /** Un lote: un libro de una hoja. */
    public byte[] exportar(Hoja hoja) {
        return exportar(List.of(hoja));
    }

    /** Varios formatos (el año completo): una hoja por formato. */
    public byte[] exportar(List<Hoja> hojas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font negrita = wb.createFont();
            negrita.setBold(true);
            CellStyle encabezado = wb.createCellStyle();
            encabezado.setFont(negrita);
            encabezado.setWrapText(true);

            for (Hoja h : hojas) escribir(wb, h, encabezado);
            if (hojas.isEmpty()) wb.createSheet("Sin datos");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel de exógena", e);
        }
    }

    private void escribir(Workbook wb, Hoja h, CellStyle estiloEncabezado) {
        ExogenaFormatoEntity f = h.formato();
        Sheet hoja = wb.createSheet(f.getCodigo());
        String[] ident = (f.getEncabezado() != null ? f.getEncabezado() : "CPT,TDOC,NID,DV,RAZ").split(",");

        Row titulo = hoja.createRow(0);
        titulo.createCell(0).setCellValue("Formato " + f.getCodigo() + " v" + f.getVersionDian() + " — "
                + f.getNombre() + " — Año " + h.lote().getAnio() + " — Versión lote " + h.lote().getVersion()
                + " (" + h.lote().getEstado() + ")");

        Row cabecera = hoja.createRow(1);
        int col = 0;
        for (String c : ident) celda(cabecera, col++, IdentidadDian.titulo(c.trim()), estiloEncabezado);
        for (ExogenaColumnaEntity c : h.columnas()) celda(cabecera, col++, c.getNombre(), estiloEncabezado);

        int filaNum = 2;
        for (Fila fila : h.filas()) {
            Row r = hoja.createRow(filaNum++);
            int i = 0;
            for (String c : ident)
                r.createCell(i++).setCellValue(IdentidadDian.valor(c.trim(), fila, h.informante()));
            for (ExogenaColumnaEntity c : h.columnas()) {
                BigDecimal v = fila.valores() != null ? fila.valores().get(c.getCodigo()) : null;
                if (v == null && h.columnas().size() == 1 && fila.valores() != null)
                    v = fila.valores().get(ExogenaCalculadora.VALOR);   // líneas viejas de una sola columna
                BigDecimal redondeado = (v != null ? v : BigDecimal.ZERO)
                        .setScale(CON_DECIMALES.contains(c.getCodigo()) ? 2 : 0, RoundingMode.HALF_UP);
                r.createCell(i++).setCellValue(redondeado.doubleValue());
            }
        }
        // Sin AWT: autoSizeColumn necesita libfreetype, que no está en el servidor.
        ExcelAnchoColumnas.ajustar(hoja, ident.length + h.columnas().size());
    }

    private static void celda(Row r, int i, String texto, CellStyle estilo) {
        Cell c = r.createCell(i);
        c.setCellValue(texto);
        c.setCellStyle(estilo);
    }
}
