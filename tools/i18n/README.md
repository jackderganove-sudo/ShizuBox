# Translations

`strings.json` is the source for English and German, `tr_<lang>.tsv` for further
languages (`key<TAB>text`, plurals as `one|||other`, `\n` for line breaks).

```sh
cd tools/i18n
python3 tsv2json.py tr es fr   # checks placeholders, reports missing keys
python3 gen.py                 # writes res/values*/strings.xml
```

New strings in a script: `from i18n import *`, then `S5(key, en, de, tr, es, fr)`
and `save()`. `edit(file, [(old, new)])` replaces literals in Kotlin sources and adds
the `R`/`t` imports. Missing translations fall back to English.
