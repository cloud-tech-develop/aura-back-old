package com.cloud_technological.aura_pos.services.nube;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.entity.nube.NubeArchivoEntity;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Reglas de lo que entra a la nube (docs/PLAN_NUBE_DOCUMENTOS.md §4).
 *
 * <ul>
 *   <li>El nombre se limpia: sin rutas, sin caracteres de control ni de dirección
 *       de texto (el truco "factura‮fdp.exe"), sin caracteres prohibidos en Windows,
 *       sin nombres reservados y sin extensiones peligrosas escondidas.</li>
 *   <li>El tipo se detecta por el CONTENIDO (firma de bytes con Apache Tika) y debe
 *       coincidir con la extensión. Lo que diga el navegador no cuenta.</li>
 *   <li>Office: se rechazan los que traen macros, aunque se llamen .xlsx/.docx.
 *       El ZIP de Office se recorre con tope de descompresión (bomba ZIP).</li>
 * </ul>
 *
 * <p>Sin estado de Spring para poder probarla sola.
 */
public final class ValidadorArchivoNube {

    public static final int MAX_LARGO_NOMBRE = 150;

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLS = "application/vnd.ms-excel";

    /** Extensión permitida → tipos detectados que se aceptan para ella. */
    private static final Map<String, Set<String>> PERMITIDOS = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("xlsx", Set.of(XLSX)),
            Map.entry("docx", Set.of(DOCX)),
            Map.entry("xls", Set.of(XLS)),
            Map.entry("csv", Set.of("text/csv", "text/plain")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("xml", Set.of("application/xml", "text/xml", "text/plain")));

    /** Tipo que se guarda (y con el que se descarga) por extensión. */
    private static final Map<String, String> MIME_GUARDADO = Map.ofEntries(
            Map.entry("pdf", "application/pdf"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("webp", "image/webp"), Map.entry("xlsx", XLSX),
            Map.entry("docx", DOCX), Map.entry("xls", XLS), Map.entry("csv", "text/csv"),
            Map.entry("txt", "text/plain"), Map.entry("xml", "application/xml"));

    /** Extensiones que no pueden aparecer en ninguna parte del nombre ("a.exe.pdf"). */
    private static final Set<String> PELIGROSAS = Set.of(
            "exe", "com", "bat", "cmd", "msi", "scr", "pif", "cpl", "dll", "ps1", "psm1", "vbs", "vbe", "js", "jse",
            "wsf", "wsh", "hta", "jar", "sh", "bash", "lnk", "reg", "html", "htm", "xhtml", "svg", "php", "asp",
            "aspx", "jsp", "xlsm", "xltm", "docm", "dotm", "pptm", "zip", "rar", "7z", "tar", "gz", "iso", "apk");

    private static final Set<String> RESERVADOS_WINDOWS = Set.of(
            "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    /** Tope de bytes descomprimidos al revisar un Office (un .xlsx normal de 1 MB no pasa de unos MB). */
    private static final long MAX_DESCOMPRIMIDO = 40L * 1024 * 1024;
    private static final int MAX_ENTRADAS_ZIP = 2000;

    private static final Tika TIKA = new Tika();

    private ValidadorArchivoNube() {
    }

    /** Resultado: nombre limpio, extensión y tipo a guardar. */
    public record ArchivoValido(String nombre, String extension, String mime) {
    }

    /**
     * Valida un archivo que se quiere subir.
     *
     * @param nombreOriginal nombre que mandó el navegador
     * @param contenido      bytes del archivo
     * @throws GlobalException 400 con un mensaje para el usuario si no cumple
     */
    public static ArchivoValido validar(String nombreOriginal, byte[] contenido) {
        if (contenido == null || contenido.length == 0)
            throw error("El archivo está vacío.");
        if (contenido.length > NubeArchivoEntity.MAX_BYTES)
            throw error("El archivo pesa más de 1 MB. Comprímalo o divídalo antes de subirlo.");

        String nombre = limpiarNombre(nombreOriginal);
        int punto = nombre.lastIndexOf('.');
        if (punto <= 0 || punto == nombre.length() - 1)
            throw error("El archivo debe tener extensión (por ejemplo .pdf).");
        String ext = nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        Set<String> aceptados = PERMITIDOS.get(ext);
        if (aceptados == null)
            throw error("Tipo de archivo no permitido. Se aceptan PDF, JPG, PNG, WebP, Excel (XLSX, XLS), Word (DOCX), CSV, TXT y XML.");
        for (String parte : nombre.toLowerCase(Locale.ROOT).split("\\.")) {
            if (PELIGROSAS.contains(parte.trim()))
                throw error("El nombre del archivo tiene una extensión no permitida (." + parte.trim() + ").");
        }

        String detectado = TIKA.detect(contenido, nombre);
        if (!aceptados.contains(detectado))
            throw error("El contenido del archivo no corresponde a un ." + ext + ". Ábralo y guárdelo de nuevo en ese formato.");

        switch (ext) {
            case "xlsx", "docx" -> revisarOfficeAbierto(contenido);
            case "xls" -> revisarOfficeViejo(contenido);
            case "csv", "txt", "xml" -> revisarTexto(contenido, ext);
            default -> { }
        }
        return new ArchivoValido(nombre, ext, MIME_GUARDADO.get(ext));
    }

    /**
     * Deja un nombre seguro para mostrar y guardar. No se usa nunca como ruta: la
     * clave del objeto en el bucket es un UUID.
     */
    public static String limpiarNombre(String original) {
        if (original == null) throw error("El archivo no tiene nombre.");
        String n = original;
        // Solo el último tramo: "C:\fakepath\a.pdf", "../../x.pdf".
        int corte = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (corte >= 0) n = n.substring(corte + 1);
        n = Normalizer.normalize(n, Normalizer.Form.NFC);

        StringBuilder sb = new StringBuilder(n.length());
        n.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp)) return;
            // Formato invisible: dirección de texto (U+202A..202E, 2066..2069), ancho cero, BOM.
            if (Character.getType(cp) == Character.FORMAT) return;
            if ("<>:\"|?*".indexOf(cp) >= 0) {
                sb.append('_');
                return;
            }
            sb.appendCodePoint(cp);
        });
        n = sb.toString().replaceAll("\\s+", " ").trim();
        // Sin puntos o espacios al inicio/fin ni "..".
        n = n.replaceAll("\\.{2,}", ".").replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (n.isEmpty()) throw error("El nombre del archivo no es válido.");

        String base = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
        if (RESERVADOS_WINDOWS.contains(base.toLowerCase(Locale.ROOT)))
            throw error("El nombre del archivo no es válido.");

        if (n.length() > MAX_LARGO_NOMBRE) {
            int punto = n.lastIndexOf('.');
            String ext = punto > 0 && n.length() - punto <= 6 ? n.substring(punto) : "";
            n = n.substring(0, MAX_LARGO_NOMBRE - ext.length()).trim() + ext;
        }
        return n;
    }

    /** Nombre de carpeta: mismas reglas de caracteres, sin extensión obligatoria. */
    public static String limpiarNombreCarpeta(String original) {
        String n = limpiarNombre(original);
        if (n.length() > MAX_LARGO_NOMBRE) n = n.substring(0, MAX_LARGO_NOMBRE).trim();
        return n;
    }

    /** XLSX/DOCX: ZIP de Office real, sin macros, sin bomba. */
    private static void revisarOfficeAbierto(byte[] contenido) {
        boolean tieneTipos = false;
        long total = 0;
        int entradas = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (++entradas > MAX_ENTRADAS_ZIP) throw error("El archivo de Office tiene una estructura inválida.");
                String nombre = e.getName().toLowerCase(Locale.ROOT);
                if (nombre.equals("[content_types].xml")) tieneTipos = true;
                if (nombre.endsWith("vbaproject.bin") || nombre.endsWith(".bin") && nombre.contains("activex"))
                    throw error("El archivo tiene macros. Guárdelo como libro o documento sin macros antes de subirlo.");
                int leidos;
                while ((leidos = zip.read(buffer)) > 0) {
                    total += leidos;
                    if (total > MAX_DESCOMPRIMIDO) throw error("El archivo de Office tiene una estructura inválida.");
                }
            }
        } catch (IOException ex) {
            throw error("El archivo de Office está dañado.");
        }
        if (!tieneTipos) throw error("El archivo de Office está dañado.");
    }

    /** XLS (formato viejo): rechaza el proyecto de macros VBA. */
    private static void revisarOfficeViejo(byte[] contenido) {
        byte[] vba = "_VBA_PROJECT".getBytes(StandardCharsets.UTF_16LE);
        if (contiene(contenido, vba))
            throw error("El archivo tiene macros. Guárdelo como libro sin macros (.xlsx) antes de subirlo.");
    }

    /** Texto: sin bytes nulos (no es binario disfrazado); XML debe empezar por una etiqueta. */
    private static void revisarTexto(byte[] contenido, String ext) {
        for (byte b : contenido) {
            if (b == 0) throw error("El archivo ." + ext + " no es texto.");
        }
        if (ext.equals("xml")) {
            String inicio = new String(contenido, 0, Math.min(contenido.length, 64), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").stripLeading();
            if (!inicio.startsWith("<")) throw error("El archivo .xml no es un XML válido.");
        }
    }

    private static boolean contiene(byte[] datos, byte[] patron) {
        outer:
        for (int i = 0; i <= datos.length - patron.length; i++) {
            for (int j = 0; j < patron.length; j++) {
                if (datos[i + j] != patron[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    /** Las extensiones aceptadas, para mostrarlas en el front. */
    public static List<String> extensionesPermitidas() {
        return PERMITIDOS.keySet().stream().sorted().toList();
    }

    private static GlobalException error(String mensaje) {
        return new GlobalException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
