package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaCalculadora.Linea;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaCalculadora.Mapeo;
import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;

/**
 * Aritmética de la exógena (E11): asignación al mapeo más específico,
 * signo por tipo de valor, corte correcto (movimiento del año vs saldo a
 * dic 31) y agrupación de cuantías menores en el tercero 222222222.
 */
class ExogenaCalculadoraTest {

    private static final BigDecimal UMBRAL = new BigDecimal("100000");

    private static final Mapeo SALARIOS = new Mapeo(5001L, "5105", null, "MOVIMIENTO_DB");
    private static final Mapeo OTROS_GASTOS = new Mapeo(5016L, "51", null, "MOVIMIENTO_DB");
    private static final Mapeo INGRESOS = new Mapeo(4001L, "41", null, "MOVIMIENTO_CR");
    private static final Mapeo SALDO_CARTERA = new Mapeo(1315L, "1305", null, "SALDO_DB");

    private static CuentaTerceroMovimientoDto mov(String codigo, Long tercero,
            String deb, String cred) {
        return new CuentaTerceroMovimientoDto(codigo, tercero,
                new BigDecimal(deb), new BigDecimal(cred));
    }

    @Test
    void elPrefijoMasEspecificoGana() {
        Optional<Mapeo> m = ExogenaCalculadora.mapeoPara("510506",
                List.of(OTROS_GASTOS, SALARIOS));
        assertTrue(m.isPresent());
        assertEquals(5001L, m.get().conceptoId(), "5105 le gana a 51");

        assertEquals(5016L, ExogenaCalculadora.mapeoPara("519530",
                List.of(OTROS_GASTOS, SALARIOS)).get().conceptoId());
    }

    @Test
    void rangoConHastaIncluyeSubcuentasDelLimite() {
        Mapeo rango = new Mapeo(9L, "5135", "5140", "MOVIMIENTO_DB");
        assertTrue(ExogenaCalculadora.coincide("5135", rango));
        assertTrue(ExogenaCalculadora.coincide("513805", rango));
        assertTrue(ExogenaCalculadora.coincide("514005", rango), "subcuenta del tope entra");
        assertFalse(ExogenaCalculadora.coincide("5145", rango));
        assertFalse(ExogenaCalculadora.coincide("5105", rango));
    }

    @Test
    void movimientoCreditoInvierteElSigno() {
        List<Linea> lineas = ExogenaCalculadora.calcular(
                List.of(mov("413501", 10L, "50000", "1250000")),
                List.of(), List.of(INGRESOS), UMBRAL);

        assertEquals(1, lineas.size());
        assertTrue(new BigDecimal("1200000").compareTo(lineas.get(0).valor()) == 0,
                "ingresos = créditos − débitos (devoluciones restan)");
    }

    @Test
    void losSaldosComenDelCorteYNoDelAnio() {
        // La misma cuenta llega en ambos datasets: el mapeo SALDO usa el corte.
        List<Linea> lineas = ExogenaCalculadora.calcular(
                List.of(mov("130505", 10L, "400000", "0")),          // solo movimiento del año
                List.of(mov("130505", 10L, "900000", "150000")),     // saldo acumulado
                List.of(SALDO_CARTERA), UMBRAL);

        assertEquals(1, lineas.size());
        assertTrue(new BigDecimal("750000").compareTo(lineas.get(0).valor()) == 0,
                "el saldo a dic 31 es el acumulado, no el movimiento del año");
    }

    @Test
    void cuantiasMenoresYSinTerceroSeAgrupanPorConcepto() {
        List<Linea> lineas = ExogenaCalculadora.calcular(
                List.of(mov("510506", 10L, "5000000", "0"),   // supera el umbral
                        mov("510506", 20L, "80000", "0"),     // cuantía menor
                        mov("519530", null, "30000", "0"),    // sin tercero
                        mov("519530", 30L, "60000", "0")),    // cuantía menor
                List.of(), List.of(SALARIOS, OTROS_GASTOS), UMBRAL);

        Linea salarios = lineas.stream()
                .filter(l -> l.conceptoId().equals(5001L) && !l.cuantiaMenor())
                .findFirst().orElseThrow();
        assertEquals(10L, salarios.terceroId());
        assertTrue(new BigDecimal("5000000").compareTo(salarios.valor()) == 0);

        Linea menoresSalarios = lineas.stream()
                .filter(l -> l.conceptoId().equals(5001L) && l.cuantiaMenor())
                .findFirst().orElseThrow();
        assertTrue(new BigDecimal("80000").compareTo(menoresSalarios.valor()) == 0);

        Linea menoresOtros = lineas.stream()
                .filter(l -> l.conceptoId().equals(5016L) && l.cuantiaMenor())
                .findFirst().orElseThrow();
        assertTrue(new BigDecimal("90000").compareTo(menoresOtros.valor()) == 0,
                "sin tercero + cuantía menor del mismo concepto van juntos");
        assertEquals(3, lineas.size());
    }

    @Test
    void cuentasSinMapeoYValoresCeroSeOmiten() {
        List<Linea> lineas = ExogenaCalculadora.calcular(
                List.of(mov("6135", 10L, "1000000", "0"),     // sin mapeo del formato
                        mov("510506", 20L, "500000", "500000")), // neto cero
                List.of(), List.of(SALARIOS, OTROS_GASTOS), UMBRAL);

        assertTrue(lineas.isEmpty());
    }
}
