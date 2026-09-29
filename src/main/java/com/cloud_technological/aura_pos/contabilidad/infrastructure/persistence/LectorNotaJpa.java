package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorNota;
import com.cloud_technological.aura_pos.entity.NotaElectronicaEntity;
import com.cloud_technological.aura_pos.repositories.ventas.NotaElectronicaJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Proyecta la nota electrónica al snapshot de solo lectura que consumen
 * {@code NotaCreditoGenerador} / {@code NotaDebitoGenerador}. La base y el IVA
 * se calcularon al persistir la nota (a partir de los items enviados a Factus).
 */
@Component
@RequiredArgsConstructor
public class LectorNotaJpa implements LectorNota {

    private final NotaElectronicaJPARepository notaRepo;

    @Override
    public NotaContable cargar(Long notaId, Integer empresaId) {
        NotaElectronicaEntity n = notaRepo.findByIdAndEmpresaId(notaId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Nota electrónica #" + notaId + " no encontrada para contabilizar"));

        LocalDate fecha = n.getCreatedAt() != null ? n.getCreatedAt().toLocalDate() : LocalDate.now();
        String documento = n.getNumero() != null && !n.getNumero().isBlank()
                ? " — " + n.getNumero()
                : "";
        BigDecimal base = n.getBaseGravable() != null ? n.getBaseGravable() : BigDecimal.ZERO;
        BigDecimal iva = n.getIva() != null ? n.getIva() : BigDecimal.ZERO;
        BigDecimal total = n.getTotal() != null ? n.getTotal() : base.add(iva);

        return new NotaContable(fecha, n.getTipo(), documento, base, iva, total, null);
    }
}
