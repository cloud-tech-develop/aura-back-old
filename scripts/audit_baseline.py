#!/usr/bin/env python3
"""
Fase -1: audita el desfase entre entidades JPA y migraciones Flyway.

Responde: si Flyway corre V14..V95 sobre una BD vacia, que tablas/columnas
declaradas por las entidades NO existirian (y por tanto ddl-auto=validate falla).
"""
import re, sys, os
from pathlib import Path
from collections import defaultdict

ROOT = Path(r"D:/DOCUMENTOS CLOUD TECNOLOGY/REPOSITORIOS/aura-back-old")
JAVA = ROOT / "src/main/java"
MIG  = ROOT / "src/main/resources/db/migration"

def camel_to_snake(n):
    s = re.sub(r'(.)([A-Z][a-z]+)', r'\1_\2', n)
    s = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', s)
    return s.lower()

# ---------- 1. Entidades ----------
entities = {}   # tabla -> set(columnas)

for f in JAVA.rglob("*.java"):
    txt = f.read_text(encoding="utf-8", errors="ignore")
    m = re.search(r'@Table\s*\(\s*name\s*=\s*"([a-zA-Z0-9_]+)"', txt)
    if not m:
        continue
    table = m.group(1)
    cols = set()

    # quitar comentarios para no capturar codigo comentado
    txt_nc = re.sub(r'//.*', '', txt)
    txt_nc = re.sub(r'/\*.*?\*/', '', txt_nc, flags=re.S)

    # Partir en declaraciones de campo. Buscamos cada 'private <Tipo> <nombre>;'
    # y miramos las anotaciones que lo preceden.
    for fm in re.finditer(
        r'((?:@[A-Za-z]+(?:\s*\([^)]*\))?\s*)*)'   # anotaciones
        r'private\s+(?:static\s+|final\s+|transient\s+)*'
        r'([A-Za-z0-9_.<>,\s]+?)\s+([a-zA-Z_][A-Za-z0-9_]*)\s*[;=]',
        txt_nc):
        annos, jtype, fname = fm.group(1), fm.group(2).strip(), fm.group(3)

        if '@Transient' in annos or 'static' in fm.group(0).split('private')[0]:
            continue
        # colecciones = otro lado de la relacion, no columna
        if re.search(r'@(OneToMany|ManyToMany)', annos):
            continue
        if re.match(r'(List|Set|Collection|Map)\s*<', jtype):
            continue

        # @JoinColumn(name="x") gana
        jc = re.search(r'@JoinColumn\s*\([^)]*name\s*=\s*"([a-zA-Z0-9_]+)"', annos)
        if jc:
            cols.add(jc.group(1)); continue
        # @Column(name="x")
        cn = re.search(r'@Column\s*\([^)]*name\s*=\s*"([a-zA-Z0-9_]+)"', annos)
        if cn:
            cols.add(cn.group(1)); continue
        # @ManyToOne/@OneToOne sin JoinColumn -> campo + _id
        if re.search(r'@(ManyToOne|OneToOne)', annos):
            cols.add(camel_to_snake(fname) + "_id"); continue
        # implicito
        cols.add(camel_to_snake(fname))

    entities[table] = cols

# ---------- 2. Migraciones ----------
created_tables = set()
mig_cols = defaultdict(set)

files = sorted(MIG.glob("*.sql"), key=lambda p: int(re.match(r'V(\d+)', p.name).group(1)))
for f in files:
    sql = f.read_text(encoding="utf-8", errors="ignore")
    sql = re.sub(r'--.*', '', sql)

    # CREATE TABLE t ( ... )
    for cm in re.finditer(
        r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z0-9_]+)\s*\((.*?)\n\s*\);',
        sql, re.S | re.I):
        t, body = cm.group(1).lower(), cm.group(2)
        created_tables.add(t)
        for line in body.split("\n"):
            line = line.strip()
            if not line or line.upper().startswith(("CONSTRAINT","PRIMARY KEY","UNIQUE","FOREIGN KEY","CHECK")):
                continue
            cm2 = re.match(r'([a-zA-Z0-9_]+)\s+[A-Za-z]', line)
            if cm2:
                mig_cols[t].add(cm2.group(1).lower())

    # ALTER TABLE t ADD COLUMN ...
    for am in re.finditer(
        r'ALTER\s+TABLE\s+(?:ONLY\s+)?([a-zA-Z0-9_]+)\s+(.*?);', sql, re.S | re.I):
        t, body = am.group(1).lower(), am.group(2)
        for col in re.finditer(
            r'ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z0-9_]+)', body, re.I):
            mig_cols[t].add(col.group(1).lower())

    # RENAME
    for rm in re.finditer(r'ALTER\s+TABLE\s+([a-zA-Z0-9_]+)\s+RENAME\s+TO\s+([a-zA-Z0-9_]+)', sql, re.I):
        created_tables.add(rm.group(2).lower())

# ---------- 3. Diff ----------
tablas_faltantes = []
cols_faltantes   = {}

for t in sorted(entities):
    if t not in created_tables:
        tablas_faltantes.append(t)
        continue
    faltan = {c for c in entities[t] if c not in mig_cols[t]}
    if faltan:
        cols_faltantes[t] = sorted(faltan)

# ---------- 4. Reporte ----------
print("=" * 78)
print("FASE -1 - DESFASE ENTIDADES JPA vs MIGRACIONES FLYWAY")
print("=" * 78)
print(f"Entidades con @Table:            {len(entities)}")
print(f"Tablas creadas por migraciones:  {len(created_tables)}")
_first = re.match(r'V(\d+)', files[0].name).group(1)
_last  = re.match(r'V(\d+)', files[-1].name).group(1)
print(f"Migraciones analizadas:          {len(files)} (V{_first}..V{_last})")
print()
print(f">>> TABLAS que la entidad declara y NINGUNA migracion crea: {len(tablas_faltantes)}")
print(f">>> TABLAS con columnas faltantes:                          {len(cols_faltantes)}")
print(f">>> TOTAL columnas faltantes:                               {sum(len(v) for v in cols_faltantes.values())}")
print()

print("-" * 78)
print("A. TABLAS COMPLETAS FALTANTES (la migracion no las crea)")
print("-" * 78)
for t in tablas_faltantes:
    print(f"  {t:<40} ({len(entities[t])} columnas)")

print()
print("-" * 78)
print("B. COLUMNAS FALTANTES EN TABLAS EXISTENTES")
print("-" * 78)
for t in sorted(cols_faltantes, key=lambda x: -len(cols_faltantes[x])):
    print(f"\n  {t}  ({len(cols_faltantes[t])} faltantes)")
    for c in cols_faltantes[t]:
        print(f"      - {c}")
