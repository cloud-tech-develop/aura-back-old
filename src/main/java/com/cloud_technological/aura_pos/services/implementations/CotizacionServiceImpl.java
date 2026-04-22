package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionTableDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CreateCotizacionDetalleDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CreateCotizacionDto;
import com.cloud_technological.aura_pos.entity.CotizacionDetalleEntity;
import com.cloud_technological.aura_pos.entity.CotizacionEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.mappers.CotizacionDetalleMapper;
import com.cloud_technological.aura_pos.mappers.CotizacionMapper;
import com.cloud_technological.aura_pos.repositories.cotizaciones.CotizacionDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.cotizaciones.CotizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.cotizaciones.CotizacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.CotizacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class CotizacionServiceImpl implements CotizacionService {

    private final CotizacionQueryRepository cotizacionRepository;
    private final CotizacionJPARepository cotizacionJPARepository;
    private final CotizacionDetalleJPARepository detalleJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final TerceroJPARepository terceroJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final CotizacionMapper cotizacionMapper;
    private final CotizacionDetalleMapper detalleMapper;

    @Autowired
    public CotizacionServiceImpl(CotizacionQueryRepository cotizacionRepository,
            CotizacionJPARepository cotizacionJPARepository,
            CotizacionDetalleJPARepository detalleJPARepository,
            ProductoJPARepository productoJPARepository,
            TerceroJPARepository terceroJPARepository,
            EmpresaJPARepository empresaRepository,
            CotizacionMapper cotizacionMapper,
            CotizacionDetalleMapper detalleMapper) {
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionJPARepository = cotizacionJPARepository;
        this.detalleJPARepository = detalleJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.terceroJPARepository = terceroJPARepository;
        this.empresaRepository = empresaRepository;
        this.cotizacionMapper = cotizacionMapper;
        this.detalleMapper = detalleMapper;
    }

    @Override
    public PageImpl<CotizacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return cotizacionRepository.listar(pageable, empresaId);
    }

    @Override
    public CotizacionDto obtenerPorId(Long id, Integer empresaId) {
        CotizacionEntity entity = cotizacionJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));

        CotizacionDto dto = cotizacionMapper.toDto(entity);
        dto.setDetalles(cotizacionRepository.obtenerDetalles(entity.getId()));
        return dto;
    }

    @Override
    @Transactional
    public CotizacionDto crear(CreateCotizacionDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        TerceroEntity tercero = null;
        if (dto.getTerceroId() != null) {
            tercero = terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        }

        Long consecutivo = cotizacionRepository.obtenerSiguienteConsecutivo(empresaId);
        String numero = String.format("COT-%04d", consecutivo);

        int diasVigencia = dto.getDiasVigencia() != null ? dto.getDiasVigencia() : 3;
        LocalDate fechaVencimiento = LocalDate.now().plusDays(diasVigencia);

        CotizacionEntity cotizacion = new CotizacionEntity();
        cotizacion.setEmpresa(empresa);
        cotizacion.setTercero(tercero);
        cotizacion.setTurnoCajaId(dto.getTurnoCajaId());
        cotizacion.setNumero(numero);
        cotizacion.setFecha(LocalDate.now());
        cotizacion.setFechaVencimiento(fechaVencimiento);
        cotizacion.setDiasVigencia(diasVigencia);
        cotizacion.setObservaciones(dto.getObservaciones());
        cotizacion.setEstado("PENDIENTE");
        cotizacion.setCreatedAt(LocalDateTime.now());

        cotizacion = cotizacionJPARepository.save(cotizacion);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal ivaTotal = BigDecimal.ZERO;
        BigDecimal descuentoTotal = BigDecimal.ZERO;

        for (CreateCotizacionDetalleDto item : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            BigDecimal baseNeta = item.getPrecioUnitario()
                    .multiply(item.getCantidad())
                    .subtract(item.getDescuentoValor())
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal ivaPorcentaje = item.getIvaPorcentaje() != null ? item.getIvaPorcentaje() : BigDecimal.ZERO;
            BigDecimal ivaLinea = baseNeta
                    .multiply(ivaPorcentaje)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal subtotalLinea = baseNeta.add(ivaLinea).setScale(2, RoundingMode.HALF_UP);

            CotizacionDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setCotizacion(cotizacion);
            detalle.setProducto(producto);
            detalle.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : producto.getNombre());
            detalle.setIvaPorcentaje(ivaPorcentaje);
            detalle.setSubtotal(subtotalLinea);
            detalleJPARepository.save(detalle);

            subtotal = subtotal.add(baseNeta);
            ivaTotal = ivaTotal.add(ivaLinea);
            descuentoTotal = descuentoTotal.add(item.getDescuentoValor());
        }

        cotizacion.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        cotizacion.setIva(ivaTotal.setScale(2, RoundingMode.HALF_UP));
        cotizacion.setDescuento(descuentoTotal.setScale(2, RoundingMode.HALF_UP));
        cotizacion.setTotal(subtotal.add(ivaTotal).setScale(2, RoundingMode.HALF_UP));
        cotizacionJPARepository.save(cotizacion);

        CotizacionDto result = cotizacionMapper.toDto(cotizacion);
        result.setDetalles(cotizacionRepository.obtenerDetalles(cotizacion.getId()));
        return result;
    }

    @Override
    @Transactional
    public CotizacionDto actualizar(Long id, CreateCotizacionDto dto, Integer empresaId) {
        CotizacionEntity cotizacion = cotizacionJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));

        if (!"PENDIENTE".equals(cotizacion.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden editar cotizaciones en estado PENDIENTE");
        }

        // Actualizar tercero si viene
        if (dto.getTerceroId() != null) {
            TerceroEntity tercero = terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
            cotizacion.setTercero(tercero);
        } else {
            cotizacion.setTercero(null);
        }

        // Actualizar observaciones y días de vigencia
        cotizacion.setObservaciones(dto.getObservaciones());
        int diasVigencia = dto.getDiasVigencia() != null ? dto.getDiasVigencia() : 3;
        cotizacion.setDiasVigencia(diasVigencia);
        cotizacion.setFechaVencimiento(LocalDate.now().plusDays(diasVigencia));

        // Eliminar detalles existentes
        detalleJPARepository.deleteByCotizacionId(cotizacion.getId());

        // Recalcular totales
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal ivaTotal = BigDecimal.ZERO;
        BigDecimal descuentoTotal = BigDecimal.ZERO;

        for (CreateCotizacionDetalleDto item : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            BigDecimal baseNeta = item.getPrecioUnitario()
                    .multiply(item.getCantidad())
                    .subtract(item.getDescuentoValor() != null ? item.getDescuentoValor() : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal ivaPorcentaje = item.getIvaPorcentaje() != null ? item.getIvaPorcentaje() : BigDecimal.ZERO;
            BigDecimal ivaLinea = baseNeta
                    .multiply(ivaPorcentaje)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal subtotalLinea = baseNeta.add(ivaLinea).setScale(2, RoundingMode.HALF_UP);

            CotizacionDetalleEntity detalle = new CotizacionDetalleEntity();
            detalle.setCotizacion(cotizacion);
            detalle.setProducto(producto);
            detalle.setDescripcion(item.getDescripcion() != null ? item.getDescripcion() : producto.getNombre());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(item.getPrecioUnitario());
            detalle.setIvaPorcentaje(ivaPorcentaje);
            detalle.setDescuentoValor(item.getDescuentoValor() != null ? item.getDescuentoValor() : BigDecimal.ZERO);
            detalle.setSubtotal(subtotalLinea);
            detalleJPARepository.save(detalle);

            subtotal = subtotal.add(baseNeta);
            ivaTotal = ivaTotal.add(ivaLinea);
            descuentoTotal = descuentoTotal.add(item.getDescuentoValor() != null ? item.getDescuentoValor() : BigDecimal.ZERO);
        }

        cotizacion.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        cotizacion.setIva(ivaTotal.setScale(2, RoundingMode.HALF_UP));
        cotizacion.setDescuento(descuentoTotal.setScale(2, RoundingMode.HALF_UP));
        cotizacion.setTotal(subtotal.add(ivaTotal).setScale(2, RoundingMode.HALF_UP));
        cotizacionJPARepository.save(cotizacion);

        CotizacionDto result = cotizacionMapper.toDto(cotizacion);
        result.setDetalles(cotizacionRepository.obtenerDetalles(cotizacion.getId()));
        return result;
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        CotizacionEntity entity = cotizacionJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));

        if ("CONVERTIDA".equals(entity.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede anular una cotización ya convertida a venta");
        }

        entity.setEstado("ANULADA");
        cotizacionJPARepository.save(entity);
    }

    @Override
    public CotizacionDto convertirAVenta(Long id, Integer empresaId) {
        CotizacionEntity entity = cotizacionJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));

        if (!"PENDIENTE".equals(entity.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden convertir cotizaciones en estado PENDIENTE");
        }

        CotizacionDto dto = cotizacionMapper.toDto(entity);
        dto.setDetalles(cotizacionRepository.obtenerDetalles(entity.getId()));
        return dto;
    }

    @Override
    @Transactional
    public void vencerCotizacionesExpiradas() {
        List<CotizacionEntity> expiradas = cotizacionJPARepository.findAll().stream()
                .filter(c -> "PENDIENTE".equals(c.getEstado())
                        && c.getFechaVencimiento() != null
                        && c.getFechaVencimiento().isBefore(LocalDate.now()))
                .toList();

        for (CotizacionEntity c : expiradas) {
            c.setEstado("VENCIDA");
        }
        cotizacionJPARepository.saveAll(expiradas);
    }
}