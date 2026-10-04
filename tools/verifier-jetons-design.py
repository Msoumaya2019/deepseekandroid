#!/usr/bin/env python3
"""Verifie que les jetons de design Android sont identiques a ceux de `coran-memoire`.

Le portage de l'interface repose sur une promesse : les couleurs et les tailles de la
nouvelle application sont **celles** de l'ancienne, pas des valeurs approchantes. Relire
quarante-huit codes hexadecimalux a l'oeil ne prouve rien ; ce script les extrait des deux
sources et les compare cle par cle.

Il compare :

  1. les cinq palettes            `src/ui/theme.tsx`  ->  `AppPalettes`        (Palettes.kt)
  2. les couleurs hors palette    `export const colors` ->  defauts d'`AppColors`
  3. les quatre accents           `src/theme/tokens.ts` ->  `Accents`          (Tokens.kt)
  4. les pastilles des themes     `themeOptions`        ->  `appThemeOptions`   (Palettes.kt)

Les codes sont normalises avant comparaison : React Native ecrit `#RRGGBB`, Compose ecrit
`0xFFRRGGBB`. Sans cette normalisation tout ressortirait en ecart, ce qui est arrive.

Le depot `coran-memoire` est ouvert en LECTURE SEULE : ce script ne fait que lire.

Usage :
    python tools/verifier-jetons-design.py --source ../work/coran-memoire

Code de sortie 0 si tout est identique, 1 sinon.
"""

from __future__ import annotations

import argparse
import io
import re
import sys
from pathlib import Path

# --- Emplacements cote Android ------------------------------------------------

DESIGN = Path("core/design/src/main/kotlin/com/msoumaya/deepseekandroid/core/design/theme")
PALETTES_KT = DESIGN / "Palettes.kt"
TOKENS_KT = DESIGN / "Tokens.kt"

# Les palettes du depot d'origine sont nommees en minuscules, celles de Kotlin en CamelCase.
PALETTE_NAMES = {
    "white": "White",
    "classic": "Classic",
    "feminine": "Feminine",
    "lilac": "Lilac",
    "night": "Night",
}

ACCENT_NAMES = {
    "prune": "Prune",
    "rose": "Rose",
    "green": "Green",
    "gold": "Gold",
}


def norm(value: str) -> str:
    """Ramene `#RRGGBB` et `FFRRGGBB` a la meme forme : six chiffres hexadecimaux."""
    value = value.upper().lstrip("#")
    if len(value) == 8 and value.startswith("FF"):
        value = value[2:]
    return value


def read(path: Path) -> str:
    if not path.exists():
        sys.exit(f"Fichier introuvable : {path}")
    return io.open(path, encoding="utf-8").read()


# --- Extraction cote React Native --------------------------------------------


def rn_palettes(source: str) -> dict[str, dict[str, str]]:
    """Les cinq objets de `const palettes = { ... }`."""
    found: dict[str, dict[str, str]] = {}
    for name, body in re.findall(r"^\s{2}(\w+):\{(.*?)\},?\s*$", source, re.M):
        found[name] = dict(re.findall(r"(\w+):'(#[0-9A-Fa-f]{6})'", body))
    return found


def rn_shared_colors(source: str) -> dict[str, str]:
    """Les couleurs posees une fois pour toutes : `export const colors = {...}`."""
    block = re.search(r"export const colors=\{(.*?)\};", source, re.S)
    if not block:
        sys.exit("`export const colors` introuvable dans src/ui/theme.tsx")
    return dict(re.findall(r"(\w+):'(#[0-9A-Fa-f]{6})'", block.group(1)))


def rn_accents(tokens: str) -> dict[str, dict[str, str]]:
    block = re.search(r"export const accents=\{(.*?)\} as const;", tokens, re.S)
    if not block:
        sys.exit("`export const accents` introuvable dans src/theme/tokens.ts")
    found: dict[str, dict[str, str]] = {}
    for name, body in re.findall(r"(\w+):\{(.*?)\}", block.group(1)):
        found[name] = dict(re.findall(r"(\w+):'(#[0-9A-Fa-f]{6})'", body))
    return found


def rn_theme_options(source: str) -> list[tuple[str, str, list[str]]]:
    """`themeOptions` : cle, nom affiche, pastilles."""
    start = source.index("export const themeOptions")
    block = source[start:]
    keys = re.findall(r"\{\s*key:'(\w+)'", block)
    names = re.findall(r"name:'([^']*)'", block)
    swatches = re.findall(r"swatches:\s*\[([^\]]*)\]", block)
    out = []
    for key, name, raw in zip(keys, names, swatches):
        out.append((key, name, re.findall(r"'(#[0-9A-Fa-f]{6})'", raw)))
    return out


# --- Extraction cote Kotlin ---------------------------------------------------


def kt_palettes(source: str) -> dict[str, dict[str, str]]:
    block = source[source.index("object AppPalettes {"):source.index("    fun of(")]
    found: dict[str, dict[str, str]] = {}
    for name, body in re.findall(r"val (\w+) = AppColors\((.*?)\n    \)", block, re.S):
        found[name] = dict(re.findall(r"(\w+) = Color\(0x(FF[0-9A-F]{6})\)", body))
    return found


def kt_shared_colors(source: str) -> dict[str, str]:
    return dict(re.findall(r"val (\w+): Color = Color\(0x(FF[0-9A-F]{6})\)", source))


def kt_accents(source: str) -> dict[str, dict[str, str]]:
    found: dict[str, dict[str, str]] = {}
    for name, body in re.findall(r"val (\w+) = Accent\((.*?)\n    \)", source, re.S):
        found[name] = dict(re.findall(r"(\w+) = Color\(0x(FF[0-9A-F]{6})\)", body))
    return found


def kt_theme_options(source: str) -> list[tuple[str, str, list[str]]]:
    pattern = (
        r"theme = AppTheme\.(\w+),\s*\n\s*name = \"([^\"]*)\",\s*\n"
        r"\s*description = \"[^\"]*\",\s*\n\s*swatches = listOf\((.*?)\n\s*\),"
    )
    out = []
    for key, name, raw in re.findall(pattern, source, re.S):
        out.append((key, name, re.findall(r"Color\(0x(FF[0-9A-F]{6})\)", raw)))
    return out


# --- Comparaison --------------------------------------------------------------


class Report:
    def __init__(self) -> None:
        self.failures = 0
        self.checks = 0

    def compare(
        self,
        title: str,
        left: dict[str, str],
        right: dict[str, str],
        label_left: str,
        label_right: str,
    ) -> None:
        self.checks += 1
        missing = sorted(set(left) - set(right))
        extra = sorted(set(right) - set(left))
        differs = [
            (k, left[k], right[k])
            for k in sorted(left)
            if k in right and norm(left[k]) != norm(right[k])
        ]
        if not (missing or extra or differs):
            print(f"  [OK]   {title} : {len(left)} cles identiques")
            return
        self.failures += 1
        print(f"  [ECART] {title}")
        for key in missing:
            print(f"           absente de {label_right} : {key} = {left[key]}")
        for key in extra:
            print(f"           en trop dans {label_right} : {key} = {right[key]}")
        for key, a, b in differs:
            print(f"           {key} : {label_left} {a} != {label_right} {b}")

    def compare_list(
        self,
        title: str,
        left: list[tuple[str, str, list[str]]],
        right: list[tuple[str, str, list[str]]],
    ) -> None:
        self.checks += 1
        if len(left) != len(right):
            self.failures += 1
            print(f"  [ECART] {title} : {len(left)} cote source, {len(right)} cote Android")
            return
        problems = []
        for (lk, lname, lsw), (rk, rname, rsw) in zip(left, right):
            if [norm(x) for x in lsw] != [norm(x) for x in rsw]:
                problems.append((lk, lsw, rsw))
        if not problems:
            print(f"  [OK]   {title} : {len(left)} entrees identiques")
            return
        self.failures += 1
        print(f"  [ECART] {title}")
        for key, a, b in problems:
            print(f"           {key} : source {[norm(x) for x in a]} != android {[norm(x) for x in b]}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        default="../work/coran-memoire",
        help="chemin vers la copie locale de coran-memoire (lecture seule)",
    )
    parser.add_argument("--android", default=".", help="racine du depot deepseekandroid")
    args = parser.parse_args()

    source_root = Path(args.source)
    rn_theme = read(source_root / "src" / "ui" / "theme.tsx")
    rn_tokens = read(source_root / "src" / "theme" / "tokens.ts")

    android_root = Path(args.android)
    kt_pal = read(android_root / PALETTES_KT)
    kt_tok = read(android_root / TOKENS_KT)

    report = Report()

    print("Palettes")
    rn_pal, kt_pal_map = rn_palettes(rn_theme), kt_palettes(kt_pal)
    for rn_name, kt_name in PALETTE_NAMES.items():
        if rn_name not in rn_pal:
            sys.exit(f"palette `{rn_name}` introuvable dans le source")
        report.compare(
            f"{rn_name} -> {kt_name}",
            rn_pal[rn_name],
            kt_pal_map.get(kt_name, {}),
            "source",
            "android",
        )

    print("\nCouleurs communes (hors palette)")
    report.compare(
        "quizLavender, quizPurple, review, reviewSoft, surfaceSecondary, mutedLight",
        rn_shared_colors(rn_theme),
        kt_shared_colors(kt_pal),
        "source",
        "android",
    )

    print("\nAccents")
    rn_acc, kt_acc_map = rn_accents(rn_tokens), kt_accents(kt_tok)
    for rn_name, kt_name in ACCENT_NAMES.items():
        report.compare(
            f"{rn_name} -> {kt_name}",
            rn_acc.get(rn_name, {}),
            kt_acc_map.get(kt_name, {}),
            "source",
            "android",
        )

    print("\nPastilles des themes")
    report.compare_list(
        "themeOptions -> appThemeOptions",
        rn_theme_options(rn_theme),
        kt_theme_options(kt_pal),
    )

    print()
    if report.failures:
        print(f"VERDICT : {report.failures} ecart(s) sur {report.checks} controles.")
        return 1
    print(f"VERDICT : fidelite confirmee, {report.checks} controles sans ecart.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
