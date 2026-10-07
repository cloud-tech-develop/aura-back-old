package com.cloud_technological.aura_pos.services.empresa;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.ActualizarConfiguracionDto;
import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.ConfiguracionEmpresaDto;
import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.EstadoPasoDto;
import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.LineaUsoDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloPermisoDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloPermisoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.ModuloQueryRepository;
import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Líneas de uso de la empresa (docs/PLAN_PERFIL_EMPRESA.md): leerlas, cambiarlas,
 * validar que tengan sus pantallas, armar la plantilla del árbol de módulos y
 * disparar el arranque. Nadie más toca {@code empresa.configuracion}.
 */
@Service
public class ConfiguracionEmpresaService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionEmpresaService.class);

    private final EmpresaJPARepository empresaRepo;
    private final ModuloQueryRepository moduloQuery;
    private final PermisoService permisoService;
    private final ArranqueEmpresaService arranque;
    private final BitacoraService bitacora;

    public ConfiguracionEmpresaService(EmpresaJPARepository empresaRepo, ModuloQueryRepository moduloQuery,
            PermisoService permisoService, ArranqueEmpresaService arranque, BitacoraService bitacora) {
        this.empresaRepo = empresaRepo;
        this.moduloQuery = moduloQuery;
        this.permisoService = permisoService;
        this.arranque = arranque;
        this.bitacora = bitacora;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    /** Líneas efectivas: las declaradas o, si no declaró, la línea por defecto. */
    public List<LineaUso> lineas(EmpresaEntity empresa) {
        List<LineaUso> l = ConfiguracionEmpresaJson.lineas(ConfiguracionEmpresaJson.leer(empresa.getConfiguracion()));
        return l.isEmpty() ? List.of(LineaUso.POR_DEFECTO) : l;
    }

    /** Tablero de inicio: el preferido si sigue entre sus líneas, si no el de la primera. */
    public LineaUso inicio(EmpresaEntity empresa) {
        List<LineaUso> lineas = lineas(empresa);
        LineaUso preferido = ConfiguracionEmpresaJson.inicio(ConfiguracionEmpresaJson.leer(empresa.getConfiguracion()));
        return preferido != null && lineas.contains(preferido) ? preferido : lineas.get(0);
    }

    public ConfiguracionEmpresaDto obtener(Integer empresaId) {
        return aDto(buscar(empresaId));
    }

    /** Catálogo de líneas con los ids de submódulo de su plantilla, para el panel. */
    public List<LineaUsoDto> catalogo() {
        List<ModuloPermisoDto> cat = moduloQuery.listarPermisosPorEmpresa(null);
        List<LineaUsoDto> out = new ArrayList<>();
        for (LineaUso l : LineaUso.values()) {
            LineaUsoDto d = new LineaUsoDto();
            d.setCodigo(l.name());
            d.setNombre(l.getNombre());
            d.setDescripcion(l.getDescripcion());
            d.setSubmodulos(new ArrayList<>(plantilla(cat, List.of(l))));
            d.setMinimos(new ArrayList<>(ids(cat, l.getMinimos())));
            out.add(d);
        }
        return out;
    }

    /** Ids de submódulos de la plantilla de esas líneas, más la base común. */
    public Set<Integer> plantilla(Collection<LineaUso> lineas) {
        return plantilla(moduloQuery.listarPermisosPorEmpresa(null), lineas);
    }

    // ── Escritura ────────────────────────────────────────────────────────────

    /**
     * Al crear la empresa. Con líneas declaradas: las guarda y exige sus pantallas
     * mínimas (los submódulos ya deben estar activados). Sin líneas (panel viejo,
     * autoregistro): no declara nada —queda la línea por defecto— pero igual arranca
     * la contabilidad, que toda empresa necesita.
     */
    @Transactional
    public void inicializar(EmpresaEntity empresa, List<LineaUso> declaradas) {
        List<LineaUso> efectivas = declaradas.isEmpty() ? List.of(LineaUso.POR_DEFECTO) : declaradas;
        if (!declaradas.isEmpty()) {
            exigirMinimos(empresa.getId(), declaradas);
            Map<String, Object> cfg = ConfiguracionEmpresaJson.leer(empresa.getConfiguracion());
            ConfiguracionEmpresaJson.escribirLineas(cfg, declaradas, null);
            empresa.setConfiguracion(ConfiguracionEmpresaJson.aColumna(cfg));
            empresaRepo.save(empresa);
        }
        arranque.programar(empresa.getId(), ArranqueEmpresaService.pasosDe(efectivas));
    }

    /** Cambio desde el panel de plataforma. Las líneas nuevas arrancan al confirmar. */
    @Transactional
    public ConfiguracionEmpresaDto actualizar(Integer empresaId, ActualizarConfiguracionDto dto) {
        EmpresaEntity empresa = buscar(empresaId);
        List<LineaUso> lineas = parsear(dto.getLineas());
        if (lineas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija al menos una línea de uso");
        LineaUso inicio = null;
        if (dto.getInicio() != null && !dto.getInicio().isBlank()) {
            inicio = LineaUso.de(dto.getInicio())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Tablero de inicio desconocido: " + dto.getInicio()));
            if (!lineas.contains(inicio))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El tablero de inicio debe ser de una de las líneas elegidas");
        }

        if (dto.isCompletarModulos()) completarModulos(empresaId, lineas);
        exigirMinimos(empresaId, lineas);

        Map<String, Object> cfg = ConfiguracionEmpresaJson.leer(empresa.getConfiguracion());
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("lineas", lineas(empresa).stream().map(Enum::name).toList());
        antes.put("inicio", cfg.get(ConfiguracionEmpresaJson.K_INICIO));

        ConfiguracionEmpresaJson.escribirLineas(cfg, lineas, inicio);
        empresa.setConfiguracion(ConfiguracionEmpresaJson.aColumna(cfg));
        empresaRepo.save(empresa);

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("lineas", lineas.stream().map(Enum::name).toList());
        despues.put("inicio", inicio != null ? inicio.name() : null);
        bitacora.registrarEnEmpresa(empresaId, "plataforma.empresas", "CONFIGURAR", "empresa", empresaId,
                "Líneas de uso: " + String.join(", ", lineas.stream().map(LineaUso::getNombre).toList()),
                antes, despues);

        // Solo lo que no ha terminado bien: lo que ya está OK no se repite.
        Set<PasoArranque> pendientes = new LinkedHashSet<>();
        for (PasoArranque p : ArranqueEmpresaService.pasosDe(lineas)) {
            Map<String, Object> estado = ConfiguracionEmpresaJson.estadoPaso(cfg, p);
            if (estado == null || !ConfiguracionEmpresaJson.OK.equals(estado.get("resultado"))) pendientes.add(p);
        }
        arranque.programar(empresaId, pendientes);
        return aDto(empresa);
    }

    /** Vuelve a correr todo el arranque de sus líneas (idempotente), ya mismo. */
    public ConfiguracionEmpresaDto reintentarArranque(Integer empresaId) {
        EmpresaEntity empresa = buscar(empresaId);
        arranque.ejecutar(empresaId, ArranqueEmpresaService.pasosDe(lineas(empresa)));
        return obtener(empresaId);
    }

    /** Convierte los códigos que llegan del front; un código desconocido es error, no se ignora. */
    public static List<LineaUso> parsear(List<String> codigos) {
        List<LineaUso> out = new ArrayList<>();
        if (codigos == null) return out;
        for (String c : codigos) {
            if (c == null || c.isBlank()) continue;
            LineaUso l = LineaUso.de(c).orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                    "Línea de uso desconocida: " + c));
            if (!out.contains(l)) out.add(l);
        }
        out.sort(null);
        return out;
    }

    // ── Validación de pantallas ──────────────────────────────────────────────

    private void exigirMinimos(Integer empresaId, List<LineaUso> lineas) {
        List<ModuloPermisoDto> activos = moduloQuery.listarPermisosPorEmpresa(empresaId);
        Map<String, SubmoduloPermisoDto> porClave = indice(activos);
        List<String> faltan = new ArrayList<>();
        for (LineaUso l : lineas) {
            List<String> nombres = new ArrayList<>();
            for (String clave : l.getMinimos()) {
                SubmoduloPermisoDto s = porClave.get(clave);
                if (s == null) {
                    // El catálogo de producción no siempre sigue los códigos locales
                    // (gotcha del módulo Caja): no se bloquea por una clave que no existe.
                    log.warn("Línea {}: la pantalla mínima {} no existe en el catálogo de submódulos", l, clave);
                    continue;
                }
                if (!Boolean.TRUE.equals(s.getActivo())) nombres.add(s.getSubmoduloNombre());
            }
            if (!nombres.isEmpty()) faltan.add(l.getNombre() + " necesita: " + String.join(", ", nombres));
        }
        if (!faltan.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, String.join(". ", faltan)
                    + ". Actívelas en los módulos de la empresa o marque \"completar módulos\".");
    }

    /** Suma la plantilla a lo que ya tiene activo; no apaga nada. */
    private void completarModulos(Integer empresaId, List<LineaUso> lineas) {
        List<ModuloPermisoDto> actual = moduloQuery.listarPermisosPorEmpresa(empresaId);
        Set<Integer> ids = new LinkedHashSet<>();
        for (ModuloPermisoDto m : actual)
            for (SubmoduloPermisoDto s : m.getSubmodulos())
                if (Boolean.TRUE.equals(s.getActivo())) ids.add(s.getSubmoduloId());
        ids.addAll(plantilla(actual, lineas));
        permisoService.activarSubmodulos(empresaId, ids);
        // Los cambios a filas existentes siguen en memoria de JPA; la validación
        // lee por JDBC y debe verlos.
        empresaRepo.flush();
    }

    // ── Catálogo de submódulos ───────────────────────────────────────────────

    private static Set<Integer> plantilla(List<ModuloPermisoDto> catalogo, Collection<LineaUso> lineas) {
        List<String> patrones = new ArrayList<>(LineaUso.BASE);
        for (LineaUso l : lineas) {
            patrones.addAll(l.getPlantilla());
            patrones.addAll(l.getMinimos());
        }
        return ids(catalogo, patrones);
    }

    /** Ids de los submódulos que casan con las claves ({@code modulo.*} = el módulo entero). */
    private static Set<Integer> ids(List<ModuloPermisoDto> catalogo, Collection<String> patrones) {
        Set<Integer> out = new LinkedHashSet<>();
        for (ModuloPermisoDto m : catalogo) {
            String mod = normalizar(m.getModuloCodigo());
            for (SubmoduloPermisoDto s : m.getSubmodulos()) {
                String clave = mod + "." + normalizar(s.getSubmoduloCodigo());
                for (String p : patrones) {
                    if (p.equals(mod + ".*") || p.equals(clave)) {
                        out.add(s.getSubmoduloId());
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static Map<String, SubmoduloPermisoDto> indice(List<ModuloPermisoDto> catalogo) {
        Map<String, SubmoduloPermisoDto> out = new LinkedHashMap<>();
        for (ModuloPermisoDto m : catalogo) {
            String mod = normalizar(m.getModuloCodigo());
            for (SubmoduloPermisoDto s : m.getSubmodulos())
                out.put(mod + "." + normalizar(s.getSubmoduloCodigo()), s);
        }
        return out;
    }

    /** "Recursos Humanos" → "recursos-humanos"; "Caja" → "caja". */
    static String normalizar(String codigo) {
        if (codigo == null) return "";
        String sinTildes = Normalizer.normalize(codigo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().toLowerCase().replaceAll("[\\s_]+", "-");
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private EmpresaEntity buscar(Integer empresaId) {
        if (empresaId == null) throw new GlobalException(HttpStatus.BAD_REQUEST, "Empresa no indicada");
        return empresaRepo.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
    }

    private ConfiguracionEmpresaDto aDto(EmpresaEntity empresa) {
        Map<String, Object> cfg = ConfiguracionEmpresaJson.leer(empresa.getConfiguracion());
        List<LineaUso> declaradas = ConfiguracionEmpresaJson.lineas(cfg);
        List<LineaUso> efectivas = lineas(empresa);

        ConfiguracionEmpresaDto d = new ConfiguracionEmpresaDto();
        d.setEmpresaId(empresa.getId());
        d.setVersion(ConfiguracionEmpresaJson.VERSION);
        d.setLineas(efectivas.stream().map(Enum::name).toList());
        d.setDeclarada(!declaradas.isEmpty());
        LineaUso preferido = ConfiguracionEmpresaJson.inicio(cfg);
        d.setInicio(preferido != null ? preferido.name() : null);
        d.setInicioResuelto(inicio(empresa).name());

        boolean pendiente = false;
        for (PasoArranque p : ArranqueEmpresaService.pasosDe(efectivas)) {
            Map<String, Object> e = ConfiguracionEmpresaJson.estadoPaso(cfg, p);
            EstadoPasoDto ep = new EstadoPasoDto();
            ep.setPaso(p.name());
            ep.setNombre(p.getNombre());
            ep.setResultado(e != null && e.get("resultado") != null
                    ? e.get("resultado").toString() : ConfiguracionEmpresaJson.PENDIENTE);
            ep.setEjecutado(e != null && e.get("ejecutado") != null ? e.get("ejecutado").toString() : null);
            ep.setDetalle(e != null && e.get("detalle") != null ? e.get("detalle").toString() : null);
            pendiente |= !ConfiguracionEmpresaJson.OK.equals(ep.getResultado());
            d.getArranque().add(ep);
        }
        d.setArranquePendiente(pendiente);
        return d;
    }
}
