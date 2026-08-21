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

import java.util.List;
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

    /** Cuenta habilitada para mover dinero: activa, auxiliar y medio de pago. */
    private PlanCuentaEntity cuentaMedioPago(Long id) {
        PlanCuentaEntity c = new PlanCuentaEntity();
        c.setId(id);
        c.setCodigo("110505");
        c.setNombre("Caja Menor");
        c.setActiva(true);
        c.setAuxiliar(true);
        c.setEsMedioPago(true);
        return c;
    }

    @Test
    void efectivoSinTurnoDeclaradoCaeEnLaCajaAbiertaDeLaSucursal() {
        // El admin registra un gasto en efectivo: no tiene turno propio, pero el
        // dinero salió de la caja del punto y tiene que quedar en su cierre.
        when(turnoRepository.findByCajaSucursalIdAndEstado(SUCURSAL, "ABIERTA"))
                .thenReturn(List.of(turno(55L, "ABIERTA")));
        when(resolucionCuentaPago.resolver(EMPRESA, "EFECTIVO", null)).thenReturn(1105L);

        OrigenFondos origen = service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, null));

        assertEquals(Tipo.CAJA, origen.tipo());
        assertEquals(1105L, origen.cuentaContableId());
        assertEquals(55L, origen.turnoId());
        assertTrue(origen.generaMovimientoCaja());
        // Nadie eligió esa caja: el sistema la dedujo. Queda marcado para poder
        // auditar después qué movimientos cayeron ahí sin decisión de una persona.
        assertTrue(origen.turnoInferido());
    }

    @Test
    void efectivoSinNingunaCajaAbiertaSeRechazaConSalida() {
        when(turnoRepository.findByCajaSucursalIdAndEstado(SUCURSAL, "ABIERTA"))
                .thenReturn(List.of());

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
        verify(turnoRepository, never()).findByCajaSucursalIdAndEstado(any(), any());
    }

    @Test
    void cuentaContableElegidaAManoTienePrioridadSobreTodo() {
        // Aunque el método diga EFECTIVO, si el usuario eligió la cuenta desde
        // la que sale la plata, esa manda y no se le exige caja abierta.
        when(planCuentaRepository.findByIdAndEmpresaId(110505L, EMPRESA))
                .thenReturn(Optional.of(cuentaMedioPago(110505L)));

        OrigenFondos origen = service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, 110505L));

        assertEquals(Tipo.CUENTA_CONTABLE, origen.tipo());
        assertEquals(110505L, origen.cuentaContableId());
        assertFalse(origen.generaMovimientoCaja());
        verify(resolucionCuentaPago, never()).resolver(any(), any(), any());
        // Y no se consultó ninguna caja: no hacía falta.
        verify(turnoRepository, never()).findByCajaSucursalIdAndEstado(any(), any());
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
    void cuentaQueNoEsMedioDePagoSeRechaza() {
        // Sin esta validación se podía pagar un gasto acreditando una cuenta de
        // ingresos: el asiento quedaba sin sentido y nadie lo notaba hasta el
        // cierre de mes.
        PlanCuentaEntity ingresos = new PlanCuentaEntity();
        ingresos.setId(413505L);
        ingresos.setCodigo("413505");
        ingresos.setNombre("Comercio al por menor");
        ingresos.setActiva(true);
        ingresos.setAuxiliar(true);
        ingresos.setEsMedioPago(false);
        when(planCuentaRepository.findByIdAndEmpresaId(413505L, EMPRESA))
                .thenReturn(Optional.of(ingresos));

        GlobalException ex = assertThrows(GlobalException.class,
                () -> service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, 413505L)));

        assertTrue(ex.getMessage().contains("medio de pago"));
    }

    @Test
    void cuentaDeAgrupacionSeRechazaAntesDeArmarElAsiento() {
        // Una cuenta no auxiliar no recibe movimientos: dejarla pasar produce un
        // asiento que el validador rechaza después, lejos de donde está el error.
        PlanCuentaEntity grupo = new PlanCuentaEntity();
        grupo.setId(1105L);
        grupo.setCodigo("1105");
        grupo.setNombre("Caja");
        grupo.setActiva(true);
        grupo.setAuxiliar(false);
        grupo.setEsMedioPago(true);
        when(planCuentaRepository.findByIdAndEmpresaId(1105L, EMPRESA))
                .thenReturn(Optional.of(grupo));

        GlobalException ex = assertThrows(GlobalException.class,
                () -> service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, 1105L)));

        assertTrue(ex.getMessage().contains("agrupación"));
    }

    @Test
    void conDosCajasAbiertasPideElegirEnVezDeAdivinar() {
        // Tomar "la más antigua" equivale a adivinar de cuál cajón salió la
        // plata, y el descuadre le cae a un cajero que no gastó nada.
        when(turnoRepository.findByCajaSucursalIdAndEstado(SUCURSAL, "ABIERTA"))
                .thenReturn(List.of(turno(55L, "ABIERTA"), turno(56L, "ABIERTA")));

        GlobalException ex = assertThrows(GlobalException.class,
                () -> service.resolver(EMPRESA, solicitud("EFECTIVO", null, null, null)));

        assertTrue(ex.getMessage().contains("2 cajas abiertas"));
    }

    @Test
    void turnoDeclaradoPorElUsuarioNoQuedaMarcadoComoInferido() {
        when(turnoRepository.findByIdAndCajaSucursalEmpresaId(3L, EMPRESA))
                .thenReturn(Optional.of(turno(3L, "ABIERTA")));
        when(resolucionCuentaPago.resolver(EMPRESA, "EFECTIVO", null)).thenReturn(1105L);

        OrigenFondos origen = service.resolver(EMPRESA, solicitud("EFECTIVO", 3L, null, null));

        assertEquals(Tipo.CAJA, origen.tipo());
        assertEquals(3L, origen.turnoId());
        assertFalse(origen.turnoInferido());
    }

    @Test
    void salidaDeCajaDeOtroDiaAcreditaCajaPeroNoMueveNingunArqueo() {
        // El caso del cliente: la plata salió del cajón ayer, él lo anotó en su
        // cuaderno y cuadró la caja física contra él. Hoy solo registra la
        // factura. Contablemente la plata sí salió de caja; operativamente no
        // hay arqueo que mover — el de aquel día ya cerró contemplándola.
        when(resolucionCuentaPago.resolver(EMPRESA, "EFECTIVO", null)).thenReturn(1105L);

        OrigenFondos origen = service.resolver(EMPRESA, new Solicitud(
                "EFECTIVO", null, null, null, SUCURSAL, "compra", true));

        assertEquals(Tipo.CAJA_OTRO_DIA, origen.tipo());
        assertEquals(1105L, origen.cuentaContableId());
        assertFalse(origen.generaMovimientoCaja());
        // Y no se buscó ninguna caja abierta: no hacía falta.
        verify(turnoRepository, never()).findByCajaSucursalIdAndEstado(any(), any());
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
