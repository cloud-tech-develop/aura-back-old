package com.cloud_technological.aura_pos.sync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.sync.nube.GuardaEmpresa;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GuardaEmpresaTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return om.readTree(s);
    }

    @Test
    void aceptaFilasDeLaEmpresaYPlantillasGlobales() throws Exception {
        assertDoesNotThrow(() -> GuardaEmpresa.verificar("producto",
                json("[{\"id\":1,\"empresa_id\":4},{\"id\":2,\"empresa_id\":4}]"), 4));
        assertDoesNotThrow(() -> GuardaEmpresa.verificar("perfil",
                json("[{\"id\":1,\"empresa_id\":null},{\"id\":2,\"empresa_id\":4}]"), 4));
        // Tablas sin empresa_id (municipios, presentaciones): las filtra su SQL.
        assertDoesNotThrow(() -> GuardaEmpresa.verificar("municipios", json("[{\"id\":1,\"nombre\":\"Cali\"}]"), 4));
    }

    @Test
    void rechazaUnaFilaDeOtraEmpresa() throws Exception {
        JsonNode filas = json("[{\"id\":1,\"empresa_id\":4},{\"id\":2,\"empresa_id\":7}]");
        assertThrows(GuardaEmpresa.DatosDeOtraEmpresa.class, () -> GuardaEmpresa.verificar("tercero", filas, 4));
    }

    @Test
    void enLaTablaEmpresaSoloLaPropia() throws Exception {
        assertDoesNotThrow(() -> GuardaEmpresa.verificar("empresa", json("[{\"id\":4}]"), 4));
        assertThrows(GuardaEmpresa.DatosDeOtraEmpresa.class,
                () -> GuardaEmpresa.verificar("empresa", json("[{\"id\":4},{\"id\":1}]"), 4));
    }

    @Test
    void sinEmpresaDelNodoNoBajaNadaConEmpresa() throws Exception {
        assertThrows(GuardaEmpresa.DatosDeOtraEmpresa.class,
                () -> GuardaEmpresa.verificar("producto", json("[{\"id\":1,\"empresa_id\":4}]"), null));
    }
}
