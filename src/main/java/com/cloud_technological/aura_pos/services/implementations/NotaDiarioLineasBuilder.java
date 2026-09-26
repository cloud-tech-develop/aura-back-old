package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioLineaDto;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository.CuentaMovimiento;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository.Dimension;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Reglas de las líneas de una nota contable, compartidas por la nota y por la
 * plantilla: si la plantilla validara distinto, generaría borradores que la
 * nota después rechaza.
 */
@Component
@RequiredArgsConstructor
public class NotaDiarioLineasBuilder {

    /** Clasificaciones admitidas. Solo informativas: no cambian cómo se contabiliza. */
    public static final Set<String> CLASIFICACIONES = Set.of(
            "AJUSTE", "RECLASIFICACION", "PROVISION", "CAUSACION", "DEPRECIACION", "CORRECCION", "OTRO");

    private final NotaDiarioQueryRepository queryRepo;

    /** null o vacío = sin clasificar; un valor desconocido se rechaza. */
    public String normalizarClasificacion(String c) {
        if (c == null || c.isBlank()) return null;
        String v = c.trim().toUpperCase();
        if (!CLASIFICACIONES.contains(v)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Clasificación no válida: " + c);
        }
        return v;
    }

    /**
     * Convierte y valida: valores no negativos, un solo lado por línea,
     * cuentas auxiliares activas de la empresa y dimensiones de la empresa.
     * No exige cuadre: un borrador puede estar a medias.
     */
    public List<AsientoDetalleEntity> construir(Integer empresaId, List<SaveNotaDiarioLineaDto> dtos) {
        List<AsientoDetalleEntity> lineas = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            SaveNotaDiarioLineaDto l = dtos.get(i);
            BigDecimal debito = valor(l.getDebito());
            BigDecimal credito = valor(l.getCredito());
            if (debito.signum() < 0 || credito.signum() < 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + (i + 1) + ": los valores no pueden ser negativos.");
            }
            if (debito.signum() > 0 && credito.signum() > 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + (i + 1) + ": tiene débito y crédito a la vez. Use una línea para cada lado.");
            }
            lineas.add(AsientoDetalleEntity.builder()
                    .cuentaId(l.getCuentaId())
                    .descripcion(blankToNull(l.getDescripcion()))
                    .debito(debito)
                    .credito(credito)
                    .terceroId(l.getTerceroId())
                    .centroCostoId(l.getCentroCostoId())
                    .proyectoId(l.getProyectoId())
                    .frenteId(l.getFrenteId())
                    .build());
        }
        validarCuentas(empresaId, lineas);
        validarDimensiones(empresaId, lineas);
        return lineas;
    }

    /** Solo cuentas de la empresa, activas y de movimiento (auxiliares). */
    public void validarCuentas(Integer empresaId, List<AsientoDetalleEntity> lineas) {
        Set<Long> ids = lineas.stream().map(AsientoDetalleEntity::getCuentaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CuentaMovimiento> cuentas = queryRepo.cuentas(empresaId, ids);
        for (int i = 0; i < lineas.size(); i++) {
            Long cuentaId = lineas.get(i).getCuentaId();
            CuentaMovimiento c = cuentaId != null ? cuentas.get(cuentaId) : null;
            if (c == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + (i + 1) + ": la cuenta contable no existe en el plan de la empresa.");
            }
            if (!c.activa()) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + (i + 1) + ": la cuenta " + c.codigo() + " - " + c.nombre() + " está inactiva.");
            }
            if (!c.auxiliar()) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + (i + 1) + ": la cuenta " + c.codigo() + " - " + c.nombre()
                                + " no es de movimiento. Elija una cuenta auxiliar (último nivel).");
            }
        }
    }

    /**
     * Tercero, centro de costo, proyecto y frente deben ser de la empresa. Sin
     * esto un id ajeno quedaría en el auxiliar por tercero de otra empresa.
     */
    private void validarDimensiones(Integer empresaId, List<AsientoDetalleEntity> lineas) {
        exigirDeEmpresa(Dimension.TERCERO, empresaId,
                lineas.stream().map(AsientoDetalleEntity::getTerceroId), "Uno de los terceros");
        exigirDeEmpresa(Dimension.CENTRO_COSTO, empresaId,
                lineas.stream().map(AsientoDetalleEntity::getCentroCostoId), "Uno de los centros de costo");
        exigirDeEmpresa(Dimension.PROYECTO, empresaId,
                lineas.stream().map(AsientoDetalleEntity::getProyectoId), "Uno de los proyectos");
        exigirDeEmpresa(Dimension.FRENTE, empresaId,
                lineas.stream().map(AsientoDetalleEntity::getFrenteId), "Uno de los frentes");

        Set<Long> frentes = lineas.stream().map(AsientoDetalleEntity::getFrenteId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Long> proyectoDe = queryRepo.proyectoDeFrentes(frentes);
        for (int i = 0; i < lineas.size(); i++) {
            AsientoDetalleEntity l = lineas.get(i);
            if (l.getFrenteId() == null) continue;
            Long proyectoDelFrente = proyectoDe.get(l.getFrenteId());
            if (l.getProyectoId() == null) {
                // El frente ya dice de qué obra es: se completa en vez de rechazar.
                l.setProyectoId(proyectoDelFrente);
            } else if (!l.getProyectoId().equals(proyectoDelFrente)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + (i + 1) + ": el frente no pertenece al proyecto elegido.");
            }
        }
    }

    private void exigirDeEmpresa(Dimension d, Integer empresaId, Stream<Long> ids, String etiqueta) {
        Set<Long> distintos = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        if (distintos.isEmpty()) return;
        if (queryRepo.contarDeEmpresa(d, empresaId, distintos) != distintos.size()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    etiqueta + " de la nota no existe, fue eliminado o no pertenece a la empresa.");
        }
    }

    private static BigDecimal valor(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
