"""Falsifie des regles : mute la source, verifie que les bons tests tombent, restaure.

Pourquoi ce script existe
-------------------------

Un test vert ne prouve rien tant qu'on ne l'a pas vu rougir **pour la bonne raison**. La
falsification consiste a casser volontairement la regle, a lancer le test, et a verifier que
les tests qui tombent sont exactement ceux qui devaient tomber.

Trois pieges rendent la falsification manuelle peu fiable, et le script les traite :

  1. **la source reste mutee.** Si le script est interrompu — un test qui bloque, une erreur —
     la mutation survit dans l'arbre de travail, et le « vert » suivant est un faux. La
     restauration est donc dans un `finally`, et le script relit le fichier apres restauration
     pour comparer a l'empreinte d'origine ;
  2. **un rapport perime est compte.** Les rapports XML restent sur le disque d'une execution a
     l'autre. Un test supprime continue d'apparaitre vert. Seuls les rapports **modifies apres
     le lancement** sont donc lus, et leur horodatage vient du systeme de fichiers ;
  3. **un test qui ne s'execute pas compte comme un succes.** `node --test` sort en 0 quand le
     motif ne designe aucun test ; cote Gradle, un filtre trop etroit donne un « BUILD
     SUCCESSFUL » sans avoir rien joue. Le script exige donc que la mutation fasse tomber
     **au moins un** test, et refuse un resultat vide.

Usage
-----

    python tools/falsifier.py                 # joue tous les cas
    python tools/falsifier.py --cas bande     # joue les cas dont le nom contient « bande »
    python tools/falsifier.py --verifier      # ne joue rien : verifie que les cas sont jouables

Les cas sont decrits dans CAS, plus bas : chaque cas nomme le fichier, le texte exact a
remplacer, le texte de remplacement, la tache Gradle, et les tests qui doivent tomber.

Deux choses que ce harnais **ne peut pas** faire, et qu'il faut savoir
----------------------------------------------------------------------

**Un `kill -9` en pleine mutation laisse la source mutee.** La restauration vit dans un
`finally`, qui ne s'execute pas si le processus est tue sans recours. La mutation est donc
**ecrite avant d'etre appliquee** et la sortie est ligne par ligne : si le harnais s'arrete
net, la derniere mutation annoncee dit exactement quoi remettre, et `--verifier` dit ensuite
quel cas est casse. Un arret propre (Ctrl-C compris) passe par le `finally` et restaure.

**La preuve de restauration est l'empreinte du fichier, pas la bonne volonte du `finally`.**
Chaque cas relit son fichier apres restauration et le compare a l'octet pres a ce qu'il a lu
avant. C'est cette comparaison-la qui compte : chercher la sous-chaine de la mutation dans le
fichier ne prouve rien, puisque la mutation peut etre une sous-chaine de l'original — c'est le
cas de « transition : la validation passe avant la preparation », ou `commit(source, page)`
existe dans les deux versions.
"""

from __future__ import annotations

import argparse
import glob
import os
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

# La racine du depot est deduite de l'emplacement de ce fichier : le harnais doit tourner sur
# n'importe quelle machine, et un chemin absolu en dur ne survit pas au premier clone.
PROJET = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JDK = os.environ.get("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr"


# --------------------------------------------------------------------------- les cas

#: Chaque cas : nom, fichier (chemin relatif au projet), `avant` (texte exact, present une seule
#: fois), `apres`, `tache` Gradle, `attendus` (sous-chaines des noms de tests qui doivent tomber).
CAS: list[dict] = [
    {
        "nom": "bande : la hauteur d'une bande n'est plus le rapport de l'image",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/MushafPageGeometry.kt",
        "avant": "fun bandHeight(innerWidth: Float): Float = innerWidth * LINE_ASPECT",
        "apres": "fun bandHeight(innerWidth: Float): Float = innerWidth * LINE_ASPECT * 2f",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["MushafPageGeometryTest"],
    },
    {
        "nom": "bande : le pas ignore la hauteur de bande (les bandes ne se chevauchent plus)",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/MushafPageGeometry.kt",
        "avant": "val step = (innerHeight - bandHeight) / (count - 1)",
        "apres": "val step = innerHeight / (count - 1)",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["MushafPageGeometryTest"],
    },
    {
        "nom": "forme : toute source prend la forme du paquet",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/MushafPageShape.kt",
        "avant": "if (!QuranSourceReady.isZipSource(source)) return EMBEDDED",
        "apres": "if (false) return EMBEDDED",
        "tache": ":core:domain:test",
        "attendus": ["MushafPageShapeTest"],
    },
    {
        # Les deux garde-fous de `MushafPageShape.of` — bornes et seuil `> 1.0` — se recouvrent
        # **exactement** sur le referentiel livre : `coran_1441-dimensions.json` porte 604 cles,
        # les pages 1 a 604, une seule valeur (1440, 2320). Mesure : neutraliser l'un ou l'autre
        # ne fait tomber **aucun** test, et c'est ecrit dans le KDoc de la regle. Un cas de
        # falsification qui ne change rien d'observable doit etre retire, pas garde pour faire
        # nombre. Ce qui est reellement eprouvable ici est le **choix** des dimensions.
        "nom": "forme : la largeur et la hauteur sont echangees",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/MushafPageShape.kt",
        "avant": "ReaderLayout.Size(data.width, data.height)",
        "apres": "ReaderLayout.Size(data.height, data.width)",
        "tache": ":core:domain:test",
        "attendus": ["MushafPageShapeTest"],
    },
    {
        "nom": "forme : le paquet n'annonce plus quinze lignes par page",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/MushafPageShape.kt",
        "avant": "if (QuranSourceReady.isZipSource(source)) ZIP_LINES_PER_PAGE else 1",
        "apres": "if (QuranSourceReady.isZipSource(source)) 1 else 1",
        "tache": ":core:domain:test",
        "attendus": ["MushafPageShapeTest"],
    },
    {
        "nom": "temoins : le paquet n'est plus reconnu comme une source telechargee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranSourceReady.kt",
        "avant": "fun isZipSource(source: MushafSource): Boolean = source == MushafSource.CORAN_1441",
        "apres": "fun isZipSource(source: MushafSource): Boolean = source == MushafSource.MEDINA",
        "tache": ":core:domain:test",
        "attendus": ["QuranSourceReadyTest", "MushafPageShapeTest"],
    },
    {
        "nom": "refus : le manque de paquet ne previent plus",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranSourceReady.kt",
        "avant": "        PageReadiness.DownloadNeeded -> QuranDownloadText.NOT_INSTALLED\n",
        "apres": "        PageReadiness.DownloadNeeded -> null\n",
        "tache": ":core:domain:test",
        "attendus": ["QuranSourceReadyTest"],
    },
    {
        "nom": "libelles : le message de paquet absent redevient une apostrophe droite",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranDownloadText.kt",
        "avant": 'const val NOT_INSTALLED = "Le paquet « Coran 1441 » n’est pas encore installé."',
        "apres": 'const val NOT_INSTALLED = "Le paquet « Coran 1441 » n\'est pas encore installé."',
        "tache": ":core:domain:test",
        "attendus": ["QuranDownloadTextTest"],
    },
    {
        "nom": "boutons : une pause est proposee pendant l'ecriture des pages",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranDownloadText.kt",
        "avant": "fun showsPause(phase: ArchivePhase): Boolean = phase == ArchivePhase.DOWNLOADING",
        "apres": "fun showsPause(phase: ArchivePhase): Boolean = ArchiveProgress(phase, 0f).isBusy",
        "tache": ":core:domain:test",
        "attendus": ["QuranDownloadTextTest"],
    },
    {
        "nom": "boutons : l'action reste proposee pendant l'ecriture des pages",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranDownloadText.kt",
        "avant": "fun showsAction(phase: ArchivePhase): Boolean = !ArchiveProgress(phase, 0f).isBusy",
        "apres": "fun showsAction(phase: ArchivePhase): Boolean = true",
        "tache": ":core:domain:test",
        "attendus": ["QuranDownloadTextTest"],
    },
    {
        "nom": "avancement : l'etiquette est annoncee meme sans travail",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranDownloadText.kt",
        "avant": "if (!ArchiveProgress(phase, progress).isBusy) return null",
        "apres": "if (false) return null",
        "tache": ":core:domain:test",
        "attendus": ["QuranDownloadTextTest"],
    },
    {
        "nom": "transition : la validation passe avant la preparation",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranSourceTransition.kt",
        "avant": "prepare(source, page)",
        "apres": "commit(source, page)",
        "tache": ":core:domain:test",
        "attendus": ["QuranSourceTransitionTest"],
    },
    {
        # `mutex.lock()` rendrait `Unit` la ou la fonction rend un `Boolean` : la mutation ne
        # compilerait pas, aucun test ne serait joue, et le harnais crierait « faux » pour la
        # mauvaise raison. On retire donc le refus sans changer le type de retour.
        "nom": "transition : une seconde transition n'est plus refusee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranSourceTransition.kt",
        "avant": "if (!mutex.tryLock()) return false",
        "apres": "if (false) return false",
        "tache": ":core:domain:test",
        "attendus": ["QuranSourceTransitionTest"],
    },
    {
        "nom": "pages : le paquet ne decrit plus quinze bandes",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/MushafPageSources.kt",
        "avant": "lines = (1..ZIP_LINES_PER_PAGE).map { line -> uri(page, line) },",
        "apres": "lines = listOf(uri(page, 1)),",
        "tache": ":core:domain:test",
        "attendus": ["MushafPageSourcesTest"],
    },
    {
        "nom": "rectangles : une page inconnue leve au lieu de rendre une liste vide",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ZipQuranSource.kt",
        "avant": "        (bounds[page] ?: emptyList()).map { row ->",
        "apres": "        bounds.getValue(page).map { row ->",
        "tache": ":core:domain:test",
        "attendus": ["ZipQuranSourceTest"],
    },
    {
        "nom": "transition : la validation n'est plus executee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/QuranSourceTransition.kt",
        "avant": "            commit(source, page)\n            return true",
        "apres": "            run { }\n            return true",
        "tache": ":core:domain:test",
        "attendus": ["QuranSourceTransitionTest"],
    },
    {
        # Le cas le plus important du lot : il se joue **la classe seule**. Une classe qui passe
        # seulement parce qu'une autre a installé le référentiel avant elle n'est pas verte, elle
        # est chanceuse — et le vert dépend alors de l'ordre de découverte des classes, qui
        # change quand on ajoute un fichier de test. C'est exactement ce qui s'est produit ici.
        "nom": "fixture : la classe jouee seule n'installe plus le referentiel",
        "fichier": "core/domain/src/test/kotlin/com/msoumaya/deepseekandroid/core/domain/ZipQuranSourceTest.kt",
        "avant": "fun setUp() = QuranFixture.install()",
        "apres": "fun setUp() = Unit",
        "tache": ":core:domain:test --tests *ZipQuranSourceTest*",
        "attendus": ["ZipQuranSourceTest"],
    },
    {
        "nom": "dernier indice : une page sans ligne annonce zero au lieu de rien",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ZipQuranSource.kt",
        "avant": "fun lastLineIndex(page: Int): Int? = (bounds[page] ?: emptyList()).maxOfOrNull { it.line }",
        "apres": "fun lastLineIndex(page: Int): Int? = (bounds[page] ?: emptyList()).maxOfOrNull { it.line } ?: 0",
        "tache": ":core:domain:test",
        "attendus": ["ZipQuranSourceTest"],
    },
    {
        # La regle existe pour une seule raison : ne pas offrir une ligne dont l'ecran n'est pas
        # ecrit. Si le filtre ne filtre plus, la feuille propose « Traduction francaise » et
        # « Reglages audio », on les touche, et rien ne se passe. Le cas mesure exactement ca.
        "nom": "options : le filtre ne retire plus les destinations absentes",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderOptionsText.kt",
        "avant": "fun visible(available: Set<Action>): List<Row> = ALL.filter { it.action in available }",
        "apres": "fun visible(available: Set<Action>): List<Row> = ALL",
        "tache": ":core:domain:test",
        "attendus": ["ReaderOptionsTextTest"],
    },
    {
        # Un `Set` ne promet aucun ordre. Si `visible` suivait celui qu'on lui donne, la feuille
        # changerait d'ordre selon l'appelant, sans qu'aucun ecran ne le montre.
        "nom": "options : l'ordre suit l'ensemble recu au lieu de l'ordre d'origine",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderOptionsText.kt",
        "avant": "fun visible(available: Set<Action>): List<Row> = ALL.filter { it.action in available }",
        "apres": "fun visible(available: Set<Action>): List<Row> = available.mapNotNull { action -> ALL.firstOrNull { it.action == action } }",
        "tache": ":core:domain:test",
        "attendus": ["ReaderOptionsTextTest"],
    },
    {
        # Sans ce garde-fou, le lecteur ouvrirait une feuille reduite a son titre et a sa
        # poignee : une impasse.
        "nom": "options : la feuille s'ouvre meme sans aucune destination",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderOptionsText.kt",
        "avant": "fun isUseful(available: Set<Action>): Boolean = visible(available).isNotEmpty()",
        "apres": "fun isUseful(available: Set<Action>): Boolean = true",
        "tache": ":core:domain:test",
        "attendus": ["ReaderOptionsTextTest"],
    },
    {
        # « Corriger » l'apostrophe typographique pour une apostrophe droite : le genre de
        # retouche qui parait anodine et qui change un texte que l'utilisateur lit.
        "nom": "options : l'apostrophe du titre est remplacee par une apostrophe droite",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderOptionsText.kt",
        "avant": "const val TITLE: String = \"Plus d\u2019options\"",
        "apres": "const val TITLE: String = \"Plus d'options\"",
        "tache": ":core:domain:test",
        "attendus": ["ReaderOptionsTextTest"],
    },
    {
        # Remettre la lecture du nombre d'ecoutes sur la forme qui LEVE : c'est exactement
        # l'etat d'avant la correction. Le collecteur qui conduit la seance meurt en silence et
        # la lecon se fige sans message. Le banc du domaine doit le voir.
        "nom": "audio : le moteur se remet a lever sur une saisie illisible",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/AudioSession.kt",
        "avant": "AudioCount.CUSTOM -> customCount.trim().toIntOrNull()?.takeIf { it > 0 } ?: 1",
        "apres": "AudioCount.CUSTOM -> countChoice.resolve(customCount)",
        "tache": ":core:domain:test",
        "attendus": ["AudioSessionTest"],
    },
    {
        # La MEME mutation, vue par le banc du moteur. Ce defaut ne se voyait pas dans un
        # rapport de domaine : il fallait une fin de verset pour le declencher, et l'exception
        # etait absorbee par la portee de coroutines. Ce cas est ce qui prouve que le banc du
        # controleur detecte la mort silencieuse du collecteur.
        "nom": "audio : la mort silencieuse du collecteur est vue par le banc du moteur",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/AudioSession.kt",
        "avant": "AudioCount.CUSTOM -> customCount.trim().toIntOrNull()?.takeIf { it > 0 } ?: 1",
        "apres": "AudioCount.CUSTOM -> countChoice.resolve(customCount)",
        "tache": ":core:audio:testDebugUnitTest",
        "attendus": ["AudioSessionControllerTest"],
    },
    {
        # Le repli inverse : une saisie illisible devient une repetition ILLIMITEE. C'est la
        # faute dangereuse — la seance ne s'arrete plus jamais, au lieu de s'arreter trop tot.
        "nom": "audio : une saisie illisible devient une repetition illimitee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/AudioSession.kt",
        "avant": "AudioCount.CUSTOM -> customCount.trim().toIntOrNull()?.takeIf { it > 0 } ?: 1",
        "apres": "AudioCount.CUSTOM -> customCount.trim().toIntOrNull()?.takeIf { it > 0 }",
        "tache": ":core:domain:test",
        "attendus": ["AudioSessionTest"],
    },
    {
        # Le titre de la feuille reprend l'en-tete du lecteur de poche au lieu du nom du bouton
        # qui l'ouvre : la personne qui appuie sur « Reglages audio » arrive sur un ecran qui
        # s'appelle autrement.
        "nom": "audio : le titre de la feuille ne suit plus la ligne qui l'ouvre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/AudioSettingsText.kt",
        "avant": "const val TITLE: String = \"Réglages audio\"",
        "apres": "const val TITLE: String = \"Écouter un récitateur\"",
        "tache": ":core:domain:test",
        "attendus": ["AudioSettingsTextTest"],
    },
    {
        # L'ecran cesse de refuser : il valide une saisie libre qu'il n'a pas lue.
        "nom": "audio : l'ecran ne refuse plus une saisie libre illisible",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/AudioSession.kt",
        "avant": "runCatching { settings.countChoice.resolve(settings.customCount) }",
        "apres": "runCatching { settings.countChoice.resolve(\"20\") }",
        "tache": ":core:domain:test",
        "attendus": ["AudioSessionTest"],
    },
    {
        # Le controleur ne retient plus que la vitesse : les autres reglages n'atteignent plus
        # la seance en cours, alors que l'ecran vient de les annoncer.
        "nom": "audio : les reglages ne s'appliquent plus a la seance en cours",
        "fichier": "core/audio/src/main/kotlin/com/msoumaya/deepseekandroid/core/audio/AudioSessionController.kt",
        "avant": "        settings = value",
        "apres": "        settings = settings.copy(speed = value.speed)",
        "tache": ":core:audio:testDebugUnitTest",
        "attendus": ["AudioSessionControllerTest"],
    },
]


# --------------------------------------------------------------------------- l'outillage


def empreinte(chemin: str) -> bytes:
    with open(chemin, "rb") as fichier:
        return fichier.read()


def commande_gradle() -> list[str]:
    """Le lanceur Gradle de la plateforme.

    Sous Windows, `gradlew.bat` doit passer par `cmd.exe` ; ailleurs, `./gradlew` s'appelle
    directement. Le harnais tourne en local, mais un outil qui ne marche que sur la machine de
    celui qui l'a ecrit est un outil qu'on ne relance pas.
    """
    if os.name == "nt":
        return ["cmd", "/c", "gradlew.bat"]
    return [os.path.join(PROJET, "gradlew")]


def lancer(tache: str) -> tuple[int, str]:
    """Joue une tache Gradle. `tache` peut porter des options (`--tests ...`)."""
    env = dict(os.environ)
    env["JAVA_HOME"] = JDK
    env["PATH"] = os.path.join(JDK, "bin") + os.pathsep + env.get("PATH", "")
    resultat = subprocess.run(
        commande_gradle() + shlex.split(tache) + ["--max-workers=1", "-Dorg.gradle.parallel=false"],
        cwd=PROJET,
        env=env,
        capture_output=True,
        text=True,
    )
    return resultat.returncode, (resultat.stdout or "") + (resultat.stderr or "")


def echecs_depuis(instant: float) -> list[str]:
    """Les noms des tests en echec, lus dans les rapports ecrits apres `instant`.

    L'horodatage est celui du systeme de fichiers : c'est la seule source qui dise *quand* un
    rapport a ete ecrit. Un rapport plus ancien appartient a une execution precedente et son
    verdict ne vaut rien ici.
    """
    tombes: list[str] = []
    for chemin in glob.glob(os.path.join(PROJET, "**/build/test-results/**/TEST-*.xml"), recursive=True):
        if os.path.getmtime(chemin) < instant:
            continue
        try:
            racine = ET.parse(chemin).getroot()
        except ET.ParseError:
            continue
        if racine.tag != "testsuite":
            continue
        classe = racine.get("name", chemin)
        for cas in racine.findall("testcase"):
            if cas.find("failure") is not None or cas.find("error") is not None:
                tombes.append(f"{classe}.{cas.get('name')}")
    return sorted(tombes)


def precondition(cas: dict) -> str | None:
    """Ce qui empeche de jouer ce cas, ou `None` s'il est jouable.

    Un cas n'est jouable que si son texte a remplacer est present **une seule fois**. Zero fois
    veut dire que la source a bouge et que le cas ne falsifie plus rien ; deux fois veut dire que
    le remplacement toucherait un endroit qu'on n'a pas choisi. Dans les deux cas la tache
    Gradle tournerait, mais elle ne prouverait pas ce que le cas annonce — d'ou ce controle
    **avant** de depenser une execution.
    """
    chemin = os.path.join(PROJET, cas["fichier"].replace("/", os.sep))
    if not os.path.exists(chemin):
        return f"fichier introuvable : {cas['fichier']}"
    occurrences = open(chemin, encoding="utf-8").read().count(cas["avant"])
    if occurrences != 1:
        return (f"le texte a remplacer apparait {occurrences} fois (1 attendu) "
                f"dans {cas['fichier']} : {cas['avant'].strip()!r}")
    return None


def verifier_cas(choisis: list[dict]) -> int:
    """Ne joue rien : dit seulement quels cas sont encore jouables."""
    casses = 0
    for cas in choisis:
        probleme = precondition(cas)
        if probleme is None:
            print(f"  jouable   {cas['nom']}")
        else:
            print(f"  CASSE     {cas['nom']}\n            {probleme}")
            casses += 1
    print()
    print(f"VERDICT : {len(choisis) - casses}/{len(choisis)} cas jouable(s)")
    return 1 if casses else 0


def jouer(cas: dict) -> bool:
    chemin = os.path.join(PROJET, cas["fichier"].replace("/", os.sep))
    avant = cas["avant"]
    apres = cas["apres"]
    origine = empreinte(chemin)
    texte = origine.decode("utf-8")

    probleme = precondition(cas)
    if probleme is not None:
        print(f"  ECHEC DU HARNAIS : {probleme}")
        return False

    print(f"  mutation : {avant.strip()!r}")
    print(f"         -> : {apres.strip()!r}")

    instant = time.time()
    try:
        with open(chemin, "w", encoding="utf-8", newline="") as fichier:
            fichier.write(texte.replace(avant, apres, 1))
        code, sortie = lancer(cas["tache"])
    finally:
        with open(chemin, "wb") as fichier:
            fichier.write(origine)

    # La restauration est verifiee par l'empreinte, pas par la bonne volonte du `finally`.
    if empreinte(chemin) != origine:
        print("  DANGER : la source n'a pas ete restauree a l'identique !")
        return False

    tombes = echecs_depuis(instant)
    if not tombes:
        print("  FAUX : aucun test n'est tombe. Le test ne couvre pas cette regle,")
        print("         ou la tache n'a rien joue (compilation en echec, filtre trop etroit).")
        print("  --- fin de sortie Gradle ---")
        for ligne in sortie.strip().splitlines()[-12:]:
            print("   " + ligne)
        return False

    correspond = [t for t in tombes if any(a in t for a in cas["attendus"])]
    if not correspond:
        print(f"  FAUX : les tests tombes ne sont pas ceux attendus ({len(tombes)} tombes).")
        for t in tombes:
            print(f"    - {t}")
        return False

    print(f"  OK : {len(tombes)} test(s) tombe(s), dont {len(correspond)} attendu(s) :")
    for t in tombes:
        print(f"    - {t}")
    return True


def main() -> int:
    # Ligne par ligne, et pas en tampon. Un lancement complet dure une vingtaine de minutes : sans
    # cela, rien n'apparait avant la fin, et la seule trace utile en cas d'arret brutal — la
    # mutation en cours — est justement celle qui resterait coincee dans le tampon.
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except (AttributeError, OSError):  # pragma: no cover - depends de l'implementation
        pass

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cas", default="", help="filtre sur le nom des cas")
    parser.add_argument("--verifier", action="store_true",
                        help="ne joue rien : verifie seulement que chaque cas est jouable")
    args = parser.parse_args()

    choisis = [c for c in CAS if args.cas in c["nom"]]
    if not choisis:
        sys.exit(f"Aucun cas ne correspond a {args.cas!r}.")

    if args.verifier:
        print(f"{len(choisis)} cas a verifier.")
        return verifier_cas(choisis)

    print(f"{len(choisis)} cas a falsifier.")
    reussis = 0
    for numero, cas in enumerate(choisis, 1):
        print()
        print(f"[{numero}/{len(choisis)}] {cas['nom']}")
        if jouer(cas):
            reussis += 1

    print()
    print(f"VERDICT : {reussis}/{len(choisis)} falsification(s) concluante(s)")
    return 0 if reussis == len(choisis) else 1


if __name__ == "__main__":
    raise SystemExit(main())
