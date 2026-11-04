package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.AfectadoPorDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.PasoFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ProbarFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.PruebaFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ReferenciaFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ValidacionFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.VariableFormulaDto;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Base;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Clase;
import com.cloud_technological.aura_pos.repositories.nomina.ConceptoNominaQueryRepository;
import com.cloud_technological.aura_pos.services.nomina.formula.AnalizadorFormula;
import com.cloud_technological.aura_pos.services.nomina.formula.ErrorFormula;
import com.cloud_technological.aura_pos.services.nomina.formula.EvaluadorFormula;
import com.cloud_technological.aura_pos.services.nomina.formula.VariableFormula;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Editor de fórmulas de conceptos (F3 de docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md):
 * validar, probar con valores de ejemplo, variables disponibles, "afectado por"
 * y las reglas al guardar (conceptos referenciados, orden de cálculo y ciclos).
 */
@Service
public class FormulaConceptoService {

    /** Salario de ejemplo cuando no hay otro dato: 2 SMMLV. */
    private static final BigDecimal DOS = new BigDecimal("2");

    private final ConceptoNominaQueryRepository queryRepo;

    public FormulaConceptoService(ConceptoNominaQueryRepository queryRepo) {
        this.queryRepo = queryRepo;
    }

    /**
     * Valida una fórmula: sintaxis, variables, funciones y que los conceptos
     * referenciados existan (y que no se referencie a sí misma).
     *
     * @param numero concepto que se edita, o null al crear
     */
    public ValidacionFormulaDto validar(Integer empresaId, String formula, Integer numero) {
        ValidacionFormulaDto r = new ValidacionFormulaDto();
        AnalizadorFormula.Analisis analisis;
        try {
            analisis = AnalizadorFormula.analizar(formula);
        } catch (ErrorFormula e) {
            r.setError(e.getMessage());
            r.setPosicion(e.getPosicion());
            return r;
        }
        Map<Integer, ConceptoDto> porNumero = porNumero(empresaId);
        for (Integer n : analisis.conceptos()) {
            ReferenciaFormulaDto ref = new ReferenciaFormulaDto();
            ref.setNumero(n);
            ConceptoDto c = porNumero.get(n);
            ref.setExiste(c != null);
            ref.setNombre(c != null ? c.getNombre() : null);
            r.getConceptos().add(ref);
            if (c == null && r.getError() == null) r.setError("El concepto [" + n + "] no existe o está inactivo.");
            if (n.equals(numero) && r.getError() == null) r.setError("La fórmula no puede usar su propio concepto [" + n + "].");
        }
        r.getVariables().addAll(analisis.variables());
        r.setValida(r.getError() == null);
        return r;
    }

    /** Evalúa una fórmula con los valores que manda el usuario, sin guardar nada. */
    public PruebaFormulaDto probar(ProbarFormulaDto dto) {
        PruebaFormulaDto r = new PruebaFormulaDto();
        try {
            AnalizadorFormula.Analisis analisis = AnalizadorFormula.analizar(dto.getFormula());
            Map<String, BigDecimal> variables = new HashMap<>();
            if (dto.getVariables() != null) {
                dto.getVariables().forEach((k, v) -> variables.put(k.toUpperCase(), v));
            }
            Map<Integer, BigDecimal> conceptos = dto.getConceptos() != null ? dto.getConceptos() : Map.of();
            EvaluadorFormula.Resultado res = EvaluadorFormula.evaluar(analisis.arbol(), variables, conceptos::get);
            res.usados().forEach((k, v) -> r.getPasos().add(new PasoFormulaDto(k, v)));
            r.setValor(res.valor());
        } catch (ErrorFormula e) {
            r.setError(e.getMessage());
        }
        return r;
    }

    /** Variables que puede usar una fórmula, con un valor de ejemplo de la empresa. */
    public List<VariableFormulaDto> variables(Integer empresaId) {
        Map<String, BigDecimal> parametros = queryRepo.parametrosNomina(empresaId);
        BigDecimal smmlv = parametros.getOrDefault("SMMLV", new BigDecimal("1423500"));
        BigDecimal aux = parametros.getOrDefault("AUX_TRANSPORTE", new BigDecimal("200000"));
        BigDecimal salario = smmlv.multiply(DOS);
        Map<String, BigDecimal> ejemplos = Map.ofEntries(
                Map.entry("SALARIO_BASICO", salario),
                Map.entry("SALARIO_PROPORCIONAL", salario),
                Map.entry("DIAS_LIQUIDADOS", new BigDecimal("30")),
                Map.entry("SMMLV", smmlv),
                Map.entry("AUX_TRANSPORTE", aux),
                Map.entry("AUXILIO_PERIODO", aux),
                Map.entry("BASE_IBC", salario),
                Map.entry("BASE_PRESTACIONAL", salario.add(aux)),
                Map.entry("DEVENGADO_TOTAL", salario.add(aux)),
                Map.entry("NOVEDADES_SALARIALES", BigDecimal.ZERO),
                Map.entry("NOVEDADES_NO_SALARIALES", BigDecimal.ZERO),
                Map.entry("ANTIGUEDAD_ANIOS", BigDecimal.ONE),
                Map.entry("ANTIGUEDAD_MESES", new BigDecimal("12")));
        List<VariableFormulaDto> out = new ArrayList<>();
        for (VariableFormula v : VariableFormula.values()) {
            VariableFormulaDto d = new VariableFormulaDto();
            d.setNombre(v.name());
            d.setDescripcion(v.getDescripcion());
            d.setEjemplo(ejemplos.get(v.name()));
            out.add(d);
        }
        return out;
    }

    /**
     * Qué alimenta la base de un concepto ("Afectado por"). Depende de su base:
     * el salario, el auxilio, los conceptos que constituyen IBC o hacen base
     * prestacional, todo lo devengado, o lo que use su fórmula.
     */
    public List<AfectadoPorDto> afectadoPor(ConceptoDto c, Integer empresaId) {
        List<AfectadoPorDto> out = new ArrayList<>();
        List<ConceptoDto> todos = queryRepo.visibles(empresaId);
        String base = c.getBase();
        if (Base.FORMULA.equals(base)) {
            try {
                AnalizadorFormula.Analisis a = AnalizadorFormula.analizar(c.getFormula());
                Map<Integer, ConceptoDto> porNumero = porNumero(todos);
                for (Integer n : a.conceptos()) {
                    ConceptoDto ref = porNumero.get(n);
                    out.add(fila(n, ref != null ? ref.getNombre() : "Concepto no disponible",
                            ref != null ? ref.getClase() : null, "Se usa en la fórmula"));
                }
                for (String v : a.variables()) {
                    out.add(fila(null, v, null, VariableFormula.de(v).map(VariableFormula::getDescripcion).orElse("")));
                }
            } catch (ErrorFormula e) {
                // Fórmula inválida: no hay de qué depender.
            }
            return out;
        }
        for (ConceptoDto o : todos) {
            if (o.getNumero().equals(c.getNumero())) continue;
            boolean devengado = Clase.DEVENGADO.equals(o.getClase());
            boolean esSalario = "SALARIO".equals(o.getCodigo());
            boolean esAuxilio = "AUX_TRANSP".equals(o.getCodigo());
            String motivo = switch (base) {
                case Base.SALARIO -> esSalario ? "Salario de los días liquidados" : null;
                case Base.SALARIO_MAS_AUXILIO -> esSalario || esAuxilio ? "Salario y auxilio de transporte"
                        : Boolean.TRUE.equals(o.getHaceBasePrestacional()) && devengado ? "Hace base prestacional" : null;
                case Base.IBC -> esSalario ? "Salario de los días cotizados"
                        : devengado && !esAuxilio && Boolean.TRUE.equals(o.getConstituyeIbc()) ? "Constituye IBC" : null;
                case Base.DEVENGADO_TOTAL -> devengado ? "Es devengado" : null;
                default -> null;
            };
            if (motivo != null) out.add(fila(o.getNumero(), o.getNombre(), o.getClase(), motivo));
        }
        return out;
    }

    /**
     * Reglas al guardar un concepto con fórmula: debe ser válida, sus conceptos
     * deben existir, no puede haber ciclos entre fórmulas y el orden de cálculo
     * queda después de todo lo que usa.
     *
     * @throws GlobalException 400 con la causa
     */
    public void prepararParaGuardar(ConceptoNominaEntity c, Integer empresaId) {
        if (!Base.FORMULA.equals(c.getBase())) {
            c.setFormula(null);
            return;
        }
        ValidacionFormulaDto v = validar(empresaId, c.getFormula(), c.getNumero());
        if (!v.isValida()) throw new GlobalException(HttpStatus.BAD_REQUEST, "Fórmula: " + v.getError());

        List<ConceptoDto> todos = queryRepo.visibles(empresaId);
        Map<Integer, ConceptoDto> porNumero = porNumero(todos);
        // Grafo de fórmulas con la versión nueva de este concepto.
        Map<Integer, Set<Integer>> grafo = new HashMap<>();
        for (ConceptoDto o : todos) {
            if (Base.FORMULA.equals(o.getBase()) && o.getFormula() != null && !o.getNumero().equals(c.getNumero())) {
                grafo.put(o.getNumero(), referencias(o.getFormula()));
            }
        }
        Set<Integer> propias = referencias(c.getFormula());
        grafo.put(c.getNumero(), propias);
        if (hayCiclo(c.getNumero(), grafo, new HashSet<>()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fórmula forma un ciclo: un concepto termina dependiendo de sí mismo a través de otros.");

        // El orden de cálculo queda después de todo lo que la fórmula usa.
        int minimo = propias.stream().map(porNumero::get).filter(x -> x != null)
                .mapToInt(ConceptoDto::getOrden).max().orElse(0) + 1;
        if (c.getOrden() == null || c.getOrden() < minimo) c.setOrden(minimo);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private Map<Integer, ConceptoDto> porNumero(Integer empresaId) {
        return porNumero(queryRepo.visibles(empresaId));
    }

    private static Map<Integer, ConceptoDto> porNumero(List<ConceptoDto> conceptos) {
        Map<Integer, ConceptoDto> m = new HashMap<>();
        conceptos.forEach(x -> m.putIfAbsent(x.getNumero(), x));
        return m;
    }

    private static Set<Integer> referencias(String formula) {
        try {
            return AnalizadorFormula.analizar(formula).conceptos();
        } catch (ErrorFormula e) {
            return Set.of();
        }
    }

    /** DFS: ¿desde {@code nodo} se vuelve a un nodo del camino actual? */
    private static boolean hayCiclo(Integer nodo, Map<Integer, Set<Integer>> grafo, Set<Integer> camino) {
        if (!camino.add(nodo)) return true;
        for (Integer siguiente : grafo.getOrDefault(nodo, Set.of())) {
            if (hayCiclo(siguiente, grafo, camino)) return true;
        }
        camino.remove(nodo);
        return false;
    }

    private static AfectadoPorDto fila(Integer numero, String nombre, String clase, String motivo) {
        AfectadoPorDto d = new AfectadoPorDto();
        d.setNumero(numero);
        d.setNombre(nombre);
        d.setClase(clase);
        d.setMotivo(motivo);
        return d;
    }
}
