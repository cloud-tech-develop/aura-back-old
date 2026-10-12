package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.nomina.VacacionesSaldoDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.FiltroVacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.RegistrarVacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.ResultadoColectivasDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.RetornoAnticipadoDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.VacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.VacacionesColectivasDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.NominaVacacionEntity;
import com.cloud_technological.aura_pos.repositories.nomina_vacaciones.NominaVacacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_vacaciones.NominaVacacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_vacaciones.NominaVacacionQueryRepository.Vinculacion;
import com.cloud_technological.aura_pos.services.NominaVacacionService;
import com.cloud_technological.aura_pos.services.nomina.DiasHabiles;
import com.cloud_technological.aura_pos.services.nomina.VacacionesService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Implementación de {@link NominaVacacionService}. */
@Service
public class NominaVacacionServiceImpl implements NominaVacacionService {

    private static final String TIEMPO = "TIEMPO";
    private static final String DINERO = "DINERO";
    private static final String REGISTRADA = "REGISTRADA";
    private static final String INTERRUMPIDA = "INTERRUMPIDA";
    private static final String ANULADA = "ANULADA";
    private static final BigDecimal DOS = new BigDecimal("2");
    private static final DateTimeFormatter LOTE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final NominaVacacionJPARepository jpaRepository;
    private final NominaVacacionQueryRepository queryRepository;
    private final ContratoLaboralService contratos;
    private final VacacionesService saldos;

    public NominaVacacionServiceImpl(NominaVacacionJPARepository jpaRepository,
            NominaVacacionQueryRepository queryRepository,
            ContratoLaboralService contratos,
            VacacionesService saldos) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.contratos = contratos;
        this.saldos = saldos;
    }

    @Override
    public PageImpl<VacacionDto> listar(PageableDto<FiltroVacacionDto> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public VacacionDto obtener(Long id, Integer empresaId) {
        return queryRepository.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Vacaciones no encontradas"));
    }

    @Override
    public VacacionesSaldoDto saldo(Long contratoId, Integer empresaId) {
        ContratoLaboralEntity contrato = contratos.obtener(contratoId, empresaId);
        return saldos.saldo(contrato.getEmpleado().getId(), empresaId);
    }

    @Override
    @Transactional
    public VacacionDto registrar(RegistrarVacacionDto dto, Integer empresaId, Long usuarioId) {
        ContratoLaboralEntity contrato = contratos.obtener(dto.getContratoId(), empresaId);
        NominaVacacionEntity v = TIEMPO.equals(dto.getTipo())
                ? enTiempo(contrato, dto.getFechaInicio(), dto.getFechaFin(), empresaId)
                : enDinero(contrato, dto.getFechaInicio(), dto.getDias(), empresaId);
        v.setObservacion(texto(dto.getObservacion()));
        v.setUsuarioId(usuarioId);
        Long id = jpaRepository.saveAndFlush(v).getId();
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public ResultadoColectivasDto colectivas(VacacionesColectivasDto dto, Integer empresaId, Long usuarioId) {
        List<Vinculacion> activas = queryRepository.vinculacionesActivas(empresaId);
        List<Vinculacion> elegidas = dto.getContratoIds() == null || dto.getContratoIds().isEmpty()
                ? activas
                : activas.stream().filter(a -> dto.getContratoIds().contains(a.contratoId())).toList();
        if (elegidas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay vinculaciones activas para las vacaciones.");

        ResultadoColectivasDto r = new ResultadoColectivasDto();
        r.setLote("COL-" + LocalDateTime.now().format(LOTE));
        for (Vinculacion a : elegidas) {
            try {
                ContratoLaboralEntity contrato = contratos.obtener(a.contratoId(), empresaId);
                NominaVacacionEntity v = enTiempo(contrato, dto.getFechaInicio(), dto.getFechaFin(), empresaId);
                v.setLote(r.getLote());
                v.setObservacion(texto(dto.getObservacion()));
                v.setUsuarioId(usuarioId);
                jpaRepository.save(v);
                r.setRegistradas(r.getRegistradas() + 1);
            } catch (GlobalException ex) {
                r.getOmitidos().add(a.empleado() + ": " + ex.getMessage());
            }
        }
        jpaRepository.flush();
        return r;
    }

    @Override
    @Transactional
    public VacacionDto retornoAnticipado(Long id, RetornoAnticipadoDto dto, Integer empresaId) {
        NominaVacacionEntity v = entidad(id, empresaId);
        if (!TIEMPO.equals(v.getTipo()) || !REGISTRADA.equals(v.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se registra el regreso de unas vacaciones en tiempo que no estén interrumpidas ni anuladas.");
        LocalDate fecha = dto.getFecha();
        if (!fecha.isAfter(v.getFechaInicio()) || fecha.isAfter(v.getFechaFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de regreso debe caer entre el "
                    + v.getFechaInicio().plusDays(1) + " y el " + v.getFechaFin() + ".");
        if (queryRepository.nominaCerradaEntre(v.getContratoId(), fecha, v.getFechaFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Esos días ya se pagaron en una nómina aprobada. Registre el ajuste en la siguiente nómina.");

        v.setFechaRegresoAnticipado(fecha);
        v.setDiasNoDisfrutados(BigDecimal.valueOf(DiasHabiles.entre(fecha, v.getFechaFin())));
        v.setEstado(INTERRUMPIDA);
        v.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(v);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public VacacionDto anular(Long id, Integer empresaId) {
        NominaVacacionEntity v = entidad(id, empresaId);
        if (ANULADA.equals(v.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "Las vacaciones ya están anuladas.");
        LocalDate hasta = v.getFechaFin() != null ? v.getFechaFin() : v.getFechaInicio();
        if (queryRepository.nominaCerradaEntre(v.getContratoId(), v.getFechaInicio(), hasta))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Las vacaciones ya están en una nómina aprobada o pagada: no se pueden anular.");
        v.setEstado(ANULADA);
        v.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(v);
        return obtener(id, empresaId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** Vacaciones en tiempo validadas (sin guardar). */
    private NominaVacacionEntity enTiempo(ContratoLaboralEntity contrato, LocalDate inicio, LocalDate fin,
            Integer empresaId) {
        validarVinculacion(contrato, inicio);
        if (fin == null || fin.isBefore(inicio))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser igual o posterior al inicio.");
        if (contrato.getFechaFin() != null && fin.isAfter(contrato.getFechaFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Las vacaciones terminan después del fin de la vinculación (" + contrato.getFechaFin() + ").");
        int habiles = DiasHabiles.entre(inicio, fin);
        if (habiles <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Entre esas fechas no hay días hábiles.");
        if (queryRepository.seCruza(contrato.getId(), inicio, fin))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya tiene vacaciones registradas en esas fechas.");
        if (queryRepository.nominaCerradaEntre(contrato.getId(), inicio, fin))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya hay una nómina aprobada o pagada en esas fechas.");
        BigDecimal dias = BigDecimal.valueOf(habiles);
        validarSaldo(contrato, dias, empresaId);

        NominaVacacionEntity v = nueva(contrato, empresaId, TIEMPO, inicio, dias);
        v.setFechaFin(fin);
        v.setFechaRegreso(DiasHabiles.siguienteHabil(fin));
        return v;
    }

    /** Vacaciones compensadas en dinero validadas (sin guardar). */
    private NominaVacacionEntity enDinero(ContratoLaboralEntity contrato, LocalDate fecha, BigDecimal dias,
            Integer empresaId) {
        validarVinculacion(contrato, fecha);
        if (dias == null || dias.signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique los días a compensar en dinero.");
        dias = dias.setScale(2, RoundingMode.HALF_UP);
        if (queryRepository.nominaCerradaEntre(contrato.getId(), fecha, fecha))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya hay una nómina aprobada o pagada en esa fecha.");
        VacacionesSaldoDto saldo = validarSaldo(contrato, dias, empresaId);
        // CST art. 189: se puede compensar en dinero hasta la mitad de lo causado.
        BigDecimal mitad = saldo.getSaldoInicial().add(saldo.getDiasCausados())
                .divide(DOS, 2, RoundingMode.HALF_UP);
        BigDecimal compensados = queryRepository.diasCompensados(contrato.getEmpleado().getId()).add(dias);
        if (compensados.compareTo(mitad) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "En dinero se puede compensar hasta la mitad de lo "
                    + "causado (" + mitad + " días); con estos serían " + compensados + ".");
        return nueva(contrato, empresaId, DINERO, fecha, dias);
    }

    private static void validarVinculacion(ContratoLaboralEntity contrato, LocalDate inicio) {
        if (!"ACTIVO".equals(contrato.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La vinculación está " + contrato.getEstado().toLowerCase() + ".");
        if ("PRESTACION_SERVICIOS".equals(contrato.getTipoContrato()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un contrato de prestación de servicios no tiene vacaciones.");
        if (inicio == null || inicio.isBefore(contrato.getFechaInicio()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha no puede ser anterior al ingreso (" + contrato.getFechaInicio() + ").");
    }

    /** No más días de los disponibles, salvo que la empresa permita vacaciones anticipadas. */
    private VacacionesSaldoDto validarSaldo(ContratoLaboralEntity contrato, BigDecimal dias, Integer empresaId) {
        VacacionesSaldoDto saldo = saldos.saldo(contrato.getEmpleado().getId(), empresaId);
        if (!Boolean.TRUE.equals(saldo.getPermiteAnticipadas()) && dias.compareTo(saldo.getDiasDisponibles()) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tiene " + saldo.getDiasDisponibles()
                    + " días disponibles y se están tomando " + dias.stripTrailingZeros().toPlainString()
                    + ". Habilite vacaciones anticipadas en la configuración de nómina si lo permite.");
        return saldo;
    }

    private static NominaVacacionEntity nueva(ContratoLaboralEntity contrato, Integer empresaId, String tipo,
            LocalDate inicio, BigDecimal dias) {
        NominaVacacionEntity v = new NominaVacacionEntity();
        v.setEmpresaId(empresaId);
        v.setContratoId(contrato.getId());
        v.setEmpleadoId(contrato.getEmpleado().getId());
        v.setTipo(tipo);
        v.setFechaInicio(inicio);
        v.setDiasHabiles(dias);
        v.setEstado(REGISTRADA);
        v.setCreatedAt(LocalDateTime.now());
        return v;
    }

    private NominaVacacionEntity entidad(Long id, Integer empresaId) {
        return jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Vacaciones no encontradas"));
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
