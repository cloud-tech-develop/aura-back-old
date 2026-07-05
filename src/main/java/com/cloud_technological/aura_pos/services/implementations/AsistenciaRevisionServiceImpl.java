package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteTableDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisarDetallesDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionAccionDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionFilterDto;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.cloud_technological.aura_pos.entity.AsistenciaFrenteAprobacionEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaFrenteDetalleEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaFrenteEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaNovedadNominaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaNovedadNominaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaAlertaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteAprobacionJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.AsistenciaFrenteService;
import com.cloud_technological.aura_pos.services.AsistenciaRevisionService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class AsistenciaRevisionServiceImpl implements AsistenciaRevisionService {

    @Autowired private AsistenciaFrenteJPARepository asistFrenteRepo;
    @Autowired private AsistenciaFrenteDetalleJPARepository detalleRepo;
    @Autowired private AsistenciaAlertaJPARepository alertaRepo;
    @Autowired private AsistenciaFrenteAprobacionJPARepository aprobacionRepo;
    @Autowired private AsistenciaFrenteQueryRepository queryRepo;
    @Autowired private AsistenciaFrenteService asistenciaFrenteService;
    @Autowired private PeriodoNominaJPARepository periodoRepo;
    @Autowired private EmpleadoJPARepository empleadoRepo;
    @Autowired private AsistenciaNovedadNominaJPARepository novedadRepo;

    @Override
    public PageImpl<AsistenciaFrenteTableDto> listar(RevisionFilterDto filtro, Integer empresaId) {
        return queryRepo.bandeja(filtro, empresaId);
    }

    @Override
    public java.util.List<com.cloud_technological.aura_pos.dto.asistencia_frente.PreliquidacionFrenteItemDto>
            preliquidacion(Long periodoId, Long proyectoId, Long frenteId, Integer empresaId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período de nómina no encontrado"));
        return queryRepo.preliquidacion(periodo.getFechaInicio(), periodo.getFechaFin(),
                proyectoId, frenteId, empresaId);
    }

    @Override
    public AsistenciaFrenteDto obtenerPorId(Long asistenciaId, Integer empresaId) {
        AsistenciaFrenteEntity af = getAsistencia(asistenciaId, empresaId);
        return asistenciaFrenteService.obtener(af.getFrenteId(), af.getFecha(), empresaId);
    }

    @Override
    @Transactional
    public void aprobar(Long asistenciaId, RevisionAccionDto dto, Integer empresaId, Long usuarioId) {
        AsistenciaFrenteEntity af = getAsistencia(asistenciaId, empresaId);
        validarEnRevision(af);
        if (alertaRepo.existsByAsistenciaFrenteIdAndNivelAndEstadoAndDeletedAtIsNull(asistenciaId, "CRITICA", "ABIERTA")) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede aprobar: existen alertas críticas abiertas. Solicita corrección primero");
        }

        String anterior = af.getEstado();
        af.setEstado("APROBADO");
        af.setAprobadoPor(usuarioId);
        af.setAprobadoAt(LocalDateTime.now());
        af.setObservacionAdmin(dto != null ? dto.getObservacion() : null);
        af.setUpdatedBy(usuarioId);
        asistFrenteRepo.save(af);

        // Respeta las decisiones por trabajador: lo que quedó PENDIENTE se aprueba;
        // lo que el admin marcó RECHAZADO/AJUSTADO se conserva.
        List<AsistenciaFrenteDetalleEntity> detalles = detalleRepo.findByAsistenciaFrenteIdAndDeletedAtIsNull(asistenciaId);
        for (AsistenciaFrenteDetalleEntity d : detalles) {
            if (d.getEstadoRevision() == null || "PENDIENTE".equals(d.getEstadoRevision())) {
                d.setEstadoRevision("APROBADO");
            }
            d.setAprobadoPor(usuarioId);
            d.setAprobadoAt(LocalDateTime.now());
            d.setUpdatedBy(usuarioId);
        }
        detalleRepo.saveAll(detalles);

        registrar(af, usuarioId, "APROBAR", anterior, "APROBADO", dto);
    }

    @Override
    @Transactional
    public void rechazar(Long asistenciaId, RevisionAccionDto dto, Integer empresaId, Long usuarioId) {
        AsistenciaFrenteEntity af = getAsistencia(asistenciaId, empresaId);
        validarEnRevision(af);

        String anterior = af.getEstado();
        af.setEstado("RECHAZADO");
        af.setRechazadoPor(usuarioId);
        af.setRechazadoAt(LocalDateTime.now());
        af.setObservacionAdmin(dto != null ? dto.getObservacion() : null);
        af.setUpdatedBy(usuarioId);
        asistFrenteRepo.save(af);

        registrar(af, usuarioId, "RECHAZAR", anterior, "RECHAZADO", dto);
    }

    @Override
    @Transactional
    public void solicitarCorreccion(Long asistenciaId, RevisionAccionDto dto, Integer empresaId, Long usuarioId) {
        AsistenciaFrenteEntity af = getAsistencia(asistenciaId, empresaId);
        validarEnRevision(af);

        String anterior = af.getEstado();
        af.setEstado("EN_CORRECCION");
        af.setObservacionAdmin(dto != null ? dto.getObservacion() : null);
        af.setUpdatedBy(usuarioId);
        asistFrenteRepo.save(af);

        registrar(af, usuarioId, "SOLICITAR_CORRECCION", anterior, "EN_CORRECCION", dto);
    }

    @Override
    @Transactional
    public void revisarDetalles(Long asistenciaId, RevisarDetallesDto dto, Integer empresaId, Long usuarioId) {
        AsistenciaFrenteEntity af = getAsistencia(asistenciaId, empresaId);
        validarEnRevision(af);
        if (dto == null || dto.getDetalles() == null) return;

        List<AsistenciaFrenteDetalleEntity> detalles = detalleRepo.findByAsistenciaFrenteIdAndDeletedAtIsNull(asistenciaId);
        java.util.Map<Long, AsistenciaFrenteDetalleEntity> porId = new java.util.HashMap<>();
        for (AsistenciaFrenteDetalleEntity d : detalles) porId.put(d.getId(), d);

        for (RevisarDetallesDto.Item item : dto.getDetalles()) {
            AsistenciaFrenteDetalleEntity d = porId.get(item.getDetalleId());
            if (d == null) continue;
            if (item.getEstadoRevision() != null) d.setEstadoRevision(item.getEstadoRevision());
            if (item.getObservacionAdmin() != null) d.setObservacionAdmin(item.getObservacionAdmin());
            d.setUpdatedBy(usuarioId);
        }
        detalleRepo.saveAll(detalles);
    }

    @Override
    @Transactional
    public int enviarNomina(Long asistenciaId, Integer empresaId, Long usuarioId) {
        AsistenciaFrenteEntity af = getAsistencia(asistenciaId, empresaId);
        if (!"APROBADO".equals(af.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La asistencia debe estar APROBADA para enviarla a nómina");
        }

        // Período de nómina que cubre la fecha de la asistencia.
        PeriodoNominaEntity periodo = periodoRepo.findByEmpresaIdOrderByIdDesc(empresaId).stream()
                .filter(p -> !"ANULADO".equals(p.getEstado()))
                .filter(p -> !af.getFecha().isBefore(p.getFechaInicio()) && !af.getFecha().isAfter(p.getFechaFin()))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "No hay un período de nómina que cubra la fecha " + af.getFecha()
                                + ". Crea el período antes de enviar a nómina."));

        List<AsistenciaFrenteDetalleEntity> detalles = detalleRepo.findByAsistenciaFrenteIdAndDeletedAtIsNull(asistenciaId);
        int generadas = 0;

        for (AsistenciaFrenteDetalleEntity d : detalles) {
            if (!"APROBADO".equals(d.getEstadoRevision())) continue;
            EmpleadoEntity empleado = empleadoRepo.findById(d.getEmpleadoId()).orElse(null);
            if (empleado == null) continue;

            for (String[] nv : mapearNovedades(d)) {
                AsistenciaNovedadNominaEntity n = new AsistenciaNovedadNominaEntity();
                n.setEmpresa(periodo.getEmpresa());
                n.setPeriodoNomina(periodo);
                n.setEmpleado(empleado);
                n.setTipoNovedad(nv[0]);
                n.setUnidad(nv[1]);
                n.setCantidad(new BigDecimal(nv[2]));
                n.setOrigen("PROYECTO_FRENTE");
                n.setEstado("APROBADA");
                n.setFechaGeneracion(LocalDateTime.now());
                n.setGeneradoPor(usuarioId != null ? usuarioId.intValue() : null);
                n.setProyectoId(af.getProyectoId());
                n.setFrenteId(af.getFrenteId());
                n.setAsistenciaFrenteId(af.getId());
                n.setAsistenciaFrenteDetalleId(d.getId());
                n.setSoportePdfId(af.getSoportePdfId());
                novedadRepo.save(n);
                generadas++;
            }

            d.setEstadoRevision("ENVIADO_NOMINA");
            d.setUpdatedBy(usuarioId);
        }
        detalleRepo.saveAll(detalles);

        af.setEstado("ENVIADO_NOMINA");
        af.setUpdatedBy(usuarioId);
        asistFrenteRepo.save(af);

        registrar(af, usuarioId, "APROBAR", "APROBADO", "ENVIADO_NOMINA", null);
        return generadas;
    }

    /**
     * Traduce un detalle aprobado en novedades de nómina {tipo, unidad, cantidad}.
     * Solo se generan novedades cuando hay conceptos que afectan la nómina
     * (ausencias y horas extra); la jornada ordinaria no genera novedad.
     */
    private List<String[]> mapearNovedades(AsistenciaFrenteDetalleEntity d) {
        List<String[]> res = new java.util.ArrayList<>();
        String estado = d.getEstadoAsistencia();

        if ("NO_ASISTIO".equals(estado)) {
            res.add(new String[] { "AUSENCIA_NO_JUSTIFICADA", "DIAS", "1" });
        }
        // Horas ordinarias nocturnas: la jornada base ya se paga por día, pero el
        // trabajo nocturno genera recargo (35%) sobre esas horas.
        if (nz(d.getHorasOrdinariasNocturnas()).signum() > 0) {
            res.add(new String[] { "RECARGO_NOCTURNO", "HORAS", nz(d.getHorasOrdinariasNocturnas()).toPlainString() });
        }
        if (nz(d.getHorasExtraDiurnas()).signum() > 0) {
            res.add(new String[] { "HORA_EXTRA_DIURNA", "HORAS", nz(d.getHorasExtraDiurnas()).toPlainString() });
        }
        if (nz(d.getHorasExtraNocturnas()).signum() > 0) {
            res.add(new String[] { "HORA_EXTRA_NOCTURNA", "HORAS", nz(d.getHorasExtraNocturnas()).toPlainString() });
        }
        // Horas ordinarias en domingo/festivo: recargo dom/fest sobre la jornada base (pagada por día).
        // El clasificador consolida en horasDominicalesFestivas; se mantienen los campos legacy como fallback.
        BigDecimal domFest = nz(d.getHorasDominicalesFestivas());
        if (domFest.signum() == 0) {
            domFest = nz(d.getHorasDominicales()).add(nz(d.getHorasFestivas()));
        }
        if (domFest.signum() > 0) {
            res.add(new String[] { "RECARGO_DOMINICAL_FESTIVO", "HORAS", domFest.toPlainString() });
        }
        // Horas EXTRA en domingo/festivo: se pagan completas con recargo extra + dom/fest.
        BigDecimal extraDomFest = nz(d.getHorasExtraDiurnasDomFest()).add(nz(d.getHorasExtraNocturnasDomFest()));
        if (extraDomFest.signum() > 0) {
            res.add(new String[] { "HORA_EXTRA_DOMINICAL_FESTIVA", "HORAS", extraDomFest.toPlainString() });
        }
        return res;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private AsistenciaFrenteEntity getAsistencia(Long id, Integer empresaId) {
        return asistFrenteRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Asistencia no encontrada"));
    }

    private void validarEnRevision(AsistenciaFrenteEntity af) {
        if (!"ENVIADO_REVISION".equals(af.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se puede revisar una asistencia enviada a revisión");
        }
    }

    private void registrar(AsistenciaFrenteEntity af, Long usuarioId, String accion,
            String anterior, String aprobado, RevisionAccionDto dto) {
        AsistenciaFrenteAprobacionEntity ap = new AsistenciaFrenteAprobacionEntity();
        ap.setEmpresaId(af.getEmpresaId());
        ap.setAsistenciaFrenteId(af.getId());
        ap.setAdministradorId(usuarioId);
        ap.setAccion(accion);
        ap.setValorAnterior(anterior);
        ap.setValorAprobado(aprobado);
        ap.setObservacion(dto != null ? dto.getObservacion() : null);
        ap.setCreatedBy(usuarioId);
        aprobacionRepo.save(ap);
    }
}
