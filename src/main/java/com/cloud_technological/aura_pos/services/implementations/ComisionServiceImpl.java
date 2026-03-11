package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.comision.ComisionConfigDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionConfigTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionLiquidacionDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionLiquidacionTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionVentaDto;
import com.cloud_technological.aura_pos.dto.comision.CreateComisionConfigDto;
import com.cloud_technological.aura_pos.dto.comision.CreateLiquidacionDto;
import com.cloud_technological.aura_pos.dto.comision.TecnicoDto;
import com.cloud_technological.aura_pos.entity.ComisionConfigEntity;
import com.cloud_technological.aura_pos.entity.ComisionLiquidacionEntity;
import com.cloud_technological.aura_pos.entity.ComisionVentaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.repositories.comision.ComisionConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.comision.ComisionLiquidacionJPARepository;
import com.cloud_technological.aura_pos.repositories.comision.ComisionQueryRepository;
import com.cloud_technological.aura_pos.repositories.comision.ComisionVentaJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.ComisionService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ComisionServiceImpl implements ComisionService {

    @Autowired private ComisionConfigJPARepository configRepo;
    @Autowired private ComisionVentaJPARepository comisionVentaRepo;
    @Autowired private ComisionLiquidacionJPARepository liquidacionRepo;
    @Autowired private ComisionQueryRepository queryRepo;
    @Autowired private ProductoJPARepository productoRepo;
    @Autowired private UsuarioJPARepository usuarioRepo;
    @Autowired private EmpresaJPARepository empresaRepo;

    // ── Técnicos ──────────────────────────────────────────────

    @Override
    public List<TecnicoDto> listarTecnicos(Integer empresaId) {
        return queryRepo.listarTecnicos(empresaId);
    }

    // ── Configuración ─────────────────────────────────────────

    @Override
    public PageImpl<ComisionConfigTableDto> listarConfig(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepo.listarConfig(pageable, empresaId);
    }

    @Override
    public ComisionConfigDto obtenerConfigPorId(Long id, Integer empresaId) {
        ComisionConfigEntity entity = configRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Configuración no encontrada"));
        return toConfigDto(entity);
    }

    @Override
    @Transactional
    public ComisionConfigDto crearConfig(CreateComisionConfigDto dto, Integer empresaId) {
        validarPorcentajes(dto.getPorcentajeTecnico(), dto.getPorcentajeNegocio());

        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        ProductoEntity producto = productoRepo.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!"SERVICIO".equals(producto.getTipoProducto())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden configurar comisiones para productos de tipo SERVICIO");
        }

        ComisionConfigEntity entity = new ComisionConfigEntity();
        entity.setEmpresa(empresa);
        entity.setProducto(producto);
        entity.setTipo(dto.getTipo());
        entity.setPorcentajeTecnico(dto.getPorcentajeTecnico());
        entity.setPorcentajeNegocio(dto.getPorcentajeNegocio());
        entity.setActivo(dto.getActivo() != null ? dto.getActivo() : true);

        if (dto.getTecnicoId() != null) {
            UsuarioEntity tecnico = usuarioRepo.findById(dto.getTecnicoId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Técnico no encontrado"));
            entity.setTecnico(tecnico);
        }

        return toConfigDto(configRepo.save(entity));
    }

    @Override
    @Transactional
    public ComisionConfigDto actualizarConfig(Long id, CreateComisionConfigDto dto, Integer empresaId) {
        validarPorcentajes(dto.getPorcentajeTecnico(), dto.getPorcentajeNegocio());

        ComisionConfigEntity entity = configRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Configuración no encontrada"));

        ProductoEntity producto = productoRepo.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!"SERVICIO".equals(producto.getTipoProducto())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden configurar comisiones para productos de tipo SERVICIO");
        }

        entity.setProducto(producto);
        entity.setTipo(dto.getTipo());
        entity.setPorcentajeTecnico(dto.getPorcentajeTecnico());
        entity.setPorcentajeNegocio(dto.getPorcentajeNegocio());

        if (dto.getActivo() != null) {
            entity.setActivo(dto.getActivo());
        }

        if (dto.getTecnicoId() != null) {
            UsuarioEntity tecnico = usuarioRepo.findById(dto.getTecnicoId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Técnico no encontrado"));
            entity.setTecnico(tecnico);
        } else {
            entity.setTecnico(null);
        }

        return toConfigDto(configRepo.save(entity));
    }

    @Override
    @Transactional
    public void toggleConfig(Long id, Integer empresaId) {
        ComisionConfigEntity entity = configRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Configuración no encontrada"));
        entity.setActivo(!entity.getActivo());
        configRepo.save(entity);
    }

    // ── Liquidaciones ─────────────────────────────────────────

    @Override
    public PageImpl<ComisionLiquidacionTableDto> listarLiquidaciones(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepo.listarLiquidaciones(pageable, empresaId);
    }

    @Override
    public ComisionLiquidacionDto obtenerLiquidacionPorId(Long id, Integer empresaId) {
        ComisionLiquidacionEntity entity = liquidacionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Liquidación no encontrada"));

        ComisionLiquidacionDto dto = toLiquidacionDto(entity);
        dto.setDetalles(queryRepo.listarDetallesLiquidacion(id));
        return dto;
    }

    @Override
    @Transactional
    public ComisionLiquidacionDto crearLiquidacion(CreateLiquidacionDto dto, Integer empresaId) {
        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        UsuarioEntity tecnico = usuarioRepo.findById(dto.getTecnicoId())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Técnico no encontrado"));

        // Validar que pertenece a la empresa
        if (!tecnico.getEmpresa().getId().equals(empresaId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El técnico no pertenece a esta empresa");
        }

        List<ComisionVentaEntity> pendientes = comisionVentaRepo
                .findPendientesByTecnicoIdAndEmpresaId(dto.getTecnicoId(), empresaId);

        if (pendientes.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El técnico no tiene comisiones pendientes de liquidar");
        }

        BigDecimal totalTecnico = pendientes.stream()
                .map(ComisionVentaEntity::getValorTecnico)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Crear liquidación
        ComisionLiquidacionEntity liquidacion = new ComisionLiquidacionEntity();
        liquidacion.setEmpresa(empresa);
        liquidacion.setTecnico(tecnico);
        liquidacion.setFechaDesde(LocalDate.parse(dto.getFechaDesde()));
        liquidacion.setFechaHasta(LocalDate.parse(dto.getFechaHasta()));
        liquidacion.setTotalServicios(pendientes.size());
        liquidacion.setValorTotal(totalTecnico.setScale(2, RoundingMode.HALF_UP));
        liquidacion.setEstado("PENDIENTE");
        liquidacion.setObservaciones(dto.getObservaciones());
        liquidacion = liquidacionRepo.save(liquidacion);

        // Asociar las comisiones a esta liquidación
        for (ComisionVentaEntity cv : pendientes) {
            cv.setLiquidacion(liquidacion);
        }
        comisionVentaRepo.saveAll(pendientes);

        return obtenerLiquidacionPorId(liquidacion.getId(), empresaId);
    }

    @Override
    @Transactional
    public void marcarPagada(Long id, String fechaPago, Integer empresaId) {
        ComisionLiquidacionEntity entity = liquidacionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Liquidación no encontrada"));

        if ("PAGADA".equals(entity.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La liquidación ya fue marcada como pagada");
        }

        entity.setEstado("PAGADA");
        entity.setFechaPago(LocalDate.parse(fechaPago));
        liquidacionRepo.save(entity);
    }

    // ── Pendientes ────────────────────────────────────────────

    @Override
    public List<ComisionVentaDto> listarPendientesTecnico(Integer tecnicoId, Integer empresaId) {
        return queryRepo.listarPendientesTecnico(tecnicoId, empresaId);
    }

    // ── Hook desde VentaService ───────────────────────────────

    @Override
    public void procesarComisionVenta(VentaDetalleEntity detalle, Integer empresaId) {
        ProductoEntity producto = detalle.getProducto();

        // Solo aplica a SERVICIO
        if (!"SERVICIO".equals(producto.getTipoProducto())) return;

        Optional<ComisionConfigEntity> configOpt = configRepo
                .findByProductoIdAndEmpresaIdAndActivoTrue(producto.getId(), empresaId);

        // Sin config activa → 100% negocio, no se registra nada
        if (configOpt.isEmpty()) return;

        ComisionConfigEntity config = configOpt.get();

        // valorBase = precio_unitario × cantidad (sin impuestos ni descuentos — base del servicio)
        BigDecimal valorBase = detalle.getPrecioUnitario()
                .multiply(detalle.getCantidad())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorTecnico = valorBase
                .multiply(config.getPorcentajeTecnico())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal valorNegocio = valorBase
                .subtract(valorTecnico)
                .setScale(2, RoundingMode.HALF_UP);

        ComisionVentaEntity cv = new ComisionVentaEntity();
        cv.setEmpresa(detalle.getVenta().getEmpresa());
        cv.setVenta(detalle.getVenta());
        cv.setVentaDetalle(detalle);
        cv.setProducto(producto);
        cv.setTecnico(config.getTecnico()); // puede ser null si se asigna en caja
        cv.setValorTotal(valorBase);
        cv.setPorcentajeTecnico(config.getPorcentajeTecnico());
        cv.setPorcentajeNegocio(config.getPorcentajeNegocio());
        cv.setValorTecnico(valorTecnico);
        cv.setValorNegocio(valorNegocio);
        // liquidacion_id queda NULL → pendiente

        comisionVentaRepo.save(cv);
    }

    // ── Mappers internos ──────────────────────────────────────

    private ComisionConfigDto toConfigDto(ComisionConfigEntity e) {
        ComisionConfigDto dto = new ComisionConfigDto();
        dto.setId(e.getId());
        dto.setEmpresaId(e.getEmpresa().getId());
        dto.setProductoId(e.getProducto().getId());
        dto.setProductoNombre(e.getProducto().getNombre());
        dto.setTipo(e.getTipo());
        dto.setPorcentajeTecnico(e.getPorcentajeTecnico());
        dto.setPorcentajeNegocio(e.getPorcentajeNegocio());
        dto.setActivo(e.getActivo());
        if (e.getTecnico() != null) {
            dto.setTecnicoId(e.getTecnico().getId());
            dto.setTecnicoNombre(resolverNombreTecnico(e.getTecnico()));
        }
        return dto;
    }

    private ComisionLiquidacionDto toLiquidacionDto(ComisionLiquidacionEntity e) {
        ComisionLiquidacionDto dto = new ComisionLiquidacionDto();
        dto.setId(e.getId());
        dto.setEmpresaId(e.getEmpresa().getId());
        dto.setTecnicoId(e.getTecnico().getId());
        dto.setTecnicoNombre(resolverNombreTecnico(e.getTecnico()));
        dto.setFechaDesde(e.getFechaDesde() != null ? e.getFechaDesde().toString() : null);
        dto.setFechaHasta(e.getFechaHasta() != null ? e.getFechaHasta().toString() : null);
        dto.setTotalServicios(e.getTotalServicios());
        dto.setValorTotal(e.getValorTotal());
        dto.setEstado(e.getEstado());
        dto.setObservaciones(e.getObservaciones());
        dto.setFechaPago(e.getFechaPago() != null ? e.getFechaPago().toString() : null);
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }

    private String resolverNombreTecnico(UsuarioEntity u) {
        if (u.getTercero() == null) return u.getUsername();
        String nombres = u.getTercero().getNombres() != null ? u.getTercero().getNombres() : "";
        String apellidos = u.getTercero().getApellidos() != null ? u.getTercero().getApellidos() : "";
        return (nombres + " " + apellidos).trim();
    }

    private void validarPorcentajes(BigDecimal tecnico, BigDecimal negocio) {
        if (tecnico == null || negocio == null) return;
        BigDecimal suma = tecnico.add(negocio).setScale(2, RoundingMode.HALF_UP);
        if (suma.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Los porcentajes deben sumar exactamente 100. Suma actual: " + suma);
        }
    }
}
