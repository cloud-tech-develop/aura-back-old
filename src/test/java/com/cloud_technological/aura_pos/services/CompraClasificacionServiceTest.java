package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto.CompraProducto;
import com.cloud_technological.aura_pos.entity.ActivoFijoEntity;
import com.cloud_technological.aura_pos.entity.CompraDetalleEntity;
import com.cloud_technological.aura_pos.entity.CompraEntity;
import com.cloud_technological.aura_pos.entity.DiferidoEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoQueryRepository;
import com.cloud_technological.aura_pos.utils.ClasificacionItem;
import com.cloud_technological.aura_pos.utils.GlobalException;

class CompraClasificacionServiceTest {

    private static final Integer EMPRESA = 1;

    private ResolucionCuentaProducto resolucion;
    private ActivoFijoJPARepository activoRepo;
    private DiferidoJPARepository diferidoRepo;
    private DiferidoQueryRepository diferidoQuery;
    private CompraClasificacionService service;

    @BeforeEach
    void setUp() {
        resolucion = mock(ResolucionCuentaProducto.class);
        activoRepo = mock(ActivoFijoJPARepository.class);
        diferidoRepo = mock(DiferidoJPARepository.class);
        diferidoQuery = mock(DiferidoQueryRepository.class);
        service = new CompraClasificacionService(resolucion, activoRepo, diferidoRepo, diferidoQuery);
        when(diferidoQuery.siguienteConsecutivoActivo(EMPRESA)).thenReturn(7L);
    }

    private static CompraEntity compra() {
        CompraEntity c = new CompraEntity();
        c.setId(40L);
        c.setFecha(LocalDateTime.of(2026, 9, 15, 10, 0));
        return c;
    }

    private static CompraDetalleEntity linea(long id, ClasificacionItem c, String cantidad, String neto) {
        ProductoEntity p = new ProductoEntity();
        p.setId(100L + id);
        p.setNombre("Item " + id);
        p.setClasificacion(c.name());
        CompraDetalleEntity d = new CompraDetalleEntity();
        d.setId(id);
        d.setProducto(p);
        d.setClasificacion(c.name());
        d.setCantidad(new BigDecimal(cantidad));
        d.setSubtotalLinea(new BigDecimal(neto));
        return d;
    }

    private void cuentas(ClasificacionItem c, Integer vida, Integer meses) {
        when(resolucion.resolverCompra(anyLong(), eq(EMPRESA)))
                .thenReturn(new CompraProducto(c, 1528L, 1592L, 5160L, vida, meses, 5195L));
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), () -> "esperado " + esperado + " y fue " + real);
    }

    @Test
    void activoConCantidadTresCreaTresFichasQueSumanElValorDeLaLinea() {
        cuentas(ClasificacionItem.ACTIVO_FIJO, 36, null);

        service.crearDesdeCompra(compra(), List.of(linea(1, ClasificacionItem.ACTIVO_FIJO, "3", "1000")),
                BigDecimal.ZERO, new BigDecimal("1000"), EMPRESA);

        ArgumentCaptor<ActivoFijoEntity> captor = ArgumentCaptor.forClass(ActivoFijoEntity.class);
        verify(activoRepo, org.mockito.Mockito.times(3)).save(captor.capture());
        List<ActivoFijoEntity> fichas = captor.getAllValues();
        igual("333.33", fichas.get(0).getValorCompra());
        igual("333.33", fichas.get(1).getValorCompra());
        igual("333.34", fichas.get(2).getValorCompra());
        assertEquals("AF-000007", fichas.get(0).getCodigo());
        assertEquals("AF-000009", fichas.get(2).getCodigo());
        assertEquals(36, fichas.get(0).getVidaUtilMeses());
        assertEquals(1528L, fichas.get(0).getCuentaActivoId());
        assertEquals(40L, fichas.get(0).getCompraId());
    }

    @Test
    void losFletesSeRepartenPorElNetoDeCadaLinea() {
        cuentas(ClasificacionItem.ACTIVO_FIJO, null, null);
        // Factura de $3.000: $1.000 de un activo y $2.000 de mercancía; flete $300.
        List<CompraDetalleEntity> lineas = List.of(
                linea(1, ClasificacionItem.ACTIVO_FIJO, "1", "1000"),
                linea(2, ClasificacionItem.PRODUCTO, "10", "2000"));

        service.crearDesdeCompra(compra(), lineas, new BigDecimal("300"), new BigDecimal("3000"), EMPRESA);

        ArgumentCaptor<ActivoFijoEntity> captor = ArgumentCaptor.forClass(ActivoFijoEntity.class);
        verify(activoRepo).save(captor.capture());
        igual("1100", captor.getValue().getValorCompra());
        // Sin vida útil en la categoría: 60 meses.
        assertEquals(60, captor.getValue().getVidaUtilMeses());
    }

    @Test
    void diferidoCreaUnRegistroConLaCuentaQueDebitoLaCompra() {
        cuentas(ClasificacionItem.DIFERIDO, null, 6);

        service.crearDesdeCompra(compra(), List.of(linea(1, ClasificacionItem.DIFERIDO, "1", "1200")),
                BigDecimal.ZERO, new BigDecimal("1200"), EMPRESA);

        ArgumentCaptor<DiferidoEntity> captor = ArgumentCaptor.forClass(DiferidoEntity.class);
        verify(diferidoRepo).save(captor.capture());
        DiferidoEntity d = captor.getValue();
        igual("1200", d.getMonto());
        assertEquals(6, d.getMeses());
        assertEquals(1528L, d.getCuentaDiferidoId());
        assertEquals(5195L, d.getCuentaGastoId());
        assertEquals(DiferidoEntity.ORIGEN_COMPRA, d.getOrigenTipo());
    }

    @Test
    void mercanciaYGastoNoCreanNada() {
        service.crearDesdeCompra(compra(), List.of(
                linea(1, ClasificacionItem.PRODUCTO, "5", "500"),
                linea(2, ClasificacionItem.GASTO, "1", "80")),
                BigDecimal.ZERO, new BigDecimal("580"), EMPRESA);

        verify(activoRepo, never()).save(any());
        verify(diferidoRepo, never()).save(any());
    }

    @Test
    void cantidadFraccionariaEsUnSoloActivo() {
        cuentas(ClasificacionItem.ACTIVO_FIJO, null, null);

        service.crearDesdeCompra(compra(), List.of(linea(1, ClasificacionItem.ACTIVO_FIJO, "3.5", "700")),
                BigDecimal.ZERO, new BigDecimal("700"), EMPRESA);

        verify(activoRepo, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void noSeRevierteSiUnActivoYaSeDeprecio() {
        when(diferidoQuery.activosDeCompraConMovimiento(EMPRESA, 40L)).thenReturn(1L);

        GlobalException e = assertThrows(GlobalException.class,
                () -> service.revertirDeCompra(40L, EMPRESA, "anular la compra"));
        assertTrue(e.getMessage().contains("ya se depreció"));
    }

    @Test
    void noSeRevierteSiUnDiferidoYaAmortizo() {
        when(diferidoQuery.cuotasDeOrigen(EMPRESA, DiferidoEntity.ORIGEN_COMPRA, 40L)).thenReturn(2L);

        assertThrows(GlobalException.class, () -> service.revertirDeCompra(40L, EMPRESA, "editar la compra"));
    }

    @Test
    void revertirEliminaFichasYAnulaDiferidos() {
        ActivoFijoEntity a = new ActivoFijoEntity();
        a.setId(9L);
        DiferidoEntity d = DiferidoEntity.builder().id(3L).build();
        when(diferidoQuery.activosDeCompra(EMPRESA, 40L)).thenReturn(new ArrayList<>(List.of(9L)));
        when(diferidoQuery.deOrigen(EMPRESA, DiferidoEntity.ORIGEN_COMPRA, 40L)).thenReturn(List.of(3L));
        when(activoRepo.findById(9L)).thenReturn(java.util.Optional.of(a));
        when(diferidoRepo.findById(3L)).thenReturn(java.util.Optional.of(d));

        service.revertirDeCompra(40L, EMPRESA, "anular la compra");

        assertTrue(a.getDeletedAt() != null);
        assertEquals(DiferidoEntity.ANULADO, d.getEstado());
    }
}
