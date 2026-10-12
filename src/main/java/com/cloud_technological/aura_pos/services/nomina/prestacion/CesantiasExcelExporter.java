package com.cloud_technological.aura_pos.services.nomina.prestacion;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.CesantiasConsignacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.CesantiasConsignacionQueryRepository.Consignacion;
import com.cloud_technological.aura_pos.services.NominaDocumentoService;
import com.cloud_technological.aura_pos.utils.ExcelEstilos;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Consignación de cesantías en Excel (rediseño R6, §9): una hoja por fondo con los
 * empleados de la nómina de cesantías confirmada y sus totales. Los empleados sin
 * fondo de cesantías van en la hoja "SIN FONDO" para que no se pierdan.
 */
@Service
public class CesantiasExcelExporter {

    private static final String[] COLUMNAS = {
        "Tipo doc.", "Documento", "Nombres", "Apellidos", "Salario", "Días", "Cesantías"
    };
    private static final String SIN_FONDO = "SIN FONDO";

    private final NominaDocumentoService documentos;
    private final CesantiasConsignacionQueryRepository queryRepository;

    public CesantiasExcelExporter(NominaDocumentoService documentos,
            CesantiasConsignacionQueryRepository queryRepository) {
        this.documentos = documentos;
        this.queryRepository = queryRepository;
    }

    /**
     * Excel de la consignación.
     *
     * @throws GlobalException 400 si el documento no es una nómina de cesantías confirmada
     */
    public byte[] excel(Long documentoId, Integer empresaId) {
        DocumentoNominaDto d = documentos.obtener(documentoId, empresaId);
        if (!"CESANTIAS".equals(d.getClaseComprobante()) || !"NOMINA".equals(d.getClaseDocumento())
                || !"CONFIRMADO".equals(d.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La consignación sale de una nómina de cesantías confirmada.");

        Map<String, List<Consignacion>> porFondo = new LinkedHashMap<>();
        for (Consignacion c : queryRepository.consignaciones(documentoId))
            porFondo.computeIfAbsent(c.fondo() != null ? c.fondo() : SIN_FONDO, k -> new ArrayList<>()).add(c);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelEstilos e = new ExcelEstilos(wb);
            if (porFondo.isEmpty()) {
                XSSFSheet ws = wb.createSheet("Cesantías");
                int fila = e.encabezado(ws, "CONSIGNACIÓN DE CESANTÍAS", subtitulo(d, null), COLUMNAS);
                e.filaVacia(ws, fila, COLUMNAS.length, "La nómina no tiene cesantías liquidadas.");
            }
            for (Map.Entry<String, List<Consignacion>> g : porFondo.entrySet())
                hoja(wb, e, d, g.getKey(), g.getValue());
            wb.write(out);
            return out.toByteArray();
        } catch (GlobalException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel de cesantías: " + ex.getMessage());
        }
    }

    /** Hoja de un fondo con sus empleados y el total. */
    private static void hoja(XSSFWorkbook wb, ExcelEstilos e, DocumentoNominaDto d, String fondo,
            List<Consignacion> filas) {
        XSSFSheet ws = wb.createSheet(nombreHoja(wb, fondo));
        String nit = filas.get(0).fondoNit();
        int fila = e.encabezado(ws, "CONSIGNACIÓN DE CESANTÍAS — " + fondo, subtitulo(d, nit), COLUMNAS);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < filas.size(); i++) {
            Consignacion c = filas.get(i);
            XSSFCellStyle st = e.fila(i);
            XSSFCellStyle val = e.filaValor(i);
            XSSFCellStyle cant = e.filaCantidad(i);
            Row r = ws.createRow(fila++);
            int col = 0;
            e.texto(r, col++, c.tipoDocumento(), st);
            e.texto(r, col++, c.numeroDocumento(), st);
            e.texto(r, col++, c.nombres(), st);
            e.texto(r, col++, c.apellidos(), st);
            e.numero(r, col++, c.salario(), val);
            e.numero(r, col++, c.dias(), cant);
            e.numero(r, col, c.valor(), val);
            total = total.add(c.valor() != null ? c.valor() : BigDecimal.ZERO);
        }
        Row rt = ws.createRow(fila);
        int col = 0;
        e.texto(rt, col++, "TOTAL (" + filas.size() + " empleado" + (filas.size() == 1 ? "" : "s") + ")", e.total);
        for (; col < COLUMNAS.length - 1; col++) e.texto(rt, col, "", e.total);
        e.numero(rt, col, total, e.valorTotal);
        e.ajustarAnchos(ws, COLUMNAS.length);
    }

    private static String subtitulo(DocumentoNominaDto d, String nit) {
        return "Nómina " + d.getNumeroCompleto() + " · período " + d.getFechaDesde() + " a " + d.getFechaHasta()
                + (nit != null ? " · NIT del fondo " + nit : "");
    }

    /** Nombre de hoja válido para Excel (31 caracteres, sin símbolos prohibidos y sin repetirse). */
    private static String nombreHoja(XSSFWorkbook wb, String fondo) {
        String base = fondo.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (base.length() > 28) base = base.substring(0, 28);
        String nombre = base;
        for (int i = 2; wb.getSheet(nombre) != null; i++) nombre = base + " " + i;
        return nombre;
    }
}
