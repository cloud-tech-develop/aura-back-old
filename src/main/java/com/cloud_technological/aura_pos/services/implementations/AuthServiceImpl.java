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

    /** Firma contable: clientes del usuario y su acceso (docs/PLAN_FIRMA_CONTABLE.md). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository firmaQuery;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.BitacoraService bitacora;

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
            if (Boolean.FALSE.equals(usuario.getActivo())) {
                throw new GlobalException(HttpStatus.UNAUTHORIZED, "El usuario está inactivo");
            }
            sesiones.registrarExito(usuario);

            // 2.1 Firma contable con clientes asignados: primero elige dónde trabajar
            //     (docs/PLAN_FIRMA_CONTABLE.md). El token de paso solo sirve para eso.
            List<LoginResponseDto.EmpresaOpcionDto> opciones = empresasDe(usuario);
            if (opciones.size() > 1) {
                return LoginResponseDto.builder()
                        .token(jwtTokenProvider.generateTokenSeleccion(usuario.getUsername(),
                                Long.valueOf(usuario.getId()), usuario.getTokenVersion()))
                        .tipoToken("Bearer")
                        .usuarioId(usuario.getId())
                        .username(usuario.getUsername())
                        .nombreCompleto(nombreDe(usuario))
                        .requiereEmpresa(true)
                        .empresas(opciones)
                        .build();
            }
            return respuestaPropia(usuario, false);

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

    /** Login en su propia empresa (lo de siempre). */
    private LoginResponseDto respuestaPropia(UsuarioEntity usuario, boolean puedeCambiar) {
            EmpresaEntity empresa = usuario.getEmpresa();

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
                    usuario.getUsername(),
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
                    .empresaId(empresaId)
                    .empresaNombre(empresa != null ? empresa.getRazonSocial() : null)
                    .empresaNit(empresa != null ? empresa.getNit() : null)
                    .puedeCambiarEmpresa(puedeCambiar)
                    .esFirmaContable(empresa != null && Boolean.TRUE.equals(empresa.getEsFirmaContable()))
                    .build();
    }

    // ── Firma contable (docs/PLAN_FIRMA_CONTABLE.md, F3) ─────────────────────

    /** Su empresa y, si es una firma contable, los clientes que tiene asignados. */
    private List<LoginResponseDto.EmpresaOpcionDto> empresasDe(UsuarioEntity usuario) {
        List<LoginResponseDto.EmpresaOpcionDto> out = new java.util.ArrayList<>();
        EmpresaEntity propia = usuario.getEmpresa();
        if (propia == null) return out;
        out.add(LoginResponseDto.EmpresaOpcionDto.builder()
                .id(propia.getId()).razonSocial(propia.getRazonSocial()).nombreComercial(propia.getNombreComercial())
                .nit(propia.getNit()).dv(propia.getDv()).logoUrl(propia.getLogoUrl()).propia(true).build());
        if (!Boolean.TRUE.equals(propia.getEsFirmaContable())) return out;
        for (var c : firmaQuery.clientesDelUsuario(usuario.getId())) {
            out.add(LoginResponseDto.EmpresaOpcionDto.builder()
                    .id(c.id()).razonSocial(c.razonSocial()).nombreComercial(c.nombreComercial())
                    .nit(c.nit()).dv(c.dv()).logoUrl(c.logoUrl()).propia(false).build());
        }
        return out;
    }

    @Override
    public List<LoginResponseDto.EmpresaOpcionDto> empresasDisponibles() {
        return empresasDe(usuarioDeLaSesion());
    }

    /**
     * Elige la empresa en la que va a trabajar: tras el login (token de paso) o desde
     * "Cambiar empresa". Su propia empresa = login normal; un cliente = solo con el
     * acceso vigente de su firma, rol CONTADOR_EXTERNO y queda en la bitácora del cliente.
     */
    @Override
    public LoginResponseDto seleccionarEmpresa(Integer empresaId) {
        if (empresaId == null) throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija la empresa");
        UsuarioEntity usuario = usuarioDeLaSesion();
        boolean varias = empresasDe(usuario).size() > 1;
        if (usuario.getEmpresa() != null && empresaId.equals(usuario.getEmpresa().getId())) {
            return respuestaPropia(usuario, varias);
        }
        var acceso = firmaQuery.acceso(usuario.getId(), empresaId);
        if (acceso == null) throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene acceso a esa empresa");
        LoginResponseDto r = respuestaCliente(usuario, acceso, null);
        bitacora.registrarEnEmpresa(empresaId, "caja.usuarios", "INGRESO_FIRMA", "usuario", usuario.getId(),
                "Ingresó " + r.getNombreCompleto() + " (" + r.getFirmaNombre() + ")", null, null);
        return r;
    }

    /** Token y datos de un usuario de la firma trabajando en el cliente; {@code sedeId} null = la principal. */
    private LoginResponseDto respuestaCliente(UsuarioEntity usuario,
            com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.AccesoCliente acceso,
            Long sedeId) {
        EmpresaEntity cliente = empresaRepository.findById(acceso.clienteEmpresaId())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        List<SucursalSimpleDto> sedes = authQueryRepository.sucursalesDeEmpresa(cliente.getId());
        if (sedes.isEmpty())
            throw new GlobalException(HttpStatus.CONFLICT, cliente.getRazonSocial() + " no tiene sedes activas");
        Long sede = sedeId;
        if (sede == null) {
            sede = sedes.stream().filter(x -> Boolean.TRUE.equals(x.getEsDefault()))
                    .map(x -> Long.valueOf(x.getId())).findFirst().orElse(Long.valueOf(sedes.get(0).getId()));
        }
        final Long elegida = sede;
        sedes.forEach(x -> x.setEsDefault(elegida.equals(Long.valueOf(x.getId()))));

        // En un cliente gestionado (V200) el administrador de la firma entra como ADMIN.
        String rol = acceso.administraElCliente() ? "ADMIN"
                : com.cloud_technological.aura_pos.services.permisos.PerfilesSistema.CONTADOR_EXTERNO;
        String token = jwtTokenProvider.generateTokenCliente(usuario.getUsername(), cliente.getId(), sede, rol,
                Long.valueOf(usuario.getId()), usuario.getTokenVersion(), acceso.vinculoId(), acceso.firmaEmpresaId());
        EmpresaEntity firma = usuario.getEmpresa();
        return LoginResponseDto.builder()
                .token(token)
                .tipoToken("Bearer")
                .usuarioId(usuario.getId())
                .username(usuario.getUsername())
                .nombreCompleto(nombreDe(usuario))
                .logo_url(cliente.getLogoUrl())
                .rol(rol)
                .facturaElectronica(cliente.isFacturaElectronica())
                .sucursales(sedes)
                .lineas(lineasDe(cliente))
                .inicio(configuracionEmpresa.inicio(cliente).name())
                .empresaId(cliente.getId())
                .empresaNombre(cliente.getRazonSocial())
                .empresaNit(cliente.getNit())
                .firmaNombre(firma != null ? firma.getRazonSocial() : null)
                .puedeCambiarEmpresa(true)
                .build();
    }

    private UsuarioEntity usuarioDeLaSesion() {
        Long usuarioId = securityUtils.getUsuarioId();
        if (usuarioId == null) throw new GlobalException(HttpStatus.UNAUTHORIZED, "Inicie sesión");
        UsuarioEntity u = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (Boolean.FALSE.equals(u.getActivo()))
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "El usuario está inactivo");
        return u;
    }

    private static String nombreDe(UsuarioEntity usuario) {
        return usuario.getTercero() != null
                ? usuario.getTercero().getNombres() + " " + usuario.getTercero().getApellidos()
                : usuario.getUsername();
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
        // Usuario de una firma en un cliente: cualquier sede del cliente, con el acceso vigente.
        if (securityUtils.getVinculoId() != null) {
            var acceso = firmaQuery.acceso(usuario.getId(), securityUtils.getEmpresaId());
            if (acceso == null) throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene acceso a esa empresa");
            if (!authQueryRepository.sucursalDeEmpresa(sucursalId, acceso.clienteEmpresaId()))
                throw new GlobalException(HttpStatus.FORBIDDEN, "Esa sede no es de la empresa");
            return respuestaCliente(usuario, acceso, sucursalId);
        }
        // La empresa es la de la sesión, no la del usuario: un usuario de una
        // firma contable trabaja en empresas cliente (docs/PLAN_FIRMA_CONTABLE.md).
        Integer empresaId = securityUtils.getEmpresaId();
        EmpresaEntity empresa = empresaId == null ? null
                : usuario.getEmpresa() != null && empresaId.equals(usuario.getEmpresa().getId())
                        ? usuario.getEmpresa()
                        : empresaRepository.findById(empresaId).orElse(null);
        List<SucursalSimpleDto> sucursales = authQueryRepository.findSucursalesByUsuario(usuario.getId());
        // La sede siempre tiene que ser de la empresa de la sesión: una sede
        // asignada de otra empresa dejaría un token mezclado.
        boolean deLaEmpresa = empresaId != null && authQueryRepository.sucursalDeEmpresa(sucursalId, empresaId);
        boolean asignada = sucursales.stream().anyMatch(s -> sucursalId.equals(Long.valueOf(s.getId())));
        boolean todas = permisos.efectivos(usuario.getId()).isTodasLasSedes();
        if (!deLaEmpresa || (!asignada && !todas)) {
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
                .lineas(empresa != null ? lineasDe(empresa) : null)
                .inicio(empresa != null ? configuracionEmpresa.inicio(empresa).name() : null)
                .build();
    }

    private List<String> lineasDe(EmpresaEntity empresa) {
        return configuracionEmpresa.lineas(empresa).stream().map(Enum::name).toList();
    }
}
