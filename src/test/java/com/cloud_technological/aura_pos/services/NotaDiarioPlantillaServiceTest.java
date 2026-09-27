package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioDto;
import com.cloud_technological.aura_pos.entity.NotaDiarioPlantillaEntity;
import com.cloud_technological.aura_pos.entity.NotaDiarioPlantillaLineaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioPlantillaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository;
import com.cloud_technological.aura_pos.services.implementations.NotaDiarioLineasBuilder;
import com.cloud_technological.aura_pos.services.implementations.NotaDiarioPlantillaServiceImpl;

/** La plantilla recurrente deja un borrador por mes, el día pactado, y nunca dos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotaDiarioPlantillaServiceTest {

    @Mock private NotaDiarioPlantillaJPARepository repo;
    @Mock private NotaDiarioQueryRepository queryRepo;
    @Mock private NotaDiarioService notaService;

    private NotaDiarioPlantillaServiceImpl service;
    private NotaDiarioPlantillaEntity plantilla;

    @BeforeEach
    void setUp() {
        service = new NotaDiarioPlantillaServiceImpl(repo, queryRepo, new NotaDiarioLineasBuilder(queryRepo), notaService);
        plantilla = new NotaDiarioPlantillaEntity();
        plantilla.setId(5L);
        plantilla.setEmpresaId(1);
        plantilla.setUsuarioId(7);
        plantilla.setNombre("Amortización seguro");
        plantilla.setDescripcion("Amortización mensual del seguro");
        plantilla.setRecurrente(true);
        plantilla.setActiva(true);
        plantilla.setDiaMes((short) 15);
        NotaDiarioPlantillaLineaEntity l = new NotaDiarioPlantillaLineaEntity();
        l.setCuentaId(20L);
        l.setDebito(new BigDecimal("100000"));
        plantilla.getLineas().add(l);
        when(repo.findById(5L)).thenReturn(Optional.of(plantilla));
    }

    @Test
    @DisplayName("Antes de su día no genera")
    void antesDelDia() {
        assertFalse(service.generarProgramada(5L, LocalDate.of(2026, 9, 14)));
        verify(notaService, never()).crear(any(), any(), any());
    }

    @Test
    @DisplayName("En su día genera el borrador con la fecha pactada y no repite el mes")
    void generaUnaVezPorMes() {
        assertTrue(service.generarProgramada(5L, LocalDate.of(2026, 9, 20)));

        ArgumentCaptor<SaveNotaDiarioDto> captor = ArgumentCaptor.forClass(SaveNotaDiarioDto.class);
        verify(notaService).crear(eq(1), eq(7), captor.capture());
        assertEquals(LocalDate.of(2026, 9, 15), captor.getValue().getFecha());
        assertEquals(Boolean.FALSE, captor.getValue().getContabilizar());
        assertEquals(5L, captor.getValue().getPlantillaId());
        assertEquals("2026-09", plantilla.getUltimoPeriodo());

        assertFalse(service.generarProgramada(5L, LocalDate.of(2026, 9, 28)));
        verify(notaService, times(1)).crear(any(), any(), any());
    }

    @Test
    @DisplayName("Inactiva no genera")
    void inactiva() {
        plantilla.setActiva(false);
        assertFalse(service.generarProgramada(5L, LocalDate.of(2026, 9, 20)));
    }
}
