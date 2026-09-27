package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroAuxiliarDto;
import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroDiarioDto;
import com.cloud_technological.aura_pos.repositories.contabilidad.LibrosContablesQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.LibrosContablesQueryRepository.Partida;
import com.cloud_technological.aura_pos.repositories.contabilidad.LibrosContablesQueryRepository.SaldoAnterior;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.LibrosContablesServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;

@ExtendWith(MockitoExtension.class)
class LibrosContablesServiceTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 30);

    @Mock private LibrosContablesQueryRepository queryRepo;
    @Mock private EmpresaJPARepository empresaRepo;
    private LibrosContablesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LibrosContablesServiceImpl(queryRepo, empresaRepo);
    }

    private static Partida partida(long asiento, String codigo, String naturaleza, Long tercero,
            String nombre, String debito, String credito) {
        return new Partida(Long.parseLong(codigo), codigo, "Cuenta " + codigo, naturaleza,
                tercero, tercero != null ? "900" + tercero : null, nombre,
                asiento, LocalDate.of(2026, 9, 10), "CO-" + asiento, "COMPRA",
                "Compra " + asiento, "linea", new BigDecimal(debito), new BigDecimal(credito));
    }

    @Test
    void proveedorSaleConSaldoPositivoEnCuentaDeNaturalezaCredito() {
        // Se le debían 100 al proveedor 7; en septiembre se le compra 50 y se le paga 30.
        when(queryRepo.saldosAnteriores(EMPRESA, DESDE, "22", "22", null)).thenReturn(List.of(
                new SaldoAnterior(2205L, "2205", "Proveedores", "CREDITO",
                        7L, "9007", "Distribuidora", new BigDecimal("-100"))));
        when(queryRepo.partidas(EMPRESA, DESDE, HASTA, "22", "22", null)).thenReturn(List.of(
                partida(1, "2205", "CREDITO", 7L, "Distribuidora", "0", "50"),
                partida(2, "2205", "CREDITO", 7L, "Distribuidora", "30", "0")));

        LibroAuxiliarDto aux = service.auxiliarPorTercero(EMPRESA, DESDE, HASTA, "22", "22", null);

        LibroAuxiliarDto.Tercero t = aux.getCuentas().get(0).getTerceros().get(0);
        assertEquals(0, new BigDecimal("100").compareTo(t.getSaldoAnterior()));
        assertEquals(0, new BigDecimal("150").compareTo(t.getLineas().get(0).getSaldo()));
        assertEquals(0, new BigDecimal("120").compareTo(t.getLineas().get(1).getSaldo()));
        assertEquals(0, new BigDecimal("120").compareTo(t.getSaldoFinal()));
        assertEquals(0, new BigDecimal("120").compareTo(aux.getCuentas().get(0).getSaldoFinal()));
    }

    @Test
    void terceroSinMovimientoEnElRangoAparecePorSuSaldoAnterior() {
        when(queryRepo.saldosAnteriores(EMPRESA, DESDE, null, null, null)).thenReturn(List.of(
                new SaldoAnterior(1305L, "1305", "Clientes", "DEBITO",
                        3L, "93", "Cliente viejo", new BigDecimal("80"))));
        when(queryRepo.partidas(EMPRESA, DESDE, HASTA, null, null, null)).thenReturn(List.of());

        LibroAuxiliarDto aux = service.auxiliarPorTercero(EMPRESA, DESDE, HASTA, null, null, null);

        LibroAuxiliarDto.Tercero t = aux.getCuentas().get(0).getTerceros().get(0);
        assertTrue(t.getLineas().isEmpty());
        assertEquals(0, new BigDecimal("80").compareTo(t.getSaldoFinal()));
    }

    @Test
    void cuentasEnOrdenPucYSinTerceroPrimero() {
        when(queryRepo.saldosAnteriores(EMPRESA, DESDE, null, null, null)).thenReturn(List.of());
        when(queryRepo.partidas(EMPRESA, DESDE, HASTA, null, null, null)).thenReturn(List.of(
                partida(1, "2205", "CREDITO", 7L, "Zeta", "0", "50"),
                partida(1, "110505", "DEBITO", null, null, "50", "0"),
                partida(2, "2205", "CREDITO", 8L, "Alfa", "0", "20"),
                partida(2, "2205", "CREDITO", null, null, "0", "5")));

        LibroAuxiliarDto aux = service.auxiliarPorTercero(EMPRESA, DESDE, HASTA, null, null, null);

        assertEquals("110505", aux.getCuentas().get(0).getCodigo());
        List<LibroAuxiliarDto.Tercero> proveedores = aux.getCuentas().get(1).getTerceros();
        assertEquals("Sin tercero", proveedores.get(0).getNombre());
        assertEquals("Alfa", proveedores.get(1).getNombre());
        assertEquals("Zeta", proveedores.get(2).getNombre());
    }

    @Test
    void diarioAgrupaPorComprobanteYDiceSiCuadra() {
        when(queryRepo.partidas(EMPRESA, DESDE, HASTA, null, null, null)).thenReturn(List.of(
                partida(1, "1435", "DEBITO", null, null, "100", "0"),
                partida(1, "2205", "CREDITO", 7L, "Prov", "0", "100"),
                partida(2, "5195", "DEBITO", null, null, "10", "0"),
                partida(2, "110510", "DEBITO", null, null, "0", "10")));

        LibroDiarioDto diario = service.diario(EMPRESA, DESDE, HASTA);

        assertEquals(2, diario.getComprobantes().size());
        assertEquals(2, diario.getComprobantes().get(0).getLineas().size());
        assertEquals(0, new BigDecimal("110").compareTo(diario.getTotalDebito()));
        assertTrue(diario.isCuadrado());
    }

    @Test
    void diarioDescuadradoSeMarca() {
        when(queryRepo.partidas(EMPRESA, DESDE, HASTA, null, null, null)).thenReturn(List.of(
                partida(1, "1435", "DEBITO", null, null, "100", "0")));

        assertFalse(service.diario(EMPRESA, DESDE, HASTA).isCuadrado());
    }

    @Test
    void rangoInvertidoSeRechaza() {
        assertThrows(GlobalException.class,
                () -> service.diario(EMPRESA, HASTA, DESDE));
    }
}
