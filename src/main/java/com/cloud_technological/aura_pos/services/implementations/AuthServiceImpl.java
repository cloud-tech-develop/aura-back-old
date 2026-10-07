package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.auth.LoginRequestDto;
import com.cloud_technological.aura_pos.services.BodegaService;
import com.cloud_technological.aura_pos.dto.auth.LoginResponseDto;
import com.cloud_technological.aura_pos.dto.auth.RegisterRequestDto;
import com.cloud_technological.aura_pos.dto.auth.SucursalSimpleDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.UsuarioSucursalEntity;
import com.cloud_technological.aura_pos.repositories.auth.AuthQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.UsuarioSucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.security.JwtTokenProvider;
import com.cloud_technological.aura_pos.services.AuthService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import jakarta.transaction.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    /** Toda sucursal nueva nace con su bodega principal (ver V172). */
    @org.springframework.beans.factory.annotation.Autowired
    private BodegaService bodegaService;

    /** Bloqueo por intentos y versión de sesión (PLAN_PERMISOS P10). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.security.SesionService sesiones;

    /** Sedes que puede usar según su perfil (PLAN_PERMISOS P9). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService permisos;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.utils.SecurityUtils securityUtils;

    /** Líneas de uso y tablero de inicio (docs/PLAN_PERFIL_EMPRESA.md). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.empresa.ConfiguracionEmpresaService configuracionEmpresa;

    private final AuthenticationManager authenticationManager;
    private final UsuarioJPARepository usuarioJPARepository;
    private final AuthQueryRepository authQueryRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmpresaJPARepository empresaRepository;
    private final SucursalJPARepository sucursalRepository;
    private final TerceroJPARepository terceroRepository;
    private final UsuarioSucursalJPARepository usuarioSucursalRepository;

    @Autowired
    public AuthServiceImpl(AuthenticationManager authenticationManager,
            UsuarioJPARepository usuarioJPARepository,
            AuthQueryRepository authQueryRepository,
            PasswordEncoder passwordEncoder,
            EmpresaJPARepository empresaRepository,
            SucursalJPARepository sucursalRepository,
            TerceroJPARepository terceroRepository,
            UsuarioSucursalJPARepository usuarioSucursalRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.usuarioJPARepository = usuarioJPARepository;
        this.passwordEncoder = passwordEncoder;
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
        this.terceroRepository = terceroRepository;
        this.usuarioSucursalRepository = usuarioSucursalRepository;
        this.authQueryRepository = authQueryRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional // ¡Crucial! Todo o nada.
    public boolean register(RegisterRequestDto dto) {

        // 1. Validaciones previas
        if (empresaRepository.existsByNit(dto.getNit())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una empresa con ese NIT");
        }
        if (usuarioJPARepository.findByUsername(dto.getEmail()).isPresent()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El email ya está registrado como usuario");
        }

        // 2. Crear Empresa
        EmpresaEntity empresa = EmpresaEntity.builder()
                .nit(dto.getNit())
                .razonSocial(dto.getRazonSocial())
                .activa(true)
                .facturaElectronica(dto.isFacturaElectronica())
                .factusClientId(dto.getFactusClientId())
                .factusClientSecret(dto.getFactusClientSecret())
                .factusUsername(dto.getFactusUsername())
                .factusPassword(dto.getFactusPassword())
                .factusNumberingRangeId(dto.getFactusNumberingRangeId())
                .factusPrefijo(dto.getFactusPrefijo())
                .build();
        empresa = empresaRepository.save(empresa);

        // 3. Crear Sucursal Principal
        SucursalEntity sucursal = SucursalEntity.builder()
                .empresa(empresa)
                .nombre(dto.getNombreSucursal())
                .codigo("001")
                .activa(true)
                .build();
        sucursal = sucursalRepository.save(sucursal);
        bodegaService.crearPrincipal(sucursal);

        // 4. Crear Tercero (Datos personales)
        TerceroEntity tercero = TerceroEntity.builder()
                .empresa(empresa)
                .nombres(dto.getNombres())
                .apellidos(dto.getApellidos())
                .numeroDocumento(dto.getNumeroDocumento())
                .email(dto.getEmail())
                .telefono(dto.getTelefono())
                .direccion(dto.getDireccion())
                .municipio(dto.getMunicipio())
                .esEmpleado(true)
                .activo(true)
                .build();
        tercero = terceroRepository.save(tercero);

        // 5. Crear Usuario (Credenciales)
        UsuarioEntity usuario = UsuarioEntity.builder()
                .empresa(empresa)
                .tercero(tercero)
                .username(dto.getEmail()) // Usamos el email como username
                .password(passwordEncoder.encode(dto.getPassword())) // Encriptamos contraseña
                .rol("SUPER_ADMIN") // Rol inicial
                .activo(true)
                .build();
        usuario = usuarioJPARepository.save(usuario);

        // 6. Asignar Sucursal al Usuario (UsuarioSucursal)
        UsuarioSucursalEntity vinculacion = UsuarioSucursalEntity.builder()
                .usuario(usuario)
                .sucursal(sucursal)
                .esDefault(true) // Es su sede principal
                .activo(true)
                .build();
        usuarioSucursalRepository.save(vinculacion);

        // 7. Arranque contable (PUC + configuración por defecto) al confirmar.
        configuracionEmpresa.inicializar(empresa, java.util.List.of());

        return true;
    }

    @Override
    public LoginResponseDto login(LoginRequestDto loginDto) {
        // 0. Bloqueado por intentos fallidos (P10): ni siquiera se prueba la clave.
        UsuarioEntity candidato = loginDto.getUsername() != null
                ? usuarioJPARepository.findByUsername(loginDto.getUsername()).orElse(null) : null;
        if (candidato != null && sesiones.bloqueadoHasta(candidato) != null) {
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Usuario bloqueado por intentos fallidos. Intente después de las "
                    + sesiones.bloqueadoHasta(candidato).toLocalTime().withNano(0));
        }
        try {
            // 1. Autenticar (Esto valida usuario y contraseña hasheada automáticamente)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 2. Obtener Entidad Usuario (JPA) para datos críticos
            UsuarioEntity usuario = usuarioJPARepository.findByUsername(loginDto.getUsername())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            EmpresaEntity empresa = usuario.getEmpresa();
            if (Boolean.FALSE.equals(usuario.getActivo())) {
                throw new GlobalException(HttpStatus.UNAUTHORIZED, "El usuario está inactivo");
            }
            sesiones.registrarExito(usuario);

            // 3. Obtener Sucursales Permitidas (JDBC - Query optimizada)
            List<SucursalSimpleDto> sucursales = authQueryRepository.findSucursalesByUsuario(usuario.getId());

            // 4. Lógica Multi-Sede: Determinar a qué sucursal entra por defecto
            Long sucursalActualId = null;
            if (!sucursales.isEmpty()) {
                // Buscamos la que tenga esDefault = true, si no, tomamos la primera
                sucursalActualId = sucursales.stream()
                        .filter(s -> Boolean.TRUE.equals(s.getEsDefault()))
                        .map(s -> Long.valueOf(s.getId()))
                        .findFirst()
                        .orElse(Long.valueOf(sucursales.get(0).getId()));
            } else {
                if (!"SUPER_ADMIN".equals(usuario.getRol())
                        && !"PLATFORM_ADMIN".equals(usuario.getRol())) { // ← AGREGAR
                    throw new GlobalException(HttpStatus.FORBIDDEN, "El usuario no tiene sucursales asignadas");
                }
            }
            Integer empresaId = usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null;
            // 5. Generar Token (Incluyendo ID de Empresa y Sucursal Actual)
            String token = jwtTokenProvider.generateToken(
                    authentication.getName(),
                    empresaId,
                    sucursalActualId,
                    usuario.getRol(),
                    Long.valueOf(usuario.getId()),
                    usuario.getTokenVersion()
            );
            // 6. Construir Respuesta
            String nombreCompleto = (usuario.getTercero() != null)
                    ? usuario.getTercero().getNombres() + " " + usuario.getTercero().getApellidos()
                    : usuario.getUsername();

            // Obtener el ID del empleado vinculado (puede ser null)
            Long empleadoId = usuario.getEmpleado() != null ? usuario.getEmpleado().getId() : null;

            return LoginResponseDto.builder()
                    .token(token)
                    .tipoToken("Bearer")
                    .usuarioId(usuario.getId())
                    .empleadoId(empleadoId)
                    .username(usuario.getUsername())
                    .nombreCompleto(nombreCompleto)
                    .logo_url(empresa.getLogoUrl())
                    .rol(usuario.getRol())
                    .facturaElectronica(usuario.getEmpresa().isFacturaElectronica())
                    .sucursales(sucursales) // El front usará esto para pintar el selector de sedes
                    .lineas(empresa != null ? lineasDe(empresa) : null)
                    .inicio(empresa != null ? configuracionEmpresa.inicio(empresa).name() : null)
                    .build();

        } catch (BadCredentialsException e) {
            if (candidato != null && sesiones.registrarFallo(candidato)) {
                throw new GlobalException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas. Por seguridad el usuario quedó"
                        + " bloqueado " + com.cloud_technological.aura_pos.security.SesionService.MINUTOS_BLOQUEO + " minutos");
            }
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        } catch (Exception e) {
            // Loguear error real en consola
            e.printStackTrace();
            if (e instanceof GlobalException) {
                throw e;
            }
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Error en el proceso de login");
        }
    }

    /**
     * Cambia la sede de trabajo (PLAN_PERMISOS P9): el selector de la barra superior
     * pide un token nuevo con la sede elegida. Debe ser una de sus sedes asignadas,
     * o cualquiera de la empresa si su perfil tiene "todas las sedes".
     */
    @Override
    public LoginResponseDto cambiarSede(Long sucursalId) {
        Long usuarioId = securityUtils.getUsuarioId();
        if (usuarioId == null || sucursalId == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique la sede");
        }
        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        Integer empresaId = usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null;
        List<SucursalSimpleDto> sucursales = authQueryRepository.findSucursalesByUsuario(usuario.getId());
        boolean asignada = sucursales.stream().anyMatch(s -> sucursalId.equals(Long.valueOf(s.getId())));
        boolean todas = empresaId != null && permisos.efectivos(usuario.getId()).isTodasLasSedes()
                && authQueryRepository.sucursalDeEmpresa(sucursalId, empresaId);
        if (!asignada && !todas) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "Esa sede no está asignada a su usuario");
        }
        String token = jwtTokenProvider.generateToken(usuario.getUsername(), empresaId, sucursalId, usuario.getRol(),
                Long.valueOf(usuario.getId()), usuario.getTokenVersion());
        return LoginResponseDto.builder()
                .token(token)
                .tipoToken("Bearer")
                .usuarioId(usuario.getId())
                .username(usuario.getUsername())
                .rol(usuario.getRol())
                .sucursales(sucursales)
                .lineas(usuario.getEmpresa() != null ? lineasDe(usuario.getEmpresa()) : null)
                .inicio(usuario.getEmpresa() != null ? configuracionEmpresa.inicio(usuario.getEmpresa()).name() : null)
                .build();
    }

    private List<String> lineasDe(EmpresaEntity empresa) {
        return configuracionEmpresa.lineas(empresa).stream().map(Enum::name).toList();
    }
}
