package com.cloud_technological.aura_pos.services.implementations;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaDetalleDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.GuardarBorradorDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.GuardarDetalleDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.FilaMarcajeDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.ImportarMarcajeDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.ResultadoFilaMarcajeDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.ResultadoMarcajeDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTrabajadorDto;
import com.cloud_technological.aura_pos.entity.AsistenciaFrenteEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaSoportePdfEntity;
import com.cloud_technological.aura_pos.entity.ProyectoFrenteEntity;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteQueryRepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaSoportePdfJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteQueryRepository;
import com.cloud_technological.aura_pos.services.AsistenciaFrenteExcelService;
import com.cloud_technological.aura_pos.services.AsistenciaFrenteService;
import com.cloud_technological.aura_pos.services.asistencia.ModoAsistenciaObra;
import com.cloud_technological.aura_pos.services.asistencia.ReglasMarcaje;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/** Implementación de {@link AsistenciaFrenteExcelService}. */
@Service
@RequiredArgsConstructor
public class AsistenciaFrenteExcelServiceImpl implements AsistenciaFrenteExcelService {

    /** Filas máximas por archivo: un mes de 60 trabajadores. */
    private static final int MAX_FILAS = 2000;
    private static final int MAX_DESCANSO = 720;
    private static final String OK = "OK";
    private static final String ADVERTENCIA = "ADVERTENCIA";
    private static final String ERROR = "ERROR";
    private static final String OMITIDA = "OMITIDA";

    private final ProyectoFrenteJPARepository frenteRepo;
    private final ProyectoFrenteQueryRepository frenteQueryRepo;
    private final AsistenciaFrenteJPARepository asistFrenteRepo;
    private final AsistenciaFrenteQueryRepository asistQueryRepo;
    private final AsistenciaSoportePdfJPARepository soporteRepo;
    private final AsistenciaFrenteService asistenciaFrenteService;
    private final ClasificadorHorasService clasificador;
    private final R2StorageService r2Storage;
    private final ModoAsistenciaObra modoAsistencia;

    /** Una fila ya validada, lista para guardar. */
    private record FilaValida(LocalDate fecha, GuardarDetalleDto detalle) {
    }

    /** Validación completa del archivo: el resultado para el usuario y lo que se guardaría. */
    private record Validacion(ResultadoMarcajeDto resultado, List<FilaValida> validas) {
    }

    @Override
    public ResultadoMarcajeDto validar(Long frenteId, ImportarMarcajeDto dto, Integer empresaId) {
        modoAsistencia.exigirExcel(empresaId);
        return validarTodo(getFrente(frenteId, empresaId), dto, empresaId).resultado();
    }

    @Override
    @Transactional
    public ResultadoMarcajeDto confirmar(Long frenteId, ImportarMarcajeDto dto, MultipartFile archivo,
            Integer empresaId, Long usuarioId) {
        modoAsistencia.exigirExcel(empresaId);
        // Un día importado no necesita PDF (el Excel es su soporte): no se avisa si el
        // archivo no se pudo guardar.
        boolean exigeSoporte = false;
        ProyectoFrenteEntity frente = getFrente(frenteId, empresaId);
        Validacion v = validarTodo(frente, dto, empresaId);
        ResultadoMarcajeDto r = v.resultado();
        if (r.getErrores() > 0 || v.validas().isEmpty()) return r;

        String soporteUrl = subirSoporte(archivo, r, exigeSoporte);
        String hash = soporteUrl != null ? hash(archivo) : null;

        Map<LocalDate, List<GuardarDetalleDto>> porDia = new TreeMap<>();
        for (FilaValida f : v.validas()) porDia.computeIfAbsent(f.fecha(), k -> new ArrayList<>()).add(f.detalle());

        for (Map.Entry<LocalDate, List<GuardarDetalleDto>> dia : porDia.entrySet()) {
            GuardarBorradorDto borrador = new GuardarBorradorDto();
            borrador.setFecha(dia.getKey());
            borrador.setDetalles(conLosYaDigitados(frenteId, dia.getKey(), dia.getValue()));
            asistenciaFrenteService.guardarBorradorImportado(frenteId, borrador, empresaId, usuarioId);

            AsistenciaFrenteEntity af = asistFrenteRepo.findByFrenteIdAndFechaAndDeletedAtIsNull(frenteId, dia.getKey())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "No se pudo guardar la asistencia del " + dia.getKey()));
            af.setOrigen("EXCEL");
            if (soporteUrl != null) af.setSoportePdfId(registrarSoporte(af, frente, archivo, soporteUrl, hash, usuarioId));
            af.setUpdatedBy(usuarioId);
            asistFrenteRepo.save(af);
        }
        r.setConfirmado(true);
        return r;
    }

    // ── Validación ───────────────────────────────────────────────────────────

    /** Valida fila por fila; las válidas se clasifican con el mismo motor que la digitación. */
    private Validacion validarTodo(ProyectoFrenteEntity frente, ImportarMarcajeDto dto, Integer empresaId) {
        List<FilaMarcajeDto> filas = dto != null && dto.getFilas() != null ? dto.getFilas() : List.of();
        if (filas.isEmpty()) throw new GlobalException(HttpStatus.BAD_REQUEST, "El archivo no trae filas.");
        if (filas.size() > MAX_FILAS)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El archivo trae " + filas.size() + " filas; el máximo es " + MAX_FILAS + ". Divídalo por quincena.");

        Map<String, FrenteTrabajadorDto> trabajadores = trabajadoresPorDocumento(frente.getId(), empresaId);
        Map<LocalDate, String> estadoDia = new HashMap<>();
        Set<String> vistos = new HashSet<>();
        Set<LocalDate> dias = new HashSet<>();
        Set<String> avisos = new LinkedHashSet<>();
        ResultadoMarcajeDto r = new ResultadoMarcajeDto();
        List<FilaValida> validas = new ArrayList<>();

        for (FilaMarcajeDto f : filas) {
            ResultadoFilaMarcajeDto rf = new ResultadoFilaMarcajeDto();
            rf.setFila(f.getFila());
            rf.setDocumento(limpio(f.getDocumento()));
            rf.setFecha(f.getFecha());
            rf.setHoraEntrada(limpio(f.getHoraEntrada()));
            rf.setHoraSalida(limpio(f.getHoraSalida()));
            rf.setMinutosDescanso(f.getMinutosDescanso());
            r.getFilas().add(rf);

            Optional<FilaValida> valida = validarFila(f, rf, frente, trabajadores, estadoDia, vistos, avisos, empresaId);
            switch (rf.getResultado()) {
                case ERROR -> r.setErrores(r.getErrores() + 1);
                case OMITIDA -> r.setOmitidas(r.getOmitidas() + 1);
                case ADVERTENCIA -> r.setAdvertencias(r.getAdvertencias() + 1);
                default -> { }
            }
            if (valida.isPresent()) {
                validas.add(valida.get());
                dias.add(valida.get().fecha());
                r.setFilasValidas(r.getFilasValidas() + 1);
                r.setHorasTrabajadas(r.getHorasTrabajadas().add(rf.getHorasTrabajadas()));
                r.setHorasExtra(r.getHorasExtra().add(rf.getHorasExtra()));
                r.setHorasRecargo(r.getHorasRecargo().add(rf.getHorasRecargo()));
            }
        }
        r.setDias(dias.size());
        r.getAvisos().addAll(avisos);
        return new Validacion(r, validas);
    }

    /** Valida una fila; deja en {@code rf} el resultado y el mensaje. */
    private Optional<FilaValida> validarFila(FilaMarcajeDto f, ResultadoFilaMarcajeDto rf, ProyectoFrenteEntity frente,
            Map<String, FrenteTrabajadorDto> trabajadores, Map<LocalDate, String> estadoDia, Set<String> vistos,
            Set<String> avisos, Integer empresaId) {
        boolean conHoras = rf.getHoraEntrada() != null || rf.getHoraSalida() != null;
        boolean sinDatos = !conHoras && limpio(f.getEstado()) == null && f.getMinutosDescanso() == null;
        if (sinDatos) return marcar(rf, OMITIDA, "Fila sin entrada, salida ni estado: no se registra.");

        if (rf.getDocumento() == null) return marcar(rf, ERROR, "Falta el documento del trabajador.");
        FrenteTrabajadorDto t = trabajadores.get(rf.getDocumento());
        if (t == null)
            return marcar(rf, ERROR, "El documento " + rf.getDocumento() + " no es un trabajador activo de este proyecto o frente.");
        rf.setEmpleado(t.getEmpleadoNombre());

        if (f.getFecha() == null) return marcar(rf, ERROR, "Falta la fecha.");
        if (f.getFecha().isAfter(LocalDate.now())) return marcar(rf, ERROR, "La fecha es futura.");
        if (!vistos.add(rf.getDocumento() + "|" + f.getFecha()))
            return marcar(rf, ERROR, "El trabajador está dos veces el " + f.getFecha() + " en el archivo.");

        String estadoAsistencia = estadoDia.computeIfAbsent(f.getFecha(), fecha -> asistFrenteRepo
                .findByFrenteIdAndFechaAndDeletedAtIsNull(frente.getId(), fecha)
                .map(AsistenciaFrenteEntity::getEstado).orElse("BORRADOR"));
        if ("APROBADO".equals(estadoAsistencia) || "ENVIADO_NOMINA".equals(estadoAsistencia) || "ANULADO".equals(estadoAsistencia))
            return marcar(rf, ERROR, "El " + f.getFecha() + " ya está " + estadoAsistencia.toLowerCase().replace('_', ' ')
                    + " y no se puede modificar.");

        Optional<String> estado = ReglasMarcaje.estado(f.getEstado(), conHoras);
        if (estado.isEmpty())
            return marcar(rf, ERROR, "Estado \"" + f.getEstado() + "\" no válido. Use: " + ReglasMarcaje.estadosValidos() + ".");
        rf.setEstadoAsistencia(estado.get());

        Optional<LocalTime> entrada = ReglasMarcaje.hora(rf.getHoraEntrada());
        Optional<LocalTime> salida = ReglasMarcaje.hora(rf.getHoraSalida());
        if (rf.getHoraEntrada() != null && entrada.isEmpty())
            return marcar(rf, ERROR, "La hora de entrada \"" + rf.getHoraEntrada() + "\" no es válida (use HH:mm, p. ej. 07:00).");
        if (rf.getHoraSalida() != null && salida.isEmpty())
            return marcar(rf, ERROR, "La hora de salida \"" + rf.getHoraSalida() + "\" no es válida (use HH:mm, p. ej. 20:00).");
        if (ReglasMarcaje.exigeHoras(estado.get()) && (entrada.isEmpty() || salida.isEmpty()))
            return marcar(rf, ERROR, "Con estado " + estado.get().replace('_', ' ').toLowerCase() + " se necesitan entrada y salida.");
        if (f.getMinutosDescanso() != null && (f.getMinutosDescanso() < 0 || f.getMinutosDescanso() > MAX_DESCANSO))
            return marcar(rf, ERROR, "El descanso debe estar entre 0 y " + MAX_DESCANSO + " minutos.");

        ClasificadorHorasService.Resultado c = clasificador.clasificar(empresaId, frente.getId(), f.getFecha(),
                entrada.orElse(null), salida.orElse(null), estado.get(), f.getMinutosDescanso());
        rf.setHorasTrabajadas(c.total);
        rf.setHorasExtra(c.extraDiurna.add(c.extraNocturna).add(c.extraDiurnaDomFest).add(c.extraNocturnaDomFest));
        rf.setHorasRecargo(c.ordNocturna.add(c.domFest));

        List<String> notas = new ArrayList<>();
        for (ClasificadorHorasService.Alerta a : c.alertas) {
            switch (a.tipo()) {
                case "SALIDA_MENOR_ENTRADA" -> {
                    return marcar(rf, ERROR, "La salida es igual o anterior a la entrada y el turno no cruza la medianoche.");
                }
                case "JORNADA_CONFIG_NO_EXISTE" -> avisos.add("No hay configuración laboral vigente (Laboral → Jornada) para "
                        + "algunas fechas: se usaron valores por defecto y esos días no se podrán enviar a revisión hasta configurarla.");
                case "TURNO_NO_CONFIGURADO" -> avisos.add("El frente no tiene turno asignado: se asumen 8 horas ordinarias por día.");
                default -> notas.add(a.descripcion());
            }
        }

        GuardarDetalleDto d = new GuardarDetalleDto();
        d.setEmpleadoId(t.getEmpleadoId());
        d.setHoraEntrada(entrada.map(this::hhmm).orElse(null));
        d.setHoraSalida(salida.map(this::hhmm).orElse(null));
        d.setMinutosDescanso(f.getMinutosDescanso());
        d.setEstadoAsistencia(estado.get());
        d.setObservacionLider(recortar(limpio(f.getObservacion()), 500));
        if (notas.isEmpty()) marcar(rf, OK, null);
        else marcar(rf, ADVERTENCIA, String.join(" ", notas));
        return Optional.of(new FilaValida(f.getFecha(), d));
    }

    private static Optional<FilaValida> marcar(ResultadoFilaMarcajeDto rf, String resultado, String mensaje) {
        rf.setResultado(resultado);
        rf.setMensaje(mensaje);
        return Optional.empty();
    }

    /** Trabajadores activos del frente por número de documento. */
    private Map<String, FrenteTrabajadorDto> trabajadoresPorDocumento(Long frenteId, Integer empresaId) {
        Map<String, FrenteTrabajadorDto> m = new HashMap<>();
        for (FrenteTrabajadorDto t : frenteQueryRepo.listarTrabajadores(frenteId, empresaId)) {
            if (!"ACTIVO".equals(t.getEstado()) || Boolean.FALSE.equals(t.getEmpleadoActivo())) continue;
            if (t.getDocumento() != null) m.put(t.getDocumento().trim(), t);
        }
        return m;
    }

    // ── Guardado ─────────────────────────────────────────────────────────────

    /**
     * El borrador del día reemplaza todos sus detalles: se le agregan los
     * trabajadores ya digitados que no vienen en el archivo, para no borrarlos.
     */
    private List<GuardarDetalleDto> conLosYaDigitados(Long frenteId, LocalDate fecha, List<GuardarDetalleDto> delExcel) {
        List<GuardarDetalleDto> todos = new ArrayList<>(delExcel);
        Set<Long> enExcel = new HashSet<>();
        delExcel.forEach(d -> enExcel.add(d.getEmpleadoId()));
        Optional<AsistenciaFrenteEntity> existente = asistFrenteRepo.findByFrenteIdAndFechaAndDeletedAtIsNull(frenteId, fecha);
        if (existente.isEmpty()) return todos;
        for (AsistenciaDetalleDto g : asistQueryRepo.listarDetalles(existente.get().getId())) {
            if (enExcel.contains(g.getEmpleadoId())) continue;
            GuardarDetalleDto d = new GuardarDetalleDto();
            d.setEmpleadoId(g.getEmpleadoId());
            d.setHoraEntrada(g.getHoraEntrada());
            d.setHoraSalida(g.getHoraSalida());
            d.setMinutosDescanso(g.getMinutosDescanso());
            d.setEstadoAsistencia(g.getEstadoAsistencia());
            d.setObservacionLider(g.getObservacionLider());
            todos.add(d);
        }
        return todos;
    }

    /**
     * Sube el Excel como soporte; si el almacenamiento falla, sigue sin él. Solo
     * avisa cuando la empresa exige soporte para enviar a revisión (V243).
     */
    private String subirSoporte(MultipartFile archivo, ResultadoMarcajeDto r, boolean exigeSoporte) {
        if (archivo == null || archivo.isEmpty()) {
            if (exigeSoporte)
                r.getAvisos().add("No se adjuntó el archivo: cargue el soporte de cada día antes de enviarlo a revisión.");
            return null;
        }
        try {
            return r2Storage.subirSoporte(archivo, "asistencia-soportes");
        } catch (GlobalException e) {
            if (exigeSoporte)
                r.getAvisos().add("La asistencia se importó, pero el Excel no se guardó como soporte (" + e.getMessage()
                        + "). Cargue el soporte de cada día antes de enviarlo a revisión.");
            return null;
        }
    }

    private Long registrarSoporte(AsistenciaFrenteEntity af, ProyectoFrenteEntity frente, MultipartFile archivo,
            String url, String hash, Long usuarioId) {
        AsistenciaSoportePdfEntity s = new AsistenciaSoportePdfEntity();
        s.setEmpresaId(af.getEmpresaId());
        s.setAsistenciaFrenteId(af.getId());
        s.setProyectoId(frente.getProyectoId());
        s.setFrenteId(frente.getId());
        s.setLiderId(frente.getLiderId());
        s.setFecha(af.getFecha());
        s.setArchivoUrl(url);
        s.setNombreArchivo(archivo.getOriginalFilename());
        s.setPesoArchivo(archivo.getSize());
        s.setMimeType(archivo.getContentType());
        s.setHashArchivo(hash);
        s.setEstado("CARGADO");
        s.setCreatedBy(usuarioId);
        return soporteRepo.save(s).getId();
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private ProyectoFrenteEntity getFrente(Long frenteId, Integer empresaId) {
        return frenteRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(frenteId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Frente no encontrado"));
    }

    private String hhmm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String hash(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(file.getBytes())) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
