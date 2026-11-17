package com.cloud_technological.aura_pos.services.nomina;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.repositories.nomina.ParametroAnualQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.ParametroAnualQueryRepository.ParametroAnual;

import lombok.RequiredArgsConstructor;

/**
 * Pone en la configuración de nómina el salario mínimo y el auxilio de
 * transporte del AÑO que se liquida (V239).
 *
 * <p>Devuelve una copia suelta (no administrada por JPA): así el motor, la PILA y
 * las prestaciones leen {@code config.getSmmlv()} como siempre, sin que el valor
 * del año se escriba de vuelta en {@code nomina_config}. Si el año no está
 * cargado, la configuración sale tal cual (sus valores son el respaldo).
 */
@Component
@RequiredArgsConstructor
public class ParametrosLegales {

    private final ParametroAnualQueryRepository repository;

    /**
     * Configuración con los valores de ley del año de {@code fecha}.
     *
     * @param config configuración de la empresa (puede ser null)
     * @param fecha  fecha que define el año: el fin del período que se liquida
     * @return una copia con los valores del año, o la misma configuración si no hay año cargado
     */
    public NominaConfigEntity delAgno(NominaConfigEntity config, LocalDate fecha) {
        if (config == null || fecha == null) return config;
        Optional<ParametroAnual> anual = repository.delAgno(fecha.getYear());
        if (anual.isEmpty()) return config;
        NominaConfigEntity copia = new NominaConfigEntity();
        BeanUtils.copyProperties(config, copia);
        copia.setSmmlv(anual.get().smmlv());
        copia.setAuxilioTransporte(anual.get().auxilioTransporte());
        return copia;
    }

    /** Valores de ley de un año, si está cargado. */
    public Optional<ParametroAnual> delAgno(int agno) {
        return repository.delAgno(agno);
    }
}
