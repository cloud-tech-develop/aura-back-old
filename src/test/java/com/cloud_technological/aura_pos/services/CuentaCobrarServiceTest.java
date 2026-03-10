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

import com.cloud_technological.aura_pos.dto.cuentas_cobrar.AbonoCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.CuentaCobrarMapper;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.implementations.CuentaCobrarServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Tests para HU-009: Gestión de Cuentas por Cobrar
 * Tests para HU-011: Abonos a Cuentas por Cobrar
 * Tests para HU-013: Cálculo de Totales de Cuentas
 */
@ExtendWith(MockitoExtension.class)
class CuentaCobrarServiceTest {

    @Mock
    private CuentaCobrarQueryRepository queryRepository;

    @Mock
    private CuentaCobrarJPARepository jpaRepository;

    @Mock
    private AbonoCobrarJPARepository abonoJpaRepository;

    @Mock
    private EmpresaJPARepository empresaRepository;

    @Mock
    private TerceroJPARepository terceroRepository;

    @Mock
    private UsuarioJPARepository usuarioRepository;

    @Mock
    private CuentaCobrarMapper mapper;

    @InjectMocks
    private CuentaCobrarServiceImpl cuentaCobrarService;

    private EmpresaEntity empresaMock;
    private TerceroEntity clienteMock;
    private UsuarioEntity usuarioMock;
    private CuentaCobrarEntity cuentaCobrarMock;

    @BeforeEach
    void setUp() {
        empresaMock = new EmpresaEntity();
        empresaMock.setId(1);
        empresaMock.setNit("12345678");

        clienteMock = new TerceroEntity();
        clienteMock.setId(1L);
        clienteMock.setNombres("Juan");
        clienteMock.setApellidos("Pérez");
        clienteMock.setNumeroDocumento("12345678");
        clienteMock.setEsCliente(true);

        usuarioMock = new UsuarioEntity();
        usuarioMock.setId(1);
        usuarioMock.setUsername("admin");

        cuentaCobrarMock = CuentaCobrarEntity.builder()
                .id(1L)
                .empresa(empresaMock)
                .tercero(clienteMock)
                .numeroCuenta("CC-20260226-0001")
                .fechaEmision(LocalDateTime.now())
                .fechaVencimiento(LocalDateTime.now().plusDays(30))
                .totalDeuda(new BigDecimal("100000"))
                .totalAbonado(BigDecimal.ZERO)
                .saldoPendiente(new BigDecimal("100000"))
                .estado("activa")
                .build();
    }

    // ==================== HU-009: Gestión de Cuentas por Cobrar ====================

    /**
     * Escenario: Crear cuenta por cobrar exitosamente
     * Dado: Un cliente válido y datos correctos
     * Cuando: Se crea la cuenta por cobrar
     * Entonces: Se crea la cuenta con número único y saldo pendiente igual al total
     */
    @Test
    void shouldCrearCuentaCobrarWhenDatosValidos() {
        // Arrange
        CreateCuentaCobrarDto dto = new CreateCuentaCobrarDto();
        dto.setClienteId(1L);
        dto.setTotalDeuda(new BigDecimal("100000"));
        dto.setFechaEmision(LocalDateTime.now());
        dto.setFechaVencimiento(LocalDateTime.now().plusDays(30));
        dto.setObservaciones("Test cuenta");

        when(terceroRepository.findById(1)).thenReturn(Optional.of(clienteMock));
        when(queryRepository.generarNumeroCuenta()).thenReturn("CC-20260226-0001");
        when(empresaRepository.findById(1)).thenReturn(Optional.of(empresaMock));
        when(jpaRepository.save(any(CuentaCobrarEntity.class))).thenReturn(cuentaCobrarMock);

        // Act
        CuentaCobrarDto resultado = cuentaCobrarService.crear(dto, 1, 1L);

        // Assert
        assertNotNull(resultado);
        assertEquals("CC-20260226-0001", resultado.getNumeroCuenta());
        assertEquals(new BigDecimal("100000"), resultado.getTotalDeuda());
        assertEquals(BigDecimal.ZERO, resultado.getTotalAbonado());
        assertEquals(new BigDecimal("100000"), resultado.getSaldoPendiente());
        assertEquals("activa", resultado.getEstado());
        verify(jpaRepository).save(any(CuentaCobrarEntity.class));
    }

    /**
     * Escenario: Error al crear cuenta por cobrar con cliente inexistente
     * Dado: Un cliente que no existe
     * Cuando: Se intenta crear la cuenta
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenClienteNoExiste() {
        // Arrange
        CreateCuentaCobrarDto dto = new CreateCuentaCobrarDto();
        dto.setClienteId(999L);
        dto.setTotalDeuda(new BigDecimal("100000"));
        dto.setFechaEmision(LocalDateTime.now());

        when(terceroRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaCobrarService.crear(dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("Cliente no encontrado"));
    }

    /**
     * Escenario: Error al crear cuenta por cobrar con tercero que no es cliente
     * Dado: Un tercero que no tiene flag de cliente
     * Cuando: Se intenta crear la cuenta
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenTerceroNoEsCliente() {
        // Arrange
        TerceroEntity terceroNoCliente = new TerceroEntity();
        terceroNoCliente.setId(1L);
        terceroNoCliente.setEsProveedor(true);
        terceroNoCliente.setEsCliente(false);

        CreateCuentaCobrarDto dto = new CreateCuentaCobrarDto();
        dto.setClienteId(1L);
        dto.setTotalDeuda(new BigDecimal("100000"));
        dto.setFechaEmision(LocalDateTime.now());

        when(terceroRepository.findById(1)).thenReturn(Optional.of(terceroNoCliente));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaCobrarService.crear(dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("no es un cliente"));
    }

    /**
     * Escenario: Error al crear cuenta por cobrar con total deuda menor o igual a 0
     * Dado: Un dto con totalDeuda inválido
     * Cuando: Se intenta crear la cuenta
     * Entonces: Lanza excepción 400
     */
    @Test
    void shouldLanzarExcepcionWhenTotalDeudaInvalido() {
        // Arrange
        CreateCuentaCobrarDto dto = new CreateCuentaCobrarDto();
        dto.setClienteId(1L);
        dto.setTotalDeuda(BigDecimal.ZERO);
        dto.setFechaEmision(LocalDateTime.now());

        when(terceroRepository.findById(1)).thenReturn(Optional.of(clienteMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaCobrarService.crear(dto, 1, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("mayor a 0"));
    }

    /**
     * Escenario: Obtener cuenta por cobrar por ID exitosamente
     * Dado: Una cuenta existente
     * Cuando: Se obtiene por ID
     * Entonces: Retorna los datos de la cuenta
     */
    @Test
    void shouldObtenerCuentaCobrarByIdWhenExiste() {
        // Arrange
        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));

        // Act
        CuentaCobrarDto resultado = cuentaCobrarService.obtenerPorId(1L, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        assertEquals("CC-20260226-0001", resultado.getNumeroCuenta());
    }

    /**
     * Escenario: Error al obtener cuenta por cobrar inexistente
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
                () -> cuentaCobrarService.obtenerPorId(999L, 1));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getMessage().contains("no encontrada"));
    }

    /**
     * Escenario: Actualizar cuenta por cobrar exitosamente
     * Dado: Una cuenta existente
     * Cuando: Se actualiza la fecha de vencimiento y observaciones
     * Entonces: Se guardan los cambios
     */
    @Test
    void shouldActualizarCuentaCobrarWhenDatosValidos() {
        // Arrange
        CreateCuentaCobrarDto dto = new CreateCuentaCobrarDto();
        dto.setFechaVencimiento(LocalDateTime.now().plusDays(60));
        dto.setObservaciones("Nueva observación");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));
        when(jpaRepository.save(any(CuentaCobrarEntity.class))).thenReturn(cuentaCobrarMock);

        // Act
        CuentaCobrarDto resultado = cuentaCobrarService.actualizar(1L, dto, 1);

        // Assert
        assertNotNull(resultado);
        verify(jpaRepository).save(any(CuentaCobrarEntity.class));
    }

    // ==================== HU-011: Abonos a Cuentas por Cobrar ====================

    /**
     * Escenario: Registrar abono exitosamente
     * Dado: Una cuenta activa con saldo pendiente
     * Cuando: Se registra un abono menor al saldo
     * Entonces: Se crea el abono y se actualiza el saldo
     */
    @Test
    void shouldRegistrarAbonoWhenMontoMenorASaldo() {
        // Arrange
        AbonoCobrarDto dto = new AbonoCobrarDto();
        dto.setMonto(new BigDecimal("25000"));
        dto.setMetodoPago("efectivo");
        dto.setReferencia("REC-001");

        AbonoCobrarEntity abonoMock = AbonoCobrarEntity.builder()
                .id(1L)
                .cuentaCobrar(cuentaCobrarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("efectivo")
                .referencia("REC-001")
                .fechaPago(LocalDateTime.now())
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(abonoJpaRepository.save(any(AbonoCobrarEntity.class))).thenReturn(abonoMock);
        when(jpaRepository.save(any(CuentaCobrarEntity.class))).thenReturn(cuentaCobrarMock);

        // Act
        AbonoCobrarDto resultado = cuentaCobrarService.registrarAbono(1L, dto, 1, 1L);

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
        cuentaCobrarMock.setSaldoPendiente(new BigDecimal("25000"));

        AbonoCobrarDto dto = new AbonoCobrarDto();
        dto.setMonto(new BigDecimal("25000"));
        dto.setMetodoPago("efectivo");

        AbonoCobrarEntity abonoMock = AbonoCobrarEntity.builder()
                .id(1L)
                .cuentaCobrar(cuentaCobrarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("efectivo")
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuarioMock));
        when(abonoJpaRepository.save(any(AbonoCobrarEntity.class))).thenReturn(abonoMock);
        when(jpaRepository.save(any(CuentaCobrarEntity.class))).thenAnswer(invocation -> {
            CuentaCobrarEntity cuenta = invocation.getArgument(0);
            return cuenta;
        });

        // Act
        AbonoCobrarDto resultado = cuentaCobrarService.registrarAbono(1L, dto, 1, 1L);

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
        cuentaCobrarMock.setEstado("pagada");

        AbonoCobrarDto dto = new AbonoCobrarDto();
        dto.setMonto(new BigDecimal("25000"));
        dto.setMetodoPago("efectivo");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaCobrarService.registrarAbono(1L, dto, 1, 1L));

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
        cuentaCobrarMock.setSaldoPendiente(new BigDecimal("25000"));

        AbonoCobrarDto dto = new AbonoCobrarDto();
        dto.setMonto(new BigDecimal("50000"));
        dto.setMetodoPago("efectivo");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaCobrarService.registrarAbono(1L, dto, 1, 1L));

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
        AbonoCobrarDto dto = new AbonoCobrarDto();
        dto.setMonto(BigDecimal.ZERO);
        dto.setMetodoPago("efectivo");

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaCobrarService.registrarAbono(1L, dto, 1, 1L));

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
        AbonoCobrarEntity abonoMock = AbonoCobrarEntity.builder()
                .id(1L)
                .cuentaCobrar(cuentaCobrarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("efectivo")
                .build();

        List<AbonoCobrarEntity> abonos = new ArrayList<>();
        abonos.add(abonoMock);

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));
        when(abonoJpaRepository.findAll()).thenReturn(abonos);

        // Act
        List<AbonoCobrarDto> resultado = cuentaCobrarService.listarAbonos(1L, 1);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
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
        AbonoCobrarEntity abonoMock = AbonoCobrarEntity.builder()
                .id(1L)
                .cuentaCobrar(cuentaCobrarMock)
                .usuario(usuarioMock)
                .monto(new BigDecimal("25000"))
                .metodoPago("efectivo")
                .createdAt(LocalDateTime.now())
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));
        when(abonoJpaRepository.findByIdAndCuentaCobrarId(1L, 1L)).thenReturn(Optional.of(abonoMock));

        // Act
        cuentaCobrarService.eliminarAbono(1L, 1L, 1);

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
        AbonoCobrarEntity abonoMock = AbonoCobrarEntity.builder()
                .id(1L)
                .cuentaCobrar(cuentaCobrarMock)
                .monto(new BigDecimal("25000"))
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        when(jpaRepository.findByIdAndEmpresaId(1L, 1)).thenReturn(Optional.of(cuentaCobrarMock));
        when(abonoJpaRepository.findByIdAndCuentaCobrarId(1L, 1L)).thenReturn(Optional.of(abonoMock));

        // Act & Assert
        GlobalException exception = assertThrows(GlobalException.class,
                () -> cuentaCobrarService.eliminarAbono(1L, 1L, 1));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("día actual"));
    }

    // ==================== HU-013: Cálculo de Totales de Cuentas ====================

    /**
     * Escenario: Obtener resumen de cuentas por cobrar
     * Dado: Una empresa con cuentas
     * Cuando: Se solicita el resumen
     * Entonces: Retorna totales de deuda, abonos, pendientes y por estado
     */
    @Test
    void shouldObtenerResumenWhenExistenCuentas() {
        // Arrange
        CuentaCobrarResumenDto resumenMock = new CuentaCobrarResumenDto();
        resumenMock.setTotalCuentas(10L);
        resumenMock.setTotalDeuda(new BigDecimal("1000000"));
        resumenMock.setTotalAbonado(new BigDecimal("300000"));
        resumenMock.setSaldoPendiente(new BigDecimal("700000"));
        resumenMock.setCantidadActivas(5L);
        resumenMock.setCantidadPagadas(3L);
        resumenMock.setCantidadVencidas(2L);

        when(queryRepository.obtenerResumen(eq(1), any(), any(), any(), any())).thenReturn(resumenMock);

        // Act
        CuentaCobrarResumenDto resultado = cuentaCobrarService.obtenerResumen(1, null, null, null, null);

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
        CuentaCobrarTableDto cuentaVencida = new CuentaCobrarTableDto();
        cuentaVencida.setId(1L);
        cuentaVencida.setNumeroCuenta("CC-20260101-0001");
        cuentaVencida.setSaldoPendiente(new BigDecimal("50000"));
        cuentaVencida.setEstado("vencida");

        List<CuentaCobrarTableDto> cuentasVencidas = new ArrayList<>();
        cuentasVencidas.add(cuentaVencida);

        when(queryRepository.obtenerVencidas(1)).thenReturn(cuentasVencidas);

        // Act
        List<CuentaCobrarTableDto> resultado = cuentaCobrarService.obtenerVencidas(1);

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

        List<CuentaCobrarTableDto> cuentas = new ArrayList<>();
        cuentas.add(new CuentaCobrarTableDto());

        PageImpl<CuentaCobrarTableDto> pageMock = new PageImpl<>(cuentas);

        when(queryRepository.listar(eq(pageable), eq(1))).thenReturn(pageMock);

        // Act
        PageImpl<CuentaCobrarTableDto> resultado = cuentaCobrarService.listar(pageable, 1);

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

        List<CuentaCobrarTableDto> cuentas = new ArrayList<>();
        cuentas.add(new CuentaCobrarTableDto());

        PageImpl<CuentaCobrarTableDto> pageMock = new PageImpl<>(cuentas);

        when(queryRepository.listarConFiltros(eq(pageable), eq(1), any(), any(), any(), any()))
                .thenReturn(pageMock);

        // Act
        PageImpl<CuentaCobrarTableDto> resultado = cuentaCobrarService.listarConFiltros(
                pageable, 1, "2026-01-01", "2026-12-31", null, "activa");

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getContent().size());
    }
}
