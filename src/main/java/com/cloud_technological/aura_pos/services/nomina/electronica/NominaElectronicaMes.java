package com.cloud_technological.aura_pos.services.nomina.electronica;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaMesQueryRepository.NominaMes;

/**
 * El mes de un empleado para la nómina electrónica: sus nóminas confirmadas (en
 * orden de período) y si ya cubren todo el mes.
 *
 * @param mes       mes del documento
 * @param contrato  contrato del empleado
 * @param nominas   nóminas confirmadas del mes, en orden de período
 * @param resumen   las mismas, con número y clase de documento
 * @param cobertura si el mes está completo para enviarlo
 */
public record NominaElectronicaMes(YearMonth mes, ContratoLaboralEntity contrato, List<NominaEntity> nominas,
                                   List<NominaMes> resumen, CoberturaMes cobertura) {

    /** La última nómina del mes: da el pago y el salario del documento. */
    public NominaEntity ultima() {
        return nominas.get(nominas.size() - 1);
    }

    /** Total devengado del mes (para comparar con lo que Factus dice tener). */
    public BigDecimal totalDevengado() {
        BigDecimal total = BigDecimal.ZERO;
        for (NominaEntity n : nominas)
            if (n.getTotalDevengado() != null) total = total.add(n.getTotalDevengado());
        return total;
    }
}
