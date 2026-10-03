package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.documento.DocumentoRelacionDtos.Relacionados;
import com.cloud_technological.aura_pos.entity.DocumentoRelacionEntity;
import com.cloud_technological.aura_pos.repositories.documento.DocumentoRelacionJPARepository;
import com.cloud_technological.aura_pos.repositories.documento.DocumentoRelacionQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Cadena documental (fase D0): registra y consulta de dónde viene y a dónde fue
 * cada documento, con la cantidad/valor aplicado por línea.
 *
 * <p>{@link #registrar} se llama <b>dentro de la misma transacción</b> del
 * documento destino (no abre transacción propia), para que si el documento no
 * se guarda, la relación tampoco quede. {@link #anularPorDestino} se llama al
 * anular el destino, y libera el pendiente del origen.
 */
@Service
@RequiredArgsConstructor
public class DocumentoRelacionService {

    private final DocumentoRelacionJPARepository repo;
    private final DocumentoRelacionQueryRepository queryRepo;

    /**
     * Registra que una línea/documento origen se aplicó a un destino.
     *
     * <p>Sin {@code @Transactional} propio: participa en la transacción activa
     * del documento destino que la invoca.
     */
    public DocumentoRelacionEntity registrar(Integer empresaId, Integer createdBy,
            String origenTipo, Long origenId, Long origenLineaId,
            String destinoTipo, Long destinoId, Long destinoLineaId,
            BigDecimal cantidad, BigDecimal valor) {
        if (empresaId == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La relación no tiene empresa");
        }
        if (origenTipo == null || origenTipo.isBlank() || origenId == null
                || destinoTipo == null || destinoTipo.isBlank() || destinoId == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La relación necesita origen y destino");
        }
        DocumentoRelacionEntity e = new DocumentoRelacionEntity();
        e.setEmpresaId(empresaId);
        e.setCreatedBy(createdBy);
        e.setOrigenTipo(origenTipo.trim().toUpperCase());
        e.setOrigenId(origenId);
        e.setOrigenLineaId(origenLineaId);
        e.setDestinoTipo(destinoTipo.trim().toUpperCase());
        e.setDestinoId(destinoId);
        e.setDestinoLineaId(destinoLineaId);
        e.setCantidad(cantidad);
        e.setValor(valor);
        e.setEstado(DocumentoRelacionEntity.ESTADO_VIGENTE);
        return repo.save(e);
    }

    /** Cantidad ya aplicada (VIGENTE) de una línea origen. */
    public BigDecimal aplicado(String origenTipo, Long origenLineaId) {
        if (origenTipo == null || origenLineaId == null) return BigDecimal.ZERO;
        return queryRepo.aplicadoCantidad(origenTipo.trim().toUpperCase(), origenLineaId);
    }

    /**
     * Pendiente de una línea origen = cantidad original − Σ aplicado vigente.
     * Nunca devuelve negativo.
     */
    public BigDecimal pendiente(String origenTipo, Long origenLineaId, BigDecimal cantidadOrigen) {
        BigDecimal base = cantidadOrigen != null ? cantidadOrigen : BigDecimal.ZERO;
        BigDecimal p = base.subtract(aplicado(origenTipo, origenLineaId));
        return p.signum() < 0 ? BigDecimal.ZERO : p;
    }

    /** Pendiente de varias líneas origen en una sola consulta (sin N+1). */
    public Map<Long, BigDecimal> aplicadoPorLineas(String origenTipo, List<Long> origenLineaIds) {
        if (origenTipo == null) return Map.of();
        return queryRepo.aplicadoCantidadPorLineas(origenTipo.trim().toUpperCase(), origenLineaIds);
    }

    /**
     * Al anular el documento destino, pasa sus relaciones VIGENTE a ANULADA para
     * liberar el pendiente del origen. Participa en la transacción de la anulación.
     *
     * @return número de relaciones anuladas.
     */
    public int anularPorDestino(String destinoTipo, Long destinoId) {
        if (destinoTipo == null || destinoId == null) return 0;
        List<Long> ids = queryRepo.idsVigentesPorDestino(destinoTipo.trim().toUpperCase(), destinoId);
        int n = 0;
        for (Long id : ids) {
            DocumentoRelacionEntity e = repo.findById(id).orElse(null);
            if (e == null || !DocumentoRelacionEntity.ESTADO_VIGENTE.equals(e.getEstado())) continue;
            e.setEstado(DocumentoRelacionEntity.ESTADO_ANULADA);
            repo.save(e);
            n++;
        }
        return n;
    }

    /** Relaciones hacia atrás (origen) y hacia adelante (destino) de un documento. */
    public Relacionados relacionados(Integer empresaId, String tipo, Long id) {
        if (tipo == null || tipo.isBlank() || id == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Documento no válido");
        }
        String t = tipo.trim().toUpperCase();
        Relacionados r = new Relacionados();
        r.getOrigenes().addAll(queryRepo.haciaAtras(empresaId, t, id));
        r.getDestinos().addAll(queryRepo.haciaAdelante(empresaId, t, id));
        return r;
    }

    /**
     * Variante transaccional de {@link #anularPorDestino} para usarse desde un
     * controlador o un flujo que no tenga ya una transacción abierta.
     */
    @Transactional
    public int anularPorDestinoTx(String destinoTipo, Long destinoId) {
        return anularPorDestino(destinoTipo, destinoId);
    }
}
