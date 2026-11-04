package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoAdjuntoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoTopeHorasDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CopiaPlantillaDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.GuardarCargoConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.GuardarCargoNovedadDto;
import com.cloud_technological.aura_pos.entity.NominaCargoAdjuntoEntity;
import com.cloud_technological.aura_pos.entity.NominaCargoConceptoEntity;
import com.cloud_technological.aura_pos.entity.NominaCargoNovedadEntity;
import com.cloud_technological.aura_pos.entity.NominaCargoTopeHorasEntity;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoAdjuntoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoConceptoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoNovedadJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoPlantillaQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoTopeHorasJPARepository;
import com.cloud_technological.aura_pos.services.NominaCargoPlantillaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link NominaCargoPlantillaService}. */
@Service
public class NominaCargoPlantillaServiceImpl implements NominaCargoPlantillaService {

    /** Ley 50 de 1990, art. 22: máximo 2 horas extra al día y 12 a la semana. */
    private static final BigDecimal MAX_EXTRA_DIA = new BigDecimal("2");
    private static final BigDecimal MAX_EXTRA_SEMANA = new BigDecimal("12");
    /** Último tipo de hora extra en nomina_concepto_tipo (3–6 extras, 7–9 recargos). */
    private static final int ULTIMO_TIPO_EXTRA = 6;
    /** Código del nivel directivo en el catálogo NIVEL_CARGO. */
    private static final String NIVEL_DIRECTIVO = "DIR";
    private static final int MAX_ADJUNTOS = 20;

    private final NominaCargoQueryRepository cargos;
    private final NominaCargoPlantillaQueryRepository queryRepo;
    private final NominaCargoConceptoJPARepository conceptoRepo;
    private final NominaCargoNovedadJPARepository novedadRepo;
    private final NominaCargoTopeHorasJPARepository topeRepo;
    private final NominaCargoAdjuntoJPARepository adjuntoRepo;
    private final R2StorageService storage;

    public NominaCargoPlantillaServiceImpl(NominaCargoQueryRepository cargos,
            NominaCargoPlantillaQueryRepository queryRepo,
            NominaCargoConceptoJPARepository conceptoRepo,
            NominaCargoNovedadJPARepository novedadRepo,
            NominaCargoTopeHorasJPARepository topeRepo,
            NominaCargoAdjuntoJPARepository adjuntoRepo,
            R2StorageService storage) {
        this.cargos = cargos;
        this.queryRepo = queryRepo;
        this.conceptoRepo = conceptoRepo;
        this.novedadRepo = novedadRepo;
        this.topeRepo = topeRepo;
        this.adjuntoRepo = adjuntoRepo;
        this.storage = storage;
    }

    // ── Conceptos ────────────────────────────────────────────────────────────

    @Override
    public List<CargoConceptoDto> conceptos(Long cargoId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        return queryRepo.conceptos(cargoId, empresaId);
    }

    @Override
    @Transactional
    public List<CargoConceptoDto> agregarConceptos(Long cargoId, List<Integer> numeros, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        Set<Integer> nuevos = numerosNuevos(cargoId, numeros, empresaId,
                new HashSet<>(queryRepo.numerosConceptos(cargoId)),
                new HashSet<>(queryRepo.numerosNovedades(cargoId)), "las novedades permitidas");
        for (Integer numero : nuevos) {
            NominaCargoConceptoEntity e = new NominaCargoConceptoEntity();
            e.setCargoId(cargoId);
            e.setConceptoNumero(numero);
            e.setCreatedAt(LocalDateTime.now());
            conceptoRepo.save(e);
        }
        conceptoRepo.flush();
        return queryRepo.conceptos(cargoId, empresaId);
    }

    @Override
    @Transactional
    public List<CargoConceptoDto> actualizarConcepto(Long cargoId, Long filaId, GuardarCargoConceptoDto dto,
            Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        NominaCargoConceptoEntity e = conceptoRepo.findById(filaId)
                .filter(f -> f.getCargoId().equals(cargoId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto del cargo no encontrado"));
        noNegativo(dto.getCantidad(), "La cantidad");
        noNegativo(dto.getValor(), "El valor");
        noNegativo(dto.getPorcentaje(), "El porcentaje");
        e.setCantidad(dto.getCantidad());
        e.setValor(dto.getValor());
        e.setPorcentaje(dto.getPorcentaje());
        e.setObservacion(recortar(dto.getObservacion(), 300));
        e.setUpdatedAt(LocalDateTime.now());
        conceptoRepo.saveAndFlush(e);
        return queryRepo.conceptos(cargoId, empresaId);
    }

    @Override
    @Transactional
    public List<CargoConceptoDto> quitarConcepto(Long cargoId, Long filaId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        NominaCargoConceptoEntity e = conceptoRepo.findById(filaId)
                .filter(f -> f.getCargoId().equals(cargoId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto del cargo no encontrado"));
        conceptoRepo.delete(e);
        conceptoRepo.flush();
        return queryRepo.conceptos(cargoId, empresaId);
    }

    // ── Novedades permitidas ─────────────────────────────────────────────────

    @Override
    public List<CargoNovedadDto> novedades(Long cargoId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        return queryRepo.novedades(cargoId, empresaId);
    }

    @Override
    @Transactional
    public List<CargoNovedadDto> agregarNovedades(Long cargoId, List<Integer> numeros, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        Set<Integer> nuevos = numerosNuevos(cargoId, numeros, empresaId,
                new HashSet<>(queryRepo.numerosNovedades(cargoId)),
                new HashSet<>(queryRepo.numerosConceptos(cargoId)), "los conceptos fijos");
        for (Integer numero : nuevos) {
            NominaCargoNovedadEntity e = new NominaCargoNovedadEntity();
            e.setCargoId(cargoId);
            e.setConceptoNumero(numero);
            e.setRequiereAutorizacion(Boolean.FALSE);
            e.setCreatedAt(LocalDateTime.now());
            novedadRepo.save(e);
        }
        novedadRepo.flush();
        return queryRepo.novedades(cargoId, empresaId);
    }

    @Override
    @Transactional
    public List<CargoNovedadDto> actualizarNovedad(Long cargoId, Long filaId, GuardarCargoNovedadDto dto,
            Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        NominaCargoNovedadEntity e = novedadRepo.findById(filaId)
                .filter(f -> f.getCargoId().equals(cargoId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Novedad del cargo no encontrada"));
        noNegativo(dto.getTopeCantidad(), "El tope de cantidad");
        noNegativo(dto.getTopeValor(), "El tope de valor");
        e.setTopeCantidad(dto.getTopeCantidad());
        e.setTopeValor(dto.getTopeValor());
        e.setRequiereAutorizacion(Boolean.TRUE.equals(dto.getRequiereAutorizacion()));
        e.setUpdatedAt(LocalDateTime.now());
        novedadRepo.saveAndFlush(e);
        return queryRepo.novedades(cargoId, empresaId);
    }

    @Override
    @Transactional
    public List<CargoNovedadDto> quitarNovedad(Long cargoId, Long filaId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        NominaCargoNovedadEntity e = novedadRepo.findById(filaId)
                .filter(f -> f.getCargoId().equals(cargoId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Novedad del cargo no encontrada"));
        novedadRepo.delete(e);
        novedadRepo.flush();
        return queryRepo.novedades(cargoId, empresaId);
    }

    // ── Topes de horas extra y recargos ──────────────────────────────────────

    @Override
    public List<CargoTopeHorasDto> topes(Long cargoId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        return queryRepo.topes(cargoId);
    }

    @Override
    @Transactional
    public List<CargoTopeHorasDto> guardarTopes(Long cargoId, List<CargoTopeHorasDto> topes, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        boolean directivo = queryRepo.codigoNivel(cargoId).map(NIVEL_DIRECTIVO::equalsIgnoreCase).orElse(false);

        for (CargoTopeHorasDto t : topes) {
            validarTope(t, directivo);
        }
        // Reemplazo completo: se borran los del cargo y se guardan los que traen algún tope.
        topeRepo.deleteAllById(queryRepo.idsTopes(cargoId));
        topeRepo.flush();
        for (CargoTopeHorasDto t : topes) {
            if (t.getTopeDia() == null && t.getTopeSemana() == null && t.getTopeMes() == null) continue;
            NominaCargoTopeHorasEntity e = new NominaCargoTopeHorasEntity();
            e.setCargoId(cargoId);
            e.setTipoConceptoId(t.getTipoConceptoId());
            e.setTopeDia(t.getTopeDia());
            e.setTopeSemana(t.getTopeSemana());
            e.setTopeMes(t.getTopeMes());
            e.setCreatedAt(LocalDateTime.now());
            topeRepo.save(e);
        }
        topeRepo.flush();
        return queryRepo.topes(cargoId);
    }

    // ── Adjuntos ─────────────────────────────────────────────────────────────

    @Override
    public List<CargoAdjuntoDto> adjuntos(Long cargoId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        return queryRepo.adjuntos(cargoId);
    }

    @Override
    @Transactional
    public CargoAdjuntoDto subirAdjunto(Long cargoId, MultipartFile archivo, Integer empresaId, Integer usuarioId) {
        exigirCargo(cargoId, empresaId);
        if (queryRepo.contarAdjuntos(cargoId) >= MAX_ADJUNTOS) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El cargo ya tiene " + MAX_ADJUNTOS + " adjuntos, que es el máximo.");
        }
        String url = storage.subirSoporte(archivo, "nomina-cargos/" + empresaId);

        NominaCargoAdjuntoEntity e = new NominaCargoAdjuntoEntity();
        e.setEmpresaId(empresaId);
        e.setCargoId(cargoId);
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "adjunto";
        e.setNombreArchivo(nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre);
        e.setArchivoUrl(url);
        e.setContentType(archivo.getContentType());
        e.setTamanoBytes(archivo.getSize());
        e.setUsuarioId(usuarioId);
        e.setCreatedAt(LocalDateTime.now());
        Long id = adjuntoRepo.saveAndFlush(e).getId();
        return queryRepo.adjuntos(cargoId).stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el adjunto"));
    }

    @Override
    @Transactional
    public void eliminarAdjunto(Long cargoId, Long adjuntoId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        NominaCargoAdjuntoEntity e = adjuntoRepo.findById(adjuntoId)
                .filter(a -> a.getCargoId().equals(cargoId) && a.getDeletedAt() == null)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
        e.setDeletedAt(LocalDateTime.now());
        adjuntoRepo.save(e);
    }

    // ── Copiar de otro cargo ─────────────────────────────────────────────────

    @Override
    @Transactional
    public CopiaPlantillaDto copiarDe(Long cargoId, Long origenId, Integer empresaId) {
        exigirCargo(cargoId, empresaId);
        exigirCargo(origenId, empresaId);
        if (cargoId.equals(origenId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija un cargo distinto para copiar.");
        }
        Set<Integer> conceptos = new HashSet<>(queryRepo.numerosConceptos(cargoId));
        Set<Integer> novedades = new HashSet<>(queryRepo.numerosNovedades(cargoId));
        Set<Integer> tipos = new HashSet<>(queryRepo.tiposConTope(cargoId));
        CopiaPlantillaDto r = new CopiaPlantillaDto();

        for (CargoConceptoDto o : queryRepo.conceptos(origenId, empresaId)) {
            if (conceptos.contains(o.getConceptoNumero()) || novedades.contains(o.getConceptoNumero())) continue;
            NominaCargoConceptoEntity e = new NominaCargoConceptoEntity();
            e.setCargoId(cargoId);
            e.setConceptoNumero(o.getConceptoNumero());
            e.setCantidad(o.getCantidad());
            e.setValor(o.getValor());
            e.setPorcentaje(o.getPorcentaje());
            e.setObservacion(o.getObservacion());
            e.setCreatedAt(LocalDateTime.now());
            conceptoRepo.save(e);
            conceptos.add(o.getConceptoNumero());
            r.setConceptos(r.getConceptos() + 1);
        }
        for (CargoNovedadDto o : queryRepo.novedades(origenId, empresaId)) {
            if (novedades.contains(o.getConceptoNumero()) || conceptos.contains(o.getConceptoNumero())) continue;
            NominaCargoNovedadEntity e = new NominaCargoNovedadEntity();
            e.setCargoId(cargoId);
            e.setConceptoNumero(o.getConceptoNumero());
            e.setTopeCantidad(o.getTopeCantidad());
            e.setTopeValor(o.getTopeValor());
            e.setRequiereAutorizacion(Boolean.TRUE.equals(o.getRequiereAutorizacion()));
            e.setCreatedAt(LocalDateTime.now());
            novedadRepo.save(e);
            r.setNovedades(r.getNovedades() + 1);
        }
        boolean directivo = queryRepo.codigoNivel(cargoId).map(NIVEL_DIRECTIVO::equalsIgnoreCase).orElse(false);
        for (CargoTopeHorasDto o : queryRepo.topes(origenId)) {
            boolean sinTope = o.getTopeDia() == null && o.getTopeSemana() == null && o.getTopeMes() == null;
            // Un directivo no tiene horas extra: esos topes no se copian.
            boolean extraEnDirectivo = directivo && o.getTipoConceptoId() <= ULTIMO_TIPO_EXTRA;
            if (sinTope || extraEnDirectivo || tipos.contains(o.getTipoConceptoId())) continue;
            NominaCargoTopeHorasEntity e = new NominaCargoTopeHorasEntity();
            e.setCargoId(cargoId);
            e.setTipoConceptoId(o.getTipoConceptoId());
            e.setTopeDia(o.getTopeDia());
            e.setTopeSemana(o.getTopeSemana());
            e.setTopeMes(o.getTopeMes());
            e.setCreatedAt(LocalDateTime.now());
            topeRepo.save(e);
            r.setTopes(r.getTopes() + 1);
        }
        return r;
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** 404 si el cargo no es de la empresa o está borrado. */
    private void exigirCargo(Long cargoId, Integer empresaId) {
        if (cargos.obtener(cargoId, empresaId).isEmpty()) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Cargo no encontrado");
        }
    }

    /**
     * Números que de verdad hay que agregar: sin repetidos ni los que ya están.
     * 400 si alguno no existe para la empresa o ya está en la otra lista del cargo.
     */
    private Set<Integer> numerosNuevos(Long cargoId, List<Integer> numeros, Integer empresaId,
            Set<Integer> yaEnLista, Set<Integer> enLaOtra, String nombreOtra) {
        if (numeros == null || numeros.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija al menos un concepto.");
        }
        Set<Integer> nuevos = new LinkedHashSet<>();
        for (Integer numero : numeros) {
            if (numero == null || yaEnLista.contains(numero)) continue;
            if (enLaOtra.contains(numero)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El concepto " + numero + " ya está en " + nombreOtra + " del cargo.");
            }
            if (!queryRepo.conceptoVisible(empresaId, numero)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "No existe un concepto activo con el código " + numero + ".");
            }
            nuevos.add(numero);
        }
        return nuevos;
    }

    private static void validarTope(CargoTopeHorasDto t, boolean directivo) {
        Integer tipo = t.getTipoConceptoId();
        if (tipo == null || tipo < 3 || tipo > 9) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de hora no válido.");
        }
        noNegativo(t.getTopeDia(), "El tope diario");
        noNegativo(t.getTopeSemana(), "El tope semanal");
        noNegativo(t.getTopeMes(), "El tope mensual");
        if (tipo > ULTIMO_TIPO_EXTRA) return;   // recargos: sin límite legal de horas

        boolean tieneTope = positivo(t.getTopeDia()) || positivo(t.getTopeSemana()) || positivo(t.getTopeMes());
        if (directivo && tieneTope) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un cargo de nivel directivo no tiene horas extra (art. 162 del CST): deje esos topes en cero.");
        }
        if (t.getTopeDia() != null && t.getTopeDia().compareTo(MAX_EXTRA_DIA) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    nombreTipo(t) + ": la ley permite máximo 2 horas extra al día.");
        }
        if (t.getTopeSemana() != null && t.getTopeSemana().compareTo(MAX_EXTRA_SEMANA) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    nombreTipo(t) + ": la ley permite máximo 12 horas extra a la semana.");
        }
    }

    private static String nombreTipo(CargoTopeHorasDto t) {
        return t.getTipoNombre() != null ? t.getTipoNombre() : "Hora extra";
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static void noNegativo(BigDecimal v, String campo) {
        if (v != null && v.signum() < 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, campo + " no puede ser negativo.");
        }
    }

    private static String recortar(String texto, int max) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
