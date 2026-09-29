package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.ConceptoNominaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Catálogo de conceptos (Fase 3).
 *
 * <p>Resuelve dos cosas que el motor necesita y hoy no tiene:
 * <ul>
 *   <li><b>Vigencia:</b> qué tarifa regía en la fecha que se liquida. Sin esto
 *       no se puede reliquidar un período viejo con las tarifas de su momento.</li>
 *   <li><b>Precedencia:</b> si una empresa personalizó un concepto, gana el
 *       suyo sobre el global de ley.</li>
 * </ul>
 */
@Slf4j
@Service
public class ConceptoNominaService {

    private final ConceptoNominaJPARepository conceptoRepo;

    public ConceptoNominaService(ConceptoNominaJPARepository conceptoRepo) {
        this.conceptoRepo = conceptoRepo;
    }

    /**
     * Conceptos que aplican a una empresa en una fecha, listos para iterar.
     *
     * <p>Resuelve la precedencia: si la empresa tiene su versión de un código,
     * el global de ese código se descarta. El orden de cálculo se respeta
     * ({@code ORDER BY orden}).
     */
    @Transactional(readOnly = true)
    public List<ConceptoNominaEntity> vigentesPara(Integer empresaId, LocalDate fecha) {
        List<ConceptoNominaEntity> todos = conceptoRepo.findVigentes(empresaId, fecha);

        // Por código, gana el de la empresa sobre el global.
        Map<String, ConceptoNominaEntity> porCodigo = new LinkedHashMap<>();
        for (ConceptoNominaEntity c : todos) {
            porCodigo.merge(c.getCodigo(), c,
                    (existente, nuevo) -> existente.esGlobal() ? nuevo : existente);
        }
        return porCodigo.values().stream()
                .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
                .toList();
    }

    /**
     * Un concepto por código, vigente en una fecha.
     *
     * <p>Falla si no existe en vez de devolver null: si el motor pide
     * {@code DED_SALUD} y no hay concepto vigente, es un error de configuración
     * que debe detener la liquidación, no producir un cero silencioso.
     */
    @Transactional(readOnly = true)
    public ConceptoNominaEntity porCodigo(Integer empresaId, String codigo, LocalDate fecha) {
        List<ConceptoNominaEntity> candidatos =
                conceptoRepo.findVigentePorCodigo(empresaId, codigo, fecha);

        if (candidatos.isEmpty()) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No hay concepto vigente con código '" + codigo + "' en " + fecha
                    + ". Revise el catálogo de conceptos.");
        }
        // NULLS LAST: el de la empresa viene primero si existe.
        return candidatos.get(0);
    }

    /**
     * Alta de un concepto propio de una empresa.
     *
     * <p>Valida que la vigencia no se solape con otra versión del mismo código:
     * dos conceptos vigentes con el mismo código en la misma fecha harían que
     * el resultado dependa del orden de la query.
     */
    @Transactional
    public ConceptoNominaEntity crear(ConceptoNominaEntity concepto) {
        validar(concepto);
        validarSinSolape(concepto);
        return conceptoRepo.save(concepto);
    }

    private void validar(ConceptoNominaEntity c) {
        if (c.getVigenteDesde() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "vigente_desde es obligatorio");
        }
        if (c.getVigenteHasta() != null && c.getVigenteHasta().isBefore(c.getVigenteDesde())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "vigente_hasta no puede ser anterior a vigente_desde");
        }
        // Coherencia base ↔ valor: un FIJO sin valor, o un porcentual sin
        // porcentaje, produce ceros silenciosos en la liquidación.
        if (ConceptoNominaEntity.Base.FIJO.equals(c.getBase()) && c.getValorFijo() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un concepto con base FIJO exige valor_fijo");
        }
        boolean esPorcentual = !ConceptoNominaEntity.Base.FIJO.equals(c.getBase())
                            && !ConceptoNominaEntity.Base.MANUAL.equals(c.getBase());
        if (esPorcentual && c.getPorcentaje() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un concepto con base " + c.getBase() + " exige porcentaje");
        }
    }

    private void validarSinSolape(ConceptoNominaEntity nuevo) {
        List<ConceptoNominaEntity> mismos = conceptoRepo
                .findByCodigoOrderByVigenteDesdeDesc(nuevo.getCodigo()).stream()
                .filter(c -> java.util.Objects.equals(c.getEmpresaId(), nuevo.getEmpresaId()))
                .filter(c -> !c.getId().equals(nuevo.getId()))
                .toList();

        for (ConceptoNominaEntity otro : mismos) {
            if (seSolapan(nuevo, otro)) {
                throw new GlobalException(HttpStatus.CONFLICT,
                        "El concepto '" + nuevo.getCodigo() + "' ya tiene una versión vigente "
                        + "entre " + otro.getVigenteDesde() + " y "
                        + (otro.getVigenteHasta() != null ? otro.getVigenteHasta() : "indefinido")
                        + ". Cierre esa vigencia antes de crear una nueva.");
            }
        }
    }

    private boolean seSolapan(ConceptoNominaEntity a, ConceptoNominaEntity b) {
        LocalDate finA = a.getVigenteHasta() != null ? a.getVigenteHasta() : LocalDate.MAX;
        LocalDate finB = b.getVigenteHasta() != null ? b.getVigenteHasta() : LocalDate.MAX;
        return !a.getVigenteDesde().isAfter(finB) && !b.getVigenteDesde().isAfter(finA);
    }
}
