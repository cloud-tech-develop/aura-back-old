package com.cloud_technological.aura_pos.services.implementations;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.CreatePlanCuentaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.PlanCuentaDto;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.PlanCuentasService;

@Service
public class PlanCuentasServiceImpl implements PlanCuentasService {

    @Autowired
    private PlanCuentaJPARepository repo;

    @Autowired
    private com.cloud_technological.aura_pos.services.ConfiguracionContableService configuracionContableService;

    @Autowired
    private com.cloud_technological.aura_pos.services.FormaPagoContableService formaPagoContableService;

    @Autowired
    private com.cloud_technological.aura_pos.services.CategoriaContableProductoService categoriaContableProductoService;

    @Autowired
    private com.cloud_technological.aura_pos.services.ImpuestoService impuestoService;

    @Autowired
    private com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaService exogenaService;

    @Override
    public List<PlanCuentaDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdOrderByCodigoAsc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<PlanCuentaDto> listarMediosPago(Integer empresaId) {
        return repo
                .findByEmpresaIdAndEsMedioPagoTrueAndActivaTrueAndAuxiliarTrueOrderByCodigoAsc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PlanCuentaDto crear(Integer empresaId, CreatePlanCuentaDto dto) {
        if (repo.existsByEmpresaIdAndCodigo(empresaId, dto.getCodigo().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cuenta con el código " + dto.getCodigo());
        }
        PlanCuentaEntity e = PlanCuentaEntity.builder()
                .empresaId(empresaId)
                .codigo(dto.getCodigo().trim())
                .nombre(dto.getNombre().trim())
                .tipo(dto.getTipo())
                .naturaleza(dto.getNaturaleza())
                .nivel(dto.getNivel())
                .padreId(dto.getPadreId())
                .auxiliar(dto.getAuxiliar() != null && dto.getAuxiliar())
                .esMedioPago(dto.getEsMedioPago() != null && dto.getEsMedioPago())
                .codigoDian(dto.getCodigoDian())
                .activa(true)
                .build();
        return toDto(repo.save(e));
    }

    @Override
    @Transactional
    public PlanCuentaDto actualizar(Long id, Integer empresaId, CreatePlanCuentaDto dto) {
        PlanCuentaEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        e.setNombre(dto.getNombre().trim());
        e.setTipo(dto.getTipo());
        e.setNaturaleza(dto.getNaturaleza());
        e.setNivel(dto.getNivel());
        e.setPadreId(dto.getPadreId());
        if (dto.getAuxiliar() != null) e.setAuxiliar(dto.getAuxiliar());
        if (dto.getEsMedioPago() != null) e.setEsMedioPago(dto.getEsMedioPago());
        e.setCodigoDian(dto.getCodigoDian());
        return toDto(repo.save(e));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        PlanCuentaEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        e.setActiva(false);
        repo.save(e);
    }

    /**
     * Carga el plan de cuentas completo (puc/puc_comerciantes.csv: clases 1 a 9
     * con grupos, cuentas, subcuentas y auxiliares) agregando las que falten, y
     * siembra la configuración por defecto sobre sus auxiliares
     * (puc/equivalencias.csv). Nunca cambia una cuenta que
     * ya existe (nombre ni si recibe movimiento): la configuración contable y los
     * asientos que ya apuntan a ella siguen igual. Devuelve cuántas agregó.
     */
    @Override
    @Transactional
    public int seedPUC(Integer empresaId) {
        int antes = repo.findByEmpresaIdOrderByCodigoAsc(empresaId).size();
        // Empresa nueva: solo el catálogo, con sus auxiliares de 8 dígitos. La
        // configuración por defecto se cuelga de esas auxiliares
        // (puc/equivalencias.csv) en vez de crear las cuentas del plan básico
        // (4135, 240801…) que el catálogo no trae.
        completarPuc(empresaId);
        int nuevas = repo.findByEmpresaIdOrderByCodigoAsc(empresaId).size() - antes;
        // Siembra/actualiza el mapeo concepto→cuenta por defecto (idempotente),
        // también para empresas que ya tenían PUC pero no configuración.
        configuracionContableService.seedDefaults(empresaId);
        formaPagoContableService.seedDefaults(empresaId);
        categoriaContableProductoService.seedDefaults(empresaId);
        impuestoService.seedDefaults(empresaId);
        exogenaService.seedDefaults(empresaId);
        return nuevas;
    }

    // ── Catálogo de cuentas completo ─────────────────────────────────────────

    /** Recurso con el catálogo oficial: "codigo;nombre", líneas # = comentario. */
    private static final String RECURSO_PUC = "puc/puc_comerciantes.csv";

    /** Agrega las cuentas oficiales que la empresa no tiene, padres antes que hijas. */
    private void completarPuc(Integer empresaId) {
        java.util.Map<String, String> oficial = leerPuc();
        java.util.Map<String, PlanCuentaEntity> existentes = new java.util.HashMap<>();
        for (PlanCuentaEntity e : repo.findByEmpresaIdOrderByCodigoAsc(empresaId)) existentes.put(e.getCodigo(), e);

        java.util.Set<String> todos = new java.util.HashSet<>(existentes.keySet());
        todos.addAll(oficial.keySet());
        // Una cuenta con hijas agrupa; la hoja es la que recibe movimiento.
        java.util.Set<String> conHijas = new java.util.HashSet<>();
        for (String c : todos) {
            String p = padreDe(c, todos);
            if (p != null) conHijas.add(p);
        }

        java.util.Map<String, Long> ids = new java.util.HashMap<>();
        existentes.forEach((c, e) -> ids.put(c, e.getId()));
        List<String> faltantes = oficial.keySet().stream()
                .filter(c -> !existentes.containsKey(c))
                .sorted(java.util.Comparator.comparingInt(String::length).thenComparing(c -> c))
                .collect(Collectors.toList());
        for (String codigo : faltantes) {
            String nombre = oficial.get(codigo);
            String padre = padreDe(codigo, todos);
            boolean auxiliar = !conHijas.contains(codigo);
            PlanCuentaEntity e = PlanCuentaEntity.builder()
                    .empresaId(empresaId)
                    .codigo(codigo)
                    .nombre(nombre)
                    .tipo(tipoDeClase(codigo.charAt(0)))
                    .naturaleza(naturalezaPuc(codigo, nombre))
                    .nivel(nivelDe(codigo))
                    .padreId(padre != null ? ids.get(padre) : null)
                    .activa(true)
                    .auxiliar(auxiliar)
                    .esMedioPago(auxiliar && esDisponible(codigo))
                    .build();
            ids.put(codigo, repo.save(e).getId());
        }

        // Cuentas creadas antes por otros procesos (nómina, activos, cierre) que
        // quedaron sin padre y salían sueltas en la raíz del árbol: se cuelgan de
        // su prefijo más largo. Solo se llena el padre que falta, nada más.
        java.util.Set<String> codigos = ids.keySet();
        for (PlanCuentaEntity e : existentes.values()) {
            if (e.getCodigo().length() <= 1) continue;
            if (e.getPadreId() != null && ids.containsValue(e.getPadreId())) continue;
            String padre = padreDe(e.getCodigo(), codigos);
            if (padre == null) continue;
            e.setPadreId(ids.get(padre));
            repo.save(e);
        }
    }

    private static java.util.Map<String, String> leerPuc() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        try (var in = PlanCuentasServiceImpl.class.getClassLoader().getResourceAsStream(RECURSO_PUC)) {
            if (in == null) throw new IllegalStateException("No se encontró el recurso " + RECURSO_PUC);
            var lector = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank() || linea.startsWith("#") || linea.startsWith("codigo;")) continue;
                int i = linea.indexOf(';');
                if (i <= 0) continue;
                m.put(linea.substring(0, i).trim(), linea.substring(i + 1).trim());
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("No se pudo leer el PUC", ex);
        }
        return m;
    }

    /** El padre es el prefijo más largo que exista (6 → 4 → 2 → 1 dígitos). */
    static String padreDe(String codigo, java.util.Set<String> todos) {
        for (int largo = codigo.length() - 1; largo >= 1; largo--) {
            String p = codigo.substring(0, largo);
            if (todos.contains(p)) return p;
        }
        return null;
    }

    /** Clase 1, grupo 2, cuenta 3, subcuenta 4, auxiliar 5 (8 dígitos o más). */
    static short nivelDe(String codigo) {
        return switch (codigo.length()) {
            case 1 -> (short) 1;
            case 2 -> (short) 2;
            case 3, 4 -> (short) 3;
            case 5, 6 -> (short) 4;
            default -> (short) 5;
        };
    }

    static String tipoDeClase(char clase) {
        return switch (clase) {
            case '1' -> "ACTIVO";
            case '2' -> "PASIVO";
            case '3' -> "PATRIMONIO";
            case '4' -> "INGRESO";
            case '5' -> "GASTO";
            case '6', '7' -> "COSTO";
            default -> "ORDEN";
        };
    }

    /**
     * Naturaleza según la clase, con las cuentas correctoras del PUC al revés:
     * provisiones, depreciación, amortización y agotamiento acumulados del activo
     * son crédito; devoluciones en ventas (4175) débito; las "por contra" de orden
     * (83 deudoras por contra, 93 acreedoras por contra) van al revés de su clase.
     */
    static String naturalezaPuc(String codigo, String nombre) {
        String n = nombre.toLowerCase();
        if (codigo.startsWith("83")) return "CREDITO";
        if (codigo.startsWith("93")) return "DEBITO";
        if (codigo.startsWith("4175")) return "DEBITO";
        if (codigo.charAt(0) == '1' && (n.contains("provisi") || n.contains("depreciaci")
                || n.contains("amortizaci") || n.contains("agotamiento") || n.contains("deterioro"))) {
            return "CREDITO";
        }
        return switch (codigo.charAt(0)) {
            case '1', '5', '6', '7', '8' -> "DEBITO";
            default -> "CREDITO";
        };
    }

    /**
     * El disponible del PUC con el que efectivamente se paga: caja (incluida la
     * caja menor), bancos y cuentas de ahorro. Se deja fuera 1115 (remesas en
     * tránsito) a propósito: es una cuenta puente de recaudo, no un medio de pago.
     */
    private static boolean esDisponible(String codigo) {
        return codigo.startsWith("1105") || codigo.startsWith("1110") || codigo.startsWith("1120");
    }

    private PlanCuentaDto toDto(PlanCuentaEntity e) {
        PlanCuentaDto dto = new PlanCuentaDto();
        dto.setId(e.getId());
        dto.setCodigo(e.getCodigo());
        dto.setNombre(e.getNombre());
        dto.setTipo(e.getTipo());
        dto.setNaturaleza(e.getNaturaleza());
        dto.setNivel(e.getNivel());
        dto.setPadreId(e.getPadreId());
        dto.setActiva(e.getActiva());
        dto.setAuxiliar(e.getAuxiliar());
        dto.setEsMedioPago(e.getEsMedioPago());
        dto.setCodigoDian(e.getCodigoDian());
        return dto;
    }
}
