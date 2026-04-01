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

        String prefix = "INGRESO".equals(tipo) ? "CI" : "CE";
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
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
