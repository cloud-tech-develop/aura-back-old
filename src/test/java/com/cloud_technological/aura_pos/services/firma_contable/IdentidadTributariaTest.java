package com.cloud_technological.aura_pos.services.firma_contable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.utils.GlobalException;

class IdentidadTributariaTest {

    @Test
    void juridicaExigeRazonSocialYCalculaElDv() {
        IdentidadTributaria i = IdentidadTributaria.resolver("JURIDICA", null, "Comercial XYZ SAS", null, null,
                null, null, "900123456");
        assertEquals("NIT", i.tipoDocumento());
        assertEquals("Comercial XYZ SAS", i.razonSocial());
        assertEquals("8", i.dv());
        assertThrows(GlobalException.class,
                () -> IdentidadTributaria.resolver("JURIDICA", "NIT", " ", null, null, null, null, "900123456"));
        assertThrows(GlobalException.class,
                () -> IdentidadTributaria.resolver("JURIDICA", "CC", "X", null, null, null, null, "900123456"));
    }

    @Test
    void naturalArmaElNombreYSoloLlevaDvConNit() {
        IdentidadTributaria conCedula = IdentidadTributaria.resolver("NATURAL", "CC", null, "Ana María",
                "Pérez Gómez", null, null, "1020304050");
        assertEquals("Ana María Pérez Gómez", conCedula.razonSocial());
        assertNull(conCedula.dv());

        IdentidadTributaria conNit = IdentidadTributaria.resolver("NATURAL", "NIT", null, "Ana", "Pérez", null, "7",
                "1020304050");
        assertEquals("7", conNit.dv());
        assertThrows(GlobalException.class,
                () -> IdentidadTributaria.resolver("NATURAL", "CC", "Ana Pérez", null, null, null, null, "1"));
    }

    @Test
    void pasarDeJuridicaANaturalLimpiaLaRazonSocialDelTercero() {
        TerceroEntity tercero = new TerceroEntity();
        tercero.setRazonSocial("Vieja SAS");
        EmpresaEntity empresa = EmpresaEntity.builder().nit("1020304050").build();

        IdentidadTributaria i = IdentidadTributaria.resolver("NATURAL", "CC", null, "Ana María", "Pérez Gómez",
                "Tienda Ana", null, "1020304050");
        i.aplicarA(empresa);
        i.aplicarA(tercero);

        assertEquals("NATURAL", empresa.getTipoPersona());
        assertEquals("Ana María Pérez Gómez", empresa.getRazonSocial());
        assertNull(tercero.getRazonSocial());
        assertEquals("Ana", tercero.getNombre1());
        assertEquals("María", tercero.getNombre2());
        assertEquals("Pérez", tercero.getApellido1());
        assertEquals("Gómez", tercero.getApellido2());
        assertEquals("CC", tercero.getTipoDocumento());
    }
}
