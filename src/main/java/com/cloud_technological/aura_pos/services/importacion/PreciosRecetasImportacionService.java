package com.cloud_technological.aura_pos.services.importacion;

import static com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository.llave;
import static com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository.llavePrecio;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.ACTUALIZA;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.NUEVO;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.confirmar;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.error;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.fila;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.neg;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.texto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaPrecio;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaPresentacion;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaReceta;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Precios;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Presentaciones;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Recetas;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.lista_precios.CreateListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.CreateProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.GuardarRecetaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaComponenteDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.CreateProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.UpdateProductoPresentacionDto;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository;
import com.cloud_technological.aura_pos.services.ListaPreciosService;
import com.cloud_technological.aura_pos.services.ProductoComposicionService;
import com.cloud_technological.aura_pos.services.ProductoPrecioService;
import com.cloud_technological.aura_pos.services.ProductoPresentacionService;

import lombok.RequiredArgsConstructor;

/**
 * Importador de la segunda parte del catálogo (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2b):
 * precios por lista, presentaciones y recetas. Todo pasa por los servicios de cada
 * pantalla, así que se cumplen las mismas reglas (factor de la presentación,
 * conversiones y merma de la receta). /validar no graba; /confirmar es todo o nada.
 *
 * <p>Un producto se busca por su código interno (SKU) o por su código de barras.
 */
@Service
@RequiredArgsConstructor
public class PreciosRecetasImportacionService {

    private final CatalogoImportacionQueryRepository query;
    private final ListaPreciosService listaService;
    private final ProductoPrecioService precioService;
    private final ProductoPresentacionService presentacionService;
    private final ProductoComposicionService composicionService;

    /** Productos de la empresa por SKU y por código de barras. */
    private record Productos(Map<String, Long> porSku, Map<String, Long> porBarras) {
        Long buscar(String codigo) {
            if (codigo == null) return null;
            Long id = porSku.get(llave(codigo));
            return id != null ? id : porBarras.get(llave(codigo));
        }
    }

    private Productos productos(Integer empresaId) {
        return new Productos(query.productosPorSku(empresaId), query.productosPorCodigoBarras(empresaId));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Precios por lista
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarPrecios(Integer empresaId, Precios req) {
        return precios(empresaId, req, false);
    }

    @Transactional
    public Resultado confirmarPrecios(Integer empresaId, Precios req) {
        return confirmar(precios(empresaId, req, false), () -> precios(empresaId, req, true));
    }

    private Resultado precios(Integer empresaId, Precios req, boolean grabar) {
        Resultado r = new Resultado();
        Productos productos = productos(empresaId);
        Map<String, long[]> presPorBarras = query.presentacionesPorCodigoBarras(empresaId);
        Map<String, Long> presentaciones = query.presentaciones(empresaId);
        Map<String, Long> listas = new HashMap<>(query.listas(empresaId));
        Map<String, Long> precios = query.precios(empresaId);
        Set<String> listasNuevas = new HashSet<>();
        Set<String> enArchivo = new HashSet<>();

        for (FilaPrecio f : req.getFilas()) {
            String lista = texto(f.getLista());
            String codigo = texto(f.getProducto());
            String presNombre = texto(f.getPresentacion());
            String etiqueta = (codigo != null ? codigo : "") + (presNombre != null ? " · " + presNombre : "")
                    + (lista != null ? " en " + lista : "");
            if (lista == null) {
                error(r, f.getFila(), etiqueta, "Falta la lista de precios");
                continue;
            }
            if (codigo == null) {
                error(r, f.getFila(), etiqueta, "Falta el código del producto");
                continue;
            }
            if (f.getPrecio() == null) {
                error(r, f.getFila(), etiqueta, "Falta el precio");
                continue;
            }
            if (neg(f.getPrecio())) {
                error(r, f.getFila(), etiqueta, "El precio no puede ser negativo");
                continue;
            }

            // El código puede ser de una presentación (su código de barras).
            Long productoId = productos.buscar(codigo);
            Long presentacionId = null;
            if (productoId == null) {
                long[] p = presPorBarras.get(llave(codigo));
                if (p != null) {
                    presentacionId = p[0];
                    productoId = p[1];
                }
            }
            if (productoId == null) {
                error(r, f.getFila(), etiqueta, "No hay un producto con el código " + codigo);
                continue;
            }
            if (presNombre != null) {
                Long p = presentaciones.get(productoId + "|" + llave(presNombre));
                if (p == null) {
                    error(r, f.getFila(), etiqueta, "El producto no tiene la presentación '" + presNombre + "'");
                    continue;
                }
                presentacionId = p;
            }

            Long listaId = listas.get(llave(lista));
            String nota = "";
            if (listaId == null && listasNuevas.add(llave(lista))) nota = "crea la lista " + lista;
            if (grabar && listaId == null) {
                CreateListaPreciosDto l = new CreateListaPreciosDto();
                l.setNombre(lista);
                l.setActiva(true);
                listaId = listaService.crear(l, empresaId).getId();
                listas.put(llave(lista), listaId);
            }
            String llavePrecio = listaId != null
                    ? llavePrecio(listaId, productoId, presentacionId != null ? presentacionId : 0)
                    : "nueva:" + llave(lista) + "|" + productoId + "|" + presentacionId;
            if (!enArchivo.add(llavePrecio)) {
                error(r, f.getFila(), etiqueta, "Ese precio está repetido en el archivo");
                continue;
            }
            Long existente = listaId != null ? precios.get(llavePrecio) : null;

            if (grabar) {
                if (existente != null) {
                    UpdateProductoPrecioDto u = new UpdateProductoPrecioDto();
                    u.setPrecio(f.getPrecio());
                    u.setUtilidadEsperada(f.getUtilidadEsperada());
                    precioService.actualizar(existente, u, empresaId);
                } else {
                    CreateProductoPrecioDto c = new CreateProductoPrecioDto();
                    c.setListaPrecioId(listaId);
                    c.setProductoId(presentacionId == null ? productoId : null);
                    c.setProductoPresentacionId(presentacionId);
                    c.setPrecio(f.getPrecio());
                    c.setUtilidadEsperada(f.getUtilidadEsperada());
                    precioService.crear(c, empresaId);
                }
            }
            if (existente != null) {
                fila(r, f.getFila(), ACTUALIZA, etiqueta, "Se cambia el precio");
                r.setActualizados(r.getActualizados() + 1);
            } else {
                fila(r, f.getFila(), NUEVO, etiqueta, nota);
                r.setNuevos(r.getNuevos() + 1);
            }
        }
        r.setConfirmado(grabar);
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Presentaciones
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarPresentaciones(Integer empresaId, Presentaciones req) {
        return presentaciones(empresaId, req, false);
    }

    @Transactional
    public Resultado confirmarPresentaciones(Integer empresaId, Presentaciones req) {
        return confirmar(presentaciones(empresaId, req, false), () -> presentaciones(empresaId, req, true));
    }

    private Resultado presentaciones(Integer empresaId, Presentaciones req, boolean grabar) {
        Resultado r = new Resultado();
        Productos productos = productos(empresaId);
        Map<String, Long> existentes = query.presentaciones(empresaId);
        Map<String, long[]> presPorBarras = query.presentacionesPorCodigoBarras(empresaId);
        Set<String> enArchivo = new HashSet<>();
        Set<String> barrasArchivo = new HashSet<>();

        for (FilaPresentacion f : req.getFilas()) {
            String codigo = texto(f.getProducto());
            String nombre = texto(f.getNombre());
            String barras = texto(f.getCodigoBarras());
            String etiqueta = (codigo != null ? codigo : "") + (nombre != null ? " · " + nombre : "");
            if (codigo == null || nombre == null) {
                error(r, f.getFila(), etiqueta, codigo == null ? "Falta el código del producto" : "Falta el nombre de la presentación");
                continue;
            }
            Long productoId = productos.buscar(codigo);
            if (productoId == null) {
                error(r, f.getFila(), etiqueta, "No hay un producto con el código " + codigo);
                continue;
            }
            if (!enArchivo.add(productoId + "|" + llave(nombre))) {
                error(r, f.getFila(), etiqueta, "La presentación está repetida en el archivo");
                continue;
            }
            Long id = existentes.get(productoId + "|" + llave(nombre));
            if (id == null && (f.getContiene() == null || f.getContiene().signum() <= 0)) {
                error(r, f.getFila(), etiqueta, "Indique cuántas unidades contiene (mayor que cero)");
                continue;
            }
            if (f.getContiene() != null && f.getContiene().signum() <= 0) {
                error(r, f.getFila(), etiqueta, "Lo que contiene debe ser mayor que cero");
                continue;
            }
            if (neg(f.getPrecio()) || neg(f.getCosto())) {
                error(r, f.getFila(), etiqueta, "El precio y el costo no pueden ser negativos");
                continue;
            }
            if (barras != null) {
                long[] dueno = presPorBarras.get(llave(barras));
                if ((dueno != null && !Long.valueOf(dueno[0]).equals(id)) || !barrasArchivo.add(llave(barras))) {
                    error(r, f.getFila(), etiqueta, "El código de barras " + barras + " ya está en uso");
                    continue;
                }
            }

            if (grabar) {
                if (id != null) {
                    UpdateProductoPresentacionDto u = new UpdateProductoPresentacionDto();
                    u.setNombre(nombre);
                    u.setCodigoBarras(barras);
                    u.setFactorConversion(f.getContiene());
                    u.setPrecio(f.getPrecio());
                    u.setCosto(f.getCosto());
                    u.setSeVende(f.getSeVende());
                    u.setEsDefaultVenta(f.getDefaultVenta());
                    u.setEsDefaultCompra(f.getDefaultCompra());
                    u.setActivo(f.getActivo());
                    presentacionService.actualizar(id, u, empresaId);
                } else {
                    CreateProductoPresentacionDto c = new CreateProductoPresentacionDto();
                    c.setProductoId(productoId);
                    c.setNombre(nombre);
                    c.setCodigoBarras(barras);
                    c.setFactorConversion(f.getContiene());
                    c.setPrecio(f.getPrecio());
                    c.setCosto(f.getCosto());
                    c.setSeVende(f.getSeVende() == null || f.getSeVende());
                    c.setEsDefaultVenta(Boolean.TRUE.equals(f.getDefaultVenta()));
                    c.setEsDefaultCompra(Boolean.TRUE.equals(f.getDefaultCompra()));
                    c.setActivo(f.getActivo() == null || f.getActivo());
                    presentacionService.crear(c, empresaId);
                }
            }
            if (id != null) {
                fila(r, f.getFila(), ACTUALIZA, etiqueta, "Se actualiza");
                r.setActualizados(r.getActualizados() + 1);
            } else {
                fila(r, f.getFila(), NUEVO, etiqueta, "Contiene " + f.getContiene().stripTrailingZeros().toPlainString());
                r.setNuevos(r.getNuevos() + 1);
            }
        }
        r.setConfirmado(grabar);
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Recetas
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarRecetas(Integer empresaId, Recetas req) {
        return recetas(empresaId, req, false);
    }

    @Transactional
    public Resultado confirmarRecetas(Integer empresaId, Recetas req) {
        return confirmar(recetas(empresaId, req, false), () -> recetas(empresaId, req, true));
    }

    /** Las filas de un mismo producto arman su receta y la reemplazan completa. */
    private Resultado recetas(Integer empresaId, Recetas req, boolean grabar) {
        Resultado r = new Resultado();
        Productos productos = productos(empresaId);
        Map<String, Long> presentaciones = query.presentaciones(empresaId);
        Set<Long> conReceta = query.conReceta(empresaId);

        Map<Long, List<FilaReceta>> porProducto = new LinkedHashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        for (FilaReceta f : req.getFilas()) {
            String padre = texto(f.getProducto());
            Long id = productos.buscar(padre);
            if (padre == null) {
                error(r, f.getFila(), "", "Falta el código del producto");
            } else if (id == null) {
                error(r, f.getFila(), padre, "No hay un producto con el código " + padre);
            } else {
                porProducto.computeIfAbsent(id, k -> new ArrayList<>()).add(f);
                nombres.putIfAbsent(id, padre);
            }
        }

        for (Map.Entry<Long, List<FilaReceta>> e : porProducto.entrySet()) {
            Long padreId = e.getKey();
            String padre = nombres.get(padreId);
            List<FilaReceta> filas = e.getValue();
            String tipo = filas.stream().map(f -> texto(f.getTipo())).filter(t -> t != null).findFirst()
                    .map(t -> t.toUpperCase()).orElse("RECETA");
            BigDecimal rendimiento = filas.stream().map(FilaReceta::getRendimiento).filter(v -> v != null)
                    .findFirst().orElse(null);
            boolean bien = true;
            if (!tipo.equals("RECETA") && !tipo.equals("KIT")) {
                error(r, filas.get(0).getFila(), padre, "Tipo no válido: " + tipo + " (use RECETA o KIT)");
                bien = false;
            }
            if (rendimiento != null && rendimiento.signum() <= 0) {
                error(r, filas.get(0).getFila(), padre, "El rendimiento debe ser mayor que cero");
                bien = false;
            }

            List<RecetaComponenteDto> componentes = new ArrayList<>();
            Set<Long> vistos = new HashSet<>();
            int orden = 1;
            for (FilaReceta f : filas) {
                String comp = texto(f.getComponente());
                String etiqueta = padre + " ← " + (comp != null ? comp : "");
                Long hijoId = productos.buscar(comp);
                if (comp == null || hijoId == null) {
                    error(r, f.getFila(), etiqueta, comp == null ? "Falta el componente" : "No hay un producto con el código " + comp);
                    bien = false;
                    continue;
                }
                if (hijoId.equals(padreId)) {
                    error(r, f.getFila(), etiqueta, "Un producto no puede ser componente de sí mismo");
                    bien = false;
                    continue;
                }
                if (!vistos.add(hijoId)) {
                    error(r, f.getFila(), etiqueta, "El componente está repetido en la receta");
                    bien = false;
                    continue;
                }
                if (f.getCantidad() == null || f.getCantidad().signum() <= 0) {
                    error(r, f.getFila(), etiqueta, "La cantidad debe ser mayor que cero");
                    bien = false;
                    continue;
                }
                if (f.getMerma() != null && (f.getMerma().signum() < 0 || f.getMerma().compareTo(new BigDecimal("99.99")) > 0)) {
                    error(r, f.getFila(), etiqueta, "La merma va de 0 a 99,99 %");
                    bien = false;
                    continue;
                }
                Long presId = null;
                String pres = texto(f.getPresentacion());
                if (pres != null) {
                    presId = presentaciones.get(hijoId + "|" + llave(pres));
                    if (presId == null) {
                        error(r, f.getFila(), etiqueta, "El componente no tiene la presentación '" + pres + "'");
                        bien = false;
                        continue;
                    }
                }
                RecetaComponenteDto c = new RecetaComponenteDto();
                c.setProductoHijoId(hijoId);
                c.setCantidadReceta(f.getCantidad());
                c.setProductoPresentacionId(presId);
                c.setMermaPorcentaje(f.getMerma());
                c.setOrden(orden++);
                c.setNota(texto(f.getNota()));
                componentes.add(c);
            }
            if (!bien) continue;

            if (grabar) {
                GuardarRecetaDto dto = new GuardarRecetaDto();
                dto.setTipo(tipo);
                dto.setRendimiento(rendimiento);
                dto.setComponentes(componentes);
                composicionService.guardarReceta(padreId, dto, empresaId);
            }
            boolean reemplaza = conReceta.contains(padreId);
            String mensaje = componentes.size() + " componente(s)"
                    + (rendimiento != null ? ", rinde " + rendimiento.stripTrailingZeros().toPlainString() : "")
                    + (reemplaza ? ": reemplaza la receta que tenía" : "");
            fila(r, filas.get(0).getFila(), reemplaza ? ACTUALIZA : NUEVO, padre + " (" + tipo.toLowerCase() + ")", mensaje);
            if (reemplaza) r.setActualizados(r.getActualizados() + 1);
            else r.setNuevos(r.getNuevos() + 1);
        }
        r.setConfirmado(grabar);
        return r;
    }
}
