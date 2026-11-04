package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.AfectadoPorDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.BuscarConceptoParams;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoTipoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.CreateConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.FormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.NuevaVigenciaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ProbarFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.PruebaFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.UpdateConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ValidacionFormulaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.VariableFormulaDto;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Base;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Clase;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Entrada;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Frecuencia;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.RedondeoModo;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.RedondeoPrecision;
import com.cloud_technological.aura_pos.services.implementations.ConceptoNominaService;
import com.cloud_technological.aura_pos.services.implementations.FormulaConceptoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Catálogo de conceptos de nómina (Fase 3; ampliado en V230).
 *
 * <p>El usuario identifica los conceptos por su número. La clave interna de
 * texto no se escribe ni se edita (docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md).
 */
@RestController
@RequestMapping("/api/concepto")
public class ConceptoNominaController {

    private final ConceptoNominaService conceptoService;
    private final FormulaConceptoService formulas;
    private final SecurityUtils securityUtils;

    public ConceptoNominaController(ConceptoNominaService conceptoService, FormulaConceptoService formulas,
                                    SecurityUtils securityUtils) {
        this.conceptoService = conceptoService;
        this.formulas = formulas;
        this.securityUtils = securityUtils;
    }

    /**
     * Conceptos vigentes en una fecha, con la precedencia resuelta: si la empresa
     * personalizó un concepto, gana el suyo.
     *
     * @param fecha por defecto, hoy
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ConceptoDto>>> vigentes(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate f = fecha != null ? fecha : LocalDate.now();

        List<ConceptoNominaEntity> vigentes = conceptoService.vigentesPara(empresaId, f);

        // Qué conceptos tiene personalizados la empresa: el front lo usa para
        // marcar cuáles se pueden editar y cuáles son de ley.
        Set<String> personalizados = vigentes.stream()
                .filter(c -> !c.esGlobal())
                .map(ConceptoNominaEntity::getCodigo)
                .collect(Collectors.toSet());

        List<ConceptoDto> result = vigentes.stream()
                .map(c -> aDto(c, personalizados))
                .toList();

        return ok("Listado exitoso", result);
    }

    /**
     * Buscador avanzado: texto (número, nombre o tipo) y filtros por clase, tipo,
     * grupo, entrada y frecuencia.
     */
    @PostMapping("/buscar")
    public ResponseEntity<ApiResponse<PageImpl<ConceptoDto>>> buscar(
            @RequestBody PageableDto<BuscarConceptoParams> pageable) {
        return ok("Listado exitoso", conceptoService.buscar(pageable, securityUtils.getEmpresaId()));
    }

    /** Un concepto con los nombres de tipo y grupo. */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<ConceptoDto>> obtener(@PathVariable Long id) {
        return ok("Concepto", conceptoService.obtener(id, securityUtils.getEmpresaId()));
    }

    /**
     * Alta de un concepto propio. Responde 409 si el número ya está en uso o si
     * la vigencia se solapa con otra versión del mismo concepto.
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ConceptoDto>> crear(@RequestBody CreateConceptoDto dto) {
        ConceptoDto guardado = conceptoService.crear(securityUtils.getEmpresaId(), dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Concepto creado exitosamente", false, guardado),
                HttpStatus.CREATED);
    }

    /**
     * Edita un concepto. Si es global, guarda la versión de la empresa; en uno de
     * ley solo cambian los campos descriptivos.
     */
    @PutMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<ConceptoDto>> actualizar(@PathVariable Long id,
                                                               @RequestBody UpdateConceptoDto dto) {
        return ok("Concepto actualizado", conceptoService.actualizar(id, securityUtils.getEmpresaId(), dto));
    }

    /**
     * Nueva vigencia de un concepto propio: cambia el cálculo desde una fecha y
     * cierra la versión anterior.
     */
    @PostMapping("/{id:\\d+}/vigencia")
    public ResponseEntity<ApiResponse<ConceptoDto>> nuevaVigencia(@PathVariable Long id,
                                                                  @RequestBody NuevaVigenciaDto dto) {
        ConceptoDto nueva = conceptoService.nuevaVigencia(id, securityUtils.getEmpresaId(), dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Vigencia creada", false, nueva),
                HttpStatus.CREATED);
    }

    /** Valida una fórmula: sintaxis, variables, funciones y conceptos referenciados. */
    @PostMapping("/formula/validar")
    public ResponseEntity<ApiResponse<ValidacionFormulaDto>> validarFormula(@RequestBody FormulaDto dto) {
        return ok("Validación", formulas.validar(securityUtils.getEmpresaId(), dto.getFormula(), dto.getNumero()));
    }

    /** Prueba una fórmula con valores de ejemplo, sin guardar. */
    @PostMapping("/formula/probar")
    public ResponseEntity<ApiResponse<PruebaFormulaDto>> probarFormula(@RequestBody ProbarFormulaDto dto) {
        return ok("Prueba", formulas.probar(dto));
    }

    /** Variables que puede usar una fórmula, con un valor de ejemplo. */
    @GetMapping("/formula/variables")
    public ResponseEntity<ApiResponse<List<VariableFormulaDto>>> variablesFormula() {
        return ok("Variables", formulas.variables(securityUtils.getEmpresaId()));
    }

    /** Qué alimenta la base del concepto ("Afectado por"). */
    @GetMapping("/{id:\\d+}/afectado-por")
    public ResponseEntity<ApiResponse<List<AfectadoPorDto>>> afectadoPor(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Afectado por", formulas.afectadoPor(conceptoService.obtener(id, empresaId), empresaId));
    }

    /** Catálogo de tipos de concepto (qué es cada concepto). */
    @GetMapping("/tipos")
    public ResponseEntity<ApiResponse<List<ConceptoTipoDto>>> tipos() {
        return ok("Consulta exitosa", conceptoService.tipos());
    }

    /** Número que se propone para un concepto nuevo. */
    @GetMapping("/siguiente-numero")
    public ResponseEntity<ApiResponse<Integer>> siguienteNumero() {
        return ok("Consulta exitosa", conceptoService.siguienteNumero(securityUtils.getEmpresaId()));
    }

    /** Opciones de los enums, para que el front no las duplique. */
    @GetMapping("/opciones")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> opciones() {
        return ok("Consulta exitosa", Map.of(
                "clase", Clase.TODAS,
                "base", List.of(Base.SALARIO, Base.SALARIO_MAS_AUXILIO, Base.IBC, Base.DEVENGADO_TOTAL,
                        Base.FIJO, Base.MANUAL, Base.NOVEDAD, Base.FORMULA),
                "entrada", Entrada.TODAS,
                "redondeoPrecision", RedondeoPrecision.TODAS,
                "redondeoModo", RedondeoModo.TODOS,
                "frecuencia", Frecuencia.TODAS));
    }

    private ConceptoDto aDto(ConceptoNominaEntity c, Set<String> personalizados) {
        ConceptoDto d = new ConceptoDto();
        d.setId(c.getId());
        d.setEmpresaId(c.getEmpresaId());
        d.setNumero(c.getNumero());
        d.setCodigo(c.getCodigo());
        d.setNombre(c.getNombre());
        d.setClase(c.getClase());
        d.setTipoId(c.getTipoId());
        d.setEntrada(c.getEntrada());
        d.setRedondeoPrecision(c.getRedondeoPrecision());
        d.setRedondeoModo(c.getRedondeoModo());
        d.setFrecuencia(c.getFrecuencia());
        d.setGrupoId(c.getGrupoId());
        d.setAplicaArt383(c.getAplicaArt383());
        d.setDeLey(c.getDeLey());
        d.setDescripcion(c.getDescripcion());
        d.setFormula(c.getFormula());
        d.setHaceBasePrestacional(c.getHaceBasePrestacional());
        d.setCentroCostoId(c.getCentroCostoId());
        d.setConstituyeIbc(c.getConstituyeIbc());
        d.setBase(c.getBase());
        d.setPorcentaje(c.getPorcentaje());
        d.setValorFijo(c.getValorFijo());
        d.setVigenteDesde(c.getVigenteDesde());
        d.setVigenteHasta(c.getVigenteHasta());
        d.setCodigoDian(c.getCodigoDian());
        d.setOrden(c.getOrden());
        d.setActivo(c.getActivo());
        d.setEsGlobal(c.esGlobal());
        d.setPersonalizado(personalizados.contains(c.getCodigo()));
        return d;
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
