package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.DetalleLiquidacionDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.LiquidacionEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ProcesoDocumentoDto;
import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;
import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoLiquidacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoLiquidacionQueryRepository.FilaALiquidar;
import com.cloud_technological.aura_pos.services.NominaDocumentoLiquidacionService;
import com.cloud_technological.aura_pos.services.NominaTipoComprobanteService;
import com.cloud_technological.aura_pos.services.nomina.comprobante.ClaseComprobanteNomina;
import com.cloud_technological.aura_pos.services.nomina.job.LiquidacionDocumentoJob;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link NominaDocumentoLiquidacionService}. */
@Service
public class NominaDocumentoLiquidacionServiceImpl implements NominaDocumentoLiquidacionService {

    private final NominaDocumentoJPARepository documentoRepository;
    private final NominaDocumentoLiquidacionQueryRepository queryRepository;
    private final NominaTipoComprobanteService tiposComprobante;
    private final ProcesoNominaService procesoService;
    private final LiquidacionDocumentoJob job;

    public NominaDocumentoLiquidacionServiceImpl(NominaDocumentoJPARepository documentoRepository,
            NominaDocumentoLiquidacionQueryRepository queryRepository,
            NominaTipoComprobanteService tiposComprobante,
            ProcesoNominaService procesoService,
            LiquidacionDocumentoJob job) {
        this.documentoRepository = documentoRepository;
        this.queryRepository = queryRepository;
        this.tiposComprobante = tiposComprobante;
        this.procesoService = procesoService;
        this.job = job;
    }

    @Override
    public ProcesoNominaEntity liquidar(Long documentoId, List<Long> nominaIds, boolean soloPendientes,
            Integer empresaId, Long usuarioId) {
        NominaDocumentoEntity d = buscar(documentoId, empresaId);
        if (!"PRENOMINA".equals(d.getClaseDocumento()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nómina no se modifica: los cambios se hacen en su prenómina.");
        if (!NominaDocumentoServiceImpl.PRELIMINAR.equals(d.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El documento está " + d.getEstado().toLowerCase() + " y no se liquida.");

        TipoComprobanteTableDto tipo = tiposComprobante.obtener(d.getTipoComprobanteId(), empresaId);
        ClaseComprobanteNomina clase = ClaseComprobanteNomina.valueOf(tipo.getClase());
        if (clase == ClaseComprobanteNomina.OTROS_PAGOS)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    tipo.getNombre() + " aún no se liquida por documento.");

        List<FilaALiquidar> filas = queryRepository.filasALiquidar(documentoId,
                nominaIds == null ? List.of() : nominaIds, soloPendientes);
        if (filas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, soloPendientes
                    ? "Todos los empleados ya están liquidados; use Reliquidar en la fila que necesite."
                    : "Agregue empleados antes de liquidar.");

        // crear() responde 409 si ya hay una liquidación corriendo para este documento.
        ProcesoNominaEntity proceso = procesoService.crear(empresaId,
                ProcesoNominaEntity.Tipo.LIQUIDACION_DOCUMENTO, documentoId, usuarioId, filas.size());
        job.liquidar(proceso.getId(), documentoId, filas, d.getFechaLiquidacion(), empresaId);
        return proceso;
    }

    @Override
    public List<LiquidacionEmpleadoDto> liquidacion(Long documentoId, Integer empresaId) {
        buscar(documentoId, empresaId);
        return queryRepository.liquidacion(documentoId);
    }

    @Override
    public List<DetalleLiquidacionDto> detalle(Long documentoId, Long nominaId, Integer empresaId) {
        buscar(documentoId, empresaId);
        return queryRepository.detalle(documentoId, nominaId);
    }

    @Override
    public List<ProcesoDocumentoDto> traza(Long documentoId, Integer empresaId) {
        buscar(documentoId, empresaId);
        return queryRepository.procesos(empresaId, documentoId);
    }

    /** El documento de la empresa; 404 si no existe. */
    private NominaDocumentoEntity buscar(Long id, Integer empresaId) {
        return documentoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));
    }
}
