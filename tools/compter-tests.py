#!/usr/bin/env python3
"""Compte les tests reellement executes, d'apres les rapports du coureur.

Pourquoi ce script existe : un total de tests ne se deduit pas d'un `grep @Test`. Deux pieges
ont deja fait publier un faux chiffre dans la documentation de ce depot :

  1. les rapports XML des variantes `debug` et `release` restent tous les deux sur le disque ;
     additionner `**/build/test-results/**` compte donc chaque test deux fois ;
  2. un rapport perime survit a la suppression d'un test, et continue d'etre compte.

Ce script lit les rapports, regroupe par classe, et ne retient qu'**une** variante par classe.
Si les deux variantes d'une meme classe n'annoncent pas le meme nombre de tests, il le dit :
c'est le signe qu'un rapport est perime et que le chiffre ne vaut rien.

Usage :
    python tools/compter-tests.py                 # depuis la racine du depot
    python tools/compter-tests.py --variante release
    python tools/compter-tests.py --json          # sortie machine
"""

from __future__ import annotations

import argparse
import glob
import json
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

VARIANTES = ("debug", "release")


def variante_de(chemin: str) -> str | None:
    """Deduit la variante du chemin du rapport.

    Un module Kotlin/JVM pur (comme `core:domain`) ecrit sous `build/test-results/test/`, sans
    variante Android. L'ignorer faisait disparaitre ses tests du total : 132 tests oublies.
    """
    if "/test-results/testDebugUnitTest/" in chemin:
        return "debug"
    if "/test-results/testReleaseUnitTest/" in chemin:
        return "release"
    if "/test-results/test/" in chemin:
        return "jvm"
    return None


def rapports() -> dict[str, dict[str, dict[str, int]]]:
    """classe -> variante -> {tests, echecs, ignores}"""
    trouves: dict[str, dict[str, dict[str, int]]] = defaultdict(dict)
    for chemin in glob.glob("**/build/test-results/**/TEST-*.xml", recursive=True):
        chemin_normalise = chemin.replace("\\", "/")
        if "/binary/" in chemin_normalise:
            continue
        variante = variante_de(chemin_normalise)
        if variante is None:
            continue
        try:
            racine = ET.parse(chemin).getroot()
        except ET.ParseError:
            continue
        if racine.tag != "testsuite":
            continue
        classe = racine.get("name", chemin)
        trouves[classe][variante] = {
            "tests": int(racine.get("tests", 0)),
            "echecs": int(racine.get("failures", 0)) + int(racine.get("errors", 0)),
            "ignores": int(racine.get("skipped", 0)),
        }
    return trouves


def module_de(classe: str) -> str:
    """`com.msoumaya.deepseekandroid.feature.home.X` -> `feature:home`.

    Les modules a sous-groupe (`core:*`, `feature:*`) sont nommes par leurs deux derniers
    segments ; les autres (`navigation`, `app`) par le segment qui suit le nom du projet.
    """
    morceaux = classe.split(".")
    for groupe in ("core", "feature"):
        if groupe in morceaux:
            i = morceaux.index(groupe)
            if i + 1 < len(morceaux):
                return f"{groupe}:{morceaux[i + 1]}"
    if "deepseekandroid" in morceaux:
        i = morceaux.index("deepseekandroid")
        if i + 1 < len(morceaux):
            return morceaux[i + 1]
    return "?"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--variante",
        choices=VARIANTES,
        default="debug",
        help="variante de test a compter (defaut : debug)",
    )
    parser.add_argument("--json", action="store_true", help="sortie lisible par une machine")
    args = parser.parse_args()

    trouves = rapports()
    if not trouves:
        sys.exit("Aucun rapport de test trouve. Lancez d'abord une execution des tests.")

    par_module: dict[str, list[int]] = defaultdict(lambda: [0, 0, 0])
    incoherences = []
    classes = []

    # On compte la variante Android demandee **et** les modules JVM purs, qui n'en ont qu'une.
    retenues = {"jvm", args.variante}

    for classe in sorted(trouves):
        variantes = trouves[classe]
        # Deux variantes Android presentes avec des totaux differents : un rapport est perime.
        android = {v: variantes[v] for v in VARIANTES if v in variantes}
        if len(android) > 1:
            totaux = {v: android[v]["tests"] for v in android}
            if len(set(totaux.values())) > 1:
                incoherences.append((classe, totaux))

        mesure = next((variantes[v] for v in sorted(retenues) if v in variantes), None)
        if mesure is None:
            continue

        classes.append({"classe": classe, "module": module_de(classe), **mesure})
        agg = par_module[module_de(classe)]
        agg[0] += mesure["tests"]
        agg[1] += mesure["echecs"]
        agg[2] += mesure["ignores"]

    total = sum(m[0] for m in par_module.values())
    echecs = sum(m[1] for m in par_module.values())
    ignores = sum(m[2] for m in par_module.values())

    if args.json:
        print(json.dumps(
            {
                "variante": args.variante,
                "total": total,
                "echecs": echecs,
                "ignores": ignores,
                "modules": {
                    m: {"tests": v[0], "echecs": v[1], "ignores": v[2]}
                    for m, v in sorted(par_module.items())
                },
                "classes": classes,
                "incoherences": [
                    {"classe": c, "totaux_par_variante": t} for c, t in incoherences
                ],
            },
            indent=2,
            ensure_ascii=False,
        ))
        return 0 if echecs == 0 else 1

    print(f"Variante comptee : {args.variante}")
    print(f"Classes de test  : {len(classes)}")
    print()
    print(f"{'module':<14} {'tests':>6} {'echecs':>7} {'ignores':>8}")
    for module in sorted(par_module):
        n, f, s = par_module[module]
        print(f"{module:<14} {n:>6} {f:>7} {s:>8}")
    print("-" * 38)
    print(f"{'TOTAL':<14} {total:>6} {echecs:>7} {ignores:>8}")

    if incoherences:
        print()
        print("ATTENTION : deux variantes annoncent des totaux differents pour la meme classe.")
        print("Un rapport est perime ; le chiffre ci-dessus n'est pas fiable.")
        for classe, totaux in incoherences:
            print(f"  {classe} : {totaux}")
        return 1

    print()
    print("VERDICT :", "TOUT VERT" if echecs == 0 else f"{echecs} ECHEC(S)")
    return 0 if echecs == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
