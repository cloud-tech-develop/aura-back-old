package com.cloud_technological.aura_pos.dto.importacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Filas del importador de catálogo (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2).
 * El Excel se lee en el navegador; cada fila trae su número para ubicar los errores.
 * El resultado es el mismo {@link ImportacionDtos.Resultado} del importador contable.
 */
public final class CatalogoImportacionDtos {

    private CatalogoImportacionDtos() {
    }

    @Data
    public static class FilaCategoria {
        private Integer fila;
        private String nombre;
        /** Nombre de la categoría padre (opcional; debe existir o venir antes en el archivo). */
        private String padre;
        /** % de IVA sugerido para sus productos. */
        private BigDecimal ivaDefecto;
    }

    @Data
    public static class Categorias {
        private List<FilaCategoria> filas = new ArrayList<>();
    }

    @Data
    public static class FilaMarca {
        private Integer fila;
        private String nombre;
    }

    @Data
    public static class Marcas {
        private List<FilaMarca> filas = new ArrayList<>();
    }

    @Data
    public static class FilaProducto {
        private Integer fila;
        /** Código interno. Con él (o el código de barras) se reconoce un producto que ya existe. */
        private String sku;
        private String codigoBarras;
        private String nombre;
        private String descripcion;
        /** Nombre; si no existe se crea. */
        private String categoria;
        /** Nombre; si no existe se crea. */
        private String marca;
        /** Nombre o abreviatura de la unidad (Unidad, UND, KG…); por defecto Unidad. */
        private String unidad;
        /** PRODUCTO | SERVICIO (por defecto PRODUCTO). */
        private String clasificacion;
        private Boolean manejaInventario;
        private BigDecimal costo;
        private BigDecimal precio;
        private BigDecimal precio2;
        private BigDecimal precio3;
        /** 0, 5 o 19. */
        private BigDecimal ivaPorcentaje;
        private Boolean ivaIncluido;
        private BigDecimal impoconsumo;
        private Boolean activo;
    }

    @Data
    public static class Productos {
        private List<FilaProducto> filas = new ArrayList<>();
    }

    // ── Segunda parte (2b) ───────────────────────────────────────────────

    /** Precio de un producto (o de una presentación suya) en una lista. */
    @Data
    public static class FilaPrecio {
        private Integer fila;
        /** Nombre de la lista; si no existe se crea. */
        private String lista;
        /** Código interno (SKU) o código de barras del producto o de la presentación. */
        private String producto;
        /** Nombre de la presentación (opcional; sin ella el precio es del producto). */
        private String presentacion;
        private BigDecimal precio;
        private BigDecimal utilidadEsperada;
    }

    @Data
    public static class Precios {
        private List<FilaPrecio> filas = new ArrayList<>();
    }

    @Data
    public static class FilaPresentacion {
        private Integer fila;
        /** Código interno (SKU) o código de barras del producto. */
        private String producto;
        private String nombre;
        private String codigoBarras;
        /** Unidades del producto que contiene ("Caja x 12" = 12). */
        private BigDecimal contiene;
        private BigDecimal precio;
        private BigDecimal costo;
        private Boolean seVende;
        private Boolean defaultVenta;
        private Boolean defaultCompra;
        private Boolean activo;
    }

    @Data
    public static class Presentaciones {
        private List<FilaPresentacion> filas = new ArrayList<>();
    }

    /** Una línea de receta: varias filas con el mismo producto arman su receta completa. */
    @Data
    public static class FilaReceta {
        private Integer fila;
        /** Código del producto que se fabrica o se arma. */
        private String producto;
        /** RECETA o KIT (por defecto RECETA); vale el de la primera fila del producto. */
        private String tipo;
        /** Unidades que salen (ej. 40 panes); vale el de la primera fila que lo traiga. */
        private BigDecimal rendimiento;
        /** Código del ingrediente o componente. */
        private String componente;
        private BigDecimal cantidad;
        /** Nombre de la presentación del componente en la que está escrita la cantidad (opcional). */
        private String presentacion;
        private BigDecimal merma;
        private String nota;
    }

    @Data
    public static class Recetas {
        private List<FilaReceta> filas = new ArrayList<>();
    }

    // ── Inventario (2c) ──────────────────────────────────────────────────

    @Data
    public static class FilaBodega {
        private Integer fila;
        private String nombre;
        /** Nombre o código de la sede; vacío = la sede en la que está trabajando. */
        private String sede;
        private String codigo;
        private Boolean permiteVenta;
        private String ubicacion;
    }

    @Data
    public static class Bodegas {
        private List<FilaBodega> filas = new ArrayList<>();
    }

    /** Existencias con las que arranca un producto en una bodega. */
    @Data
    public static class FilaSaldoInventario {
        private Integer fila;
        /** Código interno (SKU) o código de barras. */
        private String producto;
        /** Nombre o código de la bodega; vacío = la principal de la sede de trabajo. */
        private String bodega;
        private BigDecimal cantidad;
        /** Costo unitario; vacío = el costo del producto. */
        private BigDecimal costo;
        private String lote;
        /** AAAA-MM-DD */
        private String vencimiento;
        private String fabricacion;
        /** Seriales separados por coma (uno por unidad). */
        private String seriales;
    }

    @Data
    public static class SaldosInventario {
        private List<FilaSaldoInventario> filas = new ArrayList<>();
    }

    /** Lo contado en un reconteo abierto. */
    @Data
    public static class FilaConteo {
        private Integer fila;
        private String producto;
        /** Código del lote, si el producto tiene varios en el conteo. */
        private String lote;
        private BigDecimal cantidad;
    }

    @Data
    public static class Conteo {
        private Long reconteoId;
        private List<FilaConteo> filas = new ArrayList<>();
    }
}
