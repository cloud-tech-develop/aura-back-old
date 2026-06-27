package com.cloud_technological.aura_pos.services.implementations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaConfigDto;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.CuentaConfigEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.CuentaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;

@Service
public class ConfiguracionContableServiceImpl implements ConfiguracionContableService {

    @Autowired
    private CuentaConfigJPARepository configRepo;

    @Autowired
    private PlanCuentaJPARepository planRepo;

    @Override
    public PlanCuentaEntity resolverCuenta(Integer empresaId, ConceptoContable concepto) {
        // 1) Override configurado por la empresa
        Optional<CuentaConfigEntity> config = configRepo.findByEmpresaIdAndConcepto(empresaId, concepto);
        if (config.isPresent()) {
            PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(config.get().getCuentaId(), empresaId)
                    .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                    .orElse(null);
            if (cuenta != null) {
                return cuenta;
            }
        }

        // 2) Fallback: código por defecto del concepto
        PlanCuentaEntity porDefecto = planRepo
                .findByEmpresaIdAndCodigo(empresaId, concepto.getCodigoDefault())
                .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                .orElse(null);
        if (porDefecto != null) {
            return porDefecto;
        }

        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "No hay cuenta contable configurada para el concepto '" + concepto.name()
                + "' (" + concepto.getDescripcion() + "). Configure el mapeo o cree la cuenta "
                + concepto.getCodigoDefault() + " en el plan de cuentas.");
    }

    @Override
    public List<CuentaConfigDto> listar(Integer empresaId) {
        Map<ConceptoContable, CuentaConfigEntity> overrides = new LinkedHashMap<>();
        for (CuentaConfigEntity c : configRepo.findByEmpresaId(empresaId)) {
            overrides.put(c.getConcepto(), c);
        }

        List<CuentaConfigDto> result = new ArrayList<>();
        for (ConceptoContable concepto : ConceptoContable.values()) {
            CuentaConfigEntity override = overrides.get(concepto);
            PlanCuentaEntity cuenta = null;
            boolean porDefecto = true;

            if (override != null) {
                cuenta = planRepo.findByIdAndEmpresaId(override.getCuentaId(), empresaId).orElse(null);
                porDefecto = false;
            }
            if (cuenta == null) {
                cuenta = planRepo.findByEmpresaIdAndCodigo(empresaId, concepto.getCodigoDefault()).orElse(null);
                porDefecto = true;
            }

            result.add(CuentaConfigDto.builder()
                    .concepto(concepto.name())
                    .descripcionConcepto(concepto.getDescripcion())
                    .cuentaId(cuenta != null ? cuenta.getId() : null)
                    .codigoCuenta(cuenta != null ? cuenta.getCodigo() : null)
                    .nombreCuenta(cuenta != null ? cuenta.getNombre() : null)
                    .porDefecto(porDefecto)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public CuentaConfigDto actualizar(Integer empresaId, ConceptoContable concepto, Long cuentaId) {
        PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "La cuenta " + cuentaId + " no existe en el plan de cuentas de la empresa"));
        if (!Boolean.TRUE.equals(cuenta.getActiva())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede asignar una cuenta inactiva");
        }

        CuentaConfigEntity config = configRepo.findByEmpresaIdAndConcepto(empresaId, concepto)
                .orElseGet(() -> CuentaConfigEntity.builder()
                        .empresaId(empresaId)
                        .concepto(concepto)
                        .build());
        config.setCuentaId(cuentaId);
        configRepo.save(config);

        return CuentaConfigDto.builder()
                .concepto(concepto.name())
                .descripcionConcepto(concepto.getDescripcion())
                .cuentaId(cuenta.getId())
                .codigoCuenta(cuenta.getCodigo())
                .nombreCuenta(cuenta.getNombre())
                .porDefecto(false)
                .build();
    }

    @Override
    @Transactional
    public void seedDefaults(Integer empresaId) {
        for (ConceptoContable concepto : ConceptoContable.values()) {
            if (configRepo.findByEmpresaIdAndConcepto(empresaId, concepto).isPresent()) {
                continue;
            }
            planRepo.findByEmpresaIdAndCodigo(empresaId, concepto.getCodigoDefault())
                    .ifPresent(cuenta -> configRepo.save(CuentaConfigEntity.builder()
                            .empresaId(empresaId)
                            .concepto(concepto)
                            .cuentaId(cuenta.getId())
                            .build()));
        }
    }
}
