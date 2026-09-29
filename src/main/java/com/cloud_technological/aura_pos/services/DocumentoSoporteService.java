package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.documento_soporte.DocumentoSoporteDto;
import com.cloud_technological.aura_pos.dto.documento_soporte.EmitirDocumentoSoporteDto;
import com.cloud_technological.aura_pos.dto.documento_soporte.PreviaDocumentoSoporteDto;

/** Documento soporte electrónico de compras y gastos a no obligados a facturar. */
public interface DocumentoSoporteService {

    /** Lo que se enviaría, qué falta y los intentos anteriores. No llama a Factus. */
    PreviaDocumentoSoporteDto previa(Integer empresaId, String origenTipo, Long origenId);

    DocumentoSoporteDto emitir(Integer empresaId, Integer usuarioId, EmitirDocumentoSoporteDto req);

    List<DocumentoSoporteDto> listar(Integer empresaId, LocalDate desde, LocalDate hasta);

    /** PDF en Base64 desde Factus. */
    String pdf(Integer empresaId, Long id);

    /** Descarta un intento no aceptado. */
    DocumentoSoporteDto descartar(Integer empresaId, Long id);
}
