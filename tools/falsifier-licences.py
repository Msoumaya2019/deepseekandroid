"""Eprouve `tools/verifier-licences.py` : chaque famille doit pouvoir passer au ROUGE.

Pourquoi ce script existe
-------------------------

Un controle qui reste vert ne prouve rien tant qu'on ne l'a pas vu rougir sur le defaut qu'il
pretend couvrir. Les quatre familles du verificateur sont independantes, et chacune a donc son
temoin :

  A. une ancre disparait du `NOTICE.md`      -> la couverture doit tomber ;
  B. une page disparait de `provenance.json` -> la provenance doit tomber ;
  C. une police de page perd `COLR`/`CPAL`   -> l'invariant couleur doit tomber ;
  D. une dependance publicitaire apparait    -> la condition 1 doit tomber.

Le temoin **negatif** compte autant : sur une copie intacte, le verificateur doit sortir en 0.
Sans lui, un verificateur qui echouerait toujours passerait les quatre mutations.

Tout se fait sur des **copies** dans un dossier temporaire : l'arbre reel n'est jamais mute. C'est
la raison d'etre de l'option `--projet` du verificateur.

Le temoin de C est une police **deja livree** — `surah-name-v4.woff2`, la police des titres, qui
ne porte ni `COLR` ni `CPAL`. Prendre un fichier du depot plutot qu'un fichier exterieur rend ce
falsificateur autonome : il n'a besoin ni du depot d'origine, ni d'un reseau.

Usage
-----

    python tools/falsifier-licences.py

Sortie 0 si les cinq cas se comportent comme attendu, 1 sinon.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile

RACINE = os.path.dirname(os.path.abspath(__file__))
DEPOT = os.path.dirname(RACINE)
VERIFICATEUR = os.path.join(RACINE, "verifier-licences.py")
PYTHON = sys.executable

#: La police des titres de sourate : livree, et **sans** tables couleur. Mesure : elle porte
#: `GSUB`, `cmap`, `glyf` et rien qui ressemble a `COLR`/`CPAL` — c'est ce qui en fait un temoin.
POLICE_NON_COULEUR = os.path.join(
    DEPOT, "app", "src", "main", "assets", "coran-test", "surah-name-v4.woff2")

#: Ce qu'il faut copier pour que le verificateur ait de quoi travailler. Copier tout le depot
#: trainerait `build/` et `.git/` pour rien : le verificateur ne lit que ces chemins.
A_COPIER = [
    "NOTICE.md",
    os.path.join("gradle", "libs.versions.toml"),
    os.path.join("app", "src", "main", "assets", "coran-test"),
    os.path.join("core", "domain", "src", "main", "resources", "quran", "TANZIL-LICENSE.txt"),
    os.path.join("core", "design", "src", "main", "assets", "licenses"),
]


def copier_arbre(destination: str) -> None:
    """Copie les chemins lus par le verificateur, plus tous les `*.gradle.kts` du depot."""
    for relatif in A_COPIER:
        source = os.path.join(DEPOT, relatif)
        cible = os.path.join(destination, relatif)
        if os.path.isdir(source):
            shutil.copytree(source, cible)
        else:
            os.makedirs(os.path.dirname(cible), exist_ok=True)
            shutil.copy2(source, cible)

    for racine, dossiers, noms in os.walk(DEPOT):
        dossiers[:] = [d for d in dossiers if d not in ("build", ".git")]
        for nom in noms:
            if not nom.endswith(".gradle.kts"):
                continue
            source = os.path.join(racine, nom)
            relatif = os.path.relpath(source, DEPOT)
            cible = os.path.join(destination, relatif)
            os.makedirs(os.path.dirname(cible), exist_ok=True)
            shutil.copy2(source, cible)


def lancer(racine: str) -> tuple[int, str]:
    """Lance le verificateur sur `racine` et rend (code de sortie, sortie complete)."""
    resultat = subprocess.run(
        [PYTHON, VERIFICATEUR, "--projet", racine],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    return resultat.returncode, (resultat.stdout or "") + (resultat.stderr or "")


def remplacer(chemin: str, avant: str, apres: str, occurrences: int | None = 1) -> None:
    """Remplace `avant` par `apres` dans `chemin`, en verifiant le nombre d'occurrences.

    Le compte est exige parce qu'une mutation doit etre **celle qu'on croit** : remplacer
    silencieusement une occurrence parmi trois laisserait l'ancre en place, et le cas passerait
    pour un harnais defectueux au lieu d'un controle qui ne mord pas. `occurrences=None` demande
    « au moins une, toutes » — utile quand l'ancre visee est citee plusieurs fois.
    """
    with open(chemin, encoding="utf-8") as f:
        texte = f.read()
    trouvees = texte.count(avant)
    if occurrences is None:
        if trouvees < 1:
            raise AssertionError(f"{chemin} : « {avant[:40]} » est absent, aucune mutation possible")
    elif trouvees != occurrences:
        raise AssertionError(
            f"{chemin} : « {avant[:40]} » apparait {trouvees} fois, attendu {occurrences}")
    with open(chemin, "w", encoding="utf-8", newline="") as f:
        f.write(texte.replace(avant, apres))


def cas_temoin(racine: str) -> tuple[bool, str]:
    """Sur une copie intacte, le verificateur doit sortir en 0."""
    code, sortie = lancer(racine)
    if code == 0:
        return True, "vert sur une copie intacte"
    return False, f"ROUGE sur une copie intacte (code {code})\n{sortie}"


def cas_couverture(racine: str) -> tuple[bool, str]:
    """A. Retirer l'ancre « KFGQPC » du NOTICE : la couverture doit tomber.

    L'ancre est citee trois fois — dans les conditions recopiees, dans leur condition 2 et dans
    les references. Les trois disparaissent : c'est l'ancre qui doit manquer, pas une occurrence.
    """
    notice = os.path.join(racine, "NOTICE.md")
    remplacer(notice, "KFGQPC", "l'editeur", occurrences=None)
    code, sortie = lancer(racine)
    if code == 1 and "KFGQPC" in sortie:
        return True, "rouge, et l'ecart nomme l'ancre manquante"
    return False, f"attendu rouge avec l'ancre nommee, obtenu code {code}\n{sortie}"


def cas_provenance(racine: str) -> tuple[bool, str]:
    """B. Retirer la page 300 de la provenance : la couverture doit tomber."""
    chemin = os.path.join(racine, "app", "src", "main", "assets", "coran-test", "provenance.json")
    with open(chemin, encoding="utf-8") as f:
        donnees = json.load(f)
    cle = "fonts/quran_fonts_qfc4/QCF4300_COLOR-Regular.ttf.gz"
    if cle not in donnees["resources"]:
        return False, f"la cle temoin {cle!r} est absente de la provenance livree"
    del donnees["resources"][cle]
    with open(chemin, "w", encoding="utf-8", newline="") as f:
        json.dump(donnees, f)
    code, sortie = lancer(racine)
    if code == 1 and "300" in sortie:
        return True, "rouge, et l'ecart nomme la page decouverte"
    return False, f"attendu rouge avec la page nommee, obtenu code {code}\n{sortie}"


def cas_couleur(racine: str) -> tuple[bool, str]:
    """C. Mettre a la place de la page 300 une police sans COLR/CPAL : l'invariant doit tomber."""
    if not os.path.exists(POLICE_NON_COULEUR):
        return False, f"temoin introuvable : {POLICE_NON_COULEUR}"
    cible = os.path.join(racine, "app", "src", "main", "assets", "coran-test", "300.woff2")
    shutil.copy2(POLICE_NON_COULEUR, cible)
    code, sortie = lancer(racine)
    if code == 1 and "COLR" in sortie and "300" in sortie:
        return True, "rouge, et l'ecart nomme la police sans tables couleur"
    return False, f"attendu rouge avec la police nommee, obtenu code {code}\n{sortie}"


def cas_publicite(racine: str) -> tuple[bool, str]:
    """D. Ajouter une dependance publicitaire : la condition 1 doit tomber."""
    chemin = os.path.join(racine, "gradle", "libs.versions.toml")
    remplacer(chemin, "[libraries]",
              '[libraries]\nregie = { module = "com.google.android.gms:play-services-ads", '
              'version = "24.0.0" }')
    code, sortie = lancer(racine)
    if code == 1 and "publicitaire" in sortie:
        return True, "rouge, et l'ecart nomme la dependance publicitaire"
    return False, f"attendu rouge avec la regie nommee, obtenu code {code}\n{sortie}"


CAS = [
    ("temoin : copie intacte", cas_temoin),
    ("A. couverture : une ancre du NOTICE disparait", cas_couverture),
    ("B. provenance : une page disparait de provenance.json", cas_provenance),
    ("C. couleur : une police de page perd COLR/CPAL", cas_couleur),
    ("D. publicite : une dependance publicitaire apparait", cas_publicite),
]


def main() -> int:
    if not os.path.exists(VERIFICATEUR):
        print(f"verificateur introuvable : {VERIFICATEUR}")
        return 1

    resultats: list[tuple[str, bool, str]] = []
    for nom, travail in CAS:
        with tempfile.TemporaryDirectory(prefix="falsif-licences-") as temporaire:
            copie = os.path.join(temporaire, "arbre")
            copier_arbre(copie)
            try:
                ok, detail = travail(copie)
            except Exception as erreur:  # un cas qui leve est un cas qui n'a pas conclu
                ok, detail = False, f"HARNAIS : {type(erreur).__name__} — {erreur}"
        resultats.append((nom, ok, detail))
        print(f"[{'CONCLUANT' if ok else 'ECHEC     '}] {nom}")
        print(f"            {detail.splitlines()[0]}")

    conclus = sum(1 for _, ok, _ in resultats if ok)
    print(f"\n{conclus}/{len(resultats)} cas concluants.")
    for nom, ok, detail in resultats:
        if not ok:
            print(f"\n--- {nom} ---\n{detail}")
    return 0 if conclus == len(resultats) else 1


if __name__ == "__main__":
    sys.exit(main())
