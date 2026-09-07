package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

import com.cloud_technological.aura_pos.dto.auditoria.AreaAuditoria;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaFiltroDto;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaResultadoDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDetalleDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDto;
import com.cloud_technological.aura_pos.dto.auditoria.Severidad;
import com.cloud_technological.aura_pos.repositories.cartera.ReporteCarteraQueryRepository;
import com.cloud_technological.aura_pos.repositories.gastos.ReporteGastosQueryRepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

/**
 * El PDF gerencial se le entrega a un dueño de negocio y, eventualmente, a
 * quien le cuestione las cifras. Estos tests leen el texto del PDF generado —
 * no solo comprueban que no reviente — porque lo que importa es qué dice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteGerencialServiceTest {

    private static final Integer EMPRESA_ID = 1;

    @Mock private AuditoriaService auditoriaService;
    @Mock private AsientoContableService contabilidad;
    @Mock private ReporteAvanzadoService avanzado;
    @Mock private ReporteCarteraQueryRepository carteraRepo;
    @Mock private ReporteGastosQueryRepository gastosRepo;
    @Mock private com.cloud_technological.aura_pos.repositories.kardex.KardexQueryRepository kardexRepo;
    @Mock private IEmpresaService empresaService;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks private ReporteGerencialService service;

    @BeforeEach
    void setUp() {
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA_ID);
        // Las fuentes de indicadores se dejan caer a propósito: el reporte tiene
        // que salir igual, con su nota de "no hay datos", no con una excepción.
        when(empresaService.obtenerEmpresaActual(anyInt(), any(), any()))
                .thenThrow(new RuntimeException("sin empresa"));
        when(avanzado.resumenAvanzado(anyInt(), any(), any()))
                .thenThrow(new RuntimeException("sin ventas"));
        when(contabilidad.estadoResultados(anyInt(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("sin contabilidad"));
        when(carteraRepo.resumen(any(), anyInt()))
                .thenThrow(new RuntimeException("sin cartera"));
        when(gastosRepo.resumen(any(), anyInt()))
                .thenThrow(new RuntimeException("sin gastos"));
        when(kardexRepo.movimientoPorFamilia(anyInt(), any(), any()))
                .thenThrow(new RuntimeException("sin inventario"));
    }

    private AuditoriaResultadoDto auditoria(List<HallazgoDto> hallazgos, int alta,
            BigDecimal enRiesgo) {
        AuditoriaResultadoDto a = new AuditoriaResultadoDto();
        a.setFechaDesde(LocalDate.of(2026, 8, 1));
        a.setFechaHasta(LocalDate.of(2026, 8, 31));
        a.setHallazgos(hallazgos);
        a.setAlta(alta);
        a.setMedia(0);
        a.setBaja(0);
        a.setMontoEnRiesgo(enRiesgo);
        return a;
    }

    private HallazgoDto hallazgo() {
        HallazgoDto h = new HallazgoDto();
        h.setCodigo("CAJA_DIFERENCIA");
        h.setTitulo("Arqueos que no cuadraron");
        h.setDescripcion("El efectivo contado no coincidió con el esperado.");
        h.setCruce("Efectivo contado  vs  base + ventas + ingresos − egresos");
        h.setRecomendacion("Revisar turno por turno con el cajero.");
        h.setCausasProbables(List.of(
                "Ventas cobradas que no pasaron por el sistema.",
                "Gastos pagados del cajón sin registrarlos."));
        h.setSeveridad(Severidad.ALTA);
        h.setArea(AreaAuditoria.CAJA);
        h.setCantidad(1);
        h.setMonto(new BigDecimal("954076.02"));

        HallazgoDetalleDto d = new HallazgoDetalleDto();
        d.setReferencia("Turno #55");
        d.setFecha(LocalDate.of(2026, 8, 21));
        d.setDescripcion("CAJA 1 — sobrante");
        d.setMonto(new BigDecimal("954076.02"));
        h.setDetalle(List.of(d));

        HallazgoDetalleDto ctx = new HallazgoDetalleDto();
        ctx.setReferencia("Merma #12");
        ctx.setFecha(LocalDate.of(2026, 8, 21));
        ctx.setDescripcion("Turno #55 · Estuco x 25 kg · MERMA · 8 unidades");
        ctx.setMonto(new BigDecimal("80000"));
        h.setContexto(List.of(ctx));
        h.setContextoTitulo("Qué se movió en el inventario durante esos turnos");
        return h;
    }

    /**
     * El texto del PDF con los espacios normalizados.
     *
     * <p>Dónde corta una línea lo decide el ancho de la caja, no el
     * contenido: una frase puede quedar partida en cualquier punto. Sin
     * normalizar, un test pasa o falla según dónde caiga el salto — que no es
     * lo que se quiere probar.
     */
    private String texto(byte[] pdf) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                sb.append(PdfTextExtractor.getTextFromPage(doc.getPage(i))).append(' ');
            }
        }
        return sb.toString().replaceAll("\\s+", " ");
    }

    // ── Lo que el documento tiene que decir ────────────────────────────

    @Test
    @DisplayName("El semáforo abre el documento, no lo cierra")
    void elSemaforoVaEnLaPortada() throws Exception {
        when(auditoriaService.auditar(any()))
                .thenReturn(auditoria(List.of(hallazgo()), 1, new BigDecimal("954076.02")));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        // Quien abre el reporte quiere saber si tiene un problema, no leer doce
        // páginas hasta encontrarlo.
        int posAviso = t.indexOf("requieren revisión");
        int posDetalle = t.indexOf("Turno #55");
        assertTrue(posAviso >= 0, "falta el aviso del semáforo");
        assertTrue(posDetalle > posAviso, "el detalle no puede ir antes del semáforo");
    }

    @Test
    @DisplayName("Cada hallazgo lleva su monto y qué hacer con él")
    void elHallazgoTraeMontoYSalida() throws Exception {
        when(auditoriaService.auditar(any()))
                .thenReturn(auditoria(List.of(hallazgo()), 1, new BigDecimal("954076.02")));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        assertTrue(t.contains("954.076"), "el monto tiene que salir con separador de miles");
        // Un hallazgo sin salida es solo una queja.
        assertTrue(t.contains("Revisar turno por turno"));
    }

    @Test
    @DisplayName("El anexo A dice qué se cruzó contra qué")
    void elAnexoCitaLasFuentes() throws Exception {
        when(auditoriaService.auditar(any()))
                .thenReturn(auditoria(List.of(hallazgo()), 1, new BigDecimal("954076.02")));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        // Un reporte de auditoría que no puede señalar de dónde sale un número
        // no se puede defender frente a quien lo cuestione.
        assertTrue(t.contains("Cómo se obtuvo cada cifra"));
        assertTrue(t.contains("Efectivo contado"));
    }

    @Test
    @DisplayName("El PDF siempre pide el detalle: sin él el anexo B queda vacío")
    void siempreSePideElDetalle() {
        when(auditoriaService.auditar(any())).thenReturn(auditoria(List.of(), 0, BigDecimal.ZERO));
        AuditoriaFiltroDto f = new AuditoriaFiltroDto();
        f.setIncluirDetalle(Boolean.FALSE); // aunque venga apagado...

        service.generarPdf(f);

        // ...un hallazgo sin sus filas es una cifra que nadie puede ir a mirar.
        ArgumentCaptor<AuditoriaFiltroDto> captor =
                ArgumentCaptor.forClass(AuditoriaFiltroDto.class);
        verify(auditoriaService).auditar(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getIncluirDetalle());
    }

    @Test
    @DisplayName("Un período limpio lo dice, no sale en blanco")
    void periodoLimpioLoDice() throws Exception {
        when(auditoriaService.auditar(any())).thenReturn(auditoria(List.of(), 0, BigDecimal.ZERO));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        assertTrue(t.contains("sin hallazgos graves"));
        assertFalse(t.contains("requieren revisión"));
    }

    @Test
    @DisplayName("Si falta una fuente de datos el reporte sale igual, con su nota")
    void unaFuenteCaidaNoTumbaElReporte() throws Exception {
        // Todas las fuentes de indicadores están lanzando excepción (ver setUp).
        when(auditoriaService.auditar(any())).thenReturn(auditoria(List.of(), 0, BigDecimal.ZERO));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        // Un reporte que no se puede generar porque el negocio no tiene ventas
        // este mes es peor que uno que lo dice.
        assertTrue(t.contains("REPORTE GERENCIAL DE AUDITORÍA"));
        assertTrue(t.contains("No hay ventas registradas"));
        assertTrue(t.contains("contabilidad todavía no está configurada"));
    }

    @Test
    @DisplayName("Cada hallazgo explica por qué suele pasar, marcado como hipótesis")
    void elHallazgoExplicaSusCausas() throws Exception {
        when(auditoriaService.auditar(any()))
                .thenReturn(auditoria(List.of(hallazgo()), 1, new BigDecimal("954076.02")));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        // Es lo que separa un dato de un análisis: "sobrante de $954.076" no le
        // dice a nadie dónde buscar.
        assertTrue(t.contains("Por qué suele pasar"));
        assertTrue(t.contains("Ventas cobradas que no pasaron por el sistema"));
        // Y se dicen como lo que son: el motor detecta el desacuerdo, no la
        // causa. Sin esta advertencia alguien toma la primera como un hecho.
        assertTrue(t.contains("no un diagnóstico"));
    }

    @Test
    @DisplayName("El sobrante de caja se muestra junto a lo que se movió en el inventario")
    void elHallazgoDeCajaTraeSuContextoDeInventario() throws Exception {
        when(auditoriaService.auditar(any()))
                .thenReturn(auditoria(List.of(hallazgo()), 1, new BigDecimal("954076.02")));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        // Un sobrante de caja y una merma del mismo turno no son dos hallazgos:
        // son uno con dos caras. Por separado, cada uno parece otra cosa.
        assertTrue(t.contains("Qué se movió en el inventario"));
        assertTrue(t.contains("Merma #12"));
        assertTrue(t.contains("MERMA · 8 unidades"));
    }

    @Test
    @DisplayName("Sin movimientos de inventario la sección lo dice, no desaparece")
    void laSeccionDeInventarioSiempreEstá() throws Exception {
        when(auditoriaService.auditar(any())).thenReturn(auditoria(List.of(), 0, BigDecimal.ZERO));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        // El lector tiene que poder distinguir "no hubo movimientos" de "esta
        // parte del reporte falló".
        assertTrue(t.contains("Cómo se movió el inventario"));
    }

    @Test
    @DisplayName("Las gráficas no usan AWT: el PDF se genera sin fuentes del sistema")
    void lasGraficasNoDependenDeAwt() throws Exception {
        // Este test no puede probar la ausencia de AWT directamente, pero sí que
        // el PDF con gráficas se genera completo. Si alguien cambiara GraficaPdf
        // por una librería basada en Java2D, en un servidor sin libfreetype
        // reventaría aquí — que es donde se quiere que reviente, y no en
        // producción cuando un cliente descarga el reporte.
        var familias = List.of(
                new com.cloud_technological.aura_pos.repositories.kardex
                        .KardexQueryRepository.MovimientoPorFamilia(
                        "VENTAS", BigDecimal.ZERO, new BigDecimal("120"),
                        BigDecimal.ZERO, new BigDecimal("1200000"), 40),
                new com.cloud_technological.aura_pos.repositories.kardex
                        .KardexQueryRepository.MovimientoPorFamilia(
                        "MERMAS", BigDecimal.ZERO, new BigDecimal("15"),
                        BigDecimal.ZERO, new BigDecimal("150000"), 3),
                new com.cloud_technological.aura_pos.repositories.kardex
                        .KardexQueryRepository.MovimientoPorFamilia(
                        "COMPRAS", new BigDecimal("200"), BigDecimal.ZERO,
                        new BigDecimal("1800000"), BigDecimal.ZERO, 12));
        // doReturn y no when(...): `when` LLAMA al método, y el stub del setUp
        // está puesto para lanzar — se dispararía al configurarlo.
        org.mockito.Mockito.doReturn(familias).when(kardexRepo)
                .movimientoPorFamilia(anyInt(), any(), any());
        when(auditoriaService.auditar(any())).thenReturn(auditoria(List.of(), 0, BigDecimal.ZERO));

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        assertTrue(t.contains("Por dónde salió la mercancía"));
        assertTrue(t.contains("Mermas"));
        // La merma como porcentaje de lo que salió: en valor absoluto nadie
        // sabe si es mucho o poco.
        assertTrue(t.contains("de todo lo que salió del inventario"));
    }

    @Test
    @DisplayName("Los descuadres heredados salen marcados y aparte")
    void losHeredadosSalenAparte() throws Exception {
        HallazgoDto heredado = hallazgo();
        heredado.setCodigo("FACTURA_SIN_FECHA");
        heredado.setTitulo("Facturas sin fecha de emisión");
        heredado.setHeredado(Boolean.TRUE);

        AuditoriaResultadoDto a = auditoria(List.of(), 1, BigDecimal.ZERO);
        a.setHeredados(List.of(heredado));
        when(auditoriaService.auditar(any())).thenReturn(a);

        String t = texto(service.generarPdf(new AuditoriaFiltroDto()));

        // Sin la separación, el cliente lee "el sistema está mal" cuando el
        // reporte dice "hay deuda técnica ya diagnosticada".
        assertTrue(t.contains("anteriores a las correcciones"));
        assertTrue(t.contains("ya están diagnosticados"));
    }
}
