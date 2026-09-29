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
import com.cloud_technological.aura_pos.dto.comision.MarcarPagadaDto;
import com.cloud_technological.aura_pos.dto.comision.TecnicoDto;
import com.cloud_technological.aura_pos.entity.CategoriaEntity;
import com.cloud_technological.aura_pos.entity.ComisionConfigEntity;
import com.cloud_technological.aura_pos.entity.ComisionLiquidacionEntity;
import com.cloud_technological.aura_pos.entity.ComisionVentaEntity;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaJPARepository;
import com.cloud_technological.aura_pos.repositories.comision.ComisionConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.comision.ComisionLiquidacionJPARepository;
import com.cloud_technological.aura_pos.repositories.comision.ComisionQueryRepository;
import com.cloud_technological.aura_pos.repositories.comision.ComisionVentaJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
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
    @Autowired private CategoriaJPARepository categoriaRepo;
    @Autowired private CuentaBancariaJPARepository cuentaBancariaRepo;
    @Autowired private EmpleadoJPARepository empleadoRepo;

    // ── Técnicos y vendedores ─────────────────────────────────

    @Override
    public List<TecnicoDto> listarTecnicos(Integer empresaId) {
        return queryRepo.listarTecnicos(empresaId);
    }

    @Override
    public List<TecnicoDto> listarVendedores(Integer empresaId) {
        return queryRepo.listarVendedores(empresaId);
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
        String modalidad = dto.getModalidad() != null ? dto.getModalidad().toUpperCase() : "SERVICIO";

        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        ComisionConfigEntity entity = new ComisionConfigEntity();
        entity.setEmpresa(empresa);
        entity.setModalidad(modalidad);
        entity.setTipo(dto.getTipo());
        entity.setActivo(dto.getActivo() != null ? dto.getActivo() : true);

        if ("SERVICIO".equals(modalidad)) {
            if (dto.getProductoId() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El producto es obligatorio para comisiones de tipo SERVICIO");
            validarPorcentajes(dto.getPorcentajeTecnico(), dto.getPorcentajeNegocio());
            ProductoEntity producto = productoRepo.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            if (!"SERVICIO".equals(producto.getTipoProducto()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Para comisiones SERVICIO el producto debe ser de tipo SERVICIO");
            entity.setProducto(producto);
            entity.setPorcentajeTecnico(dto.getPorcentajeTecnico());
            entity.setPorcentajeNegocio(dto.getPorcentajeNegocio());

        } else { // VENTA
            if (dto.getProductoId() == null && dto.getCategoriaId() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Debe especificar un producto o una categoría para comisiones de tipo VENTA");
            if (dto.getProductoId() != null) {
                ProductoEntity producto = productoRepo.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
                entity.setProducto(producto);
            }
            if (dto.getCategoriaId() != null) {
                categoriaRepo.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
                entity.setCategoriaId(dto.getCategoriaId());
            }
            entity.setPorcentajeTecnico(dto.getPorcentajeTecnico());
            entity.setPorcentajeNegocio(BigDecimal.ZERO);
        }

        if (dto.getTecnicoId() != null) {
            UsuarioEntity tecnico = usuarioRepo.findById(dto.getTecnicoId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            entity.setTecnico(tecnico);
        }

        return toConfigDto(configRepo.save(entity));
    }

    @Override
    @Transactional
    public ComisionConfigDto actualizarConfig(Long id, CreateComisionConfigDto dto, Integer empresaId) {
        ComisionConfigEntity entity = configRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Configuración no encontrada"));

        String modalidad = entity.getModalidad(); // No cambia la modalidad en edición

        entity.setTipo(dto.getTipo());
        if (dto.getActivo() != null) entity.setActivo(dto.getActivo());

        if ("SERVICIO".equals(modalidad)) {
            validarPorcentajes(dto.getPorcentajeTecnico(), dto.getPorcentajeNegocio());
            if (dto.getProductoId() != null) {
                ProductoEntity producto = productoRepo.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
                if (!"SERVICIO".equals(producto.getTipoProducto()))
                    throw new GlobalException(HttpStatus.BAD_REQUEST, "Para comisiones SERVICIO el producto debe ser de tipo SERVICIO");
                entity.setProducto(producto);
            }
            entity.setPorcentajeTecnico(dto.getPorcentajeTecnico());
            entity.setPorcentajeNegocio(dto.getPorcentajeNegocio());

        } else { // VENTA
            if (dto.getProductoId() != null) {
                ProductoEntity producto = productoRepo.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
                entity.setProducto(producto);
                entity.setCategoriaId(null); // producto toma precedencia
            } else if (dto.getCategoriaId() != null) {
                categoriaRepo.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
                entity.setCategoriaId(dto.getCategoriaId());
                entity.setProducto(null);
            }
            entity.setPorcentajeTecnico(dto.getPorcentajeTecnico());
            entity.setPorcentajeNegocio(BigDecimal.ZERO);
        }

        entity.setTecnico(dto.getTecnicoId() != null
                ? usuarioRepo.findById(dto.getTecnicoId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"))
                : null);

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

        String tipo = dto.getTipo() != null ? dto.getTipo().toUpperCase() : "TECNICO";
        List<ComisionVentaEntity> pendientes;
        ComisionLiquidacionEntity liquidacion = new ComisionLiquidacionEntity();

        boolean seleccionManual = dto.getComisionIds() != null && !dto.getComisionIds().isEmpty();

        if ("VENDEDOR".equals(tipo)) {
            if (dto.getVendedorId() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El vendedorId es requerido para liquidaciones de tipo VENDEDOR");

            EmpleadoEntity vendedor = empleadoRepo.findByIdAndEmpresaId(dto.getVendedorId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Vendedor no encontrado"));

            if (seleccionManual) {
                pendientes = comisionVentaRepo.findAllById(dto.getComisionIds());
                pendientes.removeIf(cv -> cv.getLiquidacion() != null
                        || !cv.getEmpresa().getId().equals(empresaId)
                        || cv.getVendedor() == null
                        || !cv.getVendedor().getId().equals(dto.getVendedorId()));
            } else {
                pendientes = comisionVentaRepo.findPendientesVendedor(dto.getVendedorId(), empresaId);
            }

            if (pendientes.isEmpty())
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El vendedor no tiene comisiones pendientes de liquidar");

            liquidacion.setVendedor(vendedor);

        } else {
            if (dto.getTecnicoId() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El tecnicoId es requerido para liquidaciones de tipo TECNICO");

            UsuarioEntity tecnico = usuarioRepo.findById(dto.getTecnicoId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Técnico no encontrado"));

            if (!tecnico.getEmpresa().getId().equals(empresaId))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El técnico no pertenece a esta empresa");

            if (seleccionManual) {
                pendientes = comisionVentaRepo.findAllById(dto.getComisionIds());
                pendientes.removeIf(cv -> cv.getLiquidacion() != null
                        || !cv.getEmpresa().getId().equals(empresaId)
                        || cv.getTecnico() == null
                        || !cv.getTecnico().getId().equals(dto.getTecnicoId()));
            } else {
                pendientes = comisionVentaRepo.findPendientesTecnico(dto.getTecnicoId(), empresaId);
            }

            if (pendientes.isEmpty())
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El técnico no tiene comisiones pendientes de liquidar");

            liquidacion.setTecnico(tecnico);
        }

        BigDecimal total = pendientes.stream()
                .map(ComisionVentaEntity::getValorTecnico)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        liquidacion.setEmpresa(empresa);
        liquidacion.setFechaDesde(LocalDate.parse(dto.getFechaDesde()));
        liquidacion.setFechaHasta(LocalDate.parse(dto.getFechaHasta()));
        liquidacion.setTotalServicios(pendientes.size());
        liquidacion.setValorTotal(total.setScale(2, RoundingMode.HALF_UP));
        liquidacion.setEstado("PENDIENTE");
        liquidacion.setTipo(tipo);
        liquidacion.setObservaciones(dto.getObservaciones());
        liquidacion = liquidacionRepo.save(liquidacion);

        for (ComisionVentaEntity cv : pendientes) {
            cv.setLiquidacion(liquidacion);
        }
        comisionVentaRepo.saveAll(pendientes);

        return obtenerLiquidacionPorId(liquidacion.getId(), empresaId);
    }

    @Override
    @Transactional
    public void marcarPagada(Long id, MarcarPagadaDto dto, Integer empresaId) {
        ComisionLiquidacionEntity entity = liquidacionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Liquidación no encontrada"));

        if ("PAGADA".equals(entity.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La liquidación ya fue marcada como pagada");
        }

        // Descontar del banco si aplica
        boolean requiereCuenta = dto.getMetodoPago() != null && !dto.getMetodoPago().equalsIgnoreCase("EFECTIVO");
        if (requiereCuenta) {
            if (dto.getCuentaBancariaId() == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Debe seleccionar la cuenta bancaria para este método de pago");
            }
            CuentaBancariaEntity cuenta = cuentaBancariaRepo
                    .findByIdAndEmpresaId(dto.getCuentaBancariaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta bancaria no encontrada"));
            cuenta.setSaldoActual(cuenta.getSaldoActual().subtract(entity.getValorTotal()));
            cuentaBancariaRepo.save(cuenta);
        }

        entity.setEstado("PAGADA");
        entity.setFechaPago(LocalDate.parse(dto.getFechaPago()));
        entity.setMetodoPago(dto.getMetodoPago());
        entity.setCuentaBancariaId(dto.getCuentaBancariaId());
        liquidacionRepo.save(entity);
    }

    // ── Pendientes ────────────────────────────────────────────

    @Override
    public List<ComisionVentaDto> listarPendientesTecnico(Integer tecnicoId, Integer empresaId, String modalidad,
            String fechaDesde, String fechaHasta) {
        return queryRepo.listarPendientesTecnico(tecnicoId, empresaId, modalidad, fechaDesde, fechaHasta);
    }

    @Override
    public List<ComisionVentaDto> listarPendientesVendedor(Long vendedorId, Integer empresaId,
            String fechaDesde, String fechaHasta) {
        return queryRepo.listarPendientesVendedor(vendedorId, empresaId, fechaDesde, fechaHasta);
    }

    // ── Hook desde VentaService ───────────────────────────────

    @Override
    public void procesarComisionVenta(VentaDetalleEntity detalle, Integer empresaId) {
        ProductoEntity producto = detalle.getProducto();

        // 1. Comisión SERVICIO (taller): aplica al técnico configurado, no al cajero
        if ("SERVICIO".equals(producto.getTipoProducto())) {
            configRepo.findByProductoIdAndEmpresaIdAndModalidadAndActivoTrue(
                    producto.getId(), empresaId, "SERVICIO")
                .ifPresent(config -> registrarComisionServicio(detalle, config));
        }

        // 2. Comisión VENTA (vendedor): aplica al usuario logueado si NO es CAJERO
        //    Prioridad: config de producto > config de categoría
        UsuarioEntity vendedor = detalle.getVenta().getUsuario();
        if ("CAJERO".equalsIgnoreCase(vendedor.getRol())) return; // cajeros no generan comisión VENTA

        Optional<ComisionConfigEntity> ventaConfig =
            configRepo.findFirstByProductoIdAndEmpresaIdAndModalidadAndActivoTrue(
                producto.getId(), empresaId, "VENTA");

        if (ventaConfig.isEmpty() && producto.getCategoria() != null) {
            ventaConfig = configRepo.findFirstByCategoriaIdAndEmpresaIdAndModalidadAndActivoTrue(
                producto.getCategoria().getId(), empresaId, "VENTA");
        }

        ventaConfig.ifPresent(config -> registrarComisionVenta(detalle, config, vendedor));
    }

    /** Comisión SERVICIO: técnico cobra su %, el negocio el resto */
    private void registrarComisionServicio(VentaDetalleEntity detalle, ComisionConfigEntity config) {
        BigDecimal valorBase = detalle.getSubtotalLinea() != null
                ? detalle.getSubtotalLinea()
                : detalle.getPrecioUnitario().multiply(detalle.getCantidad()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorTecnico = valorBase
                .multiply(config.getPorcentajeTecnico())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        ComisionVentaEntity cv = new ComisionVentaEntity();
        cv.setEmpresa(detalle.getVenta().getEmpresa());
        cv.setVenta(detalle.getVenta());
        cv.setVentaDetalle(detalle);
        cv.setProducto(detalle.getProducto());
        cv.setTecnico(config.getTecnico()); // técnico configurado (se asigna en caja)
        cv.setModalidad("SERVICIO");
        cv.setValorTotal(valorBase);
        cv.setPorcentajeTecnico(config.getPorcentajeTecnico());
        cv.setPorcentajeNegocio(config.getPorcentajeNegocio());
        cv.setValorTecnico(valorTecnico);
        cv.setValorNegocio(valorBase.subtract(valorTecnico).setScale(2, RoundingMode.HALF_UP));
        comisionVentaRepo.save(cv);
    }

    /** Comisión VENTA: el vendedor logueado cobra su % sobre la venta */
    private void registrarComisionVenta(VentaDetalleEntity detalle, ComisionConfigEntity config, UsuarioEntity vendedor) {
        BigDecimal valorBase = detalle.getSubtotalLinea() != null
                ? detalle.getSubtotalLinea()
                : detalle.getPrecioUnitario().multiply(detalle.getCantidad()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorVendedor = valorBase
                .multiply(config.getPorcentajeTecnico())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        ComisionVentaEntity cv = new ComisionVentaEntity();
        cv.setEmpresa(detalle.getVenta().getEmpresa());
        cv.setVenta(detalle.getVenta());
        cv.setVentaDetalle(detalle);
        cv.setProducto(detalle.getProducto());
        cv.setTecnico(vendedor); // usuario logueado
        cv.setModalidad("VENTA");
        // Si el usuario tiene empleado asociado, registrar como vendedor
        if (vendedor.getEmpleado() != null) {
            cv.setVendedor(vendedor.getEmpleado());
        }
        cv.setValorTotal(valorBase);
        cv.setPorcentajeTecnico(config.getPorcentajeTecnico());
        cv.setPorcentajeNegocio(BigDecimal.ZERO);
        cv.setValorTecnico(valorVendedor);
        cv.setValorNegocio(BigDecimal.ZERO);
        comisionVentaRepo.save(cv);
    }

    // ── Mappers internos ──────────────────────────────────────

    private ComisionConfigDto toConfigDto(ComisionConfigEntity e) {
        ComisionConfigDto dto = new ComisionConfigDto();
        dto.setId(e.getId());
        dto.setEmpresaId(e.getEmpresa().getId());
        dto.setModalidad(e.getModalidad());
        dto.setTipo(e.getTipo());
        dto.setPorcentajeTecnico(e.getPorcentajeTecnico());
        dto.setPorcentajeNegocio(e.getPorcentajeNegocio());
        dto.setActivo(e.getActivo());
        if (e.getProducto() != null) {
            dto.setProductoId(e.getProducto().getId());
            dto.setProductoNombre(e.getProducto().getNombre());
        }
        if (e.getCategoriaId() != null) {
            dto.setCategoriaId(e.getCategoriaId());
            categoriaRepo.findById(e.getCategoriaId())
                    .ifPresent(c -> dto.setCategoriaNombre(c.getNombre()));
        }
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
        dto.setTipo(e.getTipo());

        if ("VENDEDOR".equals(e.getTipo()) && e.getVendedor() != null) {
            dto.setTecnicoId(e.getVendedor().getId().intValue());
            dto.setTecnicoNombre(e.getVendedor().getNombres() + " " + e.getVendedor().getApellidos());
        } else if (e.getTecnico() != null) {
            dto.setTecnicoId(e.getTecnico().getId());
            dto.setTecnicoNombre(resolverNombreTecnico(e.getTecnico()));
        }

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
