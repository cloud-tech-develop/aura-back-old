package com.cloud_technological.aura_pos.services.nomina.contabilidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.services.nomina.contabilidad.ReglaContableNomina.Regla;
import com.cloud_technological.aura_pos.services.nomina.contabilidad.ReglaContableNomina.TerceroCredito;

class ReglaContableNominaTest {

    @Test
    void unDevengadoVaAlGastoYSuCreditoEsElNeto() {
        Regla r = ReglaContableNomina.para("HORA_EXTRA_DIURNA", "DEVENGADO");
        assertEquals(ConceptoContable.NOMINA_SUELDOS, r.debito());
        assertNull(r.credito());
    }

    @Test
    void laSaludDelEmpleadoYLaDelEmpleadorVanALaEps() {
        assertEquals(TerceroCredito.EPS, ReglaContableNomina.para("DED_SALUD", "DEDUCCION").tercero());
        Regla aporte = ReglaContableNomina.para("APO_SALUD", "APORTE_EMPLEADOR");
        assertEquals(TerceroCredito.EPS, aporte.tercero());
        assertEquals(ConceptoContable.NOMINA_APORTE_SALUD, aporte.debito());
        assertEquals(ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR, aporte.credito());
    }

    @Test
    void pensionYFondoDeSolidaridadVanALaAfp() {
        assertEquals(TerceroCredito.AFP, ReglaContableNomina.para("DED_PENSION", "DEDUCCION").tercero());
        assertEquals(TerceroCredito.AFP, ReglaContableNomina.para("DED_FONDO_SOLIDARIDAD", "DEDUCCION").tercero());
        assertEquals(TerceroCredito.AFP, ReglaContableNomina.para("APO_PENSION", "APORTE_EMPLEADOR").tercero());
    }

    @Test
    void laRetefuenteVaALa2365DeLaDian() {
        Regla r = ReglaContableNomina.para("RETEFUENTE_P1", "DEDUCCION");
        assertEquals(ConceptoContable.RETEFUENTE_PRACTICADA, r.credito());
        assertEquals(TerceroCredito.DIAN, r.tercero());
        assertNull(r.debito());
    }

    @Test
    void arlYCajaVanASuEntidadEIcbfSenaSinTercero() {
        assertEquals(TerceroCredito.ARL, ReglaContableNomina.para("APO_ARL", "APORTE_EMPLEADOR").tercero());
        assertEquals(TerceroCredito.CCF, ReglaContableNomina.para("APO_CCF", "APORTE_EMPLEADOR").tercero());
        assertEquals(TerceroCredito.NINGUNO, ReglaContableNomina.para("APO_ICBF", "APORTE_EMPLEADOR").tercero());
    }

    @Test
    void lasProvisionesVanASuPasivoANombreDelEmpleado() {
        Regla r = ReglaContableNomina.para("PRO_CESANTIAS", "PROVISION");
        assertEquals(ConceptoContable.NOMINA_CESANTIAS, r.debito());
        assertEquals(ConceptoContable.CESANTIAS_POR_PAGAR, r.credito());
        assertEquals(TerceroCredito.EMPLEADO, r.tercero());
    }

    @Test
    void lasPrestacionesQueSePaganConsumenElPasivoProvisionado() {
        assertEquals(ConceptoContable.CESANTIAS_POR_PAGAR, ReglaContableNomina.para("CESANTIAS", "DEVENGADO").debito());
        assertEquals(ConceptoContable.PRIMA_POR_PAGAR, ReglaContableNomina.para("PRIMA", "DEVENGADO").debito());
        assertEquals(ConceptoContable.VACACIONES_POR_PAGAR, ReglaContableNomina.para("VACACIONES", "DEVENGADO").debito());
        assertEquals(ConceptoContable.NOMINA_INDEMNIZACION,
                ReglaContableNomina.para("INDEMNIZACION", "DEVENGADO").debito());
    }

    @Test
    void unaClaseDesconocidaFalla() {
        assertThrows(IllegalArgumentException.class, () -> ReglaContableNomina.para("X", "OTRA"));
    }
}
