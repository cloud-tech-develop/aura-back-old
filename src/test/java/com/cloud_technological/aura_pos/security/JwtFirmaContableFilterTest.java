package com.cloud_technological.aura_pos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;

/** Token de paso y acceso de la firma contable en el filtro (docs/PLAN_FIRMA_CONTABLE.md, F3). */
class JwtFirmaContableFilterTest {

    private JwtTokenProvider tokens;
    private AccesoFirmaService accesoFirma;
    private JwtAuthenticationFilter filtro;
    private FilterChain cadena;

    @BeforeEach
    void setUp() {
        tokens = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokens, "jwtSecret",
                Base64.getEncoder().encodeToString("clave-de-prueba-de-32-bytes-o-mas!!".getBytes()));
        ReflectionTestUtils.setField(tokens, "jwtExpirationDate", 3_600_000L);

        UserDetailsService usuarios = mock(UserDetailsService.class);
        when(usuarios.loadUserByUsername("conta@firma.co")).thenReturn(
                new User("conta@firma.co", "x", List.of(new SimpleGrantedAuthority("ADMIN"))));
        SesionService sesiones = mock(SesionService.class);
        when(sesiones.versionActual(any())).thenReturn(0);
        accesoFirma = mock(AccesoFirmaService.class);
        filtro = new JwtAuthenticationFilter(tokens, usuarios, sesiones, accesoFirma);
        cadena = mock(FilterChain.class);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse pedir(String ruta, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", ruta);
        req.setRequestURI(ruta);
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, cadena);
        return res;
    }

    @Test
    void elTokenDePasoSoloSirveParaElegirEmpresa() throws Exception {
        String paso = tokens.generateTokenSeleccion("conta@firma.co", 7L, 0);

        MockHttpServletResponse otra = pedir("/api/ventas/page", paso);
        assertEquals(401, otra.getStatus());
        assertTrue(otra.getContentAsString().contains("ELEGIR_EMPRESA"));
        verify(cadena, never()).doFilter(any(), any());

        MockHttpServletResponse elegir = pedir(JwtAuthenticationFilter.RUTA_SELECCION, paso);
        assertEquals(200, elegir.getStatus());
    }

    @Test
    void conElAccesoQuitadoElClienteResponde401() throws Exception {
        String t = tokens.generateTokenCliente("conta@firma.co", 50, 3L, "CONTADOR_EXTERNO", 7L, 0, 9L, 20);
        when(accesoFirma.vigente(7L, 9L)).thenReturn(false);

        MockHttpServletResponse res = pedir("/api/contabilidad/asientos", t);

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("ACCESO_FIRMA_REVOCADO"));
    }

    @Test
    void conAccesoVigenteEntraConElRolDelToken() throws Exception {
        String t = tokens.generateTokenCliente("conta@firma.co", 50, 3L, "CONTADOR_EXTERNO", 7L, 0, 9L, 20);
        when(accesoFirma.vigente(7L, 9L)).thenReturn(true);
        // La cadena corre dentro del filtro: ahí se revisa la autenticación.
        org.mockito.Mockito.doAnswer(i -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals(List.of("CONTADOR_EXTERNO"),
                    auth.getAuthorities().stream().map(a -> a.getAuthority()).toList());
            return null;
        }).when(cadena).doFilter(any(), any());

        MockHttpServletResponse res = pedir("/api/contabilidad/asientos", t);

        assertEquals(200, res.getStatus());
        verify(cadena).doFilter(any(), any());
    }

    @Test
    void elTokenNormalNoCambia() throws Exception {
        String t = tokens.generateToken("conta@firma.co", 20, 1L, "ADMIN", 7L, 0);

        MockHttpServletResponse res = pedir("/api/ventas/page", t);

        assertEquals(200, res.getStatus());
        verify(accesoFirma, never()).vigente(any(), any());
    }
}
