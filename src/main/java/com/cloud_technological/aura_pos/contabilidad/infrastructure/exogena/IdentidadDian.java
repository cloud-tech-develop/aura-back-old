package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.util.Map;

import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador;

/**
 * Columnas de identificación de la exógena como las pide la DIAN: tipo de documento
 * con su código, número sin puntos ni DV, DV (calculado si falta), apellidos y
 * nombres por separado (persona natural) o razón social (jurídica), dirección,
 * departamento y municipio DANE y país con el código DIAN. Lógica pura.
 */
public final class IdentidadDian {

    /** Colombia en la tabla de países de la DIAN. */
    public static final String PAIS_COLOMBIA = "169";

    private static final Map<String, String> TITULOS = Map.ofEntries(
            Map.entry("CPT", "Concepto"),
            Map.entry("TDOC", "Tipo de documento"),
            Map.entry("NID", "Número identificación"),
            Map.entry("DV", "DV"),
            Map.entry("APL1", "Primer apellido"),
            Map.entry("APL2", "Segundo apellido"),
            Map.entry("NOM1", "Primer nombre"),
            Map.entry("NOM2", "Otros nombres"),
            Map.entry("RAZ", "Razón social"),
            Map.entry("DIR", "Dirección"),
            Map.entry("DPTO", "Código dpto."),
            Map.entry("MUN", "Código mcp."),
            Map.entry("PAIS", "País de residencia o domicilio"));

    /** Países frecuentes ISO → código DIAN (el resto se deja como venga). */
    private static final Map<String, String> PAISES = Map.ofEntries(
            Map.entry("CO", PAIS_COLOMBIA), Map.entry("US", "249"), Map.entry("VE", "862"),
            Map.entry("EC", "239"), Map.entry("PE", "589"), Map.entry("PA", "580"), Map.entry("MX", "493"),
            Map.entry("ES", "245"), Map.entry("CL", "211"), Map.entry("AR", "063"), Map.entry("BR", "105"));

    private IdentidadDian() {
    }

    public static String titulo(String codigo) {
        return TITULOS.getOrDefault(codigo, codigo);
    }

    /** Valor de una columna de identificación para la fila (sin datos del informante). */
    public static String valor(String codigo, ExogenaExcelExporter.Fila fila) {
        return valor(codigo, fila, null);
    }

    /**
     * Valor de una columna de identificación para la fila. En las cuantías menores la
     * dirección y el municipio son los del informante.
     */
    public static String valor(String codigo, ExogenaExcelExporter.Fila fila, ExogenaExcelExporter.Informante informante) {
        TerceroExogenaDto t = fila.tercero();
        if (t == null) return cuantiasMenores(codigo, fila.concepto(), informante);
        boolean juridica = esJuridica(t);
        String[] apellidos = partes(t.apellido1(), t.apellido2(), t.apellidos());
        String[] nombres = partes(t.nombre1(), t.nombre2(), t.nombres());
        return switch (codigo) {
            case "CPT" -> nz(fila.concepto());
            case "TDOC" -> tipoDocumento(t.tipoDocumento());
            case "NID" -> numero(t.numeroDocumento());
            case "DV" -> "31".equals(tipoDocumento(t.tipoDocumento())) ? dv(t) : "";
            case "APL1" -> juridica ? "" : apellidos[0];
            case "APL2" -> juridica ? "" : apellidos[1];
            case "NOM1" -> juridica ? "" : nombres[0];
            case "NOM2" -> juridica ? "" : nombres[1];
            case "RAZ" -> juridica ? nz(t.razonSocial()) : "";
            case "DIR" -> nz(t.direccion());
            case "DPTO" -> t.codigoDane() != null && t.codigoDane().length() >= 2 ? t.codigoDane().substring(0, 2) : "";
            case "MUN" -> t.codigoDane() != null && t.codigoDane().length() >= 5 ? t.codigoDane().substring(2, 5) : "";
            case "PAIS" -> pais(t.codigoPais());
            default -> "";
        };
    }

    private static String cuantiasMenores(String codigo, String concepto, ExogenaExcelExporter.Informante inf) {
        String dane = inf != null ? inf.codigoDane() : null;
        return switch (codigo) {
            case "CPT" -> nz(concepto);
            case "TDOC" -> "43";
            case "NID" -> ExogenaExcelExporter.NIT_CUANTIAS_MENORES;
            case "RAZ" -> "CUANTÍAS MENORES";
            case "DIR" -> inf != null ? nz(inf.direccion()) : "";
            case "DPTO" -> dane != null && dane.length() >= 2 ? dane.substring(0, 2) : "";
            case "MUN" -> dane != null && dane.length() >= 5 ? dane.substring(2, 5) : "";
            case "PAIS" -> PAIS_COLOMBIA;
            default -> "";
        };
    }

    /** Jurídica: NIT con razón social, o marcada como jurídica en el tercero. */
    static boolean esJuridica(TerceroExogenaDto t) {
        if ("JURIDICA".equalsIgnoreCase(t.tipoPersona())) return true;
        if ("NATURAL".equalsIgnoreCase(t.tipoPersona())) return false;
        return "31".equals(tipoDocumento(t.tipoDocumento())) && t.razonSocial() != null && !t.razonSocial().isBlank();
    }

    /** Códigos DIAN de tipo de documento; si ya viene numérico se deja tal cual. */
    public static String tipoDocumento(String tipo) {
        if (tipo == null) return "13";
        return switch (tipo.trim().toUpperCase()) {
            case "RC" -> "11";
            case "TI" -> "12";
            case "CC", "CEDULA", "CÉDULA" -> "13";
            case "TE" -> "21";
            case "CE" -> "22";
            case "NIT", "RUT" -> "31";
            case "PASAPORTE", "PP", "PA" -> "41";
            case "DIE", "DOC_EXTRANJERO" -> "42";
            case "PEP" -> "47";
            case "PPT" -> "48";
            case "NIT_EXTRANJERO" -> "50";
            default -> tipo.trim();
        };
    }

    /** Número sin puntos, espacios ni el DV pegado con guion. */
    public static String numero(String n) {
        if (n == null) return "";
        String s = n.trim();
        int guion = s.indexOf('-');
        if (guion > 0) s = s.substring(0, guion);
        return s.replaceAll("[^0-9A-Za-z]", "");
    }

    private static String dv(TerceroExogenaDto t) {
        if (t.dv() != null && !t.dv().isBlank()) return t.dv().trim();
        String n = numero(t.numeroDocumento());
        return n.matches("\\d+") ? String.valueOf(DocumentoSoporteArmador.digitoVerificacion(n)) : "";
    }

    private static String pais(String codigo) {
        if (codigo == null || codigo.isBlank()) return PAIS_COLOMBIA;
        String c = codigo.trim().toUpperCase();
        return c.matches("\\d+") ? c : PAISES.getOrDefault(c, c);
    }

    /**
     * Primero y resto: de los campos separados si los hay; si no, partiendo el texto
     * completo ("PÉREZ GÓMEZ" → PÉREZ / GÓMEZ).
     */
    private static String[] partes(String primero, String segundo, String completo) {
        if (primero != null && !primero.isBlank()) return new String[] { primero.trim(), nz(segundo).trim() };
        if (completo == null || completo.isBlank()) return new String[] { "", "" };
        String[] p = completo.trim().split("\\s+", 2);
        return new String[] { p[0], p.length > 1 ? p[1] : "" };
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
