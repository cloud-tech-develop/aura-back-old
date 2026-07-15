# ADR-003 — Evento único de contabilización con listener AFTER_COMMIT

**Estado:** aceptada (2026-07-09) · **Detalle:** `docs/ARQUITECTURA_CONTABILIDAD.md` §5

## Contexto
7 pares evento/listener casi idénticos (`VentaContabilizableEvent`,
`CompraContabilizableEvent`, …) que solo difieren en el nombre y el switch del listener.

## Decisión
- Un solo `DocumentoContabilizableEvent(tipoOrigen, origenId, empresaId, usuarioId)`
  y un solo `ContabilizacionListener` (`@TransactionalEventListener(AFTER_COMMIT)`)
  que despacha al use case vía registry.
- Publicar SOLO al final del método de negocio, con la transacción aún abierta.
- El fallo del posting va a `PostingLog` + ErrorLog y JAMÁS propaga: la venta no se
  cae porque la contabilidad falle.
- Los eventos legacy se marcan `@Deprecated` cuando su origen migra y se borran en E11.

## Consecuencias
- El publisher de venta ya usa el evento único (E1); el resto migra por etapas.
- Reproceso: reejecutar el use case es seguro (idempotente por tipoOrigen+origenId+empresa).
