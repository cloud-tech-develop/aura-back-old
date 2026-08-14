# Plan de seguridad — Aura POS

> Auditoría de código del backend (`aura-back-old`) y frontend (`aura-frontend`)
> realizada el 2026-08-12 sobre la rama `fix/camilo/correcion-errores`.
>
> ⚠ **Este documento es un mapa de ataque.** No lo publiques, no lo subas a un
> tablero público ni lo compartas fuera del equipo hasta cerrar la Fase 0.

**Estado:** Fase 0 implementada el 2026-08-12 salvo CORS (aplazado por decisión)
y la rotación del `JWT_SECRET` (requiere acceso al despliegue). Fases 1–3
pendientes. El detalle de lo hecho está en §4; lo que falta hacer a mano, en §5.

**Índice**

1. Resumen ejecutivo
2. Hallazgos (3 críticos, 5 altos, 6 medios)
3. Lo que ya está bien — no romper
4. Plan de ejecución por fases
5. Pendientes operativos (variables de entorno, rotación, decisiones)
6. Checklist de verificación

---

## 1. Resumen ejecutivo

El sistema tiene una base sólida en lo que suele fallar primero: **no encontré
inyección SQL** y **no encontré fugas entre empresas (IDOR)**. Eso es mérito del
diseño actual y hay que conservarlo.

El riesgo real está en otro lado:

1. **Las llaves criptográficas están escritas en el repositorio.** Cualquiera con
   acceso al código —hoy o en el historial de git— puede firmar un token válido y
   entrar como administrador de cualquier empresa. Esto es un compromiso total.
2. **La API no valida roles.** De 642 endpoints, solo los de `/api/platform/**`
   verifican quién llama. Un cajero con su token legítimo puede liquidar nómina o
   borrar contabilidad con `curl`. El menú del frontend oculta las opciones, pero
   eso es cosmético.
3. **No hay límite de peticiones.** El login acepta intentos ilimitados.

| Severidad | Hallazgos |
|---|---|
| 🔴 Crítico | 3 |
| 🟠 Alto | 5 |
| 🟡 Medio | 6 |
| 🟢 Ya correcto | 6 |

---

## 2. Hallazgos

### 🔴 C-01 · Llaves criptográficas versionadas en git

**Evidencia:** `src/main/resources/application.properties:52-54`

```properties
app.jwt.secret=WnBtQ0xXaE9WSlR6eENaUFRvU3V1ZGk0V0w2...
app.aes.key=b7e230b35e5b379f03d51f7453f4ae3b3b71a0a0...
```

El archivo está **rastreado por git** (`git ls-files` lo confirma). Las demás
credenciales sí usan variables de entorno (`${DB_PASSWORD}`, `${R2_SECRET_ACCESS_KEY}`),
pero estas dos quedaron en texto plano.

**Impacto:** con `app.jwt.secret` se forja un JWT con cualquier `empresaId`, `rol`
y `usuarioId` (ver `JwtTokenProvider:32-49`). No hace falta contraseña: el atacante
se fabrica un token de `SUPER_ADMIN` de la empresa que quiera. Y como el secreto
está en el historial, **borrarlo del archivo no basta**.

**Remediación:**
1. Mover ambas a variables de entorno: `app.jwt.secret=${JWT_SECRET}` y `app.aes.key=${AES_KEY}`.
2. **Rotar las dos llaves.** Generar nuevas (`openssl rand -base64 64`). Rotar el
   JWT invalida todas las sesiones activas: los usuarios vuelven a iniciar sesión.
3. Antes de rotar el AES: identificar qué cifra (`AESencryptUtil`) y re-cifrar esos
   datos con la llave nueva, o quedarán ilegibles.
4. Purgar el historial con `git filter-repo` o BFG, o —si el repo es privado y el
   acceso es controlado— asumir las llaves viejas como quemadas y no reutilizarlas jamás.
5. Revisar quién tuvo acceso al repositorio.

---

### 🔴 C-02 · La API no verifica roles

**Evidencia:** `SecurityConfig:40-50`. La única regla de fondo es
`.anyRequest().authenticated()`. Hay 642 endpoints (`@GetMapping`/`@PostMapping`/…)
y solo dos reglas de autorización, ambas para `/api/platform/**`.

**Impacto:** cualquier usuario autenticado —cajero, vendedor, auxiliar— puede
invocar cualquier endpoint del sistema con su propio token: liquidar nómina,
crear asientos contables, anular ventas, tocar tesorería, borrar productos. El
control de roles vive **solo en el frontend** (`sidebar.config.ts` y `rolGuard`),
que se salta con cualquier cliente HTTP.

**Remediación:** ver Fase 1. La estrategia es habilitar seguridad de método y
anotar por controlador, empezando por los de mayor impacto económico.

---

### 🔴 C-03 · `@PreAuthorize` está inerte

**Evidencia:** hay 6 anotaciones `@PreAuthorize` (`AuthController:47`,
`PlatformAdminController:37`, `ModuloController:31`, …) pero **no existe
`@EnableMethodSecurity` en ninguna clase de configuración**. En Spring Boot 3 la
seguridad de método viene desactivada por defecto: esas anotaciones no hacen nada.

Además usan `hasRole('PLATFORM_ADMIN')`, pero las authorities se construyen sin el
prefijo `ROLE_` (`CustomUserDetailsService:41-42`: `new SimpleGrantedAuthority(usuario.getRol())`).
Al activar la seguridad de método **tal cual está**, esas seis anotaciones
empezarían a rechazar a los usuarios legítimos con 403.

**Impacto hoy:** ninguno directo — esos controladores están bajo `/api/platform/**`,
protegido por regla de URL. El problema es la **falsa sensación de seguridad**: el
patrón está copiado en el código y no protege. Si mañana alguien anota un
controlador nuevo fuera de `/api/platform`, quedará abierto sin que nadie lo note.

**Remediación:** activar `@EnableMethodSecurity` **y** cambiar todos los
`hasRole('X')` por `hasAuthority('X')` en el mismo commit.

---

### 🟠 A-01 · Sin límite de peticiones (rate limiting)

**Evidencia:** no hay bucket4j, ni `RateLimiter` de Resilience4j, ni filtro propio.
Los circuit breakers configurados protegen las llamadas *salientes* a Factus, no
las *entrantes*.

**Impacto:**
- `POST /api/auth/login` acepta intentos ilimitados → fuerza bruta de contraseñas.
- `POST /api/auth/forgot-password` permite inundar de correos a un usuario.
- Los reportes pesados (`/api/reportes/*/excel`, `/reportes/avanzado/*`) permiten
  tumbar el servidor con pocas peticiones concurrentes: son consultas caras y
  construyen el archivo entero en memoria.

---

### 🟠 A-02 · Sin bloqueo de cuenta ni registro de intentos fallidos

No existe conteo de fallos, bloqueo temporal ni alerta. Combinado con A-01, un
atacante prueba contraseñas indefinidamente y en silencio.

---

### 🟠 A-03 · CORS abierto a cualquier origen

**Evidencia:** `WebConfig:13-17`

```java
registry.addMapping("/**")
        .allowedOriginPatterns("*")
        .allowedHeaders("*")
```

Cualquier sitio web puede llamar a la API desde el navegador de un usuario.
No hay `allowCredentials(true)`, así que no es robo de sesión directo, pero
elimina el aislamiento por origen y amplifica cualquier XSS o token filtrado.

**Remediación:** restringir a los dominios reales (`app.frontend.url` ya existe
como propiedad).

---

### 🟠 A-04 · JWT de 3 días, sin refresh ni revocación

**Evidencia:** `app.jwt.expiration-milliseconds=259200000` (72 h). No hay refresh
token ni lista de revocación.

**Impacto:** un token robado sirve 3 días. Un empleado despedido conserva acceso
hasta que expire — cambiarle la contraseña **no** invalida su token.

---

### 🟠 A-05 · Swagger público en producción

**Evidencia:** `SecurityConfig:48` — `/v3/api-docs/**`, `/swagger-ui/**` con `permitAll()`.
Entrega a cualquiera el catálogo completo de 642 endpoints con sus DTOs.

**Remediación:** exponerlo solo con perfil `dev`, o exigir autenticación.

---

### 🟡 Medios

| ID | Hallazgo | Evidencia | Riesgo |
|---|---|---|---|
| M-01 | `spring.jpa.show-sql=true` en producción | `application.properties:27` | Los logs registran cada consulta con sus datos (clientes, montos, documentos) |
| M-02 | Se devuelve `ex.getMessage()` al cliente y se persiste el stack trace | `GlobalExceptionHandler:35-38` | Filtra estructura interna, nombres de tablas y rutas |
| M-03 | Sin Content-Security-Policy en el frontend | — | Un XSS puede exfiltrar el token; Spring sí aplica por defecto X-Frame-Options y X-Content-Type-Options |
| M-04 | Token de sesión en IndexedDB, accesible por JavaScript | `auth.service.ts` + `index-db.service.ts` | Cualquier XSS roba la sesión. Mitigar con CSP y evitando `innerHTML` |
| M-05 | Sin auditoría de acciones sensibles | — | No hay forma de saber quién borró ventas, cambió precios o liquidó nómina |
| M-06 | Sin escaneo de dependencias | `pom.xml`, `package.json` | Las CVE nuevas de las librerías pasan desapercibidas |

---

## 3. Lo que ya está bien (no romper)

- ✅ **Sin inyección SQL.** Las 4 consultas nativas usan parámetros nombrados;
  `FacturaQueryRepository` y demás usan `NamedParameterJdbcTemplate`.
- ✅ **`ORDER BY` dinámico blindado.** `CuentaCobrarQueryRepository:30-45` y su
  gemelo de CxP mapean el campo con un `switch` de lista blanca y validan que la
  dirección sea `ASC`/`DESC`. Este es el patrón correcto: **cualquier repositorio
  nuevo con ordenamiento dinámico debe copiarlo.**
- ✅ **Sin IDOR entre empresas.** Cero endpoints reciben `empresaId` por parámetro
  o path: todos lo toman del token vía `SecurityUtils.getEmpresaId()`. Es la
  defensa más valiosa que tiene el sistema hoy — **nunca aceptar `empresaId` del
  cliente.**
- ✅ **BCrypt** para contraseñas (`SecurityConfig:79`).
- ✅ **Tokens de reset con `UUID.randomUUID()`**, que en Java usa `SecureRandom`:
  son impredecibles. Además tienen expiración y marca de un solo uso
  (`AuthController:62`).
- ✅ **Spring Boot 3.5.10**, versión reciente y con soporte.

---

## 4. Plan de ejecución

### Fase 0 — Contención (24–48 h)

> Estado al 2026-08-12. CORS (0.4) queda **aplazado por decisión del equipo**;
> el resto está implementado salvo la rotación en producción.

| # | Acción | Archivo | Estado |
|---|---|---|---|
| 0.1 | Sacar `app.jwt.secret` y `app.aes.key` a variables de entorno | `application.properties` | ✅ hecho |
| 0.2 | Generar y desplegar llaves nuevas (invalida sesiones) | infra | ⏳ **pendiente — requiere acceso al despliegue** |
| 0.3 | Re-cifrar lo que dependa de la llave AES anterior | `AESencryptUtil` | ✅ no aplica: la clase no tiene un solo uso en el código |
| 0.4 | Cerrar CORS a los dominios reales | `WebConfig` | ⏸️ aplazado por decisión |
| 0.5 | Rate limit en `/api/auth/**` | `RateLimitFilter` | ✅ hecho, con 5 pruebas |
| 0.6 | `spring.jpa.show-sql=false` en producción | `application.properties` | ✅ hecho (`JPA_SHOW_SQL`) |
| 0.7 | Swagger solo en desarrollo | `SecurityConfig` | ✅ hecho (`SWAGGER_ENABLED`) |

#### Qué se implementó (detalle)

**0.1 · Llaves fuera del repositorio** — `application.properties`

```properties
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-milliseconds=${JWT_EXPIRATION_MS:259200000}
app.aes.key=${AES_KEY}
```

Sin valor por defecto **a propósito**: si la variable falta, la aplicación no
arranca. Un secreto con valor por defecto termina llegando a producción.

Ambas variables quedaron en el `.env` local, que ya estaba en `.gitignore`
(`.gitignore:22`) y no está rastreado por git.

**0.3 · La llave AES no cifraba nada.** `AESencryptUtil` **no tiene un solo uso
en el código** (`grep -rn "AESencrypt" src/main/java` solo devuelve su propia
definición). Además la llave anterior era hexadecimal y la clase hace
`Base64.getDecoder().decode()` (`AESencryptUtil:30`), así que habría producido
una llave de tamaño inválido en cuanto alguien intentara usarla. Se reemplazó por
una base64 válida y **no hay nada que re-cifrar**.

**0.5 · Rate limit** — `security/RateLimitFilter.java`

Se implementó **sin dependencias nuevas** (no se agregó `bucket4j`): ventana fija
por IP sobre `/api/auth/**`, 5 intentos por minuto, respuesta 429 con cabecera
`Retry-After`. Todo configurable por properties.

Dos decisiones de implementación que conviene no deshacer:

1. **Purga del mapa.** Un `ConcurrentHashMap` sin limpieza crece con cada IP
   distinta: es una fuga de memoria que un atacante puede provocar a propósito
   rotando IPs. El filtro barre las ventanas vencidas cada ciclo y tiene un techo
   de 10 000 entradas.
2. **Registro automático desactivado.** Spring Boot registra en la cadena de
   servlets **cualquier** bean de tipo `Filter`. Sin el `FilterRegistrationBean`
   con `setEnabled(false)` de `SecurityConfig`, el filtro correría dos veces por
   petición y consumiría el doble de intentos.

> **Nota sobre `JwtAuthenticationFilter`:** tiene ese mismo doble registro hoy
> (es `@Component` y además se añade a la cadena). Ahí es inofensivo porque solo
> vuelve a fijar la autenticación, pero conviene corregirlo cuando se toque.

Límites conocidos, a resolver en la fase 2:

- **Es por instancia.** Con varias réplicas del backend, el límite efectivo se
  multiplica por el número de réplicas. Para un límite real y compartido hay que
  mover el contador a Redis.
- **Ventana fija, no deslizante.** En el borde entre dos ventanas caben hasta
  2× el máximo. Frena fuerza bruta; no es un control antifraude.

Pruebas: `src/test/java/.../security/RateLimitFilterTest.java`, 5 casos
(bloqueo al superar el máximo, contador independiente por IP, no afecta al resto
de la API, distingue clientes tras el mismo proxy, se puede desactivar).
`Tests run: 5, Failures: 0`.

**0.6 / 0.7** — `spring.jpa.show-sql=${JPA_SHOW_SQL:false}` y Swagger tras
`app.swagger.enabled` (`SWAGGER_ENABLED`), apagado por defecto.

#### 0.4 · CORS — aplazado por decisión del equipo

Sigue como está (`WebConfig:13-17`, `allowedOriginPatterns("*")`). Cuando se
retome, el reemplazo es:

```java
@Value("${app.frontend.url}")
private String frontendUrl;

@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOrigins(frontendUrl.split(","))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type")
            .maxAge(3600);
}
```

**Riesgo que se acepta mientras tanto:** cualquier sitio web puede llamar a la API
desde el navegador de un usuario. No hay `allowCredentials(true)`, así que no es
robo de sesión directo, pero se pierde el aislamiento por origen y se amplifica
cualquier XSS o token filtrado.

---

### Fase 1 — Autorización (semanas 1–2)

Es el trabajo más grande y el que cierra C-02. **No intentar los 642 endpoints de
una vez**: ordenar por impacto económico.

**1.1** Activar seguridad de método y corregir el prefijo, en un solo commit:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // ← sin esto, @PreAuthorize se ignora
public class SecurityConfig { ... }
```

Y cambiar las 6 anotaciones existentes de `hasRole('PLATFORM_ADMIN')` a
`hasAuthority('PLATFORM_ADMIN')`.

**1.2** Anotar por controlador, en este orden:

| Prioridad | Controladores | Rol mínimo sugerido |
|---|---|---|
| 1 | Nómina, prestaciones, PILA, nómina electrónica | `ADMIN` |
| 2 | Contabilidad, comprobantes, cierre, exógena | `ADMIN` |
| 3 | Tesorería, cuentas por pagar/cobrar, obligaciones | `ADMIN` |
| 4 | Usuarios, empresa, configuración | `SUPER_ADMIN` |
| 5 | Anulación de ventas, devoluciones, cierre de caja | `ADMIN` (ya hay `app.caja.roles-cierre`) |
| 6 | Productos/inventario: lectura para todos, escritura y borrado para `ADMIN` | mixto |

**1.3** Alternativa más rápida y más segura como red de contención: reglas por URL
en `SecurityConfig`, que aplican aunque nadie anote el controlador nuevo.

```java
.requestMatchers("/api/nomina/**", "/api/prestaciones/**", "/api/pila/**")
    .hasAnyAuthority("ADMIN", "SUPER_ADMIN")
.requestMatchers("/api/contabilidad/**", "/api/asientos/**")
    .hasAnyAuthority("ADMIN", "SUPER_ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/**")
    .hasAnyAuthority("ADMIN", "SUPER_ADMIN")
```

> Recomiendo hacer 1.3 **primero** (una tarde) y luego 1.2 con calma. Así el hueco
> queda tapado desde el día uno.

**1.4** Bloqueo de cuenta: columnas `intentos_fallidos` y `bloqueado_hasta` en
`usuario`; 5 fallos → 15 minutos de bloqueo; reset al iniciar sesión con éxito.

---

### Fase 2 — Endurecimiento (mes 1)

- **2.1** Access token de 15–30 min + refresh token de 7 días, guardado en la base
  y revocable. Cerrar sesión y cambiar contraseña revocan el refresh.
- **2.2** Rate limiting general: 100 req/min por usuario autenticado, más estricto
  en reportes y exportaciones.
- **2.3** Auditoría: tabla `auditoria_acceso` con usuario, empresa, endpoint, IP y
  fecha para operaciones sensibles (borrados, anulaciones, nómina, contabilidad).
  Ya existe `contabilidad_posting_log` como precedente del patrón.
- **2.4** CSP en el frontend y revisión de todo `innerHTML`/`bypassSecurityTrust*`.
- **2.5** Dejar de devolver `ex.getMessage()` al cliente: mensaje genérico + un id
  de correlación que sí se registre completo en el log del servidor.

### Fase 3 — Continuo

- OWASP Dependency-Check y `npm audit` en el pipeline, fallando el build en severidad alta.
- Rotación de llaves cada 6 meses, documentada.
- Revisión de accesos al repositorio y al servidor cada trimestre.
- Pentest externo una vez cerradas las fases 0 a 2.

---

## 5. Pendientes operativos (no son código)

### 5.1 Variables de entorno

Deben existir en **todos** los entornos antes de desplegar. Las tres primeras son
nuevas: sin ellas la aplicación no arranca.

| Variable | Obligatoria | Valor | Notas |
|---|---|---|---|
| `JWT_SECRET` | **sí** | base64 | `openssl rand -base64 64`. Rotarlo cierra todas las sesiones |
| `AES_KEY` | **sí** | base64 | `openssl rand -base64 32`. Nada depende de ella hoy |
| `JWT_EXPIRATION_MS` | no | 259200000 | 3 días. Bajará a 15–30 min en la fase 2 |
| `JPA_SHOW_SQL` | no | `false` | `true` solo para depurar en local |
| `SWAGGER_ENABLED` | no | `false` | `true` solo en desarrollo |
| `RATE_LIMIT_ENABLED` | no | `true` | |
| `RATE_LIMIT_MAX` | no | `5` | Intentos por ventana |
| `RATE_LIMIT_WINDOW` | no | `60` | Segundos |
| `RATE_LIMIT_TRUST_PROXY` | no | `true` | **Ver 5.3** |

### 5.2 Rotación del `JWT_SECRET` — pendiente

El valor que está hoy en el `.env` es **el mismo que estuvo versionado en git**.
Se dejó así para no cerrar las sesiones a mitad de jornada, pero **sigue
comprometido**: cualquiera con acceso al historial del repositorio puede firmar
un token de `SUPER_ADMIN` de cualquier empresa.

Pasos, en una ventana de bajo tráfico:

1. `openssl rand -base64 64`
2. Actualizar `JWT_SECRET` en el `.env` local y en el entorno de despliegue.
3. Reiniciar el backend. **Todos los usuarios deben volver a iniciar sesión** —
   avisarles antes.
4. Decidir sobre el historial de git: purgarlo con `git filter-repo`/BFG, o
   asumir la llave vieja como quemada y no reutilizarla jamás.
5. Revisar quién ha tenido acceso al repositorio.

### 5.3 Decisión pendiente: `RATE_LIMIT_TRUST_PROXY`

Quedó en `true`, que lee la IP real de `X-Forwarded-For`.

- **Si la app está detrás de nginx / Cloudflare / un balanceador:** `true` es lo
  correcto. Déjalo así.
- **Si la app está expuesta directamente a internet:** ponlo en `false`. Con
  `true` y sin proxy, un atacante evade el límite falsificando la cabecera.

Se eligió `true` por defecto porque el error contrario es peor: sin proxy real,
todos los usuarios compartirían un único contador de 5 intentos por minuto y se
bloquearían entre ellos.

### 5.4 Verificación en runtime — pendiente

Los cambios de la fase 0 están compilados y con pruebas unitarias, pero **la
aplicación no se levantó** (requiere base de datos). Antes de dar la fase por
cerrada, arrancar el backend y correr el checklist de la sección 6.

---

## 6. Checklist de verificación

Después de cada fase, comprobar con `curl` —no desde la interfaz, que es la que
miente—:

```bash
# C-02: un token de CAJERO no debe poder liquidar nómina
curl -H "Authorization: Bearer $TOKEN_CAJERO" \
     -X POST https://api.tudominio.com/api/nomina/liquidar   # → 403

# A-01: el sexto intento de login en un minuto debe rebotar
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST .../api/auth/login \
       -H 'Content-Type: application/json' \
       -d '{"username":"x","password":"y"}'
done                                                          # → 200×5, luego 429

# A-05: Swagger cerrado en producción
curl -s -o /dev/null -w "%{http_code}\n" .../v3/api-docs      # → 401 o 404

# A-03: CORS — APLAZADO. Hoy este comando devuelve la cabecera; cuando se
# retome 0.4 debe dejar de devolverla.
curl -I -H "Origin: https://sitio-malicioso.com" .../api/productos
                                    # → sin Access-Control-Allow-Origin
```

Y en el arranque, verificar que el fallo cerrado funciona:

```bash
# Sin JWT_SECRET la aplicación NO debe arrancar
unset JWT_SECRET && ./mvnw spring-boot:run
# → Could not resolve placeholder 'JWT_SECRET'
```
