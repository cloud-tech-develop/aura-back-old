package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDateTime;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.auth.LoginRequestDto;
import com.cloud_technological.aura_pos.dto.auth.LoginResponseDto;
import com.cloud_technological.aura_pos.dto.auth.RegisterRequestDto;
import com.cloud_technological.aura_pos.dto.auth.ResetPasswordDto;
import com.cloud_technological.aura_pos.entity.PasswordResetTokenEntity;
import com.cloud_technological.aura_pos.repositories.users.PasswordResetTokenRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.AuthService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.BusinessException;
import com.cloud_technological.aura_pos.utils.GlobalException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private UsuarioJPARepository usuarioRepo;
    @Autowired private PasswordResetTokenRepository tokenRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private com.cloud_technological.aura_pos.security.SesionService sesiones;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto loginRequest) throws BusinessException {
        LoginResponseDto responseDto = authService.login(loginRequest);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Login exitoso", false, responseDto));
    }

    /** Cambia la sede de trabajo: devuelve un token nuevo con la sede elegida (PLAN_PERMISOS P9). */
    @PostMapping("/cambiar-sede")
    public ResponseEntity<ApiResponse<LoginResponseDto>> cambiarSede(@RequestBody java.util.Map<String, Long> body) {
        LoginResponseDto r = authService.cambiarSede(body != null ? body.get("sucursalId") : null);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Sede cambiada", false, r));
    }

    /**
     * Elige la empresa de trabajo: tras el login de quien tiene varias (token de paso)
     * o desde "Cambiar empresa". Devuelve el token para esa empresa.
     */
    @PostMapping("/seleccionar-empresa")
    public ResponseEntity<ApiResponse<LoginResponseDto>> seleccionarEmpresa(@RequestBody java.util.Map<String, Integer> body) {
        LoginResponseDto r = authService.seleccionarEmpresa(body != null ? body.get("empresaId") : null);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Empresa seleccionada", false, r));
    }

    /** Empresas en las que puede trabajar (para "Cambiar empresa"). */
    @org.springframework.web.bind.annotation.GetMapping("/empresas")
    public ResponseEntity<ApiResponse<java.util.List<LoginResponseDto.EmpresaOpcionDto>>> empresas() {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Empresas", false,
                authService.empresasDisponibles()));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> register(@Valid @RequestBody RegisterRequestDto dto) {
        boolean success = authService.register(dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Empresa y Usuario creados exitosamente", false, success),
                HttpStatus.CREATED);
    }

    // ── Cambiar contraseña con token ──────────────────────────────
    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordDto dto) {
        PasswordResetTokenEntity resetToken = tokenRepo.findByToken(dto.getToken())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Token inválido o expirado"));

        if (resetToken.getUsado() || resetToken.isExpired()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Token inválido o expirado");
        }

        resetToken.getUsuario().setPassword(passwordEncoder.encode(dto.getNuevaPassword()));
        // Clave nueva: se desbloquea y se cierran las sesiones abiertas (PLAN_PERMISOS P10).
        resetToken.getUsuario().setIntentosFallidos(0);
        resetToken.getUsuario().setBloqueadoHasta(null);
        sesiones.revocar(resetToken.getUsuario());

        resetToken.setUsado(true);
        tokenRepo.save(resetToken);

        return ResponseEntity.ok(new ApiResponse<>(200, "Contraseña actualizada correctamente", false, null));
    }
}
