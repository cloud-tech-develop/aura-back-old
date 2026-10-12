package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.PagarDocumentoDto;
import com.cloud_technological.aura_pos.entity.NominaDetalleEntity;
import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.mappers.NominaCopiaMapper;
import com.cloud_technological.aura_pos.repositories.nomina.NominaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoQueryRepository;
import com.cloud_technological.aura_pos.services.NominaDocumentoCicloService;
import com.cloud_technological.aura_pos.services.NominaDocumentoService;
import com.cloud_technological.aura_pos.services.NominaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link NominaDocumentoCicloService}. */
@Service
public class NominaDocumentoCicloServiceImpl implements NominaDocumentoCicloService {

    private static final String PRENOMINA = "PRENOMINA";
    private static final String NOMINA = "NOMINA";
    private static final String PRELIMINAR = "PRELIMINAR";
    private static final String CONFIRMADO = "CONFIRMADO";
    private static final String ANULADO = "ANULADO";
    /** Estado de las filas de la nómina hasta confirmarla. */
    private static final String FILA_SIN_APROBAR = "BORRADOR";
    /** Estado de las filas de la nómina confirmada pendientes de pago. */
    private static final String FILA_APROBADA = "APROBADO";

    private final NominaDocumentoJPARepository documentoRepository;
    private final NominaDocumentoQueryRepository queryRepository;
    private final NominaJPARepository nominaRepository;
    private final NominaDetalleJPARepository detalleRepository;
    private final NominaDocumentoService documentos;
    private final NominaService nominaService;
    private final PeriodoContableResolver periodosContables;
    private final NominaCopiaMapper copia;

    public NominaDocumentoCicloServiceImpl(NominaDocumentoJPARepository documentoRepository,
            NominaDocumentoQueryRepository queryRepository,
            NominaJPARepository nominaRepository,
            NominaDetalleJPARepository detalleRepository,
            NominaDocumentoService documentos,
            NominaService nominaService,
            PeriodoContableResolver periodosContables,
            NominaCopiaMapper copia) {
        this.documentoRepository = documentoRepository;
        this.queryRepository = queryRepository;
        this.nominaRepository = nominaRepository;
        this.detalleRepository = detalleRepository;
        this.documentos = documentos;
        this.nominaService = nominaService;
        this.periodosContables = periodosContables;
        this.copia = copia;
    }

    @Override
    @Transactional
    public DocumentoNominaDto confirmarPrenomina(Long id, Integer empresaId) {
        NominaDocumentoEntity d = buscar(id, empresaId, PRENOMINA, PRELIMINAR);
        if (Boolean.TRUE.equals(d.getSimulacion()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Una simulación no se confirma. Cree una prenómina real para pagar.");
        if (queryRepository.filas(id).isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La prenómina no tiene empleados.");
        int sinLiquidar = queryRepository.filasSinLiquidar(id);
        if (sinLiquidar > 0)
            throw new GlobalException(HttpStatus.CONFLICT, "Hay " + sinLiquidar
                    + " empleado(s) sin liquidar o con error. Liquídelos o quítelos antes de confirmar.");
        if (d.getTotalDevengado() == null || d.getTotalDevengado().signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La prenómina no tiene valores liquidados.");
        periodosContables.exigirAbierto(empresaId, d.getFecha());

        d.setEstado(CONFIRMADO);
        d.setFechaConfirmacion(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        // saveAndFlush: obtener() relee con SQL y debe ver el cambio dentro de esta transacción.
        documentoRepository.saveAndFlush(d);
        return documentos.obtener(id, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto reabrirPrenomina(Long id, Integer empresaId) {
        NominaDocumentoEntity d = buscar(id, empresaId, PRENOMINA, CONFIRMADO);
        if (queryRepository.nominaActivaDeOrigen(id).isPresent())
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Ya se generó la nómina de esta prenómina. Anúlela primero para reabrir.");
        d.setEstado(PRELIMINAR);
        d.setFechaConfirmacion(null);
        d.setUpdatedAt(LocalDateTime.now());
        // saveAndFlush: obtener() relee con SQL y debe ver el cambio dentro de esta transacción.
        documentoRepository.saveAndFlush(d);
        return documentos.obtener(id, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto generarNomina(Long prenominaId, Integer empresaId, Long usuarioId) {
        NominaDocumentoEntity pre = buscar(prenominaId, empresaId, PRENOMINA, CONFIRMADO);
        queryRepository.nominaActivaDeOrigen(prenominaId).ifPresent(n -> {
            throw new GlobalException(HttpStatus.CONFLICT, "Ya se generó la nómina de esta prenómina.");
        });

        NominaDocumentoEntity nomina = copia.copiarEncabezado(pre);
        nomina.setClaseDocumento(NOMINA);
        nomina.setEstado(PRELIMINAR);
        nomina.setNumero(queryRepository.siguienteNumero(empresaId, pre.getTipoComprobanteId(), NOMINA));
        nomina.setDocumentoOrigenId(pre.getId());
        nomina.setUsuarioId(usuarioId);
        nomina.setCreatedAt(LocalDateTime.now());
        Long nominaId = documentoRepository.save(nomina).getId();

        for (Long filaId : queryRepository.filas(prenominaId))
            copiarFila(filaId, nominaId, empresaId);
        return documentos.obtener(nominaId, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto confirmarNomina(Long id, Integer empresaId) {
        NominaDocumentoEntity d = buscar(id, empresaId, NOMINA, PRELIMINAR);
        periodosContables.exigirAbierto(empresaId, d.getFecha());
        // aprobar() deja la fila APROBADO y, tras el commit, genera su asiento y
        // prepara la nómina electrónica (mismo camino que la liquidación por períodos).
        for (Long filaId : queryRepository.filas(id))
            nominaService.aprobar(filaId, empresaId);

        d.setEstado(CONFIRMADO);
        d.setFechaConfirmacion(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        // saveAndFlush: obtener() relee con SQL y debe ver el cambio dentro de esta transacción.
        documentoRepository.saveAndFlush(d);
        return documentos.obtener(id, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto anular(Long id, Integer empresaId) {
        NominaDocumentoEntity d = documentoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));
        if (PRENOMINA.equals(d.getClaseDocumento())) return documentos.anular(id, empresaId);

        if (ANULADO.equals(d.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "La nómina ya está anulada.");
        // anular() reversa el asiento de cada empleado y falla si alguno ya se pagó.
        for (Long filaId : queryRepository.filas(id))
            nominaService.anular(filaId, empresaId);

        d.setEstado(ANULADO);
        d.setUpdatedAt(LocalDateTime.now());
        // saveAndFlush: obtener() relee con SQL y debe ver el cambio dentro de esta transacción.
        documentoRepository.saveAndFlush(d);
        return documentos.obtener(id, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto pagar(Long id, PagarDocumentoDto dto, Integer empresaId) {
        buscar(id, empresaId, NOMINA, CONFIRMADO);
        List<Long> pendientes = queryRepository.filasEnEstado(id, FILA_APROBADA);
        List<Long> filas = dto.getNominaIds() == null || dto.getNominaIds().isEmpty()
                ? pendientes
                : dto.getNominaIds();
        if (filas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay empleados pendientes de pago.");
        for (Long filaId : filas) {
            if (!pendientes.contains(filaId))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Un empleado elegido no está pendiente de pago en esta nómina.");
            // Mismo pago que la liquidación por períodos: medio, saldo y asiento de pago.
            nominaService.pagar(filaId, dto.getPago(), empresaId);
        }
        return documentos.obtener(id, empresaId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** El documento de la clase y el estado esperados; 404 o 409 en otro caso. */
    private NominaDocumentoEntity buscar(Long id, Integer empresaId, String clase, String estado) {
        NominaDocumentoEntity d = documentoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));
        if (!clase.equals(d.getClaseDocumento()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    PRENOMINA.equals(clase) ? "El documento no es una prenómina." : "El documento no es una nómina.");
        if (!estado.equals(d.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El documento está " + d.getEstado().toLowerCase() + ".");
        return d;
    }

    /**
     * Copia una fila de la prenómina a la nómina con sus novedades y su detalle (el
     * detalle conserva el vínculo con la novedad copiada).
     */
    private void copiarFila(Long filaId, Long nominaId, Integer empresaId) {
        NominaEntity origen = nominaRepository.findByIdAndEmpresaId(filaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Fila de prenómina no encontrada"));

        NominaEntity fila = copia.copiarFila(origen);
        fila.setDocumentoId(nominaId);
        fila.setEsPrenomina(Boolean.FALSE);
        fila.setEstado(FILA_SIN_APROBAR);
        fila.setCreatedAt(LocalDateTime.now());

        Map<Long, NominaNovedadEntity> novedades = new HashMap<>();
        for (NominaNovedadEntity nov : origen.getNovedades()) {
            NominaNovedadEntity nueva = copia.copiarNovedad(nov);
            nueva.setNomina(fila);
            fila.getNovedades().add(nueva);
            novedades.put(nov.getId(), nueva);
        }
        NominaEntity guardada = nominaRepository.save(fila);

        List<NominaDetalleEntity> detalle = detalleRepository.findParaDesprendible(filaId);
        for (NominaDetalleEntity linea : detalle) {
            NominaDetalleEntity nueva = copia.copiarDetalle(linea);
            nueva.setNomina(guardada);
            nueva.setNovedad(linea.getNovedad() == null ? null : novedades.get(linea.getNovedad().getId()));
            nueva.setCreatedAt(LocalDateTime.now());
            detalleRepository.save(nueva);
        }
    }
}
