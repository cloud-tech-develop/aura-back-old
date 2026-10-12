package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.contabilidad.ConceptoContableNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.contabilidad.GuardarConceptoContableDto;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.NominaConceptoContableEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables.NominaConceptoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables.NominaConceptoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables.NominaConceptoContableQueryRepository.Cambio;
import com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables.NominaConceptoContableQueryRepository.Concepto;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.NominaConceptoContableService;
import com.cloud_technological.aura_pos.services.nomina.contabilidad.ReglaContableNomina;
import com.cloud_technological.aura_pos.services.nomina.contabilidad.ReglaContableNomina.Regla;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link NominaConceptoContableService}. */
@Service
public class NominaConceptoContableServiceImpl implements NominaConceptoContableService {

    /** Cómo se lee cada tipo de tercero en la pantalla. */
    private static final Map<String, String> TERCEROS = Map.of(
            "EMPLEADO", "Empleado",
            "EPS", "EPS del empleado",
            "AFP", "Fondo de pensiones del empleado",
            "ARL", "ARL del empleado",
            "CCF", "Caja de compensación del empleado",
            "DIAN", "DIAN",
            "NINGUNO", "Sin tercero",
            "FIJO", "Tercero fijo");

    private final NominaConceptoContableJPARepository jpaRepository;
    private final NominaConceptoContableQueryRepository queryRepository;
    private final ConfiguracionContableService cuentas;

    public NominaConceptoContableServiceImpl(NominaConceptoContableJPARepository jpaRepository,
            NominaConceptoContableQueryRepository queryRepository,
            ConfiguracionContableService cuentas) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.cuentas = cuentas;
    }

    @Override
    public List<ConceptoContableNominaDto> listar(Integer empresaId) {
        List<Concepto> conceptos = queryRepository.conceptos(empresaId);
        Map<String, Cambio> cambios = queryRepository.cambios(empresaId);
        Map<Long, String> etiquetas = queryRepository.etiquetasCuentas(cambios.values().stream()
                .flatMap(c -> Stream.of(c.cuentaDebitoId(), c.cuentaCreditoId()))
                .filter(Objects::nonNull).distinct().toList());
        Map<Long, String> terceros = queryRepository.nombresTerceros(cambios.values().stream()
                .map(Cambio::terceroFijoId).filter(Objects::nonNull).distinct().toList());
        Map<ConceptoContable, String> porDefecto = new HashMap<>();

        List<ConceptoContableNominaDto> out = new ArrayList<>();
        for (Concepto c : conceptos)
            out.add(armar(c, cambios.get(c.codigo()), etiquetas, terceros, porDefecto, empresaId));
        return out;
    }

    @Override
    @Transactional
    public ConceptoContableNominaDto guardar(String codigo, GuardarConceptoContableDto dto, Integer empresaId) {
        Concepto concepto = buscarConcepto(codigo, empresaId);
        Regla regla = ReglaContableNomina.para(concepto.codigo(), concepto.clase());
        validar(dto, regla, empresaId);

        NominaConceptoContableEntity e = queryRepository.idCambio(empresaId, codigo)
                .flatMap(jpaRepository::findById)
                .orElseGet(() -> {
                    NominaConceptoContableEntity n = new NominaConceptoContableEntity();
                    n.setEmpresaId(empresaId);
                    n.setConceptoCodigo(codigo);
                    n.setCreatedAt(LocalDateTime.now());
                    return n;
                });
        e.setCuentaDebitoId(dto.getCuentaDebitoId());
        e.setCuentaCreditoId(dto.getCuentaCreditoId());
        e.setTerceroCredito(dto.getTerceroCredito());
        e.setTerceroFijoId("FIJO".equals(dto.getTerceroCredito()) ? dto.getTerceroFijoId() : null);
        e.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(e);
        return uno(codigo, empresaId);
    }

    @Override
    @Transactional
    public ConceptoContableNominaDto restablecer(String codigo, Integer empresaId) {
        buscarConcepto(codigo, empresaId);
        queryRepository.idCambio(empresaId, codigo).ifPresent(jpaRepository::deleteById);
        jpaRepository.flush();
        return uno(codigo, empresaId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private Concepto buscarConcepto(String codigo, Integer empresaId) {
        return queryRepository.conceptos(empresaId).stream()
                .filter(c -> c.codigo().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "El concepto " + codigo + " no existe o está inactivo."));
    }

    /** Lo que se cambia debe existir en el concepto y ser válido para la empresa. */
    private void validar(GuardarConceptoContableDto dto, Regla regla, Integer empresaId) {
        if (dto.getCuentaDebitoId() != null) {
            if (regla.debito() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Este concepto no tiene débito propio: es parte del neto a pagar.");
            exigirCuenta(dto.getCuentaDebitoId(), empresaId);
        }
        boolean cambiaCredito = dto.getCuentaCreditoId() != null || dto.getTerceroCredito() != null;
        if (cambiaCredito && regla.credito() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Este concepto no tiene crédito propio: lo devengado va al neto por pagar del empleado.");
        if (dto.getCuentaCreditoId() != null) exigirCuenta(dto.getCuentaCreditoId(), empresaId);
        if ("FIJO".equals(dto.getTerceroCredito())) {
            if (dto.getTerceroFijoId() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija el tercero fijo.");
            if (!queryRepository.terceroValido(dto.getTerceroFijoId(), empresaId))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El tercero elegido no es válido.");
        }
    }

    private void exigirCuenta(Long cuentaId, Integer empresaId) {
        if (!queryRepository.cuentaDeMovimiento(cuentaId, empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta debe ser auxiliar (de movimiento), activa y del plan de la empresa.");
    }

    /** Un concepto con su valor por defecto y su cambio, recién leídos. */
    private ConceptoContableNominaDto uno(String codigo, Integer empresaId) {
        return listar(empresaId).stream().filter(c -> c.getCodigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto no encontrado"));
    }

    private ConceptoContableNominaDto armar(Concepto c, Cambio cambio, Map<Long, String> etiquetas,
            Map<Long, String> terceros, Map<ConceptoContable, String> porDefecto, Integer empresaId) {
        Regla regla = ReglaContableNomina.para(c.codigo(), c.clase());
        ConceptoContableNominaDto dto = new ConceptoContableNominaDto();
        dto.setCodigo(c.codigo());
        dto.setNombre(c.nombre());
        dto.setClase(c.clase());
        dto.setAplicaDebito(regla.debito() != null);
        dto.setAplicaCredito(regla.credito() != null);
        if (regla.debito() != null)
            dto.setDebitoDefecto(porDefecto.computeIfAbsent(regla.debito(), k -> etiquetaPorDefecto(empresaId, k)));
        dto.setCreditoDefecto(regla.credito() != null
                ? porDefecto.computeIfAbsent(regla.credito(), k -> etiquetaPorDefecto(empresaId, k))
                : "Neto por pagar al empleado");
        dto.setTerceroDefecto(regla.credito() != null ? TERCEROS.get(regla.tercero().name()) : "Empleado");
        if (cambio != null) {
            dto.setPersonalizado(true);
            dto.setCuentaDebitoId(cambio.cuentaDebitoId());
            dto.setCuentaDebito(etiquetas.get(cambio.cuentaDebitoId()));
            dto.setCuentaCreditoId(cambio.cuentaCreditoId());
            dto.setCuentaCredito(etiquetas.get(cambio.cuentaCreditoId()));
            dto.setTerceroCredito(cambio.terceroCredito());
            dto.setTerceroFijoId(cambio.terceroFijoId());
            dto.setTerceroFijo(terceros.get(cambio.terceroFijoId()));
        }
        return dto;
    }

    /** "510506 — Sueldos" de la parametrización; si falta, lo dice. */
    private String etiquetaPorDefecto(Integer empresaId, ConceptoContable concepto) {
        try {
            PlanCuentaEntity cuenta = cuentas.resolverCuenta(empresaId, concepto);
            return cuenta.getCodigo() + " — " + cuenta.getNombre();
        } catch (RuntimeException e) {
            return "Sin configurar (" + concepto.getCodigoDefault() + ")";
        }
    }
}
