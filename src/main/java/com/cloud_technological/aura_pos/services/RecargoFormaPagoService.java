package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaPagoDto;
import com.cloud_technological.aura_pos.entity.FormaPagoContableEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.FormaPagoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.RecargoFormaPagoQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.unidad_medida.UnidadMedidaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Recargo por forma de pago (V188): Sistecrédito, ADDI… se cobran con un
 * porcentaje que paga el cliente.
 *
 * Antes de procesar la venta, cada pago hecho con una forma que tiene recargo
 * sube ese porcentaje y el recargo entra como una línea de servicio
 * ("Recargo por forma de pago", SKU {@value #SKU}). Así queda en el total, en
 * la factura electrónica y en el ingreso del asiento sin tocar esos cálculos.
 *
 * Lo decide el servidor, no el POS: el POS solo lo muestra.
 */
@Service
@RequiredArgsConstructor
public class RecargoFormaPagoService {

    public static final String SKU = "RECARGO-FP";

    private final FormaPagoContableJPARepository formaPagoRepo;
    private final RecargoFormaPagoQueryRepository queryRepo;
    private final ProductoJPARepository productoRepo;
    private final EmpresaJPARepository empresaRepo;
    private final UnidadMedidaJPARepository unidadRepo;

    /** Recargo de un monto: monto × porcentaje, redondeado al peso. */
    public static BigDecimal recargo(BigDecimal monto, BigDecimal porcentaje) {
        if (monto == null || porcentaje == null || porcentaje.signum() <= 0 || monto.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return monto.multiply(porcentaje).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    /**
     * Sube los pagos con recargo y agrega una línea por cada forma de pago
     * con recargo. No hace nada si la empresa no tiene formas con recargo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void aplicar(CreateVentaDto dto, Integer empresaId) {
        if (dto.getPagos() == null || dto.getPagos().isEmpty()) return;

        Map<String, FormaPagoContableEntity> conRecargo = formaPagoRepo.findByEmpresaIdOrderByNombreAsc(empresaId)
                .stream()
                .filter(f -> Boolean.TRUE.equals(f.getActivo())
                        && f.getRecargoPorcentaje() != null
                        && f.getRecargoPorcentaje().signum() > 0)
                .collect(Collectors.toMap(f -> f.getCodigo().toUpperCase(), f -> f, (a, b) -> a));
        if (conRecargo.isEmpty()) return;

        List<CreateVentaDetalleDto> lineas = new ArrayList<>();
        Long productoId = null;
        for (CreateVentaPagoDto pago : dto.getPagos()) {
            if (pago.getMetodoPago() == null) continue;
            FormaPagoContableEntity forma = conRecargo.get(pago.getMetodoPago().toUpperCase());
            if (forma == null) continue;
            BigDecimal valor = recargo(pago.getMonto(), forma.getRecargoPorcentaje());
            if (valor.signum() <= 0) continue;

            if (productoId == null) productoId = productoRecargo(empresaId);
            pago.setMonto(pago.getMonto().add(valor));

            CreateVentaDetalleDto linea = new CreateVentaDetalleDto();
            linea.setProductoId(productoId);
            linea.setCantidad(BigDecimal.ONE);
            linea.setPrecioUnitario(valor);
            linea.setDescuentoValor(BigDecimal.ZERO);
            linea.setImpuestoValor(BigDecimal.ZERO);
            lineas.add(linea);
        }
        if (lineas.isEmpty()) return;

        List<CreateVentaDetalleDto> detalles = new ArrayList<>(dto.getDetalles() != null ? dto.getDetalles() : List.of());
        detalles.addAll(lineas);
        dto.setDetalles(detalles);
    }

    /** El servicio que lleva el recargo; se crea la primera vez, oculto en el POS. */
    private Long productoRecargo(Integer empresaId) {
        Long id = queryRepo.productoRecargoId(empresaId, SKU);
        if (id != null) return id;

        Long unidadId = queryRepo.unidadServicioId();
        if (unidadId == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay unidades de medida activas para crear el servicio del recargo");
        }
        ProductoEntity p = new ProductoEntity();
        p.setEmpresa(empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada")));
        p.setUnidadMedidaBase(unidadRepo.findById(unidadId).orElseThrow());
        p.setSku(SKU);
        p.setNombre("Recargo por forma de pago");
        p.setDescripcion("Lo agrega la venta cuando se paga con una forma que tiene recargo (Sistecrédito, ADDI…).");
        p.setTipoProducto("SERVICIO");
        p.setUsoProducto("VENTA");
        p.setClasificacion("SERVICIO");
        p.setManejaInventario(false);
        p.setManejaLotes(false);
        p.setManejaSerial(false);
        p.setPermitirStockNegativo(true);
        p.setCosto(BigDecimal.ZERO);
        p.setPrecio(BigDecimal.ZERO);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setIvaIncluido(false);
        p.setActivo(true);
        p.setVisibleEnPos(false);
        p.setVendePorUnidad(true);
        p.setCreatedAt(LocalDateTime.now());
        return productoRepo.save(p).getId();
    }
}
