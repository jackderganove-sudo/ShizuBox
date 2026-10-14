import json, re, sys
db = json.load(open('strings.json'))
for lang in sys.argv[1:]:
    out = {}
    for line in open(f'tr_{lang}.tsv'):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'): continue
        k, v = line.split('\t', 1); v = v.replace('\\n', '\n')
        if k not in db: print(lang, 'unknown key', k); continue
        if db[k]['plural']:
            one, other = v.split('|||')
            v = {'one': one, 'other': other}
            en = db[k]['en']['other']; chk = other
        else:
            en = db[k]['en']; chk = v
        ph = lambda s: sorted(re.findall(r'%(?:\d+\$)?[sd%]', s))
        if ph(en) != ph(chk): print(lang, 'placeholder mismatch', k, ph(en), ph(chk))
        out[k] = v
    missing = [k for k in db if k not in out]
    if missing: print(lang, 'missing', len(missing), missing[:20])
    json.dump(out, open(f'tr_{lang}.json', 'w'), ensure_ascii=False, indent=0)
    print(lang, len(out))
