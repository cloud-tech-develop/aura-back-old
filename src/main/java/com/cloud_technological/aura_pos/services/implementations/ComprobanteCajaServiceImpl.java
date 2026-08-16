package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.comprobante.ComprobanteCajaDto;
import com.cloud_technological.aura_pos.entity.ComprobanteCajaEntity;
import com.cloud_technological.aura_pos.repositories.comprobante_caja.ComprobanteCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.comprobante_caja.ComprobanteCajaQueryRepository;
import com.cloud_technological.aura_pos.services.ComprobanteCajaService;

@Service
public class ComprobanteCajaServiceImpl implements ComprobanteCajaService {

    @Autowired private ComprobanteCajaJPARepository repo;
    @Autowired private ComprobanteCajaQueryRepository queryRepo;

    @Override
    public List<ComprobanteCajaDto> listar(Integer empresaId, String tipo,
            String desde, String hasta, int page, int rows) {
        return queryRepo.paginar(empresaId, tipo, desde, hasta, page, rows);
    }

    @Override
    public ComprobanteCajaDto obtenerPorId(Long id, Integer empresaId) {
        ComprobanteCajaEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));
        return toDto(e);
    }

    @Override
    public ComprobanteCajaEntity generar(Integer empresaId, Integer usuarioId,
            String tipo, String concepto, BigDecimal monto,
            String metodoPago, String entregadoA,
            String origen, Long origenId, Long turnoCajaId) {

        // Numeración unificada con los comprobantes contables: RC ingreso, CE egreso.
        String prefix = "INGRESO".equals(tipo) ? "RC" : "CE";
        String numero = queryRepo.siguienteNumeroComprobante(empresaId, prefix);

        ComprobanteCajaEntity comprobante = ComprobanteCajaEntity.builder()
                .empresaId(empresaId)
                .numeroComprobante(numero)
                .tipo(tipo)
                .concepto(concepto)
                .monto(monto)
                .metodoPago(metodoPago)
                .entregadoA(entregadoA)
                .origen(origen)
                .origenId(origenId)
                .turnoCajaId(turnoCajaId)
                .usuarioId(usuarioId)
                .build();

        return repo.save(comprobante);
    }

    @Override
    public ComprobanteCajaEntity sincronizarDeDocumento(Integer empresaId, Integer usuarioId,
            String tipo, String concepto, BigDecimal monto,
            String metodoPago, String entregadoA,
            String origen, Long origenId, Long turnoCajaId) {

        if (monto == null || monto.signum() <= 0) {
            return null;
        }
        ComprobanteCajaEntity existente = repo
                .findFirstByEmpresaIdAndOrigenAndOrigenId(empresaId, origen, origenId)
                .orElse(null);

        if (existente == null) {
            return generar(empresaId, usuarioId, tipo, concepto, monto,
                    metodoPago, entregadoA, origen, origenId, turnoCajaId);
        }
        // Si estaba anulado y el documento vuelve a mover plata (se corrigió de
        // crédito a contado), revive con su mismo número.
        existente.setAnulado(false);
        existente.setMotivoAnulacion(null);
        existente.setAnuladoAt(null);
        // Se conserva numero_comprobante: el consecutivo ya circuló y volver a
        // numerarlo dejaría un hueco en la serie.
        existente.setConcepto(concepto);
        existente.setMonto(monto);
        existente.setMetodoPago(metodoPago);
        existente.setEntregadoA(entregadoA);
        existente.setTurnoCajaId(turnoCajaId);
        return repo.save(existente);
    }

    @Override
    public void anularDeDocumento(Integer empresaId, String origen, Long origenId, String motivo) {
        repo.findFirstByEmpresaIdAndOrigenAndOrigenId(empresaId, origen, origenId)
                .filter(c -> !Boolean.TRUE.equals(c.getAnulado()))
                .ifPresent(c -> {
                    c.setAnulado(true);
                    c.setMotivoAnulacion(motivo);
                    c.setAnuladoAt(java.time.LocalDateTime.now());
                    repo.save(c);
                });
    }

    private ComprobanteCajaDto toDto(ComprobanteCajaEntity e) {
        ComprobanteCajaDto dto = new ComprobanteCajaDto();
        dto.setId(e.getId());
        dto.setNumeroComprobante(e.getNumeroComprobante());
        dto.setTipo(e.getTipo());
        dto.setConcepto(e.getConcepto());
        dto.setMonto(e.getMonto());
        dto.setMetodoPago(e.getMetodoPago());
        dto.setEntregadoA(e.getEntregadoA());
        dto.setOrigen(e.getOrigen());
        dto.setOrigenId(e.getOrigenId());
        dto.setTurnoCajaId(e.getTurnoCajaId());
        dto.setUsuarioId(e.getUsuarioId());
        dto.setAnulado(e.getAnulado());
        dto.setMotivoAnulacion(e.getMotivoAnulacion());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
