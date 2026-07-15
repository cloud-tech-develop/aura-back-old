package com.cloud_technological.aura_pos.contabilidad.infrastructure.revision;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.application.exception.PeriodoCerradoException;
import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Bandeja de revisión del contador (E3): aprueba comprobantes BORRADOR
 * (individual o masivo) y expone pendientes/descuadrados. Solo transición
 * de estado — la generación vive en los generadores; CONTABILIZADO es
 * inmutable (solo reversa).
 */
@Service
@RequiredArgsConstructor
public class AsientoRevisionService {

    private final AsientoContableJPARepository asientoRepo;
    private final PeriodoContableJPARepository periodoRepo;

    public List<AsientoContableTableDto> pendientes(Integer empresaId) {
        return asientoRepo.findByEmpresaIdAndEstadoOrderByFechaDescIdDesc(empresaId, "BORRADOR")
                .stream().map(this::toDto).toList();
    }

    public List<AsientoContableTableDto> descuadrados(Integer empresaId) {
        return asientoRepo.findDescuadrados(empresaId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public AsientoContableTableDto contabilizar(Long asientoId, Integer empresaId) {
        AsientoContableEntity asiento = asientoRepo.findByIdAndEmpresaId(asientoId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Asiento #" + asientoId + " no encontrado"));
        if (!"BORRADOR".equals(asiento.getEstado())) {
            throw new IllegalStateException("El asiento " + asiento.getNumeroComprobante()
                    + " no está en borrador (estado " + asiento.getEstado() + ").");
        }
        validarPeriodoAbierto(asiento);
        asiento.setEstado("CONTABILIZADO");
        return toDto(asientoRepo.save(asiento));
    }

    /**
     * Aprueba todos los borradores del rango (y tipo de origen, si viene).
     * Devuelve cuántos quedaron contabilizados; los de período cerrado
     * revientan con mensaje accionable (nada queda a medias: transaccional).
     */
    @Transactional
    public int contabilizarMasivo(Integer empresaId, LocalDate desde, LocalDate hasta,
            String tipoOrigen) {
        List<AsientoContableEntity> borradores = asientoRepo
                .findByEmpresaIdAndEstadoAndFechaBetweenOrderByFechaAscIdAsc(
                        empresaId, "BORRADOR", desde, hasta);
        int contabilizados = 0;
        for (AsientoContableEntity asiento : borradores) {
            if (tipoOrigen != null && !tipoOrigen.isBlank()
                    && !tipoOrigen.equalsIgnoreCase(asiento.getTipoOrigen())) {
                continue;
            }
            validarPeriodoAbierto(asiento);
            asiento.setEstado("CONTABILIZADO");
            asientoRepo.save(asiento);
            contabilizados++;
        }
        return contabilizados;
    }

    private void validarPeriodoAbierto(AsientoContableEntity asiento) {
        boolean abierto = asiento.getPeriodoContableId() != null
                && periodoRepo.findById(asiento.getPeriodoContableId())
                        .map(p -> "ABIERTO".equals(p.getEstado()))
                        .orElse(false);
        if (!abierto) {
            throw new PeriodoCerradoException(
                    "El período del comprobante " + asiento.getNumeroComprobante()
                            + " está cerrado: no se puede contabilizar el borrador.");
        }
    }

    private AsientoContableTableDto toDto(AsientoContableEntity e) {
        AsientoContableTableDto dto = new AsientoContableTableDto();
        dto.setId(e.getId());
        dto.setNumeroComprobante(e.getNumeroComprobante());
        dto.setFecha(e.getFecha() != null ? e.getFecha().toString() : null);
        dto.setDescripcion(e.getDescripcion());
        dto.setTipoOrigen(e.getTipoOrigen());
        dto.setOrigenId(e.getOrigenId());
        dto.setTotalDebito(e.getTotalDebito());
        dto.setTotalCredito(e.getTotalCredito());
        dto.setEstado(e.getEstado());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}
