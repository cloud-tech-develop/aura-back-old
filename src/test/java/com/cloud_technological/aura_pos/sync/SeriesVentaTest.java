package com.cloud_technological.aura_pos.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.repositories.ventas.VentaQueryRepository;
import com.cloud_technological.aura_pos.sync.local.EstadoNodoLocal;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Numeración de ventas en modo híbrido: la tienda y la nube nunca pueden sacar
 * el mismo número, y la nube conserva el que la tienda ya imprimió.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeriesVentaTest {

    private static final Integer EMPRESA = 1;

    @Mock private SyncProperties props;
    @Mock private VentaQueryRepository ventas;
    @Mock private EstadoNodoLocal estadoNodo;
    @Mock private SyncNubeQueryRepository nube;

    private SeriesVenta series;
    private SucursalEntity sucursal;

    @BeforeEach
    void setUp() {
        series = new SeriesVenta(props, ventas, estadoNodo, nube);
        sucursal = new SucursalEntity();
        sucursal.setId(7);
        sucursal.setPrefijoFacturacion("POS");
    }

    @Test
    void enReplayConservaLaNumeracionDelNodo() {
        SyncContexto.Replay replay = new SyncContexto.Replay(1L, LocalDateTime.now(), "T1", 42L);

        SeriesVenta.Numero n = SyncContexto.ejecutar(replay, () -> series.siguiente(sucursal, EMPRESA));

        assertEquals("T1", n.prefijo());
        assertEquals(42L, n.consecutivo());
        verifyNoInteractions(ventas);
    }

    @Test
    void elNodoUsaSuSerieDesdeElPisoQueConoceLaNube() {
        when(props.esNodoLocal()).thenReturn(true);
        when(estadoNodo.prefijoVentas()).thenReturn(Optional.of("T1"));
        when(estadoNodo.pisoConsecutivoVenta(7)).thenReturn(120L);
        when(ventas.obtenerSiguienteConsecutivoSerie(7L, "T1", 120L)).thenReturn(121L);

        SeriesVenta.Numero n = series.siguiente(sucursal, EMPRESA);

        assertEquals("T1", n.prefijo());
        assertEquals(121L, n.consecutivo());
        verify(ventas, never()).obtenerSiguienteConsecutivo(anyLong(), any());
    }

    @Test
    void elNodoSinSincronizarNoVende() {
        when(props.esNodoLocal()).thenReturn(true);
        when(estadoNodo.prefijoVentas()).thenReturn(Optional.empty());

        GlobalException e = assertThrows(GlobalException.class, () -> series.siguiente(sucursal, EMPRESA));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verifyNoInteractions(ventas);
    }

    @Test
    void laNubeNoCuentaLasSeriesDeLosNodos() {
        when(props.esNodoLocal()).thenReturn(false);
        when(props.esNube()).thenReturn(true);
        when(nube.prefijosNodos(EMPRESA)).thenReturn(List.of("T1", "T2"));
        when(ventas.obtenerSiguienteConsecutivo(7L, List.of("T1", "T2"))).thenReturn(15L);

        SeriesVenta.Numero n = series.siguiente(sucursal, EMPRESA);

        assertEquals("POS", n.prefijo());
        assertEquals(15L, n.consecutivo());
    }
}
