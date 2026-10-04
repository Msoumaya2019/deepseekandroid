#!/usr/bin/env python3
"""Importe les illustrations de `coran-memoire` dans `core:design`, en JPEG.

Les cinq fonds de thème et l'illustration de lecture sont des **photographies** : PNG en
couleurs vraies, sans transparence, mal compresses. Ils pesaient 2 Mo piece, soit environ
12 Mo pour six images destinees a une bande de 130 px de haut affichee a 55 % d'opacite.

Les reencoder en JPEG de qualite 85 donne la meme image pour environ dix fois moins, sans
transparence a perdre (il n'y en avait pas). C'est une transformation de format, pas un
redimensionnement : les dimensions d'origine sont conservees, donc aucun ecran ne devient flou.

Ce qui n'est PAS converti : les images **avec transparence** (medallion.png, type RGBA). Un
JPEG les aplatirait sur un fond noir.

Le depot `coran-memoire` est ouvert en LECTURE SEULE : ce script ne fait que lire.

Usage :
    python tools/import-theme-art.py --source ../work/coran-memoire
    python tools/import-theme-art.py --source <...> --qualite 90
"""

from __future__ import annotations

import argparse
import io
import struct
import sys
from pathlib import Path

# nom de destination -> (fichier source, description)
IMAGES = {
    "theme_white": ("assets/themes/white.png", "fond du theme blanc"),
    "theme_classic": ("assets/themes/emerald.png", "fond du theme vert (fichier « emerald »)"),
    "theme_feminine": ("assets/themes/rose.png", "fond du theme rose"),
    "theme_lilac": ("assets/themes/lilac.png", "fond du theme lilas"),
    "theme_night": ("assets/themes/night.png", "fond du theme nuit"),
    "reading_art": ("assets/illustrations/reading.png", "illustration « continuer ma lecture »"),
}

DESTINATION = Path(
    "core/design/src/main/res/drawable-nodpi",
)


def entete_png(chemin: Path) -> tuple[int, int, int]:
    """Largeur, hauteur et type de couleur, lus dans l'en-tete IHDR."""
    octets = chemin.read_bytes()[:33]
    if octets[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit(f"{chemin} n'est pas un PNG")
    largeur, hauteur = struct.unpack(">II", octets[16:24])
    return largeur, hauteur, octets[25]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default="../work/coran-memoire")
    parser.add_argument("--qualite", type=int, default=85, help="qualite JPEG (defaut 85)")
    args = parser.parse_args()

    source = Path(args.source)
    destination = DESTINATION
    destination.mkdir(parents=True, exist_ok=True)

    try:
        from PIL import Image
    except ImportError:
        sys.exit(
            "Pillow est requis. Installez-le dans l'environnement isole :\n"
            "  <venv>/Scripts/python.exe -m pip install Pillow"
        )

    total_avant = 0
    total_apres = 0

    print(f"{'destination':<18} {'dimensions':>11} {'avant':>9} {'apres':>9} {'gain':>6}")
    for nom, (relatif, description) in IMAGES.items():
        chemin = source / relatif
        if not chemin.exists():
            print(f"  [ABSENT] {relatif}")
            continue

        largeur, hauteur, type_couleur = entete_png(chemin)
        if type_couleur in (4, 6):
            print(f"  [IGNORE] {relatif} : transparence presente (type {type_couleur}), garder en PNG")
            continue

        avant = chemin.stat().st_size
        sortie = destination / f"{nom}.jpg"
        with Image.open(chemin) as image:
            image = image.convert("RGB")
            image.save(sortie, format="JPEG", quality=args.qualite, optimize=True, progressive=True)
        apres = sortie.stat().st_size

        total_avant += avant
        total_apres += apres
        print(
            f"{nom:<18} {largeur:>5} x {hauteur:<4} {avant / 1024:>8.0f}K {apres / 1024:>8.0f}K "
            f"{100 * (1 - apres / avant):>5.0f}%  {description}"
        )

    print()
    print(
        f"Total : {total_avant / 1024 / 1024:.1f} Mo -> {total_apres / 1024 / 1024:.1f} Mo "
        f"({100 * (1 - total_apres / max(1, total_avant)):.0f} % de moins)"
    )
    print("Les dimensions d'origine sont conservees : aucune image n'est agrandie a l'affichage.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
