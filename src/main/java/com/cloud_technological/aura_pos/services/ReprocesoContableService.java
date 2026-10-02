package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.contabilidad.application.ContabilizarDocumentoUseCase;
import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.generador.GeneradorRegistry;
import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.DocumentoSinAsientoDto;
import com.cloud_technological.aura_pos.repositories.contabilidad.DocumentosSinAsientoQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Detecta los documentos que no quedaron en el mayor y los vuelve a
 * contabilizar.
 *
 * <p>El asiento se genera DESPUÉS del commit del documento: si falla (cuenta
 * sin configurar, mes cerrado, choque de consecutivo) el documento queda vivo
 * y el error solo iba al log, sin que nadie lo recogiera. Este servicio es la
 * red: la pantalla de revisión los lista y el contador los reprocesa cuando
 * corrige la causa.
 */
@Service
public class ReprocesoContableService {

    private final DocumentosSinAsientoQueryRepository queryRepo;
    private final GeneradorRegistry registry;
    private final ContabilizarDocumentoUseCase contabilizar;
    private final ContabilidadAutoService autoService;

    public ReprocesoContableService(DocumentosSinAsientoQueryRepository queryRepo, GeneradorRegistry registry,
            ContabilizarDocumentoUseCase contabilizar, ContabilidadAutoService autoService) {
        this.queryRepo = queryRepo;
        this.registry = registry;
        this.contabilizar = contabilizar;
        this.autoService = autoService;
    }

    public List<DocumentoSinAsientoDto> detectar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return queryRepo.listar(empresaId, desde, hasta);
    }

    /**
     * Genera el asiento de un documento que no lo tiene. Si el motor nuevo
     * tiene generador para el tipo, se usa ese (es el que corre en línea);
     * si no, el generador del motor anterior. Los dos son idempotentes: si el
     * documento ya tiene asiento, no se crea otro.
     *
     * @return id del asiento creado, o null si ya existía
     */
    public Long reprocesar(Integer empresaId, Integer usuarioId, String tipoOrigen, Long origenId) {
        if (tipoOrigen == null || origenId == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique el tipo y el id del documento");
        }
        String tipo = tipoOrigen.trim().toUpperCase();
        if (registry.soporta(tipo)) {
            return contabilizar.ejecutar(new ContextoContabilizacion(tipo, origenId, empresaId, usuarioId));
        }
        AsientoContableTableDto creado = switch (tipo) {
            case "COMPRA" -> autoService.reprocesarCompra(origenId, empresaId, usuarioId);
            case "GASTO" -> autoService.generarDesdeGasto(origenId, empresaId, usuarioId);
            case "DEVOLUCION" -> autoService.generarDesdeDevolucion(origenId, empresaId, usuarioId);
            case "MERMA" -> autoService.generarDesdeMerma(origenId, empresaId, usuarioId);
            default -> throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay reproceso automático para documentos de tipo " + tipo);
        };
        return creado != null ? creado.getId() : null;
    }
}
