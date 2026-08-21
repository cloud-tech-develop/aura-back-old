package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.ControlFechaRetroactivaServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Fase 6: el freno de documentos viejos. Solo aplica a la vía CAJA, que es la
 * única que le descuadra el arqueo a otra persona.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControlFechaRetroactivaServiceTest {

    private static final Integer EMPRESA = 1;
    private static final Long USUARIO = 9L;
    private static final boolean SALE_DE_CAJA = true;
    private static final String MOTIVO_VALIDO = "El proveedor vino a cobrar hoy en persona";

    @Mock private EmpresaJPARepository empresaRepository;
    @Mock private SecurityUtils securityUtils;

    private ControlFechaRetroactivaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ControlFechaRetroactivaServiceImpl(empresaRepository, securityUtils);
        empresaCon(3, true, "ADMIN");
    }

    private void empresaCon(int gracia, boolean bloquea, String rolAutoriza) {
        EmpresaEntity e = new EmpresaEntity();
        e.setDiasGraciaDocumentoRetroactivo(gracia);
        e.setBloquearCajaRetroactiva(bloquea);
        e.setRolAutorizaRetroactivo(rolAutoriza);
        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.of(e));
    }

    private Integer validar(LocalDate fecha, boolean saleDeCaja, String motivo) {
        return service.validar(EMPRESA, fecha, saleDeCaja, motivo, USUARIO, "compra");
    }

    @Test
    void loQueNoSaleDeCajaNiSiquieraConsultaLaConfiguracion() {
        // Caja menor, banco y crédito no descuadran el arqueo de nadie. Ponerles
        // fricción empujaría al usuario de vuelta a "Caja", que es lo contrario
        // de lo que se busca.
        assertNull(validar(LocalDate.now().minusMonths(6), !SALE_DE_CAJA, null));

        verify(empresaRepository, never()).findById(any());
    }

    @Test
    void dentroDeLaVentanaNoPideNada() {
        // Pagar hoy la factura de ayer es la operación más normal del mundo.
        assertNull(validar(LocalDate.now().minusDays(2), SALE_DE_CAJA, null));
    }

    @Test
    void elBordeDeLaVentanaTodaviaPasa() {
        // Con gracia = 3, el documento de hace exactamente 3 días entra.
        assertNull(validar(LocalDate.now().minusDays(3), SALE_DE_CAJA, null));
    }

    @Test
    void sinFechaSeTrataComoDeHoy() {
        assertNull(validar(null, SALE_DE_CAJA, null));
    }

    @Test
    void fueraDeLaVentanaSinRolYConBloqueoSeRechazaOfreciendoSalidas() {
        when(securityUtils.getRol()).thenReturn("CAJERO");

        GlobalException ex = assertThrows(GlobalException.class,
                () -> validar(LocalDate.now().minusDays(20), SALE_DE_CAJA, MOTIVO_VALIDO));

        // El mensaje tiene que decir qué hacer, no solo que no se puede.
        assertTrue(ex.getMessage().contains("caja menor"));
        assertTrue(ex.getMessage().contains("cuenta por pagar"));
    }

    @Test
    void sinBloqueoPeroSinRolTampocoPasa() {
        // Apagar el bloqueo relaja el requisito de que la vía caja esté cerrada,
        // no el de quién puede autorizar.
        empresaCon(3, false, "ADMIN");
        when(securityUtils.getRol()).thenReturn("CAJERO");

        GlobalException ex = assertThrows(GlobalException.class,
                () -> validar(LocalDate.now().minusDays(20), SALE_DE_CAJA, MOTIVO_VALIDO));

        assertTrue(ex.getMessage().contains("ADMIN"));
    }

    @Test
    void conRolPeroSinMotivoSeRechaza() {
        when(securityUtils.getRol()).thenReturn("ADMIN");

        GlobalException ex = assertThrows(GlobalException.class,
                () -> validar(LocalDate.now().minusDays(20), SALE_DE_CAJA, null));

        assertTrue(ex.getMessage().contains("Explica"));
    }

    @Test
    void unMotivoDeTresLetrasNoEsUnMotivo() {
        // "ok" o "ya" es saltarse el control, no explicarlo.
        when(securityUtils.getRol()).thenReturn("ADMIN");

        assertThrows(GlobalException.class,
                () -> validar(LocalDate.now().minusDays(20), SALE_DE_CAJA, "ok"));
    }

    @Test
    void conRolYMotivoPasaYQuedaRegistradoQuienAutorizo() {
        when(securityUtils.getRol()).thenReturn("ADMIN");

        Integer autorizadoPor = validar(LocalDate.now().minusDays(20), SALE_DE_CAJA, MOTIVO_VALIDO);

        assertEquals(USUARIO.intValue(), autorizadoPor);
    }

    @Test
    void elRolSeComparaSinImportarMayusculas() {
        when(securityUtils.getRol()).thenReturn("admin");

        assertEquals(USUARIO.intValue(),
                validar(LocalDate.now().minusDays(20), SALE_DE_CAJA, MOTIVO_VALIDO));
    }

    @Test
    void unaEmpresaSinGraciaExigeAutorizacionDesdeElPrimerDia() {
        empresaCon(0, true, "ADMIN");
        when(securityUtils.getRol()).thenReturn("ADMIN");

        // Hoy sigue pasando: cero días de gracia no es "ni siquiera hoy".
        assertNull(validar(LocalDate.now(), SALE_DE_CAJA, null));
        // Ayer ya no.
        assertThrows(GlobalException.class,
                () -> validar(LocalDate.now().minusDays(1), SALE_DE_CAJA, null));
    }
}
