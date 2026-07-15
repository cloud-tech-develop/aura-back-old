package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import com.cloud_technological.aura_pos.entity.ExogenaConceptoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaFormatoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLineaEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLoteEntity;

/**
 * Excel del lote de exógena con las columnas que espera el prevalidador
 * DIAN (E11): concepto, tipo y número de documento, DV, nombres/razón
 * social, dirección, municipio y valor. Las cuantías menores salen con el
 * NIT genérico 222222222 (tipo de documento 43).
 */
@Component
public class ExogenaExcelExporter {

    public static final String NIT_CUANTIAS_MENORES = "222222222";

    private static final String[] COLUMNAS = {
            "Concepto", "Tipo documento", "Número identificación", "DV",
            "Primer apellido", "Primer nombre", "Razón social",
            "Dirección", "Municipio", "Valor"
    };

    public byte[] exportar(ExogenaFormatoEntity formato, ExogenaLoteEntity lote,
            List<ExogenaLineaEntity> lineas,
            Map<Long, ExogenaConceptoEntity> conceptosPorId,
            Map<Long, TerceroExogenaDto> tercerosPorId) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Formato " + formato.getCodigo());

            Font negrita = wb.createFont();
            negrita.setBold(true);
            CellStyle encabezado = wb.createCellStyle();
            encabezado.setFont(negrita);

            Row titulo = hoja.createRow(0);
            titulo.createCell(0).setCellValue("Formato " + formato.getCodigo() + " v"
                    + formato.getVersionDian() + " — " + formato.getNombre()
                    + " — Año " + lote.getAnio() + " — Versión lote " + lote.getVersion());

            Row cabecera = hoja.createRow(1);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell c = cabecera.createCell(i);
                c.setCellValue(COLUMNAS[i]);
                c.setCellStyle(encabezado);
            }

            int filaNum = 2;
            for (ExogenaLineaEntity linea : lineas) {
                ExogenaConceptoEntity concepto = conceptosPorId.get(linea.getConceptoId());
                TerceroExogenaDto tercero = linea.getTerceroId() != null
                        ? tercerosPorId.get(linea.getTerceroId()) : null;
                Row fila = hoja.createRow(filaNum++);
                fila.createCell(0).setCellValue(concepto != null ? concepto.getCodigo() : "");
                if (tercero != null) {
                    fila.createCell(1).setCellValue(codigoTipoDocumento(tercero.tipoDocumento()));
                    fila.createCell(2).setCellValue(nz(tercero.numeroDocumento()));
                    fila.createCell(3).setCellValue(nz(tercero.dv()));
                    fila.createCell(4).setCellValue(nz(tercero.apellidos()));
                    fila.createCell(5).setCellValue(nz(tercero.nombres()));
                    fila.createCell(6).setCellValue(nz(tercero.razonSocial()));
                    fila.createCell(7).setCellValue(nz(tercero.direccion()));
                    fila.createCell(8).setCellValue(nz(tercero.municipio()));
                } else {
                    fila.createCell(1).setCellValue("43");
                    fila.createCell(2).setCellValue(NIT_CUANTIAS_MENORES);
                    fila.createCell(6).setCellValue("CUANTÍAS MENORES");
                }
                fila.createCell(9).setCellValue(linea.getValor().doubleValue());
            }
            for (int i = 0; i < COLUMNAS.length; i++) {
                hoja.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel de exógena", e);
        }
    }

    /** Códigos DIAN de tipo de documento; si no se reconoce, se deja tal cual. */
    private static String codigoTipoDocumento(String tipo) {
        if (tipo == null) {
            return "43";
        }
        return switch (tipo.toUpperCase()) {
            case "CC", "CEDULA", "CÉDULA" -> "13";
            case "NIT" -> "31";
            case "CE" -> "22";
            case "TI" -> "12";
            case "PASAPORTE", "PP" -> "41";
            default -> tipo;
        };
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
