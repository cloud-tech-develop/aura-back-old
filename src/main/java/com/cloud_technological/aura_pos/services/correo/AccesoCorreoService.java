package com.cloud_technological.aura_pos.services.correo;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PasswordResetTokenEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.PasswordResetTokenRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Acceso del administrador de una empresa nueva por correo
 * (docs/PLAN_CUENTA_COBRO_CORREO.md §7): al crearla en la plataforma le llega su
 * usuario, la clave temporal y el enlace para poner la suya; "Reenviar acceso" le
 * manda un enlace nuevo. Sale como "Aura" desde no-reply. La clave temporal va solo
 * en el correo: en el historial no se guarda.
 */
@Service
@RequiredArgsConstructor
public class AccesoCorreoService {

    /** Horas que dura el enlace para crear la contraseña. */
    private static final int HORAS_ENLACE = 72;

    private final CorreoService correos;
    private final EmpresaJPARepository empresaRepo;
    private final UsuarioJPARepository usuarioRepo;
    private final PasswordResetTokenRepository tokenRepo;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Resultado del envío del acceso.
     *
     * @param enlace el enlace para crear la contraseña (por si hay que pasarlo a mano)
     */
    public record EnvioAcceso(String email, boolean enviado, String error, String enlace) {
    }

    /** Correo de bienvenida con usuario, clave temporal y enlace. Nunca lanza: devuelve si se envió. */
    public EnvioAcceso enviarAccesoInicial(Integer empresaId, String email, String claveTemporal, String enlace,
            Integer actorId) {
        String empresa = empresaRepo.findById(empresaId).map(this::nombre).orElse("su empresa");
        String mensaje = "Hola,\n\nSe creó la cuenta de " + empresa + " en Aura y usted es su administrador.\n\n"
                + "Usuario: " + email + "\nClave temporal: " + claveTemporal + "\n\n"
                + "Por seguridad, cree su propia contraseña con el botón de abajo. El enlace vence en " + HORAS_ENLACE
                + " horas; después puede entrar con la clave temporal y cambiarla.\n\nBienvenido a Aura.";
        return enviar(empresaId, email, "Bienvenido a Aura - acceso de " + empresa, mensaje,
                "Acceso inicial del administrador (la clave temporal no se guarda)", enlace, actorId);
    }

    /**
     * Genera un enlace nuevo para que el administrador (SUPER_ADMIN) cree su
     * contraseña y se lo envía. Los enlaces anteriores siguen sirviendo hasta vencer.
     */
    @Transactional
    public EnvioAcceso reenviarAcceso(Integer empresaId, Integer actorId) {
        EmpresaEntity e = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        UsuarioEntity admin = usuarioRepo.findSuperAdminByEmpresaId(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La empresa no tiene un usuario administrador principal"));
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenRepo.save(PasswordResetTokenEntity.builder()
                .usuario(admin)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(HORAS_ENLACE))
                .usado(false)
                .build());
        String enlace = frontendUrl + "/reset-password?token=" + token;
        String mensaje = "Hola,\n\nEste es su acceso a Aura como administrador de " + nombre(e) + ".\n\n"
                + "Usuario: " + admin.getUsername() + "\n\nCree su contraseña con el botón de abajo. El enlace vence en "
                + HORAS_ENLACE + " horas.";
        return enviar(empresaId, admin.getUsername(), "Su acceso a Aura - " + nombre(e), mensaje,
                "Acceso reenviado al administrador", enlace, actorId);
    }

    private EnvioAcceso enviar(Integer empresaId, String email, String asunto, String mensaje, String registro,
            String enlace, Integer actorId) {
        try {
            ResendClient.Resultado r = correos.enviarSistema(empresaId, CorreoService.ACCESO, empresaId.longValue(), email,
                    asunto, mensaje, registro, "Crear mi contraseña", enlace, actorId);
            return new EnvioAcceso(email, r.ok(), r.ok() ? null : r.error(), enlace);
        } catch (Exception ex) {
            return new EnvioAcceso(email, false, ex.getMessage(), enlace);
        }
    }

    private String nombre(EmpresaEntity e) {
        return e.getNombreComercial() != null && !e.getNombreComercial().isBlank() ? e.getNombreComercial()
                : e.getRazonSocial();
    }
}
