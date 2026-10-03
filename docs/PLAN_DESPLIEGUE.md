# Plan de despliegue por domingos

> Creado 2026-10-01. Se sube **un lote por domingo**. Cada lote = back + front +
> migraciones Laravel + SQL de menú que van juntos. Nada se sube sin pasar su
> prueba en local sobre una copia de producción.

## Punto de partida (verificado en git el 2026-10-01)

| Repo | En `origin/main` (último merge: dom 27-sep) | Pendiente |
|---|---|---|
| Back `aura-back-old` | hasta **V182** | commits `39e5f8a`, `1934e1c`, `f0663ab` + D0/D1 sin commit (V189) |
| Front `aura-frontend` | — | commit `ad0ae31` (F2–F4) + D0/D1 sin commit |
| Laravel `aura-pos-migracion-old` | hasta **000182** | 000183–000189 **sin commit** (untracked) |

**Supuesto a confirmar antes del primer domingo:** producción tiene corrido
hasta 000182. Verificar en prod (solo lectura):
```sql
SELECT migration FROM migrations ORDER BY id DESC LIMIT 5;
```
Si falta alguna ≤ 182, se corre primero, sola, antes del Lote 1.

## Por qué se corta así

- Los commits del back son **lineales**: cada lote es "subir hasta tal commit".
  Los dos cortes (`1934e1c` y `f0663ab`) **compilan solos** (verificado en un
  worktree aparte el 2026-10-01).
- Con `ddl-auto=validate` el back **no arranca** si falta una columna: el código
  de un lote va siempre con **todas** sus migraciones, ni una más ni una menos.
- Las migraciones Laravel corren **todo lo pendiente** con `php artisan migrate`:
  el lote se controla **commiteando solo los archivos de ese lote**.
- `f0663ab` mezcla V184–V188 (costo promedio, catálogo, activos, fusión de
  terceros, recargo): partirlo costaría más que probarlo bien. Va como un lote.

## Lotes

### Lote 1 · dom 4-oct · Seguridad de roles + bodega principal (chico)
| | |
|---|---|
| Back | hasta `1934e1c` (incluye `39e5f8a`) |
| Front | nada (todo es del back) |
| Laravel | `000183_bodega_principal_faltante` |
| Menú | — |
| Riesgo | Bajo. Corrige la escalada a PLATFORM_ADMIN (P0 #1 de la auditoría). |
| Prueba | PLAN_PRUEBAS_F1_F4 §1A (1.1–1.4) + toda sucursal tiene bodega principal. |

### Lote 2 · dom 11-oct · Productos, costo promedio, activos, contador (grande)
| | |
|---|---|
| Back | hasta `f0663ab` |
| Front | `ad0ae31` (rama `feat/camilo/nomina-pila`) |
| Laravel | `000184` a `000188` |
| Menú | `docs/sql/menu_submodulos_f2_f4.sql` |
| Riesgo | **Alto**: cambia escala del costo (6 decimales), catálogo, activos y fusión de terceros sobre datos reales. |
| Prueba | **PLAN_PRUEBAS_F1_F4 completo**, sobre una copia de producción. Recargo de forma de pago (V188) incluido. |

### Lote 3 · dom 18-oct · Cadena documental D0 + D1
| | |
|---|---|
| Back | commit nuevo de D0/D1 (hoy sin commit) |
| Front | commit nuevo de D0/D1 (hoy sin commit) |
| Laravel | `000189_documento_relacion` |
| Menú | — |
| Riesgo | Medio. **Back, front y V189 van juntos**: sin V189 se rompe el detalle de cotizaciones. |
| Prueba | Escenario de D1 (cotización 10 → venta 6 → PARCIAL → venta 4 → CONVERTIDA → anular → PARCIAL). |

### Lotes siguientes (por construir)
- Plan de seguridad: llaves JWT/AES fuera de git + rotación (domingo = nadie trabajando, rotar cierra todas las sesiones).
- D2 en adelante de la cadena documental, **un lote por fase**.

## Rutina de cada lote

**Entre semana (cuando el lote está listo)**
1. Restaurar en local una copia reciente de producción (no la base de pruebas):
   la base real tiene diferencias que la local no (ej. stock en `(38,2)`).
2. Correr las migraciones del lote sobre esa copia y arrancar el back: si
   `ddl-auto=validate` falla, el lote no está listo.
3. Pasar la prueba del lote. Anotar ✅/❌.
4. Dejar el PR del lote abierto contra `main` (back y front) y la rama de Laravel
   con **solo** los archivos del lote.

**Domingo**
1. Backup de producción: `pg_dump` de `aura-db` y guardarlo con la fecha.
2. Etiquetar lo que está en prod antes de tocar: `git tag prod-AAAA-MM-DD` (back y front).
3. Merge del PR del back → correr migraciones Laravel del lote contra prod → desplegar back.
4. Merge y despliegue del front.
5. Correr el SQL de menú del lote, si tiene.
6. Prueba de humo en prod: login, una venta, abrir las pantallas del lote.
7. Actualizar la tabla "Registro" de abajo.

**Si algo sale mal**
- Código: volver a desplegar la etiqueta `prod-…` anterior.
- Base: las migraciones con datos (escala de costo, fusión de terceros) **no se
  deshacen con `down()`**: se restaura el backup del paso 1. Por eso el backup no
  es opcional.

## Cuidados

- El repo `aura-post` (el del front) ve `aura-pos-migracion-old/` como carpeta sin
  seguimiento: **no hacer `git add .` en la raíz**, o se mete el repo de Laravel dentro del front.
- D0/D1 hoy están sin commit encima de `f0663ab`: commitearlos como un commit
  propio para que el Lote 3 se pueda cortar limpio.
- No juntar dos lotes un mismo domingo para "adelantar": si falla, no se sabe cuál fue.

## Registro

| Domingo | Lote | Migraciones | Resultado | Notas |
|---|---|---|---|---|
| 27-sep | (previo) | ≤ 000182 | Subido | Último merge a main |
| 4-oct | 1 | 000183 | | |
| 11-oct | 2 | 000184–000188 | | |
| 18-oct | 3 | 000189 | | |
