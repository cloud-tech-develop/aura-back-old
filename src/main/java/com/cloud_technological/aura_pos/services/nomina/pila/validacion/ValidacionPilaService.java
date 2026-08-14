package com.cloud_technological.aura_pos.services.nomina.pila.validacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.pila.ValidacionPilaDtos.CotizanteValidacionDto;
import com.cloud_technological.aura_pos.dto.nomina.pila.ValidacionPilaDtos.HallazgoDto;
import com.cloud_technological.aura_pos.dto.nomina.pila.ValidacionPilaDtos.ReporteValidacionDto;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.PilaCatalogoEntity;
import com.cloud_technological.aura_pos.entity.PilaCotizanteEntity;
import com.cloud_technological.aura_pos.entity.PilaEncabezadoEntity;
import com.cloud_technological.aura_pos.entity.PilaPlanillaEntity;
import com.cloud_technological.aura_pos.entity.PilaEntidadEntity;
import com.cloud_technological.aura_pos.entity.PilaTipoCotizanteEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaCatalogoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaCotizanteRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaEncabezadoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaEntidadRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaPlanillaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaTipoCotizanteRepo;

/**
 * Motor de validación de PILA — P1 (plan {@code docs/PLAN_VALIDACION_PILA.md}).
 *
 * <p>NO liquida, NO corrige, NO inventa catálogos ni tarifas. Solo revisa el
 * modelo ya generado ({@code pila_cotizante}) y produce hallazgos clasificados
 * (ERROR / ADVERTENCIA / INFORMACIÓN / NO_EVALUABLE) con estados por cotizante y
 * por planilla, según el agente validador tipo UGPP.
 *
 * <p><b>Alcance P1:</b> reglas que NO dependen de catálogos oficiales: contexto,
 * identificación, días (0–30 y coherencia con novedades), fechas, rangos de IBC
 * (mínimo/tope con SMMLV) y detección de duplicados. Todo lo que requiera un
 * catálogo o matriz oficial (validez de tipo de cotizante, vigencia de entidad,
 * tarifas del período, compatibilidades) se marca {@code NO_EVALUABLE} hasta P2.
 */
@Service
public class ValidacionPilaService {

    private final PilaEncabezadoRepo encabezadoRepo;
    private final PilaPlanillaRepo planillaRepo;
    private final PilaCotizanteRepo cotizanteRepo;
    private final NominaConfigJPARepository configRepo;
    private final PilaTipoCotizanteRepo tipoCotizanteRepo;
    private final PilaEntidadRepo entidadRepo;
    private final PilaCatalogoRepo catalogoRepo;

    public ValidacionPilaService(PilaEncabezadoRepo encabezadoRepo,
                                 PilaPlanillaRepo planillaRepo,
                                 PilaCotizanteRepo cotizanteRepo,
                                 NominaConfigJPARepository configRepo,
                                 PilaTipoCotizanteRepo tipoCotizanteRepo,
                                 PilaEntidadRepo entidadRepo,
                                 PilaCatalogoRepo catalogoRepo) {
        this.encabezadoRepo = encabezadoRepo;
        this.planillaRepo = planillaRepo;
        this.cotizanteRepo = cotizanteRepo;
        this.configRepo = configRepo;
        this.tipoCotizanteRepo = tipoCotizanteRepo;
        this.entidadRepo = entidadRepo;
        this.catalogoRepo = catalogoRepo;
    }

    @Transactional(readOnly = true)
    public ReporteValidacionDto validar(Integer empresaId, String periodo) {
        PilaEncabezadoEntity enc = encabezadoRepo.findByEmpresaIdAndPeriodo(empresaId, periodo).orElse(null);

        // Sin planilla generada no hay nada que validar → NO EVALUABLE.
        if (enc == null) {
            return reporteVacio(periodo, hallazgo("PILA-ARC-008", SeveridadPila.NO_EVALUABLE,
                    "Planilla", "No generada",
                    "No existe una planilla de PILA generada para el período.",
                    "Generar la planilla del período antes de validarla.",
                    "Sin modelo generado no hay registros que revisar.",
                    "El período no puede considerarse aprobado."));
        }

        List<PilaPlanillaEntity> planillas = planillaRepo.findByEncabezadoId(enc.getId());
        PilaPlanillaEntity planilla = planillas.isEmpty() ? null : planillas.get(0);
        List<PilaCotizanteEntity> cotizantes = planilla != null
                ? cotizanteRepo.findByPlanillaIdOrderBySecuencia(planilla.getId())
                : List.of();

        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId).orElse(null);

        // ── Contexto de la planilla (Paso 1) ────────────────────────────────
        List<HallazgoDto> hPlanilla = new ArrayList<>();
        BigDecimal smmlv = config != null ? config.getSmmlv() : null;
        if (smmlv == null || smmlv.signum() <= 0) {
            hPlanilla.add(hallazgo("PILA-CTX-004", SeveridadPila.NO_EVALUABLE,
                    "SMMLV", str(smmlv),
                    "No se conoce el SMMLV del período: no se pueden validar mínimos ni topes de IBC.",
                    "Configurar el SMMLV vigente en la configuración de nómina.",
                    "Los topes y mínimos de IBC dependen del SMMLV del período.",
                    "Las validaciones de IBC quedan sin evaluar."));
        }
        validarContextoCodigo(planilla != null ? planilla.getTipoPlanilla() : null,
                cargarCatalogo("TIPO_PLANILLA"), "tipo de planilla",
                "PILA-PLA-001", "PILA-PLA-002", hPlanilla);
        validarContextoCodigo(enc.getTipoAportante(),
                cargarCatalogo("TIPO_APORTANTE"), "tipo de aportante",
                "PILA-APO-001", "PILA-APO-002", hPlanilla);

        BigDecimal tope = null;
        if (smmlv != null && config != null) {
            BigDecimal topeSmmlv = config.getTopeIbcSmmlv() != null ? config.getTopeIbcSmmlv() : new BigDecimal("25");
            tope = smmlv.multiply(topeSmmlv);
        }
        // Umbral de exoneración (Ley 1607): por defecto 10 SMMLV, por empleado.
        BigDecimal umbralExo = (smmlv != null && config != null && config.getUmbralExoneracionSmmlv() != null)
                ? smmlv.multiply(config.getUmbralExoneracionSmmlv()) : null;

        // Catálogo global de tipos de cotizante (P2). Vacío ⇒ tipo NO_EVALUABLE.
        Map<String, PilaTipoCotizanteEntity> catalogoTipos = new LinkedHashMap<>();
        for (PilaTipoCotizanteEntity t : tipoCotizanteRepo.findAll()) {
            catalogoTipos.put(t.getCodigo(), t);
        }

        // Catálogo global de entidades por tipo (P2b). Tipo sin filas ⇒ solo
        // se valida presencia, no código/vigencia (no se inventan códigos).
        Map<String, Map<String, PilaEntidadEntity>> catalogoEntidades = new LinkedHashMap<>();
        for (PilaEntidadEntity e : entidadRepo.findAll()) {
            catalogoEntidades.computeIfAbsent(e.getTipo(), k -> new LinkedHashMap<>()).put(e.getCodigo(), e);
        }
        LocalDate periodoDate = parsePeriodo(periodo);
        LocalDate periodoFin = periodoDate != null
                ? periodoDate.withDayOfMonth(periodoDate.lengthOfMonth()) : null;

        // ── Por cotizante ───────────────────────────────────────────────────
        List<CotizanteValidacionDto> resultados = new ArrayList<>();
        Map<String, List<PilaCotizanteEntity>> porDocumento = new LinkedHashMap<>();

        for (PilaCotizanteEntity c : cotizantes) {
            porDocumento.computeIfAbsent(clave(c), k -> new ArrayList<>()).add(c);
        }

        for (PilaCotizanteEntity c : cotizantes) {
            PilaTipoCotizanteEntity tipo = c.getTipoCotizante() != null
                    ? catalogoTipos.get(c.getTipoCotizante()) : null;
            List<HallazgoDto> hs = new ArrayList<>();
            validarIdentificacion(c, hs);
            validarClasificacion(c, tipo, hs);
            validarEntidades(c, tipo, catalogoEntidades, periodoDate, hs);
            validarDias(c, hs);
            validarFechas(c, periodoDate, periodoFin, hs);
            validarNovedades(c, hs);
            validarIbc(c, smmlv, tope, hs);
            validarValores(c, umbralExo, hs);
            validarDuplicados(c, porDocumento.get(clave(c)), hs);

            if (hs.isEmpty()) {
                hs.add(hallazgo("PILA-OK-001", SeveridadPila.INFORMACION, "Cotizante", "",
                        "El cotizante cumple las validaciones aplicables con la información suministrada.",
                        "No se requieren ajustes antes de preparar el archivo PILA.", "", ""));
            }
            hs.sort(Comparator.comparingInt(x -> x.getSeveridad().getOrden()));

            resultados.add(CotizanteValidacionDto.builder()
                    .secuencia(c.getSecuencia())
                    .nombreCompleto(nombre(c))
                    .tipoDocumento(c.getTipoDocumento())
                    .numeroIdentificacion(c.getNumeroIdentificacion())
                    .tipoCotizante(c.getTipoCotizante())
                    .subtipoCotizante(c.getSubtipoCotizante())
                    .estado(estadoCotizante(hs))
                    .hallazgos(hs)
                    .build());
        }

        validarEncabezadoYTotales(enc, planilla, cotizantes, hPlanilla);

        return consolidar(periodo, enc, planilla, hPlanilla, resultados);
    }

    /** Sección 9 — encabezado completo y coherencia de totales. */
    private void validarEncabezadoYTotales(PilaEncabezadoEntity enc, PilaPlanillaEntity planilla,
                                           List<PilaCotizanteEntity> cotizantes, List<HallazgoDto> hs) {
        // 9.1 Encabezado: datos del aportante que el operador exige.
        if (blank(enc.getRepLegalDocumento()) || blank(enc.getRepLegalApellido1())
                || blank(enc.getRepLegalNombre1())) {
            hs.add(hallazgo("PILA-APO-005", SeveridadPila.ADVERTENCIA, "Representante legal", "Incompleto",
                    "Falta el representante legal del aportante (documento, apellidos o nombres).",
                    "Completar los datos del representante legal antes de generar el archivo.",
                    "El operador exige el representante legal desagregado.",
                    "El archivo puede ser rechazado por el operador."));
        }
        if (blank(enc.getCodActividadEconomica())) {
            hs.add(hallazgo("PILA-ARC-001", SeveridadPila.ADVERTENCIA, "Actividad económica", "Vacío",
                    "El encabezado no tiene código de actividad económica.",
                    "Registrar el código de actividad económica del aportante.",
                    "El operador exige la actividad económica en el encabezado.",
                    "El archivo puede ser rechazado por el operador."));
        }

        if (planilla == null) return;

        // 9.3 Totales: encabezado ↔ detalle ↔ totales.
        int n = cotizantes.size();
        if (planilla.getTotalEmpleados() != null && planilla.getTotalEmpleados() != n) {
            hs.add(hallazgo("PILA-ARC-003", SeveridadPila.ERROR, "Total de cotizantes",
                    planilla.getTotalEmpleados() + " vs " + n,
                    "El total de cotizantes del registro 01 (" + planilla.getTotalEmpleados()
                            + ") no coincide con el número de registros 02 (" + n + ").",
                    "Regenerar la planilla para cuadrar el total con el detalle.",
                    "El total del encabezado debe coincidir con el detalle.",
                    "El operador rechaza el archivo descuadrado."));
        }

        BigDecimal sumaIbcPension = BigDecimal.ZERO;
        for (PilaCotizanteEntity c : cotizantes) {
            sumaIbcPension = sumaIbcPension.add(c.getIbcPension() != null ? c.getIbcPension() : BigDecimal.ZERO);
        }
        if (planilla.getTotalNomina() != null
                && planilla.getTotalNomina().compareTo(sumaIbcPension) != 0) {
            hs.add(hallazgo("PILA-ARC-004", SeveridadPila.ERROR, "Total planilla",
                    str(planilla.getTotalNomina()) + " vs " + str(sumaIbcPension),
                    "El total de la planilla no coincide con la suma de los IBC de pensión del detalle.",
                    "Regenerar la planilla para cuadrar el total con el detalle.",
                    "Los totales del encabezado deben cuadrar con el detalle.",
                    "El operador rechaza el archivo descuadrado."));
        }
    }

    // ── Reglas por cotizante ────────────────────────────────────────────────

    /** Paso 5 — identificación. */
    private void validarIdentificacion(PilaCotizanteEntity c, List<HallazgoDto> hs) {
        if (blank(c.getTipoDocumento())) {
            hs.add(hallazgo("PILA-ID-001", SeveridadPila.ERROR, "Tipo de documento", "Vacío",
                    "El cotizante no tiene tipo de documento.",
                    "Registrar el tipo de documento del cotizante.",
                    "El tipo de documento es obligatorio en el registro tipo 02.",
                    "El registro puede ser rechazado por el operador."));
        }
        String num = c.getNumeroIdentificacion();
        if (blank(num)) {
            hs.add(hallazgo("PILA-ID-003", SeveridadPila.ERROR, "Número de documento", "Vacío",
                    "El cotizante no tiene número de documento.",
                    "Registrar el número de documento del cotizante.",
                    "El número de documento es obligatorio.",
                    "El registro puede ser rechazado por el operador."));
        } else {
            if (!num.equals(num.trim()) || num.contains(" ")) {
                hs.add(hallazgo("PILA-ID-004", SeveridadPila.ERROR, "Número de documento", num,
                        "El número de documento contiene espacios.",
                        "Eliminar espacios del número de documento.",
                        "El documento no admite espacios ni caracteres inválidos.",
                        "El operador puede rechazar el registro."));
            } else if (esDocumentoNumerico(c.getTipoDocumento()) && !num.matches("\\d+")) {
                hs.add(hallazgo("PILA-ID-004", SeveridadPila.ERROR, "Número de documento", num,
                        "El número de documento debe ser numérico para el tipo " + c.getTipoDocumento() + ".",
                        "Verificar el número de documento.",
                        "El formato del documento debe ser compatible con su tipo.",
                        "El operador puede rechazar el registro."));
            }
        }
        if (blank(c.getApellido1()) || blank(c.getNombre1())) {
            hs.add(hallazgo("PILA-ID-005", SeveridadPila.ERROR, "Nombre", nombre(c),
                    "Falta el primer apellido o el primer nombre del cotizante.",
                    "Completar los nombres y apellidos obligatorios.",
                    "El primer apellido y el primer nombre son obligatorios.",
                    "El operador puede rechazar el registro."));
        }
    }

    /** Paso 3 — clasificación (validez contra catálogo, P2). */
    private void validarClasificacion(PilaCotizanteEntity c, PilaTipoCotizanteEntity tipo, List<HallazgoDto> hs) {
        if (blank(c.getTipoCotizante())) {
            hs.add(hallazgo("PILA-COT-001", SeveridadPila.ERROR, "Tipo de cotizante", "Vacío",
                    "El cotizante no tiene tipo de cotizante (código UGPP).",
                    "Asignar el tipo de cotizante correspondiente.",
                    "El tipo de cotizante determina las reglas aplicables.",
                    "El registro puede ser rechazado o mal clasificado."));
        } else if (tipo == null) {
            // No está en el catálogo cargado: no afirmamos que sea inválido.
            hs.add(hallazgo("PILA-COT-002", SeveridadPila.NO_EVALUABLE, "Tipo de cotizante", c.getTipoCotizante(),
                    "El tipo de cotizante no está en el catálogo cargado: no se puede verificar su validez ni sus subsistemas obligatorios.",
                    "Completar el catálogo de tipos de cotizante con el código " + c.getTipoCotizante() + ".",
                    "La validez del tipo de cotizante se verifica contra el catálogo oficial (Anexo Técnico 2).",
                    "No debe considerarse aprobado hasta validar el catálogo."));
        } else if (!Boolean.TRUE.equals(tipo.getActivo())) {
            hs.add(hallazgo("PILA-COT-002", SeveridadPila.ERROR, "Tipo de cotizante", c.getTipoCotizante(),
                    "El tipo de cotizante (" + tipo.getNombre() + ") no está vigente.",
                    "Usar un tipo de cotizante vigente para el período.",
                    "El tipo de cotizante debe estar vigente en el catálogo oficial.",
                    "El operador rechaza el registro."));
        }
    }

    /**
     * Paso 10 — entidades. Exige EPS/AFP/ARL/CCF SOLO cuando el tipo de
     * cotizante está obligado a ese subsistema (presencia, P2a). Si el catálogo
     * de ese tipo de entidad está cargado (P2b), valida además que el código
     * exista y esté vigente para el período.
     */
    private void validarEntidades(PilaCotizanteEntity c, PilaTipoCotizanteEntity tipo,
                                  Map<String, Map<String, PilaEntidadEntity>> catEnt,
                                  LocalDate periodoDate, List<HallazgoDto> hs) {
        if (tipo == null) return; // sin obligaciones conocidas → cubierto por PILA-COT-002
        validarEntidad(tipo.getObligSalud(), c.getCodEps(), "EPS", "EPS", "salud",
                "PILA-ENT-001", catEnt, periodoDate, hs);
        validarEntidad(tipo.getObligPension(), c.getCodAfp(), "AFP", "AFP", "pensión",
                "PILA-ENT-002", catEnt, periodoDate, hs);
        validarEntidad(tipo.getObligArl(), c.getCodArl(), "ARL", "ARL", "riesgos laborales",
                "PILA-ENT-003", catEnt, periodoDate, hs);
        validarEntidad(tipo.getObligCcf(), c.getCodCcf(), "CCF", "Caja de compensación", "caja de compensación",
                "PILA-ENT-004", catEnt, periodoDate, hs);
    }

    private void validarEntidad(Boolean obligado, String codigo, String tipoKey, String label, String subsistema,
                                String codPresencia, Map<String, Map<String, PilaEntidadEntity>> catEnt,
                                LocalDate periodoDate, List<HallazgoDto> hs) {
        boolean oblig = Boolean.TRUE.equals(obligado);
        if (blank(codigo)) {
            if (oblig) {
                hs.add(hallazgo(codPresencia, SeveridadPila.ERROR, label, "Vacío",
                        "El cotizante está obligado a aportar a " + subsistema + " y no tiene código de " + label + ".",
                        "Verificar la afiliación y registrar el código PILA de la " + label + ".",
                        "El tipo de cotizante obliga a este subsistema (matriz del Anexo Técnico 2).",
                        "Posible riesgo de omisión de afiliación frente a la UGPP."));
            }
            return;
        }
        // Presente: validar contra el catálogo SOLO si ese tipo está cargado.
        Map<String, PilaEntidadEntity> cat = catEnt.get(tipoKey);
        if (cat == null || cat.isEmpty()) return; // catálogo no cargado → no se valida el código
        PilaEntidadEntity ent = cat.get(codigo);
        if (ent == null) {
            hs.add(hallazgo("PILA-ENT-005", SeveridadPila.ERROR, label, codigo,
                    "El código de " + label + " (" + codigo + ") no existe en el catálogo oficial.",
                    "Verificar el código PILA de la " + label + ".",
                    "El código de la entidad debe existir en el catálogo oficial.",
                    "El operador rechaza el registro."));
        } else if (!vigenteEnFecha(ent, periodoDate)) {
            hs.add(hallazgo("PILA-ENT-005", SeveridadPila.ERROR, label, codigo,
                    "La " + label + " (" + ent.getNombre() + ") no está vigente para el período.",
                    "Verificar la afiliación: la entidad no está vigente en el período de cotización.",
                    "La entidad debe estar vigente para el período.",
                    "El operador rechaza el registro."));
        }
    }

    private boolean vigenteEnFecha(PilaEntidadEntity ent, LocalDate fecha) {
        if (!Boolean.TRUE.equals(ent.getActivo())) return false;
        if (fecha == null) return true; // sin fecha del período no forzamos la vigencia
        if (ent.getVigenciaDesde() != null && fecha.isBefore(ent.getVigenciaDesde())) return false;
        if (ent.getVigenciaHasta() != null && fecha.isAfter(ent.getVigenciaHasta())) return false;
        return true;
    }

    private LocalDate parsePeriodo(String periodo) {
        try {
            return java.time.YearMonth.parse(periodo).atDay(1);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, PilaCatalogoEntity> cargarCatalogo(String dominio) {
        Map<String, PilaCatalogoEntity> m = new LinkedHashMap<>();
        for (PilaCatalogoEntity e : catalogoRepo.findByDominio(dominio)) {
            m.put(e.getCodigo(), e);
        }
        return m;
    }

    /**
     * Valida un código de contexto (tipo de planilla, tipo de aportante) contra
     * un catálogo. Ausente → NO_EVALUABLE (faltante); en catálogo y activo → OK;
     * inactivo → ERROR; presente pero no en el catálogo cargado → NO_EVALUABLE.
     */
    private void validarContextoCodigo(String valor, Map<String, PilaCatalogoEntity> cat, String campo,
                                       String codFaltante, String codInvalido, List<HallazgoDto> hs) {
        if (blank(valor)) {
            hs.add(hallazgo(codFaltante, SeveridadPila.NO_EVALUABLE, campo, "No suministrado",
                    "No se conoce el " + campo + ".",
                    "Suministrar el " + campo + " en el encabezado de la planilla.",
                    "Las reglas aplicables dependen del " + campo + ".",
                    "La planilla no puede considerarse aprobada."));
            return;
        }
        PilaCatalogoEntity e = cat.get(valor);
        if (cat.isEmpty() || e == null) {
            hs.add(hallazgo(codInvalido, SeveridadPila.NO_EVALUABLE, campo, valor,
                    "El " + campo + " (" + valor + ") no está en el catálogo cargado: no se puede verificar su validez.",
                    "Completar el catálogo con el código " + valor + " o corregirlo.",
                    "La validez se verifica contra el catálogo oficial (Anexo Técnico 2).",
                    "No debe considerarse aprobado hasta validar el catálogo."));
        } else if (!Boolean.TRUE.equals(e.getActivo())) {
            hs.add(hallazgo(codInvalido, SeveridadPila.ERROR, campo, valor,
                    "El " + campo + " (" + e.getNombre() + ") no está vigente.",
                    "Usar un " + campo + " vigente para el período.",
                    "El código debe estar vigente en el catálogo oficial.",
                    "El operador rechaza el registro."));
        }
    }

    /** Paso 6 — días por subsistema. */
    private void validarDias(PilaCotizanteEntity c, List<HallazgoDto> hs) {
        validarUnDia(c, "Días pensión", c.getDiasCotizadosPension(), hs);
        validarUnDia(c, "Días salud", c.getDiasCotizadosSalud(), hs);
        validarUnDia(c, "Días ARL", c.getDiasCotizadosArl(), hs);
        validarUnDia(c, "Días CCF", c.getDiasCotizadosCcf(), hs);

        Integer diasSalud = c.getDiasCotizadosSalud();
        if (diasSalud != null && diasSalud >= 0 && diasSalud < 30 && !hayNovedadQueReduceDias(c)) {
            hs.add(hallazgo("PILA-DIA-004", SeveridadPila.ERROR, "Días salud", str(diasSalud),
                    "Los días de salud son inferiores a 30 y no hay una novedad que lo respalde "
                            + "(ingreso, retiro, licencia, incapacidad, suspensión o vacaciones).",
                    "Registrar la novedad correspondiente o corregir los días.",
                    "Los días inferiores a 30 deben estar respaldados por una novedad.",
                    "Posible riesgo de reporte de días inferiores frente a la UGPP."));
        }
    }

    private void validarUnDia(PilaCotizanteEntity c, String campo, Integer dias, List<HallazgoDto> hs) {
        if (dias == null) return;
        if (dias < 0) {
            hs.add(hallazgo("PILA-DIA-001", SeveridadPila.ERROR, campo, str(dias),
                    "El número de días es negativo.", "Corregir los días reportados.",
                    "Los días no pueden ser negativos.", "El operador rechaza el registro."));
        } else if (dias > 30) {
            hs.add(hallazgo("PILA-DIA-002", SeveridadPila.ERROR, campo, str(dias),
                    "El número de días supera el máximo mensual de 30.",
                    "Corregir los días: el máximo por subsistema es 30.",
                    "Para los campos de días de PILA el máximo general es 30.",
                    "El operador rechaza el registro."));
        }
    }

    /** Paso 8 — fechas (+ coherencia con período, ingreso y retiro). */
    private void validarFechas(PilaCotizanteEntity c, LocalDate periodoIni, LocalDate periodoFin,
                               List<HallazgoDto> hs) {
        LocalDate ing = c.getFechaIngreso();
        LocalDate ret = c.getFechaRetiro();

        if (ing != null && ret != null && ing.isAfter(ret)) {
            hs.add(hallazgo("PILA-FEC-003", SeveridadPila.ERROR, "Fechas ingreso/retiro",
                    ing + " / " + ret,
                    "La fecha de ingreso es posterior a la fecha de retiro.",
                    "Corregir las fechas de ingreso o retiro.",
                    "La fecha de ingreso no puede ser posterior a la de retiro.",
                    "El operador rechaza el registro."));
        }
        if (Boolean.TRUE.equals(c.getIng())) {
            if (ing == null) hs.add(faltaFecha("Fecha de ingreso", "ingreso (ING)"));
            else if (fueraDePeriodo(ing, periodoIni, periodoFin)) hs.add(fechaFueraPeriodo("Fecha de ingreso", ing));
        }
        if (Boolean.TRUE.equals(c.getRet())) {
            if (ret == null) hs.add(faltaFecha("Fecha de retiro", "retiro (RET)"));
            else if (fueraDePeriodo(ret, periodoIni, periodoFin)) hs.add(fechaFueraPeriodo("Fecha de retiro", ret));
        }

        validarNovedadFechas(c.getVst(), c.getFechaInicioVst(), c.getFechaFinVst(), "VST", ing, ret, hs);
        validarNovedadFechas(c.getSln(), c.getFechaInicioSln(), c.getFechaFinSln(), "SLN", ing, ret, hs);
        validarNovedadFechas(c.getIge(), c.getFechaInicioIge(), c.getFechaFinIge(), "IGE", ing, ret, hs);
        validarNovedadFechas(c.getLma(), c.getFechaInicioLma(), c.getFechaFinLma(), "LMA", ing, ret, hs);
        validarNovedadFechas(c.getVacLr(), c.getFechaInicioVacLr(), c.getFechaFinVacLr(), "VAC/LR", ing, ret, hs);
        validarNovedadFechas(c.getIrl(), c.getFechaInicioIrl(), c.getFechaFinIrl(), "IRL", ing, ret, hs);
        validarNovedadFechas(c.getVct(), c.getFechaInicioVct(), c.getFechaFinVct(), "VCT", ing, ret, hs);
    }

    private void validarNovedadFechas(Boolean flag, LocalDate ini, LocalDate fin, String nov,
                                      LocalDate ing, LocalDate ret, List<HallazgoDto> hs) {
        if (!Boolean.TRUE.equals(flag)) return;
        if (ini == null) {
            hs.add(hallazgo("PILA-NOV-005", SeveridadPila.ERROR, "Fecha inicial " + nov, "Vacío",
                    "La novedad " + nov + " no tiene fecha inicial.",
                    "Registrar la fecha inicial de la novedad " + nov + ".",
                    "Las novedades de ausentismo exigen su par de fechas.",
                    "El operador rechaza la novedad."));
        }
        if (fin == null) {
            hs.add(hallazgo("PILA-NOV-006", SeveridadPila.ERROR, "Fecha final " + nov, "Vacío",
                    "La novedad " + nov + " no tiene fecha final.",
                    "Registrar la fecha final de la novedad " + nov + ".",
                    "Las novedades de ausentismo exigen su par de fechas.",
                    "El operador rechaza la novedad."));
        }
        if (ini != null && fin != null && ini.isAfter(fin)) {
            hs.add(hallazgo("PILA-NOV-007", SeveridadPila.ERROR, "Rango " + nov, ini + " / " + fin,
                    "La fecha inicial de " + nov + " es posterior a la final.",
                    "Corregir el rango de fechas de la novedad " + nov + ".",
                    "La fecha inicial no puede ser posterior a la final.",
                    "El operador rechaza la novedad."));
        }
        // Coherencia con ingreso/retiro.
        if (ret != null && ini != null && ini.isAfter(ret)) {
            hs.add(hallazgo("PILA-FEC-005", SeveridadPila.ERROR, "Novedad " + nov, ini + " > retiro " + ret,
                    "La novedad " + nov + " ocurre después del retiro del cotizante.",
                    "Revisar la novedad o la fecha de retiro.",
                    "No puede reportarse una novedad posterior al retiro sin justificación.",
                    "El operador rechaza la novedad."));
        }
        if (ing != null && fin != null && fin.isBefore(ing)) {
            hs.add(hallazgo("PILA-FEC-006", SeveridadPila.ERROR, "Novedad " + nov, fin + " < ingreso " + ing,
                    "La novedad " + nov + " ocurre antes del ingreso del cotizante.",
                    "Revisar la novedad o la fecha de ingreso.",
                    "No puede reportarse una novedad anterior al ingreso sin justificación.",
                    "El operador rechaza la novedad."));
        }
    }

    /** Paso 7 — coherencia de novedades: traslados y novedad ↔ IBC. */
    private void validarNovedades(PilaCotizanteEntity c, List<HallazgoDto> hs) {
        if ((Boolean.TRUE.equals(c.getTde()) || Boolean.TRUE.equals(c.getTae())) && blank(c.getCodEpsTraslado())) {
            hs.add(hallazgo("PILA-NOV-010", SeveridadPila.ERROR, "Traslado EPS", "Vacío",
                    "Hay traslado de EPS (TDE/TAE) pero no se reporta la EPS anterior/nueva.",
                    "Registrar el código de la EPS del traslado.",
                    "Un traslado exige la administradora anterior y la nueva.",
                    "El operador rechaza la novedad de traslado."));
        }
        if ((Boolean.TRUE.equals(c.getTdp()) || Boolean.TRUE.equals(c.getTap())) && blank(c.getCodAfpTraslado())) {
            hs.add(hallazgo("PILA-NOV-010", SeveridadPila.ERROR, "Traslado AFP", "Vacío",
                    "Hay traslado de pensión (TDP/TAP) pero no se reporta la AFP anterior/nueva.",
                    "Registrar el código de la AFP del traslado.",
                    "Un traslado exige la administradora anterior y la nueva.",
                    "El operador rechaza la novedad de traslado."));
        }
        // SLN (suspensión / licencia no remunerada) no cotiza pensión: un mes
        // completo con IBC de pensión > 0 es incoherente (con matices → advertencia).
        if (Boolean.TRUE.equals(c.getSln()) && c.getIbcPension() != null && c.getIbcPension().signum() > 0
                && c.getDiasCotizadosPension() != null && c.getDiasCotizadosPension() == 30) {
            hs.add(hallazgo("PILA-NOV-009", SeveridadPila.ADVERTENCIA, "IBC pensión", str(c.getIbcPension()),
                    "Hay suspensión/licencia no remunerada (SLN) de mes completo pero el IBC de pensión no es cero.",
                    "Revisar: durante la SLN no se cotiza a pensión.",
                    "La SLN no genera cotización a pensión por los días suspendidos.",
                    "Posible riesgo de inexactitud frente a la UGPP."));
        }
    }

    private boolean fueraDePeriodo(LocalDate f, LocalDate ini, LocalDate fin) {
        if (f == null || ini == null || fin == null) return false;
        return f.isBefore(ini) || f.isAfter(fin);
    }

    private HallazgoDto fechaFueraPeriodo(String campo, LocalDate f) {
        return hallazgo("PILA-FEC-004", SeveridadPila.ADVERTENCIA, campo, str(f),
                "La " + campo.toLowerCase() + " (" + f + ") está fuera del período de la planilla.",
                "Verificar que la novedad corresponda a este período.",
                "La novedad de ingreso/retiro se reporta en el período en que ocurre.",
                "Posible inconsistencia de período frente a la UGPP.");
    }

    /** Paso 9 — IBC (rangos con SMMLV; compatibilidades quedan para P2). */
    private void validarIbc(PilaCotizanteEntity c, BigDecimal smmlv, BigDecimal tope, List<HallazgoDto> hs) {
        validarIbcNegativo("IBC pensión", c.getIbcPension(), hs);
        validarIbcNegativo("IBC salud", c.getIbcSalud(), hs);
        validarIbcNegativo("IBC ARL", c.getIbcArl(), hs);
        validarIbcNegativo("IBC CCF", c.getIbcCcf(), hs);

        if (tope != null) {
            validarTope("IBC pensión", c.getIbcPension(), tope, hs);
            validarTope("IBC salud", c.getIbcSalud(), tope, hs);
        }

        // Mínimo legal: mes completo sin licencia/suspensión con IBC < SMMLV.
        // Existen excepciones (tiempo parcial), por eso es ADVERTENCIA, no ERROR.
        if (smmlv != null && !Boolean.TRUE.equals(c.getSalarioIntegral())
                && !Boolean.TRUE.equals(c.getSln())
                && c.getDiasCotizadosPension() != null && c.getDiasCotizadosPension() == 30
                && c.getIbcPension() != null && c.getIbcPension().compareTo(smmlv) < 0) {
            hs.add(hallazgo("PILA-UGPP-003", SeveridadPila.ADVERTENCIA, "IBC pensión", str(c.getIbcPension()),
                    "El IBC de pensión de un mes completo es inferior a un SMMLV (" + str(smmlv) + ").",
                    "Verificar si aplica una excepción (tiempo parcial) o corregir el IBC.",
                    "El IBC de un mes completo no debe ser inferior al SMMLV salvo excepción.",
                    "Posible riesgo de IBC inferior frente a la UGPP."));
        }
    }

    private void validarIbcNegativo(String campo, BigDecimal ibc, List<HallazgoDto> hs) {
        if (ibc != null && ibc.signum() < 0) {
            hs.add(hallazgo("PILA-IBC-002", SeveridadPila.ERROR, campo, str(ibc),
                    "El IBC es negativo.", "Corregir el IBC.", "El IBC no puede ser negativo.",
                    "El operador rechaza el registro."));
        }
    }

    private void validarTope(String campo, BigDecimal ibc, BigDecimal tope, List<HallazgoDto> hs) {
        if (ibc != null && ibc.compareTo(tope) > 0) {
            hs.add(hallazgo("PILA-IBC-005", SeveridadPila.ERROR, campo, str(ibc),
                    "El IBC supera el tope máximo legal (" + str(tope) + ").",
                    "Ajustar el IBC al tope máximo de cotización.",
                    "El IBC no puede superar el tope legal (25 SMMLV salvo regla especial).",
                    "El operador rechaza el registro."));
        }
    }

    /**
     * Paso 13 + 14 — valores de aportes con exoneración Ley 1607.
     *
     * <p>Los aportes almacenados son del EMPLEADOR. Con exoneración, el aporte
     * de salud del empleador es 0 para empleados por debajo del umbral (10 SMMLV),
     * así que un aporte de salud en cero NO es error en ese caso. La pensión
     * nunca se exonera.
     */
    private void validarValores(PilaCotizanteEntity c, BigDecimal umbralExo, List<HallazgoDto> hs) {
        // Pensión: siempre obligatoria (no exonerable).
        validarAportePension(c, hs);
        controlAporte("pensión", c.getIbcPension(), c.getTarifaPension(), c.getAportePension(), hs);

        // Salud (empleador): exoneración según umbral.
        boolean exonerado = Boolean.TRUE.equals(c.getExoneradoLey1607());
        BigDecimal ibcSalud = c.getIbcSalud();
        BigDecimal apSalud = c.getAporteSalud();
        boolean bajoUmbral = umbralExo != null && ibcSalud != null && ibcSalud.compareTo(umbralExo) < 0;

        if (ibcSalud != null && ibcSalud.signum() > 0) {
            boolean apCero = apSalud == null || apSalud.signum() <= 0;
            if (exonerado && bajoUmbral) {
                // Correcto que el aporte de salud del empleador sea 0. Si no lo es,
                // es informativo (paga estando exonerado): no bloquea.
                if (!apCero) {
                    hs.add(hallazgo("PILA-VAL-004", SeveridadPila.INFORMACION, "Aporte salud", str(apSalud),
                            "El aportante está exonerado (Ley 1607) para este empleado, pero reporta aporte de salud del empleador.",
                            "Verificar si corresponde aplicar la exoneración de salud.",
                            "Con exoneración y bajo el umbral, el empleador no aporta salud.",
                            ""));
                }
            } else if (apCero) {
                if (exonerado && !bajoUmbral) {
                    hs.add(hallazgo("PILA-UGPP-006", SeveridadPila.ADVERTENCIA, "Aporte salud", str(apSalud),
                            "El empleado supera el umbral de exoneración (10 SMMLV): la exoneración de salud no aplica y no hay aporte del empleador.",
                            "Verificar el aporte de salud: por encima del umbral no hay exoneración.",
                            "La exoneración de salud aplica solo por debajo del umbral.",
                            "Posible aplicación incorrecta de la exoneración frente a la UGPP."));
                } else {
                    hs.add(hallazgo("PILA-VAL-001", SeveridadPila.ERROR, "Aporte salud", str(apSalud),
                            "Hay IBC de salud pero el aporte del empleador es cero (sin exoneración aplicable).",
                            "Verificar el aporte de salud liquidado por la nómina.",
                            "Con IBC mayor que cero y sin exoneración debe existir aporte de salud.",
                            "Posible riesgo de omisión de aporte frente a la UGPP."));
                }
            } else {
                controlAporte("salud", ibcSalud, c.getTarifaSalud(), apSalud, hs);
            }
        }
    }

    private void validarAportePension(PilaCotizanteEntity c, List<HallazgoDto> hs) {
        BigDecimal ibc = c.getIbcPension();
        BigDecimal aporte = c.getAportePension();
        if (ibc == null || ibc.signum() <= 0) return;
        if (aporte == null || aporte.signum() <= 0) {
            hs.add(hallazgo("PILA-VAL-001", SeveridadPila.ERROR, "Aporte pensión", str(aporte),
                    "Hay IBC de pensión pero el aporte reportado es cero.",
                    "Verificar el aporte de pensión liquidado por la nómina.",
                    "Con IBC mayor que cero debe existir aporte a pensión.",
                    "Posible riesgo de omisión de aporte frente a la UGPP."));
        }
    }

    /**
     * Control de aporte contra IBC × tarifa (Paso 13). Con tolerancia por
     * redondeo. Diferencia relevante → ADVERTENCIA (no bloquea): el aporte
     * almacenado es del empleador y hay reglas de redondeo del operador.
     */
    private void controlAporte(String subsistema, BigDecimal ibc, BigDecimal tarifa,
                               BigDecimal aporte, List<HallazgoDto> hs) {
        if (ibc == null || ibc.signum() <= 0 || aporte == null
                || tarifa == null || tarifa.signum() <= 0) return;
        // Tarifa puede venir como porcentaje (12.000) o fracción (0.120).
        BigDecimal frac = tarifa.compareTo(BigDecimal.ONE) > 0 ? tarifa.movePointLeft(2) : tarifa;
        BigDecimal esperado = ibc.multiply(frac).setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal dif = aporte.subtract(esperado).abs();
        BigDecimal tol = esperado.multiply(new BigDecimal("0.02")).max(new BigDecimal("100"));
        if (dif.compareTo(tol) > 0) {
            hs.add(hallazgo("PILA-UGPP-005", SeveridadPila.ADVERTENCIA, "Aporte " + subsistema, str(aporte),
                    "El aporte de " + subsistema + " no corresponde al IBC por la tarifa (esperado ≈ " + str(esperado) + ").",
                    "Revisar el aporte de " + subsistema + " frente al IBC y la tarifa.",
                    "El aporte debe corresponder al IBC por la tarifa vigente.",
                    "Posible riesgo de inexactitud en aportes frente a la UGPP."));
        }
    }

    /** Paso 15 — múltiples registros / duplicados. */
    private void validarDuplicados(PilaCotizanteEntity c, List<PilaCotizanteEntity> mismos, List<HallazgoDto> hs) {
        if (mismos == null || mismos.size() <= 1) return;
        boolean hayIdentica = mismos.stream().anyMatch(o -> o != c && lineasIguales(c, o));
        if (hayIdentica) {
            hs.add(hallazgo("PILA-REG-001", SeveridadPila.ERROR, "Documento", c.getNumeroIdentificacion(),
                    "El cotizante aparece en varias líneas exactamente iguales (mismo IBC y días, sin novedad que las distinga).",
                    "Eliminar el registro duplicado o justificar las líneas con una novedad.",
                    "Un mismo documento en líneas idénticas es una doble cotización.",
                    "El operador rechaza la doble cotización."));
        } else {
            hs.add(hallazgo("PILA-REG-002", SeveridadPila.INFORMACION, "Documento", c.getNumeroIdentificacion(),
                    "El cotizante se reporta en varias líneas que parecen justificadas (distinto IBC, días o novedad).",
                    "Confirmar que cada línea corresponde a una novedad, período o entidad distinta.",
                    "Un cotizante puede tener múltiples registros válidos.",
                    ""));
        }
        // Entidades contradictorias entre líneas SIN novedad de traslado.
        // Se emite una sola vez (en la primera línea del grupo).
        if (c == mismos.get(0)) {
            entidadesContradictorias(mismos, hs);
        }
    }

    private void entidadesContradictorias(List<PilaCotizanteEntity> mismos, List<HallazgoDto> hs) {
        java.util.Set<String> eps = new java.util.HashSet<>();
        java.util.Set<String> afp = new java.util.HashSet<>();
        boolean trasladoEps = false, trasladoAfp = false;
        for (PilaCotizanteEntity o : mismos) {
            if (!blank(o.getCodEps())) eps.add(o.getCodEps());
            if (!blank(o.getCodAfp())) afp.add(o.getCodAfp());
            trasladoEps |= Boolean.TRUE.equals(o.getTde()) || Boolean.TRUE.equals(o.getTae());
            trasladoAfp |= Boolean.TRUE.equals(o.getTdp()) || Boolean.TRUE.equals(o.getTap());
        }
        if (eps.size() > 1 && !trasladoEps) {
            hs.add(hallazgo("PILA-REG-004", SeveridadPila.ERROR, "EPS", String.join(", ", eps),
                    "El cotizante aparece con EPS distintas en varias líneas sin una novedad de traslado (TDE/TAE).",
                    "Registrar la novedad de traslado o unificar la EPS.",
                    "Distintas entidades en el mismo mes exigen una novedad de traslado.",
                    "El operador rechaza entidades contradictorias."));
        }
        if (afp.size() > 1 && !trasladoAfp) {
            hs.add(hallazgo("PILA-REG-004", SeveridadPila.ERROR, "AFP", String.join(", ", afp),
                    "El cotizante aparece con AFP distintas en varias líneas sin una novedad de traslado (TDP/TAP).",
                    "Registrar la novedad de traslado o unificar la AFP.",
                    "Distintas entidades en el mismo mes exigen una novedad de traslado.",
                    "El operador rechaza entidades contradictorias."));
        }
    }

    // ── Consolidación ───────────────────────────────────────────────────────

    private ReporteValidacionDto consolidar(String periodo, PilaEncabezadoEntity enc, PilaPlanillaEntity planilla,
                                            List<HallazgoDto> hPlanilla, List<CotizanteValidacionDto> cotizantes) {
        int aptos = 0, conAdv = 0, bloqueados = 0, noEval = 0;
        int errores = 0, advertencias = 0, info = 0, noEvaluable = 0;

        for (HallazgoDto h : hPlanilla) {
            switch (h.getSeveridad()) {
                case ERROR -> errores++;
                case ADVERTENCIA -> advertencias++;
                case INFORMACION -> info++;
                case NO_EVALUABLE -> noEvaluable++;
            }
        }
        for (CotizanteValidacionDto c : cotizantes) {
            switch (c.getEstado()) {
                case APTO -> aptos++;
                case APTO_CON_ADVERTENCIAS -> conAdv++;
                case BLOQUEADO -> bloqueados++;
                case NO_EVALUABLE -> noEval++;
            }
            for (HallazgoDto h : c.getHallazgos()) {
                switch (h.getSeveridad()) {
                    case ERROR -> errores++;
                    case ADVERTENCIA -> advertencias++;
                    case INFORMACION -> info++;
                    case NO_EVALUABLE -> noEvaluable++;
                }
            }
        }

        EstadoPlanillaPila estadoPlanilla;
        boolean hayErrorContexto = hPlanilla.stream().anyMatch(h -> h.getSeveridad() == SeveridadPila.ERROR);
        boolean hayNoEvalContexto = hPlanilla.stream().anyMatch(h -> h.getSeveridad() == SeveridadPila.NO_EVALUABLE);
        if (bloqueados > 0 || hayErrorContexto) {
            estadoPlanilla = EstadoPlanillaPila.BLOQUEADA;
        } else if (noEval > 0 || hayNoEvalContexto) {
            estadoPlanilla = EstadoPlanillaPila.NO_EVALUABLE;
        } else if (conAdv > 0 || advertencias > 0) {
            estadoPlanilla = EstadoPlanillaPila.LISTA_CON_ADVERTENCIAS;
        } else {
            estadoPlanilla = EstadoPlanillaPila.LISTA;
        }

        hPlanilla.sort(Comparator.comparingInt(x -> x.getSeveridad().getOrden()));

        return ReporteValidacionDto.builder()
                .periodo(periodo)
                .tipoPlanilla(planilla != null ? planilla.getTipoPlanilla() : null)
                .tipoAportante(enc.getTipoAportante())
                .numeroAportante(enc.getNumeroDocumento())
                .estadoPlanilla(estadoPlanilla)
                .totalCotizantes(cotizantes.size())
                .cotizantesAptos(aptos)
                .cotizantesAptosConAdvertencias(conAdv)
                .cotizantesBloqueados(bloqueados)
                .cotizantesNoEvaluables(noEval)
                .totalErrores(errores)
                .totalAdvertencias(advertencias)
                .totalInformacion(info)
                .totalNoEvaluable(noEvaluable)
                .hallazgosPlanilla(hPlanilla)
                .cotizantes(cotizantes)
                .build();
    }

    private EstadoCotizantePila estadoCotizante(List<HallazgoDto> hs) {
        boolean error = hs.stream().anyMatch(h -> h.getSeveridad() == SeveridadPila.ERROR);
        if (error) return EstadoCotizantePila.BLOQUEADO;
        boolean noEval = hs.stream().anyMatch(h -> h.getSeveridad() == SeveridadPila.NO_EVALUABLE);
        if (noEval) return EstadoCotizantePila.NO_EVALUABLE;
        boolean adv = hs.stream().anyMatch(h -> h.getSeveridad() == SeveridadPila.ADVERTENCIA);
        return adv ? EstadoCotizantePila.APTO_CON_ADVERTENCIAS : EstadoCotizantePila.APTO;
    }

    private ReporteValidacionDto reporteVacio(String periodo, HallazgoDto h) {
        List<HallazgoDto> hp = new ArrayList<>();
        hp.add(h);
        return ReporteValidacionDto.builder()
                .periodo(periodo)
                .estadoPlanilla(EstadoPlanillaPila.NO_EVALUABLE)
                .hallazgosPlanilla(hp)
                .cotizantes(List.of())
                .totalNoEvaluable(1)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private boolean hayNovedadQueReduceDias(PilaCotizanteEntity c) {
        return Boolean.TRUE.equals(c.getIng()) || Boolean.TRUE.equals(c.getRet())
                || Boolean.TRUE.equals(c.getSln()) || Boolean.TRUE.equals(c.getIge())
                || Boolean.TRUE.equals(c.getLma()) || Boolean.TRUE.equals(c.getVacLr())
                || Boolean.TRUE.equals(c.getIrl());
    }

    private boolean lineasIguales(PilaCotizanteEntity a, PilaCotizanteEntity b) {
        return java.util.Objects.equals(a.getIbcPension(), b.getIbcPension())
                && java.util.Objects.equals(a.getIbcSalud(), b.getIbcSalud())
                && java.util.Objects.equals(a.getDiasCotizadosPension(), b.getDiasCotizadosPension())
                && java.util.Objects.equals(a.getDiasCotizadosSalud(), b.getDiasCotizadosSalud())
                && !hayNovedadQueReduceDias(a) && !hayNovedadQueReduceDias(b);
    }

    private String clave(PilaCotizanteEntity c) {
        return str(c.getTipoDocumento()) + "|" + str(c.getNumeroIdentificacion());
    }

    private HallazgoDto faltaFecha(String campo, String novedad) {
        return hallazgo("PILA-NOV-005", SeveridadPila.ERROR, campo, "Vacío",
                "La novedad de " + novedad + " no tiene fecha.",
                "Registrar la fecha de la novedad de " + novedad + ".",
                "Las novedades de ingreso y retiro exigen su fecha.",
                "El operador rechaza el registro.");
    }

    private HallazgoDto hallazgo(String codigo, SeveridadPila sev, String campo, String valor,
                                 String desc, String accion, String fundamento, String riesgo) {
        return HallazgoDto.builder()
                .codigo(codigo).severidad(sev).campo(campo).valorRecibido(valor)
                .descripcion(desc).condicionEsperada(null).accionSugerida(accion)
                .fundamento(fundamento).riesgo(riesgo)
                .build();
    }

    private String nombre(PilaCotizanteEntity c) {
        return String.join(" ", java.util.stream.Stream.of(
                        c.getApellido1(), c.getApellido2(), c.getNombre1(), c.getNombre2())
                .filter(s -> s != null && !s.isBlank())
                .toList());
    }

    private boolean esDocumentoNumerico(String tipo) {
        if (tipo == null) return false;
        return switch (tipo) {
            case "CC", "TI", "RC", "NI", "NIT" -> true;
            default -> false;
        };
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
