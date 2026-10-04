package com.cloud_technological.aura_pos.services.permisos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.CodigoGenerado;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Exceso;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.UsarCodigo;
import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.entity.AutorizacionEntity;
import com.cloud_technological.aura_pos.repositories.auditoria.AutorizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.auditoria.BitacoraQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Límites de descuento/precio y autorización (PLAN_PERMISOS P8). */
class AutorizacionServiceTest {

    private static final int EMPRESA = 7;
    private static final int CAJERO = 1;

    private PermisoUsuarioService permisos;
    private BitacoraQueryRepository query;
    private AutorizacionJPARepository repo;
    private AutorizacionService service;

    @BeforeEach
    void setUp() {
        permisos = mock(PermisoUsuarioService.class);
        query = mock(BitacoraQueryRepository.class);
        repo = mock(AutorizacionJPARepository.class);
        service = new AutorizacionService(permisos, query, repo, mock(BitacoraService.class));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static final int SUPERVISOR = 2;

    private void supervisor(boolean puede, String descuentoMax) {
        when(permisos.puedeEspecial(SUPERVISOR, AutorizacionService.ESPECIAL_AUTORIZAR)).thenReturn(puede);
        PermisosUsuarioDto p = new PermisosUsuarioDto();
        p.setDescuentoMaxPct(descuentoMax != null ? new BigDecimal(descuentoMax) : null);
        when(permisos.efectivos(SUPERVISOR)).thenReturn(p);
    }

    private static UsarCodigo usar(String codigo, String descuento) {
        UsarCodigo u = new UsarCodigo();
        u.setCodigo(codigo);
        u.setDescuentoPct(new BigDecimal(descuento));
        return u;
    }

    @Test
    void elCodigoSoloSeGuardaComoHuellaYSirveUnaVez() {
        supervisor(true, null);
        org.mockito.ArgumentCaptor<AutorizacionEntity> captor = org.mockito.ArgumentCaptor.forClass(AutorizacionEntity.class);
        CodigoGenerado g = service.generarCodigo(SUPERVISOR, EMPRESA);
        org.mockito.Mockito.verify(repo).save(captor.capture());
        AutorizacionEntity guardada = captor.getValue();

        assertTrue(g.getCodigo().matches("\\d{6}"));
        assertFalse(guardada.getCodigoHash().contains(g.getCodigo()), "la base no guarda el código");
        assertEquals(AutorizacionService.huella(EMPRESA, g.getCodigo()), guardada.getCodigoHash());

        guardada.setId(50L);
        when(query.autorizacionPorCodigo(EMPRESA, guardada.getCodigoHash())).thenReturn(50L);
        when(repo.findById(50L)).thenReturn(Optional.of(guardada));
        service.usarCodigo(usar(g.getCodigo(), "15"), CAJERO, EMPRESA);
        assertEquals(AutorizacionEntity.VIGENTE, guardada.getEstado());
        assertEquals(CAJERO, guardada.getSolicitanteId());

        // Ya no está en estado CODIGO: la consulta no la vuelve a encontrar.
        when(query.autorizacionPorCodigo(EMPRESA, guardada.getCodigoHash())).thenReturn(null);
        assertThrows(GlobalException.class, () -> service.usarCodigo(usar(g.getCodigo(), "15"), CAJERO, EMPRESA));
    }

    @Test
    void sinPermisoNoGeneraCodigo() {
        supervisor(false, null);
        assertThrows(GlobalException.class, () -> service.generarCodigo(SUPERVISOR, EMPRESA));
    }

    @Test
    void elCodigoNoCubreMasQueElLimiteDelSupervisor() {
        supervisor(true, "10");
        AutorizacionEntity a = new AutorizacionEntity();
        a.setId(51L);
        a.setEmpresaId(EMPRESA);
        a.setAutorizadorId(SUPERVISOR);
        a.setEstado(AutorizacionEntity.CODIGO);
        a.setExpiraEn(LocalDateTime.now().plusMinutes(2));
        when(query.autorizacionPorCodigo(any(), any())).thenReturn(51L);
        when(repo.findById(51L)).thenReturn(Optional.of(a));

        assertThrows(GlobalException.class, () -> service.usarCodigo(usar("123456", "15"), CAJERO, EMPRESA));
    }

    @Test
    void cincoCodigosErradosFrenanAlCajero() {
        when(query.autorizacionPorCodigo(any(), any())).thenReturn(null);
        for (int i = 0; i < 5; i++) {
            assertThrows(GlobalException.class, () -> service.usarCodigo(usar("000000", "5"), CAJERO, EMPRESA));
        }
        GlobalException frenado = assertThrows(GlobalException.class,
                () -> service.usarCodigo(usar("000000", "5"), CAJERO, EMPRESA));
        assertTrue(frenado.getMessage().contains("Demasiados intentos"));
    }

    @Test
    void laSolicitudRemotaLaApruebaOtroConPermiso() {
        supervisor(true, null);
        AutorizacionEntity s = new AutorizacionEntity();
        s.setId(60L);
        s.setEmpresaId(EMPRESA);
        s.setSolicitanteId(CAJERO);
        s.setEstado(AutorizacionEntity.SOLICITADA);
        s.setDescuentoPct(new BigDecimal("20"));
        s.setRebajaPct(BigDecimal.ZERO);
        s.setExpiraEn(LocalDateTime.now().plusMinutes(10));
        when(repo.findById(60L)).thenReturn(Optional.of(s));

        assertThrows(GlobalException.class, () -> service.aprobar(60L, CAJERO, EMPRESA), "nadie se aprueba solo");
        service.aprobar(60L, SUPERVISOR, EMPRESA);
        assertEquals(AutorizacionEntity.VIGENTE, s.getEstado());
        assertEquals(SUPERVISOR, s.getAutorizadorId());
    }

    private void limites(String descuento, String rebaja) {
        PermisosUsuarioDto p = new PermisosUsuarioDto();
        p.setDescuentoMaxPct(descuento != null ? new BigDecimal(descuento) : null);
        p.setRebajaPrecioMaxPct(rebaja != null ? new BigDecimal(rebaja) : null);
        when(permisos.efectivos(CAJERO)).thenReturn(p);
    }

    private static CreateVentaDetalleDto linea(long productoId, String precio, String cantidad, String descuento) {
        CreateVentaDetalleDto d = new CreateVentaDetalleDto();
        d.setProductoId(productoId);
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setCantidad(new BigDecimal(cantidad));
        d.setDescuentoValor(new BigDecimal(descuento));
        return d;
    }

    private static CreateVentaDto venta(String descuentoGeneral, CreateVentaDetalleDto... lineas) {
        CreateVentaDto v = new CreateVentaDto();
        v.setDetalles(List.of(lineas));
        v.setDescuentoGeneral(descuentoGeneral != null ? new BigDecimal(descuentoGeneral) : null);
        return v;
    }

    @Test
    void sinLimitesNoMideNada() {
        limites(null, null);
        Exceso x = service.exceso(venta(null, linea(1, "1000", "1", "500")), CAJERO, EMPRESA);
        assertFalse(x.requiereAutorizacion());
    }

    @Test
    void descuentoDeLineaDentroYFueraDelLimite() {
        limites("10", null);
        assertFalse(service.exceso(venta(null, linea(1, "1000", "2", "200")), CAJERO, EMPRESA).requiereAutorizacion());

        Exceso x = service.exceso(venta(null, linea(1, "1000", "2", "300")), CAJERO, EMPRESA);
        assertTrue(x.requiereAutorizacion());
        assertEquals(0, new BigDecimal("15").compareTo(x.getDescuentoPct()));
    }

    @Test
    void descuentoDeLineaYGeneralSeAcumulan() {
        limites("15", null);
        // 10% en la línea y 10% general sobre lo que queda = 19%.
        Exceso x = service.exceso(venta("90", linea(1, "1000", "1", "100")), CAJERO, EMPRESA);
        assertEquals(0, new BigDecimal("19").compareTo(x.getDescuentoPct()));
        assertTrue(x.requiereAutorizacion());
    }

    @Test
    void lasReglasAutomaticasNoCuentan() {
        limites("5", null);
        CreateVentaDetalleDto d = linea(1, "1000", "1", "300");
        d.setReglaDescuentoId(4L);
        assertFalse(service.exceso(venta(null, d), CAJERO, EMPRESA).requiereAutorizacion());
    }

    @Test
    void rebajaContraElMenorPrecioDelProducto() {
        limites(null, "5");
        when(query.precioMinimoSinIva(eq(EMPRESA), eq(1L), any())).thenReturn(new BigDecimal("1000"));

        assertFalse(service.exceso(venta(null, linea(1, "960", "1", "0")), CAJERO, EMPRESA).requiereAutorizacion());
        Exceso x = service.exceso(venta(null, linea(1, "900", "1", "0")), CAJERO, EMPRESA);
        assertTrue(x.requiereAutorizacion());
        assertEquals(0, new BigDecimal("10").compareTo(x.getRebajaPct()));
    }

    @Test
    void elRedondeoDelPosNoPideAutorizacion() {
        limites(null, "0");
        when(query.precioMinimoSinIva(eq(EMPRESA), eq(1L), any())).thenReturn(new BigDecimal("840.34"));
        // 840.336 redondeado: menos de 0.5% de diferencia.
        assertFalse(service.exceso(venta(null, linea(1, "840.30", "1", "0")), CAJERO, EMPRESA).requiereAutorizacion());
    }

    @Test
    void sinAutorizacionLaVentaNoPasa() {
        limites("10", null);
        Exceso x = service.exceso(venta(null, linea(1, "1000", "1", "200")), CAJERO, EMPRESA);
        assertThrows(GlobalException.class, () -> service.validarParaVenta(x, null, CAJERO, EMPRESA));
    }

    @Test
    void laAutorizacionDebeCubrirElExcesoYSerDelMismoUsuario() {
        limites("10", null);
        Exceso x = service.exceso(venta(null, linea(1, "1000", "1", "200")), CAJERO, EMPRESA);
        AutorizacionEntity a = new AutorizacionEntity();
        a.setEmpresaId(EMPRESA);
        a.setSolicitanteId(CAJERO);
        a.setDescuentoPct(new BigDecimal("20"));
        a.setRebajaPct(BigDecimal.ZERO);
        a.setExpiraEn(LocalDateTime.now().plusMinutes(5));
        when(repo.findById(9L)).thenReturn(Optional.of(a));

        assertEquals(a, service.validarParaVenta(x, 9L, CAJERO, EMPRESA));

        a.setDescuentoPct(new BigDecimal("15"));
        assertThrows(GlobalException.class, () -> service.validarParaVenta(x, 9L, CAJERO, EMPRESA), "no la cubre");
        a.setDescuentoPct(new BigDecimal("20"));
        assertThrows(GlobalException.class, () -> service.validarParaVenta(x, 9L, 2, EMPRESA), "otro usuario");
        a.setEstado(AutorizacionEntity.USADA);
        assertThrows(GlobalException.class, () -> service.validarParaVenta(x, 9L, CAJERO, EMPRESA), "ya usada");
    }

    @Test
    void sinExcesoNoPideNada() {
        limites("10", null);
        Exceso x = service.exceso(venta(null, linea(1, "1000", "1", "50")), CAJERO, EMPRESA);
        assertNull(service.validarParaVenta(x, null, CAJERO, EMPRESA));
    }
}
