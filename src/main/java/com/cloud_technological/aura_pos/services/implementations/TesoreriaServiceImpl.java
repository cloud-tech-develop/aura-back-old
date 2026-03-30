package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
                .usuarioId(usuarioId)
                .build();
        return toDto(movRepo.save(mov));
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
                .usuarioId(usuarioId)
                .build();
        return toDto(movRepo.save(mov));
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

    // ── Helpers ──────────────────────────────────────────────────────
    private CuentaBancariaEntity getCuenta(Long cuentaId, Integer empresaId) {
        return cuentaRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta bancaria no encontrada"));
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
