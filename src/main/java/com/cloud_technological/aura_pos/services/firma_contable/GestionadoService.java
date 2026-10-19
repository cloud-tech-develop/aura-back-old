package com.cloud_technological.aura_pos.services.firma_contable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.firma_contable.CrearGestionadoDto;
import com.cloud_technological.aura_pos.dto.firma_contable.IdentidadGestionadoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaFirmaClienteEntity;
import com.cloud_technological.aura_pos.entity.PerfilEntity;
import com.cloud_technological.aura_pos.entity.PerfilPermisoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.UsuarioSucursalEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.VinculoFila;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilPermisoJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.SubmoduloHabilitado;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.UsuarioSucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.BodegaService;
import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.services.empresa.ConfiguracionEmpresaService;
import com.cloud_technological.aura_pos.services.empresa.LineaUso;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.services.permisos.PerfilesSistemaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Clientes gestionados: empresas que crea una firma contable para llevarles la
 * contabilidad sin que el cliente use Aura (docs/PLAN_FIRMA_CONTABLE.md §9). La firma
 * paga por contrato aparte; tiene un cupo (10 al inicio). No llevan administrador
 * propio: los administradores de la firma entran como ADMIN. Módulos: solo los de
 * {@link PaqueteGestionado} (nunca POS, caja ni inventario).
 */
@Service
@RequiredArgsConstructor
public class GestionadoService {

    /** Perfil de solo lectura para el usuario de consulta del cliente. */
    public static final String PERFIL_CONSULTA = "CONSULTA";
    private static final String CLAVE_BITACORA = "caja.usuarios";

    private final EmpresaJPARepository empresaRepo;
    private final SucursalJPARepository sucursalRepo;
    private final BodegaService bodegaService;
    private final TerceroJPARepository terceroRepo;
    private final UsuarioJPARepository usuarioRepo;
    private final UsuarioSucursalJPARepository usuarioSucursalRepo;
    private final PasswordEncoder passwordEncoder;
    private final PermisoService permisoService;
    private final ConfiguracionEmpresaService configuracion;
    private final PerfilesSistemaService perfilesSistema;
    private final PerfilJPARepository perfilRepo;
    private final PerfilPermisoJPARepository perfilPermisoRepo;
    private final PermisoUsuarioQueryRepository permisoQuery;
    private final FirmaContableService firmaContable;
    private final FirmaContableQueryRepository query;
    private final BitacoraService bitacora;

    public record PaqueteDto(String codigo, String nombre, String descripcion) {
    }

    public record ResumenFirma(int cupo, int gestionados, List<VinculoFila> clientes) {
    }

    public List<PaqueteDto> paquetes() {
        List<PaqueteDto> out = new ArrayList<>();
        for (PaqueteGestionado p : PaqueteGestionado.values())
            out.add(new PaqueteDto(p.name(), p.getNombre(), p.getDescripcion()));
        return out;
    }

    /** Cupo, gestionados que lleva y todos sus clientes (gestionados y los que pagan). */
    public ResumenFirma resumen(Integer firmaId) {
        EmpresaEntity firma = firmaActiva(firmaId);
        return new ResumenFirma(cupo(firma), query.gestionadosActivos(firmaId), query.clientesDeLaFirma(firmaId));
    }

    @Transactional
    public VinculoFila crear(Integer firmaId, CrearGestionadoDto dto, Integer actorId) {
        EmpresaEntity firma = firmaActiva(firmaId);
        int usados = query.gestionadosActivos(firmaId);
        if (usados >= cupo(firma))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Llegó al cupo de " + cupo(firma)
                    + " clientes gestionados. Comuníquese con Aura para ampliarlo");
        String nit = dto.getNit().trim();
        if (empresaRepo.existsByNit(nit))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una empresa en Aura con ese NIT");
        var consulta = dto.getUsuarioConsulta();
        if (consulta != null && usuarioRepo.findByUsername(consulta.getEmail().trim()).isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El correo del usuario de consulta ya está registrado");
        List<PaqueteGestionado> paquetes = parsear(dto.getPaquetes());
        IdentidadTributaria identidad = IdentidadTributaria.resolver(dto.getTipoPersona(), dto.getTipoDocumento(),
                dto.getRazonSocial(), dto.getNombres(), dto.getApellidos(), dto.getNombreComercial(), dto.getDv(), nit);

        // 1. Empresa, sede principal con su bodega y su tercero (el que sale en la factura).
        EmpresaEntity nueva = EmpresaEntity.builder()
                .nit(nit)
                .telefono(limpiar(dto.getTelefono()))
                .municipio(limpiar(dto.getMunicipio()))
                .municipioId(dto.getMunicipioId())
                .activa(true)
                .build();
        identidad.aplicarA(nueva);
        EmpresaEntity empresa = empresaRepo.save(nueva);
        SucursalEntity sede = sucursalRepo.save(SucursalEntity.builder()
                .empresa(empresa).nombre("Principal").codigo("001").activa(true).build());
        bodegaService.crearPrincipal(sede);
        TerceroEntity terceroEmpresa = TerceroEntity.builder()
                .empresa(empresa)
                .numeroDocumento(nit)
                .email(limpiar(dto.getCorreo()))
                .telefono(limpiar(dto.getTelefono()))
                .direccion(limpiar(dto.getDireccion()))
                .municipio(limpiar(dto.getMunicipio()))
                .municipioId(dto.getMunicipioId() != null ? dto.getMunicipioId().longValue() : null)
                .pais("Colombia").codigoPais("CO")
                .esCliente(false).esProveedor(false).esEmpleado(false)
                .activo(true)
                .build();
        identidad.aplicarA(terceroEmpresa);
        terceroRepo.save(terceroEmpresa);

        // 2. Módulos de los paquetes, líneas de uso y arranque (PUC + configuración contable).
        permisoService.activarSubmodulos(empresa.getId(), configuracion.submodulosPorPatron(
                PaqueteGestionado.claves(paquetes)));
        List<LineaUso> lineas = new ArrayList<>(List.of(LineaUso.CONTABILIDAD));
        if (paquetes.contains(PaqueteGestionado.NOMINA)) lineas.add(LineaUso.NOMINA);
        configuracion.inicializar(empresa, lineas);
        perfilesSistema.asegurar(empresa.getId());

        // 3. Vínculo gestionado: los administradores de la firma lo administran.
        firmaContable.vincular(empresa.getId(), firmaId, actorId, EmpresaFirmaClienteEntity.MODALIDAD_GESTIONADO);

        // 4. Usuario de consulta del cliente (opcional).
        if (consulta != null) crearUsuarioConsulta(empresa, sede, consulta);

        bitacora.registrarEnEmpresa(firmaId, CLAVE_BITACORA, "CLIENTE_GESTIONADO", "empresa", empresa.getId(),
                "Creó el cliente gestionado " + empresa.getRazonSocial() + " (" + (usados + 1) + " de " + cupo(firma) + ")",
                null, null);
        return query.vinculoActivoDelCliente(empresa.getId());
    }

    /** Paquetes que el gestionado tiene activos hoy. */
    public List<String> paquetesDe(Integer firmaId, Integer clienteId) {
        exigirGestionado(firmaId, clienteId);
        Set<Long> activos = new HashSet<>();
        for (SubmoduloHabilitado s : permisoQuery.habilitadosEmpresa(clienteId)) activos.add(s.id());
        List<String> out = new ArrayList<>();
        for (PaqueteGestionado p : PaqueteGestionado.values()) {
            boolean alguno = configuracion.submodulosPorPatron(p.getClaves()).stream()
                    .anyMatch(id -> activos.contains(id.longValue()));
            if (alguno || p == PaqueteGestionado.CONTABILIDAD) out.add(p.name());
        }
        return out;
    }

    /** Deja al gestionado con exactamente esos paquetes (más la base y Contabilidad). */
    @Transactional
    public List<String> cambiarPaquetes(Integer firmaId, Integer clienteId, List<String> codigos, Integer actorId) {
        exigirGestionado(firmaId, clienteId);
        List<PaqueteGestionado> paquetes = parsear(codigos);
        permisoService.activarSubmodulos(clienteId, configuracion.submodulosPorPatron(PaqueteGestionado.claves(paquetes)));
        if (paquetes.contains(PaqueteGestionado.NOMINA)) {
            EmpresaEntity cliente = empresaRepo.findById(clienteId).orElseThrow();
            List<LineaUso> lineas = new ArrayList<>(configuracion.lineas(cliente));
            if (!lineas.contains(LineaUso.NOMINA)) {
                lineas.add(LineaUso.NOMINA);
                configuracion.inicializar(cliente, lineas);
            }
        }
        bitacora.registrarEnEmpresa(clienteId, CLAVE_BITACORA, "CLIENTE_GESTIONADO", "empresa", clienteId,
                "La firma dejó los módulos: " + String.join(", ", paquetes.stream().map(PaqueteGestionado::getNombre).toList()),
                null, null);
        return paquetesDe(firmaId, clienteId);
    }

    /** Identidad tributaria del gestionado: tipo de persona, nombre, documento y DV. */
    public IdentidadGestionadoDto identidad(Integer firmaId, Integer clienteId) {
        exigirGestionado(firmaId, clienteId);
        EmpresaEntity empresa = empresaRepo.findById(clienteId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente gestionado no encontrado"));
        TerceroEntity tercero = terceroRepo.findEmpresaTerceroByNit(clienteId, empresa.getNit()).orElse(null);
        IdentidadGestionadoDto dto = new IdentidadGestionadoDto();
        dto.setNit(empresa.getNit());
        dto.setTipoPersona(empresa.getTipoPersona());
        dto.setTipoDocumento(tercero != null && tercero.getTipoDocumento() != null ? tercero.getTipoDocumento() : "NIT");
        dto.setRazonSocial(empresa.getRazonSocial());
        dto.setNombres(tercero != null ? tercero.getNombres() : null);
        dto.setApellidos(tercero != null ? tercero.getApellidos() : null);
        dto.setNombreComercial(empresa.getNombreComercial());
        dto.setDv(empresa.getDv());
        return dto;
    }

    /**
     * La firma cambia la identidad del gestionado (natural ↔ jurídica, nombre, DV) en la
     * empresa y en su tercero. El NIT no cambia. Queda en la bitácora del cliente.
     */
    @Transactional
    public IdentidadGestionadoDto cambiarIdentidad(Integer firmaId, Integer clienteId, IdentidadGestionadoDto dto,
            Integer actorId) {
        IdentidadGestionadoDto antes = identidad(firmaId, clienteId);
        EmpresaEntity empresa = empresaRepo.findById(clienteId).orElseThrow();
        IdentidadTributaria identidad = IdentidadTributaria.resolver(dto.getTipoPersona(), dto.getTipoDocumento(),
                dto.getRazonSocial(), dto.getNombres(), dto.getApellidos(), dto.getNombreComercial(), dto.getDv(),
                empresa.getNit());
        identidad.aplicarA(empresa);
        empresaRepo.save(empresa);
        TerceroEntity tercero = terceroRepo.findEmpresaTerceroByNit(clienteId, empresa.getNit()).orElse(null);
        if (tercero != null) {
            identidad.aplicarA(tercero);
            terceroRepo.save(tercero);
        }
        IdentidadGestionadoDto despues = identidad(firmaId, clienteId);
        bitacora.registrarEnEmpresa(clienteId, CLAVE_BITACORA, "IDENTIDAD_GESTIONADO", "empresa", clienteId,
                "La firma cambió la identidad del cliente: " + identidad.razonSocial() + " ("
                        + (IdentidadTributaria.NATURAL.equals(identidad.tipoPersona()) ? "persona natural" : "persona jurídica")
                        + ")", antes, despues);
        return despues;
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private void crearUsuarioConsulta(EmpresaEntity empresa, SucursalEntity sede, CrearGestionadoDto.UsuarioConsulta c) {
        TerceroEntity persona = terceroRepo.save(TerceroEntity.builder()
                .empresa(empresa)
                .tipoDocumento(c.getTipoDocumento() != null && !c.getTipoDocumento().isBlank() ? c.getTipoDocumento() : "CC")
                .numeroDocumento(c.getDocumento().trim())
                // Los viejos para mostrar el nombre; los desagregados para DIAN/UGPP (V97).
                .nombres(c.getNombres().trim())
                .apellidos(limpiar(c.getApellidos()))
                .nombre1(parte(c.getNombres(), 0))
                .nombre2(parte(c.getNombres(), 1))
                .apellido1(parte(c.getApellidos(), 0))
                .apellido2(parte(c.getApellidos(), 1))
                .email(c.getEmail().trim())
                .tipoPersona("NATURAL")
                .pais("Colombia").codigoPais("CO")
                .esCliente(false).esProveedor(false).esEmpleado(false)
                .activo(true)
                .build());
        UsuarioEntity u = usuarioRepo.save(UsuarioEntity.builder()
                .empresa(empresa)
                .tercero(persona)
                .username(c.getEmail().trim())
                .password(passwordEncoder.encode(c.getPassword()))
                .rol(PERFIL_CONSULTA)
                .activo(true)
                .build());
        u.setPerfilId(asegurarPerfilConsulta(empresa.getId()));
        usuarioRepo.save(u);
        usuarioSucursalRepo.save(UsuarioSucursalEntity.builder()
                .usuario(u).sucursal(sede).esDefault(true).activo(true).build());
    }

    /** "Consulta": ver todo lo que un gestionado puede tener, sin crear, editar ni anular. */
    private Long asegurarPerfilConsulta(Integer empresaId) {
        Long existente = permisoQuery.perfilIdPorCodigo(empresaId, PERFIL_CONSULTA);
        if (existente != null) return existente;
        PerfilEntity p = new PerfilEntity();
        p.setEmpresaId(empresaId);
        p.setCodigo(PERFIL_CONSULTA);
        p.setNombre("Consulta");
        p.setDescripcion("Solo ver: el cliente revisa el estado de su empresa sin cambiar nada");
        p.setAccesoTotal(false);
        p.setEsSistema(true);
        p.setActivo(true);
        p = perfilRepo.save(p);
        List<String> claves = new ArrayList<>(PaqueteGestionado.todasLasClaves());
        claves.removeAll(List.of("caja.usuarios", "caja.perfiles", "caja.bitacora"));
        for (Integer submoduloId : configuracion.submodulosPorPatron(claves)) {
            PerfilPermisoEntity pp = new PerfilPermisoEntity();
            pp.setPerfilId(p.getId());
            pp.setSubmoduloId(submoduloId.longValue());
            pp.setVer(true);
            pp.setCrear(false);
            pp.setEditar(false);
            pp.setAnular(false);
            perfilPermisoRepo.save(pp);
        }
        return p.getId();
    }

    /** 404 si el cliente no es un gestionado activo de esa firma. */
    void exigirGestionado(Integer firmaId, Integer clienteId) {
        VinculoFila v = query.vinculoActivoDelCliente(clienteId);
        if (v == null || !firmaId.equals(v.firmaEmpresaId())
                || !EmpresaFirmaClienteEntity.MODALIDAD_GESTIONADO.equals(v.modalidad()))
            throw new GlobalException(HttpStatus.NOT_FOUND, "Cliente gestionado no encontrado");
    }

    private EmpresaEntity firmaActiva(Integer firmaId) {
        EmpresaEntity firma = empresaRepo.findById(firmaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        if (!Boolean.TRUE.equals(firma.getEsFirmaContable()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La empresa no es una firma contable");
        return firma;
    }

    private static int cupo(EmpresaEntity firma) {
        return firma.getCupoGestionados() != null ? firma.getCupoGestionados() : 10;
    }

    private static List<PaqueteGestionado> parsear(List<String> codigos) {
        Set<PaqueteGestionado> out = new LinkedHashSet<>();
        out.add(PaqueteGestionado.CONTABILIDAD);
        for (String c : codigos != null ? codigos : List.<String>of()) {
            out.add(PaqueteGestionado.de(c).orElseThrow(
                    () -> new GlobalException(HttpStatus.BAD_REQUEST, "Módulo no permitido para un cliente gestionado: " + c)));
        }
        return new ArrayList<>(out);
    }

    /** Primera palabra (0) o el resto (1); null si no hay. */
    private static String parte(String v, int cual) {
        String t = limpiar(v);
        if (t == null) return null;
        String[] p = t.split("\\s+", 2);
        return cual == 0 ? p[0] : p.length > 1 ? p[1] : null;
    }

    private static String limpiar(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
