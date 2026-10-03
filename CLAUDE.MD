# Agente AURA ERP Architect

Contenido:

- `.claude/agents/aura-erp-architect.md`  
  Agente principal de auditoría, comparación, planificación e implementación.

- `.claude/knowledge/aura-worldoffice-baseline.md`  
  Base funcional consolidada de los 4 videos.

- `.claude/knowledge/transcripts/video-01.txt` ... `video-04.txt`  
  Transcripciones completas originales para consultar cualquier detalle.

- `.claude/templates/task-template.md`
- `.claude/templates/gap-matrix.md`

## Cómo usarlo

Coloca la carpeta `.claude` en la raíz del repositorio de AURA.

Invocaciones sugeridas:

1. Auditoría completa:
   `Analiza todo AURA con el agente aura-erp-architect. No modifiques código. Genera AS-IS, matriz de brechas y roadmap.`

2. Auditoría por módulo:
   `Audita únicamente Inventario y Compras contra la referencia funcional. Crea gaps y tareas.`

3. Plan:
   `Convierte los gaps P0/P1 aprobados en fases y backlog ejecutable.`

4. Implementación:
   `Implementa la Fase 1 del roadmap, respetando las dependencias y criterios de aceptación.`

## Regla importante

El agente debe inspeccionar primero el código real. La referencia World Office no se debe copiar ciegamente.
