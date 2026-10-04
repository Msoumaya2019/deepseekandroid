"""Verifie que les donnees embarquees sont celles de `coran-memoire`, a l'octet pres.

Pourquoi ce script existe
-------------------------

Le portage ne *reconstruit* pas le corpus : il le **recopie**. `verses.json`, `bounds.json`,
les 604 pages du moushaf — tout vient du depot d'origine. Une copie peut donc etre fausse de
trois facons, et aucune ne se voit a l'oeil :

  1. **tronquee.** Un import interrompu laisse un JSON valide mais plus court, et le domaine
     s'execute dessus sans se plaindre ;
  2. **perimee.** Le depot d'origine bouge ; une copie faite avant un correctif reste
     silencieusement en place ;
  3. **melangee.** Le nom du fichier dit une chose, son contenu une autre — le cas le plus
     vicieux, parce que le manifeste d'import, lui, garde l'empreinte de ce qui *devait*
     arriver.

Le script mesure les trois, en une passe :

  * il **recalcule** l'empreinte de chaque fichier de donnees et la compare a celle que le
    manifeste d'import a enregistree — ce qui detecte un fichier modifie apres l'import ;
  * il **retrouve** le meme fichier dans la copie locale de `coran-memoire` et compare les
    octets — ce qui detecte une copie qui n'est pas la bonne ;
  * il fait de meme pour les **604 pages**, une par une.

Il **ne devine pas** l'algorithme d'empreinte du manifeste : il essaie `md5`, `sha1` et
`sha256` sur les douze premiers caracteres et dit lequel correspond. Un manifeste dont aucune
empreinte ne se retrouve est un manifeste suspect, et le script le signale au lieu de le
croire.

Usage
-----

    python tools/verifier-parite-donnees.py --source <copie locale de coran-memoire>

La source est ouverte en **lecture seule** : le script lit, ne copie rien et n'ecrit nulle
part. Un ecart fait sortir en 1, ce qui rend le script utilisable tel quel dans une CI.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys

PROJET = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

#: Les donnees du domaine, et l'endroit ou le manifeste les decrit.
DONNEES = os.path.join(PROJET, "core", "domain", "src", "main", "resources", "quran")
MANIFESTE = os.path.join(DONNEES, "import-manifest.json")

#: Les pages embarquees, et leur nom dans la source.
PAGES_PORT = os.path.join(PROJET, "app", "src", "main", "assets", "quran", "pages")
PAGES_SOURCE = os.path.join("assets", "mushaf")

#: Livre dans la source mais **volontairement non importe** : le moushaf Tajweed par images
#: pese 138 Mo de plus. Le nommer evite de le compter comme un oubli au prochain import.
NON_IMPORTE = os.path.join("assets", "mushaf-tajweed")

#: Le manifeste enregistre douze caracteres hexadecimaux. On essaie les trois algorithmes
#: plausibles plutot que d'en supposer un.
ALGORITHMES = ("md5", "sha1", "sha256")


def empreinte(chemin: str) -> dict[str, str]:
    """Les douze premiers caracteres de chaque algorithme, lus en une seule passe disque."""
    with open(chemin, "rb") as fichier:
        contenu = fichier.read()
    return {nom: hashlib.new(nom, contenu).hexdigest()[:12] for nom in ALGORITHMES}


def octets(chemin: str) -> int:
    return os.path.getsize(chemin)


def indexer(racine: str) -> dict[str, list[str]]:
    """Tous les fichiers d'une extension donnas, indexes par nom de base.

    Le nom de base suffit : le portage reprend les fichiers par leur nom, et deux fichiers du
    meme nom dans deux dossiers differents seraient eux-memes une anomalie a voir.
    """
    trouves: dict[str, list[str]] = {}
    for dossier, sous_dossiers, fichiers in os.walk(racine):
        sous_dossiers[:] = [d for d in sous_dossiers if d not in ("node_modules", ".git", "build")]
        for nom in fichiers:
            trouves.setdefault(nom, []).append(os.path.join(dossier, nom))
    return trouves


def verifier_donnees(source: str, index_source: dict[str, list[str]]) -> int:
    """Les fichiers de donnees. Rend le nombre d'anomalies."""
    if not os.path.exists(MANIFESTE):
        print(f"MANIFESTE ABSENT : {MANIFESTE}")
        return 1

    with open(MANIFESTE, encoding="utf-8") as fichier:
        manifeste = json.load(fichier)
    attendus: dict[str, str] = manifeste.get("dataFiles", {})
    if not attendus:
        print("MANIFESTE VIDE : aucune empreinte a confronter.")
        return 1

    print(f"--- donnees du domaine ({len(attendus)} fichiers declares) ---")
    anomalies = 0
    algo_retenu: set[str] = set()

    for nom in sorted(attendus):
        declaree = attendus[nom]
        ici = os.path.join(DONNEES, nom)
        if not os.path.exists(ici):
            print(f"  ABSENT DU PORT   {nom}")
            anomalies += 1
            continue

        miennes = empreinte(ici)
        correspondances = [a for a, valeur in miennes.items() if valeur == declaree]
        algo_retenu.update(correspondances)

        if not correspondances:
            print(f"  EMPREINTE        {nom} : le manifeste dit {declaree}, "
                  f"le fichier donne {miennes['md5']} (md5). Le fichier a change depuis l'import.")
            anomalies += 1
            continue

        candidats = index_source.get(nom, [])
        if not candidats:
            print(f"  SOURCE INTROUVABLE {nom} : aucun fichier de ce nom dans la copie fournie.")
            anomalies += 1
            continue

        identique = None
        for candidat in candidats:
            if empreinte(candidat)["md5"] == miennes["md5"]:
                identique = candidat
                break
        if identique is None:
            print(f"  CONTENU DIFFERENT  {nom} : present dans la source, mais pas les memes octets.")
            anomalies += 1
            continue

        print(f"  OK               {nom}  {declaree}  {octets(ici):>9} octets  "
              f"<- {os.path.relpath(identique, source)}")

    if algo_retenu:
        print(f"  (empreinte du manifeste : {'/'.join(sorted(algo_retenu))}, "
              f"12 premiers caracteres)")
    else:
        print("  ATTENTION : aucune empreinte du manifeste ne se retrouve. "
              "Le manifeste n'a pas ete produit par ce corpus.")
        anomalies += 1
    return anomalies


def verifier_pages(source: str) -> int:
    """Les pages du moushaf, une par une. Rend le nombre d'anomalies."""
    dossier_source = os.path.join(source, PAGES_SOURCE)
    if not os.path.isdir(PAGES_PORT):
        print(f"  PAGES ABSENTES DU PORT : {PAGES_PORT}")
        return 1
    if not os.path.isdir(dossier_source):
        print(f"  PAGES ABSENTES DE LA SOURCE : {dossier_source}")
        return 1

    noms_port = sorted(n for n in os.listdir(PAGES_PORT) if n.endswith(".png"))
    noms_source = sorted(n for n in os.listdir(dossier_source) if n.endswith(".png"))
    print(f"--- pages du moushaf ({len(noms_port)} dans le port, {len(noms_source)} dans la source) ---")

    anomalies = 0
    total = 0
    for nom in noms_port:
        chemin = os.path.join(PAGES_PORT, nom)
        total += octets(chemin)
        jumeau = os.path.join(dossier_source, nom)
        if not os.path.exists(jumeau):
            print(f"  SANS JUMEAU      {nom} : presente dans le port, absente de la source.")
            anomalies += 1
            continue
        if empreinte(chemin)["md5"] != empreinte(jumeau)["md5"]:
            print(f"  CONTENU DIFFERENT {nom}")
            anomalies += 1

    for nom in noms_source:
        if nom not in set(noms_port):
            print(f"  MANQUANTE        {nom} : presente dans la source, absente du port.")
            anomalies += 1

    print(f"  {len(noms_port)} pages, {total} octets ({total / 1e6:.2f} Mo)")

    # Ce que la source porte et que le portage a **decide** de ne pas reprendre. Le dire ici
    # evite qu'un prochain import le prenne pour un oubli.
    non_importe = os.path.join(source, NON_IMPORTE)
    if os.path.isdir(non_importe):
        poids = sum(octets(os.path.join(non_importe, n)) for n in os.listdir(non_importe))
        print(f"  non importe, et c'est voulu : {NON_IMPORTE} "
              f"({len(os.listdir(non_importe))} fichiers, {poids / 1e6:.2f} Mo)")
    return anomalies


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True,
                        help="chemin d'une copie locale de coran-memoire (lecture seule)")
    args = parser.parse_args()

    source = os.path.abspath(args.source)
    if not os.path.isdir(source):
        sys.exit(f"Source introuvable : {source}")

    index_source = indexer(source)
    anomalies = verifier_donnees(source, index_source) + verifier_pages(source)

    print()
    if anomalies:
        print(f"VERDICT : {anomalies} anomalie(s). Les donnees embarquees ne sont pas celles de la source.")
        return 1
    print("VERDICT : les donnees embarquees sont identiques a celles de la source, a l'octet pres.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
