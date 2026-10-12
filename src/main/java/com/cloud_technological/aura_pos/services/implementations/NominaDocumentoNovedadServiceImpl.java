package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.documento.AgregarNovedadDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.NovedadDocumentoDto;
import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoLiquidacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoNovedadQueryRepository;
import com.cloud_technological.aura_pos.services.NominaDocumentoNovedadService;
import com.cloud_technological.aura_pos.services.NominaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link NominaDocumentoNovedadService}. */
@Service
public class NominaDocumentoNovedadServiceImpl implements NominaDocumentoNovedadService {

    private final NominaDocumentoJPARepository documentoRepository;
    private final NominaDocumentoNovedadQueryRepository queryRepository;
    private final NominaDocumentoLiquidacionQueryRepository liquidacionQuery;
    private final NominaService nominaService;
    private final NominaJPARepository nominaRepository;

    public NominaDocumentoNovedadServiceImpl(NominaDocumentoJPARepository documentoRepository,
            NominaDocumentoNovedadQueryRepository queryRepository,
            NominaDocumentoLiquidacionQueryRepository liquidacionQuery,
            NominaService nominaService,
            NominaJPARepository nominaRepository) {
        this.documentoRepository = documentoRepository;
        this.queryRepository = queryRepository;
        this.liquidacionQuery = liquidacionQuery;
        this.nominaService = nominaService;
        this.nominaRepository = nominaRepository;
    }

    @Override
    public List<NovedadDocumentoDto> listar(Long documentoId, Integer empresaId) {
        buscar(documentoId, empresaId);
        return queryRepository.novedades(documentoId);
    }

    @Override
    @Transactional
    public List<NovedadDocumentoDto> agregar(Long documentoId, AgregarNovedadDocumentoDto dto, Integer empresaId) {
        buscarPrenominaPreliminar(documentoId, empresaId);
        if (!queryRepository.filaDelDocumento(documentoId, dto.getNominaId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El empleado no está en esta prenómina.");
        // Mismas reglas que la liquidación por períodos (días, incapacidad, vacaciones, topes).
        nominaService.agregarNovedad(dto.getNominaId(), dto, empresaId);
        quedaPendiente(documentoId, dto.getNominaId());
        return queryRepository.novedades(documentoId);
    }

    @Override
    @Transactional
    public List<NovedadDocumentoDto> eliminar(Long documentoId, Long novedadId, Integer empresaId) {
        buscarPrenominaPreliminar(documentoId, empresaId);
        Long nominaId = queryRepository.filaDeLaNovedad(documentoId, novedadId);
        if (nominaId == null)
            throw new GlobalException(HttpStatus.NOT_FOUND, "La novedad no es de esta prenómina.");
        nominaService.eliminarNovedad(nominaId, novedadId, empresaId);
        quedaPendiente(documentoId, nominaId);
        return queryRepository.novedades(documentoId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /**
     * El empleado vuelve a "sin liquidar" (su liquidación ya no refleja las
     * novedades) y lo suyo deja de sumar en el encabezado hasta reliquidarlo.
     */
    private void quedaPendiente(Long documentoId, Long nominaId) {
        // flush: la fila que guardó NominaService debe estar escrita antes del SQL; si
        // no, el commit la reescribe completa y deshace "sin liquidar".
        nominaRepository.flush();
        queryRepository.marcarPendiente(nominaId);
        liquidacionQuery.recalcularTotales(documentoId);
    }

    private NominaDocumentoEntity buscar(Long id, Integer empresaId) {
        return documentoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));
    }

    /** Solo la prenómina preliminar admite cambios en las novedades. */
    private void buscarPrenominaPreliminar(Long id, Integer empresaId) {
        NominaDocumentoEntity d = buscar(id, empresaId);
        if (!"PRENOMINA".equals(d.getClaseDocumento()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nómina no se modifica: las novedades se registran en su prenómina.");
        if (!"PRELIMINAR".equals(d.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La prenómina está " + d.getEstado().toLowerCase() + ": sus novedades no se modifican.");
    }
}
