package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.productos.CambioUnidadPreviewDto;
import com.cloud_technological.aura_pos.dto.productos.CambioUnidadRequestDto;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.CambioUnidadProductoRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PresentacionConversion;

/**
 * "Pasar a unidad": un producto cargado con la unidad grande como base (ARROZ
 * base PACA, con la presentación UNIDAD que contiene 0,04) pasa a contarse en la
 * pequeña. Inventario, lotes y kardex se multiplican por N; costos se dividen;
 * la base vieja queda como presentación "PACA contiene N" y se lleva el código
 * de barras del producto.
 *
 * Venta y devolución guardan la presentación y se reapuntan. Compra, merma,
 * obsequio y traslado no la guardan: sus documentos anteriores al cambio no se
 * pueden anular ni editar para ese producto ({@link #validarDocumentoPrevio}).
 */
@Service
public class CambioUnidadProductoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProductoJPARepository productoJPARepository;
    private final ProductoPresentacionJPARepository presentacionJPARepository;
    private final CambioUnidadProductoRepository repo;

    public CambioUnidadProductoService(ProductoJPARepository productoJPARepository,
            ProductoPresentacionJPARepository presentacionJPARepository,
            CambioUnidadProductoRepository repo) {
        this.productoJPARepository = productoJPARepository;
        this.presentacionJPARepository = presentacionJPARepository;
        this.repo = repo;
    }

    private record Contexto(ProductoEntity producto, ProductoPresentacionEntity pequena, BigDecimal n) {
    }

    public CambioUnidadPreviewDto preview(Long productoId, Long presentacionId, Integer empresaId) {
        return armarPreview(cargar(productoId, presentacionId, empresaId));
    }

    @Transactional
    public CambioUnidadPreviewDto aplicar(Long productoId, CambioUnidadRequestDto dto, Integer empresaId,
            Long usuarioId) {
        Contexto ctx = cargar(productoId, dto.getPresentacionId(), empresaId);
        CambioUnidadPreviewDto preview = armarPreview(ctx);
        if (!preview.isPuedeAplicar())
            throw new GlobalException(HttpStatus.BAD_REQUEST, String.join(" ", preview.getBloqueos()));
        if (!repo.existeUnidadMedida(dto.getUnidadMedidaId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Unidad de medida no encontrada o inactiva");

        String nombreGrande = dto.getNombrePresentacion().trim();
        if (nombreGrande.length() > 100)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre de la presentación admite 100 caracteres");

        ProductoEntity producto = ctx.producto();
        ProductoPresentacionEntity pequena = ctx.pequena();
        BigDecimal n = ctx.n();
        Long unidadAnteriorId = producto.getUnidadMedidaBase() != null ? producto.getUnidadMedidaBase().getId() : null;
        Map<String, Long> ultimos = repo.ultimosDocumentos();

        // Antes de crear la grande: las demás presentaciones estaban en la base vieja.
        repo.multiplicarFactorOtrasPresentaciones(producto.getId(), pequena.getId(), n);
        repo.desmarcarDefaultCompra(producto.getId());

        ProductoPresentacionEntity grande = new ProductoPresentacionEntity();
        grande.setProducto(producto);
        grande.setNombre(nombreGrande);
        grande.setCodigoBarras(sinBlancos(producto.getCodigoBarras()));
        grande.setFactorConversion(n);
        grande.setPrecio(cero(producto.getPrecio()));
        grande.setCosto(cero(producto.getCosto()));
        grande.setEsDefaultCompra(true);
        grande.setEsDefaultVenta(false);
        grande.setActivo(true);
        grande = presentacionJPARepository.saveAndFlush(grande);

        repo.retirarPresentacionPequena(pequena.getId());
        repo.actualizarProducto(producto.getId(), dto.getUnidadMedidaId(), sinBlancos(pequena.getCodigoBarras()),
                preview.getPrecioDespues(), preview.getCostoDespues(), n);
        repo.convertirInventario(producto.getId(), n);
        repo.convertirLotes(producto.getId(), n);
        repo.convertirKardex(producto.getId(), n);
        repo.reapuntarLineasVenta(producto.getId(), pequena.getId(), grande.getId());
        repo.reapuntarPreciosLista(producto.getId(), pequena.getId(), grande.getId());
        repo.convertirRecetas(producto.getId(), pequena.getId(), grande.getId(), unidadAnteriorId,
                dto.getUnidadMedidaId(), n);
        repo.registrarCambio(empresaId, producto.getId(), n, unidadAnteriorId, dto.getUnidadMedidaId(),
                grande.getId(), pequena.getId(), ultimos, usuarioId);

        return preview;
    }

    /**
     * Bloquea anular o editar un documento sin presentación hecho antes de que
     * alguno de sus productos cambiara de unidad: sus cantidades están en la
     * unidad vieja y revertirlas movería N veces menos.
     *
     * @param documento COMPRA, MERMA, OBSEQUIO, CONSUMO_INTERNO o TRASLADO
     * @param accion    lo que se intenta, para el mensaje ("anular la compra")
     */
    public void validarDocumentoPrevio(String documento, Long documentoId, Collection<Long> productoIds,
            String accion) {
        if (documentoId == null || productoIds == null || productoIds.isEmpty())
            return;
        repo.cambioPosteriorA(documento, documentoId, productoIds).ifPresent(c -> {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede " + accion + ": el producto '" + c.productoNombre()
                            + "' cambió de unidad base el " + c.fecha().format(FECHA)
                            + " y este documento es anterior, con cantidades en la unidad vieja. "
                            + "Si hace falta, corrige el inventario con un reconteo.");
        });
    }

    // ── Privados ──────────────────────────────────────────────────────────

    private Contexto cargar(Long productoId, Long presentacionId, Integer empresaId) {
        if (presentacionId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indica la presentación que pasará a ser la unidad base");

        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(productoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        ProductoPresentacionEntity pequena = presentacionJPARepository
                .findByIdAndProductoEmpresaId(presentacionId, empresaId)
                .filter(pp -> pp.getProducto().getId().equals(producto.getId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La presentación no pertenece a este producto"));

        return new Contexto(producto, pequena, PresentacionConversion.reciprocoEntero(pequena.getFactorConversion()));
    }

    private CambioUnidadPreviewDto armarPreview(Contexto ctx) {
        ProductoEntity producto = ctx.producto();
        ProductoPresentacionEntity pequena = ctx.pequena();
        BigDecimal n = ctx.n() != null ? ctx.n() : BigDecimal.ONE;
        String unidadActual = producto.getUnidadMedidaBase() != null
                ? producto.getUnidadMedidaBase().getNombre()
                : "unidad actual";

        CambioUnidadPreviewDto dto = new CambioUnidadPreviewDto();
        dto.setProductoId(producto.getId());
        dto.setProductoNombre(producto.getNombre());
        dto.setPresentacionId(pequena.getId());
        dto.setPresentacionNombre(pequena.getNombre());
        dto.setFactor(n);
        dto.setUnidadActualId(producto.getUnidadMedidaBase() != null ? producto.getUnidadMedidaBase().getId() : null);
        dto.setUnidadActualNombre(unidadActual);
        dto.setUnidadSugeridaId(repo.unidadSugerida(pequena.getNombre()));
        dto.setNombrePresentacionSugerido(unidadActual + " x " + n.stripTrailingZeros().toPlainString());

        dto.setPrecioAntes(cero(producto.getPrecio()));
        dto.setPrecioDespues(pequena.getPrecio() != null && pequena.getPrecio().signum() > 0
                ? pequena.getPrecio().setScale(2, RoundingMode.HALF_UP)
                : cero(producto.getPrecio()).divide(n, 2, RoundingMode.HALF_UP));
        dto.setCostoAntes(cero(producto.getCosto()));
        dto.setCostoDespues(cero(producto.getCosto()).divide(n, 2, RoundingMode.HALF_UP));

        dto.setStock(repo.stockPorSucursal(producto.getId()));
        dto.getStock().forEach(s -> s.setDespues(cero(s.getAntes()).multiply(n)));

        Map<String, Integer> conteos = repo.conteos(producto.getId(), pequena.getId());
        dto.setMovimientosKardex(conteos.getOrDefault("movimientos", 0));
        dto.setLotes(conteos.getOrDefault("lotes", 0));
        dto.setLineasVenta(conteos.getOrDefault("lineas_venta", 0));
        dto.setRecetas(conteos.getOrDefault("recetas", 0));
        dto.setPreciosLista(conteos.getOrDefault("precios_lista", 0));

        if (!Boolean.TRUE.equals(pequena.getActivo()))
            dto.getBloqueos().add("La presentación está inactiva.");
        if (ctx.n() == null || ctx.n().compareTo(BigDecimal.ONE) <= 0)
            dto.getBloqueos().add("Solo se puede pasar a una presentación más pequeña que la base y que quepa "
                    + "un número exacto de veces (por ejemplo, 25 unidades en una paca).");
        if (repo.esPadreDeReceta(producto.getId()))
            dto.getBloqueos().add("El producto tiene receta: lo que se descuenta son sus componentes, "
                    + "no su propio inventario.");
        int reconteos = repo.reconteosAbiertos(producto.getId());
        if (reconteos > 0)
            dto.getBloqueos().add("Hay " + reconteos + " reconteo(s) sin cerrar con este producto: "
                    + "apruébalos o anúlalos primero.");

        int pendientes = repo.documentosPendientes(producto.getId());
        if (pendientes > 0)
            dto.getAvisos().add("Hay " + pendientes + " cotización(es), pedido(s) u orden(es) de compra abiertas "
                    + "con este producto: sus cantidades siguen en " + unidadActual + ". Revísalas antes de usarlas.");
        int preciosDinamicos = repo.preciosDinamicosDePresentacion(pequena.getId());
        if (preciosDinamicos > 0)
            dto.getAvisos().add(preciosDinamicos + " precio(s) por volumen o por cliente de \"" + pequena.getNombre()
                    + "\" dejarán de aplicar: vuelve a crearlos después del cambio.");
        dto.getAvisos().add("Las compras, mermas, obsequios, consumos internos y traslados anteriores al cambio ya no se podrán "
                + "anular ni editar para este producto.");

        return dto;
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static String sinBlancos(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
