package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.repositories.municipios.MunicipioQueryRepository;
import com.cloud_technological.aura_pos.dto.municipios.MunicipioDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.CreateVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.FiltroVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.TipoVinculacionTableDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionTableDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaCargoEntity;
import com.cloud_technological.aura_pos.entity.NominaCatalogoEntity;
import com.cloud_technological.aura_pos.entity.NominaTipoVinculacionEntity;
import com.cloud_technological.aura_pos.mappers.VinculacionMapper;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_catalogos.NominaCatalogoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_catalogos.NominaCatalogoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_tipos_vinculacion.NominaTipoVinculacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionQueryRepository;
import com.cloud_technological.aura_pos.services.NominaTipoVinculacionService;
import com.cloud_technological.aura_pos.services.VinculacionService;
import com.cloud_technological.aura_pos.services.nomina.catalogos.TipoCatalogoNomina;
import com.cloud_technological.aura_pos.services.nomina.vinculacion.ReglasVinculacion;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.JsonMapas;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Implementación de {@link VinculacionService}. Las reglas de fechas, contrato
 * principal e histórico salarial las sigue aplicando {@link ContratoLaboralService};
 * aquí se agregan las de catálogo, cargo, prueba y jornada, y se sincronizan las
 * columnas viejas que lee el motor.
 */
@Service
public class VinculacionServiceImpl implements VinculacionService {

    private static final String TERMINADO = "TERMINADO";
    private static final List<String> CLASES_ARL = List.of("I", "II", "III", "IV", "V");

    /** Tipo de vinculación sembrado que corresponde a cada tipo de contrato (igual que V210). */
    private static final Map<String, String> TIPO_POR_CONTRATO = Map.of(
            "INDEFINIDO", "PLANTA",
            "FIJO", "FIJO",
            "OBRA_LABOR", "OBRA",
            "APRENDIZAJE", "APRENDIZ");

    private final ContratoLaboralService contratos;
    private final VinculacionQueryRepository queryRepository;
    private final EmpleadoJPARepository empleadoRepository;
    private final NominaTipoVinculacionJPARepository tipoRepository;
    private final NominaCargoJPARepository cargoRepository;
    private final NominaCatalogoJPARepository catalogoRepository;
    private final NominaCatalogoQueryRepository catalogoQuery;
    private final VinculacionMapper mapper;
    private final R2StorageService storage;
    private final MunicipioQueryRepository municipios;
    private final NominaTipoVinculacionService tipos;
    private final NominaCargoQueryRepository cargoQuery;

    public VinculacionServiceImpl(ContratoLaboralService contratos,
            VinculacionQueryRepository queryRepository,
            EmpleadoJPARepository empleadoRepository,
            NominaTipoVinculacionJPARepository tipoRepository,
            NominaCargoJPARepository cargoRepository,
            NominaCatalogoJPARepository catalogoRepository,
            NominaCatalogoQueryRepository catalogoQuery,
            VinculacionMapper mapper,
            R2StorageService storage,
            MunicipioQueryRepository municipios,
            NominaTipoVinculacionService tipos,
            NominaCargoQueryRepository cargoQuery) {
        this.contratos = contratos;
        this.queryRepository = queryRepository;
        this.empleadoRepository = empleadoRepository;
        this.tipoRepository = tipoRepository;
        this.cargoRepository = cargoRepository;
        this.catalogoRepository = catalogoRepository;
        this.catalogoQuery = catalogoQuery;
        this.mapper = mapper;
        this.storage = storage;
        this.municipios = municipios;
        this.tipos = tipos;
        this.cargoQuery = cargoQuery;
    }

    @Override
    public PageImpl<VinculacionTableDto> listar(PageableDto<FiltroVinculacionDto> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public VinculacionDto obtener(Long id, Integer empresaId) {
        return queryRepository.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Vinculación no encontrada"));
    }

    @Override
    @Transactional
    public VinculacionDto crear(CreateVinculacionDto dto, Integer empresaId) {
        Long empleadoId = empleadoDelTercero(dto.getTerceroId(), empresaId, "El tercero elegido");
        EmpleadoEntity empleado = empleadoRepository.findByIdAndEmpresaId(empleadoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        if (dto.getSalarioBase() == null || dto.getSalarioBase().signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El salario debe ser mayor a cero.");

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        ContratoLaboralEntity c = new ContratoLaboralEntity();
        c.setEmpresa(empresa);
        c.setEmpleado(empleado);
        c.setEstado("ACTIVO");
        c.setSalarioBase(dto.getSalarioBase());
        mapper.actualizar(dto, c);
        asignarJerarquia(c, dto, empresaId);

        NominaCargoEntity cargo = aplicarReglas(c, empresaId);
        validarSalarioEnRango(dto.getSalarioBase(), cargo);
        c.setCodigo(queryRepository.siguienteCodigo(empresaId));

        // crear() valida fechas y contrato principal, y abre el histórico salarial.
        return obtener(contratos.crear(c).getId(), empresaId);
    }

    @Override
    @Transactional
    public VinculacionDto actualizar(Long id, CreateVinculacionDto dto, Integer empresaId) {
        ContratoLaboralEntity c = contratos.obtener(id, empresaId);
        if (TERMINADO.equals(c.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "La vinculación está retirada y no se edita.");

        mapper.actualizar(dto, c);
        asignarJerarquia(c, dto, empresaId);
        aplicarReglas(c, empresaId);
        contratos.guardarCambios(c);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public VinculacionDto subirSoporte(Long id, String tipo, MultipartFile archivo, Integer empresaId) {
        ContratoLaboralEntity c = contratos.obtener(id, empresaId);
        if (archivo == null || archivo.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Adjunte el archivo del soporte.");
        String url = storage.subirSoporte(archivo, "vinculaciones/" + empresaId);
        switch (tipo) {
            case "interno" -> c.setDocInternoUrl(url);
            case "externo" -> c.setDocExternoUrl(url);
            default -> throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de soporte no válido: " + tipo);
        }
        contratos.guardarCambios(c);
        return obtener(id, empresaId);
    }

    @Override
    public void completarContratoHeredado(ContratoLaboralEntity contrato, Integer empresaId) {
        contrato.setCodigo(queryRepository.siguienteCodigo(empresaId));
        contrato.setTipoVinculacionId(tipoParaContrato(contrato.getTipoContrato(), empresaId));
        if (contrato.getCargo() != null && !contrato.getCargo().isBlank()) {
            NominaCargoEntity cargo = cargoPorNombre(contrato.getCargo(), empresaId);
            contrato.setCargoId(cargo.getId());
            contrato.setCargo(cargo.getNombre());
        }
    }

    // ── Contrato heredado ────────────────────────────────────────────────────

    /**
     * Tipo de vinculación para un tipo de contrato: primero el sembrado (PLANTA, FIJO…);
     * si la empresa lo borró, el primer tipo activo con ese contrato. Prestación de
     * servicios no tiene tipo sembrado y queda sin tipo (como en V210).
     *
     * @return id del tipo, o null si ninguno corresponde
     */
    private Long tipoParaContrato(String tipoContrato, Integer empresaId) {
        // activos() siembra los tipos iniciales si la empresa aún no tiene.
        List<TipoVinculacionTableDto> activos = tipos.activos(empresaId);
        String sembrado = TIPO_POR_CONTRATO.get(tipoContrato);
        return activos.stream().filter(t -> t.getCodigo().equals(sembrado)).findFirst()
                .or(() -> activos.stream().filter(t -> Objects.equals(t.getTipoContrato(), tipoContrato)).findFirst())
                .map(TipoVinculacionTableDto::getId)
                .orElse(null);
    }

    /** Cargo del catálogo con ese nombre; si no existe se crea con código automático (CAR-001…). */
    private NominaCargoEntity cargoPorNombre(String nombre, Integer empresaId) {
        Optional<Long> existente = cargoQuery.idPorNombre(empresaId, nombre);
        if (existente.isPresent())
            return cargoRepository.findById(existente.get()).orElseThrow();

        NominaCargoEntity cargo = new NominaCargoEntity();
        cargo.setEmpresaId(empresaId);
        cargo.setCodigo(cargoQuery.siguienteCodigoAutomatico(empresaId));
        cargo.setNombre(nombre.trim());
        cargo.setActivo(Boolean.TRUE);
        cargo.setCreatedAt(LocalDateTime.now());
        return cargoRepository.save(cargo);
    }

    // ── Reglas ───────────────────────────────────────────────────────────────

    /**
     * Valida la vinculación contra sus catálogos y la ley, y sincroniza las columnas
     * que lee el motor (cargo en texto, integral, ARL).
     *
     * @return el cargo resuelto
     */
    private NominaCargoEntity aplicarReglas(ContratoLaboralEntity c, Integer empresaId) {
        NominaTipoVinculacionEntity tipo = tipoRepository.findByIdAndEmpresaId(c.getTipoVinculacionId(), empresaId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El tipo de vinculación no es válido."));
        NominaCargoEntity cargo = cargoRepository.findByIdAndEmpresaId(c.getCargoId(), empresaId)
                .filter(k -> k.getDeletedAt() == null)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El cargo no es válido."));

        if (Boolean.TRUE.equals(tipo.getExigeFechaFin()) && c.getFechaFin() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Una vinculación " + tipo.getNombre() + " exige fecha de terminación.");
        if (Boolean.TRUE.equals(tipo.getUnicaPorEmpleado())
                && queryRepository.tieneOtraActivaDelTipo(c.getEmpleado().getId(), tipo.getId(), c.getId()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El empleado ya tiene una vinculación activa de tipo " + tipo.getNombre() + ".");
        validarPlazas(cargo, c.getId());

        exigirCatalogo(c.getGrupoNominaId(), empresaId, TipoCatalogoNomina.GRUPO_NOMINA, "grupo de nómina");
        exigirCatalogo(c.getCalendarioId(), empresaId, TipoCatalogoNomina.CALENDARIO_LABORAL, "calendario laboral");
        validarUbicacion(c, empresaId);

        exigir(ReglasVinculacion.periodoPrueba(c.getFechaInicio(), c.getFechaFin(), c.getFechaFinPrueba()));
        exigir(ReglasVinculacion.faseAprendiz(c.getTipoContrato(), c.getFase()));
        LocalDate referencia = c.getFechaInicio().isAfter(LocalDate.now()) ? c.getFechaInicio() : LocalDate.now();
        exigir(ReglasVinculacion.jornada(c.getHorasSemana(),
                queryRepository.jornadaLegal(empresaId, referencia).orElse(null)));

        if (!"APRENDIZAJE".equals(c.getTipoContrato())) c.setFase(null);
        c.setCargo(cargo.getNombre());
        c.setMunicipioTrabajo(nombreMunicipio(c.getMunicipioTrabajoId()));
        c.setEsSalarioIntegral("INTEGRAL".equals(c.getTipoRemuneracion()));
        sincronizarArl(c, empresaId);
        return cargo;
    }

    /** Las plazas del cargo (si tiene límite) no pueden estar todas ocupadas. */
    private void validarPlazas(NominaCargoEntity cargo, Long excluirId) {
        if (cargo.getPlazas() == null) return;
        int ocupadas = queryRepository.ocupadasEnCargo(cargo.getId(), excluirId);
        if (ocupadas >= cargo.getPlazas())
            throw new GlobalException(HttpStatus.CONFLICT, "El cargo " + cargo.getNombre() + " tiene sus "
                    + cargo.getPlazas() + " plazas ocupadas. Amplíe las plazas o use otro cargo.");
    }

    /** Al vincular, el salario debe caer en el rango del cargo (si lo tiene). */
    private static void validarSalarioEnRango(BigDecimal salario, NominaCargoEntity cargo) {
        if (cargo.getSalarioMinimo() != null && salario.compareTo(cargo.getSalarioMinimo()) < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El salario es menor que el mínimo del cargo " + cargo.getNombre() + ".");
        if (cargo.getSalarioMaximo() != null && salario.compareTo(cargo.getSalarioMaximo()) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El salario es mayor que el máximo del cargo " + cargo.getNombre() + ".");
    }

    /** Jefe directo y evaluador llegan como terceros: se guarda su ficha de empleado. */
    private void asignarJerarquia(ContratoLaboralEntity c, CreateVinculacionDto dto, Integer empresaId) {
        c.setJefeDirectoId(dto.getJefeDirectoTerceroId() == null ? null
                : empleadoDelTercero(dto.getJefeDirectoTerceroId(), empresaId, "El jefe directo"));
        c.setEvaluadorId(dto.getEvaluadorTerceroId() == null ? null
                : empleadoDelTercero(dto.getEvaluadorTerceroId(), empresaId, "El evaluador"));
    }

    /**
     * Ficha de empleado del tercero; 400 si el tercero no está registrado como empleado.
     *
     * @param quien sujeto del mensaje de error ("El jefe directo"…)
     */
    private Long empleadoDelTercero(Long terceroId, Integer empresaId, String quien) {
        return queryRepository.empleadoPorTercero(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        quien + " no tiene ficha de empleado. Créela primero en Empleados."));
    }

    /** La sede debe ser de la empresa; jefe y evaluador no pueden ser el mismo empleado. */
    private void validarUbicacion(ContratoLaboralEntity c, Integer empresaId) {
        if (c.getSucursalId() != null && !queryRepository.sucursalDeLaEmpresa(c.getSucursalId(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La sede no es válida.");
        for (Long jefe : new Long[] { c.getJefeDirectoId(), c.getEvaluadorId() })
            if (jefe != null && Objects.equals(jefe, c.getEmpleado().getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El jefe directo o evaluador no puede ser el mismo empleado.");
    }

    /**
     * El riesgo ARL del catálogo fija la clase (1–5) y la tarifa que ya usan el motor y
     * PILA. Sin riesgo, se conservan los valores que tenga el contrato.
     */
    private void sincronizarArl(ContratoLaboralEntity c, Integer empresaId) {
        if (c.getRiesgoArlId() == null) return;
        NominaCatalogoEntity riesgo = catalogoRepository.findByIdAndEmpresaId(c.getRiesgoArlId(), empresaId)
                .filter(r -> r.getDeletedAt() == null && TipoCatalogoNomina.RIESGO_ARL.name().equals(r.getTipo()))
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El riesgo ARL no es válido."));
        int clase = CLASES_ARL.indexOf(riesgo.getCodigo());
        if (clase >= 0) c.setNivelRiesgoArl(clase + 1);
        Map<String, Object> atributos = JsonMapas.deJson(riesgo.getAtributos());
        Object porcentaje = atributos.get("porcentaje");
        if (porcentaje != null) c.setTarifaArl(new BigDecimal(porcentaje.toString()));
    }

    /** "Ibagué - Tolima" para el municipio elegido; 400 si no existe. */
    private String nombreMunicipio(Long municipioId) {
        if (municipioId == null) return null;
        MunicipioDto m = municipios.findById(municipioId);
        if (m == null) throw new GlobalException(HttpStatus.BAD_REQUEST, "El municipio de trabajo no es válido.");
        return m.getLabel();
    }

    private void exigirCatalogo(Long id, Integer empresaId, TipoCatalogoNomina tipo, String nombre) {
        if (id != null && !catalogoQuery.esDelCatalogo(id, empresaId, tipo.name()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El " + nombre + " no es válido.");
    }

    private static void exigir(Optional<String> incumplimiento) {
        incumplimiento.ifPresent(m -> {
            throw new GlobalException(HttpStatus.BAD_REQUEST, m);
        });
    }
}
