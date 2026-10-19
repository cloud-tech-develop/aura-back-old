package com.cloud_technological.aura_pos.services.firma_contable;

import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Identidad tributaria de una empresa gestionada: persona natural o jurídica
 * (docs/PLAN_FIRMA_CONTABLE.md §10). La jurídica factura con su razón social y NIT;
 * la natural con sus nombres y apellidos y su cédula o NIT. Se escribe en la empresa
 * (la exógena lee {@code empresa.tipo_persona}) y en su tercero, que es el que sale
 * en los documentos y en los formatos de la DIAN.
 *
 * @param tipoPersona   NATURAL | JURIDICA
 * @param tipoDocumento NIT, CC, CE, PA o PPT
 * @param razonSocial   la de la jurídica, o nombres + apellidos de la natural
 * @param dv            digitado o calculado con el NIT
 */
public record IdentidadTributaria(String tipoPersona, String tipoDocumento, String razonSocial, String nombres,
        String apellidos, String nombreComercial, String dv) {

    public static final String NATURAL = "NATURAL";
    public static final String JURIDICA = "JURIDICA";

    /**
     * Valida y arma la identidad. Una jurídica exige razón social y documento NIT;
     * una natural exige nombres y apellidos. El DV vacío se calcula con el NIT.
     *
     * @throws GlobalException 400 si falta un dato o no cuadra con el tipo de persona
     */
    public static IdentidadTributaria resolver(String tipoPersona, String tipoDocumento, String razonSocial,
            String nombres, String apellidos, String nombreComercial, String dv, String nit) {
        String tipo = limpiar(tipoPersona) == null ? JURIDICA : tipoPersona.trim().toUpperCase();
        if (!NATURAL.equals(tipo) && !JURIDICA.equals(tipo))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El tipo de persona debe ser natural o jurídica");
        String doc = limpiar(tipoDocumento) == null ? "NIT" : tipoDocumento.trim().toUpperCase();
        String dvFinal = limpiar(dv) != null ? dv.trim() : calcularDv(nit);

        if (JURIDICA.equals(tipo)) {
            if (!"NIT".equals(doc))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Una persona jurídica se identifica con NIT");
            if (limpiar(razonSocial) == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La razón social es obligatoria");
            return new IdentidadTributaria(tipo, doc, razonSocial.trim(), null, null, limpiar(nombreComercial), dvFinal);
        }
        if (limpiar(nombres) == null || limpiar(apellidos) == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Para una persona natural escriba los nombres y los apellidos");
        String nombreCompleto = nombres.trim() + " " + apellidos.trim();
        // La cédula sin RUT no lleva DV; con NIT (cédula + DV) sí.
        return new IdentidadTributaria(tipo, doc, nombreCompleto, nombres.trim(), apellidos.trim(),
                limpiar(nombreComercial), "NIT".equals(doc) ? dvFinal : null);
    }

    /** Escribe la identidad en la empresa (el NIT no cambia). */
    public void aplicarA(EmpresaEntity empresa) {
        empresa.setTipoPersona(tipoPersona);
        empresa.setRazonSocial(razonSocial);
        empresa.setNombreComercial(nombreComercial);
        empresa.setDv(dv);
    }

    /**
     * Escribe la identidad en el tercero de la empresa: los campos de la persona
     * natural (los viejos para mostrar y los desagregados para DIAN/UGPP, V97) o la
     * razón social de la jurídica, limpiando los del otro tipo.
     */
    public void aplicarA(TerceroEntity tercero) {
        tercero.setTipoPersona(tipoPersona);
        tercero.setTipoDocumento(tipoDocumento);
        tercero.setDv(dv);
        tercero.setNombreComercial(nombreComercial);
        if (NATURAL.equals(tipoPersona)) {
            tercero.setRazonSocial(null);
            tercero.setNombres(nombres);
            tercero.setApellidos(apellidos);
            tercero.setNombre1(parte(nombres, 0));
            tercero.setNombre2(parte(nombres, 1));
            tercero.setApellido1(parte(apellidos, 0));
            tercero.setApellido2(parte(apellidos, 1));
        } else {
            tercero.setRazonSocial(razonSocial);
            tercero.setNombres(null);
            tercero.setApellidos(null);
            tercero.setNombre1(null);
            tercero.setNombre2(null);
            tercero.setApellido1(null);
            tercero.setApellido2(null);
        }
    }

    private static String calcularDv(String nit) {
        if (nit == null || nit.replaceAll("\\D", "").isEmpty()) return null;
        return String.valueOf(DocumentoSoporteArmador.digitoVerificacion(nit));
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
