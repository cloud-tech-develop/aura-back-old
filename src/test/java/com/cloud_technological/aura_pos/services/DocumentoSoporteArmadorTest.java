package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador.Item;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador.Pago;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador.Proveedor;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador.Resultado;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador.Retenciones;
import com.fasterxml.jackson.databind.JsonNode;

/** Cuerpo de Factus v1 /v1/support-documents/validate. */
class DocumentoSoporteArmadorTest {

    private static Proveedor personaNaturalConCedula() {
        return new Proveedor("CC", "1.098.765.432", null, "Pedro Pérez", null, "Cra 5 # 10-20",
                "CO", 980L, "pedro@correo.com", "3001234567", "NO_RESPONSABLE_IVA");
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void digitoDeVerificacionSegunDian() {
        // NIT de la DIAN: 800197268-4
        assertEquals(4, DocumentoSoporteArmador.digitoVerificacion("800197268"));
        // Factus S.A.S. del ejemplo de la documentación: 901724254-1
        assertEquals(1, DocumentoSoporteArmador.digitoVerificacion("901724254"));
    }

    @Test
    void cedulaViajaComoNitConSuDigitoDeVerificacion() {
        Resultado r = DocumentoSoporteArmador.armar("DS-C1-1", null, "Fletes",
                personaNaturalConCedula(),
                List.of(new Item("FLETE", "Flete", bd("1"), bd("50000"), bd("20"), BigDecimal.ZERO)),
                new Retenciones(bd("3.5"), null, bd("1400")),
                new Pago(false, "EFECTIVO"));

        assertTrue(r.puedeEmitirse());
        JsonNode prov = r.payload().path("provider");
        assertEquals(6, prov.path("identification_document_id").asInt());
        assertEquals("1098765432", prov.path("identification").asText());
        assertEquals(DocumentoSoporteArmador.digitoVerificacion("1098765432"), prov.path("dv").asInt());
        assertEquals(1, prov.path("is_resident").asInt());
        assertEquals(980, prov.path("municipality_id").asInt());

        // Igual que el ejemplo de Factus: 50.000 − 20 % = 40.000, ReteRenta 3,5 %
        JsonNode item = r.payload().path("items").get(0);
        assertEquals(1, item.path("quantity").asInt());
        assertEquals(70, item.path("unit_measure_id").asInt());
        assertEquals(1, item.path("standard_code_id").asInt());
        assertEquals("06", item.path("withholding_taxes").get(0).path("code").asText());
        assertEquals("3.50", item.path("withholding_taxes").get(0).path("withholding_tax_rate").asText());
        assertTrue(item.path("taxes").isMissingNode());
        assertEquals(0, bd("40000").compareTo(r.total()));
        assertEquals(0, bd("38600").compareTo(r.netoAPagar()));
        assertEquals("10", r.payload().path("payment_method_code").asText());
    }

    @Test
    void cantidadDecimalSeEnviaComoUnaLineaPorElTotal() {
        Resultado r = DocumentoSoporteArmador.armar("DS-C1-2", "148", null, personaNaturalConCedula(),
                List.of(new Item("CAFE", "Café", bd("2.5"), bd("10000"), BigDecimal.ZERO, BigDecimal.ZERO)),
                null, new Pago(true, null));

        JsonNode item = r.payload().path("items").get(0);
        assertEquals(1, item.path("quantity").asInt());
        assertEquals(0, bd("25000").compareTo(item.path("price").decimalValue()));
        assertEquals("Café (2.5)", item.path("name").asText());
        assertEquals(148, r.payload().path("numbering_range_id").asInt());
        // A crédito: medio de pago no definido
        assertEquals("1", r.payload().path("payment_method_code").asText());
    }

    @Test
    void ivaRegistradoSeSumaAlPrecioYSeAdvierte() {
        Resultado r = DocumentoSoporteArmador.armar("DS-C1-3", null, null, personaNaturalConCedula(),
                List.of(new Item("A", "Insumo", bd("2"), bd("1000"), BigDecimal.ZERO, bd("19"))),
                null, new Pago(false, "TRANSFERENCIA"));

        assertEquals(0, bd("1190").compareTo(r.payload().path("items").get(0).path("price").decimalValue()));
        assertEquals(0, bd("2380").compareTo(r.total()));
        assertEquals(0, bd("380").compareTo(r.iva()));
        assertEquals(1, r.advertencias().size());
        assertEquals("47", r.payload().path("payment_method_code").asText());
    }

    @Test
    void faltantesSeListanTodosDeUnaVez() {
        Proveedor incompleto = new Proveedor("TI", "", null, null, null, null, "CO", null,
                null, null, null);

        Resultado r = DocumentoSoporteArmador.armar("DS-G1-1", null, null, incompleto,
                List.of(new Item("G", "Gasto", bd("1"), BigDecimal.ZERO, null, null)),
                null, new Pago(false, "EFECTIVO"));

        assertFalse(r.puedeEmitirse());
        // tipo no admitido, número, nombre, dirección, correo, municipio y precio del ítem
        assertEquals(7, r.faltantes().size());
    }

    @Test
    void proveedorResponsableDeIvaAdvierteQueDebeFacturar() {
        Proveedor responsable = new Proveedor("NIT", "900123456", "7", "Ferretería SAS", null,
                "Calle 1", "CO", 169L, "f@f.com", null, "RESPONSABLE_IVA");

        Resultado r = DocumentoSoporteArmador.armar("DS-C2-1", null, null, responsable,
                List.of(new Item("A", "Tornillos", bd("1"), bd("100"), null, null)),
                null, new Pago(false, "EFECTIVO"));

        assertTrue(r.puedeEmitirse());
        assertEquals(1, r.advertencias().size());
        // El NIT trae su DV registrado: se respeta
        assertEquals(7, r.payload().path("provider").path("dv").asInt());
    }
}
