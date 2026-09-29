package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.ContabilidadConfigLogEntity;
import com.cloud_technological.aura_pos.entity.CuentaConfigEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ContabilidadConfigLogJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.CuentaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.ConfiguracionContableServiceImpl;

/**
 * Guardarraíles de parametrización (E1): el contador remapea DESTINOS dentro
 * de la clase PUC permitida, nunca cualquier cuenta; todo cambio queda en el
 * log de auditoría.
 */
@ExtendWith(MockitoExtension.class)
class ConfiguracionContableGuardarrailesTest {

    private static final Integer EMPRESA = 1;
    private static final Long USUARIO = 9L;

    @Mock
    private CuentaConfigJPARepository configRepo;
    @Mock
    private PlanCuentaJPARepository planRepo;
    @Mock
    private ContabilidadConfigLogJPARepository logRepo;

    @InjectMocks
    private ConfiguracionContableServiceImpl service;

    private PlanCuentaEntity cuenta(Long id, String codigo, boolean auxiliar) {
        return PlanCuentaEntity.builder()
                .id(id).empresaId(EMPRESA).codigo(codigo).nombre("Cuenta " + codigo)
                .tipo("ACTIVO").naturaleza("DEBITO").nivel((short) 3)
                .activa(true).auxiliar(auxiliar)
                .build();
    }

    @Test
    void rechazaCuentaDeClaseNoPermitida() {
        // INGRESOS_VENTAS (clase 4) mapeado a una cuenta clase 1 → rechazado.
        when(planRepo.findByIdAndEmpresaId(10L, EMPRESA))
                .thenReturn(Optional.of(cuenta(10L, "110505", true)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.actualizar(EMPRESA, ConceptoContable.INGRESOS_VENTAS, 10L, USUARIO));

        assertTrue(ex.getReason().contains("4xx"),
                "el mensaje debe decir qué clase se admite: " + ex.getReason());
        verify(configRepo, never()).save(any());
    }

    @Test
    void rechazaCuentaQueNoEsDeMovimiento() {
        when(planRepo.findByIdAndEmpresaId(10L, EMPRESA))
                .thenReturn(Optional.of(cuenta(10L, "4135", false)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.actualizar(EMPRESA, ConceptoContable.INGRESOS_VENTAS, 10L, USUARIO));

        assertTrue(ex.getReason().contains("movimiento"), ex.getReason());
    }

    @Test
    void aceptaCuentaValidaYEscribeLog() {
        when(planRepo.findByIdAndEmpresaId(10L, EMPRESA))
                .thenReturn(Optional.of(cuenta(10L, "413505", true)));
        when(configRepo.findByEmpresaIdAndConcepto(EMPRESA, ConceptoContable.INGRESOS_VENTAS))
                .thenReturn(Optional.of(CuentaConfigEntity.builder()
                        .empresaId(EMPRESA)
                        .concepto(ConceptoContable.INGRESOS_VENTAS)
                        .cuentaId(4L)
                        .build()));

        service.actualizar(EMPRESA, ConceptoContable.INGRESOS_VENTAS, 10L, USUARIO);

        ArgumentCaptor<ContabilidadConfigLogEntity> captor =
                ArgumentCaptor.forClass(ContabilidadConfigLogEntity.class);
        verify(logRepo).save(captor.capture());
        assertEquals(4L, captor.getValue().getCuentaAnteriorId());
        assertEquals(10L, captor.getValue().getCuentaNuevaId());
        assertEquals(USUARIO, captor.getValue().getUsuarioId());
    }

    @Test
    void noEscribeLogSiLaCuentaNoCambia() {
        when(planRepo.findByIdAndEmpresaId(eq(10L), eq(EMPRESA)))
                .thenReturn(Optional.of(cuenta(10L, "413505", true)));
        when(configRepo.findByEmpresaIdAndConcepto(EMPRESA, ConceptoContable.INGRESOS_VENTAS))
                .thenReturn(Optional.of(CuentaConfigEntity.builder()
                        .empresaId(EMPRESA)
                        .concepto(ConceptoContable.INGRESOS_VENTAS)
                        .cuentaId(10L)
                        .build()));

        service.actualizar(EMPRESA, ConceptoContable.INGRESOS_VENTAS, 10L, USUARIO);

        verify(logRepo, never()).save(any());
    }
}
