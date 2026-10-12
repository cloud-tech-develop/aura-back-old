package com.cloud_technological.aura_pos.services.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Conteo;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaConteo;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaSaldoInventario;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.SaldosInventario;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.entity.BodegaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ReconteoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ReconteoEntity;
import com.cloud_technological.aura_pos.repositories.bodegas.BodegaJPARepository;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository.BodegaFila;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.reconteo.ReconteoJPARepository;
import com.cloud_technological.aura_pos.services.BodegaService;
import com.cloud_technological.aura_pos.services.CostoPromedioService;
import com.cloud_technological.aura_pos.services.LoteStockService;
import com.cloud_technological.aura_pos.services.ReconteoService;
import com.cloud_technological.aura_pos.services.SerialStockService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Saldos iniciales y conteo físico desde Excel (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2c). */
class InventarioImportacionServiceTest {

    private static final int EMPRESA = 7;
    private static final long SEDE = 3L;

    private CatalogoImportacionQueryRepository query;
    private ProductoJPARepository productoRepo;
    private InventarioJPARepository inventarioRepo;
    private MovimientoInventarioJPARepository movimientoRepo;
    private LoteStockService loteStock;
    private SerialStockService serialStock;
    private CostoPromedioService costoPromedio;
    private ReconteoJPARepository reconteoRepo;
    private ReconteoService reconteoService;
    private InventarioImportacionService service;

    private final ProductoEntity arroz = producto(1L, "Arroz", false, false);
    private final ProductoEntity leche = producto(2L, "Leche", true, false);
    private final ProductoEntity celular = producto(3L, "Celular", false, true);
    private final ProductoEntity servicio = producto(4L, "Instalación", false, false);

    private static ProductoEntity producto(Long id, String nombre, boolean lotes, boolean serial) {
        ProductoEntity p = new ProductoEntity();
        p.setId(id);
        p.setNombre(nombre);
        p.setManejaInventario(true);
        p.setManejaLotes(lotes);
        p.setManejaSerial(serial);
        p.setClasificacion("PRODUCTO");
        p.setCosto(new BigDecimal("1000"));
        return p;
    }

    @BeforeEach
    void setUp() {
        query = mock(CatalogoImportacionQueryRepository.class);
        productoRepo = mock(ProductoJPARepository.class);
        inventarioRepo = mock(InventarioJPARepository.class);
        movimientoRepo = mock(MovimientoInventarioJPARepository.class);
        loteStock = mock(LoteStockService.class);
        serialStock = mock(SerialStockService.class);
        costoPromedio = mock(CostoPromedioService.class);
        reconteoRepo = mock(ReconteoJPARepository.class);
        reconteoService = mock(ReconteoService.class);
        BodegaJPARepository bodegaRepo = mock(BodegaJPARepository.class);
        service = new InventarioImportacionService(query, mock(BodegaService.class), bodegaRepo, productoRepo,
                inventarioRepo, movimientoRepo, loteStock, serialStock, costoPromedio, reconteoRepo, reconteoService);

        servicio.setManejaInventario(false);
        when(query.productosPorSku(EMPRESA)).thenReturn(Map.of("arroz", 1L, "leche", 2L, "cel", 3L, "inst", 4L));
        when(query.productosPorCodigoBarras(EMPRESA)).thenReturn(Map.of());
        when(query.bodegas(EMPRESA)).thenReturn(List.of(new BodegaFila(10L, SEDE, "Principal", "B01", true),
                new BodegaFila(11L, SEDE, "Trasera", "B02", false)));
        when(query.inventarios(EMPRESA)).thenReturn(Map.of("11|1",
                new BigDecimal[] { BigDecimal.valueOf(99), new BigDecimal("5") }));
        for (ProductoEntity p : List.of(arroz, leche, celular, servicio))
            when(productoRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(loteStock.manejaLotes(any())).thenAnswer(i -> Boolean.TRUE.equals(((ProductoEntity) i.getArgument(0)).getManejaLotes()));
        when(serialStock.manejaSerial(any())).thenAnswer(i -> Boolean.TRUE.equals(((ProductoEntity) i.getArgument(0)).getManejaSerial()));
        BodegaEntity b = new BodegaEntity();
        b.setId(10L);
        when(bodegaRepo.findById(10L)).thenReturn(Optional.of(b));
        when(inventarioRepo.saveAndFlush(any())).thenAnswer(i -> {
            InventarioEntity inv = i.getArgument(0);
            inv.setId(500L);
            return inv;
        });
    }

    private FilaSaldoInventario saldo(int fila, String producto, String bodega, String cantidad, String costo) {
        FilaSaldoInventario f = new FilaSaldoInventario();
        f.setFila(fila);
        f.setProducto(producto);
        f.setBodega(bodega);
        f.setCantidad(cantidad != null ? new BigDecimal(cantidad) : null);
        f.setCosto(costo != null ? new BigDecimal(costo) : null);
        return f;
    }

    private Resultado validar(FilaSaldoInventario... filas) {
        SaldosInventario req = new SaldosInventario();
        req.setFilas(List.of(filas));
        return service.validarSaldos(EMPRESA, SEDE, req);
    }

    @Test
    void sinBodegaVaALaPrincipalDeLaSede() {
        Resultado r = validar(saldo(2, "ARROZ", null, "10", "1500"));
        assertEquals(0, r.getErrores());
        assertTrue(r.getFilas().get(0).getMensaje().contains("Principal"));
        assertTrue(r.getAvisos().get(0).contains("No genera asiento"));
    }

    @Test
    void conExistenciasEnEsaBodegaNoEntra() {
        assertEquals(1, validar(saldo(2, "ARROZ", "Trasera", "10", null)).getErrores());
    }

    @Test
    void unServicioNoTieneInventario() {
        assertEquals(1, validar(saldo(2, "INST", null, "1", null)).getErrores());
    }

    @Test
    void loteEnUnProductoSinLotesEsError() {
        FilaSaldoInventario f = saldo(2, "ARROZ", null, "10", null);
        f.setLote("L1");
        assertEquals(1, validar(f).getErrores());
    }

    @Test
    void fechaMalEscritaEsError() {
        FilaSaldoInventario f = saldo(2, "LECHE", null, "10", null);
        f.setLote("L1");
        f.setVencimiento("31/12/2026");
        assertEquals(1, validar(f).getErrores());
    }

    @Test
    void losSerialesTienenQueSerUnoPorUnidad() {
        FilaSaldoInventario f = saldo(2, "CEL", null, "3", "500000");
        f.setSeriales("S1, S2");
        assertEquals(1, validar(f).getErrores());
        f.setSeriales("S1, S2, S3");
        assertEquals(0, validar(f).getErrores());
    }

    @Test
    void grabarMueveStockKardexLotesYCostoPromedio() {
        FilaSaldoInventario a = saldo(2, "LECHE", null, "10", "2000");
        a.setLote("L1");
        a.setVencimiento("2026-12-31");
        FilaSaldoInventario b = saldo(3, "LECHE", null, "5", "3000");
        b.setLote("L2");
        SaldosInventario req = new SaldosInventario();
        req.setFilas(List.of(a, b));

        Resultado r = service.confirmarSaldos(EMPRESA, SEDE, req);

        assertTrue(r.isConfirmado());
        ArgumentCaptor<MovimientoInventarioEntity> mov = ArgumentCaptor.forClass(MovimientoInventarioEntity.class);
        verify(movimientoRepo).save(mov.capture());
        assertEquals(0, new BigDecimal("15").compareTo(mov.getValue().getCantidad()));
        // 10 × 2000 + 5 × 3000 = 35.000 → costo promedio de la entrada
        verify(costoPromedio).aplicar(eq(2L), eq(new BigDecimal("15")), eq(new BigDecimal("35000")));
        verify(loteStock).entradaDocumento(eq(LoteStockService.AJUSTE_INVENTARIO), eq(500L), eq(leche), any(), eq(EMPRESA),
                eq(new BigDecimal("10")), any(), eq(new BigDecimal("2000")));
    }

    @Test
    void conErroresNoGrabaNada() {
        SaldosInventario req = new SaldosInventario();
        req.setFilas(List.of(saldo(2, "ARROZ", "Trasera", "10", null)));
        Resultado r = service.confirmarSaldos(EMPRESA, SEDE, req);
        assertEquals(1, r.getErrores());
        verify(inventarioRepo, never()).saveAndFlush(any());
        verify(costoPromedio, never()).aplicar(any(), any(), any());
    }

    // ── Conteo ───────────────────────────────────────────────────────────

    private ReconteoEntity reconteo(String estado) {
        ReconteoEntity r = new ReconteoEntity();
        r.setId(40L);
        r.setEstado(estado);
        List<ReconteoDetalleEntity> detalles = new ArrayList<>();
        detalles.add(detalle(1L, arroz, null, "8"));
        LoteEntity l1 = new LoteEntity();
        l1.setCodigoLote("L1");
        LoteEntity l2 = new LoteEntity();
        l2.setCodigoLote("L2");
        detalles.add(detalle(2L, leche, l1, "3"));
        detalles.add(detalle(3L, leche, l2, "4"));
        r.setDetalles(detalles);
        when(reconteoRepo.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(r));
        return r;
    }

    private static ReconteoDetalleEntity detalle(Long id, ProductoEntity p, LoteEntity lote, String sistema) {
        ReconteoDetalleEntity d = new ReconteoDetalleEntity();
        d.setId(id);
        d.setProducto(p);
        d.setLote(lote);
        d.setStockSistema(new BigDecimal(sistema));
        return d;
    }

    private Conteo conteo(FilaConteo... filas) {
        Conteo c = new Conteo();
        c.setReconteoId(40L);
        c.setFilas(List.of(filas));
        return c;
    }

    private FilaConteo contado(int fila, String producto, String lote, String cantidad) {
        FilaConteo f = new FilaConteo();
        f.setFila(fila);
        f.setProducto(producto);
        f.setLote(lote);
        f.setCantidad(new BigDecimal(cantidad));
        return f;
    }

    @Test
    void elConteoMuestraLaDiferenciaYPideElLoteCuandoHayVarios() {
        reconteo("EN_CONTEO");
        Resultado r = service.validarConteo(EMPRESA, conteo(contado(2, "ARROZ", null, "7"), contado(3, "LECHE", null, "3"),
                contado(4, "LECHE", "L2", "4"), contado(5, "CEL", null, "1")));
        assertEquals(2, r.getErrores()); // leche sin lote y celular que no está en el conteo
        assertTrue(r.getFilas().get(0).getMensaje().contains("diferencia -1"));
        assertTrue(r.getFilas().get(2).getMensaje().contains("cuadra"));
    }

    @Test
    void unReconteoAprobadoNoRecibeConteo() {
        reconteo("APROBADO");
        try {
            service.validarConteo(EMPRESA, conteo(contado(2, "ARROZ", null, "7")));
            throw new AssertionError("debió fallar");
        } catch (GlobalException e) {
            assertTrue(e.getMessage().contains("aprobado"));
        }
    }

    @Test
    void confirmarConteoGuardaCadaLinea() {
        reconteo("BORRADOR");
        service.confirmarConteo(EMPRESA, conteo(contado(2, "ARROZ", null, "7")));
        verify(reconteoService).actualizarDetalle(eq(40L), eq(1L), any(), eq(EMPRESA));
    }
}
