package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaFiltroDto;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaResultadoDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDetalleDto;
import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDto;
import com.cloud_technological.aura_pos.dto.auditoria.Severidad;
import com.cloud_technological.aura_pos.repositories.auditoria.AuditoriaQueryRepository;
import com.cloud_technological.aura_pos.repositories.auditoria.AuditoriaQueryRepository.Resumen;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * El motor de hallazgos tiene una sola promesa: si reporta algo, ese algo
 * existe. Un hallazgo falso destruye la confianza en el reporte completo — el
 * lector deja de creerle también a los verdaderos.
 *
 * <p>Estos tests fijan las reglas que sostienen esa promesa: no se reporta lo
 * que no tiene filas, la severidad no la decide el monto, y lo heredado no se
 * mezcla con la operación del período.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditoriaServiceTest {

    private static final Integer EMPRESA_ID = 1;
    private static final Resumen NADA = new Resumen(0, BigDecimal.ZERO);

    @Mock private AuditoriaQueryRepository repository;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks private AuditoriaService service;

    @BeforeEach
    void setUp() {
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA_ID);
        // Por defecto todo limpio; cada test enciende solo el cruce que le importa.
        when(repository.cajaDiferencias(anyInt(), any(), any(), any())).thenReturn(NADA);
        when(repository.carteraDescuadre(anyInt(), any(), any(), anyBoolean(), any())).thenReturn(NADA);
        when(repository.kardexSigno(anyInt(), any(), any())).thenReturn(NADA);
        when(repository.kardexVsStock(anyInt())).thenReturn(NADA);
        when(repository.asientosDescuadrados(anyInt(), any(), any())).thenReturn(NADA);
        when(repository.cabeceraVsDetalle(anyInt(), any(), any())).thenReturn(NADA);
        when(repository.facturasSinAceptar(anyInt(), any(), any())).thenReturn(NADA);
        when(repository.gastosSinSoporte(anyInt(), any(), any())).thenReturn(NADA);
        when(repository.abonosSinArqueo(anyInt(), any(), any(), anyBoolean())).thenReturn(NADA);
        when(repository.facturasSinFecha(anyInt())).thenReturn(NADA);
    }

    private AuditoriaFiltroDto filtro() {
        AuditoriaFiltroDto f = new AuditoriaFiltroDto();
        f.setFechaDesde(LocalDate.of(2026, 9, 1));
        f.setFechaHasta(LocalDate.of(2026, 9, 30));
        return f;
    }

    private HallazgoDto buscar(AuditoriaResultadoDto r, String codigo) {
        return r.getHallazgos().stream()
                .filter(h -> codigo.equals(h.getCodigo()))
                .findFirst()
                .orElseGet(() -> r.getHeredados().stream()
                        .filter(h -> codigo.equals(h.getCodigo()))
                        .findFirst().orElse(null));
    }

    // ── La promesa: no reportar lo que no existe ────────────────────────

    @Test
    @DisplayName("Un período sin problemas no devuelve ni un hallazgo")
    void periodoLimpioNoInventaHallazgos() {
        AuditoriaResultadoDto r = service.auditar(filtro());

        // Ni un hallazgo con cantidad 0 "para que se vea que se revisó": eso
        // llena el reporte de ruido y entierra los que sí importan.
        assertTrue(r.getHallazgos().isEmpty());
        assertTrue(r.getHeredados().isEmpty());
        assertEquals(0, r.getAlta());
        assertTrue(r.estaLimpio());
    }

    @Test
    @DisplayName("El detalle solo se consulta si el cruce encontró algo")
    void noSePideElDetalleDeUnHallazgoVacio() {
        AuditoriaFiltroDto f = filtro();
        f.setIncluirDetalle(Boolean.TRUE);

        service.auditar(f);

        // Preguntar por las filas de un hallazgo vacío es un viaje a la base
        // que siempre devuelve nada. Con diez cruces son diez viajes inútiles.
        verify(repository, never()).cajaDiferenciasDetalle(anyInt(), any(), any(), any(), anyInt());
        verify(repository, never()).asientosDescuadradosDetalle(anyInt(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Si el cruce encuentra algo, el hallazgo trae su monto y su detalle")
    void hallazgoConDatosTraeMontoYDetalle() {
        when(repository.cajaDiferencias(anyInt(), any(), any(), any()))
                .thenReturn(new Resumen(3, new BigDecimal("150000")));
        HallazgoDetalleDto fila = new HallazgoDetalleDto();
        fila.setReferencia("Turno #88");
        fila.setMonto(new BigDecimal("120000"));
        when(repository.cajaDiferenciasDetalle(anyInt(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(fila));

        AuditoriaFiltroDto f = filtro();
        f.setIncluirDetalle(Boolean.TRUE);
        AuditoriaResultadoDto r = service.auditar(f);

        HallazgoDto h = buscar(r, "CAJA_DIFERENCIA");
        assertEquals(3, h.getCantidad());
        // Sin monto el hallazgo no mueve a nadie: "3 arqueos con problemas" no
        // es lo mismo que "$150.000 que no se pueden explicar".
        assertEquals(new BigDecimal("150000"), h.getMonto());
        assertEquals(1, h.getDetalle().size());
        assertEquals("Turno #88", h.getDetalle().get(0).getReferencia());
    }

    @Test
    @DisplayName("Cada hallazgo dice qué se cruzó contra qué")
    void cadaHallazgoCitaSuFuente() {
        when(repository.asientosDescuadrados(anyInt(), any(), any()))
                .thenReturn(new Resumen(1, new BigDecimal("1000")));

        HallazgoDto h = buscar(service.auditar(filtro()), "ASIENTO_DESCUADRADO");

        // Un reporte de auditoría que no puede señalar de dónde sale un número
        // no se puede defender frente a quien lo cuestione.
        assertFalse(h.getCruce().isBlank());
        assertFalse(h.getRecomendacion().isBlank());
    }

    // ── La severidad no la decide el monto ─────────────────────────────

    @Test
    @DisplayName("Un asiento descuadrado de $1.000 es ALTA; un gasto sin soporte de $5.000.000 es MEDIA")
    void laSeveridadNoDependeDelMonto() {
        when(repository.asientosDescuadrados(anyInt(), any(), any()))
                .thenReturn(new Resumen(1, new BigDecimal("1000")));
        when(repository.gastosSinSoporte(anyInt(), any(), any()))
                .thenReturn(new Resumen(1, new BigDecimal("5000000")));

        AuditoriaResultadoDto r = service.auditar(filtro());

        // La contabilidad que no cierra es grave aunque sean mil pesos; un gasto
        // sin papel es serio pero la plata está y se sabe dónde.
        assertEquals(Severidad.ALTA, buscar(r, "ASIENTO_DESCUADRADO").getSeveridad());
        assertEquals(Severidad.MEDIA, buscar(r, "GASTO_DEDUCIBLE_SIN_SOPORTE").getSeveridad());

        // Y el monto en riesgo suma solo los ALTA: mezclar severidades daría
        // una cifra que no significa nada.
        assertEquals(0, new BigDecimal("1000").compareTo(r.getMontoEnRiesgo()));
    }

    // ── Lo heredado va aparte ──────────────────────────────────────────

    @Test
    @DisplayName("Los descuadres heredados no se mezclan con los del período")
    void losHeredadosVanEnSuPropiaLista() {
        when(repository.facturasSinFecha(anyInt()))
                .thenReturn(new Resumen(40, new BigDecimal("9000000")));
        when(repository.cajaDiferencias(anyInt(), any(), any(), any()))
                .thenReturn(new Resumen(1, new BigDecimal("5000")));

        AuditoriaResultadoDto r = service.auditar(filtro());

        // Si se mezclaran, el primer reporte parecería decir "el sistema está
        // mal" cuando dice "hay deuda técnica ya diagnosticada".
        assertEquals(1, r.getHallazgos().size());
        assertEquals("CAJA_DIFERENCIA", r.getHallazgos().get(0).getCodigo());
        assertEquals(1, r.getHeredados().size());
        assertTrue(r.getHeredados().get(0).getHeredado());

        // Pero sí cuentan en el semáforo: son problemas reales.
        assertEquals(2, r.getAlta());
    }

    @Test
    @DisplayName("Se pueden apagar los heredados sin tocar el resto")
    void losHeredadosSePuedenApagar() {
        when(repository.facturasSinFecha(anyInt()))
                .thenReturn(new Resumen(40, new BigDecimal("9000000")));
        AuditoriaFiltroDto f = filtro();
        f.setIncluirHeredados(Boolean.FALSE);

        AuditoriaResultadoDto r = service.auditar(f);

        assertTrue(r.getHeredados().isEmpty());
        assertEquals(0, r.getAlta());
    }

    // ── Límites ────────────────────────────────────────────────────────

    // ── Lo que enseñó la primera corrida real ──────────────────────────

    @Test
    @DisplayName("Un descuadre de centavos no se reporta: es redondeo, no un problema")
    void losCentavosNoSonHallazgo() {
        AuditoriaFiltroDto f = filtro();

        service.auditar(f);

        // La primera corrida real trajo un arqueo con 22 centavos de sobrante
        // junto a uno de $954.000, los dos en ALTA. El de centavos le quita
        // fuerza al que importa, así que el umbral lo filtra en el SQL.
        org.mockito.ArgumentCaptor<BigDecimal> umbral =
                org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        verify(repository).cajaDiferencias(anyInt(), any(), any(), umbral.capture());
        assertTrue(umbral.getValue().compareTo(BigDecimal.ONE) > 0,
                "el umbral por defecto tiene que filtrar los centavos");
    }

    @Test
    @DisplayName("El umbral no aplica a los asientos: la partida doble cuadra al centavo")
    void elUmbralNoTocaLaContabilidad() {
        service.auditar(filtro());

        // Un asiento descuadrado por $0,50 sigue siendo un asiento que no
        // cierra. El umbral es para el redondeo de conteo, no para la
        // contabilidad.
        verify(repository).asientosDescuadrados(anyInt(), any(), any());
    }

    @Test
    @DisplayName("Un descuadre de inventario sin costo cargado reporta las unidades")
    void elStockSinCostoReportaUnidades() {
        // Caso real: 10 unidades sin explicar valorizadas en $0 porque al
        // producto le falta el costo. Un hallazgo que dice "$0" se lee como
        // "no pasa nada".
        when(repository.kardexVsStock(anyInt()))
                .thenReturn(new Resumen(1, BigDecimal.ZERO, new BigDecimal("10.00")));

        HallazgoDto h = buscar(service.auditar(filtro()), "STOCK_SIN_HISTORIA");

        assertEquals("10 unidades", h.getMagnitud());
    }

    @Test
    @DisplayName("Un rango invertido se rechaza")
    void rangoInvertidoSeRechaza() {
        AuditoriaFiltroDto f = filtro();
        f.setFechaDesde(LocalDate.of(2026, 9, 30));
        f.setFechaHasta(LocalDate.of(2026, 9, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.auditar(f));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Un rango de más de un trimestre se rechaza en vez de colgar la consulta")
    void rangoDemasiadoLargoSeRechaza() {
        AuditoriaFiltroDto f = filtro();
        f.setFechaDesde(LocalDate.of(2026, 1, 1));
        f.setFechaHasta(LocalDate.of(2026, 12, 31));

        // Los cruces recorren las tablas más grandes del sistema. "Toda la
        // historia" es una consulta que nadie va a esperar; mejor decirlo.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.auditar(f));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("trimestre"));
    }

    @Test
    @DisplayName("Sin fechas se audita el mes corriente")
    void sinFechasSeAuditaElMesCorriente() {
        AuditoriaResultadoDto r = service.auditar(new AuditoriaFiltroDto());

        assertEquals(LocalDate.now().withDayOfMonth(1), r.getFechaDesde());
        assertEquals(LocalDate.now(), r.getFechaHasta());
    }

    @Test
    @DisplayName("Se mide cuánto tardó: es el dato que decide si hace falta generarlo aparte")
    void seMideLaDuracion() {
        AuditoriaResultadoDto r = service.auditar(filtro());
        assertTrue(r.getDuracionMs() != null && r.getDuracionMs() >= 0);
    }
}
