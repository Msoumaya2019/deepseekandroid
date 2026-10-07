#!/usr/bin/env python3
"""Verifie que les compteurs annonces dans les documents sont ceux du banc.

Pourquoi ce script existe : le meme total de tests vit dans **deux** tableaux — celui du `README`
et celui d'`ARCHITECTURE.md` — et rien ne les tenait ensemble. Le fait est mesure, pas suppose :

  * le tableau du `README` annoncait **1443** tests quand le banc en comptait **1564** (121
    d'ecart), et il ignorait le module `feature:quiz` ;
  * celui d'`ARCHITECTURE.md` annoncait **1471** (93 d'ecart) ;
  * un troisieme endroit, la phrase qui annonce le nombre de cas du falsifier, est reste en
    arriere **deux fois de plus** : a **272** alors que le banc en portait davantage, puis a
    **282** alors qu'il en portait **283**.

Un compteur recopie derive, et il est **cru**. Ce script relit les **deux** tableaux, les compare
au rapport du coureur — par `compter-tests.py`, qui sait ne compter qu'une variante par classe —
et **refuse** en nommant la ligne fautive. Il verifie aussi que la **somme** des lignes retombe
sur le total annonce : c'est la seule preuve qu'aucun module n'a ete oublie.

Le **troisieme** endroit est tenu par `verifier_compte_des_cas` : il compare la phrase du `README`
au nombre de cas que `falsifier.py` porte reellement, au lieu de le croire.

Usage :
    python tools/verifier-compteurs-documents.py          # depuis la racine du depot
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys

# `| \`feature:home\` | 36 |` et `| \`feature:home\` | **36** |` : les deux formes existent.
LIGNE_MODULE = re.compile(
    r"^\|\s*`(?P<module>[a-z]+(?::[a-z]+)?)`\s*\|\s*(?:\*\*)?(?P<compte>\d+)(?:\*\*)?\s*\|"
)
LIGNE_TOTAL = re.compile(r"^\|\s*\*\*total\*\*\s*\|\s*\*\*(?P<total>\d+)\*\*\s*\|\s*(?P<classes>\d+)")

# Les documents qui portent un tableau de tests, et la ligne de phrase qui annonce le total.
DOCUMENTS = {
    "README.md": re.compile(r"\*\*(?P<total>\d+) tests\*\*"),
    "ARCHITECTURE.md": None,
}


def lancer_compter_tests() -> dict:
    """Le rapport du coureur, par l'outil qui sait le lire."""
    sortie = subprocess.run(
        [sys.executable, os.path.join("tools", "compter-tests.py"), "--json"],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if not sortie.stdout.strip():
        sys.exit(f"compter-tests.py n'a rien rendu.\n{sortie.stderr}")
    return json.loads(sortie.stdout)


# La phrase du `README` qui annonce le nombre de cas du falsifier. Elle est ecrite a la main, dans
# une phrase : rien ne la relie a la liste des cas — c'est le troisieme endroit de la docstring.
MOTIF_CAS_FALSIFIER = re.compile(r"\*\*(?P<cas>\d+)\*\* cas sont encore jouables")


def compter_cas_du_banc() -> int:
    """Le nombre de cas que `falsifier.py` porte reellement.

    Lu par l'interface du falsificateur — `--verifier` — et non en comptant des motifs dans son
    source : un `grep` sur `"nom":` compterait aussi les cas commentes, et ne dirait rien de ce
    que le banc joue. `--verifier` ne lance pas Gradle, donc le controle reste court.

    Le compte rendu est le nombre **total** de cas, et le script s'arrete si les deux nombres
    different : annoncer combien de cas porte une liste dont une partie n'est pas jouable n'aurait
    pas de sens, et c'est le genre de desaccord qu'il vaut mieux voir que traverser.
    """
    sortie = subprocess.run(
        [sys.executable, os.path.join("tools", "falsifier.py"), "--verifier"],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    trouve = re.search(r"VERDICT : (\d+)/(\d+) cas jouable", sortie.stdout)
    if not trouve:
        sys.exit(
            "falsifier.py --verifier n'a pas rendu son verdict.\n"
            f"--- sortie ---\n{sortie.stdout[-2000:]}\n--- erreur ---\n{sortie.stderr}"
        )
    jouables, total = int(trouve.group(1)), int(trouve.group(2))
    if jouables != total:
        sys.exit(
            f"falsifier.py : {jouables} cas jouables sur {total}. Le compte des cas n'a pas de "
            "sens tant que la liste n'est pas saine."
        )
    return total


def verifier_compte_des_cas(soucis: list[str]) -> None:
    """Le troisieme endroit : le nombre de cas annonce dans le `README`.

    La docstring de ce script decrit ce trou depuis le debut, et **rien** ne le verifiait. Mesure :
    le nombre est passe de 272 a 282, puis de 282 a 283, chaque fois sans que rien ne le dise.
    """
    reel = compter_cas_du_banc()
    with open("README.md", encoding="utf-8", newline="") as flux:
        contenu = flux.read()

    print("=== compte des cas du falsificateur ===")
    trouve = MOTIF_CAS_FALSIFIER.search(contenu)
    if not trouve:
        soucis.append(
            "README.md : la phrase annoncant le nombre de cas du falsificateur est introuvable"
        )
        print("  PHRASE INTROUVABLE")
        return

    annonce = int(trouve.group("cas"))
    if annonce != reel:
        soucis.append(f"README.md : annonce {annonce} cas au falsificateur, le banc en porte {reel}")
        print(f"  NON annonce {annonce}, banc {reel}")
        return

    print(f"  ok  {annonce} cas")


def main() -> int:
    rapport = lancer_compter_tests()
    modules_banc: dict[str, int] = {
        module: valeurs["tests"] for module, valeurs in rapport["modules"].items()
    }
    total_banc = rapport["total"]
    classes_banc = rapport["rapports"]

    print(f"banc : {total_banc} tests, {classes_banc} classes")
    for module in sorted(modules_banc):
        print(f"   {module:<16} {modules_banc[module]:>5}")
    print()

    soucis: list[str] = []

    verifier_compte_des_cas(soucis)

    for document, motif_phrase in DOCUMENTS.items():
        with open(document, encoding="utf-8", newline="") as flux:
            lignes = flux.readlines()

        annonces: dict[str, int] = {}
        total_annonce: int | None = None
        classes_annoncees: int | None = None

        for ligne in lignes:
            trouve = LIGNE_MODULE.match(ligne)
            if trouve:
                annonces[trouve.group("module")] = int(trouve.group("compte"))
                continue
            trouve = LIGNE_TOTAL.match(ligne)
            if trouve:
                total_annonce = int(trouve.group("total"))
                classes_annoncees = int(trouve.group("classes"))
                continue
            if motif_phrase:
                trouve = motif_phrase.search(ligne)
                if trouve and total_annonce is None:
                    total_annonce = int(trouve.group("total"))

        print(f"=== {document} ===")
        if not annonces:
            soucis.append(f"{document} : aucun tableau de modules reconnu")
            print("  AUCUN TABLEAU RECONNU")
            continue

        for module in sorted(set(annonces) | set(modules_banc)):
            attendu = modules_banc.get(module)
            vu = annonces.get(module)
            if attendu is None:
                print(f"  ligne {module} : annoncee {vu}, absente du banc")
                soucis.append(f"{document} : {module} annonce {vu}, module inconnu du banc")
            elif vu != attendu:
                print(f"  ligne {module} : annoncee {vu}, banc {attendu}")
                soucis.append(f"{document} : {module} annonce {vu}, banc {attendu}")
            else:
                print(f"  ok  {module:<16} {vu:>5}")

        somme = sum(annonces.values())
        if total_annonce is None:
            soucis.append(f"{document} : total introuvable")
            print("  TOTAL INTROUVABLE")
        elif somme != total_annonce:
            soucis.append(f"{document} : somme des lignes {somme} != total annonce {total_annonce}")
            print(f"  NON somme des lignes {somme} != total annonce {total_annonce}")
        else:
            print(f"  ok  somme des lignes {somme} == total annonce {total_annonce}")

        if total_annonce is not None and total_annonce != total_banc:
            soucis.append(f"{document} : total annonce {total_annonce}, banc {total_banc}")
            print(f"  NON total annonce {total_annonce} != banc {total_banc}")

        if classes_annoncees is not None and classes_annoncees != classes_banc:
            soucis.append(
                f"{document} : {classes_annoncees} classes annoncees, banc {classes_banc}"
            )
            print(f"  NON classes annoncees {classes_annoncees} != banc {classes_banc}")
        elif classes_annoncees is not None:
            print(f"  ok  classes {classes_annoncees}")
        print()

    if soucis:
        print("ECARTS :")
        for souci in soucis:
            print(f"  {souci}")
        return 1
    print("VERDICT : les documents annoncent les compteurs du banc")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
