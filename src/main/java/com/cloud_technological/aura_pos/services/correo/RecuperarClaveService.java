package com.cloud_technological.aura_pos.services.correo;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.PasswordResetTokenEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.users.PasswordResetTokenRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Recuperación de la contraseña por correo desde el login ("¿Olvidaste tu
 * contraseña?"): el usuario pide un enlace y le llega al correo registrado.
 *
 * <p>Nunca dice si el usuario existe: la respuesta es siempre la misma, así nadie
 * puede averiguar usuarios probando. Solo vale el último enlace pedido, dura
 * {@link #MINUTOS_ENLACE} minutos y entre dos pedidos del mismo usuario debe pasar
 * {@link #MINUTOS_ESPERA} (el límite por IP ya lo pone RateLimitFilter).
 */
@Service
@RequiredArgsConstructor
public class RecuperarClaveService {

    private static final Logger log = LoggerFactory.getLogger(RecuperarClaveService.class);

    /** Minutos que dura el enlace para cambiar la contraseña. */
    static final int MINUTOS_ENLACE = 60;

    /** Minutos mínimos entre dos enlaces del mismo usuario (evita inundar su correo). */
    static final int MINUTOS_ESPERA = 2;

    /** Tipo del correo en correo_envio; documento_id = el usuario. */
    public static final String TIPO = "CLAVE";

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CorreoService correos;
    private final UsuarioJPARepository usuarioRepo;
    private final UsuarioQueryRepository usuarioQuery;
    private final PasswordResetTokenRepository tokenRepo;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Si el usuario existe, está activo y tiene correo, le envía un enlace nuevo
     * para cambiar la contraseña. En cualquier otro caso no hace nada: quien llama
     * responde igual para no revelar nada.
     *
     * @param usuario el usuario con el que entra al sistema
     */
    @Transactional
    public void solicitar(String usuario) {
        if (usuario == null || usuario.isBlank()) return;
        UsuarioEntity u = usuarioRepo.findByUsername(usuario.trim()).orElse(null);
        if (u == null || !Boolean.TRUE.equals(u.getActivo()) || u.getEmpresa() == null) return;

        String destino = correoDe(u);
        if (destino == null) {
            log.info("Recuperar contraseña: el usuario {} no tiene correo registrado", u.getId());
            return;
        }
        LocalDateTime ultimo = usuarioQuery.ultimoEnlaceClave(u.getId());
        if (ultimo != null && ultimo.isAfter(LocalDateTime.now().minusMinutes(MINUTOS_ESPERA))) return;

        // Solo sirve el último enlace: los anteriores se borran.
        tokenRepo.deleteByUsuarioId(u.getId());
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenRepo.save(PasswordResetTokenEntity.builder()
                .usuario(u)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(MINUTOS_ENLACE))
                .usado(false)
                .build());

        String mensaje = "Hola,\n\nRecibimos una solicitud para cambiar la contraseña de su usuario en Aura.\n\n"
                + "Usuario: " + u.getUsername() + "\n\n"
                + "Cree su nueva contraseña con el botón de abajo. El enlace vence en " + MINUTOS_ENLACE
                + " minutos y sirve una sola vez.\n\n"
                + "Si usted no pidió este cambio, ignore este correo: su contraseña sigue siendo la misma.";
        enviar(u, destino, "Cambiar su contraseña de Aura", mensaje, "Enlace para cambiar la contraseña",
                "Cambiar mi contraseña", frontendUrl + "/reset-password?token=" + token);
    }

    /**
     * Aviso al usuario de que su contraseña cambió: si no fue él, sabe que debe reaccionar.
     * Va en su propia transacción: si el correo falla, el cambio de contraseña queda igual.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void avisarCambio(Integer usuarioId) {
        UsuarioEntity u = usuarioId != null ? usuarioRepo.findById(usuarioId).orElse(null) : null;
        if (u == null || u.getEmpresa() == null) return;
        String destino = correoDe(u);
        if (destino == null) return;
        String mensaje = "Hola,\n\nLa contraseña de su usuario " + u.getUsername() + " en Aura se cambió y se "
                + "cerraron las sesiones abiertas.\n\n"
                + "Si no fue usted, avise de inmediato al administrador de su empresa.";
        enviar(u, destino, "Su contraseña de Aura cambió", mensaje, "Aviso de contraseña cambiada", null, null);
    }

    /** Nunca lanza: un fallo de Resend no debe cambiar la respuesta del login. */
    private void enviar(UsuarioEntity u, String destino, String asunto, String mensaje, String registro,
            String botonTexto, String botonUrl) {
        try {
            ResendClient.Resultado r = correos.enviarSistema(u.getEmpresa().getId(), TIPO, u.getId().longValue(),
                    destino, asunto, mensaje, registro, botonTexto, botonUrl, u.getId());
            if (!r.ok()) log.warn("Correo de contraseña al usuario {} no enviado: {}", u.getId(), r.error());
        } catch (Exception ex) {
            log.warn("Correo de contraseña al usuario {} falló: {}", u.getId(), ex.getMessage());
        }
    }

    /** El usuario si es un correo; si no, el correo de su tercero. */
    private static String correoDe(UsuarioEntity u) {
        if (esCorreo(u.getUsername())) return u.getUsername().trim();
        TerceroEntity t = u.getTercero();
        if (t == null) return null;
        if (esCorreo(t.getEmail())) return t.getEmail().trim();
        if (esCorreo(t.getEmailFe())) return t.getEmailFe().trim();
        return null;
    }

    private static boolean esCorreo(String v) {
        return v != null && CORREO.matcher(v.trim()).matches();
    }
}
