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

import com.cloud_technological.aura_pos.dto.cuentas_cobrar.AbonoCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.mappers.CuentaCobrarMapper;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.CuentaCobrarService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class CuentaCobrarServiceImpl implements CuentaCobrarService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final CuentaCobrarQueryRepository queryRepository;
    private final CuentaCobrarJPARepository jpaRepository;
    private final AbonoCobrarJPARepository abonoJpaRepository;
    private final EmpresaJPARepository empresaRepository;
    private final TerceroJPARepository terceroRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final TurnoCajaJPARepository turnoCajaRepository;
    private final VentaJPARepository ventaRepository;
    private final CuentaCobrarMapper mapper;

    @Autowired
    public CuentaCobrarServiceImpl(CuentaCobrarQueryRepository queryRepository,
            CuentaCobrarJPARepository jpaRepository,
            AbonoCobrarJPARepository abonoJpaRepository,
            EmpresaJPARepository empresaRepository,
            TerceroJPARepository terceroRepository,
            UsuarioJPARepository usuarioRepository,
            TurnoCajaJPARepository turnoCajaRepository,
            VentaJPARepository ventaRepository,
            CuentaCobrarMapper mapper) {
        this.queryRepository = queryRepository;
        this.jpaRepository = jpaRepository;
        this.abonoJpaRepository = abonoJpaRepository;
        this.empresaRepository = empresaRepository;
        this.terceroRepository = terceroRepository;
        this.usuarioRepository = usuarioRepository;
        this.turnoCajaRepository = turnoCajaRepository;
        this.ventaRepository = ventaRepository;
        this.mapper = mapper;
    }

    @Override
    public PageImpl<CuentaCobrarTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public PageImpl<CuentaCobrarTableDto> listarConFiltros(PageableDto<Object> pageable, Integer empresaId,
            String fechaDesde, String fechaHasta, Long clienteId, String estado) {
        return queryRepository.listarConFiltros(pageable, empresaId, fechaDesde, fechaHasta, clienteId, estado);
    }

    @Override
    public CuentaCobrarDto obtenerPorId(Long id, Integer empresaId) {
        CuentaCobrarEntity entity = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));
        
        return toDto(entity);
    }

    @Override
    @Transactional
    public CuentaCobrarDto crear(CreateCuentaCobrarDto dto, Integer empresaId, Long usuarioId) {
        // Validar cliente
        TerceroEntity tercero = terceroRepository.findById(dto.getClienteId().intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Cliente no encontrado"));
        
        if (!Boolean.TRUE.equals(tercero.getEsCliente())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El tercero no es un cliente");
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
        CuentaCobrarEntity entity = mapper.toEntity(dto);
        entity.setEmpresa(empresa);
        entity.setTercero(tercero);
        // Relacionar la venta de origen (necesario para imprimir la factura con sus productos)
        if (dto.getVentaId() != null) {
            VentaEntity venta = ventaRepository.findByIdAndEmpresaId(dto.getVentaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Venta no encontrada"));
            entity.setVenta(venta);
        }
        entity.setNumeroCuenta(numeroCuenta);
        entity.setTotalDeuda(dto.getTotalDeuda());
        entity.setTotalAbonado(BigDecimal.ZERO);
        entity.setSaldoPendiente(dto.getTotalDeuda());
        entity.setEstado("activa");

        entity = jpaRepository.save(entity);

        return toDto(entity);
    }

    @Override
    @Transactional
    public CuentaCobrarDto actualizar(Long id, CreateCuentaCobrarDto dto, Integer empresaId) {
        CuentaCobrarEntity entity = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));

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
    public AbonoCobrarDto registrarAbono(Long cuentaId, AbonoCobrarDto dto, Integer empresaId, Long usuarioId) {
        CuentaCobrarEntity cuenta = jpaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));

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
        AbonoCobrarEntity abono = AbonoCobrarEntity.builder()
                .cuentaCobrar(cuenta)
                .usuario(usuario)
                .turnoCaja(turnoCaja)
                .monto(dto.getMonto())
                .metodoPago(dto.getMetodoPago())
                .referencia(dto.getReferencia())
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

        // Asiento contable del recaudo tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.AbonoContabilizableEvent(
                        "COBRO", abono.getId(), empresaId, usuarioId != null ? usuarioId.intValue() : null));

        return toAbonoDto(abono);
    }

    @Override
    public List<AbonoCobrarDto> listarAbonos(Long cuentaId, Integer empresaId) {
        // Verificar que la cuenta pertenece a la empresa
        jpaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));

        List<AbonoCobrarEntity> abonos = abonoJpaRepository.findByCuentaCobrarId(cuentaId);

        return abonos.stream().map(this::toAbonoDto).toList();
    }

    @Override
    @Transactional
    public void eliminarAbono(Long cuentaId, Long abonoId, Integer empresaId) {
        CuentaCobrarEntity cuenta = jpaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));

        AbonoCobrarEntity abono = abonoJpaRepository.findByIdAndCuentaCobrarId(abonoId, cuentaId)
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

    @Override
    @Transactional
    public void anularPorVenta(Long ventaId, Integer empresaId) {
        // Buscar la cuenta por cobrar generada por la venta (puede no existir si fue de contado).
        CuentaCobrarEntity cuenta = jpaRepository.findByVentaIdAndEmpresaId(ventaId, empresaId)
                .orElse(null);

        if (cuenta == null || cuenta.getDeletedAt() != null) {
            return;
        }

        // Bloquear la anulación si la cuenta ya tiene abonos registrados (dinero recibido).
        boolean tieneAbonos = abonoJpaRepository.findByCuentaCobrarId(cuenta.getId()).stream()
                .anyMatch(a -> a.getDeletedAt() == null);

        if (tieneAbonos) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede anular la venta porque su cuenta por cobrar ya tiene abonos. "
                    + "Reverse primero los abonos.");
        }

        // Sin abonos: anular la cuenta limpiamente.
        cuenta.setEstado("anulada");
        cuenta.setDeletedAt(LocalDateTime.now());
        jpaRepository.save(cuenta);
    }

    private CuentaCobrarDto toDto(CuentaCobrarEntity entity) {
        CuentaCobrarDto dto = new CuentaCobrarDto();
        dto.setId(entity.getId());
        dto.setNumeroCuenta(entity.getNumeroCuenta());
        dto.setFechaEmision(entity.getFechaEmision());
        dto.setFechaVencimiento(entity.getFechaVencimiento());
        dto.setTotalDeuda(entity.getTotalDeuda());
        
        // Calcular totales desde abonos (fuente de verdad)
        BigDecimal totalAbonado = BigDecimal.ZERO;
        try {
            if (entity.getAbonos() != null) {
                for (AbonoCobrarEntity abono : entity.getAbonos()) {
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
            dto.setClienteNombre(getClienteNombre(entity.getTercero()));
            dto.setClienteDocumento(entity.getTercero().getNumeroDocumento());
        }
        if (entity.getVenta() != null) {
            dto.setVentaId(entity.getVenta().getId());
        }

        // Cargar abonos - usar lazy loading seguro
        try {
            List<AbonoCobrarDto> abonos = entity.getAbonos() != null && !entity.getAbonos().isEmpty() ? 
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

    private String getClienteNombre(TerceroEntity tercero) {
        if (tercero.getRazonSocial() != null && !tercero.getRazonSocial().isEmpty()) {
            return tercero.getRazonSocial();
        }
        String nombres = tercero.getNombres() != null ? tercero.getNombres() : "";
        String apellidos = tercero.getApellidos() != null ? tercero.getApellidos() : "";
        return (nombres + " " + apellidos).trim();
    }

    private AbonoCobrarDto toAbonoDto(AbonoCobrarEntity entity) {
        AbonoCobrarDto dto = new AbonoCobrarDto();
        dto.setId(entity.getId());
        
        if (entity.getCuentaCobrar() != null) {
            dto.setCuentaCobrarId(entity.getCuentaCobrar().getId());
        }
        
        dto.setMonto(entity.getMonto());
        dto.setMetodoPago(entity.getMetodoPago());
        dto.setReferencia(entity.getReferencia());
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
    public CuentaCobrarResumenDto obtenerResumen(Integer empresaId, String fechaDesde, String fechaHasta, Long clienteId, String estado) {
        return queryRepository.obtenerResumen(empresaId, fechaDesde, fechaHasta, clienteId, estado);
    }
    
    @Override
    public List<CuentaCobrarTableDto> obtenerVencidas(Integer empresaId) {
        return queryRepository.obtenerVencidas(empresaId);
    }
}
