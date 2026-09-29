package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConceptoConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoTableDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.CreateConsumoInternoDetalleDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.CreateConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.SaveConceptoConsumoInternoDto;
import com.cloud_technological.aura_pos.entity.ConceptoConsumoInternoEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.ConsumoInternoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ConsumoInternoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.repositories.consumo_interno.ConceptoConsumoInternoJPARepository;
import com.cloud_technological.aura_pos.repositories.consumo_interno.ConsumoInternoDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.consumo_interno.ConsumoInternoJPARepository;
import com.cloud_technological.aura_pos.repositories.consumo_interno.ConsumoInternoQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.CambioUnidadProductoService;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;
import com.cloud_technological.aura_pos.services.ConsumoInternoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.PresentacionConversion;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * El negocio usa su propio inventario (aseo, mantenimiento, cafetería).
 * Descarga inventario y kardex como la merma; contabiliza el costo en la
 * cuenta del concepto y, si aplica, causa el IVA por retiro como el obsequio.
 */
@Service
@RequiredArgsConstructor
public class ConsumoInternoServiceImpl implements ConsumoInternoService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final String DOCUMENTO = "CONSUMO_INTERNO";

    /** Lo que reciben las empresas nuevas la primera vez que abren la pantalla (igual que V163). */
    private static final List<String> CONCEPTOS_INICIALES = List.of(
            "Aseo y cafetería", "Papelería y útiles", "Mantenimiento del local",
            "Dotación y consumo del personal", "Exhibición y decoración", "Otro");

    private final ConsumoInternoQueryRepository queryRepository;
    private final ConsumoInternoJPARepository consumoRepository;
    private final ConsumoInternoDetalleJPARepository detalleRepository;
    private final ConceptoConsumoInternoJPARepository conceptoRepository;
    private final PlanCuentaJPARepository planCuentaRepository;
    private final ProductoJPARepository productoRepository;
    private final ProductoPresentacionJPARepository presentacionRepository;
    private final InventarioJPARepository inventarioRepository;
    private final LoteJPARepository loteRepository;
    private final SucursalJPARepository sucursalRepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final TerceroJPARepository terceroRepository;
    private final MovimientoInventarioJPARepository movimientoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ConsumoComposicionService consumoComposicion;
    private final CambioUnidadProductoService cambioUnidadProducto;
    private final com.cloud_technological.aura_pos.services.BodegaService bodegaService;
    private final com.cloud_technological.aura_pos.services.LoteStockService loteStock;
    private final com.cloud_technological.aura_pos.services.SerialStockService serialStock;

    @Override
    public PageImpl<ConsumoInternoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public ConsumoInternoDto obtenerPorId(Long id, Integer empresaId) {
        ConsumoInternoEntity entity = consumoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Consumo interno no encontrado"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public ConsumoInternoDto crear(CreateConsumoInternoDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalRepository
                .findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        UsuarioEntity usuario = usuarioRepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        ConceptoConsumoInternoEntity concepto = conceptoRepository
                .findByIdAndEmpresaId(dto.getConceptoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Concepto no encontrado"));
        if (!Boolean.TRUE.equals(concepto.getActivo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El concepto '" + concepto.getNombre() + "' está inactivo");

        TerceroEntity responsable = null;
        if (dto.getResponsableTerceroId() != null) {
            responsable = terceroRepository.findByIdAndEmpresaId(dto.getResponsableTerceroId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Responsable no encontrado"));
        }

        // Lo que diga el documento; si no dice, lo que traiga el concepto.
        // Usar en el negocio un producto vencido no: la regla de la empresa manda.
        final boolean permitirVencidos = !loteStock.bloqueaVencidos(empresaId);

        boolean generaIva = dto.getGeneraIva() != null
                ? dto.getGeneraIva()
                : Boolean.TRUE.equals(concepto.getGeneraIva());

        ConsumoInternoEntity consumo = new ConsumoInternoEntity();
        consumo.setEmpresa(empresa);
        consumo.setSucursal(sucursal);
        // De qué bodega sale. Sin bodega en el documento, la principal.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(dto.getBodegaId(), sucursal.getId(), empresaId);
        consumo.setBodega(bodega);
        consumo.setUsuario(usuario);
        consumo.setConcepto(concepto);
        consumo.setResponsable(responsable);
        consumo.setFecha(LocalDateTime.now());
        consumo.setObservacion(dto.getObservacion());
        consumo.setGeneraIva(generaIva);
        consumo.setEstado(ConsumoInternoEntity.ESTADO_APROBADO);
        consumo.setCostoTotal(BigDecimal.ZERO);
        consumo.setBaseComercialTotal(BigDecimal.ZERO);
        consumo.setIvaTotal(BigDecimal.ZERO);
        consumo = consumoRepository.save(consumo);

        String referencia = "Consumo interno #" + consumo.getId();
        BigDecimal costoTotal = BigDecimal.ZERO;
        BigDecimal baseTotal = BigDecimal.ZERO;
        BigDecimal ivaTotal = BigDecimal.ZERO;

        for (CreateConsumoInternoDetalleDto item : dto.getDetalles()) {
            if (item.getCantidad() == null || item.getCantidad().signum() <= 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
            }

            ProductoEntity producto = productoRepository
                    .findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            boolean porReceta = consumoComposicion.tieneComposicion(producto.getId());

            // Línea escrita en una presentación (1 Bulto): se pasa a unidad base.
            ProductoPresentacionEntity presentacion =
                    resolverPresentacion(item.getProductoPresentacionId(), producto, empresaId);
            BigDecimal cantidadPresentacion = null;
            BigDecimal basePresentacion = null;
            if (presentacion != null) {
                if (porReceta)
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "'" + producto.getNombre() + "' se descuenta por su receta: regístralo por unidad");
                cantidadPresentacion = item.getCantidad();
                basePresentacion = baseComercialDePresentacion(item, presentacion, producto);
                item.setCantidad(PresentacionConversion.aBase(item.getCantidad(), presentacion));
            }
            BigDecimal cantidad = item.getCantidad();

            List<ConsumoComposicionService.Consumo> componentes = List.of();
            InventarioEntity inventario = null;
            if (porReceta) {
                if (item.getLoteId() != null)
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "'" + producto.getNombre() + "' se descuenta por su receta: no admite lote");
                componentes = consumoComposicion.explotar(
                        producto.getId(), cantidad, bodega.getId());
                consumoComposicion.validarStock(producto, componentes);
            } else {
                inventario = inventarioRepository
                        .findByBodegaIdAndProductoId(bodega.getId(), producto.getId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                                "El producto " + producto.getNombre() + " no tiene inventario en la bodega "
                                + bodega.getNombre()));
                if (!Boolean.TRUE.equals(producto.getPermitirStockNegativo())
                        && inventario.getStockActual().compareTo(cantidad) < 0)
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Stock insuficiente para: " + producto.getNombre()
                                    + ". Disponible: " + inventario.getStockActual().stripTrailingZeros().toPlainString());
            }

            // El costo se congela aquí; con receta se fija después de consumir.
            BigDecimal costoUnitario = !porReceta && producto.getCosto() != null
                    ? producto.getCosto() : BigDecimal.ZERO;
            BigDecimal baseUnitaria = basePresentacion != null
                    ? basePresentacion.multiply(cantidadPresentacion).divide(cantidad, 2, RoundingMode.HALF_UP)
                    : resolverBaseComercial(item, producto);
            BigDecimal tarifaIva = producto.getIvaPorcentaje() != null
                    ? producto.getIvaPorcentaje() : BigDecimal.ZERO;
            BigDecimal ivaLinea = generaIva
                    ? baseUnitaria.multiply(cantidad).multiply(tarifaIva).divide(CIEN, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            ConsumoInternoDetalleEntity detalle = new ConsumoInternoDetalleEntity();
            detalle.setConsumoInterno(consumo);
            detalle.setProducto(producto);
            detalle.setProductoPresentacion(presentacion);
            detalle.setCantidadPresentacion(cantidadPresentacion);
            detalle.setCantidad(cantidad);
            detalle.setCostoUnitario(costoUnitario);
            detalle.setBaseComercialUnitaria(baseUnitaria);
            detalle.setIvaValor(ivaLinea);

            detalle = detalleRepository.save(detalle);

            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotes = porReceta ? List.of()
                    : loteStock.salidaDocumento(com.cloud_technological.aura_pos.services.LoteStockService.CONSUMO_INTERNO, detalle.getId(), producto, bodega,
                            empresaId, cantidad, item.getLoteId(), permitirVencidos,
                            Boolean.TRUE.equals(producto.getPermitirStockNegativo()));
            if (lotes.size() == 1) {
                detalle.setLote(lotes.get(0).lote());
                detalle = detalleRepository.save(detalle);
            }
            if (!porReceta) {
                serialStock.salida(com.cloud_technological.aura_pos.services.SerialStockService.ORIGEN_CONSUMO_INTERNO, detalle.getId(), producto, bodega, empresaId,
                        cantidad, item.getSerialIds(), com.cloud_technological.aura_pos.services.SerialStockService.CONSUMO_INTERNO);
            }

            BigDecimal costoLinea;
            if (porReceta) {
                costoLinea = consumoComposicion.consumir(ConsumoComposicionService.ORIGEN_CONSUMO_INTERNO,
                        detalle.getId(), empresaId, bodega, producto, componentes,
                        TipoMovimientoInventario.CONSUMO_INTERNO.codigo(), referencia);
                detalle.setCostoUnitario(costoLinea.divide(cantidad, 2, RoundingMode.HALF_UP));
                detalleRepository.save(detalle);
            } else {
                BigDecimal saldoAnterior = inventario.getStockActual();
                BigDecimal saldoNuevo = saldoAnterior.subtract(cantidad);
                inventario.setStockActual(saldoNuevo);
                inventario.setUpdatedAt(LocalDateTime.now());
                inventarioRepository.save(inventario);

                final BigDecimal costoKardex = costoUnitario;
                loteStock.kardex(lotes, cantidad.negate(), saldoAnterior,
                        (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, producto, lote, cant, ant, nuevo,
                                costoKardex, TipoMovimientoInventario.CONSUMO_INTERNO.codigo(), referencia));

                costoLinea = cantidad.multiply(costoUnitario);
            }

            costoTotal = costoTotal.add(costoLinea);
            baseTotal = baseTotal.add(cantidad.multiply(baseUnitaria));
            ivaTotal = ivaTotal.add(ivaLinea);
        }

        consumo.setCostoTotal(costoTotal.setScale(2, RoundingMode.HALF_UP));
        consumo.setBaseComercialTotal(baseTotal.setScale(2, RoundingMode.HALF_UP));
        consumo.setIvaTotal(ivaTotal.setScale(2, RoundingMode.HALF_UP));
        consumo = consumoRepository.save(consumo);

        // Sin costo ni IVA no hay asiento: publicar dejaría un fallo por asiento vacío.
        if (consumo.getCostoTotal().signum() > 0 || consumo.getIvaTotal().signum() > 0) {
            eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                    DOCUMENTO, consumo.getId(), empresaId,
                    usuarioId != null ? usuarioId.intValue() : null));
        }

        return obtenerPorId(consumo.getId(), empresaId);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        ConsumoInternoEntity consumo = consumoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Consumo interno no encontrado"));

        if (ConsumoInternoEntity.ESTADO_ANULADO.equals(consumo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El consumo interno ya está anulado");

        List<ConsumoInternoDetalleEntity> detalles = detalleRepository.findByConsumoInternoId(id);
        String referencia = "Anulación Consumo interno #" + consumo.getId();

        // Lo que salió por receta también cuenta: el componente pudo cambiar de unidad.
        List<Long> productos = new ArrayList<>();
        for (ConsumoInternoDetalleEntity d : detalles) {
            productos.add(d.getProducto().getId());
            consumoComposicion.consumosDe(ConsumoComposicionService.ORIGEN_CONSUMO_INTERNO, d.getId())
                    .forEach(c -> productos.add(c.getProductoHijo().getId()));
        }
        cambioUnidadProducto.validarDocumentoPrevio(DOCUMENTO, id, productos, "anular el consumo interno");

        // La bodega del documento: la mercancía vuelve de donde salió.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(consumo.getBodega() != null ? consumo.getBodega().getId() : null,
                        consumo.getSucursal().getId(), empresaId);

        for (ConsumoInternoDetalleEntity detalle : detalles) {
            // Por receta se devuelve lo que se consumió entonces, no lo que diga la receta hoy.
            if (consumoComposicion.revertir(ConsumoComposicionService.ORIGEN_CONSUMO_INTERNO, detalle.getId(),
                    bodega, detalle.getProducto(),
                    TipoMovimientoInventario.ANULACION_CONSUMO_INTERNO.codigo(), referencia)) {
                continue;
            }

            InventarioEntity inventario = inventarioRepository
                    .findByBodegaIdAndProductoId(bodega.getId(), detalle.getProducto().getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario no encontrado para: " + detalle.getProducto().getNombre()));

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(detalle.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inventario);

            serialStock.revertirSalida(com.cloud_technological.aura_pos.services.SerialStockService.ORIGEN_CONSUMO_INTERNO, detalle.getId(), "anular el consumo interno");

            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotes = loteStock.revertirDocumento(com.cloud_technological.aura_pos.services.LoteStockService.CONSUMO_INTERNO,
                    detalle.getId(), true, "anular el consumo interno");

            loteStock.kardex(lotes, detalle.getCantidad(), saldoAnterior,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, detalle.getProducto(),
                            lote, cant, ant, nuevo, detalle.getCostoUnitario(),
                            TipoMovimientoInventario.ANULACION_CONSUMO_INTERNO.codigo(), referencia));
        }

        consumo.setEstado(ConsumoInternoEntity.ESTADO_ANULADO);
        consumoRepository.save(consumo);

        eventPublisher.publishEvent(new ContabilidadReversaEvent(DOCUMENTO, consumo.getId(), empresaId, null));
    }

    // ── Conceptos ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<ConceptoConsumoInternoDto> listarConceptos(Integer empresaId) {
        // Una empresa creada después de V163 no tiene conceptos: se le siembran
        // la primera vez, para que la pantalla nunca arranque vacía.
        if (!conceptoRepository.existsByEmpresaId(empresaId)) {
            for (String nombre : CONCEPTOS_INICIALES) {
                ConceptoConsumoInternoEntity c = new ConceptoConsumoInternoEntity();
                c.setEmpresaId(empresaId);
                c.setNombre(nombre);
                conceptoRepository.save(c);
            }
        }
        return queryRepository.listarConceptos(empresaId);
    }

    @Override
    @Transactional
    public ConceptoConsumoInternoDto guardarConcepto(Long id, SaveConceptoConsumoInternoDto dto, Integer empresaId) {
        String nombre = dto.getNombre().trim();

        ConceptoConsumoInternoEntity concepto;
        if (id == null) {
            if (conceptoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresaId, nombre))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un concepto llamado '" + nombre + "'");
            concepto = new ConceptoConsumoInternoEntity();
            concepto.setEmpresaId(empresaId);
        } else {
            concepto = conceptoRepository.findByIdAndEmpresaId(id, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto no encontrado"));
            if (conceptoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(empresaId, nombre, id))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un concepto llamado '" + nombre + "'");
        }

        if (dto.getCuentaId() != null) {
            validarCuenta(dto.getCuentaId(), empresaId);
        }

        concepto.setNombre(nombre);
        concepto.setCuentaId(dto.getCuentaId());
        concepto.setGeneraIva(!Boolean.FALSE.equals(dto.getGeneraIva()));
        concepto.setActivo(!Boolean.FALSE.equals(dto.getActivo()));
        // saveAndFlush: la respuesta se lee por JDBC y, sin flush, un UPDATE
        // todavía no está en la base y devolvía la cuenta anterior.
        Long guardadoId = conceptoRepository.saveAndFlush(concepto).getId();

        return queryRepository.listarConceptos(empresaId).stream()
                .filter(c -> c.getId().equals(guardadoId))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el concepto"));
    }

    /** Mismos guardarraíles que la configuración contable: activa, de movimiento y de gasto o activo. */
    private void validarCuenta(Long cuentaId, Integer empresaId) {
        PlanCuentaEntity cuenta = planCuentaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta no existe en el plan de cuentas de la empresa"));
        String etiqueta = cuenta.getCodigo() + " - " + cuenta.getNombre();
        if (!Boolean.TRUE.equals(cuenta.getActiva()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta " + etiqueta + " está inactiva");
        if (!Boolean.TRUE.equals(cuenta.getAuxiliar()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + etiqueta + " no es de movimiento. Elige una cuenta auxiliar (último nivel).");
        if (!ConceptoContable.GASTO_CONSUMO_INTERNO.permiteCodigo(cuenta.getCodigo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El consumo interno solo admite cuentas "
                            + ConceptoContable.GASTO_CONSUMO_INTERNO.prefijosLegibles()
                            + "; la cuenta " + etiqueta + " no pertenece a esa clase.");
    }

    // ── Privados ──────────────────────────────────────────────────────────

    private ProductoPresentacionEntity resolverPresentacion(Long presentacionId, ProductoEntity producto,
            Integer empresaId) {
        if (presentacionId == null) return null;
        return presentacionRepository.findByIdAndProductoEmpresaId(presentacionId, empresaId)
                .filter(p -> p.getProducto().getId().equals(producto.getId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La presentación no pertenece a " + producto.getNombre()));
    }

    /** Base comercial de UNA presentación: la enviada o el precio de la presentación sin IVA. */
    private BigDecimal baseComercialDePresentacion(CreateConsumoInternoDetalleDto item,
            ProductoPresentacionEntity presentacion, ProductoEntity producto) {
        if (item.getBaseComercialUnitaria() != null) {
            return item.getBaseComercialUnitaria();
        }
        return sinIva(presentacion.getPrecio(), producto, 6);
    }

    /** Base comercial por unidad base: la enviada o el precio del producto sin IVA. */
    private BigDecimal resolverBaseComercial(CreateConsumoInternoDetalleDto item, ProductoEntity producto) {
        if (item.getBaseComercialUnitaria() != null) {
            return item.getBaseComercialUnitaria().setScale(2, RoundingMode.HALF_UP);
        }
        return sinIva(producto.getPrecio(), producto, 2);
    }

    /** El precio de venta se guarda con IVA incluido: la base es precio ÷ (1 + tarifa). */
    private BigDecimal sinIva(BigDecimal precio, ProductoEntity producto, int escala) {
        BigDecimal valor = precio != null ? precio : BigDecimal.ZERO;
        BigDecimal tarifa = producto.getIvaPorcentaje() != null ? producto.getIvaPorcentaje() : BigDecimal.ZERO;
        if (tarifa.signum() <= 0) {
            return valor.setScale(escala, RoundingMode.HALF_UP);
        }
        return valor.divide(BigDecimal.ONE.add(tarifa.divide(CIEN, 6, RoundingMode.HALF_UP)),
                escala, RoundingMode.HALF_UP);
    }

    private void registrarMovimiento(com.cloud_technological.aura_pos.entity.BodegaEntity bodega, ProductoEntity producto, LoteEntity lote,
            BigDecimal cantidad, BigDecimal saldoAnterior, BigDecimal saldoNuevo, BigDecimal costo,
            String tipo, String referencia) {
        MovimientoInventarioEntity movimiento = new MovimientoInventarioEntity();
        movimiento.setBodega(bodega);
        movimiento.setSucursal(bodega.getSucursal());
        movimiento.setProducto(producto);
        movimiento.setLote(lote);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setSaldoAnterior(saldoAnterior);
        movimiento.setSaldoNuevo(saldoNuevo);
        movimiento.setCostoHistorico(costo);
        movimiento.setReferenciaOrigen(referencia);
        movimiento.setCreatedAt(LocalDateTime.now());
        movimientoRepository.save(movimiento);
    }

    private ConsumoInternoDto toDto(ConsumoInternoEntity entity) {
        ConsumoInternoDto dto = new ConsumoInternoDto();
        dto.setId(entity.getId());
        dto.setFecha(entity.getFecha());
        dto.setObservacion(entity.getObservacion());
        dto.setCostoTotal(entity.getCostoTotal());
        dto.setBaseComercialTotal(entity.getBaseComercialTotal());
        dto.setIvaTotal(entity.getIvaTotal());
        dto.setGeneraIva(entity.getGeneraIva());
        dto.setEstado(entity.getEstado());

        if (entity.getSucursal() != null) {
            dto.setSucursalId(Long.valueOf(entity.getSucursal().getId()));
            dto.setSucursalNombre(entity.getSucursal().getNombre());
        }
        if (entity.getConcepto() != null) {
            dto.setConceptoId(entity.getConcepto().getId());
            dto.setConceptoNombre(entity.getConcepto().getNombre());
        }
        if (entity.getResponsable() != null) {
            dto.setResponsableTerceroId(entity.getResponsable().getId());
            dto.setResponsableNombre(nombreTercero(entity.getResponsable()));
        }
        if (entity.getUsuario() != null) {
            dto.setUsuarioNombre(entity.getUsuario().getUsername());
        }

        dto.setDetalles(queryRepository.obtenerDetalles(entity.getId()));
        return dto;
    }

    private String nombreTercero(TerceroEntity t) {
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) {
            return t.getRazonSocial();
        }
        String nombres = t.getNombres() != null ? t.getNombres() : "";
        String apellidos = t.getApellidos() != null ? t.getApellidos() : "";
        return (nombres + " " + apellidos).trim();
    }
}
