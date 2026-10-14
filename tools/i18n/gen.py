import json, os, re, sys
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../app/src/main/res/')
db = json.load(open(os.path.join(os.path.dirname(__file__), 'strings.json')))
langs = {'en': 'values', 'de': 'values-de'}
extra = {}  # translations for further languages: {lang: {key: value}}
for f in os.listdir(os.path.dirname(__file__) or '.'):
    m = re.match(r'tr_(\w+)\.json$', f)
    if m:
        extra[m.group(1)] = json.load(open(os.path.join(os.path.dirname(__file__), f)))
        langs[m.group(1)] = f'values-{m.group(1)}'

def esc(s):
    s = s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    s = s.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if s.startswith('@') or s.startswith('?'): s = '\\' + s
    return s

def fmt_attr(s):
    # strings with % but without positional args must not be treated as format strings
    return ' formatted="false"' if '%' in s and not re.search(r'%(\d+\$)?[sd]', s) else ''

for lang, folder in langs.items():
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    if lang == 'en':
        out.append('    <string name="app_name" translatable="false">ShizuBox</string>')
        out.append('    <string name="installer_label">ShizuBox Installer</string>')
    for key, v in db.items():
        val = v[lang] if lang in ('en', 'de') else extra[lang].get(key)
        if val is None:
            continue  # falls back to English
        if v['plural']:
            out.append(f'    <plurals name="{key}">')
            for q, s in val.items():
                out.append(f'        <item quantity="{q}">{esc(s)}</item>')
            out.append('    </plurals>')
        else:
            out.append(f'    <string name="{key}"{fmt_attr(val)}>{esc(val)}</string>')
    if lang == 'de':
        out.append('    <string name="installer_label">ShizuBox Installer</string>')
    out.append('</resources>')
    os.makedirs(RES + folder, exist_ok=True)
    open(RES + folder + '/strings.xml', 'w').write('\n'.join(out) + '\n')
    print(folder, len(out))
