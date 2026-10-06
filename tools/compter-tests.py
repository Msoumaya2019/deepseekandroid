#!/usr/bin/env python3
"""Compte les tests reellement executes, d'apres les rapports du coureur.

Pourquoi ce script existe : un total de tests ne se deduit pas d'un `grep @Test`. Trois pieges
ont deja fait publier un faux chiffre dans la documentation de ce depot :

  1. les rapports XML des variantes `debug` et `release` restent tous les deux sur le disque ;
     additionner `**/build/test-results/**` compte donc chaque test deux fois ;
  2. un rapport perime survit a la suppression d'un test, et continue d'etre compte ;
  3. **une campagne de falsification laisse des rapports en ECHEC sur le disque.** Le code livre
     est vert, et le compteur annonce pourtant des echecs : ce sont ceux des sources mutees, qui
     n'existent plus. Constate pour de vrai : cinq rapports en echec datant d'une campagne,
     sur un arbre ou `test` venait de passer.

Le piege 3 se traite en regardant les **horodatages** : une execution complete ecrit tous ses
rapports dans une meme fenetre de temps. Deux groupes separes par un trou franc ne viennent donc
pas de la meme execution, et le total melange deux etats du code. Le script le dit alors au lieu
de rendre un verdict — « tout vert » ne voudrait rien dire sur un arbre qui n'a jamais ete joue
d'un bout a l'autre.

Usage :
    python tools/compter-tests.py                 # depuis la racine du depot
    python tools/compter-tests.py --variante release
    python tools/compter-tests.py --json          # sortie machine
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import time
import xml.etree.ElementTree as ET
from collections import defaultdict

VARIANTES = ("debug", "release")

# Deux rapports separes par plus que cela ne viennent pas de la meme execution. La valeur est
# large a dessein : elle doit couvrir la duree d'une execution complete, meme lente, mais rester
# plus petite que le trou qui separe deux executions distinctes. Sur ce depot une execution
# complete ecrit ses rapports en quelques minutes.
TROU_ENTRE_EXECUTIONS = 900.0


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
    """classe -> variante -> {tests, echecs, ignores, ecrit}"""
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
            "ecrit": os.path.getmtime(chemin),
        }
    return trouves


def executions(releves: list[dict]) -> list[list[dict]]:
    """Groupe les rapports retenus par execution, du plus ancien au plus recent.

    Un groupe est une suite d'horodatages sans trou plus grand que `TROU_ENTRE_EXECUTIONS`.
    Rendre plusieurs groupes signifie que les rapports lus ne viennent pas tous de la meme
    execution — donc que le total porte sur plusieurs etats du code.
    """
    if not releves:
        return []
    groupes: list[list[dict]] = [[releves[0]]]
    for precedent, courant in zip(releves, releves[1:]):
        if courant["ecrit"] - precedent["ecrit"] > TROU_ENTRE_EXECUTIONS:
            groupes.append([])
        groupes[-1].append(courant)
    return groupes


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


def horodate(instant: float) -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(instant))


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

    # Le total se juge sur des rapports d'une **seule** execution.
    par_ecriture = sorted(classes, key=lambda c: c["ecrit"])
    groupes = executions(par_ecriture)
    plus_recent = par_ecriture[-1]["ecrit"] if par_ecriture else 0.0
    plus_ancien = par_ecriture[0]["ecrit"] if par_ecriture else 0.0
    dernieres = {c["classe"] for c in groupes[-1]} if groupes else set()
    hors_derniere = [c for c in par_ecriture if c["classe"] not in dernieres]

    if args.json:
        print(json.dumps(
            {
                "variante": args.variante,
                "total": total,
                "echecs": echecs,
                "ignores": ignores,
                "rapports": len(classes),
                "ecrit_entre": [horodate(plus_ancien), horodate(plus_recent)] if classes else [],
                "executions": len(groupes),
                "hors_derniere_execution": [c["classe"] for c in hors_derniere],
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
        # Plusieurs executions ne font pas echouer la mesure : sans echec, les rapports plus
        # anciens viennent de modules que Gradle n'a pas rejoues faute d'entree modifiee.
        return 0 if echecs == 0 and not incoherences else 1

    print(f"Variante comptee : {args.variante}")
    print(f"Classes de test  : {len(classes)}")
    if classes:
        print(f"Rapports ecrits  : du {horodate(plus_ancien)} au {horodate(plus_recent)}")
    print()
    print(f"{'module':<14} {'tests':>6} {'echecs':>7} {'ignores':>8}")
    for module in sorted(par_module):
        n, f, s = par_module[module]
        print(f"{module:<14} {n:>6} {f:>7} {s:>8}")
    print("-" * 38)
    print(f"{'TOTAL':<14} {total:>6} {echecs:>7} {ignores:>8}")

    verdict_sur = True

    if incoherences:
        verdict_sur = False
        print()
        print("ATTENTION : deux variantes annoncent des totaux differents pour la meme classe.")
        print("Un rapport est perime ; le chiffre ci-dessus n'est pas fiable.")
        for classe, totaux in incoherences:
            print(f"  {classe} : {totaux}")

    if len(groupes) > 1:
        # Plusieurs executions ne sont pas un defaut en soi : Gradle ne rejoue pas la tache d'un
        # module dont les entrees n'ont pas change (`UP-TO-DATE`), et son rapport reste alors plus
        # ancien que les autres — **valable**, puisque Gradle ne rejoue que si une entree a bouge.
        # Ce qui ne se rattrape pas, c'est un **echec** : il peut venir d'une source mutee par une
        # campagne de falsification, qui n'existe plus dans le code livre.
        print()
        print(f"NOTE : les rapports lus viennent de {len(groupes)} executions distinctes")
        print(f"(du {horodate(plus_ancien)} au {horodate(plus_recent)}).")
        print(f"Rapports anterieurs a la derniere execution ({len(hors_derniere)}) :")
        for releve in hors_derniere[:10]:
            print(f"  {horodate(releve['ecrit'])}  {releve['classe']}  ({releve['echecs']} echec(s))")
        if len(hors_derniere) > 10:
            print(f"  ... et {len(hors_derniere) - 10} autre(s)")
        if echecs:
            verdict_sur = False
            print()
            print("Des echecs figurent dans cet ensemble, et il melange plusieurs executions : ils")
            print("peuvent venir d'une campagne de falsification. Relancez la suite, puis comptez.")
            for releve in par_ecriture:
                if releve["echecs"]:
                    print(f"  {horodate(releve['ecrit'])}  {releve['classe']}  ({releve['echecs']} echec(s))")
        else:
            print("Aucun echec : les rapports plus anciens viennent de modules que Gradle n'a pas")
            print("rejoues faute d'entree modifiee. Le total reste valable.")

    if not verdict_sur:
        print()
        print("VERDICT : NON CONCLUANT (voir les avertissements ci-dessus)")
        return 1

    print()
    print("VERDICT :", "TOUT VERT" if echecs == 0 else f"{echecs} ECHEC(S)")
    return 0 if echecs == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
