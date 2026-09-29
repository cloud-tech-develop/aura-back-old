package com.cloud_technological.aura_pos.services.nomina.pila;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.PilaCotizanteEntity;
import com.cloud_technological.aura_pos.entity.PilaEncabezadoEntity;
import com.cloud_technological.aura_pos.entity.PilaLayoutCampoEntity;
import com.cloud_technological.aura_pos.entity.PilaPlanillaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaCotizanteRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaEncabezadoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaLayoutCampoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaPlanillaRepo;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Exportador del archivo PILA (P6), dirigido por el layout de {@code
 * pila_layout_campo}. El formato es el estándar del Anexo Técnico 2 — el mismo
 * que aceptan todos los operadores. El layout es DATA (editable): ajustar orden,
 * longitudes o formato no requiere recompilar.
 */
@Service
public class PilaArchivoExporter {

    private final PilaEncabezadoRepo encabezadoRepo;
    private final PilaPlanillaRepo planillaRepo;
    private final PilaCotizanteRepo cotizanteRepo;
    private final PilaLayoutCampoRepo layoutRepo;

    public PilaArchivoExporter(PilaEncabezadoRepo encabezadoRepo, PilaPlanillaRepo planillaRepo,
                               PilaCotizanteRepo cotizanteRepo, PilaLayoutCampoRepo layoutRepo) {
        this.encabezadoRepo = encabezadoRepo;
        this.planillaRepo = planillaRepo;
        this.cotizanteRepo = cotizanteRepo;
        this.layoutRepo = layoutRepo;
    }

    @Transactional(readOnly = true)
    public String generar(Integer empresaId, String periodo) {
        PilaEncabezadoEntity enc = encabezadoRepo.findByEmpresaIdAndPeriodo(empresaId, periodo)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "No hay planilla de PILA generada para " + periodo));
        List<PilaPlanillaEntity> planillas = planillaRepo.findByEncabezadoId(enc.getId());
        PilaPlanillaEntity planilla = planillas.isEmpty() ? null : planillas.get(0);
        List<PilaCotizanteEntity> cotizantes = planilla != null
                ? cotizanteRepo.findByPlanillaIdOrderBySecuencia(planilla.getId()) : List.of();

        List<PilaLayoutCampoEntity> layout01 = layoutRepo.findByTipoRegistroAndActivoTrueOrderByOrdenAsc("01");
        List<PilaLayoutCampoEntity> layout02 = layoutRepo.findByTipoRegistroAndActivoTrueOrderByOrdenAsc("02");
        if (layout01.isEmpty() && layout02.isEmpty()) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No hay layout de archivo PILA definido (pila_layout_campo).");
        }

        StringBuilder sb = new StringBuilder();
        // Registro tipo 01 (encabezado)
        StringBuilder linea01 = new StringBuilder();
        for (PilaLayoutCampoEntity campo : layout01) {
            linea01.append(formatear(campo, resolverEncabezado(campo.getCodigo(), enc, planilla)));
        }
        sb.append(linea01).append('\n');

        // Registros tipo 02 (cotizantes)
        for (PilaCotizanteEntity c : cotizantes) {
            StringBuilder linea = new StringBuilder();
            for (PilaLayoutCampoEntity campo : layout02) {
                linea.append(formatear(campo, resolverCotizante(campo.getCodigo(), c)));
            }
            sb.append(linea).append('\n');
        }
        return sb.toString();
    }

    // ── Resolución de valores ───────────────────────────────────────────────

    private String resolverEncabezado(String codigo, PilaEncabezadoEntity e, PilaPlanillaEntity p) {
        return switch (codigo) {
            case "TIPO_REGISTRO"      -> "01";
            case "MODALIDAD_PLANILLA" -> p != null ? nz(p.getModalidad()) : "";
            case "TIPO_PLANILLA"      -> p != null ? nz(p.getTipoPlanilla()) : "";
            case "RAZON_SOCIAL"       -> nz(e.getRazonSocial());
            case "TIPO_DOC_APORTANTE" -> nz(e.getTipoDocumento());
            case "NUM_DOC_APORTANTE"  -> nz(e.getNumeroDocumento());
            case "DV_APORTANTE"       -> nz(e.getDigitoVerificacion());
            case "TIPO_APORTANTE"     -> nz(e.getTipoAportante());
            case "PERIODO_PAGO"       -> p != null ? nz(p.getPeriodoPago()) : "";
            case "PERIODO_PAGO_SALUD" -> p != null ? nz(p.getPeriodoPagoSalud()) : "";
            case "TOTAL_EMPLEADOS"    -> p != null ? str(p.getTotalEmpleados()) : "0";
            default -> "";
        };
    }

    private String resolverCotizante(String codigo, PilaCotizanteEntity c) {
        return switch (codigo) {
            case "TIPO_REGISTRO"        -> "02";
            case "SECUENCIA"            -> str(c.getSecuencia());
            case "TIPO_DOCUMENTO"       -> nz(c.getTipoDocumento());
            case "NUMERO_IDENTIFICACION"-> nz(c.getNumeroIdentificacion());
            case "TIPO_COTIZANTE"       -> nz(c.getTipoCotizante());
            case "SUBTIPO_COTIZANTE"    -> nz(c.getSubtipoCotizante());
            case "APELLIDO1"            -> nz(c.getApellido1());
            case "APELLIDO2"            -> nz(c.getApellido2());
            case "NOMBRE1"              -> nz(c.getNombre1());
            case "NOMBRE2"              -> nz(c.getNombre2());
            case "COD_EPS"              -> nz(c.getCodEps());
            case "COD_AFP"              -> nz(c.getCodAfp());
            case "COD_CCF"              -> nz(c.getCodCcf());
            case "COD_ARL"              -> nz(c.getCodArl());
            case "DIAS_PENSION"         -> str(c.getDiasCotizadosPension());
            case "DIAS_SALUD"           -> str(c.getDiasCotizadosSalud());
            case "DIAS_ARL"             -> str(c.getDiasCotizadosArl());
            case "DIAS_CCF"             -> str(c.getDiasCotizadosCcf());
            case "IBC_PENSION"          -> entero(c.getIbcPension());
            case "IBC_SALUD"            -> entero(c.getIbcSalud());
            case "IBC_ARL"              -> entero(c.getIbcArl());
            case "IBC_CCF"              -> entero(c.getIbcCcf());
            case "TARIFA_PENSION"       -> digitos(c.getTarifaPension());
            case "APORTE_PENSION"       -> entero(c.getAportePension());
            case "TARIFA_SALUD"         -> digitos(c.getTarifaSalud());
            case "APORTE_SALUD"         -> entero(c.getAporteSalud());
            case "TARIFA_ARL"           -> digitos(c.getTarifaRiesgos());
            case "APORTE_RIESGOS"       -> entero(c.getAporteRiesgos());
            case "APORTE_CCF"           -> entero(c.getAporteCcf());
            default -> "";
        };
    }

    // ── Formateo ────────────────────────────────────────────────────────────

    private String formatear(PilaLayoutCampoEntity campo, String valorRaw) {
        String v = valorRaw != null ? valorRaw : "";
        Integer len = campo.getLongitud();
        if (len == null || len <= 0) return v;

        boolean numerico = "N".equalsIgnoreCase(campo.getTipoDato());
        char relleno = campo.getRelleno() != null && !campo.getRelleno().isEmpty()
                ? campo.getRelleno().charAt(0) : (numerico ? '0' : ' ');
        boolean derecha = "D".equalsIgnoreCase(campo.getAlineacion());

        if (v.length() > len) {
            // Recorte: numérico conserva los dígitos menos significativos (derecha).
            v = numerico ? v.substring(v.length() - len) : v.substring(0, len);
            return v;
        }
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < len - v.length(); i++) pad.append(relleno);
        return derecha ? pad + v : v + pad;
    }

    private String nz(String s) { return s != null ? s : ""; }
    private String str(Object o) { return o != null ? o.toString() : "0"; }

    /** Entero en pesos (sin decimales). */
    private String entero(BigDecimal v) {
        return v != null ? v.setScale(0, RoundingMode.HALF_UP).toPlainString() : "0";
    }

    /** Solo dígitos (para tarifas: quita el punto decimal y el signo). */
    private String digitos(BigDecimal v) {
        if (v == null) return "0";
        String s = v.stripTrailingZeros().toPlainString().replace(".", "").replace("-", "");
        return s.isEmpty() ? "0" : s;
    }
}
