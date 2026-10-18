package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.productos.CompraProductoHistorialDto;
import com.cloud_technological.aura_pos.dto.productos.HistorialCostoProductoDto;
import com.cloud_technological.aura_pos.entity.ProductoCostoHistorialEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.productos.ProductoCostoHistorialJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoCostoHistorialQueryRepository;
import com.cloud_technological.aura_pos.services.CostoHistorialService;
import com.cloud_technological.aura_pos.services.OrigenCosto;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Registra y consulta el historial del costo de los productos. Cada fila guarda
 * el costo antes y después, el stock y el documento que lo movió.
 */
@Service
public class CostoHistorialServiceImpl implements CostoHistorialService {

    private static final int ESCALA = 6;
    private static final String ESTADO_ANULADA = "ANULADA";
    private static final String TIPO_NOTA_CREDITO = "NOTA_CREDITO";

    private final ProductoCostoHistorialJPARepository jpaRepo;
    private final ProductoCostoHistorialQueryRepository queryRepo;
    private final SecurityUtils securityUtils;

    public CostoHistorialServiceImpl(ProductoCostoHistorialJPARepository jpaRepo,
            ProductoCostoHistorialQueryRepository queryRepo, SecurityUtils securityUtils) {
        this.jpaRepo = jpaRepo;
        this.queryRepo = queryRepo;
        this.securityUtils = securityUtils;
    }

    @Override
    public void registrarMovimiento(ProductoEntity producto, OrigenCosto origen, BigDecimal cantidad,
            BigDecimal valor, BigDecimal stockDespues, BigDecimal costoAnterior) {
        if (producto == null || origen == null) return;
        ProductoCostoHistorialEntity h = base(producto, costoAnterior, origen);
        h.setCantidad(cantidad);
        if (cantidad != null && cantidad.signum() != 0 && valor != null) {
            h.setCostoEntrada(valor.divide(cantidad, ESCALA, RoundingMode.HALF_UP));
        }
        h.setStockDespues(stockDespues);
        if (stockDespues != null && cantidad != null) h.setStockAntes(stockDespues.subtract(cantidad));
        jpaRepo.save(h);
    }

    @Override
    public void registrarCambio(ProductoEntity producto, BigDecimal costoAnterior, OrigenCosto origen) {
        if (producto == null || origen == null) return;
        if (mismoValor(costoAnterior, producto.getCosto())) return;
        jpaRepo.save(base(producto, costoAnterior, origen));
    }

    @Override
    public HistorialCostoProductoDto obtener(Long productoId, Integer empresaId) {
        HistorialCostoProductoDto dto = queryRepo.cabecera(productoId, empresaId);
        if (dto == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        dto.setCambios(queryRepo.cambios(productoId, empresaId));
        List<CompraProductoHistorialDto> compras = queryRepo.compras(productoId, empresaId);
        dto.setCompras(compras);
        resumirCompras(dto, compras);
        return dto;
    }

    /** Último, mínimo y máximo costo neto de las compras vigentes (sin anuladas ni notas crédito). */
    private static void resumirCompras(HistorialCostoProductoDto dto, List<CompraProductoHistorialDto> compras) {
        List<BigDecimal> costos = compras.stream()
                .filter(c -> !ESTADO_ANULADA.equalsIgnoreCase(c.getEstado()))
                .filter(c -> !TIPO_NOTA_CREDITO.equalsIgnoreCase(c.getTipoDocumento()))
                .map(CompraProductoHistorialDto::getCostoNeto)
                .filter(Objects::nonNull)
                .toList();
        if (costos.isEmpty()) return;
        dto.setUltimoCostoCompra(costos.get(0));
        dto.setCostoCompraMinimo(costos.stream().min(BigDecimal::compareTo).orElse(null));
        dto.setCostoCompraMaximo(costos.stream().max(BigDecimal::compareTo).orElse(null));
    }

    private ProductoCostoHistorialEntity base(ProductoEntity producto, BigDecimal costoAnterior, OrigenCosto origen) {
        ProductoCostoHistorialEntity h = new ProductoCostoHistorialEntity();
        h.setEmpresaId(producto.getEmpresa().getId());
        h.setProductoId(producto.getId());
        h.setOrigen(origen.tipo());
        h.setDocumentoId(origen.documentoId());
        h.setDocumentoNumero(origen.documentoNumero());
        h.setTerceroId(origen.terceroId());
        h.setObservacion(recortar(origen.observacion()));
        h.setCostoAnterior(costoAnterior);
        h.setCostoNuevo(producto.getCosto() != null ? producto.getCosto() : BigDecimal.ZERO);
        h.setUsuarioId(securityUtils.getUsuarioId());
        return h;
    }

    private static boolean mismoValor(BigDecimal a, BigDecimal b) {
        BigDecimal x = a != null ? a : BigDecimal.ZERO;
        BigDecimal y = b != null ? b : BigDecimal.ZERO;
        return x.compareTo(y) == 0;
    }

    private static String recortar(String s) {
        if (s == null) return null;
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
