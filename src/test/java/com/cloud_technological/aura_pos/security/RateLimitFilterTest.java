package com.cloud_technological.aura_pos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Verifica el límite de intentos de la fase 0 del plan de seguridad. */
class RateLimitFilterTest {

    private static final int MAX = 5;

    private RateLimitFilter filtro(boolean habilitado) {
        return new RateLimitFilter(habilitado, MAX, 60, true);
    }

    private MockHttpServletRequest peticionLogin(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    /** Ejecuta el filtro y devuelve el status resultante. */
    private int ejecutar(RateLimitFilter filtro, MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    @DisplayName("deja pasar hasta el máximo y bloquea el siguiente intento")
    void bloqueaAlSuperarElMaximo() throws Exception {
        RateLimitFilter filtro = filtro(true);

        for (int i = 1; i <= MAX; i++) {
            assertThat(ejecutar(filtro, peticionLogin("10.0.0.1")))
                    .as("intento %d debe pasar", i)
                    .isEqualTo(200);
        }

        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(peticionLogin("10.0.0.1"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(res.getContentAsString()).contains("Demasiados intentos");
    }

    @Test
    @DisplayName("el contador es por IP: una IP bloqueada no afecta a otra")
    void contadorIndependientePorIp() throws Exception {
        RateLimitFilter filtro = filtro(true);

        for (int i = 0; i <= MAX; i++) {
            ejecutar(filtro, peticionLogin("10.0.0.1"));
        }
        assertThat(ejecutar(filtro, peticionLogin("10.0.0.1"))).isEqualTo(429);
        assertThat(ejecutar(filtro, peticionLogin("10.0.0.2"))).isEqualTo(200);
    }

    @Test
    @DisplayName("solo limita /api/auth/**; el resto de la API pasa siempre")
    void noLimitaOtrasRutas() throws Exception {
        RateLimitFilter filtro = filtro(true);

        for (int i = 0; i < MAX * 4; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/productos");
            req.setRemoteAddr("10.0.0.9");
            assertThat(ejecutar(filtro, req)).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("usa X-Forwarded-For cuando se confía en el proxy")
    void distingueIpsDetrasDelProxy() throws Exception {
        RateLimitFilter filtro = filtro(true);

        for (int i = 0; i <= MAX; i++) {
            MockHttpServletRequest req = peticionLogin("172.16.0.1");   // IP del proxy
            req.addHeader("X-Forwarded-For", "200.1.1.1, 172.16.0.1");
            ejecutar(filtro, req);
        }

        MockHttpServletRequest bloqueada = peticionLogin("172.16.0.1");
        bloqueada.addHeader("X-Forwarded-For", "200.1.1.1, 172.16.0.1");
        assertThat(ejecutar(filtro, bloqueada)).isEqualTo(429);

        // Otro cliente detrás del MISMO proxy no debe verse afectado.
        MockHttpServletRequest otra = peticionLogin("172.16.0.1");
        otra.addHeader("X-Forwarded-For", "200.2.2.2, 172.16.0.1");
        assertThat(ejecutar(filtro, otra)).isEqualTo(200);
    }

    @Test
    @DisplayName("con la propiedad apagada no limita nada")
    void sePuedeDesactivar() throws Exception {
        RateLimitFilter filtro = filtro(false);

        for (int i = 0; i < MAX * 3; i++) {
            assertThat(ejecutar(filtro, peticionLogin("10.0.0.3"))).isEqualTo(200);
        }
    }
}
