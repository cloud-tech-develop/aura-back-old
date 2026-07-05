package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaAlertaDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaDetalleDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.GuardarBorradorDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.GuardarDetalleDto;
import com.cloud_technological.aura_pos.dto.laboral.JornadaConfigDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTrabajadorDto;
import com.cloud_technological.aura_pos.entity.AsistenciaAlertaEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaFrenteDetalleEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaFrenteEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaSoportePdfEntity;
import com.cloud_technological.aura_pos.entity.ProyectoFrenteEntity;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaAlertaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteQueryRepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaSoportePdfJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteQueryRepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteTrabajadorJPARepository;
import com.cloud_technological.aura_pos.services.AsistenciaFrenteService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class AsistenciaFrenteServiceImpl implements AsistenciaFrenteService {

    private static final BigDecimal HORAS_MAX_DIA = new BigDecimal("16");

    @Autowired private ProyectoFrenteJPARepository frenteRepo;
    @Autowired private ProyectoFrenteQueryRepository frenteQueryRepo;
    @Autowired private ProyectoFrenteTrabajadorJPARepository trabajadorRepo;
    @Autowired private AsistenciaFrenteJPARepository asistFrenteRepo;
    @Autowired private AsistenciaFrenteDetalleJPARepository detalleRepo;
    @Autowired private AsistenciaAlertaJPARepository alertaRepo;
    @Autowired private AsistenciaSoportePdfJPARepository soporteRepo;
    @Autowired private AsistenciaFrenteQueryRepository queryRepo;
    @Autowired private com.cloud_technological.aura_pos.services.implementations.R2StorageService r2Storage;
    @Autowired private ClasificadorHorasService clasificador;
    @Autowired private LaboralConfigService jornadaService;

    @Override
    public AsistenciaFrenteDto obtener(Long frenteId, LocalDate fecha, Integer empresaId) {
        ProyectoFrenteEntity frente = getFrente(frenteId, empresaId);
        Optional<AsistenciaFrenteEntity> opt = asistFrenteRepo.findByFrenteIdAndFechaAndDeletedAtIsNull(frenteId, fecha);

        AsistenciaFrenteDto dto = new AsistenciaFrenteDto();
        dto.setProyectoId(frente.getProyectoId());
        dto.setFrenteId(frenteId);
        dto.setFrenteNombre(frente.getNombre());
        dto.setFecha(fecha);
        dto.setLiderId(frente.getLiderId());

        if (opt.isPresent()) {
            AsistenciaFrenteEntity af = opt.get();
            dto.setId(af.getId());
            dto.setEstado(af.getEstado());
            dto.setObservacionLider(af.getObservacionLider());
            dto.setSoportePdfId(af.getSoportePdfId());
            if (af.getSoportePdfId() != null) {
                com.cloud_technological.aura_pos.dto.asistencia_frente.SoporteInfoDto info =
                        queryRepo.soporteInfo(af.getSoportePdfId());
                if (info != null) {
                    dto.setSoportePdfUrl(info.getArchivoUrl());
                    dto.setSoportePdfNombre(info.getNombreArchivo());
                    dto.setSoportePdfSubidoPor(info.getSubidoPor());
                    dto.setSoportePdfSubidoAt(info.getSubidoAt());
                }
            }
            // Mostrar SIEMPRE los trabajadores asignados, fusionando lo ya digitado.
            dto.setDetalles(fusionar(queryRepo.listarDetalles(af.getId()), frenteId, empresaId));
            dto.setAlertas(queryRepo.listarAlertas(af.getId()));
        } else {
            dto.setEstado("BORRADOR");
            dto.setDetalles(detallesEnBlanco(frenteId, empresaId));
            dto.setAlertas(new ArrayList<>());
        }
        return dto;
    }

    @Override
    @Transactional
    public AsistenciaFrenteDto guardarBorrador(Long frenteId, GuardarBorradorDto dto,
            Integer empresaId, Long usuarioId) {
        ProyectoFrenteEntity frente = getFrente(frenteId, empresaId);
        LocalDate fecha = dto.getFecha();

        AsistenciaFrenteEntity af = asistFrenteRepo
                .findByFrenteIdAndFechaAndDeletedAtIsNull(frenteId, fecha)
                .orElseGet(AsistenciaFrenteEntity::new);

        if (af.getId() != null && esNoEditable(af.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La asistencia ya fue aprobada o enviada a nómina y no se puede modificar");
        }

        af.setEmpresaId(empresaId);
        af.setProyectoId(frente.getProyectoId());
        af.setFrenteId(frenteId);
        af.setLiderId(frente.getLiderId());
        af.setFecha(fecha);
        af.setEstado("BORRADOR");
        af.setObservacionLider(dto.getObservacionLider());
        if (af.getId() == null) af.setCreatedBy(usuarioId);
        else af.setUpdatedBy(usuarioId);
        AsistenciaFrenteEntity saved = asistFrenteRepo.save(af);

        // Reemplazar detalles y alertas anteriores
        detalleRepo.deleteByAsistenciaFrenteId(saved.getId());
        alertaRepo.deleteByAsistenciaFrenteId(saved.getId());

        List<AsistenciaAlertaEntity> alertas = new ArrayList<>();
        List<GuardarDetalleDto> detalles = dto.getDetalles() != null ? dto.getDetalles() : new ArrayList<>();

        for (GuardarDetalleDto d : detalles) {
            String estadoAsis = d.getEstadoAsistencia() != null ? d.getEstadoAsistencia() : "SIN_REGISTRO";
            LocalTime entrada = parseHora(d.getHoraEntrada());
            LocalTime salida = parseHora(d.getHoraSalida());

            // ── Motor de clasificación de horas (G2) ──
            ClasificadorHorasService.Resultado r = clasificador.clasificar(
                    empresaId, frenteId, fecha, entrada, salida, estadoAsis);

            AsistenciaFrenteDetalleEntity det = new AsistenciaFrenteDetalleEntity();
            det.setEmpresaId(empresaId);
            det.setAsistenciaFrenteId(saved.getId());
            det.setProyectoId(frente.getProyectoId());
            det.setFrenteId(frenteId);
            det.setEmpleadoId(d.getEmpleadoId());
            det.setFecha(fecha);
            det.setHoraEntrada(entrada);
            det.setHoraSalida(salida);
            det.setHorasTrabajadas(r.total);
            det.setHorasOrdinarias(r.ordDiurna.add(r.ordNocturna).add(r.domFest));
            det.setHorasOrdinariasDiurnas(r.ordDiurna);
            det.setHorasOrdinariasNocturnas(r.ordNocturna);
            det.setHorasExtraDiurnas(r.extraDiurna);
            det.setHorasExtraNocturnas(r.extraNocturna);
            det.setHorasDominicalesFestivas(r.domFest);
            det.setHorasExtraDiurnasDomFest(r.extraDiurnaDomFest);
            det.setHorasExtraNocturnasDomFest(r.extraNocturnaDomFest);
            det.setEstadoAsistencia(estadoAsis);
            det.setEstadoRevision("PENDIENTE");
            det.setObservacionLider(d.getObservacionLider());
            boolean tieneCritica = r.alertas.stream().anyMatch(a -> "CRITICA".equals(a.nivel()));
            det.setRequiereRevision(tieneCritica);
            det.setCreatedBy(usuarioId);
            AsistenciaFrenteDetalleEntity savedDet = detalleRepo.save(det);

            // Alertas del clasificador (config/turno, franjas, límite diario)
            for (ClasificadorHorasService.Alerta a : r.alertas) {
                AsistenciaAlertaEntity ae = alerta(saved, frente, d.getEmpleadoId(), a.tipo(), a.nivel(), a.descripcion());
                ae.setAsistenciaFrenteDetalleId(savedDet.getId());
                alertas.add(ae);
            }

            // Alertas antifraude
            boolean asignado = trabajadorRepo.existsByFrenteIdAndEmpleadoIdAndEstadoAndDeletedAtIsNull(
                    frenteId, d.getEmpleadoId(), "ACTIVO");
            if (!asignado) {
                alertas.add(alerta(saved, frente, d.getEmpleadoId(), "TRABAJADOR_NO_ASIGNADO", "ADVERTENCIA",
                        "El trabajador no está asignado activo a este frente"));
            }
            if (detalleRepo.existsByEmpleadoIdAndFechaAndFrenteIdNotAndDeletedAtIsNull(
                    d.getEmpleadoId(), fecha, frenteId)) {
                alertas.add(alerta(saved, frente, d.getEmpleadoId(), "TRABAJADOR_DUPLICADO_MISMO_DIA", "ADVERTENCIA",
                        "El trabajador ya fue reportado el mismo día en otro frente"));
            }
        }

        // ── Cierre semanal: límite de horas extra por semana (Lun–Dom que contiene la fecha) ──
        java.time.LocalDate lunes = fecha.with(java.time.DayOfWeek.MONDAY);
        java.time.LocalDate domingo = lunes.plusDays(6);
        JornadaConfigDto cfgSem = jornadaService.vigente(empresaId, fecha);
        BigDecimal maxSemana = cfgSem != null && cfgSem.getMaxHorasExtraSemana() != null
                ? cfgSem.getMaxHorasExtraSemana() : new BigDecimal("12");
        for (GuardarDetalleDto d : detalles) {
            BigDecimal extrasSemana = queryRepo.sumExtrasSemana(empresaId, d.getEmpleadoId(), lunes, domingo);
            if (extrasSemana.compareTo(maxSemana) > 0) {
                alertas.add(alerta(saved, frente, d.getEmpleadoId(), "HORAS_EXTRA_SUPERA_LIMITE_SEMANA", "CRITICA",
                        "Las horas extra de la semana (" + extrasSemana + ") superan el máximo (" + maxSemana + ")"));
            }
        }

        if (!alertas.isEmpty()) alertaRepo.saveAll(alertas);

        return obtener(frenteId, fecha, empresaId);
    }

    @Override
    @Transactional
    public AsistenciaFrenteDto subirSoporte(Long frenteId, LocalDate fecha, MultipartFile file,
            Integer empresaId, Long usuarioId) {
        ProyectoFrenteEntity frente = getFrente(frenteId, empresaId);

        // Asegurar que exista la cabecera de asistencia para ese frente/fecha.
        AsistenciaFrenteEntity af = asistFrenteRepo
                .findByFrenteIdAndFechaAndDeletedAtIsNull(frenteId, fecha)
                .orElseGet(() -> {
                    AsistenciaFrenteEntity nueva = new AsistenciaFrenteEntity();
                    nueva.setEmpresaId(empresaId);
                    nueva.setProyectoId(frente.getProyectoId());
                    nueva.setFrenteId(frenteId);
                    nueva.setLiderId(frente.getLiderId());
                    nueva.setFecha(fecha);
                    nueva.setEstado("BORRADOR");
                    nueva.setCreatedBy(usuarioId);
                    return asistFrenteRepo.save(nueva);
                });

        if (esNoEditable(af.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La asistencia ya fue aprobada o enviada a nómina; no se puede cambiar el soporte");
        }

        String hash = calcularHash(file);
        // Duplicado: ¿ya existía otro PDF con el mismo hash? (chequear ANTES de guardar el nuevo)
        boolean duplicado = hash != null
                && soporteRepo.existsByEmpresaIdAndHashArchivoAndDeletedAtIsNull(empresaId, hash);

        String url = r2Storage.subirPdf(file, "asistencia-soportes");

        AsistenciaSoportePdfEntity soporte = new AsistenciaSoportePdfEntity();
        soporte.setEmpresaId(empresaId);
        soporte.setAsistenciaFrenteId(af.getId());
        soporte.setProyectoId(frente.getProyectoId());
        soporte.setFrenteId(frenteId);
        soporte.setLiderId(frente.getLiderId());
        soporte.setFecha(fecha);
        soporte.setArchivoUrl(url);
        soporte.setNombreArchivo(file.getOriginalFilename());
        soporte.setPesoArchivo(file.getSize());
        soporte.setMimeType(file.getContentType());
        soporte.setHashArchivo(hash);
        soporte.setEstado("CARGADO");
        soporte.setCreatedBy(usuarioId);
        AsistenciaSoportePdfEntity savedSoporte = soporteRepo.save(soporte);

        // Alerta si el mismo PDF ya se había cargado antes (posible duplicado).
        if (duplicado) {
            AsistenciaAlertaEntity a = alerta(af, frente, null, "PDF_DUPLICADO", "ADVERTENCIA",
                    "El PDF cargado coincide con uno ya existente (posible duplicado)");
            alertaRepo.save(a);
        }

        af.setSoportePdfId(savedSoporte.getId());
        af.setUpdatedBy(usuarioId);
        asistFrenteRepo.save(af);

        return obtener(frenteId, fecha, empresaId);
    }

    @Override
    @Transactional
    public void enviarRevision(Long asistenciaId, Integer empresaId, Long usuarioId) {
        AsistenciaFrenteEntity af = asistFrenteRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(asistenciaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Asistencia no encontrada"));

        if (!"BORRADOR".equals(af.getEstado()) && !"EN_CORRECCION".equals(af.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se puede enviar a revisión una asistencia en borrador o en corrección");
        }
        if (af.getSoportePdfId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No puede enviar la asistencia a revisión porque aún no se ha cargado el PDF soporte");
        }
        if (detalleRepo.findByAsistenciaFrenteIdAndDeletedAtIsNull(asistenciaId).isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay asistencia digitada para enviar a revisión");
        }
        if (alertaRepo.existsByAsistenciaFrenteIdAndNivelAndEstadoAndDeletedAtIsNull(asistenciaId, "CRITICA", "ABIERTA")) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Existen alertas críticas abiertas. Corrígelas antes de enviar a revisión");
        }

        af.setEstado("ENVIADO_REVISION");
        af.setEnviadoRevisionAt(LocalDateTime.now());
        af.setUpdatedBy(usuarioId);
        asistFrenteRepo.save(af);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private ProyectoFrenteEntity getFrente(Long frenteId, Integer empresaId) {
        return frenteRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(frenteId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Frente no encontrado"));
    }

    /**
     * Une los trabajadores actualmente asignados al frente con lo ya digitado:
     * cada asignado aparece con su detalle guardado (si existe) o en blanco; además
     * se conservan los detalles guardados de empleados que ya no están asignados.
     */
    private List<AsistenciaDetalleDto> fusionar(List<AsistenciaDetalleDto> guardados,
            Long frenteId, Integer empresaId) {
        java.util.Map<Long, AsistenciaDetalleDto> porEmpleado = new java.util.HashMap<>();
        for (AsistenciaDetalleDto g : guardados) porEmpleado.put(g.getEmpleadoId(), g);

        List<AsistenciaDetalleDto> res = new ArrayList<>();
        java.util.Set<Long> vistos = new java.util.HashSet<>();
        for (AsistenciaDetalleDto asignado : detallesEnBlanco(frenteId, empresaId)) {
            AsistenciaDetalleDto g = porEmpleado.get(asignado.getEmpleadoId());
            res.add(g != null ? g : asignado);
            vistos.add(asignado.getEmpleadoId());
        }
        for (AsistenciaDetalleDto g : guardados) {
            if (!vistos.contains(g.getEmpleadoId())) res.add(g);
        }
        return res;
    }

    private List<AsistenciaDetalleDto> detallesEnBlanco(Long frenteId, Integer empresaId) {
        List<AsistenciaDetalleDto> res = new ArrayList<>();
        for (FrenteTrabajadorDto t : frenteQueryRepo.listarTrabajadores(frenteId, empresaId)) {
            if (!"ACTIVO".equals(t.getEstado())) continue;
            AsistenciaDetalleDto d = new AsistenciaDetalleDto();
            d.setEmpleadoId(t.getEmpleadoId());
            d.setEmpleadoNombre(t.getEmpleadoNombre());
            d.setDocumento(t.getDocumento());
            d.setCargo(t.getCargo());
            d.setHorasTrabajadas(BigDecimal.ZERO);
            d.setEstadoAsistencia("SIN_REGISTRO");
            d.setEstadoRevision("PENDIENTE");
            res.add(d);
        }
        return res;
    }

    private AsistenciaAlertaEntity alerta(AsistenciaFrenteEntity af, ProyectoFrenteEntity frente,
            Long empleadoId, String tipo, String nivel, String descripcion) {
        AsistenciaAlertaEntity a = new AsistenciaAlertaEntity();
        a.setEmpresaId(af.getEmpresaId());
        a.setAsistenciaFrenteId(af.getId());
        a.setProyectoId(frente.getProyectoId());
        a.setFrenteId(frente.getId());
        a.setEmpleadoId(empleadoId);
        a.setTipoAlerta(tipo);
        a.setNivel(nivel);
        a.setDescripcion(descripcion);
        a.setEstado("ABIERTA");
        return a;
    }

    private LocalTime parseHora(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            return LocalTime.parse(hhmm.trim().length() == 5 ? hhmm.trim() : hhmm.trim().substring(0, 5));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean esNoEditable(String estado) {
        return "APROBADO".equals(estado) || "ENVIADO_NOMINA".equals(estado) || "ANULADO".equals(estado);
    }

    private String calcularHash(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
