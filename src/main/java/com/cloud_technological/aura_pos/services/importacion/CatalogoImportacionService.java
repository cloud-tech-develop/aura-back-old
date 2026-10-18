package com.cloud_technological.aura_pos.services.importacion;

import static com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository.llave;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Categorias;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaCategoria;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaMarca;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaProducto;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Marcas;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Productos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.ResultadoFila;
import com.cloud_technological.aura_pos.dto.productos.CreateProductoDto;
import com.cloud_technological.aura_pos.entity.CategoriaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.MarcaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaJPARepository;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.marcas.MarcaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoQueryRepository;
import com.cloud_technological.aura_pos.services.CostoHistorialService;
import com.cloud_technological.aura_pos.services.OrigenCosto;
import com.cloud_technological.aura_pos.services.ProductoService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Importador de catálogo desde Excel (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2):
 * categorías, marcas y productos. /validar no graba; /confirmar graba todo o nada.
 *
 * <p>Si el registro ya existe se <b>actualiza</b> (categoría y marca por nombre; producto
 * por SKU o, si no trae, por código de barras). En un producto con existencias el
 * costo no se toca: lo lleva el costo promedio ponderado.
 */
@Service
@RequiredArgsConstructor
public class CatalogoImportacionService {

    static final String NUEVO = "NUEVO";
    static final String ACTUALIZA = "ACTUALIZA";
    static final String EXISTE = "EXISTE";
    static final String ERROR = "ERROR";
    private static final Set<BigDecimal> IVAS = Set.of(BigDecimal.ZERO, BigDecimal.valueOf(5), BigDecimal.valueOf(19));

    private final CatalogoImportacionQueryRepository query;
    private final CategoriaJPARepository categoriaRepo;
    private final MarcaJPARepository marcaRepo;
    private final ProductoJPARepository productoRepo;
    private final ProductoQueryRepository productoQuery;
    private final ProductoService productoService;
    private final EntityManager em;
    private final CostoHistorialService costoHistorial;

    // ════════════════════════════════════════════════════════════════════════
    // Categorías
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarCategorias(Integer empresaId, Categorias req) {
        return categorias(empresaId, req, false);
    }

    @Transactional
    public Resultado confirmarCategorias(Integer empresaId, Categorias req) {
        return confirmar(categorias(empresaId, req, false), () -> categorias(empresaId, req, true));
    }

    private Resultado categorias(Integer empresaId, Categorias req, boolean grabar) {
        Resultado r = new Resultado();
        Map<String, Long> existentes = new HashMap<>(query.categorias(empresaId));
        Set<String> enArchivo = new HashSet<>();
        for (FilaCategoria f : req.getFilas()) enArchivo.add(llave(f.getNombre()));
        Set<String> vistos = new HashSet<>();
        EmpresaEntity empresa = grabar ? em.getReference(EmpresaEntity.class, empresaId) : null;

        // Las que tienen padre van después: así el padre del mismo archivo ya existe.
        List<FilaCategoria> orden = new ArrayList<>(req.getFilas());
        orden.sort((a, b) -> Boolean.compare(texto(a.getPadre()) != null, texto(b.getPadre()) != null));

        for (FilaCategoria f : orden) {
            String nombre = texto(f.getNombre());
            String padre = texto(f.getPadre());
            if (nombre == null) {
                error(r, f.getFila(), "", "Falta el nombre");
                continue;
            }
            if (!vistos.add(llave(nombre))) {
                error(r, f.getFila(), nombre, "La categoría está repetida en el archivo");
                continue;
            }
            if (padre != null && llave(padre).equals(llave(nombre))) {
                error(r, f.getFila(), nombre, "Una categoría no puede ser su propio padre");
                continue;
            }
            if (padre != null && !existentes.containsKey(llave(padre)) && !enArchivo.contains(llave(padre))) {
                error(r, f.getFila(), nombre, "La categoría padre '" + padre + "' no existe ni viene en el archivo");
                continue;
            }
            BigDecimal iva = f.getIvaDefecto();
            if (iva != null && !esIva(iva)) {
                error(r, f.getFila(), nombre, "IVA no válido: " + iva.stripTrailingZeros().toPlainString() + " (use 0, 5 o 19)");
                continue;
            }

            Long id = existentes.get(llave(nombre));
            if (id != null && padre == null && iva == null) {
                fila(r, f.getFila(), EXISTE, nombre, "Ya existe: no hay nada que cambiar");
                r.setExistentes(r.getExistentes() + 1);
                continue;
            }
            if (grabar) {
                CategoriaEntity c = id != null ? categoriaRepo.findById(id).orElseThrow() : new CategoriaEntity();
                if (id == null) {
                    c.setEmpresa(empresa);
                    c.setNombre(nombre);
                    c.setActivo(true);
                }
                if (padre != null) c.setPadre(categoriaRepo.findById(existentes.get(llave(padre))).orElseThrow());
                if (iva != null) c.setImpuestoDefecto(iva);
                c = categoriaRepo.saveAndFlush(c);
                existentes.put(llave(nombre), c.getId());
            } else if (id == null) {
                existentes.put(llave(nombre), -1L);
            }
            if (id != null) {
                fila(r, f.getFila(), ACTUALIZA, nombre, "Se actualiza" + (padre != null ? " (padre: " + padre + ")" : ""));
                r.setActualizados(r.getActualizados() + 1);
            } else {
                fila(r, f.getFila(), NUEVO, nombre, padre != null ? "Dentro de " + padre : "");
                r.setNuevos(r.getNuevos() + 1);
            }
        }
        r.setConfirmado(grabar);
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Marcas
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarMarcas(Integer empresaId, Marcas req) {
        return marcas(empresaId, req, false);
    }

    @Transactional
    public Resultado confirmarMarcas(Integer empresaId, Marcas req) {
        return confirmar(marcas(empresaId, req, false), () -> marcas(empresaId, req, true));
    }

    private Resultado marcas(Integer empresaId, Marcas req, boolean grabar) {
        Resultado r = new Resultado();
        Map<String, Long> existentes = query.marcas(empresaId);
        Set<String> vistos = new HashSet<>();
        EmpresaEntity empresa = grabar ? em.getReference(EmpresaEntity.class, empresaId) : null;
        for (FilaMarca f : req.getFilas()) {
            String nombre = texto(f.getNombre());
            if (nombre == null) {
                error(r, f.getFila(), "", "Falta el nombre");
                continue;
            }
            if (!vistos.add(llave(nombre))) {
                error(r, f.getFila(), nombre, "La marca está repetida en el archivo");
                continue;
            }
            if (existentes.containsKey(llave(nombre))) {
                fila(r, f.getFila(), EXISTE, nombre, "Ya existe");
                r.setExistentes(r.getExistentes() + 1);
                continue;
            }
            if (grabar) marcaRepo.save(nuevaMarca(empresa, nombre));
            fila(r, f.getFila(), NUEVO, nombre, "");
            r.setNuevos(r.getNuevos() + 1);
        }
        r.setConfirmado(grabar);
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Productos
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarProductos(Integer empresaId, Productos req) {
        return productos(empresaId, req, false);
    }

    @Transactional
    public Resultado confirmarProductos(Integer empresaId, Productos req) {
        return confirmar(productos(empresaId, req, false), () -> productos(empresaId, req, true));
    }

    private Resultado productos(Integer empresaId, Productos req, boolean grabar) {
        Resultado r = new Resultado();
        Map<String, Long> porSku = query.productosPorSku(empresaId);
        Map<String, Long> porBarras = query.productosPorCodigoBarras(empresaId);
        Map<String, Long> categorias = new HashMap<>(query.categorias(empresaId));
        Map<String, Long> marcas = new HashMap<>(query.marcas(empresaId));
        Map<String, Long> unidades = query.unidades();
        Long unidadPorDefecto = unidades.getOrDefault("unidad", unidades.get("und"));
        Set<String> skusArchivo = new HashSet<>();
        Set<String> barrasArchivo = new HashSet<>();
        Set<String> categoriasNuevas = new HashSet<>();
        Set<String> marcasNuevas = new HashSet<>();
        EmpresaEntity empresa = grabar ? em.getReference(EmpresaEntity.class, empresaId) : null;

        for (FilaProducto f : req.getFilas()) {
            String sku = texto(f.getSku());
            String barras = texto(f.getCodigoBarras());
            String nombre = texto(f.getNombre());
            String etiqueta = (sku != null ? sku + " " : "") + (nombre != null ? nombre : barras != null ? barras : "");

            // ¿Cuál producto es? Por SKU; por código de barras solo si la fila no trae SKU
            // (un SKU nuevo con el código de barras de otro producto es un error, no ese producto).
            Long id = sku != null ? porSku.get(llave(sku)) : null;
            if (sku == null && barras != null) id = porBarras.get(llave(barras));

            if (sku != null && !skusArchivo.add(llave(sku))) {
                error(r, f.getFila(), etiqueta, "El código interno (SKU) está repetido en el archivo");
                continue;
            }
            if (barras != null && !barrasArchivo.add(llave(barras))) {
                error(r, f.getFila(), etiqueta, "El código de barras está repetido en el archivo");
                continue;
            }
            if (barras != null) {
                Long dueno = porBarras.get(llave(barras));
                if (dueno != null && !dueno.equals(id)) {
                    error(r, f.getFila(), etiqueta, "El código de barras " + barras + " ya es de otro producto");
                    continue;
                }
            }
            if (id == null && nombre == null) {
                error(r, f.getFila(), etiqueta, "Falta el nombre del producto");
                continue;
            }
            if (id == null && f.getPrecio() == null) {
                error(r, f.getFila(), etiqueta, "Falta el precio de venta");
                continue;
            }
            String negativo = negativo(f);
            if (negativo != null) {
                error(r, f.getFila(), etiqueta, negativo + " no puede ser negativo");
                continue;
            }
            if (f.getIvaPorcentaje() != null && !esIva(f.getIvaPorcentaje())) {
                error(r, f.getFila(), etiqueta, "IVA no válido: " + f.getIvaPorcentaje().stripTrailingZeros().toPlainString()
                        + " (use 0, 5 o 19)");
                continue;
            }
            String clasificacion = clasificacion(f.getClasificacion());
            if (f.getClasificacion() != null && clasificacion == null) {
                error(r, f.getFila(), etiqueta, "Clasificación no válida: " + f.getClasificacion() + " (use PRODUCTO o SERVICIO)");
                continue;
            }
            Long unidadId = texto(f.getUnidad()) != null ? unidades.get(llave(f.getUnidad())) : unidadPorDefecto;
            if (unidadId == null) {
                error(r, f.getFila(), etiqueta, "Unidad de medida no reconocida: " + f.getUnidad());
                continue;
            }

            List<String> notas = new ArrayList<>();
            String categoria = texto(f.getCategoria());
            String marca = texto(f.getMarca());
            if (categoria != null && !categorias.containsKey(llave(categoria)) && categoriasNuevas.add(llave(categoria)))
                notas.add("crea la categoría " + categoria);
            if (marca != null && !marcas.containsKey(llave(marca)) && marcasNuevas.add(llave(marca)))
                notas.add("crea la marca " + marca);

            boolean conStock = id != null && f.getCosto() != null
                    && productoQuery.stockTotal(id, empresaId).signum() != 0;
            if (conStock) notas.add("el costo no se cambia: tiene existencias y lo lleva el costo promedio");

            if (grabar) {
                Long categoriaId = categoria != null ? asegurarCategoria(categorias, categoria, empresa) : null;
                Long marcaId = marca != null ? asegurarMarca(marcas, marca, empresa) : null;
                if (id == null) {
                    CreateProductoDto dto = new CreateProductoDto();
                    dto.setNombre(nombre);
                    dto.setSku(sku);
                    dto.setCodigoBarras(barras);
                    dto.setDescripcion(texto(f.getDescripcion()));
                    dto.setCategoriaId(categoriaId);
                    dto.setMarcaId(marcaId);
                    dto.setUnidadMedidaBaseId(unidadId);
                    dto.setClasificacion(clasificacion != null ? clasificacion : "PRODUCTO");
                    boolean servicio = "SERVICIO".equals(dto.getClasificacion());
                    dto.setManejaInventario(f.getManejaInventario() != null ? f.getManejaInventario() : !servicio);
                    dto.setCosto(nz(f.getCosto()));
                    dto.setPrecio(nz(f.getPrecio()));
                    dto.setPrecio2(f.getPrecio2());
                    dto.setPrecio3(f.getPrecio3());
                    dto.setIvaPorcentaje(nz(f.getIvaPorcentaje()));
                    dto.setIvaIncluido(Boolean.TRUE.equals(f.getIvaIncluido()));
                    dto.setImpoconsumo(nz(f.getImpoconsumo()));
                    dto.setActivo(f.getActivo() == null || f.getActivo());
                    productoService.crear(dto, empresaId);
                } else {
                    actualizarProducto(id, f, categoriaId, marcaId, conStock);
                }
            }
            if (id == null) {
                fila(r, f.getFila(), NUEVO, etiqueta, String.join("; ", notas));
                r.setNuevos(r.getNuevos() + 1);
            } else {
                fila(r, f.getFila(), ACTUALIZA, etiqueta, "Se actualiza" + (notas.isEmpty() ? "" : "; " + String.join("; ", notas)));
                r.setActualizados(r.getActualizados() + 1);
            }
        }
        r.setConfirmado(grabar);
        return r;
    }

    /** Solo lo que trae la fila: una celda vacía no borra lo que el producto tiene. */
    private void actualizarProducto(Long id, FilaProducto f, Long categoriaId, Long marcaId, boolean conStock) {
        ProductoEntity p = productoRepo.findById(id).orElseThrow();
        if (texto(f.getNombre()) != null) p.setNombre(texto(f.getNombre()));
        if (texto(f.getDescripcion()) != null) p.setDescripcion(texto(f.getDescripcion()));
        if (texto(f.getCodigoBarras()) != null) p.setCodigoBarras(texto(f.getCodigoBarras()));
        if (categoriaId != null) p.setCategoria(categoriaRepo.findById(categoriaId).orElseThrow());
        if (marcaId != null) p.setMarca(marcaRepo.findById(marcaId).orElseThrow());
        if (f.getPrecio() != null) p.setPrecio(f.getPrecio());
        if (f.getPrecio2() != null) p.setPrecio2(f.getPrecio2());
        if (f.getPrecio3() != null) p.setPrecio3(f.getPrecio3());
        if (f.getIvaPorcentaje() != null) p.setIvaPorcentaje(f.getIvaPorcentaje());
        if (f.getIvaIncluido() != null) p.setIvaIncluido(f.getIvaIncluido());
        if (f.getImpoconsumo() != null) p.setImpoconsumo(f.getImpoconsumo());
        if (f.getActivo() != null) p.setActivo(f.getActivo());
        BigDecimal costoAntes = p.getCosto();
        if (f.getCosto() != null && !conStock) p.setCosto(f.getCosto());
        p.setUpdatedAt(LocalDateTime.now());
        productoRepo.save(p);
        costoHistorial.registrarCambio(p, costoAntes,
                OrigenCosto.de(OrigenCosto.IMPORTACION, "Importación del catálogo desde Excel"));
    }

    private Long asegurarCategoria(Map<String, Long> categorias, String nombre, EmpresaEntity empresa) {
        Long id = categorias.get(llave(nombre));
        if (id != null && id > 0) return id;
        CategoriaEntity c = new CategoriaEntity();
        c.setEmpresa(empresa);
        c.setNombre(nombre);
        c.setActivo(true);
        c = categoriaRepo.saveAndFlush(c);
        categorias.put(llave(nombre), c.getId());
        return c.getId();
    }

    private Long asegurarMarca(Map<String, Long> marcas, String nombre, EmpresaEntity empresa) {
        Long id = marcas.get(llave(nombre));
        if (id != null && id > 0) return id;
        MarcaEntity m = marcaRepo.saveAndFlush(nuevaMarca(empresa, nombre));
        marcas.put(llave(nombre), m.getId());
        return m.getId();
    }

    private static MarcaEntity nuevaMarca(EmpresaEntity empresa, String nombre) {
        MarcaEntity m = new MarcaEntity();
        m.setEmpresa(empresa);
        m.setNombre(nombre);
        m.setActivo(true);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        return m;
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /**
     * Valida primero; si hay errores devuelve ese resultado sin grabar. Si al grabar
     * algo falla (otra regla del producto), se deshace todo y se devuelve el motivo.
     */
    static Resultado confirmar(Resultado validacion, java.util.function.Supplier<Resultado> grabar) {
        if (!validacion.puedeConfirmarse()) return validacion;
        try {
            return grabar.get();
        } catch (GlobalException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            Resultado r = new Resultado();
            r.setErrores(1);
            r.getAvisos().add("No se importó nada: " + e.getMessage());
            return r;
        }
    }

    private static String clasificacion(String v) {
        String t = texto(v);
        if (t == null) return null;
        String k = llave(t).toUpperCase();
        if (k.startsWith("SERV")) return "SERVICIO";
        if (k.startsWith("PROD") || k.startsWith("MERC")) return "PRODUCTO";
        return null;
    }

    private static String negativo(FilaProducto f) {
        if (neg(f.getCosto())) return "El costo";
        if (neg(f.getPrecio()) || neg(f.getPrecio2()) || neg(f.getPrecio3())) return "El precio";
        if (neg(f.getImpoconsumo())) return "El impoconsumo";
        return null;
    }

    static boolean neg(BigDecimal v) {
        return v != null && v.signum() < 0;
    }

    private static boolean esIva(BigDecimal v) {
        return IVAS.stream().anyMatch(i -> i.compareTo(v) == 0);
    }

    static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    static String texto(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    static void fila(Resultado r, Integer fila, String estado, String detalle, String mensaje) {
        r.getFilas().add(new ResultadoFila(fila, estado, detalle, mensaje));
    }

    static void error(Resultado r, Integer fila, String detalle, String mensaje) {
        fila(r, fila, ERROR, detalle, mensaje);
        r.setErrores(r.getErrores() + 1);
    }
}
