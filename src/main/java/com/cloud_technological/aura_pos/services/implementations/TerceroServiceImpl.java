package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.terceros.CreateTerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.EstadoCuentaClienteDto;
import com.cloud_technological.aura_pos.dto.terceros.MovimientoCuentaDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroTableDto;
import com.cloud_technological.aura_pos.dto.terceros.UpdateTerceroDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.mappers.TerceroMapper;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroQueryRepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.ITerceroService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;


@Service
public class TerceroServiceImpl implements ITerceroService {

    private final TerceroQueryRepository terceroRepository;
    private final TerceroJPARepository terceroJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final TerceroMapper terceroMapper;
    private final VentaJPARepository ventaRepository;
    private final CuentaCobrarJPARepository cuentaCobrarRepository;
    private final AbonoCobrarJPARepository abonoCobrarRepository;

    @Autowired
    public TerceroServiceImpl(TerceroQueryRepository terceroRepository,
            TerceroJPARepository terceroJPARepository,
            EmpresaJPARepository empresaRepository,
            TerceroMapper terceroMapper,
            VentaJPARepository ventaRepository,
            CuentaCobrarJPARepository cuentaCobrarRepository,
            AbonoCobrarJPARepository abonoCobrarRepository) {
        this.terceroRepository = terceroRepository;
        this.terceroJPARepository = terceroJPARepository;
        this.empresaRepository = empresaRepository;
        this.terceroMapper = terceroMapper;
        this.ventaRepository = ventaRepository;
        this.cuentaCobrarRepository = cuentaCobrarRepository;
        this.abonoCobrarRepository = abonoCobrarRepository;
    }

    @Override
    public PageImpl<TerceroTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return terceroRepository.listar(pageable, empresaId);
    }

    @Override
    public TerceroDto obtenerPorId(Long id, Integer empresaId) {
        TerceroEntity entity = terceroJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));
        return terceroMapper.toDto(entity);
    }

    @Override
    public List<TerceroTableDto> listarClientes(String search, Integer empresaId) {
        return terceroRepository.listarClientes(search, empresaId);
    }

    @Override
    public List<TerceroTableDto> listarProveedores(String search, Integer empresaId) {
        return terceroRepository.listarProveedores(search, empresaId);
    }

    @Override
    public List<TerceroTableDto> listarTodos(String search, Integer empresaId) {
        return terceroRepository.listarTodos(search, empresaId);
    }

    @Override
    @Transactional
    public TerceroDto crear(CreateTerceroDto dto, Integer empresaId) {
        if (terceroRepository.existeDocumento(dto.getNumeroDocumento(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un tercero con este número de documento");

        // Validar que persona natural tenga nombres o jurídica tenga razón social
        if (dto.getRazonSocial() == null && dto.getNombres() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Debe ingresar razón social o nombres");

        TerceroEntity entity = terceroMapper.toEntity(dto);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);
        entity.setCreated_at(LocalDateTime.now());
        entity.setUpdated_at(LocalDateTime.now());

        return terceroMapper.toDto(terceroJPARepository.save(entity));
    }

    @Override
    @Transactional
    public TerceroDto actualizar(Long id, UpdateTerceroDto dto, Integer empresaId) {
        TerceroEntity entity = terceroJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));

        if (!entity.getNumeroDocumento().equals(dto.getNumeroDocumento()) &&
                terceroRepository.existeDocumentoExcluyendo(dto.getNumeroDocumento(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El número de documento ya está en uso");

        terceroMapper.updateEntityFromDto(dto, entity);
        entity.setUpdated_at(LocalDateTime.now());
        return terceroMapper.toDto(terceroJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        TerceroEntity entity = terceroJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));

        entity.setDeleted_at(LocalDateTime.now());
        entity.setActivo(false);
        terceroJPARepository.save(entity);
    }

    @Override
    public EstadoCuentaClienteDto obtenerEstadoCuenta(Long clienteId, Integer empresaId,
            String fechaDesde, String fechaHasta) {

        TerceroEntity cliente = terceroJPARepository.findByIdAndEmpresaId(clienteId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime desde = fechaDesde != null
                ? LocalDate.parse(fechaDesde, fmt).atStartOfDay()
                : null;
        LocalDateTime hasta = fechaHasta != null
                ? LocalDate.parse(fechaHasta, fmt).atTime(LocalTime.MAX)
                : null;

        // ── 1. Ventas del cliente ────────────────────────────────────────────
        List<VentaEntity> ventas = (desde != null && hasta != null)
                ? ventaRepository.findByClienteIdAndEmpresaIdAndFechaEmisionBetweenOrderByFechaEmisionAsc(
                        clienteId, empresaId, desde, hasta)
                : ventaRepository.findByClienteIdAndEmpresaIdOrderByFechaEmisionAsc(clienteId, empresaId);

        // ── 2. Cuentas por cobrar del cliente ────────────────────────────────
        List<CuentaCobrarEntity> cuentas = cuentaCobrarRepository
                .findByTerceroIdAndEmpresaIdAndDeletedAtIsNullOrderByCreatedAtAsc(clienteId, empresaId);

        // Filtrar cuentas por fecha si aplica
        if (desde != null && hasta != null) {
            LocalDateTime desdeFinal = desde;
            LocalDateTime hastaFinal = hasta;
            cuentas = cuentas.stream()
                    .filter(c -> c.getCreatedAt() != null
                            && !c.getCreatedAt().isBefore(desdeFinal)
                            && !c.getCreatedAt().isAfter(hastaFinal))
                    .toList();
        }

        // ── 3. Armar movimientos ─────────────────────────────────────────────
        List<MovimientoCuentaDto> movimientos = new ArrayList<>();

        // Ventas → cargo
        for (VentaEntity v : ventas) {
            BigDecimal total = v.getTotalPagar() != null ? v.getTotalPagar() : BigDecimal.ZERO;
            String numero = (v.getPrefijo() != null ? v.getPrefijo() + "-" : "")
                    + (v.getConsecutivo() != null ? v.getConsecutivo() : v.getId());
            movimientos.add(MovimientoCuentaDto.builder()
                    .tipo("VENTA")
                    .fecha(v.getFechaEmision())
                    .referencia(numero)
                    .descripcion("Venta " + numero)
                    .cargo(total)
                    .abono(BigDecimal.ZERO)
                    .esCredito("PAGO_PARCIAL".equals(v.getEstadoVenta()))
                    .build());
        }

        // Cuentas por cobrar → cargo + abonos
        for (CuentaCobrarEntity cc : cuentas) {
            // La cuenta en sí es informativa si ya existe la venta; aquí solo agregamos abonos
            List<AbonoCobrarEntity> abonos = abonoCobrarRepository.findByCuentaCobrarId(cc.getId());
            for (AbonoCobrarEntity abono : abonos) {
                // Filtrar abono por fecha si aplica
                if (desde != null && hasta != null && abono.getFechaPago() != null) {
                    if (abono.getFechaPago().isBefore(desde) || abono.getFechaPago().isAfter(hasta)) {
                        continue;
                    }
                }
                BigDecimal monto = abono.getMonto() != null ? abono.getMonto() : BigDecimal.ZERO;
                movimientos.add(MovimientoCuentaDto.builder()
                        .tipo("ABONO")
                        .fecha(abono.getFechaPago())
                        .referencia(cc.getNumeroCuenta())
                        .descripcion("Abono cuenta " + cc.getNumeroCuenta()
                                + (abono.getReferencia() != null ? " - " + abono.getReferencia() : ""))
                        .cargo(BigDecimal.ZERO)
                        .abono(monto)
                        .build());
            }
        }

        // Ordenar cronológicamente ASC para calcular el saldo acumulado correctamente
        movimientos.sort(Comparator.comparing(
                MovimientoCuentaDto::getFecha,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        // ── 4. Calcular saldo acumulado por movimiento ───────────────────────
        BigDecimal saldoAcumulado = BigDecimal.ZERO;
        for (MovimientoCuentaDto m : movimientos) {
            saldoAcumulado = saldoAcumulado.add(m.getCargo()).subtract(m.getAbono());
            m.setSaldoAcumulado(saldoAcumulado);
        }

        // Invertir para mostrar los más recientes primero
        java.util.Collections.reverse(movimientos);

        // ── 5. Resumen general (sobre todo el historial de cuentas, sin filtro de fecha) ──
        List<CuentaCobrarEntity> todasLasCuentas = cuentaCobrarRepository
                .findByTerceroIdAndEmpresaIdAndDeletedAtIsNullOrderByCreatedAtAsc(clienteId, empresaId);

        BigDecimal totalVentas = ventas.stream()
                .map(v -> v.getTotalPagar() != null ? v.getTotalPagar() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeuda = todasLasCuentas.stream()
                .map(c -> c.getTotalDeuda() != null ? c.getTotalDeuda() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAbonado = todasLasCuentas.stream()
                .map(c -> c.getTotalAbonado() != null ? c.getTotalAbonado() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoPendiente = todasLasCuentas.stream()
                .map(c -> c.getSaldoPendiente() != null ? c.getSaldoPendiente() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime ahora = LocalDateTime.now();
        long cuentasActivas = todasLasCuentas.stream()
                .filter(c -> "activa".equalsIgnoreCase(c.getEstado()))
                .count();
        long cuentasVencidas = todasLasCuentas.stream()
                .filter(c -> c.getFechaVencimiento() != null && c.getFechaVencimiento().isBefore(ahora)
                        && !"pagada".equalsIgnoreCase(c.getEstado()))
                .count();

        // ── 6. Nombre del cliente ────────────────────────────────────────────
        String nombre = cliente.getRazonSocial() != null
                ? cliente.getRazonSocial()
                : ((cliente.getNombres() != null ? cliente.getNombres() : "")
                        + (cliente.getApellidos() != null ? " " + cliente.getApellidos() : "")).trim();

        return EstadoCuentaClienteDto.builder()
                .clienteId(clienteId)
                .nombreCliente(nombre)
                .tipoDocumento(cliente.getTipoDocumento())
                .numeroDocumento(cliente.getNumeroDocumento())
                .email(cliente.getEmail())
                .telefono(cliente.getTelefono())
                .municipio(cliente.getMunicipio())
                .totalVentas(totalVentas)
                .totalDeuda(totalDeuda)
                .totalAbonado(totalAbonado)
                .saldoPendiente(saldoPendiente)
                .cuentasActivas(cuentasActivas)
                .cuentasVencidas(cuentasVencidas)
                .movimientos(movimientos)
                .build();
    }
}
