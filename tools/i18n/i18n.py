"""Helpers for moving hard-coded German UI texts into Android string resources."""
import json, os, re
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../app/src/main/java/de/emil/shizubox/')
STORE = os.path.join(os.path.dirname(__file__), 'strings.json')

def load():
    return json.load(open(STORE)) if os.path.exists(STORE) else {}

_db = load()

def S(key, en, de, plural=False):
    """Register a string. For plurals pass dicts {'one':..,'other':..}."""
    if key in _db and _db[key] != {'en': en, 'de': de, 'plural': plural}:
        raise SystemExit(f'conflicting key {key}')
    _db[key] = {'en': en, 'de': de, 'plural': plural}

def edit(rel, pairs):
    p = ROOT + rel
    s = open(p).read()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(f'{rel}: not found: {old[:80]!r}')
        s = s.replace(old, new)
    if 'R.string.' in s or 'R.plurals.' in s or 't(' in s:
        pkg = re.search(r'^package ([\w.]+)', s, re.M).group(1)
        imports = []
        if pkg != 'de.emil.shizubox':
            if ('R.string.' in s or 'R.plurals.' in s) and 'import de.emil.shizubox.R\n' not in s:
                imports.append('import de.emil.shizubox.R')
            if re.search(r'(?<![\w.])t\(', s) and 'import de.emil.shizubox.t\n' not in s:
                imports.append('import de.emil.shizubox.t')
            if 'tp(R.plurals' in s and 'import de.emil.shizubox.tp\n' not in s:
                imports.append('import de.emil.shizubox.tp')
        if imports:
            # insert after the package line's following import block start
            m = re.search(r'^import .*$', s, re.M)
            if m:
                s = s[:m.start()] + '\n'.join(imports) + '\n' + s[m.start():]
            else:
                pl = re.search(r'^package .*$', s, re.M)
                s = s[:pl.end()] + '\n\n' + '\n'.join(imports) + '\n' + s[pl.end():]
    open(p, 'w').write(s)

def save():
    json.dump(_db, open(STORE, 'w'), ensure_ascii=False, indent=1)
    print(len(_db), 'strings')

LANGS = ('tr', 'es', 'fr')

def S5(key, en, de, tr, es, fr, plural=False):
    """Register a string with all five languages. Plurals: pass (one, other) tuples."""
    if plural:
        S(key, {'one': en[0], 'other': en[1]}, {'one': de[0], 'other': de[1]}, True)
    else:
        S(key, en, de)
    for lang, val in zip(LANGS, (tr, es, fr)):
        p = os.path.join(os.path.dirname(__file__), f'tr_{lang}.tsv')
        lines = open(p).read().splitlines()
        val = '|||'.join(val) if plural else val
        val = val.replace('\n', '\\n')
        lines = [l for l in lines if not l.startswith(key + '\t')] + [f'{key}\t{val}']
        open(p, 'w').write('\n'.join(lines) + '\n')
