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
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraAbonoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraDocumentoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraResumenDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraTerceroDto;
import com.cloud_technological.aura_pos.repositories.cartera.ReporteCarteraQueryRepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * El estado de cuenta tiene que poder defenderse frente al cliente: cuánto
 * debe, desde cuándo, y qué abonos se le reconocieron con el dato de en qué
 * caja entró cada uno — que es lo que la parte B del plan hizo posible.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteCarteraServiceTest {

    private static final Integer EMPRESA_ID = 1;

    @Mock private ReporteCarteraQueryRepository repository;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks private ReporteCarteraService service;

    @BeforeEach
    void setUp() {
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA_ID);
    }

    private ReporteCarteraFiltroDto filtro() {
        ReporteCarteraFiltroDto f = new ReporteCarteraFiltroDto();
        f.setTipo(ReporteCarteraFiltroDto.CXC);
        return f;
    }

    private ReporteCarteraDocumentoDto documento(List<ReporteCarteraAbonoDto> abonos) {
        ReporteCarteraDocumentoDto d = new ReporteCarteraDocumentoDto();
        d.setId(7L);
        d.setNumeroCuenta("CC-000007");
        d.setTerceroNombre("Tienda La Esquina");
        d.setTerceroDocumento("900123456");
        d.setFechaEmision(LocalDateTime.of(2026, 8, 1, 10, 0));
        d.setFechaVencimiento(LocalDateTime.of(2026, 8, 31, 10, 0));
        d.setTotalDeuda(new BigDecimal("500000"));
        d.setTotalAbonado(new BigDecimal("200000"));
        d.setSaldoPendiente(new BigDecimal("300000"));
        d.setDiasMora(6);
        d.setEdad("1-30");
        d.setEstado("VENCIDA");
        d.setAbonos(abonos);
        return d;
    }

    private ReporteCarteraAbonoDto abono(String caja, boolean otroDia) {
        ReporteCarteraAbonoDto a = new ReporteCarteraAbonoDto();
        a.setId(11L);
        a.setCuentaId(7L);
        a.setFechaPago(LocalDateTime.of(2026, 9, 2, 15, 30));
        a.setMonto(new BigDecimal("200000"));
        a.setMetodoPago("EFECTIVO");
        a.setReferencia("Comprobante RC-000045");
        a.setCajaNombre(caja);
        a.setUsuarioNombre("cajera1");
        a.setCajaOtroDia(otroDia);
        return a;
    }

    // ── Validación ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Un tipo que no sea CXC o CXP se rechaza")
    void tipoInvalidoSeRechaza() {
        ReporteCarteraFiltroDto f = filtro();
        f.setTipo("CXZ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resumen(f));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).resumen(any(), anyInt());
    }

    @Test
    @DisplayName("Un rango invertido se rechaza: cero cartera se lee como que nadie debe")
    void rangoInvertidoSeRechaza() {
        ReporteCarteraFiltroDto f = filtro();
        f.setFechaDesde(LocalDate.of(2026, 9, 30));
        f.setFechaHasta(LocalDate.of(2026, 9, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.documentos(f));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).documentos(any(), anyInt());
    }

    // ── Estado de cuenta ────────────────────────────────────────────────

    @Test
    @DisplayName("El Excel del detalle pide los abonos: sin ellos el saldo parcial no se explica")
    void elDetalleSiempreTraeLosAbonos() {
        ReporteCarteraFiltroDto f = filtro();
        f.setIncluirAbonos(Boolean.FALSE); // el usuario no los pidió...
        when(repository.documentos(any(), anyInt())).thenReturn(
                new PageImpl<>(List.of(documento(List.of(abono("Caja principal", false)))),
                        PageRequest.of(0, 50), 1));

        service.excelDetalle(f);

        // ...pero un estado de cuenta sin abonos es una lista de saldos que el
        // cliente no puede verificar. El servicio lo fuerza.
        ArgumentCaptor<ReporteCarteraFiltroDto> captor =
                ArgumentCaptor.forClass(ReporteCarteraFiltroDto.class);
        verify(repository).documentos(captor.capture(), anyInt());
        assertEquals(Boolean.TRUE, captor.getValue().getIncluirAbonos());
    }

    @Test
    @DisplayName("Cada abono dice en qué caja entró la plata")
    void elAbonoDiceEnQueCajaEntro() throws Exception {
        when(repository.documentos(any(), anyInt())).thenReturn(
                new PageImpl<>(List.of(documento(List.of(abono("Caja principal", false)))),
                        PageRequest.of(0, 50), 1));

        byte[] xlsx = service.excelDetalle(filtro());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet ws = wb.getSheetAt(0);
            // fila 4 el documento, fila 5 su abono indentado debajo
            Row abono = ws.getRow(5);
            assertTrue(abono.getCell(0).getStringCellValue().contains("abono"));
            String detalle = abono.getCell(2).getStringCellValue();
            // Este dato no existía hasta que la parte B ató los abonos de
            // comprobante a su turno: antes no se podía saber quién recibió la
            // plata ni dónde cayó.
            assertTrue(detalle.contains("Caja principal"), detalle);
            assertTrue(detalle.contains("cajera1"), detalle);
            assertEquals(200000d, abono.getCell(7).getNumericCellValue(), 0.01);
        }
    }

    @Test
    @DisplayName("Un abono sin caja lo dice, no lo deja en blanco")
    void abonoSinCajaSeExplica() throws Exception {
        when(repository.documentos(any(), anyInt())).thenReturn(
                new PageImpl<>(List.of(documento(List.of(abono(null, true)))),
                        PageRequest.of(0, 50), 1));

        byte[] xlsx = service.excelDetalle(filtro());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            String detalle = wb.getSheetAt(0).getRow(5).getCell(2).getStringCellValue();
            // Una celda vacía se lee como un error del reporte; el motivo real
            // es que esa plata se movió del cajón otro día.
            assertTrue(detalle.contains("otro día"), detalle);
        }
    }

    // ── Resumen por edades ──────────────────────────────────────────────

    @Test
    @DisplayName("El resumen imprime el saldo repartido por edades y el título de la cara correcta")
    void resumenPorEdades() throws Exception {
        ReporteCarteraTerceroDto x = new ReporteCarteraTerceroDto();
        x.setTerceroNombre("Proveedor S.A.S.");
        x.setTerceroDocumento("800999111");
        x.setDocumentos(3);
        x.setTotalDeuda(new BigDecimal("1000000"));
        x.setTotalAbonado(new BigDecimal("250000"));
        x.setSaldoPendiente(new BigDecimal("750000"));
        x.setCorriente(new BigDecimal("100000"));
        x.setMora1a30(new BigDecimal("150000"));
        x.setMora31a60(BigDecimal.ZERO);
        x.setMora61a90(BigDecimal.ZERO);
        x.setMoraMas90(new BigDecimal("500000"));
        x.setDiasMoraMax(120);

        ReporteCarteraResumenDto datos = new ReporteCarteraResumenDto();
        datos.setTerceros(List.of(x));
        datos.setDocumentos(3);
        datos.setSaldoPendiente(new BigDecimal("750000"));
        datos.setMoraMas90(new BigDecimal("500000"));
        when(repository.resumen(any(), anyInt())).thenReturn(datos);

        ReporteCarteraFiltroDto f = filtro();
        f.setTipo(ReporteCarteraFiltroDto.CXP);
        byte[] xlsx = service.excelResumen(f);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet ws = wb.getSheetAt(0);
            assertTrue(ws.getRow(0).getCell(0).getStringCellValue().contains("CUENTAS POR PAGAR"));

            Row fila = ws.getRow(4);
            assertEquals("Proveedor S.A.S.", fila.getCell(0).getStringCellValue());
            assertEquals(750000d, fila.getCell(6).getNumericCellValue(), 0.01);
            // "Me deben 750.000" y "500.000 llevan más de 90 días" son dos
            // frases distintas: la segunda es la que hace que alguien llame.
            assertEquals(500000d, fila.getCell(11).getNumericCellValue(), 0.01);
            assertEquals(120d, fila.getCell(12).getNumericCellValue(), 0.01);

            // Las cifras se guardan como número y se dibujan con separador de
            // miles: 750.000, no 750000.
            assertTrue(fila.getCell(6).getCellStyle().getDataFormatString().contains("#,##0"),
                    "el saldo salió sin formato de miles");
        }
    }
}
