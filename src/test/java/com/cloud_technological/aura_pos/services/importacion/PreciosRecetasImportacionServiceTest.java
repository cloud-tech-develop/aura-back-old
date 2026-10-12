package com.cloud_technological.aura_pos.services.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaPrecio;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaPresentacion;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaReceta;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Precios;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Presentaciones;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Recetas;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.producto_composicion.GuardarRecetaDto;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository;
import com.cloud_technological.aura_pos.services.ListaPreciosService;
import com.cloud_technological.aura_pos.services.ProductoComposicionService;
import com.cloud_technological.aura_pos.services.ProductoPrecioService;
import com.cloud_technological.aura_pos.services.ProductoPresentacionService;

/** Precios por lista, presentaciones y recetas desde Excel (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2b). */
class PreciosRecetasImportacionServiceTest {

    private static final int EMPRESA = 7;

    private CatalogoImportacionQueryRepository query;
    private ProductoComposicionService composicion;
    private PreciosRecetasImportacionService service;

    @BeforeEach
    void setUp() {
        query = mock(CatalogoImportacionQueryRepository.class);
        composicion = mock(ProductoComposicionService.class);
        service = new PreciosRecetasImportacionService(query, mock(ListaPreciosService.class),
                mock(ProductoPrecioService.class), mock(ProductoPresentacionService.class), composicion);
        when(query.productosPorSku(EMPRESA)).thenReturn(Map.of("pan", 1L, "harina", 2L, "azucar", 3L, "gaseosa", 4L));
        when(query.productosPorCodigoBarras(EMPRESA)).thenReturn(Map.of("7701", 4L));
        when(query.presentaciones(EMPRESA)).thenReturn(Map.of("2|bulto 50 kg", 20L, "4|caja x 12", 40L));
        when(query.presentacionesPorCodigoBarras(EMPRESA)).thenReturn(Map.of("7799", new long[] { 40L, 4L }));
        when(query.listas(EMPRESA)).thenReturn(Map.of("mayoristas", 9L));
        when(query.precios(EMPRESA)).thenReturn(Map.of(CatalogoImportacionQueryRepository.llavePrecio(9, 4, 0), 100L));
        when(query.conReceta(EMPRESA)).thenReturn(Set.of(1L));
    }

    // ── Precios ──────────────────────────────────────────────────────────

    private FilaPrecio precio(int fila, String lista, String producto, String presentacion, String valor) {
        FilaPrecio f = new FilaPrecio();
        f.setFila(fila);
        f.setLista(lista);
        f.setProducto(producto);
        f.setPresentacion(presentacion);
        f.setPrecio(valor != null ? new BigDecimal(valor) : null);
        return f;
    }

    private Resultado precios(FilaPrecio... filas) {
        Precios p = new Precios();
        p.setFilas(List.of(filas));
        return service.validarPrecios(EMPRESA, p);
    }

    @Test
    void elPrecioQueYaExisteSeActualizaYElNuevoSeCrea() {
        Resultado r = precios(precio(2, "Mayoristas", "GASEOSA", null, "2000"),
                precio(3, "Mayoristas", "gaseosa", "Caja x 12", "22000"));
        assertEquals(1, r.getActualizados());
        assertEquals(1, r.getNuevos());
        assertEquals(0, r.getErrores());
    }

    @Test
    void elCodigoDeBarrasDeUnaPresentacionSirve() {
        Resultado r = precios(precio(2, "Mayoristas", "7799", null, "22000"));
        assertEquals(1, r.getNuevos());
    }

    @Test
    void unaListaNuevaSeAvisa() {
        Resultado r = precios(precio(2, "Distribuidores", "PAN", null, "300"));
        assertEquals(1, r.getNuevos());
        assertTrue(r.getFilas().get(0).getMensaje().contains("crea la lista"));
    }

    @Test
    void productoOPresentacionInexistentesSonError() {
        Resultado r = precios(precio(2, "Mayoristas", "NO-EXISTE", null, "1"),
                precio(3, "Mayoristas", "PAN", "Docena", "1"));
        assertEquals(2, r.getErrores());
    }

    @Test
    void precioRepetidoEnElArchivoEsError() {
        Resultado r = precios(precio(2, "Mayoristas", "PAN", null, "1"), precio(3, "mayoristas", "pan", null, "2"));
        assertEquals(1, r.getErrores());
    }

    // ── Presentaciones ───────────────────────────────────────────────────

    @Test
    void presentacionNuevaSinLoQueContieneEsError() {
        FilaPresentacion f = new FilaPresentacion();
        f.setFila(2);
        f.setProducto("PAN");
        f.setNombre("Docena");
        Presentaciones req = new Presentaciones();
        req.setFilas(List.of(f));
        assertEquals(1, service.validarPresentaciones(EMPRESA, req).getErrores());
    }

    @Test
    void presentacionExistenteSeActualiza() {
        FilaPresentacion f = new FilaPresentacion();
        f.setFila(2);
        f.setProducto("GASEOSA");
        f.setNombre("Caja x 12");
        f.setPrecio(new BigDecimal("23000"));
        Presentaciones req = new Presentaciones();
        req.setFilas(List.of(f));
        assertEquals(1, service.validarPresentaciones(EMPRESA, req).getActualizados());
    }

    // ── Recetas ──────────────────────────────────────────────────────────

    private FilaReceta linea(int fila, String producto, String componente, String cantidad) {
        FilaReceta f = new FilaReceta();
        f.setFila(fila);
        f.setProducto(producto);
        f.setComponente(componente);
        f.setCantidad(cantidad != null ? new BigDecimal(cantidad) : null);
        return f;
    }

    @Test
    void lasFilasDeUnProductoArmanUnaSolaReceta() {
        FilaReceta a = linea(2, "PAN", "HARINA", "1");
        a.setPresentacion("Bulto 50 kg");
        a.setRendimiento(new BigDecimal("40"));
        FilaReceta b = linea(3, "PAN", "AZUCAR", "0.5");
        Recetas req = new Recetas();
        req.setFilas(List.of(a, b));

        Resultado r = service.confirmarRecetas(EMPRESA, req);

        assertEquals(0, r.getErrores());
        assertEquals(1, r.getActualizados()); // el pan ya tenía receta: se reemplaza
        ArgumentCaptor<GuardarRecetaDto> c = ArgumentCaptor.forClass(GuardarRecetaDto.class);
        verify(composicion).guardarReceta(eq(1L), c.capture(), eq(EMPRESA));
        assertEquals(2, c.getValue().getComponentes().size());
        assertEquals(20L, c.getValue().getComponentes().get(0).getProductoPresentacionId());
        assertEquals(0, new BigDecimal("40").compareTo(c.getValue().getRendimiento()));
        assertEquals("RECETA", c.getValue().getTipo());
    }

    @Test
    void unProductoNoEsComponenteDeSiMismo() {
        Recetas req = new Recetas();
        req.setFilas(List.of(linea(2, "PAN", "PAN", "1")));
        Resultado r = service.confirmarRecetas(EMPRESA, req);
        assertEquals(1, r.getErrores());
        verify(composicion, never()).guardarReceta(anyLong(), any(), any());
    }

    @Test
    void componenteRepetidoYCantidadCeroSonError() {
        Recetas req = new Recetas();
        req.setFilas(List.of(linea(2, "GASEOSA", "AZUCAR", "1"), linea(3, "GASEOSA", "AZUCAR", "2"),
                linea(4, "GASEOSA", "HARINA", "0")));
        assertEquals(2, service.validarRecetas(EMPRESA, req).getErrores());
    }
}
