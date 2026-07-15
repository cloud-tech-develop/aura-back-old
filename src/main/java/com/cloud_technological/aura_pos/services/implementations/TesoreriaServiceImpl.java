package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.event.OperacionContabilizableEvent;

import com.cloud_technological.aura_pos.dto.tesoreria.ConciliacionResumenDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CreateMovimientoDto;
import com.cloud_technological.aura_pos.dto.tesoreria.TesoreriaMovimientoDto;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.TesoreriaMovimientoEntity;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.TesoreriaMovimientoJPARepository;
import com.cloud_technological.aura_pos.services.TesoreriaService;

@Service
public class TesoreriaServiceImpl implements TesoreriaService {

    @Autowired
    private TesoreriaMovimientoJPARepository movRepo;

    @Autowired
    private CuentaBancariaJPARepository cuentaRepo;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public List<TesoreriaMovimientoDto> listarEgresos(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta) {
        return movRepo.findByFiltros(empresaId, "EGRESO", cuentaId, desde, hasta)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<TesoreriaMovimientoDto> listarRecaudos(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta) {
        return movRepo.findByFiltros(empresaId, "RECAUDO", cuentaId, desde, hasta)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TesoreriaMovimientoDto crearEgreso(Integer empresaId, Integer usuarioId, CreateMovimientoDto dto) {
        CuentaBancariaEntity cuenta = getCuenta(dto.getCuentaBancariaId(), empresaId);
        validarSaldoConSobregiro(cuenta, dto.getMonto());
        cuenta.setSaldoActual(cuenta.getSaldoActual().subtract(dto.getMonto()));
        cuentaRepo.save(cuenta);

        TesoreriaMovimientoEntity mov = TesoreriaMovimientoEntity.builder()
                .empresaId(empresaId)
                .cuentaBancariaId(cuenta.getId())
                .tipo("EGRESO")
                .monto(dto.getMonto())
                .concepto(dto.getConcepto().trim())
                .beneficiario(dto.getBeneficiario())
                .referencia(dto.getReferencia())
                .fecha(dto.getFecha())
                .categoria(dto.getCategoria())
                .contrapartidaCuentaId(dto.getContrapartidaCuentaId())
                .usuarioId(usuarioId)
                .build();
        TesoreriaMovimientoEntity saved = movRepo.save(mov);
        publicarContabilizacion(saved, empresaId, usuarioId);
        return toDto(saved);
    }

    @Override
    @Transactional
    public TesoreriaMovimientoDto crearRecaudo(Integer empresaId, Integer usuarioId, CreateMovimientoDto dto) {
        CuentaBancariaEntity cuenta = getCuenta(dto.getCuentaBancariaId(), empresaId);
        cuenta.setSaldoActual(cuenta.getSaldoActual().add(dto.getMonto()));
        cuentaRepo.save(cuenta);

        TesoreriaMovimientoEntity mov = TesoreriaMovimientoEntity.builder()
                .empresaId(empresaId)
                .cuentaBancariaId(cuenta.getId())
                .tipo("RECAUDO")
                .monto(dto.getMonto())
                .concepto(dto.getConcepto().trim())
                .beneficiario(dto.getBeneficiario())
                .referencia(dto.getReferencia())
                .fecha(dto.getFecha())
                .categoria(dto.getCategoria())
                .contrapartidaCuentaId(dto.getContrapartidaCuentaId())
                .usuarioId(usuarioId)
                .build();
        TesoreriaMovimientoEntity saved = movRepo.save(mov);
        publicarContabilizacion(saved, empresaId, usuarioId);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        TesoreriaMovimientoEntity mov = movRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
        if (mov.getAnulado()) return;
        if (mov.getConciliado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se puede anular un movimiento ya conciliado");
        }

        CuentaBancariaEntity cuenta = getCuenta(mov.getCuentaBancariaId(), empresaId);
        // Reversar efecto en saldo
        if ("EGRESO".equals(mov.getTipo())) {
            cuenta.setSaldoActual(cuenta.getSaldoActual().add(mov.getMonto()));
        } else if ("RECAUDO".equals(mov.getTipo())) {
            cuenta.setSaldoActual(cuenta.getSaldoActual().subtract(mov.getMonto()));
        }
        cuentaRepo.save(cuenta);
        mov.setAnulado(true);
        movRepo.save(mov);

        // Reversar el asiento contable si el movimiento se había contabilizado.
        if (mov.getContrapartidaCuentaId() != null) {
            eventPublisher.publishEvent(new ContabilidadReversaEvent(
                    "TESORERIA", mov.getId(), empresaId, mov.getUsuarioId()));
        }
    }

    /**
     * Publica el evento de contabilización del movimiento (AFTER_COMMIT). Solo se
     * contabiliza si el movimiento trae cuenta de contrapartida; si no, queda solo
     * registrado en tesorería.
     */
    private void publicarContabilizacion(TesoreriaMovimientoEntity mov, Integer empresaId, Integer usuarioId) {
        if (mov.getContrapartidaCuentaId() != null) {
            eventPublisher.publishEvent(new OperacionContabilizableEvent(
                    "TESORERIA", mov.getId(), empresaId, usuarioId));
        }
    }

    @Override
    public List<TesoreriaMovimientoDto> listarParaConciliacion(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta) {
        return movRepo.findParaConciliacion(empresaId, cuentaId, desde, hasta)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void toggleConciliado(Long id, Integer empresaId) {
        TesoreriaMovimientoEntity mov = movRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
        mov.setConciliado(!mov.getConciliado());
        mov.setFechaConciliacion(mov.getConciliado() ? LocalDate.now() : null);
        movRepo.save(mov);
    }

    @Override
    @Transactional
    public void conciliarLote(List<Long> ids, Integer empresaId) {
        for (Long id : ids) {
            TesoreriaMovimientoEntity mov = movRepo.findByIdAndEmpresaId(id, empresaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado: " + id));
            if (!mov.getConciliado()) {
                mov.setConciliado(true);
                mov.setFechaConciliacion(LocalDate.now());
                movRepo.save(mov);
            }
        }
    }

    @Override
    public ConciliacionResumenDto getResumen(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta) {
        CuentaBancariaEntity cuenta = getCuenta(cuentaId, empresaId);
        List<TesoreriaMovimientoEntity> movs = movRepo.findParaConciliacion(empresaId, cuentaId, desde, hasta);

        long total = movs.size();
        long conciliados = movs.stream().filter(TesoreriaMovimientoEntity::getConciliado).count();
        long pendientes = total - conciliados;

        BigDecimal entradasConc = movs.stream()
                .filter(m -> m.getConciliado() && isEntrada(m.getTipo()))
                .map(TesoreriaMovimientoEntity::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salidasConc = movs.stream()
                .filter(m -> m.getConciliado() && !isEntrada(m.getTipo()))
                .map(TesoreriaMovimientoEntity::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal entradasPend = movs.stream()
                .filter(m -> !m.getConciliado() && isEntrada(m.getTipo()))
                .map(TesoreriaMovimientoEntity::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salidasPend = movs.stream()
                .filter(m -> !m.getConciliado() && !isEntrada(m.getTipo()))
                .map(TesoreriaMovimientoEntity::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoContable = cuenta.getSaldoInicial()
                .add(entradasConc).subtract(salidasConc);

        ConciliacionResumenDto dto = new ConciliacionResumenDto();
        dto.setCuentaId(cuentaId);
        dto.setCuentaNombre(cuenta.getNombre());
        dto.setSaldoContable(saldoContable);
        dto.setTotalMovimientos(total);
        dto.setMovimientosConciliados(conciliados);
        dto.setMovimientosPendientes(pendientes);
        dto.setTotalEntradasConciliadas(entradasConc);
        dto.setTotalSalidasConciliadas(salidasConc);
        dto.setTotalEntradasPendientes(entradasPend);
        dto.setTotalSalidasPendientes(salidasPend);
        return dto;
    }

    private boolean isEntrada(String tipo) {
        return "RECAUDO".equals(tipo) || "TRANSFERENCIA_ENTRADA".equals(tipo);
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private CuentaBancariaEntity getCuenta(Long cuentaId, Integer empresaId) {
        return cuentaRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta bancaria no encontrada"));
    }

    /**
     * Sobregiro (E2): un egreso puede dejar la cuenta en negativo SOLO si
     * la cuenta permite sobregiro y el nuevo saldo no excede el cupo.
     */
    private void validarSaldoConSobregiro(CuentaBancariaEntity cuenta, java.math.BigDecimal monto) {
        java.math.BigDecimal nuevoSaldo = cuenta.getSaldoActual().subtract(monto);
        if (nuevoSaldo.signum() >= 0) {
            return;
        }
        if (!Boolean.TRUE.equals(cuenta.getPermiteSobregiro())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Saldo insuficiente en " + cuenta.getNombre() + " (saldo "
                            + cuenta.getSaldoActual() + "). La cuenta no permite sobregiro.");
        }
        if (cuenta.getCupoSobregiro() != null
                && nuevoSaldo.negate().compareTo(cuenta.getCupoSobregiro()) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El egreso excede el cupo de sobregiro de " + cuenta.getNombre()
                            + " (cupo " + cuenta.getCupoSobregiro() + ", saldo resultante "
                            + nuevoSaldo + ").");
        }
    }

    private TesoreriaMovimientoDto toDto(TesoreriaMovimientoEntity e) {
        String nombreCuenta = cuentaRepo.findById(e.getCuentaBancariaId())
                .map(CuentaBancariaEntity::getNombre).orElse("—");
        return TesoreriaMovimientoDto.builder()
                .id(e.getId())
                .cuentaBancariaId(e.getCuentaBancariaId())
                .cuentaBancariaNombre(nombreCuenta)
                .tipo(e.getTipo())
                .monto(e.getMonto())
                .concepto(e.getConcepto())
                .beneficiario(e.getBeneficiario())
                .referencia(e.getReferencia())
                .fecha(e.getFecha())
                .categoria(e.getCategoria())
                .conciliado(e.getConciliado())
                .anulado(e.getAnulado())
                .build();
    }
}
