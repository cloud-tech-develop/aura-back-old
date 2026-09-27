package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionesQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionesQueryRepository.CuentaImpuesto;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionesQueryRepository.Movimiento;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionesQueryRepository.PorTarifa;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.DeclaracionesServiceImpl;

@ExtendWith(MockitoExtension.class)
class DeclaracionesServiceTest {

    private static final Integer EMP = 1;
    private static final LocalDate DESDE = LocalDate.of(2026, 7, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 8, 31);
    private static final long GEN = 801L;   // 240801
    private static final long DESC = 802L;  // 240802
    private static final long PADRE = 800L; // 2408 (movimientos anteriores a E5)

    @Mock private DeclaracionesQueryRepository repo;
    @Mock private ConfiguracionContableService config;
    @Mock private EmpresaJPARepository empresaRepo;
    private DeclaracionesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeclaracionesServiceImpl(repo, config, empresaRepo);
        lenient().when(empresaRepo.findById(EMP)).thenReturn(Optional.empty());
        lenient().when(config.resolverCuenta(eq(EMP), any(ConceptoContable.class))).thenAnswer(inv -> {
            PlanCuentaEntity c = new PlanCuentaEntity();
            c.setId(inv.getArgument(1) == ConceptoContable.IVA_GENERADO ? GEN : DESC);
            return c;
        });
    }

    private static Movimiento mov(long cuenta, String codigo, String origen, String deb, String cred) {
        return new Movimiento(cuenta, codigo, "cta " + codigo, origen, new BigDecimal(deb), new BigDecimal(cred));
    }

    private static BigDecimal total(BorradorDeclaracionDto d, String titulo) {
        return d.getSecciones().stream().filter(s -> s.getTitulo().startsWith(titulo))
                .findFirst().orElseThrow().getTotal();
    }

    @Test
    void ivaSeparaGeneradoDescontableYRetencionesYDaElSaldo() {
        when(repo.cuentasImpuesto(EMP)).thenReturn(List.of(new CuentaImpuesto("IVA", GEN, DESC)));
        when(repo.movimientos(EMP, DESDE, HASTA, List.of("2408"))).thenReturn(List.of(
                mov(GEN, "240801", "VENTA", "0", "1900000"),
                mov(GEN, "240801", "NOTA_CREDITO", "190000", "0"),
                mov(DESC, "240802", "COMPRA", "600000", "0"),
                mov(DESC, "240802", "GASTO", "95000", "0"),
                // Anteriores a E5: la 2408 misma, clasificada por su documento
                mov(PADRE, "2408", "COMPRA", "5000", "0"),
                mov(PADRE, "2408", "VENTA", "0", "10000")));
        when(repo.movimientos(EMP, DESDE, HASTA, List.of("135517"))).thenReturn(List.of(
                mov(1, "135517", "ABONO_COBRAR", "26180", "0")));
        when(repo.movimientos(EMP, DESDE, HASTA, List.of("41", "42"))).thenReturn(List.of());
        when(repo.ventasPorTarifa(EMP, DESDE, HASTA)).thenReturn(List.of(
                new PorTarifa(new BigDecimal("19"), new BigDecimal("10052631"), new BigDecimal("1910000"), 40)));

        BorradorDeclaracionDto d = service.iva(EMP, DESDE, HASTA);

        // 1.900.000 + 10.000 − 190.000
        assertEquals(0, new BigDecimal("1720000").compareTo(total(d, "IVA generado")));
        // 600.000 + 95.000 + 5.000
        assertEquals(0, new BigDecimal("700000").compareTo(total(d, "IVA descontable")));
        assertEquals(0, new BigDecimal("26180").compareTo(total(d, "Retenciones de IVA")));
        assertEquals(0, new BigDecimal("993820").compareTo(d.getResultado()));
        assertEquals("Saldo a pagar", d.getResultadoEtiqueta());
        // IVA de ventas en documentos = 1.910.000 = contabilizado (1.900.000 + 10.000): sin advertencia
        assertTrue(d.getAdvertencias().stream().noneMatch(a -> a.startsWith("El IVA de las ventas")));
    }

    @Test
    void incEnLaMismaCuentaDelIvaSeAdvierte() {
        when(repo.cuentasImpuesto(EMP)).thenReturn(List.of(
                new CuentaImpuesto("IVA", GEN, DESC), new CuentaImpuesto("INC", GEN, DESC)));
        when(repo.movimientos(eq(EMP), eq(DESDE), eq(HASTA), any())).thenReturn(List.of());
        when(repo.ventasPorTarifa(EMP, DESDE, HASTA)).thenReturn(List.of());

        BorradorDeclaracionDto d = service.iva(EMP, DESDE, HASTA);

        assertTrue(d.getAdvertencias().stream().anyMatch(a -> a.startsWith("El INC y el IVA")));
    }

    @Test
    void retencionSumaRentaEIvaYDejaElIcaAparte() {
        when(repo.movimientos(EMP, DESDE, HASTA, List.of("2365", "2367", "2368"))).thenReturn(List.of(
                mov(1, "2365", "COMPRA", "0", "250000"),
                mov(1, "2365", "ANULACION_COMPRA", "10000", "0"),
                mov(2, "2367", "COMPRA", "0", "57000"),
                mov(3, "2368", "COMPRA", "0", "9660")));
        when(repo.retencionCompras(EMP, DESDE, HASTA)).thenReturn(List.of(
                new PorTarifa(new BigDecimal("2.5"), new BigDecimal("10000000"), new BigDecimal("250000"), 3)));
        when(repo.retencionGastos(EMP, DESDE, HASTA)).thenReturn(List.of());

        BorradorDeclaracionDto d = service.retencion(EMP, DESDE, HASTA);

        assertEquals(0, new BigDecimal("240000").compareTo(total(d, "Retención en la fuente")));
        assertEquals(0, new BigDecimal("57000").compareTo(total(d, "Retención a título de IVA")));
        // El ICA se informa pero no entra al total del 350
        assertEquals(0, new BigDecimal("297000").compareTo(d.getResultado()));
    }
}
