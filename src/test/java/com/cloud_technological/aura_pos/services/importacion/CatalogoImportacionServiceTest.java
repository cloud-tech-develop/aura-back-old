package com.cloud_technological.aura_pos.services.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Categorias;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaCategoria;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaMarca;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaProducto;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Marcas;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Productos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaJPARepository;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.marcas.MarcaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoQueryRepository;
import com.cloud_technological.aura_pos.services.ProductoService;

import jakarta.persistence.EntityManager;

/** Reglas del importador de catálogo (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2). */
class CatalogoImportacionServiceTest {

    private static final int EMPRESA = 7;

    private CatalogoImportacionQueryRepository query;
    private ProductoQueryRepository productoQuery;
    private ProductoService productoService;
    private CatalogoImportacionService service;

    @BeforeEach
    void setUp() {
        query = mock(CatalogoImportacionQueryRepository.class);
        productoQuery = mock(ProductoQueryRepository.class);
        productoService = mock(ProductoService.class);
        service = new CatalogoImportacionService(query, mock(CategoriaJPARepository.class), mock(MarcaJPARepository.class),
                mock(ProductoJPARepository.class), productoQuery, productoService, mock(EntityManager.class));
        when(query.unidades()).thenReturn(Map.of("unidad", 1L, "und", 1L, "kg", 2L));
        when(query.productosPorSku(EMPRESA)).thenReturn(Map.of("p-001", 50L));
        when(query.productosPorCodigoBarras(EMPRESA)).thenReturn(Map.of("7701", 60L));
        when(query.categorias(EMPRESA)).thenReturn(Map.of("bebidas", 3L));
        when(query.marcas(EMPRESA)).thenReturn(Map.of("postobon", 4L));
    }

    private FilaProducto producto(int fila, String sku, String nombre, String precio) {
        FilaProducto f = new FilaProducto();
        f.setFila(fila);
        f.setSku(sku);
        f.setNombre(nombre);
        f.setPrecio(precio != null ? new BigDecimal(precio) : null);
        return f;
    }

    private Resultado validar(FilaProducto... filas) {
        Productos p = new Productos();
        p.setFilas(List.of(filas));
        return service.validarProductos(EMPRESA, p);
    }

    @Test
    void unProductoNuevoValido() {
        FilaProducto f = producto(2, "P-900", "Gaseosa 400 ml", "2500");
        f.setCategoria("Bebidas");
        f.setMarca("Nueva marca");
        Resultado r = validar(f);
        assertEquals(1, r.getNuevos());
        assertEquals(0, r.getErrores());
        assertTrue(r.getFilas().get(0).getMensaje().contains("crea la marca Nueva marca"));
        assertTrue(r.puedeConfirmarse());
    }

    @Test
    void elSkuQueYaExisteSeActualiza() {
        Resultado r = validar(producto(2, "p-001", null, "3000"));
        assertEquals(1, r.getActualizados());
        assertEquals("ACTUALIZA", r.getFilas().get(0).getEstado());
    }

    @Test
    void unNuevoSinPrecioEsError() {
        Resultado r = validar(producto(2, "P-901", "Sin precio", null));
        assertEquals(1, r.getErrores());
        assertFalse(r.puedeConfirmarse());
    }

    @Test
    void ivaRaroEsError() {
        FilaProducto f = producto(2, "P-902", "Raro", "1000");
        f.setIvaPorcentaje(new BigDecimal("16"));
        assertEquals(1, validar(f).getErrores());
    }

    @Test
    void skuRepetidoEnElArchivoEsError() {
        Resultado r = validar(producto(2, "P-1", "A", "1"), producto(3, "p-1", "B", "1"));
        assertEquals(1, r.getErrores());
        assertEquals(3, r.getFilas().get(1).getFila());
    }

    @Test
    void codigoDeBarrasDeOtroProductoEsError() {
        FilaProducto f = producto(2, "P-903", "Otro", "1000");
        f.setCodigoBarras("7701");
        assertEquals(1, validar(f).getErrores());
    }

    @Test
    void unidadDesconocidaEsError() {
        FilaProducto f = producto(2, "P-904", "Arroz", "1000");
        f.setUnidad("Bulto");
        assertEquals(1, validar(f).getErrores());
    }

    @Test
    void conExistenciasElCostoNoSeCambia() {
        when(productoQuery.stockTotal(50L, EMPRESA)).thenReturn(BigDecimal.TEN);
        FilaProducto f = producto(2, "P-001", null, null);
        f.setCosto(new BigDecimal("999"));
        Resultado r = validar(f);
        assertTrue(r.getFilas().get(0).getMensaje().contains("el costo no se cambia"));
    }

    @Test
    void conErroresConfirmarNoGraba() {
        Productos p = new Productos();
        p.setFilas(List.of(producto(2, "P-905", "Sin precio", null)));
        Resultado r = service.confirmarProductos(EMPRESA, p);
        assertFalse(r.isConfirmado());
        verify(productoService, never()).crear(any(), anyInt());
    }

    @Test
    void categoriaConPadreQueNoExisteEsError() {
        FilaCategoria c = new FilaCategoria();
        c.setFila(2);
        c.setNombre("Gaseosas");
        c.setPadre("No existe");
        Categorias req = new Categorias();
        req.setFilas(List.of(c));
        assertEquals(1, service.validarCategorias(EMPRESA, req).getErrores());
    }

    @Test
    void categoriaConPadreDelMismoArchivoEsValida() {
        FilaCategoria hija = new FilaCategoria();
        hija.setFila(2);
        hija.setNombre("Gaseosas");
        hija.setPadre("Refrescos");
        FilaCategoria padre = new FilaCategoria();
        padre.setFila(3);
        padre.setNombre("Refrescos");
        Categorias req = new Categorias();
        req.setFilas(List.of(hija, padre));
        Resultado r = service.validarCategorias(EMPRESA, req);
        assertEquals(0, r.getErrores());
        assertEquals(2, r.getNuevos());
    }

    @Test
    void marcaQueYaExisteNoSeDuplica() {
        FilaMarca m = new FilaMarca();
        m.setFila(2);
        m.setNombre("Postobón");
        Marcas req = new Marcas();
        req.setFilas(List.of(m));
        Resultado r = service.validarMarcas(EMPRESA, req);
        assertEquals(1, r.getExistentes());
        assertEquals(0, r.getNuevos());
    }

    @SuppressWarnings("unused")
    private static long cualquiera() {
        return anyLong();
    }
}
