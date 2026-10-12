package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto.PorPagar;
import com.cloud_technological.aura_pos.repositories.contabilidad.AcreedorImpuestoQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AcreedorImpuestoQueryRepository.CuentaAcreedor;
import com.cloud_technological.aura_pos.repositories.contabilidad.AcreedorImpuestoQueryRepository.Tercero;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoQueryRepository.SaldoCuenta;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;

/** A quién se le paga cada declaración (V204). */
@ExtendWith(MockitoExtension.class)
class DeclaracionPagoServiceTest {

    private static final Integer EMP = 1;
    private static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 30);
    private static final long DIAN = 100, MONTERIA = 200, CERETE = 300;

    @Mock private DeclaracionPagoQueryRepository query;
    @Mock private DeclaracionPagoJPARepository pagoRepo;
    @Mock private DeclaracionPagoCuentaJPARepository pagoCuentaRepo;
    @Mock private AcreedorImpuestoQueryRepository acreedores;
    @Mock private PlanCuentaJPARepository planRepo;
    @Mock private CuentaBancariaJPARepository cuentaBancariaRepo;
    @Mock private PeriodoContableResolver periodoGuard;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private DeclaracionPagoService service;

    @BeforeEach
    void setUp() {
        service = new DeclaracionPagoService(query, pagoRepo, pagoCuentaRepo, acreedores, planRepo,
                cuentaBancariaRepo, periodoGuard, eventPublisher);
        lenient().when(acreedores.dian(EMP)).thenReturn(Optional.of(DIAN));
        lenient().when(acreedores.tercero(anyLong(), eq(EMP)))
                .thenAnswer(inv -> Optional.of(new Tercero(inv.getArgument(0), "T" + inv.getArgument(0), "1")));
        lenient().when(query.pagosVigentes(EMP, DESDE, HASTA)).thenReturn(List.of());
    }

    private static SaldoCuenta saldo(long id, String codigo, String valor) {
        return new SaldoCuenta(id, codigo, "cta " + codigo, new BigDecimal(valor));
    }

    @Test
    void ivaSeLePagaALaDianPorElNetoDeGeneradoDescontableYRetenciones() {
        when(acreedores.acreedoresIva(EMP)).thenReturn(List.of(DIAN));
        when(query.saldos(EMP, DESDE, HASTA, List.of("2408", "135517"))).thenReturn(List.of(
                saldo(1, "240801", "1900000"), saldo(2, "240802", "-600000"), saldo(3, "135517", "-50000")));

        List<PorPagar> pp = service.porPagar("IVA", EMP, DESDE, HASTA, new ArrayList<>());

        assertEquals(1, pp.size());
        assertEquals(DIAN, pp.get(0).getTerceroId());
        assertEquals(0, new BigDecimal("1250000").compareTo(pp.get(0).getValor()));
        assertEquals(3, pp.get(0).getCuentas().size());
    }

    @Test
    void reteicaSeParteEntreMunicipiosPorLaCuentaDeCadaTarifa() {
        when(acreedores.cuentasRetencion(EMP)).thenReturn(List.of(
                new CuentaAcreedor("RETEFUENTE", 10L, DIAN),
                new CuentaAcreedor("RETEICA", 20L, MONTERIA),
                new CuentaAcreedor("RETEICA", 30L, CERETE)));
        when(acreedores.acreedoresReteica(EMP)).thenReturn(List.of(MONTERIA, CERETE));
        when(query.saldos(EMP, DESDE, HASTA, List.of("2365", "2367"))).thenReturn(List.of(
                saldo(10, "23651502", "110000"), saldo(11, "23670501", "28500")));
        when(query.saldos(EMP, DESDE, HASTA, List.of("2368"))).thenReturn(List.of(
                saldo(20, "23680501", "9660"), saldo(30, "23680502", "4000")));

        List<PorPagar> pp = service.porPagar("RETENCION", EMP, DESDE, HASTA, new ArrayList<>());

        // La retefuente y el reteIVA (sin tarifa propia) van juntos a la DIAN.
        PorPagar dian = pp.stream().filter(g -> g.getTipo().equals("RETENCION")).findFirst().orElseThrow();
        assertEquals(DIAN, dian.getTerceroId());
        assertEquals(0, new BigDecimal("138500").compareTo(dian.getValor()));
        // Cada municipio por su cuenta.
        assertEquals(0, new BigDecimal("9660").compareTo(ica(pp, MONTERIA).getValor()));
        assertEquals(0, new BigDecimal("4000").compareTo(ica(pp, CERETE).getValor()));
    }

    @Test
    void unaCuentaDeIcaCompartidaPorDosMunicipiosQuedaSinAcreedor() {
        when(acreedores.cuentasRetencion(EMP)).thenReturn(List.of(
                new CuentaAcreedor("RETEICA", 20L, MONTERIA),
                new CuentaAcreedor("RETEICA", 20L, CERETE)));
        when(acreedores.acreedoresReteica(EMP)).thenReturn(List.of(MONTERIA, CERETE));
        when(query.saldos(EMP, DESDE, HASTA, List.of("2365", "2367"))).thenReturn(List.of());
        when(query.saldos(EMP, DESDE, HASTA, List.of("2368"))).thenReturn(List.of(saldo(20, "23680501", "9660")));
        List<String> adv = new ArrayList<>();

        List<PorPagar> pp = service.porPagar("RETENCION", EMP, DESDE, HASTA, adv);

        assertEquals(1, pp.size());
        assertNull(pp.get(0).getTerceroId());
        assertTrue(adv.stream().anyMatch(a -> a.contains("23680501")));
    }

    private static PorPagar ica(List<PorPagar> pp, long municipio) {
        return pp.stream().filter(g -> g.getTipo().equals("RETEICA") && g.getTerceroId() == municipio)
                .findFirst().orElseThrow();
    }
}
