#!/usr/bin/env python3
"""Importe le médaillon des numéros de sourate dans `core:design`, par densité.

`assets/illustrations/medallion.png` de `coran-memoire` pèse 395 100 octets pour 1254 x 1254
pixels. Il n'est utilisé qu'à **un seul endroit** — `QuranNumberMedallion`, dans
`src/ui/DesignSystem.tsx` — et il y est affiché à **44 x 44** pixels indépendants de la
densité. C'est donc 28 fois la taille affichée sur chaque côté : Android décoderait un bitmap
de 1254 x 1254, soit 6 Mo en mémoire, pour dessiner un ornement de 44 px.

Le script produit un fichier par densité, à la taille exacte que l'écran demande :

    mdpi 44   hdpi 66   xhdpi 88   xxhdpi 132   xxxhdpi 176

Ce n'est pas un simple « redimensionner ». L'image est un **trait plat** : une étoile à huit
branches dessinée en or sur fond transparent. Deux propriétés mesurées commandent le portage.

**L'encre n'a pas de dégradé.** Mesurée sur six couronnes de rayon entre 0,66 et 1,00 du
demi-côté, sa couleur vaut `rgb(166,119,43)` avec un écart-type de 2,5 — soit 1 % — dans
chacune. Le tracé ne porte donc **aucune information de couleur** : seule la forme en porte.
Le script aplatit en conséquence le RVB sur la couleur mesurée et laisse l'alpha seul décrire
le dessin. La réduction ne peut alors ni teinter ni délaver un bord, puisqu'il n'y a plus rien
à mélanger.

**La prémultiplication par l'alpha, elle, est à écarter ici** — et c'est une mesure, pas une
préférence. C'est pourtant l'opération correcte en général, et elle a été essayée : le RVB des
pixels transparents de cette image vaut n'importe quoi (90 % des pixels sont transparents, et
leurs couleurs dominantes sont `rgb(255,0,0)` et `rgb(0,0,0)`), ce qui motive de le neutraliser.
Mais ce bruit est confiné à un alpha de 1 à 3 : une fois multiplié par l'alpha il ne pèse plus
rien, tandis que **diviser** par un alpha de quelques 1/255 amplifie l'arrondi entier d'un
cran en une cinquantaine de niveaux. Mesuré sur les pixels d'alpha au moins 64, la version
prémultipliée s'écarte de l'encre de **79 en moyenne et jusqu'à 206**, là où la version
aplatie s'en écarte de **0**. C'est la version aplatie qui est retenue.

Le dépôt `coran-memoire` est ouvert en LECTURE SEULE : ce script ne fait que lire.

Usage :
    python tools/import-medallion.py --source ../work/coran-memoire
    python tools/import-medallion.py --source <...> --densites mdpi,xxxhdpi
    python tools/import-medallion.py --verifier
"""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

SOURCE = "assets/illustrations/medallion.png"
DESTINATION = Path("core/design/src/main/res")
NOM = "medallion"

#: Côté affiché, en pixels indépendants de la densité. Mesuré dans la source : le `View` qui
#: porte le médaillon fait 44 x 44, et l'`Image` le remplit exactement.
COTE_DP = 44

#: Alpha à partir duquel un pixel compte comme de l'encre. Mesuré : entre 200 et 255 la
#: couleur moyenne ne bouge plus (166,2 / 119,1 / 42,6 contre 166,2 / 119,1 / 42,7), donc le
#: seuil n'est pas sensible.
SEUIL_ENCRE = 250

#: Dossier de densité -> facteur. Les cinq densités que `minSdk 26` peut rencontrer.
DENSITES: tuple[tuple[str, float], ...] = (
    ("mdpi", 1.0),
    ("hdpi", 1.5),
    ("xhdpi", 2.0),
    ("xxhdpi", 3.0),
    ("xxxhdpi", 4.0),
)


def entete_png(chemin: Path) -> tuple[int, int, int]:
    """Largeur, hauteur et type de couleur, lus dans l'en-tête IHDR."""
    octets = chemin.read_bytes()[:33]
    if octets[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit(f"{chemin} n'est pas un PNG")
    largeur, hauteur = struct.unpack(">II", octets[16:24])
    return largeur, hauteur, octets[25]


def couleur_encre(image) -> tuple[int, int, int]:
    """Couleur de l'encre, mesurée sur les pixels les plus opaques de la source.

    Elle est **mesurée** et non écrite en dur : si le dépôt d'origine retouchait son
    illustration, le portage suivrait sans qu'on ait à s'en apercevoir.
    """
    import numpy as np

    donnees = np.asarray(image.convert("RGBA"))
    masque = donnees[:, :, 3] >= SEUIL_ENCRE
    if not masque.any():
        sys.exit(f"aucun pixel d'alpha au moins {SEUIL_ENCRE} : l'image n'a pas d'encre")
    return tuple(int(valeur) for valeur in np.rint(donnees[masque][:, :3].mean(0)))


def reduire(image, cote: int, couleur: tuple[int, int, int]):
    """Réduit au côté demandé : RVB constant, alpha réduit.

    Réduire le RVB n'aurait aucun sens puisqu'il est constant ; c'est l'alpha qui porte la
    forme. Le composer ainsi garantit que la couleur de sortie est **exactement** celle
    mesurée, sans qu'un mélange puisse la déplacer.
    """
    from PIL import Image

    alpha = image.convert("RGBA").getchannel("A").resize((cote, cote), Image.LANCZOS)
    sortie = Image.new("RGBA", (cote, cote), couleur + (255,))
    sortie.putalpha(alpha)
    return sortie


def produire(source: Path, densites: tuple[tuple[str, float], ...]):
    """Écrit les fichiers et rend (couleur, [(densité, côté, octets)])."""
    from PIL import Image

    resultats: list[tuple[str, int, int]] = []
    with Image.open(source) as ouverte:
        if ouverte.mode != "RGBA":
            sys.exit(f"{source} est en {ouverte.mode}, or un médaillon doit porter sa transparence")
        couleur = couleur_encre(ouverte)
        for densite, facteur in densites:
            cote = round(COTE_DP * facteur)
            dossier = DESTINATION / f"drawable-{densite}"
            dossier.mkdir(parents=True, exist_ok=True)
            sortie = dossier / f"{NOM}.png"
            reduire(ouverte, cote, couleur).save(sortie, format="PNG", optimize=True)
            resultats.append((densite, cote, sortie.stat().st_size))
    return couleur, resultats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default="../work/coran-memoire")
    parser.add_argument(
        "--densites",
        default=",".join(nom for nom, _ in DENSITES),
        help="densités à produire, séparées par des virgules",
    )
    parser.add_argument(
        "--verifier",
        action="store_true",
        help="ne rien écrire : comparer les fichiers du dépôt à ce que le script produirait",
    )
    args = parser.parse_args()

    demandees = tuple(nom.strip() for nom in args.densites.split(",") if nom.strip())
    inconnues = [nom for nom in demandees if nom not in dict(DENSITES)]
    if inconnues:
        sys.exit(f"densité(s) inconnue(s) : {', '.join(inconnues)}")

    chemin = Path(args.source) / SOURCE
    if not chemin.exists():
        sys.exit(f"absent : {chemin}")

    largeur, hauteur, type_couleur = entete_png(chemin)
    if type_couleur != 6:
        sys.exit(f"{chemin} est de type PNG {type_couleur}, or 6 (RGBA) est attendu")
    avant = chemin.stat().st_size

    try:
        import numpy  # noqa: F401
        from PIL import Image  # noqa: F401
    except ImportError as erreur:
        sys.exit(
            f"{erreur.name} est requis. Installez-le dans l'environnement isolé :\n"
            "  <venv>/Scripts/python.exe -m pip install Pillow numpy"
        )

    if args.verifier:
        return verifier(chemin, demandees)

    print(f"source : {chemin} — {largeur} x {hauteur}, {avant / 1024:.0f} Ko")
    couleur, resultats = produire(
        chemin, tuple((nom, facteur) for nom, facteur in DENSITES if nom in demandees)
    )
    print(f"encre  : rgb{couleur} — #{couleur[0]:02X}{couleur[1]:02X}{couleur[2]:02X}")
    print(f"{'densité':<10} {'côté':>5} {'octets':>8}")
    total = 0
    for densite, cote, octets in resultats:
        total += octets
        print(f"  {densite:<8} {cote:>5} {octets:>8}")
    print()
    print(
        f"Total : {avant / 1024:.0f} Ko -> {total / 1024:.1f} Ko "
        f"({100 * (1 - total / avant):.0f} % de moins) pour les {len(demandees)} densités"
    )
    print(f"Taille affichée : {COTE_DP} px indépendants, soit 44 à 176 px selon la densité.")
    return 0


def verifier(source: Path, densites: tuple[str, ...]) -> int:
    """Compare les fichiers du dépôt aux octets que le script produirait.

    Le contrôle porte sur les **octets**, pas sur les dimensions : un fichier de la bonne
    taille mais produit par un autre chemin — une réduction prémultipliée, par exemple — serait
    accepté par un contrôle de dimensions et refusé par celui-ci.
    """
    import io

    from PIL import Image

    ecarts = 0
    with Image.open(source) as ouverte:
        couleur = couleur_encre(ouverte)
        print(f"encre  : rgb{couleur} — #{couleur[0]:02X}{couleur[1]:02X}{couleur[2]:02X}")
        for densite, facteur in DENSITES:
            if densite not in densites:
                continue
            cote = round(COTE_DP * facteur)
            attendu = io.BytesIO()
            reduire(ouverte, cote, couleur).save(attendu, format="PNG", optimize=True)
            fichier = DESTINATION / f"drawable-{densite}" / f"{NOM}.png"
            if not fichier.exists():
                print(f"  [ABSENT]    {fichier}")
                ecarts += 1
                continue
            reel = fichier.read_bytes()
            if reel == attendu.getvalue():
                print(f"  [IDENTIQUE] drawable-{densite}/{NOM}.png  {cote} px  {len(reel)} octets")
            else:
                print(
                    f"  [DIFFERENT] drawable-{densite}/{NOM}.png  {len(reel)} octets "
                    f"au lieu de {len(attendu.getvalue())}"
                )
                ecarts += 1

    print()
    print(f"{ecarts} écart(s) sur {len(densites)} densité(s)" if ecarts else "Tout est identique.")
    return 1 if ecarts else 0


if __name__ == "__main__":
    raise SystemExit(main())
