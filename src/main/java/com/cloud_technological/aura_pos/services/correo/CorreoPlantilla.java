package com.cloud_technological.aura_pos.services.correo;

/**
 * HTML del correo: encabezado con el logo o el nombre de quien envía, el mensaje, un
 * botón opcional (p. ej. "Crear mi contraseña"), el aviso del adjunto y un pie que
 * dice que la dirección es no-reply (CORREO_REMITENTE no recibe respuestas) y, si la
 * empresa tiene correo, a dónde escribir. Estilos en línea (los clientes de correo
 * ignoran las hojas de estilo). Todo el texto se escapa.
 */
public final class CorreoPlantilla {

    private static final String AZUL = "#2563eb";

    private CorreoPlantilla() {
    }

    /** Correo con documento adjunto y sin botón. */
    public static String html(String empresa, String logoUrl, String mensaje, String adjuntoNombre) {
        return html(empresa, logoUrl, mensaje, adjuntoNombre, null, null, null);
    }

    /**
     * @param empresa       nombre de quien envía (la empresa, o "Aura" en los correos del sistema)
     * @param logoUrl       logo público (https); null = solo el nombre
     * @param mensaje       texto (los saltos de línea se respetan)
     * @param adjuntoNombre PDF adjunto; null = sin adjunto
     * @param contacto      correo de la empresa para responder; null = solo el aviso no-reply
     * @param botonTexto    texto del botón; null = sin botón
     * @param botonUrl      enlace del botón (solo https/http)
     */
    public static String html(String empresa, String logoUrl, String mensaje, String adjuntoNombre, String contacto,
            String botonTexto, String botonUrl) {
        String nombre = escapar(empresa);
        String cabecera = logoUrl != null && logoUrl.startsWith("https://")
                ? "<img src=\"" + escapar(logoUrl) + "\" alt=\"" + nombre
                        + "\" style=\"max-height:56px;max-width:220px;display:block;margin:0 auto 8px\">"
                : "";
        String cuerpo = escapar(mensaje == null ? "" : mensaje.trim()).replace("\r\n", "\n").replace("\n", "<br>");
        String boton = botonTexto != null && botonUrl != null && botonUrl.matches("^https?://.*")
                ? "<tr><td style=\"padding:0 28px 24px;text-align:center\"><a href=\"" + escapar(botonUrl)
                        + "\" style=\"display:inline-block;padding:12px 22px;border-radius:8px;background:" + AZUL
                        + ";color:#ffffff;text-decoration:none;font-weight:bold;font-size:14px\">" + escapar(botonTexto)
                        + "</a><div style=\"margin-top:10px;font-size:11px;color:#64748b;word-break:break-all\">"
                        + "Si el botón no funciona, copie este enlace: " + escapar(botonUrl) + "</div></td></tr>"
                : "";
        String adjunto = adjuntoNombre != null
                ? "<tr><td style=\"padding:0 28px 24px\"><div style=\"padding:12px 14px;border-radius:8px;"
                        + "background:#eff6ff;font-size:13px;color:#1e3a8a\">&#128206; Adjunto: <strong>"
                        + escapar(adjuntoNombre) + "</strong></div></td></tr>"
                : "";
        String pie = "Este correo se envió desde una dirección que no recibe respuestas (no-reply)."
                + (contacto != null && !contacto.isBlank()
                        ? " Si tiene preguntas, escriba a <a href=\"mailto:" + escapar(contacto) + "\" style=\"color:"
                                + AZUL + "\">" + escapar(contacto) + "</a>."
                        : "");
        return """
                <!doctype html>
                <html lang="es"><body style="margin:0;padding:0;background:#f4f6fa;font-family:Arial,Helvetica,sans-serif;color:#1e293b">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fa;padding:24px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0">
                <tr><td style="padding:24px 28px 16px;text-align:center;border-bottom:3px solid %s">
                %s<div style="font-size:18px;font-weight:bold;color:#0f172a">%s</div>
                </td></tr>
                <tr><td style="padding:24px 28px;font-size:14px;line-height:1.6">%s</td></tr>
                %s%s
                <tr><td style="padding:16px 28px;background:#f8fafc;font-size:11px;color:#64748b;text-align:center">
                %s<br>Enviado por %s desde Aura.
                </td></tr>
                </table></td></tr></table></body></html>
                """.formatted(AZUL, cabecera, nombre, cuerpo, boton, adjunto, pie, nombre);
    }

    static String escapar(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
