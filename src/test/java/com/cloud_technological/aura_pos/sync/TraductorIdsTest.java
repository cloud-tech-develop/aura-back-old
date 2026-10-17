package com.cloud_technological.aura_pos.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.cloud_technological.aura_pos.sync.nube.TraductorIds;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Los ids que bajaron de la nube pasan igual; los creados en la tienda se traducen. */
@ExtendWith(MockitoExtension.class)
class TraductorIdsTest {

    private static final Long NODO = 3L;
    private static final Long LOCAL = SyncIds.BASE_LOCAL + 5;

    @Mock private SyncNubeQueryRepository repo;

    private TraductorIds ids;

    @BeforeEach
    void setUp() {
        ids = new TraductorIds(repo);
    }

    @Test
    void idDeLaNubePasaSinConsultar() {
        assertEquals(88L, ids.aNube(NODO, TiposComando.T_TERCERO, 88L));
        assertNull(ids.aNube(NODO, TiposComando.T_TERCERO, null));
        verifyNoInteractions(repo);
    }

    @Test
    void idDelNodoSeTraduce() {
        when(repo.idNube(NODO, TiposComando.T_TURNO, LOCAL)).thenReturn(Optional.of(901L));

        assertEquals(901L, ids.aNube(NODO, TiposComando.T_TURNO, LOCAL));
    }

    @Test
    void idDelNodoQueNoHaLlegadoDetieneElComando() {
        when(repo.idNube(NODO, TiposComando.T_VENTA, LOCAL)).thenReturn(Optional.empty());

        GlobalException e = assertThrows(GlobalException.class,
                () -> ids.aNube(NODO, TiposComando.T_VENTA, LOCAL));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void referenciaQueNoViajaSeSuelta() {
        assertNull(TraductorIds.soloNube(LOCAL));
        assertEquals(12L, TraductorIds.soloNube(12L));
    }
}
