package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.auth.LoginRequestDto;
import com.cloud_technological.aura_pos.dto.auth.LoginResponseDto;
import com.cloud_technological.aura_pos.dto.auth.RegisterRequestDto;

public interface AuthService {
    LoginResponseDto login(LoginRequestDto loginDto);
    boolean register(RegisterRequestDto registerDto);
    /** Token nuevo con otra de sus sedes (PLAN_PERMISOS P9). */
    LoginResponseDto cambiarSede(Long sucursalId);

    /** Empresas en las que puede trabajar: la suya y, si es una firma contable, sus clientes. */
    java.util.List<LoginResponseDto.EmpresaOpcionDto> empresasDisponibles();

    /** Elige (o cambia) la empresa de trabajo y devuelve el token para ella (PLAN_FIRMA_CONTABLE F3). */
    LoginResponseDto seleccionarEmpresa(Integer empresaId);
}
