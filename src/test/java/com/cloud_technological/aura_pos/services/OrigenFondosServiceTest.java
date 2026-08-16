package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.services.OrigenFondosService.OrigenFondos;
import com.cloud_technological.aura_pos.services.OrigenFondosService.Solicitud;
import com.cloud_technological.aura_pos.services.OrigenFondosService.Tipo;
import com.cloud_technological.aura_pos.services.implementations.OrigenFondosServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Fase 1 del plan de origen de fondos: de dónde sale la plata deja de deducirse
 * del usuario que digita el documento.
 */
@ExtendWith(MockitoExtension.class)
class OrigenFondosServiceTest {

    private static final Integer EMPRESA  = 1;
    private static final Integer SUCURSAL = 7;

    @Mock private TurnoCajaJPARepository turnoRepository;
    @Mock private PlanCuentaJPARepository planCuentaRepository;
    @Mock private ResolucionCuentaPago resolucionCuentaPago;

    private OrigenFondosServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrigenFondosServiceImpl(turnoRepository, planCuentaRepository, resolucionCuentaPago);
    }

    private Solicitud solicitud(String metodo, Long turnoId, Long bancariaId, Long contableId) {
        return new Solicitud(metodo, turnoId, bancariaId, contableId, SUCURSAL, "gasto");
    }

    private TurnoCajaEntity turno(Long id, String estado) {
        TurnoCajaEntity t = new TurnoCajaEntity();
        t.setId(id);
        t.setEstado(estado);
        return t;
    }

    @Test
    void efectivoSinTurnoDeclaradoCaeEnLaCajaAbiertaDeLaSucursal() {
        // El admin registra un gasto en efectivo: no tiene turno propio, pero el
        // dinero salió de la caja del punto y tiene que quedar en su cierre.
        when(turnoRepository.findFirstByCajaSucursalIdAndEstadoOrderByFechaAperturaAsc(SUCURSAL, "ABIERTA"))
                .thenReturn(Optional.of(turno(55L, "ABIERTA")));
        when(resolucionCuentaPago.resolver(EMPRESA, "EFECTIVO", null)).thenReturn(1105L);

        OrigenFondos origen = service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, null));

        assertEquals(Tipo.CAJA, origen.tipo());
        assertEquals(1105L, origen.cuentaContableId());
        assertEquals(55L, origen.turnoId());
        assertTrue(origen.generaMovimientoCaja());
    }

    @Test
    void efectivoSinNingunaCajaAbiertaSeRechazaConSalida() {
        when(turnoRepository.findFirstByCajaSucursalIdAndEstadoOrderByFechaAperturaAsc(SUCURSAL, "ABIERTA"))
                .thenReturn(Optional.empty());

        GlobalException ex = assertThrows(GlobalException.class,
                () -> service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, null)));

        // El mensaje tiene que ofrecer la alternativa, no solo bloquear.
        assertTrue(ex.getMessage().contains("cuenta bancaria"));
    }

    @Test
    void pagoPorBancoNoExigeCajaNiMueveElArqueo() {
        when(resolucionCuentaPago.resolver(EMPRESA, "TRANSFERENCIA", 9L)).thenReturn(111005L);

        OrigenFondos origen = service.resolver(EMPRESA, solicitud("TRANSFERENCIA", null, 9L, null));

        assertEquals(Tipo.BANCO, origen.tipo());
        assertEquals(111005L, origen.cuentaContableId());
        assertFalse(origen.generaMovimientoCaja());
        verify(turnoRepository, never())
                .findFirstByCajaSucursalIdAndEstadoOrderByFechaAperturaAsc(any(), any());
    }

    @Test
    void cuentaContableElegidaAManoTienePrioridadSobreTodo() {
        // Aunque el método diga EFECTIVO, si el usuario eligió la cuenta desde
        // la que sale la plata, esa manda y no se le exige caja abierta.
        PlanCuentaEntity cuenta = new PlanCuentaEntity();
        cuenta.setId(233505L);
        cuenta.setActiva(true);
        when(planCuentaRepository.findByIdAndEmpresaId(233505L, EMPRESA)).thenReturn(Optional.of(cuenta));

        OrigenFondos origen = service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, 233505L));

        assertEquals(Tipo.CUENTA_CONTABLE, origen.tipo());
        assertEquals(233505L, origen.cuentaContableId());
        assertFalse(origen.generaMovimientoCaja());
        verify(resolucionCuentaPago, never()).resolver(any(), any(), any());
    }

    @Test
    void cuentaContableInactivaSeRechaza() {
        PlanCuentaEntity cuenta = new PlanCuentaEntity();
        cuenta.setId(233505L);
        cuenta.setActiva(false);
        when(planCuentaRepository.findByIdAndEmpresaId(233505L, EMPRESA)).thenReturn(Optional.of(cuenta));

        assertThrows(GlobalException.class,
                () -> service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, 233505L)));
    }

    @Test
    void turnoDeclaradoPeroCerradoSeRechaza() {
        when(turnoRepository.findByIdAndCajaSucursalEmpresaId(3L, EMPRESA))
                .thenReturn(Optional.of(turno(3L, "CERRADA")));

        assertThrows(GlobalException.class,
                () -> service.resolver(EMPRESA, solicitud("EFECTIVO", 3L, null, null)));
    }

    @Test
    void tarjetaConTurnoDelCajeroQuedaTrazadaPeroNoInflaElEfectivo() {
        // Este es el faltante fantasma del cierre: el abono con datáfono se
        // registraba contra el turno y subía el efectivo esperado.
        when(turnoRepository.findByIdAndCajaSucursalEmpresaId(3L, EMPRESA))
                .thenReturn(Optional.of(turno(3L, "ABIERTA")));
        when(resolucionCuentaPago.resolver(eq(EMPRESA), eq("TARJETA"), eq(null))).thenReturn(131005L);

        OrigenFondos origen = service.resolver(EMPRESA, solicitud("TARJETA", 3L, null, null));

        assertEquals(Tipo.CUENTA_CONTABLE, origen.tipo());
        assertEquals(3L, origen.turnoId());
        assertFalse(origen.generaMovimientoCaja());
    }

    @Test
    void efectivoSinSucursalNiTurnoPideDatosEnVezDeAdivinar() {
        Solicitud sinSucursal = new Solicitud("EFECTIVO", null, null, null, null, "abono");

        GlobalException ex = assertThrows(GlobalException.class,
                () -> service.resolver(EMPRESA, sinSucursal));

        assertTrue(ex.getMessage().contains("sucursal"));
        assertNull(sinSucursal.sucursalId());
    }
}
