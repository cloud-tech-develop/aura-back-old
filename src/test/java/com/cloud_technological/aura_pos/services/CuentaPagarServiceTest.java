package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.dto.cuentas_pagar.AbonoPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.entity.AbonoPagarEntity;
import com.cloud_technological.aura_pos.entity.CuentaPagarEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.CuentaPagarMapper;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.implementations.CuentaPagarServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Tests para HU-010: Gestión de Cuentas por Pagar
 * Tests para HU-012: Abonos a Cuentas por Pagar
 * Tests para HU-013: Cálculo de Totales de Cuentas
 */
@ExtendWith(MockitoExtension.class)
class CuentaPagarServiceTest {

    @Mock
    private CuentaPagarQueryRepository queryRepository;

    @Mock
    private CuentaPagarJPARepository jpaRepository;

    @Mock
    private AbonoPagarJPARepository abonoJpaRepository;

    @Mock
    private EmpresaJPARepository empresaRepository;

    @Mock
    private TerceroJPARepository terceroRepository;

    @Mock
    private UsuarioJPARepository usuarioRepository;

    @Mock
    private CuentaPagarMapper mapper;

    @InjectMocks
    private CuentaPagarServiceImpl cuentaPagarService;

    private EmpresaEntity empresaMock;
    private TerceroEntity proveedorMock;
    private UsuarioEntity usuarioMock;
    private CuentaPagarEntity cuentaPagarMock;

    @BeforeEach
    void setUp() {
        // Configurar empresa mock
        empresaMock = new EmpresaEntity();
        empresaMock.setId(1);
        empresaMock.setNit("12345678");

        // Configurar proveedor mock
        proveedorMock = new TerceroEntity();
        proveedorMock.setId(1L);
        proveedorMock.setNombres("Pedro");
        proveedorMock.setApellidos("Gómez");
        proveedorMock.setNumeroDocumento("87654321");
        proveedorMock.setEsCliente(false);
        proveedorMock.setEsProveedor(true);

        // Configurar usuario mock
        usuarioMock = new UsuarioEntity();
        usuarioMock.setId(1);
        usuarioMock.setUsername("admin");

        // Configurar cuenta por pagar mock
        cuentaPagarMock = CuentaPagarEntity.builder()
                .id(1L)
                .empresa(empresaMock)
                .tercero(proveedorMock)
                .numeroCuenta("CP-20260226-0001")
                .fechaEmision(LocalDateTime.now())
                .fechaVencimiento(LocalDateTime.now().plusDays(30))
                .totalDeuda(new BigDecimal("100000"))
                .totalAbonado(BigDecimal.ZERO)
                .saldoPendiente(new BigDecimal("100000"))
                .estado("activa")
                .build();
    }

    // ==================== HU-010: Gestión de Cuentas por Pagar ====================

    /**
     * Escenario: Crear cuenta por pagar exitosamente
     * Dado: Un proveedor válido y datos correctos
     * Cuando: Se crea la cuenta por pagar
     * Entonces: Se crea la cuenta con número único y saldo pendiente igual al total
     */
    @Test
    void shouldCrearCuentaPagarWhenDatosValidos() {
        // Arrange
        CreateCuentaPagarDto dto = new CreateCuentaPagarDto();
        dto.setProveedorId(1L);
        dto.setTotalDeuda(new BigDecimal("100000"));
        dto.setFechaEmision(LocalDateTime.now());
        dto.setFechaVencimiento(LocalDateTime.now().plusDays(30));
        dto.setObservaciones("Test cuenta");

        when(terceroRepository.findById(1)).thenReturn(Optional.of(proveedorMock));
        when(queryRepository.generarNumeroCuenta()).thenReturn("CP-20260226-0001");
        when(empresaRepository.findById(1)).thenReturn(Optional.of(empresaMock));
        when(jpaRepository.save(any(CuentaPagarEntity.class))).thenReturn(cuentaPagarMock);

        // Act
        CuentaPagarDto resultado = cuentaPagarService.crear(dto, 1, 1L);

        // Assert
        assertNotNull(resultado);
        assertEquals("CP-20260226-0001", resultado.getNumeroCuenta());
        assertEquals(new BigDecimal("100000"), resultado.getTotalDeuda());
        assertEquals(BigDecimal.ZERO, resultado.getTotalAbonado());
        assertEquals(new BigDecimal("100000"), resultado.getSaldoPendiente());
        assertEquals("activa", resultado.getEstado());
        verify(jpaRepository).save(any(CuentaPagarEntity.class));
    }

    /**
     * Escenario: Error al crear cuenta por pagar con proveedor inexistente
     * Dado: Un proveedor que no existe
     * Cuando: Se intenta crear la cuenta
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenProveedorNoExiste() {
        // Arrange
        CreateCuentaPagarDto dto = new CreateCuentaPagarDto();
        dto.setProveedorId(999L);
        dto.setTotalDeuda(new BigDecimal("100000"));
        dto.setFechaEmision(LocalDateTime.now());

        when(terceroRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.crear(dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("Proveedor no encontrado"));
    }

    /**
     * Escenario: Error al crear cuenta por pagar con tercero que no es proveedor
     * Dado: Un tercero que no tiene flag de proveedor
     * Cuando: Se intenta crear la cuenta
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenTerceroNoEsProveedor() {
        // Arrange
        TerceroEntity terceroNoProveedor = new TerceroEntity();
        terceroNoProveedor.setId(1L);
        terceroNoProveedor.setEsCliente(true);
        terceroNoProveedor.setEsProveedor(false);

        CreateCuentaPagarDto dto = new CreateCuentaPagarDto();
        dto.setProveedorId(1L);
        dto.setTotalDeuda(new BigDecimal("100000"));
        dto.setFechaEmision(LocalDateTime.now());

        when(terceroRepository.findById(1)).thenReturn(Optional.of(terceroNoProveedor));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.crear(dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("no es un proveedor"));
    }

    /**
     * Escenario: Error al crear cuenta por pagar con total deuda menor o igual a 0
     * Dado: Un dto con totalDeuda inválido
     * Cuando: Se intenta crear la cuenta
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenTotalDeudaInvalido() {
        // Arrange
        CreateCuentaPagarDto dto = new CreateCuentaPagarDto();
        dto.setProveedorId(1L);
        dto.setTotalDeuda(BigDecimal.ZERO);
        dto.setFechaEmision(LocalDateTime.now());

        when(terceroRepository.findById(1)).thenReturn(Optional.of(proveedorMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.crear(dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("mayor a 0"));
    }

    /**
     * Escenario: Obtener cuenta por pagar por ID exitosamente
     * Dado: Una cuenta existente
     * Cuando: Se obtiene por ID
     * Entonces: Retorna los datos de la cuenta
     */
    @Test
    void shouldObtenerCuentaPagarByIdWhenExiste() {
        // Arrange
        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));

        // Act
        CuentaPagarDto resultado = cuentaPagarService.obtenerPorId(1L, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        assertEquals("CP-20260226-0001", resultado.getNumeroCuenta());
    }

    /**
     * Escenario: Error al obtener cuenta por pagar inexistente
     * Dado: Una cuenta que no existe
     * Cuando: Se intenta obtener por ID
     * Entonces: Lanza excepción 404
     */
    @Test
    void shouldLanzarExcepcionWhenCuentaNoExiste() {
        // Arrange
        when(jpaRepository.findByIdAndEmpresaId(999L, 1)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.obtenerPorId(999L, 1));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getMessage().contains("no encontrada"));
    }

    /**
     * Escenario: Actualizar cuenta por pagar exitosamente
     * Dado: Una cuenta existente
     * Cuando: Se actualiza la fecha de vencimiento y observaciones
     * Entonces: Se guardan los cambios
     */
    @Test
    void shouldActualizarCuentaPagarWhenDatosValidos() {
        // Arrange
        CreateCuentaPagarDto dto = new CreateCuentaPagarDto();
        dto.setFechaVencimiento(LocalDateTime.now().plusDays(60));
        dto.setObservaciones("Nueva observación");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(jpaRepository.save(any(CuentaPagarEntity.class))).thenReturn(cuentaPagarMock);

        // Act
        CuentaPagarDto resultado = cuentaPagarService.actualizar(1L, dto, 1);

        // Assert
        assertNotNull(resultado);
        verify(jpaRepository).save(any(CuentaPagarEntity.class));
    }

    // ==================== HU-012: Abonos a Cuentas por Pagar ====================

    /**
     * Escenario: Registrar abono exitosamente
     * Dado: Una cuenta activa con saldo pendiente
     * Cuando: Se registra un abono menor al saldo
     * Entonces: Se crea el abono y se actualiza el saldo
     */
    @Test
    void shouldRegistrarAbonoWhenMontoMenorASaldo() {
        // Arrange
        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setMonto(new BigDecimal("25000"));
        dto.setMetodoPago("efectivo");
        dto.setReferencia("PAG-001");

        AbonoPagarEntity abonoMock = AbonoPagarEntity.builder()
                .id(1L)
                .cuentaPagar(cuentaPagarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("efectivo")
                .referencia("PAG-001")
                .banco("Banco de Bogotá")
                .fechaPago(LocalDateTime.now())
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(abonoJpaRepository.save(any(AbonoPagarEntity.class))).thenReturn(abonoMock);
        when(jpaRepository.save(any(CuentaPagarEntity.class))).thenReturn(cuentaPagarMock);

        // Act
        AbonoPagarDto resultado = cuentaPagarService.registrarAbono(1L, dto, 1, 1L);

        // Assert
        assertNotNull(resultado);
        assertEquals(new BigDecimal("25000"), resultado.getMonto());
        assertEquals("efectivo", resultado.getMetodoPago());
    }

    /**
     * Escenario: Registrar abono que paga la cuenta completamente
     * Dado: Una cuenta activa
     * Cuando: Se registra un abono igual al saldo pendiente
     * Entonces: La cuenta cambia a estado "pagada"
     */
    @Test
    void shouldCambiarEstadoPagadaWhenAbonoIgualASaldo() {
        // Arrange
        cuentaPagarMock.setSaldoPendiente(new BigDecimal("25000"));

        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setMonto(new BigDecimal("25000"));
        dto.setMetodoPago("transferencia");

        AbonoPagarEntity abonoMock = AbonoPagarEntity.builder()
                .id(1L)
                .cuentaPagar(cuentaPagarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("transferencia")
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(abonoJpaRepository.save(any(AbonoPagarEntity.class))).thenReturn(abonoMock);
        when(jpaRepository.save(any(CuentaPagarEntity.class))).thenAnswer(invocation -> {
            CuentaPagarEntity cuenta = invocation.getArgument(0);
            return cuenta;
        });

        // Act
        AbonoPagarDto resultado = cuentaPagarService.registrarAbono(1L, dto, 1, 1L);

        // Assert
        assertNotNull(resultado);
        verify(jpaRepository).save(argThat(cuenta -> "pagada".equals(cuenta.getEstado())));
    }

    /**
     * Escenario: Error al registrar abono en cuenta ya pagada
     * Dado: Una cuenta con estado "pagada"
     * Cuando: Se intenta registrar abono
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenCuentaYaPagada() {
        // Arrange
        cuentaPagarMock.setEstado("pagada");

        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setMonto(new BigDecimal("25000"));
        dto.setMetodoPago("efectivo");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.registrarAbono(1L, dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("ya está pagada"));
    }

    /**
     * Escenario: Error al registrar abono con monto mayor al saldo
     * Dado: Una cuenta con saldo pendiente de 25000
     * Cuando: Se intenta registrar abono de 50000
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenMontoMayorASaldo() {
        // Arrange
        cuentaPagarMock.setSaldoPendiente(new BigDecimal("25000"));

        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setMonto(new BigDecimal("50000"));
        dto.setMetodoPago("efectivo");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.registrarAbono(1L, dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("mayor al saldo pendiente"));
    }

    /**
     * Escenario: Error al registrar abono con monto menor o igual a 0
     * Dado: Un dto con monto inválido
     * Cuando: Se intenta registrar abono
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenMontoInvalido() {
        // Arrange
        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setMonto(BigDecimal.ZERO);
        dto.setMetodoPago("efectivo");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.registrarAbono(1L, dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("mayor a 0"));
    }

    /**
     * Escenario: Listar abonos de cuenta exitosamente
     * Dado: Una cuenta con abonos
     * Cuando: Se listan los abonos
     * Entonces: Retorna la lista de abonos
     */
    @Test
    void shouldListarAbonosWhenExisten() {
        // Arrange
        AbonoPagarEntity abonoMock = AbonoPagarEntity.builder()
                .id(1L)
                .cuentaPagar(cuentaPagarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("efectivo")
                .build();

        List<AbonoPagarEntity> abonos = new ArrayList<>();
        abonos.add(abonoMock);

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(abonoJpaRepository.findAll()).thenReturn(abonos);

        // Act
        List<AbonoPagarDto> resultado = cuentaPagarService.listarAbonos(1L, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
    }

    /**
     * Escenario: Error al listar abonos de cuenta inexistente
     * Dado: Una cuenta que no existe
     * Cuando: Se intenta listar los abonos
     * Entonces: Lanza excepción 404
     */
    @Test
    void shouldLanzarExcepcionWhenListarAbonosCuentaNoExiste() {
        // Arrange
        when(jpaRepository.findByIdAndEmpresaId(999L, 1)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.listarAbonos(999L, 1));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    /**
     * Escenario: Eliminar abono del día actual exitosamente
     * Dado: Un abono creado hoy
     * Cuando: Se elimina el abono
     * Entonces: Se reversa el abono y la cuenta vuelve a estado activo
     */
    @Test
    void shouldEliminarAbonoWhenEsDelDiaActual() {
        // Arrange
        AbonoPagarEntity abonoMock = AbonoPagarEntity.builder()
                .id(1L)
                .cuentaPagar(cuentaPagarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("efectivo")
                .createdAt(LocalDateTime.now())
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(abonoJpaRepository.findByIdAndCuentaPagarId(1L, 1L)).thenReturn(Optional.of(abonoMock));

        // Act
        cuentaPagarService.eliminarAbono(1L, 1L, 1);

        // Assert
        verify(jpaRepository).save(argThat(cuenta -> 
                "activa".equals(cuenta.getEstado()) && 
                new BigDecimal("25000").equals(cuenta.getSaldoPendiente())));
        verify(abonoJpaRepository).delete(abonoMock);
    }

    /**
     * Escenario: Error al eliminar abono de fecha anterior
     * Dado: Un abono de días anteriores
     * Cuando: Se intenta eliminar
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenEliminarAbonoDeFechaAnterior() {
        // Arrange
        AbonoPagarEntity abonoMock = AbonoPagarEntity.builder()
                .id(1L)
                .cuentaPagar(cuentaPagarMock)
                .monto(new BigDecimal("25000"))
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(abonoJpaRepository.findByIdAndCuentaPagarId(1L, 1L)).thenReturn(Optional.of(abonoMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.eliminarAbono(1L, 1L, 1));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("día actual"));
    }

    /**
     * Escenario: Error al eliminar abono de cuenta pagada
     * Dado: Una cuenta con estado "pagada"
     * Cuando: Se intenta eliminar un abono
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenEliminarAbonoDeCuentaPagada() {
        // Arrange
        cuentaPagarMock.setEstado("pagada");

        AbonoPagarEntity abonoMock = AbonoPagarEntity.builder()
                .id(1L)
                .cuentaPagar(cuentaPagarMock)
                .createdAt(LocalDateTime.now())
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(abonoJpaRepository.findByIdAndCuentaPagarId(1L, 1L)).thenReturn(Optional.of(abonoMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.eliminarAbono(1L, 1L, 1));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("cuenta pagada"));
    }

    // ==================== HU-013: Cálculo de Totales de Cuentas ====================

    /**
     * Escenario: Obtener resumen de cuentas por pagar
     * Dado: Una empresa con cuentas
     * Cuando: Se solicita el resumen
     * Entonces: Retorna totales de deuda, abonos, pendientes y por estado
     */
    @Test
    void shouldObtenerResumenWhenExistenCuentas() {
        // Arrange
        CuentaPagarResumenDto resumenMock = new CuentaPagarResumenDto();
        resumenMock.setTotalCuentas(10L);
        resumenMock.setTotalDeuda(new BigDecimal("1000000"));
        resumenMock.setTotalAbonado(new BigDecimal("300000"));
        resumenMock.setSaldoPendiente(new BigDecimal("700000"));
        resumenMock.setCantidadActivas(5L);
        resumenMock.setCantidadPagadas(3L);
        resumenMock.setCantidadVencidas(2L);

        when(queryRepository.obtenerResumen(eq(1), any(), any(), any(), any())).thenReturn(resumenMock);

        // Act
        CuentaPagarResumenDto resultado = cuentaPagarService.obtenerResumen(1, null, null, null, null);

        // Assert
        assertNotNull(resultado);
        assertEquals(10L, resultado.getTotalCuentas());
        assertEquals(new BigDecimal("1000000"), resultado.getTotalDeuda());
        assertEquals(new BigDecimal("300000"), resultado.getTotalAbonado());
        assertEquals(new BigDecimal("700000"), resultado.getSaldoPendiente());
        assertEquals(5L, resultado.getCantidadActivas());
        assertEquals(3L, resultado.getCantidadPagadas());
        assertEquals(2L, resultado.getCantidadVencidas());
    }

    /**
     * Escenario: Obtener cuentas vencidas
     * Dado: Una empresa con cuentas vencidas
     * Cuando: Se solicita las cuentas vencidas
     * Entonces: Retorna lista de cuentas con saldo pendiente y fecha vencida
     */
    @Test
    void shouldObtenerCuentasVencidasWhenExisten() {
        // Arrange
        CuentaPagarTableDto cuentaVencida = new CuentaPagarTableDto();
        cuentaVencida.setId(1L);
        cuentaVencida.setNumeroCuenta("CP-20260101-0001");
        cuentaVencida.setSaldoPendiente(new BigDecimal("50000"));
        cuentaVencida.setEstado("vencida");

        List<CuentaPagarTableDto> cuentasVencidas = new ArrayList<>();
        cuentasVencidas.add(cuentaVencida);

        when(queryRepository.obtenerVencidas(1)).thenReturn(cuentasVencidas);

        // Act
        List<CuentaPagarTableDto> resultado = cuentaPagarService.obtenerVencidas(1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("vencida", resultado.get(0).getEstado());
    }

    /**
     * Escenario: Listar cuentas con paginación
     * Dado: Una empresa con cuentas
     * Cuando: Se listan con paginación
     * Entonces: Retorna página de cuentas
     */
    @Test
    void shouldListarCuentasWhenPaginacion() {
        // Arrange
        PageableDto<Object> pageable = new PageableDto<>();
        pageable.setPage(0L);
        pageable.setRows(10L);
        pageable.setOrder_by("id");
        pageable.setOrder("asc");

        List<CuentaPagarTableDto> cuentas = new ArrayList<>();
        cuentas.add(new CuentaPagarTableDto());

        PageImpl<CuentaPagarTableDto> pageMock = new PageImpl<>(cuentas);

        when(queryRepository.listar(eq(pageable), eq(1))).thenReturn(pageMock);

        // Act
        PageImpl<CuentaPagarTableDto> resultado = cuentaPagarService.listar(pageable, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getContent().size());
    }

    /**
     * Escenario: Listar cuentas con filtros
     * Dado: Filtros de fecha y estado
     * Cuando: Se listan con filtros
     * Entonces: Retorna cuentas que cumplen los filtros
     */
    @Test
    void shouldListarCuentasWithFiltrosWhenDatosValidos() {
        // Arrange
        PageableDto<Object> pageable = new PageableDto<>();
        pageable.setPage(0L);
        pageable.setRows(10L);
        pageable.setOrder_by("id");
        pageable.setOrder("asc");

        List<CuentaPagarTableDto> cuentas = new ArrayList<>();
        cuentas.add(new CuentaPagarTableDto());

        PageImpl<CuentaPagarTableDto> pageMock = new PageImpl<>(cuentas);

        when(queryRepository.listarConFiltros(eq(pageable), eq(1), any(), any(), any(), any()))
                .thenReturn(pageMock);

        // Act
        PageImpl<CuentaPagarTableDto> resultado = cuentaPagarService.listarConFiltros(
                pageable, 1, "2026-01-01", "2026-12-31", null, "activa");

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getContent().size());
    }

    /**
     * Escenario: Listar cuentas con filtros por proveedor
     * Dado: Filtro por proveedor específico
     * Cuando: Se listan con filtro de proveedor
     * Entonces: Retorna solo cuentas de ese proveedor
     */
    @Test
    void shouldListarCuentasWithFiltroProveedorWhenDatosValidos() {
        // Arrange
        PageableDto<Object> pageable = new PageableDto<>();
        pageable.setPage(0L);
        pageable.setRows(10L);

        List<CuentaPagarTableDto> cuentas = new ArrayList<>();
        CuentaPagarTableDto cuenta = new CuentaPagarTableDto();
        cuenta.setId(1L);
        cuenta.setProveedorNombre("Pedro Gómez");
        cuentas.add(cuenta);

        PageImpl<CuentaPagarTableDto> pageMock = new PageImpl<>(cuentas);

        when(queryRepository.listarConFiltros(eq(pageable), eq(1), any(), any(), eq(1L), any()))
                .thenReturn(pageMock);

        // Act
        PageImpl<CuentaPagarTableDto> resultado = cuentaPagarService.listarConFiltros(
                pageable, 1, null, null, 1L, null);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getContent().size());
        assertEquals("Pedro Gómez", resultado.getContent().get(0).getProveedorNombre());
    }

    /**
     * Escenario: Obtener resumen con filtros específicos
     * Dado: Filtros de fecha y estado
     * Cuando: Se solicita el resumen con filtros
     * Entonces: Retorna totales filtrados
     */
    @Test
    void shouldObtenerResumenWithFiltrosWhenDatosValidos() {
        // Arrange
        CuentaPagarResumenDto resumenMock = new CuentaPagarResumenDto();
        resumenMock.setTotalCuentas(5L);
        resumenMock.setTotalDeuda(new BigDecimal("500000"));
        resumenMock.setTotalAbonado(new BigDecimal("100000"));
        resumenMock.setSaldoPendiente(new BigDecimal("400000"));
        resumenMock.setCantidadActivas(3L);
        resumenMock.setCantidadPagadas(1L);
        resumenMock.setCantidadVencidas(1L);

        when(queryRepository.obtenerResumen(eq(1), eq("2026-01-01"), eq("2026-12-31"), any(), eq("activa")))
                .thenReturn(resumenMock);

        // Act
        CuentaPagarResumenDto resultado = cuentaPagarService.obtenerResumen(
                1, "2026-01-01", "2026-12-31", null, "activa");

        // Assert
        assertNotNull(resultado);
        assertEquals(5, resultado.getTotalCuentas());
        assertEquals("activa", resultado.getCantidadActivas() > 0 ? "activa" : null);
    }

    /**
     * Escenario: Registrar abono con método de transferencia y banco
     * Dado: Una cuenta activa
     * Cuando: Se registra un abono por transferencia
     * Entonces: Se guarda el banco y la referencia
     */
    @Test
    void shouldRegistrarAbonoTransferenciaWhenDatosValidos() {
        // Arrange
        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setMonto(new BigDecimal("30000"));
        dto.setMetodoPago("transferencia");
        dto.setReferencia("TRF-12345");
        dto.setBanco("Banco de Colombia");

        AbonoPagarEntity abonoMock = AbonoPagarEntity.builder()
                .id(1L)
                .cuentaPagar(cuentaPagarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("30000"))
                .metodoPago("transferencia")
                .referencia("TRF-12345")
                .banco("Banco de Colombia")
                .fechaPago(LocalDateTime.now())
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(abonoJpaRepository.save(any(AbonoPagarEntity.class))).thenReturn(abonoMock);
        when(jpaRepository.save(any(CuentaPagarEntity.class))).thenReturn(cuentaPagarMock);

        // Act
        AbonoPagarDto resultado = cuentaPagarService.registrarAbono(1L, dto, 1, 1L);

        // Assert
        assertNotNull(resultado);
        assertEquals("transferencia", resultado.getMetodoPago());
    }

    /**
     * Escenario: Error al registrar abono con usuario inexistente
     * Dado: Un usuario que no existe
     * Cuando: Se intenta registrar abono
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenUsuarioNoExiste() {
        // Arrange
        AbonoPagarDto dto = new AbonoPagarDto();
        dto.setMonto(new BigDecimal("25000"));
        dto.setMetodoPago("efectivo");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(usuarioRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.registrarAbono(1L, dto, 1, 999L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("Usuario no encontrado"));
    }

    /**
     * Escenario: Error al actualizar cuenta inexistente
     * Dado: Una cuenta que no existe
     * Cuando: Se intenta actualizar
     * Entonces: Lanza excepción 404
     */
    @Test
    void shouldLanzarExcepcionWhenActualizarCuentaNoExiste() {
        // Arrange
        CreateCuentaPagarDto dto = new CreateCuentaPagarDto();
        dto.setFechaVencimiento(LocalDateTime.now().plusDays(60));

        when(jpaRepository.findByIdAndEmpresaId(999L, 1)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.actualizar(999L, dto, 1));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    /**
     * Escenario: Eliminar abono inexistente
     * Dado: Un abono que no existe
     * Cuando: Se intenta eliminar
     * Entonces: Lanza excepción 404
     */
    @Test
    void shouldLanzarExcepcionWhenEliminarAbonoNoExiste() {
        // Arrange
        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaPagarMock));
        when(abonoJpaRepository.findByIdAndCuentaPagarId(999L, 1L)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaPagarService.eliminarAbono(1L, 999L, 1));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getMessage().contains("Abono no encontrado"));
    }
}
