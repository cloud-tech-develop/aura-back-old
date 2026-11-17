package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.BuscarConceptoParams;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoTipoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.CreateConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.NuevaVigenciaDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.UpdateConceptoDto;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Base;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Clase;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Entrada;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.Frecuencia;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.RedondeoModo;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.RedondeoPrecision;
import com.cloud_technological.aura_pos.repositories.nomina.ConceptoNominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.ConceptoNominaQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

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
    private final ConceptoNominaQueryRepository queryRepo;
    private final FormulaConceptoService formulas;

    public ConceptoNominaService(ConceptoNominaJPARepository conceptoRepo,
                                 ConceptoNominaQueryRepository queryRepo,
                                 FormulaConceptoService formulas) {
        this.conceptoRepo = conceptoRepo;
        this.queryRepo = queryRepo;
        this.formulas = formulas;
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
     * Buscador avanzado de conceptos (pantalla de conceptos y agregar al cargo).
     *
     * @param pageable  página, texto y filtros
     * @param empresaId empresa
     * @return página de conceptos visibles para la empresa, por número
     */
    @Transactional(readOnly = true)
    public PageImpl<ConceptoDto> buscar(PageableDto<BuscarConceptoParams> pageable, Integer empresaId) {
        return queryRepo.buscar(pageable, empresaId);
    }

    /** Catálogo de tipos de concepto para el select. */
    @Transactional(readOnly = true)
    public List<ConceptoTipoDto> tipos() {
        return queryRepo.tipos();
    }

    /** Número que se propone al crear un concepto. */
    @Transactional(readOnly = true)
    public int siguienteNumero(Integer empresaId) {
        return queryRepo.siguienteNumero(empresaId);
    }

    /**
     * Alta de un concepto propio de la empresa.
     *
     * <p>El usuario no escribe código de texto: la clave interna se genera a
     * partir del número ({@code CN<numero>}). Si no llega número, se asigna el
     * siguiente libre.
     *
     * @param empresaId empresa dueña
     * @param dto       datos del concepto
     * @return el concepto guardado, con nombres de tipo y grupo
     */
    @Transactional
    public ConceptoDto crear(Integer empresaId, CreateConceptoDto dto) {
        Integer numero = dto.getNumero() != null ? dto.getNumero() : queryRepo.siguienteNumero(empresaId);
        if (numero <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código del concepto debe ser un número mayor que cero");
        }
        if (queryRepo.numeroOcupado(empresaId, numero, null)) {
            throw new GlobalException(HttpStatus.CONFLICT, "Ya existe un concepto con el código " + numero);
        }
        if (dto.getTipoId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El tipo del concepto es obligatorio");
        }

        ConceptoNominaEntity c = new ConceptoNominaEntity();
        c.setEmpresaId(empresaId);
        c.setNumero(numero);
        c.setCodigo(claveInterna(empresaId, numero));
        c.setNombre(dto.getNombre());
        c.setClase(dto.getClase());
        c.setTipoId(dto.getTipoId());
        c.setEntrada(valorO(dto.getEntrada(), Entrada.NINGUNA));
        c.setRedondeoPrecision(valorO(dto.getRedondeoPrecision(), RedondeoPrecision.NINGUNO));
        c.setRedondeoModo(valorO(dto.getRedondeoModo(), RedondeoModo.AUTOMATICO));
        c.setFrecuencia(valorO(dto.getFrecuencia(), Frecuencia.CADA_PAGO));
        c.setGrupoId(dto.getGrupoId());
        c.setAplicaArt383(Boolean.TRUE.equals(dto.getAplicaArt383()));
        c.setDescripcion(dto.getDescripcion());
        c.setFormula(dto.getFormula());
        c.setHaceBasePrestacional(Boolean.TRUE.equals(dto.getHaceBasePrestacional()));
        c.setCentroCostoId(dto.getCentroCostoId());
        c.setDeLey(Boolean.FALSE);
        c.setConstituyeIbc(dto.getConstituyeIbc() == null || dto.getConstituyeIbc());
        c.setBase(dto.getBase());
        c.setPorcentaje(dto.getPorcentaje());
        c.setValorFijo(dto.getValorFijo());
        c.setVigenteDesde(dto.getVigenteDesde());
        c.setVigenteHasta(dto.getVigenteHasta());
        c.setCodigoDian(dto.getCodigoDian());
        c.setOrden(dto.getOrden() != null ? dto.getOrden() : 100);
        c.setActivo(Boolean.TRUE);

        validar(c);
        validarCentroCosto(c, empresaId);
        formulas.prepararParaGuardar(c, empresaId);
        validarSinSolape(c);
        ConceptoNominaEntity guardado = conceptoRepo.save(c);
        return obtener(guardado.getId(), empresaId);
    }

    /**
     * Edita un concepto.
     *
     * <p>Si es global, se edita la versión de la empresa: se crea la primera vez
     * (personalización) con la misma clave, número y vigencia. En un concepto de
     * ley solo cambian los campos descriptivos; clase, tipo y cálculo quedan como
     * los dejó la ley, y no se puede desactivar (el motor lo seguiría liquidando
     * desde el global).
     *
     * <p>La edición es en el lugar: no crea una versión de vigencia nueva.
     *
     * @param id        concepto a editar (global o propio)
     * @param empresaId empresa que edita
     * @param dto       datos nuevos; los nulos no cambian
     * @return el concepto de la empresa ya guardado
     */
    @Transactional
    public ConceptoDto actualizar(Long id, Integer empresaId, UpdateConceptoDto dto) {
        ConceptoNominaEntity origen = conceptoRepo.findById(id)
                .filter(c -> c.esGlobal() || empresaId.equals(c.getEmpresaId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto no encontrado"));
        ConceptoNominaEntity c = origen.esGlobal() ? personalizacion(origen, empresaId) : origen;
        boolean deLey = Boolean.TRUE.equals(c.getDeLey());

        if (dto.getNombre() != null) c.setNombre(dto.getNombre());
        if (dto.getEntrada() != null) c.setEntrada(dto.getEntrada());
        if (dto.getRedondeoPrecision() != null) c.setRedondeoPrecision(dto.getRedondeoPrecision());
        if (dto.getRedondeoModo() != null) c.setRedondeoModo(dto.getRedondeoModo());
        if (dto.getFrecuencia() != null) c.setFrecuencia(dto.getFrecuencia());
        if (dto.getAplicaArt383() != null) c.setAplicaArt383(dto.getAplicaArt383());
        c.setGrupoId(dto.getGrupoId());
        c.setDescripcion(dto.getDescripcion());
        // Contabilidad y bases de la empresa: se pueden ajustar también en uno de ley.
        if (dto.getHaceBasePrestacional() != null) c.setHaceBasePrestacional(dto.getHaceBasePrestacional());
        c.setCentroCostoId(dto.getCentroCostoId());

        if (deLey) {
            if (Boolean.FALSE.equals(dto.getActivo())) {
                throw new GlobalException(HttpStatus.CONFLICT,
                        "Un concepto de ley no se puede desactivar: quítelo de los cargos donde no aplique");
            }
        } else {
            if (dto.getClase() != null) c.setClase(dto.getClase());
            if (dto.getTipoId() != null) c.setTipoId(dto.getTipoId());
            if (dto.getConstituyeIbc() != null) c.setConstituyeIbc(dto.getConstituyeIbc());
            if (dto.getBase() != null) c.setBase(dto.getBase());
            c.setPorcentaje(dto.getPorcentaje());
            c.setValorFijo(dto.getValorFijo());
            c.setFormula(dto.getFormula());
            c.setCodigoDian(dto.getCodigoDian());
            if (dto.getOrden() != null) c.setOrden(dto.getOrden());
            if (dto.getActivo() != null) c.setActivo(dto.getActivo());
        }

        validar(c);
        validarCentroCosto(c, empresaId);
        if (!deLey) formulas.prepararParaGuardar(c, empresaId);
        ConceptoNominaEntity guardado = conceptoRepo.saveAndFlush(c);  // obtener() relee con SQL
        return obtener(guardado.getId(), empresaId);
    }

    /**
     * Crea una vigencia nueva de un concepto propio de la empresa.
     *
     * <p>La nueva versión conserva clave, número, nombre y demás datos; solo cambia
     * el cálculo. La versión de la que se parte se cierra el día anterior a la
     * nueva fecha. Los conceptos de ley no cambian tarifa desde la empresa.
     *
     * @param id        versión de la que se parte
     * @param empresaId empresa dueña
     * @param dto       cálculo nuevo y fecha desde la que rige
     * @return la versión nueva
     */
    @Transactional
    public ConceptoDto nuevaVigencia(Long id, Integer empresaId, NuevaVigenciaDto dto) {
        ConceptoNominaEntity actual = conceptoRepo.findById(id)
                .filter(c -> empresaId.equals(c.getEmpresaId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto no encontrado"));
        if (Boolean.TRUE.equals(actual.getDeLey())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Los conceptos de ley no cambian de tarifa desde la empresa");
        }
        if (dto.getVigenteDesde() == null || !dto.getVigenteDesde().isAfter(actual.getVigenteDesde())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nueva vigencia debe empezar después del " + actual.getVigenteDesde());
        }

        ConceptoNominaEntity nueva = copiarParaEmpresa(actual, empresaId);
        nueva.setGrupoId(actual.getGrupoId());
        if (dto.getBase() != null) nueva.setBase(dto.getBase());
        if (dto.getConstituyeIbc() != null) nueva.setConstituyeIbc(dto.getConstituyeIbc());
        nueva.setPorcentaje(dto.getPorcentaje());
        nueva.setValorFijo(dto.getValorFijo());
        nueva.setFormula(dto.getFormula());
        nueva.setVigenteDesde(dto.getVigenteDesde());
        nueva.setVigenteHasta(null);

        // La versión anterior deja de regir el día antes de la nueva.
        if (actual.getVigenteHasta() == null || !actual.getVigenteHasta().isBefore(dto.getVigenteDesde())) {
            actual.setVigenteHasta(dto.getVigenteDesde().minusDays(1));
            conceptoRepo.saveAndFlush(actual);
        }

        validar(nueva);
        formulas.prepararParaGuardar(nueva, empresaId);
        validarSinSolape(nueva);
        ConceptoNominaEntity guardada = conceptoRepo.save(nueva);
        return obtener(guardada.getId(), empresaId);
    }

    /**
     * Un concepto con los nombres de tipo y grupo.
     *
     * @throws GlobalException 404 si no existe o es de otra empresa
     */
    @Transactional(readOnly = true)
    public ConceptoDto obtener(Long id, Integer empresaId) {
        return queryRepo.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto no encontrado"));
    }

    /**
     * La versión de la empresa de un concepto global; si no existe, una copia
     * sin guardar con la misma clave, número, tipo y vigencia.
     */
    private ConceptoNominaEntity personalizacion(ConceptoNominaEntity global, Integer empresaId) {
        return queryRepo.personalizacion(empresaId, global.getCodigo(), global.getVigenteDesde())
                .flatMap(conceptoRepo::findById)
                .orElseGet(() -> copiarParaEmpresa(global, empresaId));
    }

    private ConceptoNominaEntity copiarParaEmpresa(ConceptoNominaEntity g, Integer empresaId) {
        ConceptoNominaEntity c = new ConceptoNominaEntity();
        c.setEmpresaId(empresaId);
        c.setCodigo(g.getCodigo());
        c.setNumero(g.getNumero());
        c.setNombre(g.getNombre());
        c.setClase(g.getClase());
        c.setTipoId(g.getTipoId());
        c.setEntrada(g.getEntrada());
        c.setRedondeoPrecision(g.getRedondeoPrecision());
        c.setRedondeoModo(g.getRedondeoModo());
        c.setFrecuencia(g.getFrecuencia());
        c.setGrupoId(null);
        c.setAplicaArt383(g.getAplicaArt383());
        c.setDeLey(g.getDeLey());
        c.setDescripcion(g.getDescripcion());
        c.setFormula(g.getFormula());
        c.setHaceBasePrestacional(g.getHaceBasePrestacional());
        c.setCentroCostoId(g.getCentroCostoId());
        c.setConstituyeIbc(g.getConstituyeIbc());
        c.setBase(g.getBase());
        c.setPorcentaje(g.getPorcentaje());
        c.setValorFijo(g.getValorFijo());
        c.setVigenteDesde(g.getVigenteDesde());
        c.setVigenteHasta(g.getVigenteHasta());
        c.setCodigoDian(g.getCodigoDian());
        c.setOrden(g.getOrden());
        c.setActivo(g.getActivo());
        return c;
    }

    /** Clave interna para un concepto nuevo: {@code CN<numero>}, sin chocar con una existente. */
    private String claveInterna(Integer empresaId, Integer numero) {
        String base = "CN" + numero;
        String clave = base;
        for (int i = 2; queryRepo.existeCodigo(empresaId, clave); i++) {
            clave = base + "_" + i;
        }
        return clave;
    }

    private static String valorO(String valor, String porDefecto) {
        return valor != null && !valor.isBlank() ? valor : porDefecto;
    }

    private void validar(ConceptoNominaEntity c) {
        if (c.getNombre() == null || c.getNombre().isBlank()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre del concepto es obligatorio");
        }
        if (!Clase.TODAS.contains(c.getClase())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Clase de concepto no válida: " + c.getClase());
        }
        if (c.getTipoId() != null) {
            String claseTipo = queryRepo.claseDeTipo(c.getTipoId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de concepto no válido"));
            if (!claseTipo.equals(c.getClase())) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El tipo escogido es de clase " + claseTipo + " y el concepto es " + c.getClase());
            }
        }
        if (!Entrada.TODAS.contains(c.getEntrada())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Entrada no válida: " + c.getEntrada());
        }
        if (!RedondeoPrecision.TODAS.contains(c.getRedondeoPrecision())
                || !RedondeoModo.TODOS.contains(c.getRedondeoModo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Redondeo no válido");
        }
        if (!Frecuencia.TODAS.contains(c.getFrecuencia())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Frecuencia no válida: " + c.getFrecuencia());
        }
        // El cálculo de un concepto de ley lo fija la ley (y el motor): no se revalida.
        if (Boolean.TRUE.equals(c.getDeLey())) return;
        // No imputable cuenta días, no plata: el valor nunca lo calcula el motor.
        if (Clase.NO_IMPUTABLE.equals(c.getClase()) && !Base.NOVEDAD.equals(c.getBase())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un concepto no imputable se registra como novedad (base NOVEDAD)");
        }
        if (c.getVigenteDesde() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "vigente_desde es obligatorio");
        }
        if (c.getVigenteHasta() != null && c.getVigenteHasta().isBefore(c.getVigenteDesde())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "vigente_hasta no puede ser anterior a vigente_desde");
        }
        // Coherencia base ↔ valor: un FIJO sin valor, o un porcentual sin
        // porcentaje, produce ceros silenciosos en la liquidación.
        if (Base.FIJO.equals(c.getBase()) && c.getValorFijo() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un concepto con base FIJO exige valor_fijo");
        }
        if (Base.FORMULA.equals(c.getBase()) && (c.getFormula() == null || c.getFormula().isBlank())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba la fórmula del concepto");
        }
        boolean esPorcentual = !Base.FIJO.equals(c.getBase())
                            && !Base.MANUAL.equals(c.getBase())
                            && !Base.NOVEDAD.equals(c.getBase())
                            && !Base.FORMULA.equals(c.getBase());
        if (esPorcentual && c.getPorcentaje() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un concepto con base " + c.getBase() + " exige porcentaje");
        }
    }

    /** El centro de costo fijo, si viene, debe ser de la empresa. */
    private void validarCentroCosto(ConceptoNominaEntity c, Integer empresaId) {
        if (c.getCentroCostoId() != null && !queryRepo.centroCostoDeEmpresa(empresaId, c.getCentroCostoId())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El centro de costo no es de la empresa");
        }
    }

    private void validarSinSolape(ConceptoNominaEntity nuevo) {
        List<ConceptoNominaEntity> mismos = queryRepo.versiones(nuevo.getEmpresaId(), nuevo.getCodigo()).stream()
                .filter(otroId -> !otroId.equals(nuevo.getId()))
                .map(conceptoRepo::findById)
                .flatMap(Optional::stream)
                .toList();

        for (ConceptoNominaEntity otro : mismos) {
            if (seSolapan(nuevo, otro)) {
                throw new GlobalException(HttpStatus.CONFLICT,
                        "El concepto " + nuevo.getNumero() + " ya tiene una versión vigente "
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
