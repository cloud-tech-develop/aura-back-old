package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.empleado.CreateEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoTableDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoArlEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TipoEmpleadoEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoQueryRepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.tipo_empleado.TipoEmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.EmpleadoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class EmpleadoServiceImpl implements EmpleadoService {

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private EmpleadoQueryRepository empleadoQueryRepo;

    @Autowired
    private UsuarioJPARepository usuarioRepo;

    @Autowired
    private TipoEmpleadoJPARepository tipoEmpleadoRepo;

    @Autowired
    private TerceroJPARepository terceroRepo;

    @Autowired
    private TerceroRolService terceroRolService;

    @Autowired
    private ContratoLaboralService contratoLaboralService;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.nomina.ContratoLaboralJPARepository contratoRepo;

    /**
     * Resuelve la identidad del empleado en {@code tercero} (V99/V100).
     *
     * <p>Tres caminos, en orden:
     * <ol>
     *   <li>Si el DTO trae {@code terceroId} → se usa ese. Es el camino que
     *       debería volverse único cuando el front tenga selector de tercero.</li>
     *   <li>Si no, se busca por documento dentro de la empresa. Si hay
     *       exactamente uno, se enlaza y se le marca el rol EMPLEADO.</li>
     *   <li>Si no existe, se crea. Aquí se puebla la identificación
     *       desagregada — es la única forma de que nazca bien y no haya que
     *       partir nombres después con heurística.</li>
     * </ol>
     *
     * <p><b>Ambigüedad:</b> si hay varios terceros con el mismo documento
     * (duplicados históricos), se falla en vez de elegir uno arbitrario. El
     * usuario debe resolver el duplicado o mandar {@code terceroId} explícito.
     */
    private TerceroEntity resolverTercero(CreateEmpleadoDto dto, Integer empresaId) {
        // 1. Explícito
        if (dto.getTerceroId() != null) {
            return terceroRepo.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El tercero indicado no existe en esta empresa"));
        }

        if (dto.getNumeroDocumento() == null || dto.getTipoDocumento() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Debe indicar tipo y número de documento, o un terceroId");
        }

        // 2. Buscar por documento
        List<TerceroEntity> candidatos = terceroRepo.findByDocumento(
                empresaId, dto.getTipoDocumento(), dto.getNumeroDocumento());

        if (candidatos.size() > 1) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Hay " + candidatos.size() + " terceros con el documento "
                    + dto.getNumeroDocumento() + ". Resuelva el duplicado o indique el terceroId.");
        }

        if (candidatos.size() == 1) {
            TerceroEntity existente = candidatos.get(0);
            existente.setEsEmpleado(Boolean.TRUE);
            terceroRepo.save(existente);
            terceroRolService.sincronizarDesdeBooleanos(existente);
            return existente;
        }

        // 3. Crear
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        TerceroEntity nuevo = new TerceroEntity();
        nuevo.setEmpresa(empresa);
        nuevo.setTipoDocumento(dto.getTipoDocumento());
        nuevo.setNumeroDocumento(dto.getNumeroDocumento());
        nuevo.setTipoPersona("NATURAL");

        // Identificación desagregada: se puebla desde el DTO si viene. Si no,
        // se deja el legacy y los componentes en null — que es honesto: mejor
        // null que un apellido partido mal. La DIAN los va a exigir; que el
        // usuario los complete.
        nuevo.setNombre1(dto.getNombre1());
        nuevo.setNombre2(dto.getNombre2());
        nuevo.setApellido1(dto.getApellido1());
        nuevo.setApellido2(dto.getApellido2());
        nuevo.setNombres(dto.getNombres());
        nuevo.setApellidos(dto.getApellidos());

        // Datos bancarios: viven en tercero desde V97.
        nuevo.setBanco(dto.getBanco());
        nuevo.setNumeroCuenta(dto.getNumeroCuenta());
        nuevo.setTipoCuenta(dto.getTipoCuenta());

        nuevo.setEsEmpleado(Boolean.TRUE);
        nuevo.setEsCliente(Boolean.FALSE);
        nuevo.setEsProveedor(Boolean.FALSE);
        nuevo.setEsBanco(Boolean.FALSE);
        nuevo.setActivo(Boolean.TRUE);
        nuevo.setCreated_at(LocalDateTime.now());
        nuevo.setUpdated_at(LocalDateTime.now());

        TerceroEntity guardado = terceroRepo.save(nuevo);
        terceroRolService.sincronizarDesdeBooleanos(guardado);
        return guardado;
    }

    @Override
    public PageImpl<EmpleadoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return empleadoQueryRepo.listar(pageable, empresaId);
    }

    @Override
    public List<EmpleadoDto> listarVendedores(Integer empresaId) {
        List<EmpleadoEntity> vendedores = empleadoRepo.findByEmpresaIdAndActivoTrueAndCargoIgnoreCase(empresaId, "VENDEDOR");
        return vendedores.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<EmpleadoDto> listarConContratoActivo(Integer empresaId) {
        return contratoRepo.findEmpleadosConContratoActivo(empresaId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public EmpleadoDto obtenerPorId(Long id, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        return toDto(entity);
    }

    // ─── F7: saldos iniciales (migración) ──────────────────────────────────────

    @Override
    public List<com.cloud_technological.aura_pos.dto.nomina.empleado.SaldosInicialesDto> listarSaldosIniciales(
            Integer empresaId) {
        return empleadoRepo.findByEmpresaIdAndActivoTrue(empresaId).stream()
                .map(this::toSaldosDto)
                .toList();
    }

    @Override
    @Transactional
    public com.cloud_technological.aura_pos.dto.nomina.empleado.SaldosInicialesDto actualizarSaldosIniciales(
            Long id, com.cloud_technological.aura_pos.dto.nomina.empleado.SaldosInicialesDto dto, Integer empresaId) {
        EmpleadoEntity e = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        if (dto.getFechaIngreso() != null) e.setFechaIngreso(dto.getFechaIngreso());
        if (dto.getVacacionesSaldoInicial() != null) e.setVacacionesSaldoInicial(dto.getVacacionesSaldoInicial());
        if (dto.getCesantiasSaldoInicial() != null) e.setCesantiasSaldoInicial(dto.getCesantiasSaldoInicial());
        if (dto.getIngresosYtd() != null) e.setIngresosYtd(dto.getIngresosYtd());
        if (dto.getRetencionesYtd() != null) e.setRetencionesYtd(dto.getRetencionesYtd());
        return toSaldosDto(empleadoRepo.save(e));
    }

    private com.cloud_technological.aura_pos.dto.nomina.empleado.SaldosInicialesDto toSaldosDto(EmpleadoEntity e) {
        var d = new com.cloud_technological.aura_pos.dto.nomina.empleado.SaldosInicialesDto();
        d.setEmpleadoId(e.getId());
        d.setEmpleadoNombre(e.getNombres() + " " + e.getApellidos());
        d.setDocumento(e.getNumeroDocumento());
        d.setFechaIngreso(e.getFechaIngreso());
        d.setVacacionesSaldoInicial(e.getVacacionesSaldoInicial());
        d.setCesantiasSaldoInicial(e.getCesantiasSaldoInicial());
        d.setIngresosYtd(e.getIngresosYtd());
        d.setRetencionesYtd(e.getRetencionesYtd());
        return d;
    }

    @Override
    @Transactional
    public EmpleadoDto crear(CreateEmpleadoDto dto, Integer empresaId) {
        EmpleadoEntity entity = new EmpleadoEntity();
        mapFromDto(dto, entity);

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        entity.setEmpresa(empresa);

        // V99/V100: todo empleado debe tener identidad en `tercero`.
        entity.setTercero(resolverTercero(dto, empresaId));

        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        entity = empleadoRepo.save(entity);

        EmpleadoArlEntity arl = new EmpleadoArlEntity();
        arl.setEmpleado(entity);
        arl.setEmpresa(empresa);
        arl.setNivelRiesgo(dto.getNivelRiesgoArl() != null ? dto.getNivelRiesgoArl() : 1);
        arl.setPorcentaje(resolverPorcentajeArl(arl.getNivelRiesgo()));
        entity.setArl(arl);

        entity = empleadoRepo.save(entity);

        // V102: el contrato es su propia entidad. Crear un empleado sin contrato
        // lo dejaría sin condiciones laborales y sin historial salarial — y sin
        // eso no se puede liquidar, ni hacer retroactivos, ni calcular `vsp` en PILA.
        crearContratoInicial(dto, entity, empresaId);

        return toDto(entity);
    }

    /**
     * Contrato inicial a partir de los datos del empleado (V102).
     *
     * <p>Los campos de contrato en {@code CreateEmpleadoDto} (salario, tipo,
     * fechas, cargo) están deprecados: cuando el front tenga alta de contrato
     * aparte, esto se va y el contrato se crea explícitamente.
     */
    private void crearContratoInicial(CreateEmpleadoDto dto, EmpleadoEntity empleado, Integer empresaId) {
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        ContratoLaboralEntity contrato = new ContratoLaboralEntity();
        contrato.setEmpresa(empresa);
        contrato.setEmpleado(empleado);
        contrato.setTipoContrato(dto.getTipoContrato() != null ? dto.getTipoContrato() : "INDEFINIDO");
        contrato.setCargo(dto.getCargo());
        contrato.setFechaInicio(dto.getFechaIngreso());
        // Solo los contratos a término fijo llevan fecha de fin: el servicio
        // valida esa coherencia y falla si no cuadra.
        contrato.setFechaFin("FIJO".equals(contrato.getTipoContrato()) ? dto.getFechaFinContrato() : null);
        contrato.setSalarioBase(dto.getSalarioBase());
        contrato.setEsPrincipal(Boolean.TRUE);
        contrato.setEstado("ACTIVO");

        // crear() abre la primera fila del historial salarial.
        contratoLaboralService.crear(contrato);
    }

    /**
     * Alta de un empleado a partir de un tercero ya creado (flujo nuevo).
     *
     * <p>La identidad y los datos bancarios ya viven en {@code tercero} (los
     * puso el formulario de tercero): no se duplican en {@code empleados}. El
     * <b>contrato</b> (salario, tipo, ARL) se crea aparte, en la ficha — por eso
     * aquí no se crea, y {@code salario_base}/{@code fecha_ingreso} van con
     * placeholders (columnas legacy; el motor liquida desde el contrato).
     */
    @Override
    @Transactional
    public EmpleadoDto crearDesdeTercero(Long terceroId, String cargo, Integer empresaId) {
        TerceroEntity tercero = terceroRepo.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "El tercero indicado no existe en esta empresa"));

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        EmpleadoEntity entity = new EmpleadoEntity();
        entity.setEmpresa(empresa);
        entity.setTercero(tercero);
        entity.setCargo(cargo);
        entity.setActivo(Boolean.TRUE);
        // Columnas legacy NOT NULL de `empleados`: se copian del tercero / placeholder.
        entity.setNombres(tercero.getNombres());
        entity.setApellidos(tercero.getApellidos());
        entity.setTipoDocumento(tercero.getTipoDocumento());
        entity.setNumeroDocumento(tercero.getNumeroDocumento());
        entity.setFechaIngreso(LocalDate.now());
        entity.setSalarioBase(BigDecimal.ZERO);   // el real va en el contrato
        entity.setTipoContrato("INDEFINIDO");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity = empleadoRepo.save(entity);

        // Rol e indicador EMPLEADO en el tercero.
        tercero.setEsEmpleado(Boolean.TRUE);
        terceroRepo.save(tercero);
        terceroRolService.agregarRol(terceroId, com.cloud_technological.aura_pos.entity.TerceroRolEntity.Rol.EMPLEADO);

        // ARL por defecto (nivel 1); el nivel real se define en el contrato.
        EmpleadoArlEntity arl = new EmpleadoArlEntity();
        arl.setEmpleado(entity);
        arl.setEmpresa(empresa);
        arl.setNivelRiesgo(1);
        arl.setPorcentaje(resolverPorcentajeArl(1));
        entity.setArl(arl);
        entity = empleadoRepo.save(entity);

        return toDto(entity);
    }

    @Override
    @Transactional
    public EmpleadoDto cambiarControlAsistencia(Long id, boolean requiere, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        entity.setRequiereControlAsistencia(requiere);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(empleadoRepo.save(entity));
    }

    @Override
    @Transactional
    public EmpleadoDto actualizar(Long id, CreateEmpleadoDto dto, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        
        // Guardar el cargo anterior para comparar
        String cargoAnterior = entity.getCargo();
        
        mapFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());

        if (dto.getNivelRiesgoArl() != null) {
            EmpleadoArlEntity arl = entity.getArl();
            if (arl == null) {
                arl = new EmpleadoArlEntity();
                arl.setEmpleado(entity);
                EmpresaEntity empresa = new EmpresaEntity();
                empresa.setId(empresaId);
                arl.setEmpresa(empresa);
                entity.setArl(arl);
            }
            arl.setNivelRiesgo(dto.getNivelRiesgoArl());
            arl.setPorcentaje(resolverPorcentajeArl(dto.getNivelRiesgoArl()));
        }

        EmpleadoDto result = toDto(empleadoRepo.save(entity));

        // Sincronizar con usuario si el cargo cambió
        if (cargoAnterior == null || !cargoAnterior.equals(dto.getCargo())) {
            sincronizarConUsuario(id, empresaId);
        }

        return result;
    }

    @Override
    @Transactional
    public void retirar(Long id, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        entity.setActivo(false);
        entity.setFechaRetiro(LocalDate.now());
        entity.setUpdatedAt(LocalDateTime.now());
        empleadoRepo.save(entity);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void mapFromDto(CreateEmpleadoDto dto, EmpleadoEntity entity) {
        entity.setNombres(dto.getNombres());
        entity.setApellidos(dto.getApellidos());
        entity.setTipoDocumento(dto.getTipoDocumento());
        entity.setNumeroDocumento(dto.getNumeroDocumento());
        entity.setCargo(dto.getCargo());
        entity.setFechaIngreso(dto.getFechaIngreso());
        entity.setFechaFinContrato("FIJO".equals(dto.getTipoContrato()) ? dto.getFechaFinContrato() : null);
        entity.setSalarioBase(dto.getSalarioBase());
        entity.setTipoContrato(dto.getTipoContrato());
        entity.setBanco(dto.getBanco());
        entity.setNumeroCuenta(dto.getNumeroCuenta());
        entity.setTipoCuenta(dto.getTipoCuenta());
        if (dto.getRequiereControlAsistencia() != null)
            entity.setRequiereControlAsistencia(dto.getRequiereControlAsistencia());
    }

    private EmpleadoDto toDto(EmpleadoEntity entity) {
        EmpleadoDto dto = new EmpleadoDto();
        dto.setId(entity.getId());
        if (entity.getTercero() != null) dto.setTerceroId(entity.getTercero().getId());
        dto.setNombres(entity.getNombres());
        dto.setApellidos(entity.getApellidos());
        dto.setTipoDocumento(entity.getTipoDocumento());
        dto.setNumeroDocumento(entity.getNumeroDocumento());
        dto.setCargo(entity.getCargo());
        dto.setFechaIngreso(entity.getFechaIngreso());
        dto.setFechaRetiro(entity.getFechaRetiro());
        dto.setFechaFinContrato(entity.getFechaFinContrato());
        dto.setSalarioBase(entity.getSalarioBase());
        dto.setTipoContrato(entity.getTipoContrato());
        dto.setBanco(entity.getBanco());
        dto.setNumeroCuenta(entity.getNumeroCuenta());
        dto.setTipoCuenta(entity.getTipoCuenta());
        dto.setActivo(entity.getActivo());
        dto.setRequiereControlAsistencia(entity.getRequiereControlAsistencia());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getArl() != null) {
            dto.setNivelRiesgoArl(entity.getArl().getNivelRiesgo());
            dto.setPorcentajeArl(entity.getArl().getPorcentaje());
        }
        return dto;
    }

    private BigDecimal resolverPorcentajeArl(int nivel) {
        return switch (nivel) {
            case 1 -> new BigDecimal("0.522");
            case 2 -> new BigDecimal("1.044");
            case 3 -> new BigDecimal("2.436");
            case 4 -> new BigDecimal("4.350");
            case 5 -> new BigDecimal("6.960");
            default -> new BigDecimal("0.522");
        };
    }

    @Override
    @Transactional
    public void sincronizarConUsuario(Long id, Integer empresaId) {
        // 1. Obtener el empleado
        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        // 2. Buscar si existe un usuario vinculado a este empleado
        UsuarioEntity usuario = usuarioRepo.findByEmpleadoId(id).orElse(null);
        
        if (usuario == null) {
            // No hay usuario vinculado, no hay nada que sincronizar
            return;
        }

        // 3. Buscar el tipo de empleado por el cargo
        String cargoEmpleado = empleado.getCargo();
        TipoEmpleadoEntity tipoEmpleado = null;
        
        // Buscar en tipos de empleado de la empresa
        List<TipoEmpleadoEntity> tiposEmpresa = tipoEmpleadoRepo.findByEmpresaIdAndActivoTrue((long) empresaId);
        for (TipoEmpleadoEntity te : tiposEmpresa) {
            if (te.getNombre().equalsIgnoreCase(cargoEmpleado)) {
                tipoEmpleado = te;
                break;
            }
        }
        
        // Si no se encontró, buscar en los tipos generales (empresa_id = 1)
        if (tipoEmpleado == null) {
            List<TipoEmpleadoEntity> tiposGenerales = tipoEmpleadoRepo.findByEmpresaIdAndActivoTrue(1L);
            for (TipoEmpleadoEntity te : tiposGenerales) {
                if (te.getNombre().equalsIgnoreCase(cargoEmpleado)) {
                    tipoEmpleado = te;
                    break;
                }
            }
        }

        // 4. Actualizar el usuario con los nuevos datos del empleado
        usuario.setTipoEmpleado(tipoEmpleado);
        // El rol se actualiza con el nombre del cargo o del tipo de empleado
        String nuevoRol = tipoEmpleado != null ? tipoEmpleado.getNombre() : cargoEmpleado;
        usuario.setRol(nuevoRol);

        // 5. Guardar los cambios
        usuarioRepo.save(usuario);
    }
}
