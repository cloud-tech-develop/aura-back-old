package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosLineaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosResumenDto;
import com.cloud_technological.aura_pos.repositories.gastos.ReporteGastosQueryRepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * El reporte de gastos existe para que el contador no tenga que rehacer cuentas
 * a mano. Estos tests fijan las dos cosas de las que depende esa promesa: que
 * deducible y no deducible salgan separados, y que el pie sume el período
 * completo y no solo lo que se alcanzó a listar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteGastosServiceTest {

    private static final Integer EMPRESA_ID = 1;

    @Mock private ReporteGastosQueryRepository repository;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks private ReporteGastosService service;

    @BeforeEach
    void setUp() {
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA_ID);
    }

    private ReporteGastosFiltroDto filtro() {
        ReporteGastosFiltroDto f = new ReporteGastosFiltroDto();
        f.setFechaDesde(LocalDate.of(2026, 9, 1));
        f.setFechaHasta(LocalDate.of(2026, 9, 30));
        return f;
    }

    private ReporteGastosLineaDto linea(String grupo, String total, String ded, String noDed) {
        ReporteGastosLineaDto l = new ReporteGastosLineaDto();
        l.setGrupo(grupo);
        l.setCantidad(2);
        l.setTotal(new BigDecimal(total));
        l.setTotalDeducible(new BigDecimal(ded));
        l.setTotalNoDeducible(new BigDecimal(noDed));
        l.setTotalContado(new BigDecimal(total));
        l.setTotalCredito(BigDecimal.ZERO);
        l.setBaseIva(BigDecimal.ZERO);
        l.setValorIva(BigDecimal.ZERO);
        l.setValorRetefuente(BigDecimal.ZERO);
        l.setValorReteica(BigDecimal.ZERO);
        l.setParticipacion(new BigDecimal("50.00"));
        return l;
    }

    /** Un resumen cuyo total del período NO coincide con la suma de las filas. */
    private ReporteGastosResumenDto resumenParcial() {
        ReporteGastosResumenDto r = new ReporteGastosResumenDto();
        r.setLineas(List.of(
                linea("Arriendo", "500000", "500000", "0"),
                linea("Papelería", "100000", "0", "100000")));
        // A propósito distinto de 600.000: simula que las filas listadas son
        // solo una parte del período.
        r.setCantidad(10);
        r.setTotal(new BigDecimal("900000"));
        r.setTotalDeducible(new BigDecimal("700000"));
        r.setTotalNoDeducible(new BigDecimal("200000"));
        r.setTotalContado(new BigDecimal("900000"));
        r.setTotalCredito(BigDecimal.ZERO);
        r.setBaseIva(BigDecimal.ZERO);
        r.setValorIva(BigDecimal.ZERO);
        r.setValorRetefuente(BigDecimal.ZERO);
        r.setValorReteica(BigDecimal.ZERO);
        return r;
    }

    // ── Validación ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Un rango invertido se rechaza en vez de devolver un reporte vacío")
    void rangoInvertidoSeRechaza() {
        ReporteGastosFiltroDto f = filtro();
        f.setFechaDesde(LocalDate.of(2026, 9, 30));
        f.setFechaHasta(LocalDate.of(2026, 9, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resumen(f));

        // Devolver cero gastos sería indistinguible de "no hubo gastos": el
        // usuario cerraría el mes creyendo que no gastó nada.
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).resumen(any(), anyInt());
    }

    @Test
    @DisplayName("Un rango abierto es válido: sin fechas el reporte es toda la historia")
    void rangoAbiertoEsValido() {
        ReporteGastosFiltroDto f = new ReporteGastosFiltroDto();
        when(repository.resumen(any(), anyInt())).thenReturn(new ReporteGastosResumenDto());

        service.resumen(f);

        verify(repository).resumen(f, EMPRESA_ID);
    }

    // ── Excel ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("El pie del Excel suma el período completo, no las filas listadas")
    void elPieSumaElPeriodoNoLasFilas() throws Exception {
        when(repository.resumen(any(), anyInt())).thenReturn(resumenParcial());

        byte[] xlsx = service.excelResumen(filtro());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet ws = wb.getSheetAt(0);
            // fila 0 título, 1 subtítulo, 2 vacía, 3 cabecera, 4-5 datos, 6 totales
            Row totales = ws.getRow(6);
            assertEquals("TOTALES", totales.getCell(0).getStringCellValue());
            // Las filas suman 600.000; el período son 900.000. Gana el período:
            // un pie que suma solo lo visible es una cifra que no cuadra con
            // nada y que alguien va a copiar a una declaración.
            assertEquals(900000d, totales.getCell(2).getNumericCellValue(), 0.01);
            assertEquals(700000d, totales.getCell(3).getNumericCellValue(), 0.01);
            assertEquals(200000d, totales.getCell(4).getNumericCellValue(), 0.01);
        }
    }

    @Test
    @DisplayName("Deducible y no deducible van en columnas separadas, nunca sumados")
    void deducibleYNoDeducibleVanSeparados() throws Exception {
        when(repository.resumen(any(), anyInt())).thenReturn(resumenParcial());

        byte[] xlsx = service.excelResumen(filtro());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet ws = wb.getSheetAt(0);
            Row cabecera = ws.getRow(3);
            assertEquals("Deducible", cabecera.getCell(3).getStringCellValue());
            assertEquals("No deducible", cabecera.getCell(4).getStringCellValue());

            Row arriendo = ws.getRow(4);
            assertEquals("Arriendo", arriendo.getCell(0).getStringCellValue());
            assertEquals(500000d, arriendo.getCell(3).getNumericCellValue(), 0.01);
            assertEquals(0d, arriendo.getCell(4).getNumericCellValue(), 0.01);

            Row papeleria = ws.getRow(5);
            assertEquals(0d, papeleria.getCell(3).getNumericCellValue(), 0.01);
            assertEquals(100000d, papeleria.getCell(4).getNumericCellValue(), 0.01);
        }
    }

    @Test
    @DisplayName("El Excel deja escrito qué se filtró: si no, nadie puede defenderlo después")
    void elSubtituloDejaConstanciaDelFiltro() throws Exception {
        ReporteGastosFiltroDto f = filtro();
        f.setDeducible(Boolean.TRUE);
        f.setCategoria("Arriendo");
        when(repository.resumen(any(), anyInt())).thenReturn(resumenParcial());

        byte[] xlsx = service.excelResumen(f);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            String subtitulo = wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue();
            assertTrue(subtitulo.contains("01/09/2026"), subtitulo);
            assertTrue(subtitulo.contains("30/09/2026"), subtitulo);
            // Sin esto, tres meses después nadie sabe si el archivo traía todos
            // los gastos o solo los deducibles de una categoría.
            assertTrue(subtitulo.contains("Solo deducibles"), subtitulo);
            assertTrue(subtitulo.contains("Arriendo"), subtitulo);
        }
    }

    @Test
    @DisplayName("Las columnas de plata llevan separador de miles, no el número pelado")
    void lasCifrasLlevanSeparadorDeMiles() throws Exception {
        when(repository.resumen(any(), anyInt())).thenReturn(resumenParcial());

        byte[] xlsx = service.excelResumen(filtro());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet ws = wb.getSheetAt(0);

            // El valor guardado sigue siendo numérico — se puede sumar en Excel
            // — y el formato es lo que lo dibuja como 500.000.
            var celda = ws.getRow(4).getCell(2);
            assertEquals(500000d, celda.getNumericCellValue(), 0.01);
            assertTrue(celda.getCellStyle().getDataFormatString().contains("#,##0"),
                    "la columna de total salió sin formato de miles: "
                            + celda.getCellStyle().getDataFormatString());

            // También en el pie, que es la cifra que alguien copia.
            var total = ws.getRow(6).getCell(2);
            assertTrue(total.getCellStyle().getDataFormatString().contains("#,##0"),
                    "el total salió sin formato de miles");
        }
    }

    @Test
    @DisplayName("Un reporte sin resultados no sale vacío: lo dice")
    void reporteVacioLoDice() throws Exception {
        when(repository.resumen(any(), anyInt())).thenReturn(new ReporteGastosResumenDto());

        byte[] xlsx = service.excelResumen(filtro());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            String mensaje = wb.getSheetAt(0).getRow(4).getCell(0).getStringCellValue();
            assertTrue(mensaje.contains("No hay gastos"), mensaje);
        }
    }
}
