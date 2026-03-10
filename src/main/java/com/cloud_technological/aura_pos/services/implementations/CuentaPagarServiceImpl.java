package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.cuentas_pagar.AbonoPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarTableDto;
import com.cloud_technological.aura_pos.entity.AbonoPagarEntity;
import com.cloud_technological.aura_pos.entity.CuentaPagarEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.CuentaPagarMapper;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.CuentaPagarService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class CuentaPagarServiceImpl implements CuentaPagarService {

    private final CuentaPagarQueryRepository queryRepository;
    private final CuentaPagarJPARepository jpaRepository;
    private final AbonoPagarJPARepository abonoJpaRepository;
    private final EmpresaJPARepository empresaRepository;
    private final TerceroJPARepository terceroRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final TurnoCajaJPARepository turnoCajaRepository;
    private final CuentaPagarMapper mapper;

    @Autowired
    public CuentaPagarServiceImpl(CuentaPagarQueryRepository queryRepository,
            CuentaPagarJPARepository jpaRepository,
            AbonoPagarJPARepository abonoJpaRepository,
            EmpresaJPARepository empresaRepository,
            TerceroJPARepository terceroRepository,
            UsuarioJPARepository usuarioRepository,
            TurnoCajaJPARepository turnoCajaRepository,
            CuentaPagarMapper mapper) {
        this.queryRepository = queryRepository;
        this.jpaRepository = jpaRepository;
        this.abonoJpaRepository = abonoJpaRepository;
        this.empresaRepository = empresaRepository;
        this.terceroRepository = terceroRepository;
        this.usuarioRepository = usuarioRepository;
        this.turnoCajaRepository = turnoCajaRepository;
        this.mapper = mapper;
    }

    @Override
    public PageImpl<CuentaPagarTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public PageImpl<CuentaPagarTableDto> listarConFiltros(PageableDto<Object> pageable, Integer empresaId,
            String fechaDesde, String fechaHasta, Long proveedorId, String estado) {
        return queryRepository.listarConFiltros(pageable, empresaId, fechaDesde, fechaHasta, proveedorId, estado);
    }

    @Override
    public CuentaPagarDto obtenerPorId(Long id, Integer empresaId) {
        CuentaPagarEntity entity = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por pagar no encontrada"));
        
        return toDto(entity);
    }

    @Override
    @Transactional
    public CuentaPagarDto crear(CreateCuentaPagarDto dto, Integer empresaId, Long usuarioId) {
        // Validar proveedor
        TerceroEntity tercero = terceroRepository.findById(dto.getProveedorId().intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado"));
        
        if (!Boolean.TRUE.equals(tercero.getEsProveedor())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El tercero no es un proveedor");
        }

        // Validar totalDeuda mayor a 0
        if (dto.getTotalDeuda() == null || dto.getTotalDeuda().compareTo(BigDecimal.ZERO) <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El total de deuda debe ser mayor a 0");
        }

        // Generar número de cuenta
        String numeroCuenta = queryRepository.generarNumeroCuenta();

        // Obtener empresa
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        // Crear entidad
        CuentaPagarEntity entity = mapper.toEntity(dto);
        entity.setEmpresa(empresa);
        entity.setTercero(tercero);
        entity.setNumeroCuenta(numeroCuenta);
        entity.setNumeroFacturaExterno(dto.getNumeroFacturaExterno());
        entity.setTotalDeuda(dto.getTotalDeuda());
        entity.setTotalAbonado(BigDecimal.ZERO);
        entity.setSaldoPendiente(dto.getTotalDeuda());
        entity.setEstado("activa");

        entity = jpaRepository.save(entity);

        return toDto(entity);
    }

    @Override
    @Transactional
    public CuentaPagarDto actualizar(Long id, CreateCuentaPagarDto dto, Integer empresaId) {
        CuentaPagarEntity entity = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por pagar no encontrada"));

        // Solo permitir modificar fechaVencimiento y observaciones
        if (dto.getFechaVencimiento() != null) {
            entity.setFechaVencimiento(dto.getFechaVencimiento());
        }
        if (dto.getObservaciones() != null) {
            entity.setObservaciones(dto.getObservaciones());
        }

        entity = jpaRepository.save(entity);
        return toDto(entity);
    }

    @Override
    @Transactional
    public AbonoPagarDto registrarAbono(Long cuentaId, AbonoPagarDto dto, Integer empresaId, Long usuarioId) {
        CuentaPagarEntity cuenta = jpaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por pagar no encontrada"));

        // Validar estado
        if ("pagada".equals(cuenta.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta ya está pagada");
        }

        // Validar monto
        if (dto.getMonto() == null || dto.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a 0");
        }

        // Validar que el monto no exceda el saldo pendiente
        if (dto.getMonto().compareTo(cuenta.getSaldoPendiente()) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El monto no puede ser mayor al saldo pendiente");
        }

        // Obtener usuario
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // Obtener turno de caja (opcional)
        TurnoCajaEntity turnoCaja = null;
        if (dto.getTurnoCajaId() != null) {
            turnoCaja = turnoCajaRepository.findById(dto.getTurnoCajaId()).orElse(null);
        }

        // Crear abono
        AbonoPagarEntity abono = AbonoPagarEntity.builder()
                .cuentaPagar(cuenta)
                .usuario(usuario)
                .turnoCaja(turnoCaja)
                .monto(dto.getMonto())
                .metodoPago(dto.getMetodoPago())
                .referencia(dto.getReferencia())
                .banco(dto.getBanco())
                .fechaPago(dto.getFechaPago() != null ? dto.getFechaPago() : LocalDateTime.now())
                .build();

        abono = abonoJpaRepository.save(abono);

        // Actualizar cuenta
        cuenta.setTotalAbonado(cuenta.getTotalAbonado().add(dto.getMonto()));
        cuenta.setSaldoPendiente(cuenta.getSaldoPendiente().subtract(dto.getMonto()));

        if (cuenta.getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0) {
            cuenta.setSaldoPendiente(BigDecimal.ZERO);
            cuenta.setEstado("pagada");
        }

        jpaRepository.save(cuenta);

        return toAbonoDto(abono);
    }

    @Override
    public List<AbonoPagarDto> listarAbonos(Long cuentaId, Integer empresaId) {
        // Verificar que la cuenta pertenece a la empresa
        jpaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por pagar no encontrada"));

        List<AbonoPagarEntity> abonos = abonoJpaRepository.findByCuentaPagarId(cuentaId);

        return abonos.stream().map(this::toAbonoDto).toList();
    }

    @Override
    @Transactional
    public void eliminarAbono(Long cuentaId, Long abonoId, Integer empresaId) {
        CuentaPagarEntity cuenta = jpaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por pagar no encontrada"));

        AbonoPagarEntity abono = abonoJpaRepository.findByIdAndCuentaPagarId(abonoId, cuentaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Abono no encontrado"));

        // Solo permitir anulación si el abono es del día actual
        if (!abono.getCreatedAt().toLocalDate().equals(LocalDateTime.now().toLocalDate())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo se pueden eliminar abonos del día actual");
        }

        // Validar que la cuenta no esté pagada
        if ("pagada".equals(cuenta.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede eliminar el abono de una cuenta pagada");
        }

        // Reversar abono
        cuenta.setTotalAbonado(cuenta.getTotalAbonado().subtract(abono.getMonto()));
        cuenta.setSaldoPendiente(cuenta.getSaldoPendiente().add(abono.getMonto()));
        cuenta.setEstado("activa");

        jpaRepository.save(cuenta);
        abonoJpaRepository.delete(abono);
    }

    private CuentaPagarDto toDto(CuentaPagarEntity entity) {
        CuentaPagarDto dto = new CuentaPagarDto();
        dto.setId(entity.getId());
        dto.setNumeroCuenta(entity.getNumeroCuenta());
        dto.setNumeroFacturaExterno(entity.getNumeroFacturaExterno());
        dto.setFechaEmision(entity.getFechaEmision());
        dto.setFechaVencimiento(entity.getFechaVencimiento());
        dto.setTotalDeuda(entity.getTotalDeuda());
        
        // Calcular totales desde abonos (fuente de verdad)
        BigDecimal totalAbonado = BigDecimal.ZERO;
        try {
            if (entity.getAbonos() != null) {
                for (AbonoPagarEntity abono : entity.getAbonos()) {
                    if (abono.getDeletedAt() == null) {
                        totalAbonado = totalAbonado.add(abono.getMonto());
                    }
                }
            }
        } catch (Exception e) {
            // Si hay error, usar el valor de la entidad como fallback
            totalAbonado = entity.getTotalAbonado() != null ? entity.getTotalAbonado() : BigDecimal.ZERO;
        }
        
        BigDecimal saldoPendiente = entity.getTotalDeuda().subtract(totalAbonado);
        
        dto.setTotalAbonado(totalAbonado);
        dto.setSaldoPendiente(saldoPendiente);
        
        // Calcular estado dinámicamente
        String estado;
        if (saldoPendiente.compareTo(BigDecimal.ZERO) <= 0) {
            estado = "pagada";
        } else if (entity.getFechaVencimiento() != null && entity.getFechaVencimiento().isBefore(java.time.LocalDateTime.now())) {
            estado = "vencida";
        } else {
            estado = "activa";
        }
        dto.setEstado(estado);
        
        dto.setObservaciones(entity.getObservaciones());
        dto.setCreatedAt(entity.getCreatedAt());
        
        if (entity.getEmpresa() != null) {
            dto.setEmpresaId(entity.getEmpresa().getId().longValue());
        }
        if (entity.getTercero() != null) {
            dto.setTerceroId(entity.getTercero().getId());
            dto.setProveedorNombre(getProveedorNombre(entity.getTercero()));
            dto.setProveedorDocumento(entity.getTercero().getNumeroDocumento());
        }
        if (entity.getCompra() != null) {
            dto.setCompraId(entity.getCompra().getId());
        }

        // Cargar abonos - usar lazy loading seguro
        try {
            List<AbonoPagarDto> abonos = entity.getAbonos() != null && !entity.getAbonos().isEmpty() ? 
                    entity.getAbonos().stream()
                        .filter(a -> a.getDeletedAt() == null)
                        .map(this::toAbonoDto).toList() : new ArrayList<>();
            dto.setAbonos(abonos);
        } catch (Exception e) {
            // Si los abonos no están cargados, retornar lista vacía
            dto.setAbonos(new ArrayList<>());
        }

        return dto;
    }

    private String getProveedorNombre(TerceroEntity tercero) {
        if (tercero.getRazonSocial() != null && !tercero.getRazonSocial().isEmpty()) {
            return tercero.getRazonSocial();
        }
        String nombres = tercero.getNombres() != null ? tercero.getNombres() : "";
        String apellidos = tercero.getApellidos() != null ? tercero.getApellidos() : "";
        return (nombres + " " + apellidos).trim();
    }

    private AbonoPagarDto toAbonoDto(AbonoPagarEntity entity) {
        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setId(entity.getId());
        
        if (entity.getCuentaPagar() != null) {
            dto.setCuentaPagarId(entity.getCuentaPagar().getId());
        }
        
        dto.setMonto(entity.getMonto());
        dto.setMetodoPago(entity.getMetodoPago());
        dto.setReferencia(entity.getReferencia());
        dto.setBanco(entity.getBanco());
        dto.setFechaPago(entity.getFechaPago());
        dto.setCreatedAt(entity.getCreatedAt());
        
        if (entity.getUsuario() != null) {
            dto.setUsuarioId(entity.getUsuario().getId().longValue());
            dto.setUsuarioNombre(entity.getUsuario().getUsername());
        }

        if (entity.getTurnoCaja() != null) {
            dto.setTurnoCajaId(entity.getTurnoCaja().getId());
        }

        return dto;
    }
    
    @Override
    public CuentaPagarResumenDto obtenerResumen(Integer empresaId, String fechaDesde, String fechaHasta, Long proveedorId, String estado) {
        return queryRepository.obtenerResumen(empresaId, fechaDesde, fechaHasta, proveedorId, estado);
    }
    
    @Override
    public List<CuentaPagarTableDto> obtenerVencidas(Integer empresaId) {
        return queryRepository.obtenerVencidas(empresaId);
    }
}
