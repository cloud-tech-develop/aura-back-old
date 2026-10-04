package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioFromEmpleadoDto;
import com.cloud_technological.aura_pos.dto.usuarios.UpdateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioTableDto;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TipoEmpleadoEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.UsuarioSucursalEntity;
import com.cloud_technological.aura_pos.mappers.UsuarioMapper;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.UsuarioSucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.tipo_empleado.TipoEmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioQueryRepository;
import com.cloud_technological.aura_pos.services.UsuarioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    /** Perfil de permisos al crear o editar (docs/PLAN_PERMISOS.md, fase P1). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.PerfilService perfilService;

    /** Cerrar sesiones al desactivar o cambiar la clave o las sedes (PLAN_PERMISOS P10). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.security.SesionService sesiones;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.BitacoraService bitacora;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService permisos;

    private final UsuarioJPARepository usuarioRepo;
    private final UsuarioSucursalJPARepository usuarioSucursalRepo;
    private final SucursalJPARepository sucursalRepo;
    private final TerceroJPARepository terceroRepo;
    private final UsuarioQueryRepository queryRepo;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final EmpleadoJPARepository empleadoRepo;
    private final TipoEmpleadoJPARepository tipoEmpleadoRepo;
    private final SecurityUtils securityUtils;

    public UsuarioServiceImpl(UsuarioJPARepository usuarioRepo,
            UsuarioSucursalJPARepository usuarioSucursalRepo,
            SucursalJPARepository sucursalRepo,
            TerceroJPARepository terceroRepo,
            UsuarioQueryRepository queryRepo,
            PasswordEncoder passwordEncoder,
            UsuarioMapper usuarioMapper,
            EmpleadoJPARepository empleadoRepo,
            TipoEmpleadoJPARepository tipoEmpleadoRepo,
            SecurityUtils securityUtils) {
        this.usuarioRepo = usuarioRepo;
        this.usuarioSucursalRepo = usuarioSucursalRepo;
        this.sucursalRepo = sucursalRepo;
        this.terceroRepo = terceroRepo;
        this.queryRepo = queryRepo;
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
        this.empleadoRepo = empleadoRepo;
        this.tipoEmpleadoRepo = tipoEmpleadoRepo;
        this.securityUtils = securityUtils;
    }

    @Override
    public PageImpl<UsuarioTableDto> paginar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepo.paginar(pageable, empresaId);
    }

    @Override
    public UsuarioDto obtenerPorId(Integer id, Integer empresaId) {
        UsuarioEntity entity = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return mapToDtoCompleto(entity);
    }

    @Override
    @Transactional
    public UsuarioDto crear(CreateUsuarioDto dto, Integer empresaId) {
        // La persona es un tercero que ya existe (V192): no se crea uno nuevo por
        // cada usuario, así queda relacionado con el empleado, vendedor o cliente.
        TerceroEntity tercero = terceroDeLaEmpresa(dto.getTerceroId(), empresaId, null);
        String username = dto.getUsername() != null && !dto.getUsername().isBlank()
                ? dto.getUsername().trim() : tercero.getEmail();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Escriba el usuario de acceso (el tercero no tiene correo)");
        }
        if (usuarioRepo.existsByUsername(username)) {
            throw new IllegalArgumentException("El username ya está en uso");
        }

        UsuarioEntity usuario = usuarioMapper.toEntity(dto);
        usuario.setRol(PoliticaRoles.validarAsignacion(null, dto.getRol(), securityUtils.getRol()));
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        usuario.setPinAccesoRapido(null);
        if (dto.getPinAccesoRapido() != null) {
            usuario.setPinAccesoRapido(passwordEncoder.encode(dto.getPinAccesoRapido()));
        }

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        usuario.setEmpresa(empresa);
        usuario.setTercero(tercero);

        usuario.setPerfilId(null);
        usuario = usuarioRepo.save(usuario);
        asignarPerfil(usuario, empresaId, dto.getPerfilId(), null, null);

        asignarSucursales(usuario, dto.getSucursales());

        return mapToDtoCompleto(usuario);
    }

    @Override
    @Transactional
    public UsuarioDto actualizar(Integer id, UpdateUsuarioDto dto, Integer empresaId) {
        UsuarioEntity usuario = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        // Campo por campo, sin el mapper: copiaba el PIN sin cifrar (o lo borraba
        // si no venía) y ponía de username el correo.
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            String username = dto.getUsername().trim();
            if (usuarioRepo.existsByUsernameAndIdNot(username, id)) {
                throw new IllegalArgumentException("El username ya está en uso");
            }
            usuario.setUsername(username);
        }

        boolean claveNueva = dto.getPassword() != null && !dto.getPassword().isBlank();
        if (claveNueva) {
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        if (dto.getPinAccesoRapido() != null && !dto.getPinAccesoRapido().isBlank()) {
            usuario.setPinAccesoRapido(passwordEncoder.encode(dto.getPinAccesoRapido()));
        }

        boolean desactivado = false;
        if (dto.getActivo() != null) {
            desactivado = Boolean.TRUE.equals(usuario.getActivo()) && !dto.getActivo();
            usuario.setActivo(dto.getActivo());
        }

        // La persona: otro tercero de la empresa que no tenga ya usuario.
        if (dto.getTerceroId() != null
                && (usuario.getTercero() == null || !dto.getTerceroId().equals(usuario.getTercero().getId()))) {
            usuario.setTercero(terceroDeLaEmpresa(dto.getTerceroId(), empresaId, id));
        }

        // Se deja el rol que permita la política (ver PoliticaRoles).
        String rolAnterior = usuario.getRol();
        Long perfilAnterior = usuario.getPerfilId();
        if (dto.getRol() != null) {
            usuario.setRol(PoliticaRoles.validarAsignacion(rolAnterior, dto.getRol(), securityUtils.getRol()));
        }
        asignarPerfil(usuario, empresaId, dto.getPerfilId(), rolAnterior, perfilAnterior);

        boolean cambioSedes = false;
        if (dto.getSucursales() != null) {
            permisos.invalidarUsuario(id);
            java.util.Set<Long> antes = new java.util.TreeSet<>(permisos.efectivos(id).getSucursales());
            usuarioSucursalRepo.deleteAllByUsuarioId(id);
            asignarSucursales(usuario, dto.getSucursales());
            java.util.Set<Long> despues = new java.util.TreeSet<>();
            for (CreateUsuarioDto.SucursalAsignacion a : dto.getSucursales()) {
                if (a.getSucursalId() != null) despues.add(a.getSucursalId().longValue());
            }
            cambioSedes = !antes.equals(despues);
            permisos.invalidarUsuario(id);
            if (cambioSedes) {
                bitacora.registrar("caja.usuarios", "CAMBIO_SEDES", "usuario", id,
                        "Cambió las sedes de " + usuario.getUsername(), antes, despues);
            }
        }

        UsuarioEntity guardado = usuarioRepo.save(usuario);
        if (claveNueva) {
            bitacora.registrar("caja.usuarios", "CAMBIO_CLAVE", "usuario", id,
                    "Cambió la clave de " + usuario.getUsername() + " (se cerraron sus sesiones)", null, null);
        }
        if (desactivado) {
            bitacora.registrar("caja.usuarios", "ANULAR", "usuario", id,
                    "Desactivó el usuario " + usuario.getUsername() + " (se cerraron sus sesiones)", null, null);
        }
        // Clave nueva, sedes distintas o desactivado: las sesiones abiertas dejan de servir.
        if (claveNueva || cambioSedes || desactivado) sesiones.revocar(guardado);
        return mapToDtoCompleto(guardado);
    }

    /**
     * El tercero del usuario: de la misma empresa y sin otro usuario (una persona,
     * un usuario). Se responde "no encontrado" si es de otra empresa.
     */
    private TerceroEntity terceroDeLaEmpresa(Long terceroId, Integer empresaId, Integer exceptoUsuarioId) {
        if (terceroId == null) {
            throw new IllegalArgumentException("Elija el tercero del usuario");
        }
        TerceroEntity tercero = terceroRepo.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Tercero no encontrado"));
        String otro = queryRepo.usuarioDelTercero(terceroId, exceptoUsuarioId);
        if (otro != null) {
            throw new IllegalArgumentException("Ese tercero ya tiene el usuario '" + otro + "'");
        }
        return tercero;
    }

    @Override
    @Transactional
    public UsuarioDto actualizarPropio(Integer id, UpdateUsuarioDto dto, Integer empresaId) {
        UsuarioEntity usuario = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        boolean claveNueva = dto.getPassword() != null && !dto.getPassword().isBlank();
        if (claveNueva) {
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getPinAccesoRapido() != null && !dto.getPinAccesoRapido().isBlank()) {
            usuario.setPinAccesoRapido(passwordEncoder.encode(dto.getPinAccesoRapido()));
        }

        // Solo contacto en el tercero; el mapper de usuario no se usa porque
        // pondría en null (o cambiaría) el rol, el estado y el username.
        TerceroEntity tercero = usuario.getTercero();
        usuarioMapper.updateTerceroFromUpdateDto(dto, tercero);
        terceroRepo.save(tercero);

        UsuarioEntity guardado = usuarioRepo.save(usuario);
        if (claveNueva) {
            bitacora.registrar("caja.usuarios", "CAMBIO_CLAVE", "usuario", id,
                    usuario.getUsername() + " cambió su propia clave", null, null);
            // Cierra las sesiones en otros equipos; el front vuelve a pedir el ingreso.
            sesiones.revocar(guardado);
        }
        return mapToDtoCompleto(guardado);
    }

    @Override
    @Transactional
    public void desactivar(Integer id, Integer empresaId) {
        UsuarioEntity usuario = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        usuario.setActivo(false);
        sesiones.revocar(usuario);
        bitacora.registrar("caja.usuarios", "ANULAR", "usuario", id,
                "Desactivó el usuario " + usuario.getUsername() + " (se cerraron sus sesiones)", null, null);
    }

    @Override
    @Transactional
    public void cerrarSesiones(Integer id, Integer empresaId) {
        UsuarioEntity usuario = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        sesiones.revocar(usuario);
        bitacora.registrar("caja.usuarios", "CERRAR_SESIONES", "usuario", id,
                "Cerró las sesiones abiertas de " + usuario.getUsername(), null, null);
    }

    private void asignarSucursales(UsuarioEntity usuario,
            List<CreateUsuarioDto.SucursalAsignacion> asignaciones) {
        if (asignaciones == null || asignaciones.isEmpty()) {
            return;
        }

        boolean hayDefault = asignaciones.stream().anyMatch(a -> Boolean.TRUE.equals(a.getEsDefault()));

        for (int i = 0; i < asignaciones.size(); i++) {
            CreateUsuarioDto.SucursalAsignacion asig = asignaciones.get(i);

            SucursalEntity sucursal = sucursalDeLaEmpresa(asig.getSucursalId(), usuario);

            boolean esDefault = hayDefault
                    ? Boolean.TRUE.equals(asig.getEsDefault())
                    : i == 0;

            UsuarioSucursalEntity us = UsuarioSucursalEntity.builder()
                    .usuario(usuario)
                    .sucursal(sucursal)
                    .esDefault(esDefault)
                    .activo(true)
                    .build();

            usuarioSucursalRepo.save(us);
        }
    }

    /**
     * La sucursal debe ser de la misma empresa del usuario: la asignada termina
     * en el token al iniciar sesión, y varios módulos filtran solo por sucursal.
     * Si fuera de otra empresa, el usuario leería datos ajenos. Se responde
     * "no encontrada" para no revelar que existe en otra empresa.
     */
    private SucursalEntity sucursalDeLaEmpresa(Integer sucursalId, UsuarioEntity usuario) {
        SucursalEntity sucursal = sucursalRepo.findById(sucursalId)
                .orElseThrow(() -> new EntityNotFoundException("Sucursal no encontrada: " + sucursalId));
        Integer empresaUsuario = usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null;
        Integer empresaSucursal = sucursal.getEmpresa() != null ? sucursal.getEmpresa().getId() : null;
        if (empresaUsuario == null || !empresaUsuario.equals(empresaSucursal)) {
            throw new EntityNotFoundException("Sucursal no encontrada: " + sucursalId);
        }
        return sucursal;
    }

    private UsuarioDto mapToDtoCompleto(UsuarioEntity entity) {
        UsuarioDto dto = usuarioMapper.toDto(entity);
        dto.setTerceroId(entity.getTercero() != null ? entity.getTercero().getId() : null);
        dto.setSucursales(queryRepo.sucursalesDeUsuario(entity.getId()));
        dto.setPerfilId(entity.getPerfilId());
        dto.setPerfilNombre(queryRepo.nombrePerfil(entity.getPerfilId()));
        return dto;
    }

    /**
     * Valida y guarda el perfil de permisos: el elegido (si quien edita puede
     * darlo) o, sin elegir, el de sistema del rol. Deja constancia del cambio.
     */
    private void asignarPerfil(UsuarioEntity usuario, Integer empresaId, Long solicitado, String rolAnterior,
            Long perfilAnterior) {
        Long editor = securityUtils.getUsuarioId();
        Integer editorId = editor != null ? editor.intValue() : null;
        Long nuevo = perfilService.perfilParaAsignar(empresaId, editorId, solicitado, usuario.getRol(),
                rolAnterior, perfilAnterior);
        usuario.setPerfilId(nuevo);
        usuarioRepo.save(usuario);
        perfilService.registrarAsignacion(empresaId, editorId, usuario.getId(), perfilAnterior, nuevo);
    }

    @Override
    @Transactional
    public UsuarioDto crearDesdeEmpleado(CreateUsuarioFromEmpleadoDto dto, Integer empresaId) {
        // 1. Validar que el empleado existe y pertenece a la empresa
        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado"));

        // 2. Validar que el username no esté en uso
        if (usuarioRepo.existsByUsername(dto.getUsername())) {
            throw new GlobalException(org.springframework.http.HttpStatus.BAD_REQUEST, "El username ya está en uso");
        }

        // 3. Buscar el tipo de empleado por nombre del cargo
        // Buscar primero en los tipos de empleado de la empresa
        String cargoEmpleado = empleado.getCargo();
        List<TipoEmpleadoEntity> tiposEncontrados = tipoEmpleadoRepo.findByEmpresaIdAndActivoTrue((long) empresaId);

        TipoEmpleadoEntity tipoEmpleado = null;
        for (TipoEmpleadoEntity te : tiposEncontrados) {
            if (te.getNombre().equalsIgnoreCase(cargoEmpleado)) {
                tipoEmpleado = te;
                break;
            }
        }

        // Si no se encontró por nombre, buscar los tipos de empleado generales (empresa_id = 1)
        if (tipoEmpleado == null) {
            List<TipoEmpleadoEntity> tiposGenerales = tipoEmpleadoRepo.findByEmpresaIdAndActivoTrue(1L);
            for (TipoEmpleadoEntity te : tiposGenerales) {
                if (te.getNombre().equalsIgnoreCase(cargoEmpleado)) {
                    tipoEmpleado = te;
                    break;
                }
            }
        }

        // 4. Crear el usuario
        UsuarioEntity usuario = UsuarioEntity.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .empleado(empleado)
                .tipoEmpleado(tipoEmpleado)
                .rol(PoliticaRoles.validarAsignacion(null,
                        tipoEmpleado != null ? tipoEmpleado.getNombre() : cargoEmpleado,
                        securityUtils.getRol()))
                .activo(true)
                .build();

        // Asignar empresa
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        usuario.setEmpresa(empresa);

        // El usuario queda ligado a la persona del empleado (su tercero), con la
        // misma regla que el alta normal: un tercero, un usuario (V192).
        if (empleado.getTercero() == null) {
            throw new IllegalArgumentException(
                    "El empleado no tiene tercero: complételo en Empleados antes de crearle usuario");
        }
        usuario.setTercero(terceroDeLaEmpresa(empleado.getTercero().getId(), empresaId, null));

        usuario = usuarioRepo.save(usuario);
        asignarPerfil(usuario, empresaId, dto.getPerfilId(), null, null);

        // 5. Asignar la sucursal al usuario
        asignarSucursalesDesdeCreateEmpleado(usuario, dto.getSucursalId());

        return mapToDtoCompleto(usuario);
    }

    /**
     * Asigna una sucursal al usuario creado desde empleado.
     * La sucursal se marca como default ya que es la única asignada.
     */
    private void asignarSucursalesDesdeCreateEmpleado(UsuarioEntity usuario, Integer sucursalId) {
        if (sucursalId == null) return;

        SucursalEntity sucursal = sucursalDeLaEmpresa(sucursalId, usuario);

        UsuarioSucursalEntity us = UsuarioSucursalEntity.builder()
                .usuario(usuario)
                .sucursal(sucursal)
                .esDefault(true) // Es la única sucursal, se marca como default
                .activo(true)
                .build();

        usuarioSucursalRepo.save(us);
    }
}
