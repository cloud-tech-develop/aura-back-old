package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.contabilidad.CreateSaldosInicialesDto;
import com.cloud_technological.aura_pos.dto.contabilidad.SaldoInicialLineaDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.DocumentosAbiertos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.FilaCuenta;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.FilaDocumentoAbierto;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.FilaSaldo;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.FilaTercero;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.PlanCuentas;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.ResultadoFila;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Saldos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Terceros;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.importacion.ImportacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.importacion.ImportacionQueryRepository.Cuenta;
import com.cloud_technological.aura_pos.repositories.importacion.ImportacionQueryRepository.Municipio;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.AperturaContableService;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.CuentaCobrarService;
import com.cloud_technological.aura_pos.services.CuentaPagarService;
import com.cloud_technological.aura_pos.services.ImportacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Importador desde Excel para migrar una empresa que viene de otro software:
 * plan de cuentas, terceros, saldos iniciales y cartera/proveedores abiertos.
 *
 * <p>Cada paso tiene validar (no graba nada, dice fila por fila qué pasa) y
 * confirmar (vuelve a validar y graba todo o nada). Lo que ya existe no se
 * pisa: se reporta y se salta, así un archivo se puede cargar dos veces sin
 * duplicar.
 */
@Service
@RequiredArgsConstructor
public class ImportacionServiceImpl implements ImportacionService {

    private static final String NUEVO = "NUEVO";
    private static final String EXISTE = "EXISTE";
    private static final String ERROR = "ERROR";
    private static final String ADVERTENCIA = "ADVERTENCIA";

    private static final Set<String> TIPOS_DOCUMENTO = Set.of(
            "CC", "NIT", "CE", "TI", "RC", "PASAPORTE", "PEP", "PPT", "NUIP", "TE", "DIE");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ImportacionQueryRepository queryRepo;
    private final PlanCuentaJPARepository planRepo;
    private final TerceroJPARepository terceroRepo;
    private final AsientoContableJPARepository asientoRepo;
    private final AperturaContableService aperturaService;
    private final ConfiguracionContableService config;
    private final CuentaCobrarService cuentaCobrarService;
    private final CuentaPagarService cuentaPagarService;
    private final EntityManager em;

    // ════════════════════════════════════════════════════════════════════════
    // Plan de cuentas
    // ════════════════════════════════════════════════════════════════════════

    /** Nivel PUC por largo del código: 1 clase, 2 grupo, 4 cuenta, 6 subcuenta, 8+ auxiliar. */
    public static short nivel(String codigo) {
        int n = codigo.length();
        return (short) (n <= 2 ? n : n / 2 + 1);
    }

    /** Código del padre PUC: 1105 → 11, 110505 → 1105, 11 → 1. */
    public static String codigoPadre(String codigo) {
        int n = codigo.length();
        if (n <= 1) return null;
        if (n == 2) return codigo.substring(0, 1);
        return codigo.substring(0, n - (n % 2 == 0 ? 2 : 1));
    }

    public static String tipoPorClase(String codigo) {
        return switch (codigo.charAt(0)) {
            case '1' -> "ACTIVO";
            case '2' -> "PASIVO";
            case '3' -> "PATRIMONIO";
            case '4' -> "INGRESO";
            case '5' -> "GASTO";
            case '6', '7' -> "COSTO";
            default -> "ORDEN";
        };
    }

    public static String naturalezaPorClase(String codigo) {
        return switch (codigo.charAt(0)) {
            case '2', '3', '4', '9' -> "CREDITO";
            default -> "DEBITO";
        };
    }

    @Override
    public Resultado validarPlan(Integer empresaId, PlanCuentas req) {
        return procesarPlan(empresaId, req, false);
    }

    @Override
    @Transactional
    public Resultado confirmarPlan(Integer empresaId, PlanCuentas req) {
        return procesarPlan(empresaId, req, true);
    }

    private Resultado procesarPlan(Integer empresaId, PlanCuentas req, boolean grabar) {
        Resultado r = new Resultado();
        Map<String, Cuenta> existentes = queryRepo.cuentas(empresaId);

        List<FilaCuenta> filas = new ArrayList<>(req.getFilas());
        // Los padres antes que los hijos, vengan en el orden que vengan.
        filas.sort(Comparator.comparingInt((FilaCuenta f) -> limpiarCodigo(f.getCodigo()).length()));

        Set<String> enArchivo = new HashSet<>();
        Map<String, FilaCuenta> nuevas = new LinkedHashMap<>();
        for (FilaCuenta f : filas) {
            String codigo = limpiarCodigo(f.getCodigo());
            String nombre = f.getNombre() != null ? f.getNombre().trim() : "";
            if (codigo.isEmpty() || !codigo.matches("\\d+")) {
                error(r, f.getFila(), f.getCodigo(), "El código debe ser numérico");
                continue;
            }
            if (nombre.isEmpty()) {
                error(r, f.getFila(), codigo, "Falta el nombre de la cuenta");
                continue;
            }
            if (!enArchivo.add(codigo)) {
                error(r, f.getFila(), codigo, "El código está repetido en el archivo");
                continue;
            }
            String nat = f.getNaturaleza() != null ? f.getNaturaleza().trim().toUpperCase() : "";
            if (!nat.isEmpty() && !nat.equals("DEBITO") && !nat.equals("CREDITO")) {
                error(r, f.getFila(), codigo, "Naturaleza debe ser DEBITO o CREDITO");
                continue;
            }
            if (existentes.containsKey(codigo)) {
                r.getFilas().add(new ResultadoFila(f.getFila(), EXISTE, codigo + " " + nombre,
                        "Ya existe: no se modifica"));
                r.setExistentes(r.getExistentes() + 1);
                continue;
            }
            String padre = codigoPadre(codigo);
            if (padre != null && !existentes.containsKey(padre) && !nuevas.containsKey(padre)) {
                error(r, f.getFila(), codigo, "No existe su cuenta padre " + padre
                        + ": inclúyala en el archivo o créela antes");
                continue;
            }
            f.setCodigo(codigo);
            f.setNombre(nombre);
            nuevas.put(codigo, f);
            r.getFilas().add(new ResultadoFila(f.getFila(), NUEVO, codigo + " " + nombre,
                    tipoPorClase(codigo) + " · " + (nat.isEmpty() ? naturalezaPorClase(codigo) : nat)));
            r.setNuevos(r.getNuevos() + 1);
        }

        // Una cuenta que va a tener hijas deja de recibir movimientos. Si ya
        // los tiene, se avisa: el contador tendrá que reclasificar su saldo.
        Set<String> seranPadres = new HashSet<>();
        for (String codigo : nuevas.keySet()) {
            String padre = codigoPadre(codigo);
            if (padre != null) seranPadres.add(padre);
        }
        for (String padre : seranPadres) {
            Cuenta c = existentes.get(padre);
            if (c != null && c.auxiliar() && c.conMovimientos()) {
                r.getAvisos().add("La cuenta " + padre + " ya tiene movimientos y va a recibir subcuentas: "
                        + "queda como agrupadora y su saldo actual se debe reclasificar con una nota contable");
            }
        }

        if (!grabar) return r;
        exigirSinErrores(r);

        Map<String, Long> ids = new HashMap<>();
        existentes.forEach((k, v) -> ids.put(k, v.id()));
        for (FilaCuenta f : nuevas.values()) {
            String codigo = f.getCodigo();
            boolean auxiliar = !seranPadres.contains(codigo);
            String nat = f.getNaturaleza() != null && !f.getNaturaleza().isBlank()
                    ? f.getNaturaleza().trim().toUpperCase() : naturalezaPorClase(codigo);
            boolean medioPago = auxiliar && (codigo.startsWith("1105") || codigo.startsWith("1110")
                    || codigo.startsWith("1120"));
            PlanCuentaEntity e = PlanCuentaEntity.builder()
                    .empresaId(empresaId)
                    .codigo(codigo)
                    .nombre(f.getNombre())
                    .tipo(tipoPorClase(codigo))
                    .naturaleza(nat)
                    .nivel(nivel(codigo))
                    .padreId(codigoPadre(codigo) != null ? ids.get(codigoPadre(codigo)) : null)
                    .activa(true)
                    .auxiliar(auxiliar)
                    .esMedioPago(medioPago)
                    .build();
            ids.put(codigo, planRepo.save(e).getId());
        }
        for (String padre : seranPadres) {
            Cuenta c = existentes.get(padre);
            if (c != null && c.auxiliar()) {
                planRepo.findByIdAndEmpresaId(c.id(), empresaId).ifPresent(p -> {
                    p.setAuxiliar(false);
                    p.setEsMedioPago(false);
                    planRepo.save(p);
                });
            }
        }
        r.setConfirmado(true);
        r.getAvisos().add(r.getNuevos() + " cuenta(s) creadas");
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Terceros
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Resultado validarTerceros(Integer empresaId, Terceros req) {
        return procesarTerceros(empresaId, req, false);
    }

    @Override
    @Transactional
    public Resultado confirmarTerceros(Integer empresaId, Terceros req) {
        return procesarTerceros(empresaId, req, true);
    }

    private Resultado procesarTerceros(Integer empresaId, Terceros req, boolean grabar) {
        Resultado r = new Resultado();
        Set<String> docs = new HashSet<>();
        for (FilaTercero f : req.getFilas()) docs.add(limpiarDocumento(f.getNumeroDocumento()));
        Map<String, Long> existentes = queryRepo.terceros(empresaId, docs);
        Municipios municipios = new Municipios(queryRepo.municipios());

        Set<String> enArchivo = new HashSet<>();
        List<TerceroEntity> aCrear = new ArrayList<>();
        EmpresaEntity empresa = grabar ? em.getReference(EmpresaEntity.class, empresaId) : null;

        for (FilaTercero f : req.getFilas()) {
            String tipo = f.getTipoDocumento() != null ? f.getTipoDocumento().trim().toUpperCase() : "";
            String numero = limpiarDocumento(f.getNumeroDocumento());
            String razon = texto(f.getRazonSocial());
            String nombres = texto(f.getNombres());
            String apellidos = texto(f.getApellidos());
            String etiqueta = numero + " " + (razon != null ? razon
                    : ((nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "")).trim());

            if (!TIPOS_DOCUMENTO.contains(tipo)) {
                error(r, f.getFila(), etiqueta, "Tipo de documento no reconocido: " + f.getTipoDocumento()
                        + " (use CC, NIT, CE, TI, PASAPORTE, PEP, PPT…)");
                continue;
            }
            if (numero.isEmpty()) {
                error(r, f.getFila(), etiqueta, "Falta el número de documento");
                continue;
            }
            if (razon == null && nombres == null) {
                error(r, f.getFila(), etiqueta, "Falta la razón social o los nombres");
                continue;
            }
            if (!enArchivo.add(numero)) {
                error(r, f.getFila(), etiqueta, "El documento está repetido en el archivo");
                continue;
            }
            if (existentes.containsKey(numero)) {
                r.getFilas().add(new ResultadoFila(f.getFila(), EXISTE, etiqueta, "Ya existe: no se modifica"));
                r.setExistentes(r.getExistentes() + 1);
                continue;
            }
            String email = texto(f.getEmail());
            if (email != null && !EMAIL.matcher(email).matches()) {
                error(r, f.getFila(), etiqueta, "Correo inválido: " + email);
                continue;
            }
            Municipio mun = null;
            String municipioTxt = texto(f.getMunicipio());
            if (municipioTxt != null) {
                try {
                    mun = municipios.buscar(municipioTxt);
                } catch (IllegalArgumentException ex) {
                    error(r, f.getFila(), etiqueta, ex.getMessage());
                    continue;
                }
            }
            boolean cliente = Boolean.TRUE.equals(f.getEsCliente());
            boolean proveedor = Boolean.TRUE.equals(f.getEsProveedor());
            String mensaje = "";
            if (!cliente && !proveedor) {
                cliente = true;
                mensaje = "Sin rol indicado: se crea como cliente. ";
            }
            String persona = texto(f.getTipoPersona()) != null ? f.getTipoPersona().trim().toUpperCase()
                    : ("NIT".equals(tipo) ? "JURIDICA" : "NATURAL");
            String regimen = texto(f.getRegimen()) != null ? normalizarRegimen(f.getRegimen()) : null;

            r.getFilas().add(new ResultadoFila(f.getFila(), mensaje.isEmpty() ? NUEVO : ADVERTENCIA,
                    etiqueta, mensaje + (cliente ? "Cliente " : "") + (proveedor ? "Proveedor " : "")
                            + (mun != null ? "· " + mun.nombre() : "")));
            r.setNuevos(r.getNuevos() + 1);

            if (grabar) {
                String[] n = partir(nombres);
                String[] a = partir(apellidos);
                aCrear.add(TerceroEntity.builder()
                        .empresa(empresa)
                        .tipoDocumento(tipo)
                        .numeroDocumento(numero)
                        .dv(texto(f.getDv()))
                        .razonSocial(razon)
                        .nombres(nombres)
                        .apellidos(apellidos)
                        .nombre1(n[0]).nombre2(n[1])
                        .apellido1(a[0]).apellido2(a[1])
                        .direccion(texto(f.getDireccion()))
                        .telefono(texto(f.getTelefono()))
                        .email(email)
                        .municipioId(mun != null ? mun.id() : null)
                        .municipio(mun != null ? mun.nombre() : municipioTxt)
                        .tipoPersona(persona)
                        .regimen(regimen)
                        .responsabilidadFiscal("RESPONSABLE_IVA".equals(regimen) ? "Responsable de IVA"
                                : "NO_RESPONSABLE_IVA".equals(regimen) ? "No responsable de IVA" : null)
                        .codigoPais("CO")
                        .esCliente(cliente)
                        .esProveedor(proveedor)
                        .esEmpleado(false)
                        .esBanco(false)
                        .activo(true)
                        .build());
            }
        }
        if (!grabar) return r;
        exigirSinErrores(r);
        terceroRepo.saveAll(aCrear);
        r.setConfirmado(true);
        r.getAvisos().add(aCrear.size() + " tercero(s) creados");
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Saldos iniciales
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Resultado validarSaldos(Integer empresaId, Saldos req) {
        return procesarSaldos(empresaId, req, null, false);
    }

    @Override
    @Transactional
    public Resultado confirmarSaldos(Integer empresaId, Integer usuarioId, Saldos req) {
        return procesarSaldos(empresaId, req, usuarioId, true);
    }

    private Resultado procesarSaldos(Integer empresaId, Saldos req, Integer usuarioId, boolean grabar) {
        Resultado r = new Resultado();
        if (req.getFechaApertura() == null) {
            r.getAvisos().add("Indique la fecha de apertura");
            r.setErrores(1);
        }
        if (asientoRepo.findFirstByEmpresaIdAndTipoOrigen(empresaId, "APERTURA").isPresent()) {
            r.getAvisos().add("Ya hay saldos iniciales cargados. Elimínelos en Saldos iniciales para "
                    + "volver a cargarlos");
            r.setErrores(r.getErrores() + 1);
        }
        Map<String, Cuenta> cuentas = queryRepo.cuentas(empresaId);
        Set<String> docs = new HashSet<>();
        for (FilaSaldo f : req.getFilas()) {
            String d = limpiarDocumento(f.getDocumentoTercero());
            if (!d.isEmpty()) docs.add(d);
        }
        Map<String, Long> terceros = queryRepo.terceros(empresaId, docs);

        List<SaldoInicialLineaDto> lineas = new ArrayList<>();
        BigDecimal db = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (FilaSaldo f : req.getFilas()) {
            String codigo = limpiarCodigo(f.getCodigoCuenta());
            BigDecimal debito = nz(f.getDebito());
            BigDecimal credito = nz(f.getCredito());
            String doc = limpiarDocumento(f.getDocumentoTercero());
            String etiqueta = codigo + (doc.isEmpty() ? "" : " · " + doc);

            if (debito.signum() == 0 && credito.signum() == 0) continue;
            Cuenta c = cuentas.get(codigo);
            if (c == null) {
                error(r, f.getFila(), etiqueta, "La cuenta " + codigo + " no existe en el plan de cuentas");
                continue;
            }
            if (!c.activa() || !c.auxiliar()) {
                error(r, f.getFila(), etiqueta, "La cuenta " + codigo
                        + " es de agrupación o está inactiva: use una subcuenta auxiliar");
                continue;
            }
            if (debito.signum() < 0 || credito.signum() < 0) {
                error(r, f.getFila(), etiqueta, "Los valores no pueden ser negativos");
                continue;
            }
            if (debito.signum() > 0 && credito.signum() > 0) {
                error(r, f.getFila(), etiqueta, "Una fila lleva débito o crédito, no los dos");
                continue;
            }
            Long terceroId = null;
            if (!doc.isEmpty()) {
                terceroId = terceros.get(doc);
                if (terceroId == null) {
                    error(r, f.getFila(), etiqueta, "El tercero " + doc + " no existe: impórtelo primero");
                    continue;
                }
            }
            boolean pideTercero = codigo.startsWith("13") || codigo.startsWith("22")
                    || codigo.startsWith("23");
            r.getFilas().add(new ResultadoFila(f.getFila(), pideTercero && terceroId == null ? ADVERTENCIA : NUEVO,
                    etiqueta, pideTercero && terceroId == null
                            ? "Sin tercero: no se podrá cruzar con la cartera ni con los proveedores"
                            : (debito.signum() > 0 ? "Débito " : "Crédito ") + (debito.signum() > 0 ? debito : credito)));
            r.setNuevos(r.getNuevos() + 1);

            SaldoInicialLineaDto l = new SaldoInicialLineaDto();
            l.setCuentaId(c.id());
            l.setDebito(debito);
            l.setCredito(credito);
            l.setTerceroId(terceroId);
            lineas.add(l);
            db = db.add(debito);
            cr = cr.add(credito);
        }
        r.setTotalDebito(db);
        r.setTotalCredito(cr);

        Long cuentaAjusteId = null;
        String codAjuste = limpiarCodigo(req.getCodigoCuentaAjuste());
        if (!codAjuste.isEmpty()) {
            Cuenta aj = cuentas.get(codAjuste);
            if (aj == null || !aj.auxiliar()) {
                r.getAvisos().add("La cuenta de ajuste " + codAjuste + " no existe o no es auxiliar");
                r.setErrores(r.getErrores() + 1);
            } else {
                cuentaAjusteId = aj.id();
            }
        }
        BigDecimal diferencia = db.subtract(cr);
        if (diferencia.signum() != 0) {
            r.getAvisos().add("Los saldos no cuadran por " + diferencia.abs().toPlainString()
                    + ": la diferencia se lleva a " + (codAjuste.isEmpty()
                            ? "resultados de ejercicios anteriores" : "la cuenta " + codAjuste)
                    + ". Revise el archivo si no esperaba un descuadre");
        }

        if (!grabar) return r;
        exigirSinErrores(r);
        CreateSaldosInicialesDto dto = new CreateSaldosInicialesDto();
        dto.setFechaApertura(req.getFechaApertura());
        dto.setCuentaAjusteId(cuentaAjusteId);
        dto.setLineas(lineas);
        var asiento = aperturaService.guardar(dto, empresaId, usuarioId);
        r.setConfirmado(true);
        r.getAvisos().add("Asiento de apertura " + asiento.getNumeroComprobante() + " registrado");
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Cartera y proveedores abiertos
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public Resultado validarDocumentos(Integer empresaId, DocumentosAbiertos req) {
        return procesarDocumentos(empresaId, req, null, false);
    }

    @Override
    @Transactional
    public Resultado confirmarDocumentos(Integer empresaId, Long usuarioId, DocumentosAbiertos req) {
        return procesarDocumentos(empresaId, req, usuarioId, true);
    }

    /**
     * Las facturas pendientes del sistema anterior. Crean la cuenta por cobrar
     * o por pagar SIN asiento: el saldo contable ya lo trae la apertura, y
     * contabilizarlo aquí lo duplicaría. Al final se cruza por tercero contra
     * la apertura y se avisa lo que no coincida.
     */
    private Resultado procesarDocumentos(Integer empresaId, DocumentosAbiertos req, Long usuarioId,
            boolean grabar) {
        Resultado r = new Resultado();
        String tipo = req.getTipo() != null ? req.getTipo().trim().toUpperCase() : "";
        if (!tipo.equals("COBRAR") && !tipo.equals("PAGAR")) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El tipo debe ser COBRAR o PAGAR");
        }
        boolean cobrar = tipo.equals("COBRAR");
        String tabla = cobrar ? "cuentas_cobrar" : "cuentas_pagar";

        Set<String> docs = new HashSet<>();
        for (FilaDocumentoAbierto f : req.getFilas()) docs.add(limpiarDocumento(f.getDocumentoTercero()));
        Map<String, Long> terceros = queryRepo.terceros(empresaId, docs);
        Map<Long, TerceroEntity> entidades = new HashMap<>();

        Set<String> enArchivo = new HashSet<>();
        Map<Long, BigDecimal> porTercero = new HashMap<>();
        List<Object[]> aCrear = new ArrayList<>();
        for (FilaDocumentoAbierto f : req.getFilas()) {
            String doc = limpiarDocumento(f.getDocumentoTercero());
            String factura = texto(f.getNumeroFactura());
            String etiqueta = doc + " · " + (factura != null ? factura : "sin número");
            BigDecimal saldo = nz(f.getSaldo());

            Long terceroId = terceros.get(doc);
            if (terceroId == null) {
                error(r, f.getFila(), etiqueta, "El tercero " + doc + " no existe: impórtelo primero");
                continue;
            }
            TerceroEntity t = entidades.computeIfAbsent(terceroId,
                    id -> terceroRepo.findByIdAndEmpresaId(id, empresaId).orElse(null));
            if (t == null) {
                error(r, f.getFila(), etiqueta, "El tercero " + doc + " no pertenece a la empresa");
                continue;
            }
            if (cobrar && !Boolean.TRUE.equals(t.getEsCliente())) {
                error(r, f.getFila(), etiqueta, "El tercero " + doc + " no está marcado como cliente");
                continue;
            }
            if (!cobrar && !Boolean.TRUE.equals(t.getEsProveedor())) {
                error(r, f.getFila(), etiqueta, "El tercero " + doc + " no está marcado como proveedor");
                continue;
            }
            if (factura == null) {
                error(r, f.getFila(), etiqueta, "Falta el número de la factura");
                continue;
            }
            if (saldo.signum() <= 0) {
                error(r, f.getFila(), etiqueta, "El saldo debe ser mayor a cero");
                continue;
            }
            if (!enArchivo.add(doc + "|" + factura)) {
                error(r, f.getFila(), etiqueta, "La factura está repetida en el archivo");
                continue;
            }
            String marca = marca(factura);
            if (queryRepo.existeDocumentoImportado(tabla, empresaId, terceroId, marca)) {
                r.getFilas().add(new ResultadoFila(f.getFila(), EXISTE, etiqueta, "Ya importada: no se repite"));
                r.setExistentes(r.getExistentes() + 1);
                continue;
            }
            r.getFilas().add(new ResultadoFila(f.getFila(), NUEVO, etiqueta, "Saldo " + saldo.toPlainString()
                    + (f.getFechaVencimiento() != null ? " · vence " + f.getFechaVencimiento() : "")));
            r.setNuevos(r.getNuevos() + 1);
            porTercero.merge(terceroId, saldo, BigDecimal::add);
            aCrear.add(new Object[] { terceroId, f, marca });
        }

        cruzarConApertura(r, empresaId, cobrar, porTercero, entidades);

        if (!grabar) return r;
        exigirSinErrores(r);
        for (Object[] x : aCrear) {
            Long terceroId = (Long) x[0];
            FilaDocumentoAbierto f = (FilaDocumentoAbierto) x[1];
            String obs = (String) x[2];
            var emision = f.getFechaEmision() != null ? f.getFechaEmision().atStartOfDay() : null;
            var vence = f.getFechaVencimiento() != null ? f.getFechaVencimiento().atStartOfDay() : null;
            if (cobrar) {
                CreateCuentaCobrarDto dto = new CreateCuentaCobrarDto();
                dto.setClienteId(terceroId);
                dto.setTotalDeuda(f.getSaldo());
                dto.setFechaEmision(emision);
                dto.setFechaVencimiento(vence);
                dto.setObservaciones(obs);
                cuentaCobrarService.crear(dto, empresaId, usuarioId);
            } else {
                CreateCuentaPagarDto dto = new CreateCuentaPagarDto();
                dto.setProveedorId(terceroId);
                dto.setTotalDeuda(f.getSaldo());
                dto.setFechaEmision(emision);
                dto.setFechaVencimiento(vence);
                dto.setNumeroFacturaExterno(texto(f.getNumeroFactura()));
                dto.setObservaciones(obs);
                cuentaPagarService.crear(dto, empresaId, usuarioId);
            }
        }
        r.setConfirmado(true);
        r.getAvisos().add(aCrear.size() + (cobrar ? " cuenta(s) por cobrar" : " cuenta(s) por pagar")
                + " creadas, sin asiento: su saldo contable ya está en la apertura");
        return r;
    }

    /** Marca en observaciones que identifica la factura del sistema anterior. */
    private static String marca(String factura) {
        return "Saldo inicial · Factura " + factura;
    }

    /**
     * Si la apertura ya se cargó, lo pendiente de cada tercero debería ser su
     * saldo en clientes (o proveedores). Las diferencias se avisan, no bloquean:
     * puede haber anticipos o cuentas auxiliares distintas.
     */
    private void cruzarConApertura(Resultado r, Integer empresaId, boolean cobrar,
            Map<Long, BigDecimal> porTercero, Map<Long, TerceroEntity> entidades) {
        if (porTercero.isEmpty()) return;
        if (asientoRepo.findFirstByEmpresaIdAndTipoOrigen(empresaId, "APERTURA").isEmpty()) {
            r.getAvisos().add("Todavía no hay saldos iniciales: cargue la apertura (paso 3) para que "
                    + "estos documentos tengan su saldo contable");
            return;
        }
        PlanCuentaEntity cuenta;
        try {
            cuenta = config.resolverCuenta(empresaId,
                    cobrar ? ConceptoContable.CLIENTES : ConceptoContable.PROVEEDORES);
        } catch (Exception e) {
            return;
        }
        Map<Long, BigDecimal> contable = queryRepo.saldoPorTercero(empresaId, cuenta.getId());
        int avisos = 0;
        for (var e : porTercero.entrySet()) {
            BigDecimal enLibros = nz(contable.get(e.getKey()));
            if (!cobrar) enLibros = enLibros.negate();
            if (enLibros.subtract(e.getValue()).abs().compareTo(BigDecimal.ONE) > 0 && avisos++ < 10) {
                TerceroEntity t = entidades.get(e.getKey());
                r.getAvisos().add((t != null ? t.getNumeroDocumento() : "#" + e.getKey())
                        + ": documentos por " + e.getValue().toPlainString() + " pero la cuenta "
                        + cuenta.getCodigo() + " tiene " + enLibros.toPlainString() + " para ese tercero");
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Utilidades
    // ════════════════════════════════════════════════════════════════════════

    /** Búsqueda de municipio por código DANE o por nombre (sin tildes ni mayúsculas). */
    static final class Municipios {
        private final Map<String, Municipio> porCodigo = new HashMap<>();
        private final Map<String, List<Municipio>> porNombre = new HashMap<>();

        Municipios(List<Municipio> todos) {
            for (Municipio m : todos) {
                porCodigo.put(m.codigo(), m);
                porNombre.computeIfAbsent(normalizar(m.nombre()), k -> new ArrayList<>()).add(m);
            }
        }

        Municipio buscar(String texto) {
            String t = texto.trim();
            if (t.matches("\\d{4,5}")) {
                Municipio m = porCodigo.get(t.length() == 4 ? "0" + t : t);
                if (m == null) throw new IllegalArgumentException("No existe el municipio con código " + t);
                return m;
            }
            List<Municipio> ms = porNombre.get(normalizar(t));
            if (ms == null || ms.isEmpty()) {
                throw new IllegalArgumentException("No se encontró el municipio \"" + t
                        + "\": use el código DANE (ej. 05001)");
            }
            if (ms.size() > 1) {
                throw new IllegalArgumentException("Hay varios municipios llamados \"" + t
                        + "\": use el código DANE");
            }
            return ms.get(0);
        }
    }

    static String normalizar(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String normalizarRegimen(String r) {
        String n = normalizar(r);
        return n.startsWith("no") ? "NO_RESPONSABLE_IVA" : "RESPONSABLE_IVA";
    }

    private static String[] partir(String s) {
        if (s == null) return new String[] { null, null };
        String[] p = s.trim().split("\\s+", 2);
        // nombre1..apellido2 son columnas de 40 caracteres.
        return new String[] { corto(p[0]), p.length > 1 ? corto(p[1]) : null };
    }

    private static String corto(String s) {
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    static String limpiarCodigo(String c) {
        return c == null ? "" : c.replaceAll("[\\s.]", "").trim();
    }

    /** Documento sin puntos, espacios ni guiones; si trae DV (900123456-7) se quita. */
    static String limpiarDocumento(String d) {
        if (d == null) return "";
        String s = d.trim();
        int guion = s.lastIndexOf('-');
        if (guion > 0 && s.length() - guion <= 2) s = s.substring(0, guion);
        return s.replaceAll("[\\s.,-]", "");
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static void error(Resultado r, Integer fila, String detalle, String mensaje) {
        r.getFilas().add(new ResultadoFila(fila, ERROR, detalle, mensaje));
        r.setErrores(r.getErrores() + 1);
    }

    private static void exigirSinErrores(Resultado r) {
        if (r.getErrores() > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El archivo tiene " + r.getErrores()
                    + " error(es): corríjalos y valide de nuevo antes de importar");
        }
        if (r.getNuevos() == 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay filas nuevas para importar");
        }
    }
}
