package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import com.cloud_technological.aura_pos.dto.tesoreria.ConciliacionMayorDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CuentaBancariaDto;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.services.CuentaBancariaService;

@Service
public class CuentaBancariaServiceImpl implements CuentaBancariaService {

    @Autowired
    private CuentaBancariaJPARepository repo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository planCuentaRepo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository terceroRepo;

    @Autowired
    private AsientoContableQueryRepository asientoQueryRepo;

    @Override
    public List<CuentaBancariaDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdOrderByNombreAsc(empresaId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CuentaBancariaDto crear(Integer empresaId, CreateCuentaBancariaDto dto) {
        validarCuentaContable(empresaId, dto.getCuentaContableId());
        CuentaBancariaEntity entity = CuentaBancariaEntity.builder()
                .empresaId(empresaId)
                .nombre(dto.getNombre().trim())
                .tipo(dto.getTipo())
                .banco(dto.getBanco())
                .numeroCuenta(dto.getNumeroCuenta())
                .titular(dto.getTitular())
                .terceroId(dto.getTerceroId())
                .cuentaContableId(dto.getCuentaContableId())
                .saldoInicial(dto.getSaldoInicial())
                .saldoActual(dto.getSaldoInicial())
                .build();
        return toDto(repo.save(entity));
    }

    @Override
    public CuentaBancariaDto actualizar(Long id, Integer empresaId, CreateCuentaBancariaDto dto) {
        CuentaBancariaEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        validarCuentaContable(empresaId, dto.getCuentaContableId());
        entity.setNombre(dto.getNombre().trim());
        entity.setTipo(dto.getTipo());
        entity.setBanco(dto.getBanco());
        entity.setNumeroCuenta(dto.getNumeroCuenta());
        entity.setTitular(dto.getTitular());
        entity.setTerceroId(dto.getTerceroId());
        entity.setCuentaContableId(dto.getCuentaContableId());

        // Solo actualiza saldo_inicial si cambió y no hay movimientos aún
        if (entity.getSaldoActual().compareTo(entity.getSaldoInicial()) == 0) {
            entity.setSaldoInicial(dto.getSaldoInicial());
            entity.setSaldoActual(dto.getSaldoInicial());
        } else {
            entity.setSaldoInicial(dto.getSaldoInicial());
        }

        return toDto(repo.save(entity));
    }

    @Override
    public void toggleActiva(Long id, Integer empresaId) {
        CuentaBancariaEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        entity.setActiva(!entity.getActiva());
        repo.save(entity);
    }

    @Override
    public List<ConciliacionMayorDto> conciliarConMayor(Integer empresaId) {
        return repo.findByEmpresaIdOrderByNombreAsc(empresaId).stream()
                .filter(cb -> Boolean.TRUE.equals(cb.getActiva()))
                .map(cb -> {
                    BigDecimal saldoTes = cb.getSaldoActual() != null ? cb.getSaldoActual() : BigDecimal.ZERO;
                    PlanCuentaEntity cuenta = cb.getCuentaContableId() != null
                            ? planCuentaRepo.findByIdAndEmpresaId(cb.getCuentaContableId(), empresaId).orElse(null)
                            : null;
                    BigDecimal saldoMayor = cb.getCuentaContableId() != null
                            ? asientoQueryRepo.saldoCuenta(empresaId, cb.getCuentaContableId())
                            : BigDecimal.ZERO;
                    if (saldoMayor == null) saldoMayor = BigDecimal.ZERO;
                    return ConciliacionMayorDto.builder()
                            .cuentaBancariaId(cb.getId())
                            .cuentaBancariaNombre(cb.getNombre())
                            .cuentaContableId(cb.getCuentaContableId())
                            .cuentaContableCodigo(cuenta != null ? cuenta.getCodigo() : null)
                            .cuentaContableNombre(cuenta != null ? cuenta.getNombre() : null)
                            .saldoTesoreria(saldoTes)
                            .saldoMayor(saldoMayor)
                            .diferencia(saldoTes.subtract(saldoMayor))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Toda cuenta bancaria debe estar mapeada a una cuenta contable del grupo
     * disponible (11xx: caja/bancos), activa. Sin esto, sus movimientos no se
     * reflejan en el mayor ni en el balance.
     */
    private void validarCuentaContable(Integer empresaId, Long cuentaContableId) {
        if (cuentaContableId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta bancaria debe tener una cuenta contable (11xx) asignada.");
        }
        var cuenta = planCuentaRepo.findByIdAndEmpresaId(cuentaContableId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cuenta contable asignada no existe."));
        if (!Boolean.TRUE.equals(cuenta.getActiva())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta contable asignada está inactiva.");
        }
        if (cuenta.getCodigo() == null || !cuenta.getCodigo().startsWith("11")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta contable de una cuenta bancaria debe ser del disponible (11xx), "
                            + "p.ej. 1105 Caja o 1110 Bancos.");
        }
    }

    private CuentaBancariaDto toDto(CuentaBancariaEntity e) {
        String cuentaNombre = null;
        if (e.getCuentaContableId() != null) {
            cuentaNombre = planCuentaRepo.findByIdAndEmpresaId(e.getCuentaContableId(), e.getEmpresaId())
                    .map(c -> c.getCodigo() + " - " + c.getNombre())
                    .orElse(null);
        }
        String terceroNombre = null;
        if (e.getTerceroId() != null) {
            terceroNombre = terceroRepo.findByIdAndEmpresaId(e.getTerceroId(), e.getEmpresaId())
                    .map(t -> t.getRazonSocial() != null && !t.getRazonSocial().isBlank()
                            ? t.getRazonSocial()
                            : ((t.getNombres() != null ? t.getNombres() : "") + " "
                                    + (t.getApellidos() != null ? t.getApellidos() : "")).trim())
                    .orElse(null);
        }
        return CuentaBancariaDto.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .tipo(e.getTipo())
                .banco(e.getBanco())
                .numeroCuenta(e.getNumeroCuenta())
                .titular(e.getTitular())
                .terceroId(e.getTerceroId())
                .terceroNombre(terceroNombre)
                .cuentaContableId(e.getCuentaContableId())
                .cuentaContableNombre(cuentaNombre)
                .saldoInicial(e.getSaldoInicial())
                .saldoActual(e.getSaldoActual())
                .activa(e.getActiva())
                .build();
    }
}
