"""Verifie que les ressources tierces embarquees portent leurs conditions, et que la condition
« aucune publicite » tient.

Pourquoi ce script existe
-------------------------

Le paquet embarque **48,85 Mo de polices QCF v4** qui ne sont pas les notres. Elles viennent d'une
IPA tierce dechiffree, et les conditions qui s'y attachent — « aucune publicite distrayante »,
« respecter les conditions amont » — s'imposent a qui les redistribue. Or ces conditions vivaient
jusqu'ici **dans le depot d'origine** : un renvoi vers un autre depot ne suit pas les fichiers, et
il ne survit pas a sa disparition.

Un `NOTICE.md` qui **dit** les conditions ne suffit pas non plus, pour la meme raison qu'un
manifeste d'import qui ne serait pas verifie : un fichier de conditions se perime en silence. Ce
script est ce qui l'empeche, sur quatre points qui peuvent chacun devenir faux sans que rien ne
le signale :

  A. **Couverture.** Une famille de ressources entrees dans le paquet sans etre nommee dans le
     `NOTICE.md` — le cas le plus probable, puisqu'un import ne pense pas a la documentation.
  B. **Provenance.** `provenance.json` cesse de couvrir les polices livrees : une page de plus,
     une page renommee, et l'empreinte qui devait repondre de l'origine ne repond plus de rien.
  C. **Couleur.** Les 604 polices de page cessent de porter `COLR`/`CPAL`. C'est l'invariant qui
     fonde le choix de livrer ces ressources telles quelles : sans lui, l'alternative « une seule
     police » redeviendrait equivalente, et la justification ecrite dans le NOTICE serait fausse.
  D. **Publicite.** Une dependance publicitaire apparait. La condition 1 des termes QCF/QUL
     l'interdit, et les dependances declarees sont le seul chemin par lequel elle peut entrer.

Chaque famille est exigee **par element**, et le compte rendu dit combien d'elements ont ete
examines : une section qui n'exigerait qu'une fois pour 604 fichiers serait verte pour la mauvaise
raison, et un simple « OK » ne le distinguerait pas.

Ce que le script ne fait pas : il ne **choisit** aucune licence, et ne dit pas sous quelle licence
le code de ce depot est publie. Il constate que ce qui est embarque est nomme, et que ce qui est
promis est tenu.

Usage
-----

    python tools/verifier-licences.py

`--projet` permet de pointer une **copie** de l'arbre plutot que le depot : c'est ce que fait le
falsificateur, qui a besoin de muter les fichiers sans jamais toucher a l'arbre reel.

Sortie 0 si tout est verifie, 1 sinon.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import struct
import sys

PROJET_DEFAUT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

POLICES = os.path.join("app", "src", "main", "assets", "coran-test")
PROVENANCE = os.path.join(POLICES, "provenance.json")
QURAN = os.path.join("core", "domain", "src", "main", "resources", "quran")
LICENCES = os.path.join("core", "design", "src", "main", "assets", "licenses")
NOTICE = "NOTICE.md"

#: Les deux polices nommees que l'ecran immersif consomme en plus des 604 pages.
POLICES_NOMMEES = ["surah-name-v4.woff2", "vertopal.com_QCF_Bismillah-Regular.woff2"]

#: Les 604 pages du moushaf, numerotees de 1 a 604.
PAGES = range(1, 605)

#: Le motif des cles de `provenance.json`, tel que l'archive source les nomme.
MOTIF_PROVENANCE = re.compile(r"^fonts/quran_fonts_qfc4/QCF4(\d{3})_COLOR-Regular\.ttf\.gz$")

#: Ce que le `NOTICE.md` doit nommer pour la famille des polices QCF v4. Les ancres sont des noms
#: de fichier, de dossier ou de detenteur reels : renommer la ressource casse l'ancre, et c'est
#: voulu.
ANCRES_QCF = ["coran-test", "provenance.json", "KFGQPC", "QUL", "COLR"]

#: Un mot qui designe une regie publicitaire ou l'identifiant publicitaire dans une coordonnee
#: Maven. Volontairement large : un faux positif se voit et se corrige, une regie manquee ne se
#: voit pas.
MOTS_PUBLICITAIRES = [
    "play-services-ads", "audience-network", "applovin", "unity3d.ads", "adjust.sdk", "mopub",
    "ironsource", "vungle", "chartboost", "bytedance", "hms:ads", "startapp", "inmobi",
    "device.ads", "admob", "smaato", "mintegral", "fyber", "adcolony", "ogury", "pubnative",
    "taboola", "outbrain", "smartadserver", "adform", "ads-identifier",
]

#: Les balises WOFF2 connues, par index de la specification.
TAGS_WOFF2 = {
    0: "cmap", 1: "head", 2: "hhea", 3: "hmtx", 4: "maxp", 5: "name", 6: "OS/2", 7: "post",
    8: "cvt ", 9: "fpgm", 10: "glyf", 11: "loca", 12: "prep", 13: "CFF ", 14: "VORG", 15: "EBDT",
    16: "EBLC", 17: "gasp", 18: "hdmx", 19: "kern", 20: "LTSH", 21: "PCLT", 22: "VDMX", 23: "vhea",
    24: "vmtx", 25: "BASE", 26: "GDEF", 27: "GPOS", 28: "GSUB", 29: "EBSC", 30: "JSTF", 31: "MATH",
    32: "CBDT", 33: "CBLC", 34: "COLR", 35: "CPAL", 36: "SVG ", 37: "sbix", 38: "acnt", 39: "avar",
    40: "bdat", 41: "bloc", 42: "bsln", 43: "cvar", 44: "fdsc", 45: "feat", 46: "fmtx", 47: "fvar",
    48: "gvar", 49: "hsty", 50: "just", 51: "lcar", 52: "mort", 53: "morx", 54: "opbd", 55: "prop",
    56: "trak", 57: "Zapf", 58: "Silf", 59: "Glat", 60: "Gloc", 61: "Feat", 62: "Sill",
}


class Verificateur:
    """Accumule les ecarts au lieu de s'arreter au premier : un rapport complet vaut mieux."""

    def __init__(self, projet: str) -> None:
        self.projet = projet
        self.ecarts: list[str] = []
        self.verifications = 0

    def chemin(self, *morceaux: str) -> str:
        return os.path.join(self.projet, *morceaux)

    def exiger(self, condition: bool, message: str) -> bool:
        self.verifications += 1
        if not condition:
            self.ecarts.append(message)
        return condition

    def section(self, titre: str, travail) -> None:
        """Execute une famille de verifications et rend compte de son seul resultat.

        Le rapport dit **combien** d'elements une section a examines, et non seulement qu'elle est
        verte : une section qui n'exigerait rien serait verte pour la mauvaise raison, et c'est le
        genre de silence qu'un simple « OK » ne distinguerait pas.
        """
        print(f"\n{titre}")
        avant_ecarts, avant_verifications = len(self.ecarts), self.verifications
        travail()
        faites = self.verifications - avant_verifications
        nouveaux = len(self.ecarts) - avant_ecarts
        if nouveaux:
            print(f"  {nouveaux} ecart(s) sur {faites} element(s) examine(s).")
        else:
            print(f"  OK — {faites} element(s) examine(s).")


def lire_notice(v: Verificateur) -> str:
    chemin = v.chemin(NOTICE)
    if not os.path.exists(chemin):
        v.ecarts.append(f"{NOTICE} est absent : les conditions des ressources embarquees n'ont pas "
                        "de fichier ou vivre.")
        return ""
    with open(chemin, encoding="utf-8") as f:
        return f.read()


def verifier_couverture(v: Verificateur, notice: str) -> None:
    """Chaque famille presente dans l'arbre doit etre nommee dans le NOTICE."""
    if os.path.isdir(v.chemin(POLICES)):
        for ancre in ANCRES_QCF:
            v.exiger(ancre in notice,
                     f"la famille QCF v4 est embarquee mais le NOTICE ne nomme pas « {ancre} ».")
        for nom in POLICES_NOMMEES:
            v.exiger(nom in notice,
                     f"la police nommee « {nom} » est embarquee mais n'est pas nommee dans le "
                     "NOTICE.")

    licence_tanzil = os.path.join(QURAN, "TANZIL-LICENSE.txt")
    if os.path.exists(v.chemin(licence_tanzil)):
        v.exiger("TANZIL-LICENSE.txt" in notice,
                 "les donnees du corpus sont embarquees avec leur licence TANZIL, mais le NOTICE "
                 "ne la nomme pas.")

    dossier_licences = v.chemin(LICENCES)
    if os.path.isdir(dossier_licences):
        for fichier in sorted(os.listdir(dossier_licences)):
            if fichier.startswith("OFL-") and fichier.endswith(".txt"):
                v.exiger(fichier in notice,
                         f"la licence « {fichier} » est embarquee mais n'est pas nommee dans le "
                         "NOTICE.")


def verifier_provenance(v: Verificateur) -> None:
    """`provenance.json` doit couvrir exactement les 604 polices numerotees livrees."""
    chemin = v.chemin(PROVENANCE)
    if not os.path.exists(chemin):
        v.ecarts.append(f"{PROVENANCE} est absent : l'origine des polices n'est plus documentee.")
        return
    with open(chemin, encoding="utf-8") as f:
        provenance = json.load(f)

    v.exiger(bool(provenance.get("sha256")),
             "la provenance ne porte pas l'empreinte de l'archive source.")
    v.exiger(bool(provenance.get("source")),
             "la provenance ne nomme pas l'archive source.")

    ressources = provenance.get("resources") or {}
    pages_couvertes: set[int] = set()
    for cle in ressources:
        m = MOTIF_PROVENANCE.match(cle)
        v.exiger(bool(m), f"la provenance porte une cle hors du motif attendu : {cle!r}.")
        if m:
            pages_couvertes.add(int(m.group(1)))
    v.exiger(pages_couvertes == set(PAGES),
             f"la provenance couvre {len(pages_couvertes)} pages au lieu des 604 (1..604) — "
             f"manquantes : {sorted(set(PAGES) - pages_couvertes)[:8]}.")

    dossier = v.chemin(POLICES)
    if not os.path.isdir(dossier):
        v.ecarts.append(f"{POLICES} est absent.")
        return
    livrees = sorted(os.listdir(dossier))
    numerotees = {int(n[:-6]) for n in livrees if n.endswith(".woff2") and n[:-6].isdigit()}
    for page in PAGES:
        v.exiger(page in numerotees,
                 f"la police de la page {page} n'est pas livree alors que la provenance la couvre.")
    v.exiger(numerotees <= set(PAGES),
             f"une police numerotee hors des pages 1..604 est livree : "
             f"{sorted(numerotees - set(PAGES))[:8]}.")
    for nom in POLICES_NOMMEES:
        v.exiger(nom in livrees, f"la police nommee « {nom} » n'est pas livree.")


def tables_woff2(chemin: str) -> list[str]:
    """Les balises du repertoire d'une WOFF2, sans decompresser aucune table.

    Le repertoire n'est pas compresse : il se lit directement. Les tables `glyf` et `loca` sont
    les seules a porter une longueur de transformation conditionnelle, et l'oublier decale toutes
    les balises suivantes — un parseur qui les ignore rend des balises dupliquees au lieu d'une
    erreur, donc il ne se plaint pas.
    """
    with open(chemin, "rb") as f:
        octets = f.read(256 * 1024)
    if octets[:4] != b"wOF2":
        raise ValueError(f"{chemin} : signature WOFF2 attendue, trouvee {octets[:4]!r}")
    nb = struct.unpack(">H", octets[12:14])[0]

    def base128(i: int) -> tuple[int, int]:
        valeur = 0
        for _ in range(5):
            c = octets[i]
            i += 1
            valeur = (valeur << 7) | (c & 0x7F)
            if not c & 0x80:
                return valeur, i
        raise ValueError(f"{chemin} : entier base128 trop long")

    i = 48
    balises: list[str] = []
    for _ in range(nb):
        drapeaux = octets[i]
        i += 1
        index, transformation = drapeaux & 0x3F, drapeaux >> 6
        if index == 0x3F:
            balise = octets[i:i + 4].decode("latin1")
            i += 4
        else:
            balise = TAGS_WOFF2.get(index, f"#{index}")
        _, i = base128(i)
        if balise in ("glyf", "loca"):
            if transformation != 3:
                _, i = base128(i)
        elif transformation != 0:
            raise ValueError(f"{chemin} : version de transformation {transformation} inattendue "
                             f"pour la table {balise}")
        balises.append(balise)
    return balises


def verifier_couleur(v: Verificateur) -> None:
    """Les 604 polices de page doivent porter les tables couleur — sinon le NOTICE ment."""
    dossier = v.chemin(POLICES)
    if not os.path.isdir(dossier):
        return
    for page in PAGES:
        chemin = os.path.join(dossier, f"{page}.woff2")
        if not os.path.exists(chemin):
            continue
        balises = set(tables_woff2(chemin))
        v.exiger("COLR" in balises and "CPAL" in balises,
                 f"la police de la page {page} ne porte plus COLR/CPAL : le Tajwid vient de ces "
                 "tables, et la justification ecrite dans le NOTICE tombe.")


def coordonnees_maven(v: Verificateur) -> list[tuple[str, str]]:
    """Toutes les coordonnees `"groupe:artefact"` declarees dans les fichiers Gradle."""
    fichiers: list[str] = []
    for racine, _, noms in os.walk(v.projet):
        if os.sep + "build" in racine or os.sep + ".git" in racine:
            continue
        for nom in noms:
            if nom.endswith(".gradle.kts") or nom == "libs.versions.toml":
                fichiers.append(os.path.join(racine, nom))
    v.exiger(bool(fichiers),
             "aucun fichier Gradle n'a ete trouve : la lecture des dependances n'a rien couvert.")

    coordonnees: list[tuple[str, str]] = []
    for chemin in fichiers:
        with open(chemin, encoding="utf-8") as f:
            for ligne in f:
                for m in re.finditer(r'"([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+)"', ligne):
                    coordonnees.append((os.path.relpath(chemin, v.projet), m.group(0)))
    return coordonnees


def verifier_publicite(v: Verificateur) -> None:
    """Aucune dependance publicitaire ne doit etre declaree."""
    coordonnees = coordonnees_maven(v)
    for chemin, coordonnee in coordonnees:
        bas = coordonnee.lower()
        v.exiger(not any(mot in bas for mot in MOTS_PUBLICITAIRES),
                 f"dependance publicitaire declaree dans {chemin} : {coordonnee} — la condition 1 "
                 "des termes QCF/QUL l'interdit.")


def main() -> int:
    analyseur = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    analyseur.add_argument("--projet", default=PROJET_DEFAUT,
                           help="racine a verifier (par defaut, la racine du depot)")
    options = analyseur.parse_args()

    v = Verificateur(os.path.abspath(options.projet))
    print(f"Racine verifiee : {v.projet}")

    notice = lire_notice(v)
    v.section("A. Couverture du NOTICE", lambda: verifier_couverture(v, notice))
    v.section("B. Provenance des polices", lambda: verifier_provenance(v))
    v.section("C. Tables couleur des polices de page", lambda: verifier_couleur(v))
    v.section("D. Dependances publicitaires", lambda: verifier_publicite(v))

    print(f"\n{v.verifications} elements examines.")
    if v.ecarts:
        print(f"\n{len(v.ecarts)} ECART(S) :")
        for ecart in v.ecarts:
            print(f"  - {ecart}")
        return 1
    print("Les ressources tierces embarquees sont nommees, la provenance couvre les polices "
          "livrees, et aucune regie publicitaire n'est declaree.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
