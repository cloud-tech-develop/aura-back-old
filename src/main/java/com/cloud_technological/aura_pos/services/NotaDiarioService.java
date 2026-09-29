package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;

import org.springframework.data.domain.PageImpl;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.ImportarLineasResultadoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioFiltroDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioSoporteDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Notas contables (comprobante de diario CD) que elabora el contador.
 *
 * <p>Ciclo: BORRADOR (sin consecutivo, editable, se puede eliminar) →
 * CONTABILIZADO (recibe su CD-######, inmutable) → ANULADO (con motivo, solo
 * si el período sigue abierto). Una contabilizada también se puede REVERSAR:
 * se registra la nota inversa en un mes abierto y las dos quedan enlazadas.
 */
public interface NotaDiarioService {

    PageImpl<NotaDiarioTableDto> listar(PageableDto<NotaDiarioFiltroDto> pageable, Integer empresaId);

    NotaDiarioDto obtener(Long id, Integer empresaId);

    NotaDiarioDto crear(Integer empresaId, Integer usuarioId, SaveNotaDiarioDto dto);

    /** Solo borradores. */
    NotaDiarioDto actualizar(Long id, Integer empresaId, Integer usuarioId, SaveNotaDiarioDto dto);

    NotaDiarioDto contabilizar(Long id, Integer empresaId, Integer usuarioId);

    /** Solo borradores: no tienen consecutivo, así que borrarlos no deja hueco en la serie. */
    void eliminar(Long id, Integer empresaId);

    /** Solo contabilizadas de un período abierto. */
    NotaDiarioDto anular(Long id, Integer empresaId, Integer usuarioId, String motivo);

    /**
     * Registra y contabiliza la nota inversa con la fecha dada. Devuelve la
     * reversión (la nueva nota).
     */
    NotaDiarioDto reversar(Long id, Integer empresaId, Integer usuarioId, LocalDate fecha, String concepto);

    NotaDiarioSoporteDto subirSoporte(Long id, Integer empresaId, Integer usuarioId, MultipartFile archivo);

    /** Solo en borrador: el soporte de una nota contabilizada es evidencia. */
    void eliminarSoporte(Long id, Long soporteId, Integer empresaId);

    /** Interpreta líneas pegadas desde Excel. No guarda nada. */
    ImportarLineasResultadoDto importarLineas(Integer empresaId, String texto);
}
