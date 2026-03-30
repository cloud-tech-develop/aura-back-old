package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.compras.CompraDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDetalleDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.CreateOrdenCompraDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.CreateOrdenCompraDetalleDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.OrdenCompraDetalleDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.OrdenCompraDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.OrdenCompraTableDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.RecepcionLineaDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.RecepcionOrdenDto;
import com.cloud_technological.aura_pos.entity.OrdenCompraDetalleEntity;
import com.cloud_technological.aura_pos.entity.OrdenCompraEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.repositories.ordenes_compra.OrdenCompraDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.ordenes_compra.OrdenCompraJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.services.CompraService;
import com.cloud_technological.aura_pos.services.OrdenCompraService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import jakarta.transaction.Transactional;

@Service
public class OrdenCompraServiceImpl implements OrdenCompraService {

    private final OrdenCompraJPARepository ordenRepo;
    private final OrdenCompraDetalleJPARepository detalleRepo;
    private final TerceroJPARepository terceroRepo;
    private final SucursalJPARepository sucursalRepo;
    private final ProductoJPARepository productoRepo;
    private final CompraService compraService;

    @Autowired
    public OrdenCompraServiceImpl(OrdenCompraJPARepository ordenRepo,
            OrdenCompraDetalleJPARepository detalleRepo,
            TerceroJPARepository terceroRepo,
            SucursalJPARepository sucursalRepo,
            ProductoJPARepository productoRepo,
            CompraService compraService) {
        this.ordenRepo = ordenRepo;
        this.detalleRepo = detalleRepo;
        this.terceroRepo = terceroRepo;
        this.sucursalRepo = sucursalRepo;
        this.productoRepo = productoRepo;
        this.compraService = compraService;
    }

    // ── Número correlativo OC-YYYY-NNN ──────────────────────────────

    private String generarNumeroOrden(Integer empresaId) {
        int year = Year.now().getValue();
        long count = ordenRepo.countByEmpresaIdAndYear(empresaId, year);
        return String.format("OC-%d-%03d", year, count + 1);
    }

    // ── Listar ───────────────────────────────────────────────────────

    @Override
    public List<OrdenCompraTableDto> listar(Integer empresaId) {
        return ordenRepo.findByEmpresaIdOrderByCreatedAtDesc(empresaId)
                .stream()
                .map(o -> {
                    String proveedorNombre = terceroRepo.findById(o.getProveedorId().intValue())
                            .map(this::resolverNombreTercero).orElse("-");
                    String sucursalNombre = sucursalRepo.findById(o.getSucursalId())
                            .map(SucursalEntity::getNombre).orElse("-");
                    return OrdenCompraTableDto.builder()
                            .id(o.getId())
                            .numeroOrden(o.getNumeroOrden())
                            .estado(o.getEstado())
                            .proveedorNombre(proveedorNombre)
                            .sucursalNombre(sucursalNombre)
                            .fecha(o.getFecha())
                            .fechaEntregaEsperada(o.getFechaEntregaEsperada())
                            .total(o.getTotal())
                            .compraId(o.getCompraId())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Obtener por ID ───────────────────────────────────────────────

    @Override
    public OrdenCompraDto obtenerPorId(Long id, Integer empresaId) {
        OrdenCompraEntity orden = ordenRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Orden de compra no encontrada"));

        return toDto(orden);
    }

    // ── Crear ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrdenCompraDto crear(CreateOrdenCompraDto dto, Integer empresaId, Long usuarioId) {
        TerceroEntity proveedor = terceroRepo.findByIdAndEmpresaId(dto.getProveedorId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado"));

        sucursalRepo.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        OrdenCompraEntity orden = OrdenCompraEntity.builder()
                .empresaId(empresaId)
                .sucursalId(dto.getSucursalId())
                .proveedorId(dto.getProveedorId())
                .usuarioId(usuarioId != null ? usuarioId.intValue() : null)
                .numeroOrden(generarNumeroOrden(empresaId))
                .estado("BORRADOR")
                .fecha(dto.getFecha() != null ? dto.getFecha() : LocalDate.now())
                .fechaEntregaEsperada(dto.getFechaEntregaEsperada())
                .observaciones(dto.getObservaciones())
                .total(BigDecimal.ZERO)
                .build();

        orden = ordenRepo.save(orden);

        BigDecimal total = guardarDetalles(orden.getId(), dto.getDetalles(), empresaId);
        orden.setTotal(total);
        orden = ordenRepo.save(orden);

        return toDto(orden);
    }

    // ── Actualizar (solo BORRADOR) ───────────────────────────────────

    @Override
    @Transactional
    public OrdenCompraDto actualizar(Long id, CreateOrdenCompraDto dto, Integer empresaId) {
        OrdenCompraEntity orden = ordenRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Orden de compra no encontrada"));

        if (!"BORRADOR".equals(orden.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Solo se pueden editar órdenes en estado BORRADOR");
        }

        terceroRepo.findByIdAndEmpresaId(dto.getProveedorId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado"));

        orden.setProveedorId(dto.getProveedorId());
        orden.setSucursalId(dto.getSucursalId());
        orden.setFecha(dto.getFecha() != null ? dto.getFecha() : orden.getFecha());
        orden.setFechaEntregaEsperada(dto.getFechaEntregaEsperada());
        orden.setObservaciones(dto.getObservaciones());

        // Reemplazar detalles
        detalleRepo.deleteAll(detalleRepo.findByOrdenCompraId(id));
        BigDecimal total = guardarDetalles(id, dto.getDetalles(), empresaId);
        orden.setTotal(total);
        orden = ordenRepo.save(orden);

        return toDto(orden);
    }

    // ── Transiciones de estado ───────────────────────────────────────

    @Override
    @Transactional
    public OrdenCompraDto enviar(Long id, Integer empresaId) {
        OrdenCompraEntity orden = getOrdenOrThrow(id, empresaId);
        if (!"BORRADOR".equals(orden.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "La orden debe estar en BORRADOR para enviarse");
        orden.setEstado("ENVIADA");
        return toDto(ordenRepo.save(orden));
    }

    @Override
    @Transactional
    public OrdenCompraDto confirmar(Long id, Integer empresaId) {
        OrdenCompraEntity orden = getOrdenOrThrow(id, empresaId);
        if (!"ENVIADA".equals(orden.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "La orden debe estar ENVIADA para confirmarse");
        orden.setEstado("CONFIRMADA");
        return toDto(ordenRepo.save(orden));
    }

    // ── Recepción de mercancía ────────────────────────────────────────
    // Solo actualiza cantidades recibidas y estado de la OC.
    // El frontend se encarga de abrir el formulario de compras pre-llenado.

    @Override
    @Transactional
    public OrdenCompraDto recibirMercancia(Long id, RecepcionOrdenDto dto, Integer empresaId) {
        OrdenCompraEntity orden = getOrdenOrThrow(id, empresaId);

        if (!"CONFIRMADA".equals(orden.getEstado()) && !"RECIBIDA_PARCIAL".equals(orden.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La orden debe estar CONFIRMADA o RECIBIDA_PARCIAL para recibir mercancía");
        }

        List<OrdenCompraDetalleEntity> detalles = detalleRepo.findByOrdenCompraId(id);
        Map<Long, OrdenCompraDetalleEntity> detalleMap = detalles.stream()
                .collect(Collectors.toMap(OrdenCompraDetalleEntity::getId, Function.identity()));

        for (RecepcionLineaDto linea : dto.getLineas()) {
            OrdenCompraDetalleEntity detalle = detalleMap.get(linea.getDetalleId());
            if (detalle == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Detalle no encontrado: " + linea.getDetalleId());

            BigDecimal pendiente = detalle.getCantidad().subtract(detalle.getCantidadRecibida());
            if (linea.getCantidadRecibida().compareTo(pendiente) > 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Cantidad supera la pendiente para: " + detalle.getProductoNombre());

            if (linea.getCantidadRecibida().compareTo(BigDecimal.ZERO) > 0) {
                detalle.setCantidadRecibida(detalle.getCantidadRecibida().add(linea.getCantidadRecibida()));
                detalleRepo.save(detalle);
            }
        }

        // Determinar nuevo estado
        boolean todoRecibido = detalleRepo.findByOrdenCompraId(id).stream()
                .allMatch(d -> d.getCantidadRecibida().compareTo(d.getCantidad()) >= 0);

        orden.setEstado(todoRecibido ? "CERRADA" : "RECIBIDA_PARCIAL");
        ordenRepo.save(orden);

        return toDto(orden);
    }

    // ── Anular ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        OrdenCompraEntity orden = getOrdenOrThrow(id, empresaId);
        if ("CERRADA".equals(orden.getEstado()) || "ANULADA".equals(orden.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No se puede anular una orden CERRADA o ya ANULADA");
        orden.setEstado("ANULADA");
        ordenRepo.save(orden);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private OrdenCompraEntity getOrdenOrThrow(Long id, Integer empresaId) {
        return ordenRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Orden de compra no encontrada"));
    }

    private String resolverNombreTercero(TerceroEntity t) {
        if (t == null) return "-";
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) return t.getRazonSocial();
        String nombre = (t.getNombres() != null ? t.getNombres() : "")
                + (t.getApellidos() != null ? " " + t.getApellidos() : "");
        return nombre.isBlank() ? "-" : nombre.trim();
    }

    private BigDecimal guardarDetalles(Long ordenId, List<CreateOrdenCompraDetalleDto> items, Integer empresaId) {
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrdenCompraDetalleDto item : items) {
            String nombreProducto = productoRepo.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .map(p -> p.getNombre())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            BigDecimal subtotal = item.getCostoUnitario().multiply(item.getCantidad());
            OrdenCompraDetalleEntity detalle = OrdenCompraDetalleEntity.builder()
                    .ordenCompraId(ordenId)
                    .productoId(item.getProductoId())
                    .productoNombre(nombreProducto)
                    .cantidad(item.getCantidad())
                    .costoUnitario(item.getCostoUnitario())
                    .subtotalLinea(subtotal)
                    .build();
            detalleRepo.save(detalle);
            total = total.add(subtotal);
        }
        return total;
    }

    private OrdenCompraDto toDto(OrdenCompraEntity o) {
        String proveedorNombre = terceroRepo.findById(o.getProveedorId().intValue())
                .map(this::resolverNombreTercero).orElse("-");
        String sucursalNombre = sucursalRepo.findById(o.getSucursalId())
                .map(SucursalEntity::getNombre).orElse("-");

        List<OrdenCompraDetalleEntity> detalles = detalleRepo.findByOrdenCompraId(o.getId());
        List<OrdenCompraDetalleDto> detallesDto = detalles.stream().map(d -> {
            BigDecimal pendiente = d.getCantidad().subtract(d.getCantidadRecibida());
            return OrdenCompraDetalleDto.builder()
                    .id(d.getId())
                    .productoId(d.getProductoId())
                    .productoNombre(d.getProductoNombre())
                    .cantidad(d.getCantidad())
                    .cantidadRecibida(d.getCantidadRecibida())
                    .cantidadPendiente(pendiente)
                    .costoUnitario(d.getCostoUnitario())
                    .subtotalLinea(d.getSubtotalLinea())
                    .build();
        }).collect(Collectors.toList());

        return OrdenCompraDto.builder()
                .id(o.getId())
                .numeroOrden(o.getNumeroOrden())
                .estado(o.getEstado())
                .proveedorId(o.getProveedorId())
                .proveedorNombre(proveedorNombre)
                .sucursalId(o.getSucursalId())
                .sucursalNombre(sucursalNombre)
                .fecha(o.getFecha())
                .fechaEntregaEsperada(o.getFechaEntregaEsperada())
                .observaciones(o.getObservaciones())
                .total(o.getTotal())
                .compraId(o.getCompraId())
                .detalles(detallesDto)
                .build();
    }
}
