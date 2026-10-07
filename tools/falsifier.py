"""Falsifie des regles : mute la source, verifie que les bons tests tombent, restaure.

Pourquoi ce script existe
-------------------------

Un test vert ne prouve rien tant qu'on ne l'a pas vu rougir **pour la bonne raison**. La
falsification consiste a casser volontairement la regle, a lancer le test, et a verifier que
les tests qui tombent sont exactement ceux qui devaient tomber.

Quatre pieges rendent la falsification manuelle peu fiable, et le script les traite :

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
     **au moins un** test, et refuse un resultat vide ;
  4. **un verdict du cache passe pour un verdict du jour.** Une tache de test que Gradle sert
     sans la rejouer — `FROM-CACHE`, `UP-TO-DATE` — rend le resultat d'une execution
     **precedente** : le rapport XML n'est pas reecrit, donc rien ne tombe, et le harnais
     accuserait le test de ne pas couvrir la regle alors qu'il n'a pas tourne. La tache nommee
     est donc **toujours** rejouee (`--rerun`) ; et si malgre tout Gradle annonce l'un de ces
     etats, le cas est declare **non concluant** — jamais « faux ».

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
    {
        # La relecture n'est plus branchee au demarrage : le lecteur part des valeurs par defaut,
        # et le premier reglage ecrase le choix enregistre sans que rien ne le dise. Aucun banc de
        # comportement ne peut le voir — le conteneur ne se construit qu'avec un `Context`
        # Android — c'est donc le controle de forme qui porte cette regle.
        "nom": "reglages : la relecture au demarrage n'est plus branchee",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/AppContainer.kt",
        "avant": "        scope.launch { audioSettings.prime() }",
        "apres": "        scope.launch { }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AppContainerWiringTest"],
    },
    {
        # Le document repasse dans un dossier de compte : changer de compte changerait de facon
        # d'ecouter, alors qu'elle tient a l'appareil et a l'oreille de celui qui le tient.
        "nom": "reglages : le document repasse dans un dossier de compte",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/AppContainer.kt",
        "avant": "File(root, \"audio.json\")",
        "apres": "File(root, \"accounts/audio.json\")",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AppContainerWiringTest"],
    },
    {
        # Le recitateur enregistre n'est plus relu : le lecteur repart du premier de la liste, et
        # le prochain reglage enregistre ce defaut a la place du choix.
        "nom": "reglages : le recitateur enregistre n'est plus relu",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/AudioSettingsRepository.kt",
        "avant": "_reciterId.value = stored.reciterId",
        "apres": "_reciterId.value = null",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AudioSettingsRepositoryTest"],
    },
    {
        # Une seule ecriture pour les deux morceaux, et le recitateur est oublie : un changement
        # de recitateur ne survit plus au redemarrage.
        "nom": "reglages : l'ecriture n'emporte plus le recitateur",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/AudioSettingsRepository.kt",
        "avant": "it.copy(repeat = session.stored(), reciterId = reciterId, reciterOwnerId = ownerId)",
        "apres": "it.copy(repeat = session.stored())",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AudioSettingsRepositoryTest"],
    },
    {
        # L'echec d'ecriture est avale et l'etat est publie quand meme : la feuille annonce
        # « enregistre » pour un document qui n'existe pas.
        "nom": "reglages : un echec d'ecriture est avale et l'etat publie quand meme",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/AudioSettingsRepository.kt",
        "avant": "        store.update { it.copy(repeat = session.stored(), reciterId = reciterId, reciterOwnerId = ownerId) }",
        "apres": "        runCatching { store.update { it.copy(repeat = session.stored(), reciterId = reciterId, reciterOwnerId = ownerId) } }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AudioSettingsRepositoryTest"],
    },
    {
        # La relecture ne tolere plus un champ hors bornes : une seule valeur abimee emporte tout
        # le document, et la personne perd ses reglages sans comprendre pourquoi. Le banc du
        # domaine le voit aussi ; c'est celui du depot qui est eprouve ici.
        "nom": "reglages : la relecture ne tolere plus un champ hors bornes",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/AudioSession.kt",
        "avant": "gapSeconds = stored.gap?.takeIf { it in AudioSettings.GAP_CHOICES }",
        "apres": "gapSeconds = stored.gap",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AudioSettingsRepositoryTest"],
    },
    {
        # La feuille d'options ne recoit plus de destination pour la traduction : la ligne
        # « Traduction francaise » disparait de la feuille, et rien d'autre ne le dit — le
        # domaine reste vert, la compilation passe, et le seul symptome est une ligne absente.
        "nom": "lecteur : la ligne de traduction n'ouvre plus le panneau",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "            onTranslation = { panel = ReaderPanel.TRANSLATION },",
        "apres": "            onTranslation = { panel = ReaderPanel.NONE },",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderPanelWiringTest"],
    },
    {
        # Les lignes du panneau ne viennent plus de la regle eprouvee : le panneau s'ouvre sur du
        # vide, ou sur autre chose que la plage de la page affichee.
        "nom": "lecteur : le panneau de traduction ne calcule plus ses lignes",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    .map { TranslationPanel.rows(session = null, page = it) }",
        "apres": "                    .map { listOf<TranslationPanel.Row>() }",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderPanelWiringTest"],
    },
    {
        # La destination existe encore, mais plus rien n'est rendu pour elle : le panneau
        # s'ouvrirait sur du vide. Les deux autres reglages restent verts.
        "nom": "lecteur : le panneau de traduction n'est plus rendu",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "            TranslationPanelSheet(rows = rows, onClose = { panel = ReaderPanel.NONE })",
        "apres": "            Unit",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderPanelWiringTest"],
    },
    {
        # Le marqueur pose par le professeur ne compte plus comme une difficulte : le verset
        # qu'il a justement designe disparait de l'ecran, et de la liste des reprises. Rien
        # d'autre ne le dit — le domaine reste vert pour tout le reste.
        "nom": "marques : le marqueur du professeur ne compte plus",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Review.kt",
        "avant": "        marker?.user != null || marker?.admin != null",
        "apres": "        marker?.user != null",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMarksTest"],
    },
    {
        # Le plan de reprise cesse de lire la regle nommee : il fabrique un ensemble vide. Les
        # versets difficiles ne sont plus jamais reproposes, et l'ensemble public, lui, reste
        # juste — c'est exactement l'ecart que le test doit voir.
        "nom": "marques : le plan de reprise ne lit plus la regle nommee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Review.kt",
        "avant": "        val difficult = difficultIds(state)",
        "apres": "        val difficult = emptySet<Int>()",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMarksTest"],
    },
    {
        # Le signet est lu dans la carte brute : la suppression logique est oubliee, et un
        # signet supprime revient colorer la page. Le marqueur `deletedAt` subsiste pourtant
        # dans l'etat, ce qui rend la faute invisible a tout autre controle.
        "nom": "marques : un signet supprime colore de nouveau la page",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Bookmarks.kt",
        "avant": "        visibleBookmarks(state).map { it.verseId }.toSet()",
        "apres": "        (state.bookmarks ?: emptyMap()).values.map { it.verseId }.toSet()",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMarksTest"],
    },
    {
        # L'ordre des priorites est inverse : un verset a la fois signet et en cours d'ecoute
        # change de couleur. C'est l'ecart entre les deux rendus de la source d'origine, et
        # aucun autre test ne le verrait — la page s'affiche, simplement pas de la meme teinte.
        "nom": "teintes : la lecture passe avant les deux marques",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderTint.kt",
        "avant": "        verseId in difficultIds -> TintKind.DIFFICULT\n"
                 "        verseId in bookmarkIds -> TintKind.BOOKMARK\n"
                 "        verseId == playingVerse -> TintKind.PLAYING",
        "apres": "        verseId == playingVerse -> TintKind.PLAYING\n"
                 "        verseId in difficultIds -> TintKind.DIFFICULT\n"
                 "        verseId in bookmarkIds -> TintKind.BOOKMARK",
        "tache": ":core:domain:test",
        "attendus": ["ReaderTintTest"],
    },
    {
        # La page ne recoit plus les signets : elle compile, et perd silencieusement ses
        # marques. Le parametre garde sa valeur par defaut, l'ensemble vide.
        "nom": "lecteur : la page ne recoit plus les signets",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    bookmarkIds = bookmarkIds,",
        "apres": "                    bookmarkIds = emptySet(),",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderScreenMarksTest"],
    },
    {
        # L'etat du compte n'est plus observe : les deux ensembles se calculent une fois pour
        # toutes, sur rien. Poser un signet ailleurs ne se verrait plus sans rouvrir le
        # lecteur, et la page ne porterait plus aucune marque.
        "nom": "route : l'etat du compte n'est plus observe",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "    val userState by container.userState.state.collectAsStateWithLifecycle(initialValue = null)",
        "apres": "    val userState: com.msoumaya.deepseekandroid.core.model.AppState? = null",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteMarksTest"],
    },
    {
        # La page du signet est prise dans le decoupage de Medine quoi qu'affiche : 56 versets
        # sur 6 236 changent de page entre les deux decoupages, donc pour ceux-la le signet
        # s'ouvre sur la page voisine. Aucun autre controle ne le voit — la page s'affiche,
        # simplement pas la bonne.
        "nom": "signets : la page du signet ignore la source affichee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Bookmarks.kt",
        "avant": "            source = source,",
        "apres": "            source = MushafSource.MEDINA,",
        "tache": ":core:domain:test",
        "attendus": ["BookmarksScreenRulesTest"],
    },
    {
        # « Derniere reprise » est lue en tete de liste au lieu d'etre retriee : le signet le
        # plus recemment MODIFIE est annonce comme le plus recemment REPRIS, alors qu'il ne l'a
        # jamais ete. Le domaine reste vert partout ailleurs.
        "nom": "signets : la derniere reprise est lue en tete de liste",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Bookmarks.kt",
        "avant": "        visibleBookmarks(state)\n"
                 "            .mapNotNull { item -> item.lastUsedAt?.let { item.verseId to it } }\n"
                 "            .maxByOrNull { it.second }\n"
                 "            ?.first",
        "apres": "        visibleBookmarks(state).firstOrNull()?.verseId",
        "tache": ":core:domain:test",
        "attendus": ["BookmarksScreenRulesTest"],
    },
    {
        # La garde de bornes saute : un signet venu d'un autre appareil, qui designe un verset
        # que ce referentiel ne connait pas, fait tomber la construction de l'ecran entier au
        # lieu d'etre omis. C'est exactement l'etat synchronise que rien d'autre ne valide.
        "nom": "signets : la ligne hors corpus n'est plus ecartee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Bookmarks.kt",
        "avant": "            if (id !in 1..Quran.verses.size) return@mapNotNull null",
        "apres": "            if (id == -1) return@mapNotNull null",
        "tache": ":core:domain:test",
        "attendus": ["BookmarksScreenRulesTest"],
    },
    {
        # La ligne reprend les champs enregistres du signet au lieu du verset : une etiquette
        # fausse s'affiche a cote du bon texte arabe. Le client d'origine fait pourtant ce
        # choix-la, et c'est un ecart assume — le test doit donc le tenir.
        "nom": "signets : la ligne suit les champs enregistres au lieu du verset",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Bookmarks.kt",
        "avant": "                surahName = Quran.surahAt(id).name,\n"
                 "                ayah = verse.ayah,",
        "apres": "                surahName = Quran.surahs.getOrNull(item.surah - 1)?.name ?: \"\",\n"
                 "                ayah = item.ayah,",
        "tache": ":core:domain:test",
        "attendus": ["BookmarksScreenRulesTest"],
    },
    {
        # La liste repasse par la carte brute : la suppression logique est oubliee, et un signet
        # supprime revient dans la liste. Le marqueur `deletedAt` subsiste pourtant dans l'etat,
        # ce qui rend la faute invisible a tout autre controle.
        "nom": "signets : un signet supprime reapparait dans la liste",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Bookmarks.kt",
        "avant": "        return visibleBookmarks(state).mapNotNull { item ->",
        "apres": "        return (state.bookmarks ?: emptyMap()).values.mapNotNull { item ->",
        "tache": ":core:domain:test",
        "attendus": ["BookmarksScreenRulesTest"],
    },
    {
        # Les cles de source se replient sur deux valeurs au lieu d'etre celles du format ecrit :
        # `sourcePages` devient ambigu, et un etat deja synchronise n'est plus relu au bon mot.
        # Le cas joue les DEUX modules : c'est ce qui prouve que l'accord entre l'identifiant du
        # paquet et la cle persistee est reellement tenu, et pas seulement ecrit.
        #
        # Deux regles de `Bookmarks.pageFor` n'ont volontairement PAS de cas, parce qu'elles ont
        # ete mesurees inertes : (1) la branche « conserver la page notee » de
        # `MushafSourceNavigation.versePage` est inatteignable, un verset n'occupant qu'une page
        # par decoupage dans les bornes livrees ; (2) remplacer `persistedKey` par le repli
        # `StudyProgressCalculator.sourceKey` rend le meme resultat pour toute source
        # atteignable. Un cas qui ne fait rien tomber est un faux, pas un cas de plus — c'est
        # l'hypothese (1) qui est mesuree par `BookmarksScreenRulesTest`.
        "nom": "signets : les cles de source se replient sur deux valeurs",
        "fichier": "core/model/src/main/kotlin/com/msoumaya/deepseekandroid/core/model/Enums.kt",
        "avant": "        get() = MushafSource.serializer().descriptor.getElementName(ordinal)",
        "apres": "        get() = if (this == SIMPLIFIED) \"tajweed\" else \"traditional\"",
        "tache": ":core:model:test :core:domain:test",
        "attendus": ["MushafSourceKeyTest", "BookmarksScreenRulesTest"],
    },
    {
        # Le mode arme n'est plus exige pour enregistrer : en lecture normale, chaque appui
        # poserait un signet au lieu de masquer la coquille. Le lecteur resterait utilisable, la
        # page defilerait — et le seul symptome serait une liste de signets qui se remplit toute
        # seule. Aucun autre controle ne le voit.
        "nom": "lecteur : le mode de pose n'est plus exige pour enregistrer",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "bookmarkMode && save != null -> {",
        "apres": "save != null -> {",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderBookmarkWiringTest"],
    },
    {
        # La garde saute : un appui sur le fond de la page — qui ne designe aucun verset — est
        # traite comme un verset, et un signet est pose sur le premier d'entre eux. Le mode se
        # referme en annoncant « Marque-page enregistre ».
        #
        # La mutation porte sur **deux** lignes, et c'est mesure : remplacer la seule garde par
        # `if (true) {` ne compile pas, `touched` restant de type `Int?` alors que `save` attend
        # un `Int`. Un cas qui ne compile pas mesure le compilateur, pas la regle — d'ou le
        # `?: 1`, qui rend la mutation compilable tout en supprimant la garde.
        "nom": "lecteur : un appui qui ne designe aucun verset enregistre quand meme",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "if (touched != null) {\n"
                 "                                            save(touched)",
        "apres": "if (true) {\n"
                 "                                            save(touched ?: 1)",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderBookmarkWiringTest"],
    },
    {
        # Le verset trouve n'est plus rapporte a l'appelant : rien n'est enregistre, et la notice
        # annonce pourtant la reussite. La page reste parfaitement utilisable, donc rien ne
        # pousse a chercher.
        "nom": "lecteur : le verset touche n'est plus rapporte",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "save(touched)",
        "apres": "Unit",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderBookmarkWiringTest"],
    },
    {
        # Armer le mode de pose sans refermer le panneau : le voile de la fenetre de dialogue
        # reste devant la page qu'on demande de toucher. Le mode est arme, et impossible a
        # satisfaire — l'impasse la plus probable de tout ce branchement.
        "nom": "lecteur : armer le mode de pose ne referme plus le panneau",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    panel = ReaderPanel.NONE\n                    bookmarkMode = true",
        "apres": "                    bookmarkMode = true",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderBookmarkWiringTest"],
    },
    {
        # La coquille ne recoit plus l'ouverture des signets : le bouton disparait, puisque sa
        # presence est conditionnee par cette destination. Rien d'autre ne le signale — la
        # compilation passe, et le panneau n'a simplement plus de porte d'entree.
        "nom": "lecteur : la coquille ne recoit plus l'ouverture des signets",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "onOpenBookmarks = openBookmarks,",
        "apres": "onOpenBookmarks = null,",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderBookmarkWiringTest"],
    },
    {
        # Le bouton perd la marque d'activite : en mode de pose, plus rien ne dit que la page
        # attend un verset a toucher. Le geste fonctionne toujours — c'est ce qui rend la faute
        # invisible a l'oeil comme au test de comportement.
        "nom": "lecteur : le bouton ne marque plus l'activite",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderChrome.kt",
        "avant": "background = if (bookmarkActive) colors.soft else Color.Transparent,",
        "apres": "background = Color.Transparent,",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderBookmarkWiringTest"],
    },
    {
        # Le signet perd la source de sa page. Pour les 56 versets sur 6 236 qui changent de page
        # entre les deux decoupages, il se rouvre sur la page voisine : la page s'affiche, elle
        # n'est simplement pas la bonne. Le domaine reste vert partout ailleurs.
        "nom": "route : le signet perd la source de sa page",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "sourcePage = source.persistedKey to page,",
        "apres": "sourcePage = \"traditional\" to page,",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteBookmarkTest"],
    },
    {
        # Le de-zoom est oublie : sur une page agrandie, le doigt designe un verset d'autant plus
        # eloigne que l'agrandissement est fort. L'erreur ne se lit pas comme un calcul faux mais
        # comme un verset faux — la fiche en decrit un, le signet en pose un autre, et les deux
        # sont plausibles.
        "nom": "toucher : le de-zoom est oublie avant de chercher le verset",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderTouch.kt",
        "avant": "val unzoomedX = (x - zoom.x) / scale",
        "apres": "val unzoomedX = x - zoom.x",
        "tache": ":core:domain:test",
        "attendus": ["ReaderTouchTest"],
    },
    {
        # Le decentrage est oublie : la page est centree dans un espace plus large qu'elle, et
        # l'ecart n'est pas compte dans les rectangles. Le doigt designe alors un verset decale
        # de la marge — faux de quelques lignes, donc faux de facon credible.
        "nom": "toucher : le decentrage est oublie avant de chercher le verset",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderTouch.kt",
        "avant": "x = unzoomedX - (availableWidth - pageWidth) / 2.0,",
        "apres": "x = unzoomedX,",
        "tache": ":core:domain:test",
        "attendus": ["ReaderTouchTest"],
    },
    {
        # La confirmation l'emporte sur l'instruction : on demande de toucher un verset sous un
        # message qui annonce que c'est deja fait. La regle est une ternaire d'ordre, et c'est
        # precisement l'ordre qui est faux ici.
        "nom": "signets : la confirmation l'emporte sur l'instruction de pose",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/BookmarksText.kt",
        "avant": "    fun notice(placing: Boolean, saved: Boolean): String? = when {\n"
                 "        placing -> PLACE_NOTICE\n"
                 "        saved -> SAVED_NOTICE\n"
                 "        else -> null\n"
                 "    }",
        "apres": "    fun notice(placing: Boolean, saved: Boolean): String? = when {\n"
                 "        saved -> SAVED_NOTICE\n"
                 "        placing -> PLACE_NOTICE\n"
                 "        else -> null\n"
                 "    }",
        "tache": ":core:domain:test",
        "attendus": ["BookmarksPanelTest"],
    },
    {
        # Le panneau ne filtre plus ses entrees : il propose les deux destinations meme quand
        # aucune n'est branchee. L'entree « Mes marques-pages » se refermerait alors sur rien —
        # et le bouton de la coquille s'afficherait pour un panneau vide.
        "nom": "signets : le panneau ne filtre plus ses entrees",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/BookmarksText.kt",
        "avant": "fun panelRows(available: Set<PanelAction>): List<PanelRow> = PANEL.filter { it.action in available }",
        "apres": "fun panelRows(available: Set<PanelAction>): List<PanelRow> = PANEL",
        "tache": ":core:domain:test",
        "attendus": ["BookmarksPanelTest"],
    },
    {
        # Le libelle du bouton suit `isDifficult` — qui compte **deux** origines — au lieu du
        # seul marqueur de l'eleve. Sur un verset que le professeur a marque, le bouton annonce
        # alors un retrait la ou l'appui **ajoute** un marqueur. Le libelle ment, et rien ne le
        # dit : le panneau s'affiche, les actions fonctionnent.
        "nom": "verse-actions : le libelle suit le marqueur du professeur",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/VerseActionsText.kt",
        "avant": "            .filter { it.value.user != null }",
        "apres": "            .filter { it.value.user != null || it.value.admin != null }",
        "tache": ":core:domain:test",
        "attendus": ["VerseActionsTextTest"],
    },
    {
        # La derniere entree ne bascule plus : elle propose toujours de marquer, meme sur un
        # verset deja marque par l'eleve. L'appui retire alors le marqueur sous un mot qui
        # annonce l'inverse — le geste est juste, le mot est faux.
        "nom": "verse-actions : la derniere entree ne bascule plus",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/VerseActionsText.kt",
        "avant": "            add(Row(Action.MARK, if (markedByUser) UNMARK_DIFFICULT else MARK_DIFFICULT))",
        "apres": "            add(Row(Action.MARK, MARK_DIFFICULT))",
        "tache": ":core:domain:test",
        "attendus": ["VerseActionsTextTest"],
    },
    {
        # `isUseful` repond toujours oui : le panneau s'ouvre meme quand aucune action n'est
        # branchee, et se reduit a son titre et a sa poignee. Une impasse qui a l'air d'un ecran.
        "nom": "verse-actions : le panneau s'ouvre meme sans aucune action",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/VerseActionsText.kt",
        "avant": "    fun isUseful(available: Set<Action>): Boolean = available.isNotEmpty()",
        "apres": "    fun isUseful(available: Set<Action>): Boolean = true",
        "tache": ":core:domain:test",
        "attendus": ["VerseActionsTextTest"],
    },
    {
        # Le lecteur ouvre le panneau sans verifier qu'il a une action : l'appui long sur un
        # verset ouvre une feuille vide, et la fiche a disparu avec. Le geste ne fait plus rien
        # d'utile, et rien ne le signale.
        "nom": "verse-actions : le lecteur ouvre le panneau sans verifier qu'il est utile",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "panel = if (touched != null && VerseActionsText.isUseful(verseActions)) {",
        "apres": "panel = if (touched != null) {",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["VerseActionsWiringTest"],
    },
    {
        # Le lecteur ne transmet plus le marqueur de l'eleve : il retombe sur son defaut vide, et
        # **tous** les versets proposent de les marquer — y compris ceux qui le sont deja. La
        # bascule fonctionne toujours, donc le geste ne revele rien.
        "nom": "verse-actions : le lecteur ne transmet plus le marqueur de l'eleve",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    markedByUser = verseId in userMarkedIds,",
        "apres": "                    markedByUser = false,",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["VerseActionsWiringTest"],
    },
    {
        # Le marquage referme le panneau : le libelle ne bascule plus sous les yeux, et la
        # personne doit rouvrir le geste pour verifier ce qui vient d'etre ecrit. C'est un
        # ecart de comportement que rien d'autre ne mesure.
        "nom": "verse-actions : le marquage referme le panneau",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    onMark = onMarkDifficulty?.let { mark -> { mark(verseId) } },",
        "apres": "                    onMark = onMarkDifficulty?.let { mark -> { mark(verseId); panel = ReaderPanel.NONE } },",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["VerseActionsWiringTest"],
    },
    {
        # La fiche du verset est de nouveau dessinee sous le panneau : elle annonce « Toucher la
        # page pour fermer », alors que le voile du panneau capterait ce toucher. Un texte faux,
        # a l'endroit exact ou la personne regarde.
        "nom": "verse-actions : la fiche n'est plus masquee sous le panneau",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "selectedVerse?.takeIf { panel != ReaderPanel.VERSE }?.let { verseId ->",
        "apres": "selectedVerse?.let { verseId ->",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["VerseActionsWiringTest"],
    },
    {
        # « Selectionner un passage » revient avec une destination vide : l'entree s'affiche,
        # et l'appui ne fait rien. Le geste de designation d'une plage n'est pas porte — c'est
        # exactement ce que la regle du domaine interdit d'afficher.
        "nom": "verse-actions : selectionner un passage revient sans destination",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    markedByUser = verseId in userMarkedIds,",
        "apres": "                    markedByUser = verseId in userMarkedIds,\n                    onSelectRange = {},",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["VerseActionsWiringTest"],
    },
    {
        # La route confond les deux ensembles : elle fournit les versets difficiles, professeur
        # compris, la ou le libelle attend ceux de l'eleve. Meme faute que la premiere, mais a
        # l'autre bout du branchement — et une seule des deux suffirait a faire mentir le bouton.
        "nom": "verse-actions : la route confond les deux ensembles de marqueurs",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "        userState?.let { VerseActionsText.userMarkedIds(it) } ?: emptySet()",
        "apres": "        userState?.let { Review.difficultIds(it) } ?: emptySet()",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteVerseActionsTest"],
    },
    {
        # La route n'ecrit plus rien : l'entree reste affichee, le panneau se comporte comme
        # avant, et le marqueur n'est jamais enregistre. Rien ne le dit avant le redemarrage —
        # et encore, le verset paraissait simplement ne pas avoir ete marque.
        "nom": "verse-actions : la route n'ecrit plus le marqueur",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "                    container.userState.mutate { state -> Review.toggleDifficulty(state, verseId) }",
        "apres": "                    container.userState.mutate { state -> state }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteVerseActionsTest"],
    },

    {
        # La fenetre se limite a la largeur d'un telephone : en paysage, la liste flotte au milieu
        # de l'ecran au lieu de l'occuper. Rien d'autre ne le dit — l'ecran s'affiche, la liste
        # defile, et seuls le cadre et la largeur ont change.
        "nom": "ecran-signets : la fenetre se limite a la largeur du telephone",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/BookmarksScreen.kt",
        "avant": "properties = DialogProperties(usePlatformDefaultWidth = false),",
        "apres": "properties = DialogProperties(),",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["BookmarksScreenWiringTest"],
    },
    {
        # Le bouton de la ligne supprime directement : le dialogue de confirmation existe toujours,
        # mais plus personne ne l'ouvre. Un signet disparait au premier appui, et la personne ne
        # sait pas ce qu'elle a touche.
        "nom": "ecran-signets : le bouton de ligne supprime sans confirmer",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/BookmarksScreen.kt",
        "avant": "onDelete = { pendingDelete = row.verseId },",
        "apres": "onDelete = { onDelete(row.verseId) },",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["BookmarksScreenWiringTest"],
    },
    {
        # La vue se met a calculer ses lignes : elle connait des lors le referentiel, et le partage
        # qui rend le domaine eprouvable sans ecran tombe. La ligne hors corpus ferait aussi
        # tomber l'ecran, au lieu d'etre omise.
        "nom": "ecran-signets : la vue calcule ses lignes",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/BookmarksScreen.kt",
        "avant": "    val colors = AppTheme.colors\n\n    // Le signet dont la suppression attend confirmation.",
        "apres": "    val colors = AppTheme.colors\n    val ignore = Bookmarks.rows(\n        com.msoumaya.deepseekandroid.core.model.AppState(),\n        com.msoumaya.deepseekandroid.core.model.MushafSource.MEDINA,\n    )\n\n    // Le signet dont la suppression attend confirmation.",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["BookmarksScreenWiringTest"],
    },
    {
        # Le texte coranique n'est plus borne : un verset long repousse le bouton « Reprendre » de
        # sa propre carte hors de l'ecran. Le signet devient impossible a reprendre, et il est
        # justement celui qu'on voulait reprendre.
        "nom": "ecran-signets : le texte coranique n'est plus borne",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/BookmarksScreen.kt",
        "avant": "            maxLines = ROW_TEXT_MAX_LINES,\n",
        "apres": "",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["BookmarksScreenWiringTest"],
    },
    {
        # La carte d'explication perd son fond doux : elle ne se distingue plus des lignes de
        # signets, et se lit comme l'une d'elles. La consigne disparait dans la liste.
        "nom": "ecran-signets : la carte d'explication perd son fond",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/BookmarksScreen.kt",
        "avant": "AppCard(background = colors.soft) {",
        "apres": "AppCard {",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["BookmarksScreenWiringTest"],
    },
    {
        # La route n'affiche plus l'ecran : le bouton « Mes marques-pages » du panneau n'ouvre
        # rien. La condition est fausse pour toujours, et rien ne le signale — le bouton, lui,
        # reste la.
        "nom": "route-signets : l'ecran des signets n'est plus affiche",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "if (bookmarksOpen) {",
        "apres": "if (false) {",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteBookmarksScreenTest"],
    },
    {
        # La reprise perd le decoupage de la source : elle saute a la page de Medine pour un signet
        # lu dans le paquet 1441. 56 versets sur 6 236 sont dans ce cas, et la page s'affiche —
        # elle n'est simplement pas la bonne.
        "nom": "route-signets : la reprise perd le decoupage de la source",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "Bookmarks.pageFor(it, source, id)",
        "apres": "Bookmarks.pageFor(it, com.msoumaya.deepseekandroid.core.model.MushafSource.MEDINA, id)",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteBookmarksScreenTest"],
    },
    {
        # La route quitte avant d'afficher l'ecran : le lecteur est demonte, son lecteur audio est
        # libere, et la seance d'ecoute s'arrete parce qu'on consulte ses signets. C'est l'ecart
        # assume du portage qui disparait, et rien ne le dit.
        "nom": "route-signets : la route quitte avant d'afficher l'ecran",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "if (bookmarksOpen) {\n        BookmarksScreen(",
        "apres": "if (bookmarksOpen) {\n        return\n        BookmarksScreen(",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteBookmarksScreenTest"],
    },
    {
        # La reprise n'enregistre plus la page : « Derniere reprise » pointerait sur la page par
        # defaut du verset, et non sur celle ou l'on a effectivement repris. L'ecran des signets
        # marquerait alors une ligne qui ne correspond pas au geste.
        "nom": "route-signets : la reprise n'enregistre plus la page",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "Bookmarks.useBookmark(state, id, pageOverride = target)",
        "apres": "Bookmarks.useBookmark(state, id)",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteBookmarksScreenTest"],
    },
    {
        # Ouvrir la liste ne remet plus le verset en attente a zero : reprendre deux fois de suite
        # le meme signet ne le selectionne qu'une fois, la cle de l'effet n'ayant pas change. Le
        # defaut ne se voit qu'en recommencant, donc jamais pendant un essai rapide.
        "nom": "route-signets : ouvrir la liste n'oublie plus le verset en attente",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "onOpenBookmarks = {\n            pendingVerse = null\n            bookmarksOpen = true",
        "apres": "onOpenBookmarks = {\n            bookmarksOpen = true",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteBookmarksScreenTest"],
    },

    {
        # La reprise n'enregistre plus le verset demande : la page s'ouvre, elle est la bonne,
        # et le verset qu'on venait chercher n'est simplement plus designe. Rien ne le dit —
        # la fiche du verset est une carte parmi d'autres, et son absence ne ressemble pas a
        # une panne.
        "nom": "reprise-verset : la reprise ne designe plus le verset",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "\n            verseState.value = initialVerse",
        "apres": "",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderScreenResumeTest"],
    },
    {
        # La reprise ne referme plus le panneau qu'on avait quitte. Le lecteur n'etant **pas**
        # demonte quand on consulte ses signets, la feuille ouverte avant de partir survit a
        # l'aller-retour et revient par-dessus la page : c'est le `setSessionPanel(null)` du
        # source qui disparait, et rien ne le signale.
        "nom": "reprise-verset : la reprise ne referme plus le panneau",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "\n            verseState.value = initialVerse\n            panel = ReaderPanel.NONE",
        "apres": "\n            verseState.value = initialVerse",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderScreenResumeTest"],
    },

    {
        # Un identifiant inconnu du compte ne fait plus ecran : la route retombe sur la memoire
        # de l'appareil, et applique donc un autre choix au moment precis ou le compte dit
        # autre chose.
        "nom": "recitateur : un identifiant inconnu du compte ne fait plus ecran",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReciterPreference.kt",
        "avant": "            return if (known(synced)) Resolution(synced, who, pushToState = false)",
        "apres": "            return if (true) Resolution(synced, who, pushToState = false)",
        "tache": ":core:domain:test",
        "attendus": ["ReciterPreferenceTest"],
    },
    {
        # La valeur d'un autre compte est adoptee : sur un appareil partage, le choix du premier
        # compte est applique au second — et comme il est ensuite reporte, il est **ecrit** dans
        # son compte. La fuite n'est pas seulement affichee, elle est persistee.
        "nom": "recitateur : la valeur d'un autre compte est adoptee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReciterPreference.kt",
        "avant": "        val storedIsMine = stored != null && known(stored) && (owner == null || owner == who)",
        "apres": "        val storedIsMine = stored != null && known(stored)",
        "tache": ":core:domain:test",
        "attendus": ["ReciterPreferenceTest"],
    },
    {
        # La valeur de l'appareil n'est plus reportee au compte : un choix fait hors ligne reste
        # invisible depuis les autres appareils, et rien ne le dit.
        "nom": "recitateur : la valeur de l'appareil n'est plus reportee au compte",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReciterPreference.kt",
        "avant": "            return Resolution(stored, who, pushToState = true)",
        "apres": "            return Resolution(stored, who, pushToState = false)",
        "tache": ":core:domain:test",
        "attendus": ["ReciterPreferenceTest"],
    },
    {
        # Le proprietaire du compte est oublie : l'appareil ne sait plus a qui appartient la
        # valeur retenue, et le prochain compte l'adoptera.
        "nom": "recitateur : le proprietaire du compte est oublie",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReciterPreference.kt",
        "avant": "            return if (known(synced)) Resolution(synced, who, pushToState = false)",
        "apres": "            return if (known(synced)) Resolution(synced, null, pushToState = false)",
        "tache": ":core:domain:test",
        "attendus": ["ReciterPreferenceTest"],
    },
    {
        # L'invite devient un compte comme un autre : un choix fait sans compte est repris par
        # le premier compte connecte, alors qu'il n'appartient a personne.
        "nom": "recitateur : l'invite devient un compte comme un autre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReciterPreference.kt",
        "avant": "        val who = userId ?: GUEST",
        "apres": '        val who = userId ?: "quelqu-un"',
        "tache": ":core:domain:test",
        "attendus": ["ReciterPreferenceTest"],
    },
    {
        # Retenir n'ecrit plus le proprietaire : le document porte un recitateur sans nom, et le
        # compte suivant l'adopte. C'est la fuite, ecrite sur le disque.
        "nom": "recitateur : retenir n'ecrit plus le proprietaire",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/AudioSettingsRepository.kt",
        "avant": "        store.update { it.copy(reciterId = reciterId, reciterOwnerId = ownerId) }\n        _reciterId.value = reciterId\n        _reciterOwnerId.value = ownerId\n    }",
        "apres": "        store.update { it.copy(reciterId = reciterId) }\n        _reciterId.value = reciterId\n    }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AudioSettingsRepositoryTest"],
    },
    {
        # Retenir efface les repetitions : l'ecriture d'une decision touche a ce qu'elle ne
        # decide pas. Les repetitions tiennent a l'appareil, et les voila remises a zero.
        "nom": "recitateur : retenir efface les repetitions",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/AudioSettingsRepository.kt",
        "avant": "        store.update { it.copy(reciterId = reciterId, reciterOwnerId = ownerId) }",
        "apres": "        store.update { it.copy(repeat = com.msoumaya.deepseekandroid.core.domain.StoredAudioPreferences(), reciterId = reciterId, reciterOwnerId = ownerId) }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AudioSettingsRepositoryTest"],
    },
    {
        # L'economie d'ecriture est retiree : le document est reecrit pour rien a chaque
        # composition ou la decision est recalculee. Rien ne casse — le disque s'use.
        "nom": "recitateur : le disque est retouche pour rien",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/AudioSettingsRepository.kt",
        "avant": "        if (_reciterId.value == reciterId && _reciterOwnerId.value == ownerId) return\n",
        "apres": "",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["AudioSettingsRepositoryTest"],
    },
    {
        # La route n'apprend plus rien a l'appareil : la valeur retenue n'est pas ecrite, et le
        # document garde l'ancienne — ou rien.
        "nom": "recitateur : la route n'apprend plus rien a l'appareil",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "            runCatching { container.audioSettings.remember(retenu, proprietaire) }\n",
        "apres": "",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteReciterTest"],
    },
    {
        # La route ne reporte plus la valeur au compte : un choix fait hors ligne ne remonte
        # jamais, et le compte reste muet.
        "nom": "recitateur : la route ne reporte plus la valeur au compte",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "        if (reciterResolution.pushToState && retenu != null && userState != null) {",
        "apres": "        if (!reciterResolution.pushToState && retenu != null && userState != null) {",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteReciterTest"],
    },
    {
        # Un changement de vitesse est pris pour un choix : la feuille appelle la meme lambda
        # pour tous les reglages, et le defaut jamais choisi devient un choix, ecrit au compte.
        "nom": "recitateur : un changement de vitesse ecrit un choix",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "                if (reciterId != reciterRetenu) {",
        "apres": "                if (true) {",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteReciterTest"],
    },
    {
        # Le recitateur local perd son proprietaire : deux comptes sur le meme appareil
        # partagent la meme memoire, et le report ecrit le choix du premier dans le second.
        "nom": "recitateur : le recitateur local perd son proprietaire",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "                runCatching { container.audioSettings.save(settings, reciterId, proprietaire) }",
        "apres": "                runCatching { container.audioSettings.save(settings, reciterId, null) }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteReciterTest"],
    },

    {
        # La plage d'une page perd son maximum : la page 1 n'annonce plus qu'un verset, et six
        # versets d'Al-Fatiha sortent de la navigation sans que rien ne le dise.
        "nom": "decoupage : la plage d'une page perd son maximum",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageIndex.kt",
        "avant": "                    Range(minOf(existing.start, entry.id), maxOf(existing.end, entry.id))",
        "apres": "                    Range(minOf(existing.start, entry.id), minOf(existing.start, entry.id))",
        "tache": ":core:domain:test",
        "attendus": ["TestPageIndexTest"],
    },
    {
        # Une page inconnue rend une plage vide au lieu de refuser : le lecteur lirait alors le
        # verset 0, et le defaut ne se verrait qu'a l'ecoute.
        "nom": "decoupage : une page inconnue rend une plage vide",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageIndex.kt",
        "avant": "    fun pageRange(page: Int): Range = ranges[page] ?: throw IllegalArgumentException(\"Page invalide : $page\")",
        "apres": "    fun pageRange(page: Int): Range = ranges[page] ?: Range(0, 0)",
        "tache": ":core:domain:test",
        "attendus": ["TestPageIndexTest"],
    },
    {
        # La page courante n'est plus conservee : un verset a cheval ferait sauter la lecture en
        # arriere, au milieu du verset.
        "nom": "decoupage : la page courante n'est plus conservee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageIndex.kt",
        "avant": "        return if (current != null && pages.contains(current)) current else pages.first()",
        "apres": "        return pages.first()",
        "tache": ":core:domain:test",
        "attendus": ["TestPageIndexTest"],
    },
    {
        # Un type de ligne inconnu passe pour une basmala : une ligne d'ornement s'afficherait
        # comme une basmala, un defaut visible mais qui accuse la mauvaise chose.
        "nom": "page : un type de ligne inconnu passe pour une basmala",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageLoader.kt",
        "avant": "        else -> error(\"Type de ligne inconnu : $name\")",
        "apres": "        else -> TestLineType.BASMALLAH",
        "tache": ":core:domain:test",
        "attendus": ["TestPageLoaderTest"],
    },
    {
        # La taille fractionnaire est tronquee dans le document : 602 pages sur 604 changent de
        # corps de texte, et la composition ne tombe plus sur la page imprimee.
        "nom": "document : une taille fractionnaire est tronquee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageHtml.kt",
        "avant": "            page.fontSize.toString()",
        "apres": "            page.fontSize.toInt().toString()",
        "tache": ":core:domain:test",
        "attendus": ["TestPageHtmlTest"],
    },
    {
        # La basmala perd sa graphie propre : celle d'Al-Baqara s'affiche comme les autres.
        "nom": "document : la basmala perd sa graphie propre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageHtml.kt",
        "avant": "        2 -> BASMALA_BAQARA",
        "apres": "        2 -> BASMALA_DEFAULT",
        "tache": ":core:domain:test",
        "attendus": ["TestPageHtmlTest"],
    },
    {
        # Le balisage n'est plus echappe : un caractere de texte arabe peut fermer une balise.
        "nom": "document : le balisage n'est plus echappe",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageHtml.kt",
        "avant": "                '<' -> append(\"&lt;\")",
        "apres": "                '<' -> append(\"<\")",
        "tache": ":core:domain:test",
        "attendus": ["TestPageHtmlTest"],
    },
    {
        # Le numero de page perd ses chiffres arabes : la page imprimee en porte, la page
        # recomposee n'en porterait plus.
        "nom": "document : le numero de page perd ses chiffres arabes",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageHtml.kt",
        "avant": "        value.toString().map { ('\\u0660' + (it - '0')) }.joinToString(\"\")",
        "apres": "        value.toString()",
        "tache": ":core:domain:test",
        "attendus": ["TestPageHtmlTest"],
    },
    {
        # Un echec d'une voisine est traite comme un echec de la page lue : l'ecran annoncerait
        # « la page n'a pas pu etre chargee » a propos d'une page qui s'affiche tres bien.
        "nom": "session : un echec de voisine accuse la page lue",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageSession.kt",
        "avant": "            if (target != current) Decision.Ignored else Decision.Failed",
        "apres": "            if (target != displayed) Decision.Ignored else Decision.Failed",
        "tache": ":core:domain:test",
        "attendus": ["TestPageSessionTest"],
    },
    {
        # Une page qui annonce une autre page que la sienne est crue : une surface recyclee
        # ferait afficher la page qu'elle portait avant.
        "nom": "session : une page annoncant une autre est crue",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageSession.kt",
        "avant": "            if (message.page != target) Decision.Ignored",
        "apres": "            if (false) Decision.Ignored",
        "tache": ":core:domain:test",
        "attendus": ["TestPageSessionTest"],
    },
    {
        # Un appui sur une voisine agit : le doigt designerait un verset qu'on ne voit pas.
        "nom": "session : un appui sur une voisine agit",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageSession.kt",
        "avant": "            target != displayed -> Decision.Ignored",
        "apres": "            false -> Decision.Ignored",
        "tache": ":core:domain:test",
        "attendus": ["TestPageSessionTest"],
    },
    {
        # Les voisines ne sont plus preparees : chaque balayage repasserait par l'ecran de
        # chargement, sur le geste meme que la preparation existe pour rendre instantane.
        "nom": "session : les voisines ne sont plus preparees",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageSession.kt",
        "avant": "        if (measured) TestPageIndex.adjacentPages(page) else listOf(page)",
        "apres": "        if (measured) listOf(page) else listOf(page)",
        "tache": ":core:domain:test",
        "attendus": ["TestPageSessionTest"],
    },
    {
        # Un balayage sans distance est devine : la page tournerait au hasard.
        "nom": "session : un balayage sans distance est devine",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageSession.kt",
        "avant": "                if (dx != null && dy != null) Message.Swipe(dx, dy) else null",
        "apres": "                Message.Swipe(dx ?: 0.0, dy ?: 0.0)",
        "tache": ":core:domain:test",
        "attendus": ["TestPageSessionTest"],
    },
    {
        # La cle du verset devient son identifiant global : le document ne reconnait plus aucun
        # de ses mots, et la surimpression reste vide sans que rien ne le dise.
        "nom": "surimpression : la cle devient l'identifiant global",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageOverlay.kt",
        "avant": "        return \"${verse.surah}:${verse.ayah}\"",
        "apres": "        return id.toString()",
        "tache": ":core:domain:test",
        "attendus": ["TestPageOverlayTest"],
    },
    {
        # Un marqueur hors du corpus fait lever au lieu d'etre omis : l'ecran immersif tombe
        # entier pour un marqueur orphelin ecrit par une autre version.
        "nom": "surimpression : un marqueur hors corpus fait tomber l'ecran",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageOverlay.kt",
        "avant": "        val verse = Quran.verses.getOrNull(id - 1) ?: return null",
        "apres": "        val verse = Quran.verseAt(id)",
        "tache": ":core:domain:test",
        "attendus": ["TestPageOverlayTest"],
    },
    {
        # Une liste de marques absente au lieu d'etre vide : le document appelle `.includes`
        # dessus sans la verifier, donc son script leve et la page ne se mesure jamais.
        "nom": "surimpression : une liste de marques peut manquer",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageOverlay.kt",
        "avant": "            \"bookmarks\" to keys(markers.bookmarks, keyOf),",
        "apres": "            \"bookmarks\" to (if (markers.bookmarks.isEmpty()) JsonNull else keys(markers.bookmarks, keyOf)),",
        "tache": ":core:domain:test",
        "attendus": ["TestPageOverlayTest"],
    },
    {
        # La plus recente est evincee au lieu de la plus ancienne : le cache perd exactement la
        # page qu'on vient de charger, et relit tout a chaque aller-retour.
        "nom": "cache : la plus recente est evincee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/BoundedCache.kt",
        "avant": "            entries.remove(entries.keys.first())",
        "apres": "            entries.remove(entries.keys.last())",
        "tache": ":core:domain:test",
        "attendus": ["BoundedCacheTest"],
    },
    {
        # Une lecture ne remonte plus l'entree : revenir sur ses pas relit les trois polices de
        # la page qu'on vient de quitter, a chaque fois.
        "nom": "cache : une lecture ne protege plus de l'eviction",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/BoundedCache.kt",
        "avant": "        val hit = entries.remove(key) ?: return null",
        "apres": "        val hit = entries[key] ?: return null",
        "tache": ":core:domain:test",
        "attendus": ["BoundedCacheTest"],
    },
    {
        # La source composee se lit avec le decoupage de Medine : les 56 versets qui changent de
        # page entre les deux tables ouvrent a cote, et le defaut ne se voit qu'a l'ecoute.
        "nom": "navigation : la source composee se lit avec le decoupage de Medine",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/MushafSourceNavigation.kt",
        "avant": "        source == MushafSource.CORAN_TEST -> TestPageIndex.pageRange(page)",
        "apres": "        source == MushafSource.CORAN_TEST -> Quran.pageRange(page)",
        "tache": ":core:domain:test",
        "attendus": ["MushafSourceNavigationTest"],
    },
    {
        # La lecture simplifiee prend l'ecran compose : elle n'a ni index de pages de composition
        # ni police par page, donc son ecran n'aurait rien a afficher.
        "nom": "navigation : la lecture simplifiee prend l'ecran compose",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/MushafSourceNavigation.kt",
        "avant": "    fun isImmersive(source: MushafSource): Boolean = source == MushafSource.CORAN_TEST",
        "apres": "    fun isImmersive(source: MushafSource): Boolean = source != MushafSource.CORAN_1441",
        "tache": ":core:domain:test",
        "attendus": ["MushafSourceNavigationTest"],
    },
    {
        # Le numero de page est complete sur trois chiffres, comme les images du moushaf : aucun
        # fichier de police ne porte ce nom, et chaque page s'afficherait en carres vides.
        "nom": "polices : le numero de page est complete sur trois chiffres",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/TestPageFonts.kt",
        "avant": "    fun pageFile(page: Int): String = \"$page.woff2\"",
        "apres": "    fun pageFile(page: Int): String = page.toString().padStart(3, '0') + \".woff2\"",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["TestPageFontsTest"],
    },
    {
        # L'alpha n'est plus ecarte : la couleur fait huit chiffres, le document ne la
        # reconnait pas, et il retombe sur son propre fond sans rien dire.
        "nom": "couleur : l'alpha n'est plus ecarte",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/TestPageColors.kt",
        "avant": "    fun hex(color: Color): String = \"#%06X\".format(color.toArgb() and 0xFFFFFF)",
        "apres": "    fun hex(color: Color): String = \"#%08X\".format(color.toArgb())",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["TestPageColorsTest"],
    },
    {
        # Une forme approchante est acceptee : `#FFF` passerait, et la page perdrait sa teinte
        # sans que rien ne le signale.
        "nom": "couleur : une forme approchante est acceptee",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/TestPageColors.kt",
        "avant": "        require(FORM.matches(hex)) { \"Couleur illisible : $hex\" }",
        "apres": "        require(hex.startsWith(\"#\")) { \"Couleur illisible : $hex\" }",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["TestPageColorsTest"],
    },
    {
        # La source composee se lit avec le decoupage de Medine : la page annoncee pour un
        # verset qui n'y est pas, et le defaut ne se voit qu'a l'ecran.
        "nom": "decoupage : la source composee se lit avec le decoupage de Medine",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/StudyProgressCalculator.kt",
        "avant": "        source == TEST_SOURCE -> TestPageIndex.versePage(id)",
        "apres": "        source == TEST_SOURCE -> Quran.pageOf(id)",
        "tache": ":core:domain:test",
        "attendus": ["StudyProgressCalculatorTest"],
    },
    {
        # La plage d'une page revient au moushaf : la borne haute perd le verset que la
        # composition ajoute, et une seance s'arrete un verset trop tot.
        "nom": "decoupage : la plage d'une page revient au moushaf",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/StudyProgressCalculator.kt",
        "avant": "        source == TEST_SOURCE -> TestPageIndex.pageRange(page)",
        "apres": "        source == TEST_SOURCE -> Quran.pageRange(page)",
        "tache": ":core:domain:test",
        "attendus": ["StudyProgressCalculatorTest"],
    },
    {
        # La derniere page d'un verset revient au moushaf : un verset a cheval ferait borner la
        # seance sur la page d'un autre decoupage.
        "nom": "decoupage : la derniere page d'un verset revient au moushaf",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/StudyProgressCalculator.kt",
        "avant": "        source == TEST_SOURCE -> TestPageIndex.pagesOf(id).lastOrNull() ?: TestPageIndex.versePage(id)",
        "apres": "        source == TEST_SOURCE -> Quran.pageOf(id)",
        "tache": ":core:domain:test",
        "attendus": ["StudyProgressCalculatorTest"],
    },
    {
        # La cle d'etude replie de nouveau la source composee sur le moushaf : c'est
        # l'hypothese qui avait expire, remise en place.
        "nom": "decoupage : la cle d'etude replie de nouveau la source composee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/StudyProgressCalculator.kt",
        "avant": "        MushafSource.CORAN_TEST -> MushafSource.CORAN_TEST.persistedKey",
        "apres": "        MushafSource.CORAN_TEST -> \"traditional\"",
        "tache": ":core:domain:test",
        "attendus": ["StudyProgressCalculatorTest", "BookmarksScreenRulesTest"],
    },
    {
        # La source annoncee est nommee par l'enumeration au lieu de sa cle persistee : le mot
        # `CORAN_TEST` ne correspond a aucune branche, et la page retombe sur celle de Medine —
        # pour la source qui est justement le defaut de l'application.
        #
        # Le cas visait d'abord « repasser par le repli `sourceKey` ». Mesure : cette mutation ne
        # fait **rien tomber**, parce que le repli ne replie plus `coranTest` depuis que la source
        # a recu son decoupage. Une mutation equivalente ne falsifie rien, et c'est le falsificateur
        # qui l'a dit — pas une supposition.
        "nom": "decoupage : la source de l'accueil est nommee par l'enumeration",
        "fichier": "feature/home/src/main/kotlin/com/msoumaya/deepseekandroid/feature/home/HomeRenderer.kt",
        "avant": "        val source = (state.reader?.mushaf ?: MushafSource.CORAN_TEST).persistedKey",
        "apres": "        val source = (state.reader?.mushaf ?: MushafSource.CORAN_TEST).name",
        "tache": ":feature:home:testDebugUnitTest",
        "attendus": ["HomeRendererTest"],
    },

    # -----------------------------------------------------------------------
    # La memoire du lecteur
    # -----------------------------------------------------------------------
    {
        # La page quittee n'entre plus dans les pages lues : le compteur de pages lues de
        # l'accueil ne bougerait plus jamais, et rien d'autre ne le dirait.
        "nom": "memoire : la page quittee n'entre plus dans les pages lues",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderMemory.kt",
        "avant": "            readPages = (retenu.effectiveReadPages + page).distinct(),",
        "apres": "            readPages = retenu.effectiveReadPages,",
        "tache": ":core:domain:test",
        "attendus": ["la page quittee est ajoutee aux pages lues"],
    },
    {
        # Le verset memorise est repris sans verifier qu'il tient encore sur la page : la position
        # resterait celle d'une page qu'on a quittee, et l'accueil annoncerait un verset que
        # personne n'a sous les yeux.
        "nom": "memoire : le verset memorise est repris sans verifier sa page",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderMemory.kt",
        "avant": "        val verseId = versetRetenu(source, base.lastRead?.verseId, page)\n            ?: versetRetenu(source, start, page)\n            ?: range.start",
        "apres": "        val verseId = base.lastRead?.verseId\n            ?: versetRetenu(source, start, page)\n            ?: range.start",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMemoryTest"],
    },
    {
        # La page memorisee est ecrite pour **toutes** les sources : celle de la composition se
        # remplirait avec la page d'un moushaf en images, et l'ouverture suivante irait a cote.
        "nom": "memoire : la page memorisee est ecrite pour toutes les sources",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderMemory.kt",
        "avant": "        val retenu = if (source == MushafSource.CORAN_TEST) {\n            Program.adoptComposedSource(base, page)\n        } else {\n            base\n        }",
        "apres": "        val retenu = Program.adoptComposedSource(base, page)",
        "tache": ":core:domain:test",
        "attendus": ["une source en images ne recoit aucune page memorisee"],
    },
    {
        # L'etat n'est plus migre avant la decision : la source composee n'est plus reconnue, la
        # page n'est pas ecrite, et la migration qui suit la rend pourtant affichee — donc une
        # page memorisee pour une source qui n'en a jamais recu.
        "nom": "memoire : l'etat n'est plus migre avant la decision",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderMemory.kt",
        "avant": "        val base = Program.migrateReaderState(state)",
        "apres": "        val base = state",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMemoryTest"],
    },
    {
        # Une source en images relit la page brute au lieu de reprojeter le verset : elle ouvre
        # la page d'un autre decoupage, et le verset annonce n'est pas celui qu'on voit.
        "nom": "memoire : la source en images relit la page brute",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReaderMemory.kt",
        "avant": "            ?.let { runCatching { MushafSourceNavigation.versePage(source, it.verseId) }.getOrNull() }",
        "apres": "            ?.let { it.page }",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMemoryTest"],
    },
    {
        # La fermeture est lancee **avant** l'ecriture : la portee meurt avec l'ecran, donc
        # l'ecriture est annulee en vol — et l'ecran se ferme normalement, ce qui rend le defaut
        # invisible. C'est le seul cas de ce fichier qui mesure un **ordre**.
        "nom": "memoire : la fermeture passe avant l'ecriture",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "        val affichee = page\n        scope.launch {",
        "apres": "        val affichee = page\n        onClose()\n        scope.launch {",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteMemoryTest"],
    },
    {
        # Le retour systeme n'emprunte plus la sortie qui ecrit : quitter au geste n'enregistre
        # plus rien, et la perte ne se voit que chez qui quitte vite.
        "nom": "memoire : le retour systeme n'ecrit plus rien",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "    BackHandler(enabled = !bookmarksOpen) { quitter() }",
        "apres": "    BackHandler(enabled = !bookmarksOpen) { onClose() }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteMemoryTest"],
    },
    {
        # Le suivi de page n'est plus garde contre le premier rendu : il rejoue ce que
        # l'initialisation vient de faire, et remet le zoom a zero sur la page qu'on ouvre.
        "nom": "memoire : le suivi de page rejoue au premier rendu",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "        if (initialPage != pageState.intValue) {",
        "apres": "        if (initialPage > 0) {",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderScreenPageRequestTest"],
    },
    {
        # Le suivi de page efface le verset designe : une reprise de signet ouvrirait la bonne
        # page sans la fiche du verset qu'on venait chercher.
        "nom": "memoire : le suivi de page efface le verset designe",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "            pageState.intValue = initialPage.coerceIn(1, totalPages)\n            zoomState.value = ReaderZoom()",
        "apres": "            pageState.intValue = initialPage.coerceIn(1, totalPages)\n            verseState.value = null\n            zoomState.value = ReaderZoom()",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderScreenPageRequestTest"],
    },
    {
        # Le plein ecran n'est plus reconnu derriere un argument : deux barres s'affichent
        # par-dessus un ecran qui gere ses propres marges.
        "nom": "memoire : le lecteur perd son plein ecran derriere un argument",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppDestination.kt",
        "avant": "    fun isFullScreen(route: String?): Boolean = baseRoute(route) in fullScreen",
        "apres": "    fun isFullScreen(route: String?): Boolean = route in fullScreen",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppRoutesReaderTest"],
    },
    {
        # La route du lecteur perd son argument : le verset demande n'atteint plus la route, et
        # la position memorisee se decale d'un cran a chaque ouverture.
        #
        # L'ancre vise la construction **actuelle** de la route. La precedente — une seule
        # expression ternaire — a disparu quand la ligne 8 a remplace le ternaire par une liste
        # d'arguments, pour que les trois bornes d'une seance voyagent ensemble. Le cas est reste
        # casse jusqu'a ce que `--verifier` le dise : une ancre perimee ne se voit pas autrement,
        # puisque ce cas n'est jamais joue par les autres.
        "nom": "memoire : la route du lecteur perd son argument",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppDestination.kt",
        "avant": "            if (verseId != null) add(\"$READER_VERSE=$verseId\")",
        "apres": "            if (verseId != null) add(\"\")",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppRoutesReaderTest"],
    },
    {
        # L'accueil n'ouvre plus le lecteur. L'ancre porte le **nom de l'ecran** : le programme
        # ouvre lui aussi le lecteur depuis la phase C, et une ancre reduite a la lambda
        # apparaitrait deux fois — le remplacement toucherait alors un endroit qu'on n'a pas
        # choisi.
        "nom": "memoire : l'accueil n'ouvre plus le lecteur",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "            HomeScreen(\n                onOpenReader = { verseId ->\n                    navController.navigate(AppRoutes.readerRoute(verseId))\n                },",
        "apres": "            HomeScreen(\n                onOpenReader = {},",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppScaffoldReaderEntryTest"],
    },
    {
        # Le programme n'ouvre plus le lecteur en lecture libre : ses lignes a venir et ses cartes
        # sans seance deviennent des boutons morts. La compilation passe, et rien d'autre ne le
        # dit — l'accueil garde sa porte, donc un controle portant sur le fichier entier resterait
        # vert pour la mauvaise raison.
        "nom": "memoire : le programme n'ouvre plus le lecteur",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "            ProgramScreen(\n                onOpenReader = { verseId ->\n                    navController.navigate(AppRoutes.readerRoute(verseId))\n                },",
        "apres": "            ProgramScreen(\n                onOpenReader = {},",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppScaffoldReaderEntryTest"],
    },
    {
        # La route du lecteur n'est plus servie par son motif : l'argument du verset n'atteint
        # jamais la route, et le lecteur s'ouvre toujours sans verset.
        "nom": "memoire : la route du lecteur n'est plus servie par son motif",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "            route = AppRoutes.READER_PATTERN,",
        "apres": "            route = AppRoutes.READER,",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppScaffoldReaderEntryTest"],
    },
    # ------------------------------------------------------------------ la seance d'etude
    #
    # Les huit cas suivants couvrent le branchement de la seance, dont la **regle** est eprouvee
    # pour de vrai dans `core:domain` (`StudySessionTest`, quarante-trois cas sur le referentiel
    # entier). Ce qui ne s'eprouve nulle part ailleurs, c'est le **passage** : une lambda bien
    # branchee, une plage resolue par le domaine, un ordre d'ecriture.
    {
        # La validation ferme **avant** d'ecrire. La portee meurt avec l'ecran : l'ecriture est
        # annulee en vol, et le symptome est celui d'une validation qui n'existe pas — donc d'un
        # programme qui redemande le meme passage indefiniment.
        #
        # La mutation est une **insertion pure** : l'ancre survit. C'est le cas qui a fait tomber
        # la premiere version du controle, laquelle cherchait `quitter()` dans le rappel entier et
        # trouvait celui de la **garde** — avant l'ecriture, dans un code juste. Le controle borne
        # desormais sa mesure au chemin d'ecriture ; ce cas prouve que la borne laisse encore
        # passer le vrai defaut.
        "nom": "seance d'etude : la validation ferme avant d'ecrire",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "        onValidateStudy = { through, note ->\n            val requete = session\n            if (requete == null) {\n                quitter()\n            } else {\n                scope.launch {\n                    runCatching {",
        "apres": "        onValidateStudy = { through, note ->\n            val requete = session\n            if (requete == null) {\n                quitter()\n            } else {\n                scope.launch {\n                    quitter()\n                    runCatching {",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteStudyTest"],
    },
    {
        # Une seance s'ouvre la ou la lecture s'etait arretee, au lieu de **son** premier verset.
        # La personne relit un passage qui n'est pas celui du jour, et rien ne le dit : la page
        # affichee est plausible.
        "nom": "seance d'etude : la page d'ouverture revient a la lecture memorisee",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "        val cible = if (session != null) {\n            val ouverte = userState?.let { StudySession.opening(it, session) } ?: session\n            runCatching { MushafSourceNavigation.versePage(source, ouverte.range.start) }.getOrNull()\n        } else {\n            userState?.let { ReaderMemory.openingPage(it, source) }\n        } ?: return@LaunchedEffect",
        "apres": "        val cible = userState?.let { ReaderMemory.openingPage(it, source) }\n            ?: return@LaunchedEffect",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteStudyTest"],
    },
    {
        # La source d'etude est nommee par l'**enumeration** au lieu de sa cle persistee : le mot
        # ne correspond a aucune branche, et les regles d'etude cherchent leurs tables sous une
        # cle qui n'en a pas. La page annoncee est celle de Medine pour une source qui a son
        # propre decoupage — et une page fausse est plausible, donc invisible.
        "nom": "seance d'etude : la source n'est plus repliee par la regle du domaine",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "    val sourceEtude = StudyProgressCalculator.sourceKey(source)",
        "apres": "    val sourceEtude = source.name",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteStudyTest"],
    },
    {
        # La plage de la feuille est celle **demandee**, et non celle **prevue**. Une reprise
        # partielle ecrirait alors ses propres bornes, et `validateStudyProgress` refuse
        # l'ecriture quand elles different de celles de la plage — donc toutes les validations
        # suivantes seraient perdues en silence.
        "nom": "seance d'etude : la plage de la feuille est celle demandee",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "                range = StudySession.plannedRange(etat, session),",
        "apres": "                range = session.range,",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteStudyTest"],
    },
    {
        # Le bandeau s'affiche sans que rien ne puisse le valider : un appelant qui omet
        # `onValidateStudy` obtient un bandeau qui s'annonce comme une seance et dont le geste ne
        # mene nulle part. C'est le bouton mort que ce lecteur refuse partout ailleurs.
        "nom": "seance d'etude : le bandeau s'affiche sans que rien ne puisse le valider",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "    val seance = study?.takeIf { onValidateStudy != null }",
        "apres": "    val seance = study",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["StudyChromeWiringTest"],
    },
    {
        # Le bandeau n'ouvre plus rien : son geste ne mene nulle part, et rien ne le dit. L'ancre
        # a ete reecrite quand le geste est devenu conditionnel — deux branches au lieu d'une —
        # car la forme d'origine, sur une seule ligne, n'existait plus : c'est le piege de
        # l'ancre perimee, qui ne se voit qu'au `--verifier`.
        "nom": "seance d'etude : le bandeau devient un bouton mort",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                onPress = {\n                    if (seance.request.consolidation) {\n                        panel = ReaderPanel.SESSION\n                    } else {\n                        completionOpen = true\n                    }\n                },",
        "apres": "                onPress = {},",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["StudyChromeWiringTest"],
    },
    {
        # La feuille n'est plus gardee par le drapeau : elle s'ouvre des que la seance existe,
        # donc sans geste. C'est l'inverse du bouton mort, et tout aussi faux.
        "nom": "seance d'etude : la feuille s'ouvre sans le drapeau",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "    if (completionOpen && seance != null && onValidateStudy != null) {",
        "apres": "    if (seance != null && onValidateStudy != null) {",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["StudyChromeWiringTest"],
    },
    {
        # La feuille recoit une page constante au lieu de celle qu'on vient de lire : le point
        # d'arret propose est la fin d'une autre page — un verset d'un autre endroit, et il est
        # plausible, donc jamais signale.
        "nom": "seance d'etude : la feuille propose la page 1 au lieu de celle qu'on lit",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "            currentPage = page,\n            onClose = { completionOpen = false },",
        "apres": "            currentPage = 1,\n            onClose = { completionOpen = false },",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["StudyChromeWiringTest"],
    },
    #
    # Les huit cas suivants couvrent les **reperes de marge**, qui ont deux chemins et non un :
    # le document immersif, qui les calcule dans son propre script, et le lecteur standard, qui
    # appelle la regle de `core:domain` et les dessine en Compose. Les deux partent du meme etat ;
    # une mutation qui n'en casserait qu'un seul est donc exactement ce qu'il faut eprouver.
    {
        # Un `through` posterieur a la plage — un enregistrement repris d'un autre decoupage —
        # ferait compter plus de reperes qu'il n'y a de versets. Le nombre reste plausible, et le
        # document dessinerait des reperes qui n'existent pas.
        "nom": "reperes de marge : le compte depasse la plage",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/StudySession.kt",
        "avant": "        (minOf(range.end, through) - range.start + 1).coerceAtLeast(0)",
        "apres": "        (through - range.start + 1).coerceAtLeast(0)",
        "tache": ":core:domain:test",
        "attendus": ["StudySessionTest"],
    },
    {
        # Un `through` anterieur a la plage — une reprise qui n'a rien valide — donnerait un
        # compte **negatif**, qui ne se lit plus comme un compte. C'est la borne basse qui
        # l'empeche, et rien d'autre ne la dirait.
        "nom": "reperes de marge : le compte peut devenir negatif",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/StudySession.kt",
        "avant": "        (minOf(range.end, through) - range.start + 1).coerceAtLeast(0)",
        "apres": "        (minOf(range.end, through) - range.start + 1)",
        "tache": ":core:domain:test",
        "attendus": ["StudySessionTest"],
    },
    {
        # Un verset hors du corpus est **omis** au lieu de garder sa place : tous les reperes
        # suivants se decalent d'un cran, et le document annonce alors les numeros d'autres
        # versets. Un numero decale reste un numero plausible — donc invisible, comme une page
        # fausse.
        "nom": "reperes de marge : un verset hors corpus decale les suivants",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageOverlay.kt",
        "avant": "        JsonArray(ids.map { id -> keyOf(id)?.let { JsonPrimitive(it) } ?: JsonNull })",
        "apres": "        JsonArray(ids.mapNotNull { id -> keyOf(id)?.let { JsonPrimitive(it) } })",
        "tache": ":core:domain:test",
        "attendus": ["TestPageOverlayTest"],
    },
    {
        # Le champ lu par le document change de nom : `sessionDone` devient `sessionThrough`. Le
        # document ne le lit plus, `readerState.sessionDone||0` vaut `0`, et **aucun** repere n'est
        # plein — ce qui ressemble exactement a une seance ou rien n'a ete valide.
        "nom": "reperes de marge : le compte change de nom",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageOverlay.kt",
        "avant": "            \"sessionDone\" to JsonPrimitive(markers.sessionDone),",
        "apres": "            \"sessionThrough\" to JsonPrimitive(markers.sessionDone),",
        "tache": ":core:domain:test",
        "attendus": ["TestPageOverlayTest"],
    },
    {
        # Le document ne dessine plus ses reperes : la fonction est ecrite, mais plus appelee. La
        # vue immersive perd ses reperes, et rien d'autre ne le dit — le script est une ile, et
        # aucun test du domaine ne regarde ce qui s'y appelle.
        "nom": "reperes de marge : le document ne les dessine plus",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageHtml.kt",
        "avant": "appendChild(icon);}}drawMargin();}",
        "apres": "appendChild(icon);}}}",
        "tache": ":core:domain:test",
        "attendus": ["TestPageHtmlTest"],
    },
    {
        # La teinte de la seance n'a plus son repli : `sessionColor` absent laisserait la couleur
        # indefinie, et les reperes seraient peints d'une teinte que personne n'a choisie — ou pas
        # peints du tout.
        "nom": "reperes de marge : le repli de la teinte disparait",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/TestPageHtml.kt",
        "avant": "color=readerState.sessionColor||readerState.primary;const rail=",
        "apres": "color=readerState.primary;const rail=",
        "tache": ":core:domain:test",
        "attendus": ["TestPageHtmlTest"],
    },
    {
        # Le lecteur transmet le **dernier verset valide** au lieu du **nombre** de versets faits.
        # Le document compare ce nombre au rang d'un repere : avec un numero global, tous les
        # reperes au-dela du premier seraient pleins.
        "nom": "reperes de marge : le lecteur transmet un verset au lieu d'un compte",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "        sessionDone = seance?.let { StudySession.completedIn(it.range, it.through) } ?: 0,",
        "apres": "        sessionDone = seance?.let { it.through } ?: 0,",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderScreenMarksTest"],
    },
    {
        # Le lecteur standard ne recoit plus ses groupes : la regle de `core:domain` n'a plus aucun
        # appelant, et la vue standard perd ses reperes pendant que la vue immersive garde les
        # siens. Les deux chemins doivent dire la meme chose.
        "nom": "reperes de marge : le lecteur standard ne les recoit plus",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    sessionGroups = reperesDeMarge,",
        "apres": "                    sessionGroups = emptyList(),",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["ReaderScreenMarksTest"],
    },
    #
    # Les cinq cas suivants couvrent l'ecran **programme**, le pivot de la phase C. Chacun
    # epingle une decision qui, prise autrement, resterait parfaitement plausible a l'ecran :
    # un repli qui annonce autre chose, un tri qui prend la place d'un renversement, une
    # reprise servie sous la mauvaise identite, une seance du jour qui n'est plus celle du
    # jour. Aucun de ces defauts ne se voit a la compilation, aucun ne leve, et aucun ne
    # produit une valeur absurde : ils changent seulement ce que la personne lit.
    #
    # Les ancres sont **mono-lignes** a dessein. `jouer` decode le fichier sans normaliser les
    # fins de ligne, alors que `precondition` les normalise : une ancre a cheval sur un saut de
    # ligne passerait la verification puis ne remplacerait rien, et le cas conclurait « aucun
    # test n'est tombe » sans que rien ne dise pourquoi. Le fichier est en LF aujourd'hui, mais
    # une ancre d'une seule ligne ne depend pas de ce detail.
    {
        # Le couple du bas annonce « Objectif atteint » la ou la carte annonce « Aucune
        # seance ». Les faire dire la meme chose ne casse rien et ne se voit pas : les deux
        # absences se peignent alors pareil, alors que la source les distingue.
        "nom": "programme : le couple du bas annonce une absence au lieu d'un objectif",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ProgramRenderer.kt",
        "avant": "?: \"Objectif atteint\",",
        "apres": "?: \"Aucune séance\",",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ProgramRendererTest"],
    },
    {
        # La carte de revision a **deux** couples de replis, et ils ne disent pas la meme
        # chose : « Revisions a jour » / « Aucun passage du » en haut, « A jour » / « Voir mes
        # revisions » en bas. Confondre les deux seconds — ce que fait cette mutation — laisse
        # la carte du bas annoncer un passage du qu'elle n'a pas, et le couple du bas perd sa
        # raison d'etre.
        "nom": "programme : les deux formes de la revision se confondent",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ProgramRenderer.kt",
        "avant": "?: \"Voir mes révisions\",",
        "apres": "?: \"Aucun passage dû\",",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ProgramRendererTest"],
    },
    {
        # Une seance **reportee** redevient reprise : sa carte est un bouton mort, et le geste
        # rouvre une seance que le programme ne propose plus. Le defaut ne se voit pas — le
        # reste a valider existe, donc la ligne s'affiche et a l'air juste.
        #
        # Ce cas remplace celui qui devait viser le **refus d'une reprise de revision**. Ce refus
        # est tenu par un seul mecanisme, et il est cote appelant : le filtre du programme, qui
        # n'admet que `StudyMode.LEARNING`. `resume` lui-meme ne refuse plus rien — depuis que le
        # tableau de bord partage son constructeur de reprise, il sert les deux modes et choisit
        # l'identite selon eux. Une mutation d'un seul point ne peut donc pas faire tomber ce
        # refus : le comportement reste epingle par `le programme ne sert pas une reprise de
        # revision`, dans `ProgramRendererTest`.
        "nom": "programme : une seance reportee redevient reprise",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ProgramRenderer.kt",
        "avant": "it.id == record.id && it.status == SessionStatus.TODO",
        "apres": "it.id == record.id",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ProgramRendererTest"],
    },
    {
        # La seance du jour devient celle qui **n'est pas** planifiee aujourd'hui : une seance
        # en retard prend alors la place de celle du jour, et le rattrapage se vide de ce qu'il
        # devait proposer. Le repli sur la premiere seance a venir masque une partie du defaut,
        # ce qui le rend d'autant plus discret.
        "nom": "programme : la seance du jour n'est plus celle d'aujourd'hui",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ProgramRenderer.kt",
        "avant": "WeeklyProgress.scheduledDate(it) == at",
        "apres": "WeeklyProgress.scheduledDate(it) != at",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ProgramRendererTest"],
    },
    {
        # L'historique **trie** au lieu de renverser. Sur un etat range dans l'ordre du
        # programme, les deux donnent la meme tete — mais pas le meme ordre des identifiants,
        # et surtout pas la meme regle : un etat dont l'ordre ne serait pas l'ordre du programme
        # ferait diverger les deux clients en silence.
        "nom": "programme : l'historique trie au lieu de renverser",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ProgramRenderer.kt",
        "avant": ".takeLast(HISTORY_LIMIT)",
        "apres": ".takeLast(HISTORY_LIMIT).sortedBy { it.id }",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ProgramRendererTest"],
    },
    # ------------------------------------------------------------------ les revisions (phase C)
    #
    # Le tableau de bord des revisions, la regle de consolidation, et le transport d'une tache par
    # la route. Trois choses qui se perdent en **silence** : une reprise servie sous le mauvais
    # mode apparait sur deux ecrans a la fois ; une consolidation prise pour une revision ordinaire
    # n'ouvre jamais l'etape des trois jours ; une tache transportee sans ses bornes s'ouvre en
    # lecture libre et n'est jamais validee.
    #
    # Les deux cas a ancre multi-ligne le sont parce que leur ancre d'une ligne apparait deux
    # fois : `study = StudySession.forTask(task),` est ecrit dans `consolidation` **et** dans
    # `priority`, et `addAll(bornes)` dans le bloc de la seance **et** dans celui de la tache.
    # Le fichier est en LF, donc `\n` suffit — verifie par `git ls-files --eol`.
    {
        # Le tableau de bord sert l'apprentissage au lieu de la revision. C'est le complement exact
        # du filtre du programme : le meme enregistrement apparait alors sur les deux ecrans, et
        # chacun le compte a sa facon.
        "nom": "revisions : le tableau de bord sert une reprise d'apprentissage",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ReviewDashboardRenderer.kt",
        "avant": "                .filter { it.mode == StudyMode.REVISION && it.status == StudyStatus.PARTIAL }",
        "apres": "                .filter { it.mode == StudyMode.LEARNING && it.status == StudyStatus.PARTIAL }",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ReviewDashboardRendererTest"],
    },
    {
        # La ligne de consolidation perd sa categorie : elle n'ouvre plus l'etape des trois jours,
        # et se valide comme une revision ordinaire — donc sous une cle que le plan ne relit pas.
        "nom": "revisions : la ligne de consolidation perd sa categorie",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ReviewDashboardRenderer.kt",
        "avant": "            category = ReviewCategory.RECENT,",
        "apres": "            category = ReviewCategory.HABITUAL,",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ReviewDashboardRendererTest"],
    },
    {
        # La regle de consolidation ne vaut plus rien : aucune tache n'ouvre l'etape des trois
        # jours, et le bandeau du lecteur annonce « Revision du jour » la ou il annoncait
        # « Consolidation · J+n ». La consolidation se perd sans que rien ne le dise.
        "nom": "revisions : la regle de consolidation ne vaut plus rien",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Review.kt",
        "avant": "        val isConsolidation: Boolean get() = category == ReviewCategory.RECENT",
        "apres": "        val isConsolidation: Boolean get() = false",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ReviewDashboardRendererTest"],
    },
    {
        # Une ligne prioritaire ouvre la consolidation. La categorie `priority` n'a rien a voir avec
        # un verset recemment appris : l'etape des trois jours s'ouvrirait sur un verset qui n'en a
        # pas, et le bandeau masquerait la progression au lieu de la compter.
        "nom": "revisions : une ligne prioritaire ouvre la consolidation",
        "fichier": "feature/program/src/main/kotlin/com/msoumaya/deepseekandroid/feature/program/ReviewDashboardRenderer.kt",
        "avant": "            detail = ReviewText.priorityDetail(Review.reviewQuantity(listOf(task.range))),\n            study = StudySession.forTask(task),",
        "apres": "            detail = ReviewText.priorityDetail(Review.reviewQuantity(listOf(task.range))),\n            study = StudySession.forTask(task, consolidation = true),",
        "tache": ":feature:program:testDebugUnitTest",
        "attendus": ["ReviewDashboardRendererTest"],
    },
    {
        # Le tableau de bord ne recoit plus de quoi se fermer. Plein ecran, sans barre superieure ni
        # barre basse, il n'a plus aucun moyen de revenir en arriere : l'application s'y enferme.
        "nom": "revisions : le tableau de bord ne recoit plus de quoi se fermer",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "        composable(AppRoutes.REVIEW) {\n            ReviewDashboardScreen(\n                onClose = { navController.popBackStack() },",
        "apres": "        composable(AppRoutes.REVIEW) {\n            ReviewDashboardScreen(\n                onClose = {},",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppScaffoldReviewEntryTest"],
    },
    {
        # L'accueil recolle sa route a la main au lieu de passer par la fonction partagee. C'est le
        # defaut d'origine, a la lettre : une tache ouverte depuis l'accueil perd son identite et sa
        # session, et se transforme en lecture libre qui ne valide rien.
        "nom": "revisions : l'accueil recolle sa route au lieu de passer par studyRoute",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "                onOpenStudy = { request -> navController.navigate(AppRoutes.studyRoute(request)) },\n                // Trois rappels de l'accueil",
        "apres": "                onOpenStudy = { request -> navController.navigate(AppRoutes.readerRoute(request.range.start)) },\n                // Trois rappels de l'accueil",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppScaffoldReviewEntryTest"],
    },
    {
        # La route d'une tache de revision perd ses bornes. C'est le defaut qui a reellement ete
        # ecrit, puis attrape : les bornes n'etaient posees que dans le bloc de la seance, donc une
        # revision — qui n'a pas d'identifiant de seance — partait sans `de` ni `a`. La route
        # s'ouvrait, la page s'affichait, et rien ne disait que la plage manquait.
        "nom": "route : une tache de revision perd ses bornes",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppDestination.kt",
        "avant": '                add("$READER_CATEGORY=$reviewCategory")\n                addAll(bornes)',
        "apres": '                add("$READER_CATEGORY=$reviewCategory")',
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppRoutesStudyTest"],
    },
    {
        # Le decodeur de categorie se replie au lieu de refuser. Une cle inconnue rend alors
        # `habitual`, et une revision s'enregistre sous une categorie que personne n'a choisie —
        # ce qui rend une consolidation meconnaissable dans l'historique.
        "nom": "route : le decodeur de categorie se replie au lieu de refuser",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Review.kt",
        "avant": "        ReviewCategory.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }",
        "apres": "        ReviewCategory.entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: ReviewCategory.HABITUAL",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppRoutesStudyTest"],
    },
    {
        # Le lecteur ne relit plus la categorie par le decodeur, mais par une comparaison recopiee
        # ici. Une consolidation — categorie `recent` — passerait alors pour une revision
        # ordinaire, et l'etape des trois jours ne s'ouvrirait jamais.
        "nom": "route : le lecteur n'utilise plus le decodeur de categorie",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "                                category = Review.categoryOf(categorie) ?: ReviewCategory.HABITUAL,",
        "apres": "                                category = ReviewCategory.HABITUAL,",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppScaffoldReviewEntryTest"],
    },

    # ------------------------------------------------- le panneau « Ma seance », et sa barre

    {
        # La consolidation se voit offrir **en plus** « Valider une partie ou toute la seance » :
        # deux chemins pour un meme geste, dont un seul enregistre l'etape. C'est le
        # `focused && !reader.consolidation` du client d'origine, perdu.
        "nom": "panneau : une consolidation se voit offrir la cloture d'apprentissage",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/SessionPanelText.kt",
        "avant": "        if (request.focused && !request.consolidation && Entry.VALIDATE in available) {",
        "apres": "        if (request.focused && Entry.VALIDATE in available) {",
        "tache": ":core:domain:test",
        "attendus": ["SessionPanelTextTest"],
    },
    {
        # La barre des notes apparait sur une consolidation. Une etape de consolidation ne se note
        # pas : elle se valide. La note choisie ne serait enregistree nulle part, et la barre
        # proposerait une action qui n'existe pas pour cette tache.
        "nom": "panneau : la barre des notes apparait sur une consolidation",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/SessionPanelText.kt",
        "avant": "        if (request.reviewing && !request.consolidation && Entry.GRADES in available) {",
        "apres": "        if (request.reviewing && Entry.GRADES in available) {",
        "tache": ":core:domain:test",
        "attendus": ["SessionPanelTextTest"],
    },
    {
        # « A reapprendre » depend de `revisionId` — l'identifiant du modele de revision
        # « legacy » —, et non de `reviewing`. Sur `reviewing`, l'entree apparaitrait sur toute
        # revision ordinaire, et un appui ferait repartir a un jour un verset qu'on n'a pas marque.
        "nom": "panneau : « a reapprendre » apparait sur toute revision",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/SessionPanelText.kt",
        "avant": "        if (request.revisionId != null && Entry.RELEARN in available) add(Entry.RELEARN)",
        "apres": "        if (request.reviewing && Entry.RELEARN in available) add(Entry.RELEARN)",
        "tache": ":core:domain:test",
        "attendus": ["SessionPanelTextTest"],
    },
    {
        # Le libelle perd l'echance : le bouton annonce « J+7 » alors qu'il valide J+1, ou
        # l'inverse. C'est l'ecart entre ce que le bouton dit et ce qu'il fait, et rien d'autre ne
        # le dirait — le bouton fonctionne, il annonce seulement la mauvaise etape.
        "nom": "panneau : le libelle de consolidation annonce toujours J+7",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/SessionPanelText.kt",
        "avant": "            (offset ?: StudySession.LAST_CONSOLIDATION_OFFSET)",
        "apres": "            StudySession.LAST_CONSOLIDATION_OFFSET",
        "tache": ":core:domain:test",
        "attendus": ["SessionPanelTextTest"],
    },
    {
        # Un geste sans destination reste dans la barre : « Ma voix » s'affiche et ne fait rien.
        # C'est ce que ce lecteur refuse ailleurs — une entree sans destination se **retire**, elle
        # ne se grise pas et ne reste pas inerte.
        "nom": "barre : un geste sans destination reste dans la barre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ReviewText.kt",
        "avant": "    fun actionBar(available: Set<Action>): List<Action> = Action.entries.filter { it in available }",
        "apres": "    fun actionBar(available: Set<Action>): List<Action> = Action.entries",
        "tache": ":core:domain:test",
        "attendus": ["ReviewTextTest"],
    },
    {
        # Le trait de separation glisse d'un rang : il tombe au milieu des trois notes, et separe
        # deux gestes de meme nature au lieu de separer les notes de l'ecoute.
        "nom": "barre : le trait de separation ne tombe plus au quatrieme rang",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/RevisionActionBar.kt",
        "avant": "private const val SEPARATOR_BEFORE = 3",
        "apres": "private const val SEPARATOR_BEFORE = 4",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["RevisionActionBarWiringTest"],
    },
    {
        # Le geste du bandeau ne suit plus le genre de la tache : une consolidation ouvre la
        # feuille de validation au lieu de son panneau, et l'etape des trois jours n'est plus
        # validable depuis le bandeau qui l'a proposee.
        "nom": "bandeau : le geste ne suit plus le genre de la tache",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    if (seance.request.consolidation) {",
        "apres": "                    if (false) {",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["StudyChromeWiringTest"],
    },
    {
        # La garde du bouton mort tombe : les trois etapes faites, le panneau propose quand meme
        # « Valider la consolidation », et l'appui ne fait rien — sa garde sort avant d'ecrire.
        # Un bouton qui ne fait rien, sans que rien ne le dise.
        "nom": "lecteur : le bouton mort de consolidation revient",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "                    ?.takeIf { etat.consolidationOffset != null }",
        "apres": "                    ?.takeIf { true }",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["SessionPanelWiringTest"],
    },
    {
        # L'etape visee n'est plus epinglee : `completeConsolidation` valide la premiere etape non
        # faite, donc un second appui validerait l'etape **suivante** — et l'ecran se ferme comme si
        # tout allait bien. Rien ne distingue les deux cas a l'ecran.
        "nom": "route : l'etape de consolidation n'est plus epinglee",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "                                targetOffset = cible,",
        "apres": "                                targetOffset = null,",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteSessionTest"],
    },
    {
        # Le programme est regenere sans l'etat note. C'est exactement le defaut que le client
        # d'origine evite en enchainant `generateProgram` **sur** le resultat de `gradeRevision` :
        # le verset marque repart a un jour sans que le programme en tienne compte.
        "nom": "route : le programme est regenere sans l'etat note",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "                            Program.generateProgram(\n                                Program.gradeRevision(state, revision, LegacyReviewGrade.RELEARN),\n                            )",
        "apres": "                            Program.gradeRevision(state, revision, LegacyReviewGrade.RELEARN)",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteSessionTest"],
    },
    {
        # La demande d'ouverture devient un **etat sauvegarde** : une rotation rouvre le panneau
        # qu'on venait de refermer, alors que le client d'origine ne le rouvre pas. C'est la
        # difference entre un evenement et un etat.
        "nom": "route : la demande d'ouverture est sauvegardee",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "    var sessionPanelRequest by remember { mutableIntStateOf(0) }",
        "apres": "    var sessionPanelRequest by rememberSaveable { mutableIntStateOf(0) }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteSessionTest"],
    },
    {
        # L'entree du selecteur existe pour une lecture libre : elle propose des actions de seance
        # sur une plage qui n'en a pas, et le panneau s'ouvre sur les gestes d'une tache qui
        # n'existe pas.
        "nom": "route : l'entree du selecteur existe pour une lecture libre",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "        val actionsSeance = session?.takeIf { it.focused }?.let { requete ->",
        "apres": "        val actionsSeance = session?.let { requete ->",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteSessionTest"],
    },
    {
        # L'entree nomme la plage **demandee** au lieu de la plage **ouverte** : elle annonce des
        # versets que la reprise ne relira pas, ce qui est pire qu'un libelle muet — il est faux.
        "nom": "route : l'entree du selecteur nomme la plage demandee",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "            val ouverte = userState?.let { StudySession.opening(it, requete) } ?: requete",
        "apres": "            val ouverte = requete",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteSessionTest"],
    },
    {
        # La route ecrit puis ferme ; inversees, l'ecriture est annulee en vol par la mort de la
        # portee — et le symptome est exactement celui d'une ecriture qui n'a jamais eu lieu :
        # l'ecran se ferme normalement, et la seance reste a reporter.
        "nom": "route : la seance est reportee apres la fermeture",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt",
        "avant": "                    runCatching {\n                        container.userState.mutate { state -> Program.postponeSession(state, seance) }\n                    }\n                    quitter()",
        "apres": "                    quitter()\n                    runCatching {\n                        container.userState.mutate { state -> Program.postponeSession(state, seance) }\n                    }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["ReaderRouteSessionTest"],
    },
    {
        # L'entree du selecteur ne nomme plus la plage : « Actions de la seance · » sans rien. Le
        # libelle ne dit plus sur quoi on va agir, et c'est la seule chose qu'il apporte — le
        # selecteur, lui, est un choix de presentation.
        "nom": "selecteur : l'entree de seance ne nomme plus la plage",
        "fichier": "feature/sources/src/main/kotlin/com/msoumaya/deepseekandroid/feature/sources/QuranSourcePicker.kt",
        "avant": "                        text = QuranDownloadText.sessionActions(actions.reference),",
        "apres": "                        text = QuranDownloadText.sessionActions(\"\"),",
        "tache": ":feature:sources:testDebugUnitTest",
        "attendus": ["QuranSourcePickerEntryTest"],
    },
    {
        # L'objectif remplace l'objet entier au lieu de son libelle et de ses plages. L'echeance
        # posee a l'etape precedente du brouillon est alors perdue : l'ecran garde la date
        # affichee, et le repere enregistre est vide. C'est le defaut que ce cas a fait tomber
        # pendant l'ecriture de l'ecran — la source, elle, ecrit `{ ...next.goal, label, ranges }`.
        "nom": "objectif : le brouillon perd l'echeance en changeant d'objectif",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "            next = next.copy(goal = next.goal.copy(label = goal.label, ranges = goal.ranges))",
        "apres": "            next = next.copy(goal = goal)",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["le brouillon porte les quatre pieces"],
    },
    {
        # L'objectif ne part plus du premier verset. Le programme commencerait alors au milieu de
        # la division visee, et l'apercu annoncerait un passage que l'enregistrement ne
        # programmerait pas.
        "nom": "objectif : la plage ne part plus du premier verset",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        previous.copy(label = choice.label, ranges = listOf(Range(1, choice.end)))",
        "apres": "        previous.copy(label = choice.label, ranges = listOf(Range(choice.number, choice.end)))",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["l'objectif part toujours du premier verset"],
    },
    {
        # L'unite de rythme est deduite d'une condition recopiee de la source, qui oublie
        # `halfHizb` et `hizb`. C'est le defaut d'origine, et ce cas verifie qu'il est bien
        # detecte : un rythme enregistre en hizb s'afficherait « Par page », et un appui sur
        # « + » le remplacerait silencieusement par une demi-page.
        "nom": "rythme : l'unite est deduite d'une condition qui oublie deux rythmes",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        GoalPaceUnit.entries.firstOrNull { pace in paceOptions(it) } ?: GoalPaceUnit.PER_PAGE",
        "apres": "        when {\n            pace in listOf(Pace.VERSE1, Pace.VERSE2, Pace.VERSE3, Pace.VERSE4, Pace.VERSE5) ->\n                GoalPaceUnit.PER_VERSE\n            pace == Pace.QUARTER -> GoalPaceUnit.PER_RUBU\n            else -> GoalPaceUnit.PER_PAGE\n        }",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["les deux rythmes que la source oublie"],
    },
    {
        # L'apercu est calcule sur l'etat enregistre et non sur le brouillon. La carte
        # « Programme genere » annoncerait alors un passage, et l'enregistrement en ecrirait un
        # autre — sans que rien ne le dise, puisque les deux ecrans seraient plausibles.
        "nom": "rendu : l'apercu est calcule sur l'etat enregistre",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        preview = preview(brouillon(state, fields), at),",
        "apres": "        preview = preview(state, at),",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["l'apercu annonce la seance que la sauvegarde programme"],
    },
    {
        # Les revisions initiales ne sont plus ouvertes. L'application croirait alors neufs des
        # versets declares connus, et les reproposerait a l'apprentissage.
        "nom": "enregistrement : les revisions initiales ne sont plus ouvertes",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        Program.generateProgram(Program.seedInitialRevisions(Program.touch(draft), at), at)",
        "apres": "        Program.generateProgram(Program.touch(draft), at)",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["l'enregistrement ajoute les revisions initiales"],
    },
    {
        # Le champ de verset est offert pour toutes les unites. On pourrait alors declarer
        # « hizb 12, verset 200 », qui ne veut rien dire.
        "nom": "rendu : le champ de verset est offert pour toutes les unites",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        verseChoices = if (fields.knownUnit == GoalUnit.SURAH) {\n            verseChoices(fields.surah)\n        } else {\n            emptyList()\n        },",
        "apres": "        verseChoices = verseChoices(fields.surah),",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["le rendu ne propose de versets que pour une sourate"],
    },
    {
        # Le pas de rythme n'est plus borne a son unite : au dernier cran, l'index sort de la
        # liste. Le defaut ne rend pas un mauvais rythme, il fait tomber l'ecran.
        "nom": "rythme : le pas n'est plus borne a son unite",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        val cible = (depart + delta).coerceIn(0, options.size - 1)",
        "apres": "        val cible = (depart + delta).coerceAtLeast(0)",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["le pas de rythme est borne a son unite"],
    },
    {
        # Une sourate se declare par une division : le champ unique proposerait des sourates
        # « connues » sans dire jusqu'ou, ce qui est exactement ce que le second champ existe
        # pour eviter.
        "nom": "connaissance : une sourate se declare par une division",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        GoalUnit.SURAH -> emptyList()",
        "apres": "        GoalUnit.SURAH -> knownSurahChoices()",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["une sourate ne se declare pas par une division"],
    },
    {
        # Les options d'objectif d'une sourate perdent leur nom : « Finir le Sourate 2 » au lieu de
        # « Finir Al-Baqara ».
        "nom": "objectif : les options de sourate perdent leur nom",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "            choice(it.number, GoalText.finishSurah(it.name), it.end)",
        "apres": "            choice(it.number, GoalText.finishDivision(GoalText.SURAH, it.number), it.end)",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["les options d'objectif nomment la division selon son unite"],
    },
    {
        # L'aller-retour de la date est retire. Un analyseur permissif accepte alors le 31 fevrier
        # et le rend au 28 : l'echeance enregistree ne serait pas celle qu'on a tapee.
        "nom": "date : l'aller-retour de la date est retire",
        "fichier": "feature/profile/src/main/kotlin/com/msoumaya/deepseekandroid/feature/profile/GoalRenderer.kt",
        "avant": "        return Dates.dateKey(date) == text",
        "apres": "        return true",
        "tache": ":feature:profile:testDebugUnitTest",
        "attendus": ["un jour inexistant est refuse"],
    },
    {
        # La route de l'objectif rend de nouveau un panneau de phase. L'ecran livre serait
        # inatteignable — et la compilation ne dirait rien, puisque `ProfileScreen` existe
        # toujours.
        "nom": "route : l'ecran d'objectif n'est plus branche",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "        composable(AppRoutes.GOAL) { GoalScreen(onClose = { navController.popBackStack() }) }",
        "apres": "        composable(AppRoutes.GOAL) { ProfileScreen(mode = ProfileMode.PROFILE) }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["AppScaffoldGoalEntryTest"],
    },
    {
        # L'ecran d'objectif est branche sans `onClose`. Plein ecran, sans onglet ni barre
        # superieure, il n'aurait plus aucun moyen de revenir en arriere.
        "nom": "route : l'ecran d'objectif ne recoit plus de quoi se fermer",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "        composable(AppRoutes.GOAL) { GoalScreen(onClose = { navController.popBackStack() }) }",
        "apres": "        composable(AppRoutes.GOAL) { GoalScreen() }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["l'ecran d'objectif recoit de quoi se fermer"],
    },
    {
        # Le parametre de source du calcul des pages est **inerte sur les donnees livrees** :
        # mesure, aucune des 30 juz ni des 60 hizb ne change de page entre les trois decoupages
        # du projet. Le controle sur les donnees reelles passerait donc meme si la source etait
        # ignoree ; c'est ce cas-ci, sur une division fabriquee, qui prouve que la regle est
        # suivie. Le retirer laisserait la regle sans garde.
        "nom": "coran : le decoupage des pages ignore la source affichee",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/QuranListRenderer.kt",
        "avant": "                    first = StudyProgressCalculator.studyPage(division.start, studySource),\n                    last = StudyProgressCalculator.studyPage(division.end, studySource),",
        "apres": "                    first = StudyProgressCalculator.studyPage(division.start, \"traditional\"),\n                    last = StudyProgressCalculator.studyPage(division.end, \"traditional\"),",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["les pages d'une division se calculent"],
    },
    {
        # `studyPage` n'est pas lineaire : deduire la fin du debut donnerait un compte faux sur
        # les divisions qui chevrochent une page. Le cas ne fait tomber que le controle des deux
        # bouts — celui du decoupage, lui, reste vert, parce que sa division fabriquee a
        # `start == end`.
        "nom": "coran : la fin d'une division est deduite de son debut",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/QuranListRenderer.kt",
        "avant": "                    last = StudyProgressCalculator.studyPage(division.end, studySource),",
        "apres": "                    last = StudyProgressCalculator.studyPage(division.start, studySource),",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["les deux bouts d'une division"],
    },
    {
        # Le crayon de la carte « J'ai appris jusqu'a » ouvre l'ecran d'objectif. Le remplacer par
        # une autre route compile parfaitement : c'est exactement le defaut qu'un rappel de
        # navigation laisse passer.
        "nom": "route : le crayon des connaissances ne mene plus a l'objectif",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "                    navController.navigate(AppRoutes.GOAL) { launchSingleTop = true }",
        "apres": "                    navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["le crayon de la carte des connaissances"],
    },
    {
        # Une ligne de la liste doit ouvrir le lecteur par la porte commune. Naviguer vers la
        # route nue compile aussi : le lecteur s'ouvrirait, mais sans le verset demande — donc
        # sur une autre page, et sans rien dire.
        "nom": "route : l'ecran du Coran n'ouvre plus le lecteur par la porte commune",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "                onOpenReader = { range -> navController.navigate(AppRoutes.readerRoute(range.start)) },",
        "apres": "                onOpenReader = { navController.navigate(AppRoutes.READER) },",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["l'ecran du Coran ouvre le lecteur par la porte commune"],
    },
    {
        # Le denominateur de l'anneau et le compteur de versets ne comptent pas la meme unite :
        # `verses.size` est un nombre de versets (6 236), `totalVolume` la somme des poids en
        # lettres arabes (320 543). Les confondre n'empeche pas de compiler, ne fait rien planter,
        # et affiche « / 320543 » sous un compteur de versets : cela ne se voit qu'a l'ecran.
        "nom": "progres : le denominateur du compteur devient le volume en lettres",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressRenderer.kt",
        "avant": "                totalVerses = Quran.verses.size,",
        "apres": "                totalVerses = Quran.totalVolume,",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["le denominateur du compteur est le nombre de versets"],
    },
    {
        # Une sixieme fenetre mensuelle glisse d'une semaine le decoupage du mois : les barres
        # restent plausibles, les totaux restent justes, mais le mois se lit sur six colonnes au
        # lieu de cinq. Aucun test de total ne le verrait.
        "nom": "progres : le graphique du mois compte une semaine de trop",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressRenderer.kt",
        "avant": "        val count = if (monthly) 5 else 7",
        "apres": "        val count = if (monthly) 6 else 7",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["le graphique du mois compte cinq semaines"],
    },
    {
        # « Jours actifs » compte des jours, pas des seances : trois seances le meme jour font un
        # jour. Compter les seances compile et donne un nombre plus grand — donc flatteur, et faux.
        "nom": "progres : le compteur de jours actifs compte les seances",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressRenderer.kt",
        "avant": "                Counter(CounterKind.ACTIVE_DAYS, activity.dates.size),",
        "apres": "                Counter(CounterKind.ACTIVE_DAYS, state.sessions.size),",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["le compteur de jours actifs compte les jours, pas les seances"],
    },
    {
        # Une derniere lecture sans liste de pages vient d'un ancien schema : le client d'origine
        # affiche alors une page. Confondre « absent » et « vide » ferait tomber ce cas a zero, et
        # personne ne verrait qu'une lecture reelle a disparu du compte.
        "nom": "progres : la derniere lecture sans liste ne compte plus",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressRenderer.kt",
        "avant": "            state.lastRead != null -> 1",
        "apres": "            state.lastRead != null -> 0",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["les pages lues distinguent une liste absente d'une liste vide"],
    },
    {
        # Une seance suivie par sa progression fine ne doit pas etre comptee deux fois — une fois
        # par la seance, une fois par ses validations. Inverser le filtre est une faute d'un
        # caractere qui gonfle le graphique sans rien casser.
        "nom": "progres : une seance suivie est comptee deux fois",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressRenderer.kt",
        "avant": "            .filter { it.status == SessionStatus.DONE && it.id !in trackedIds }",
        "apres": "            .filter { it.status == SessionStatus.DONE && it.id in trackedIds }",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["une seance suivie par sa progression n'est pas comptee deux fois"],
    },
    {
        # « Voir tout », dans l'en-tete des objectifs, ouvre l'ecran d'objectif. Y mettre une autre
        # route compile parfaitement : c'est le defaut qu'un rappel de navigation laisse passer.
        "nom": "progres : la carte d'objectif ne mene plus a l'objectif",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        # L'ancre porte la ligne `ProgressScreen(` : le meme appel existe tel quel dans le bloc de
        # l'accueil, et viser le mauvais bloc aurait mute un ecran que ce cas n'annonce pas.
        "avant": "            ProgressScreen(\n                onOpenGoal = { navController.navigate(AppRoutes.GOAL) { launchSingleTop = true } },",
        "apres": "            ProgressScreen(\n                onOpenGoal = { navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true } },",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["la carte d'objectif mene a l'ecran d'objectif"],
    },
    {
        # Sans `launchSingleTop`, deux appuis sur « Voir tout » empilent deux ecrans d'objectif :
        # le retour en laisse un en travers. La difference ne se voit qu'a l'usage.
        "nom": "progres : l'objectif s'ouvre sans launchSingleTop",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        # Meme ancre que le cas precedent, et pour la meme raison : le bloc de l'accueil porte le
        # meme appel, au caractere pres.
        "avant": "            ProgressScreen(\n                onOpenGoal = { navController.navigate(AppRoutes.GOAL) { launchSingleTop = true } },",
        "apres": "            ProgressScreen(\n                onOpenGoal = { navController.navigate(AppRoutes.GOAL) },",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["l'ouverture de l'objectif n'empile pas deux ecrans"],
    },
    {
        # Une revision n'est pas une seance terminee : elle ne doit pas ouvrir un jour actif. Le
        # mode etant une enumeration a deux valeurs, inverser le test n'est pas une mutation
        # equivalente — c'est exactement l'inversion qui fait entrer les revisions.
        "nom": "progres : une revision compte comme un jour actif",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Activity.kt",
        "avant": "            if (record.mode != StudyMode.LEARNING) continue",
        "apres": "            if (record.mode == StudyMode.LEARNING) continue",
        "tache": ":core:domain:test",
        "attendus": ["une validation de revision ne compte pas"],
    },
    {
        # Le client d'origine ecrit « jours » au pluriel quelle que soit la valeur, donc « 1 jours
        # d'affilee » le premier jour. L'accord est conserve ici : ce cas tient le singulier.
        "nom": "progres : la serie perd son singulier",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ProgressText.kt",
        "avant": '    fun streak(days: Int): String = "$days jour${if (days > 1) "s" else ""} d’affilée"',
        "apres": '    fun streak(days: Int): String = "$days jours d’affilée"',
        "tache": ":core:domain:test",
        "attendus": ["la serie s'accorde au singulier et au pluriel"],
    },
    {
        # « Jour » decrit une fenetre glissante de sept jours, pas la journee : reprendre le nom du
        # selecteur serait plus simple et plus faux. Le titre est le seul endroit qui le dise.
        "nom": "progres : le titre du graphique du jour reprend le nom du selecteur",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/ProgressText.kt",
        "avant": '        Period.DAY -> "Les 7 derniers jours"',
        "apres": '        Period.DAY -> "Jour"',
        "tache": ":core:domain:test",
        "attendus": ["le titre du graphique ne suit pas le nom de la periode"],
    },

    # --- Amis : le rendu de la liste ---------------------------------------------------------
    {
        # `Social.otherId` retombe sur le demandeur quand on ne reconnait ni l'un ni l'autre
        # participant. Avec un identifiant vide, **chaque** lien rend donc son demandeur, et la
        # liste affiche des gens au hasard. La faute est silencieuse : la liste s'affiche.
        "nom": "amis : sans profil, la liste se calcule quand meme",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "        val me = state.profile?.id",
        "apres": '        val me = state.profile?.id ?: ""',
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["sans profil, aucune liste d'amis n'est calculee"],
    },
    {
        # Les apercus sont indexes par **lien** et les presences par **personne**. Les confondre
        # n'affiche ni erreur ni ami : seulement des amis qui paraissent tous hors ligne.
        "nom": "amis : la presence est lue sous la cle du lien",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "        val online = state.online[other] == true",
        "apres": "        val online = state.online[link.id] == true",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["la presence se lit sur la personne, jamais sur le lien"],
    },
    {
        # La presence passe avant la date parce qu'elle est vraie **maintenant**. L'inverser ferait
        # dire « 2 mars 09:30 » a un ami qui est en ligne a l'instant.
        "nom": "amis : la date passe avant la presence",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "            subtitle = if (online) {",
        "apres": "            subtitle = if (false) {",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["un ami dont la presence est connue et vraie dit En ligne"],
    },
    {
        # Un lien que le serveur rend sans profil ne doit pas faire disparaitre le nom : la
        # conversation existe, et la cacher ferait croire a une amitie perdue.
        "nom": "amis : un ami sans profil perd son nom",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "            name = link.other?.displayName ?: SocialText.FRIEND,",
        "apres": '            name = link.other?.displayName ?: "",',
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["un ami sans profil porte le nom de repli"],
    },
    {
        # Le nombre du titre est le nombre d'amis **acceptes** : il ne bouge ni au filtre, ni a la
        # recherche, ni au depliage. Le faire suivre le filtre annoncerait « Mes amis (0) » a
        # quelqu'un qui cherche un nom qu'il a mal orthographie.
        "nom": "amis : le compte du titre suit le filtre",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "            friendCount = Social.acceptedCount(links),",
        "apres": "            friendCount = Social.acceptedCount(links.filter { inputs.filter == Social.Filter.ALL }),",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["le compte du titre ne depend ni du filtre, ni de la recherche"],
    },
    {
        # Le code d'invitation vient du profil. Le perdre laisserait la carte d'invitation sans
        # code — ou, ici, la ferait disparaitre, et personne ne saurait pourquoi.
        "nom": "amis : le code d'invitation n'est plus lu",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "            inviteCode = state.profile?.inviteCode,",
        "apres": "            inviteCode = null,",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["avec un profil, la liste est calculee et le code est la"],
    },
    {
        # Le cercle de l'administration vit dans la meme table que les autres, et son nom ne dit
        # pas sa nature : « Contact · <nom> ». C'est ce drapeau qui la dit.
        "nom": "amis : le cercle administrateur n'est plus signale",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "                CircleRow(id = it.id, name = it.name, isAdminContact = it.contactUserId != null)",
        "apres": "                CircleRow(id = it.id, name = it.name, isAdminContact = false)",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["un cercle marque comme contact administrateur est signale"],
    },
    {
        # Un code fait d'espaces n'est pas un code. `isNotEmpty` au lieu de `isNotBlank` active le
        # bouton sur une espace tapee par erreur, et le serveur refuse un code qui n'existe pas.
        "nom": "amis : une espace suffit a activer l'invitation",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "            canSendInvitation = inputs.code.isNotBlank() && !state.busy,",
        "apres": "            canSendInvitation = inputs.code.isNotEmpty() && !state.busy,",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["l'invitation ne part pas avec un code vide ou fait d'espaces"],
    },

    # --- Amis : les regles du domaine --------------------------------------------------------
    {
        # « Demandes » **vide** la liste au lieu de la remplir : dans l'original, la section des
        # demandes vit sous la liste, et choisir ce filtre la deplie sans meler les deux.
        "nom": "amis : le filtre Demandes remplit la liste au lieu de la vider",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "            .filter { filter != Filter.REQUESTS }",
        "apres": "            .filter { true }",
        "tache": ":core:domain:test",
        "attendus": ["le filtre Demandes vide la liste au lieu de la remplir"],
    },
    {
        # La coupe a cinq entrees est ce qui rend « Voir tout » utile. Sans elle, la liste entiere
        # s'affiche et le depliage ne fait plus rien — un bouton sans effet.
        "nom": "amis : la liste n'est plus bornee a cinq",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        return if (all) visibles else visibles.take(LIST_LIMIT)",
        "apres": "        return if (all) visibles else visibles",
        "tache": ":core:domain:test",
        "attendus": ["la liste est bornee a cinq entrees, et le pli la libere"],
    },
    {
        # La recherche porte sur le nom affiche, sans distinguer la casse. La retirer ne fait pas
        # d'erreur : elle rend simplement une liste vide sur une recherche qui devrait trouver.
        "nom": "amis : la recherche distingue la casse",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        val needle = query.lowercase()",
        "apres": "        val needle = query",
        "tache": ":core:domain:test",
        "attendus": ["la recherche ignore la casse"],
    },
    {
        # Le nom d'un cercle se juge **rogne** : sans le rognage, « A » entoure d'espaces passe
        # pour deux caracteres, et le serveur refuse le cercle.
        "nom": "amis : le nom de cercle n'est plus rogne",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "    fun validGroupName(raw: String): Boolean = raw.trim().length >= MIN_GROUP_NAME",
        "apres": "    fun validGroupName(raw: String): Boolean = raw.length >= MIN_GROUP_NAME",
        "tache": ":core:domain:test",
        "attendus": ["un nom de cercle demande deux caracteres utiles"],
    },
    {
        # Une suspension **sans terme** est active pour toujours : c'est le cas d'une exclusion. La
        # lire comme « expiree » rouvrirait la messagerie a quelqu'un qu'on vient d'en ecarter.
        "nom": "amis : une suspension sans terme est lue comme expiree",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        val until = suspension.suspendedUntil ?: return true",
        "apres": "        val until = suspension.suspendedUntil ?: return false",
        "tache": ":core:domain:test",
        "attendus": ["une suspension sans terme est active pour toujours"],
    },
    {
        # Le client d'origine demande `fr-FR` explicitement. Un telephone regle en anglais doit
        # afficher « 2 mars 09:30 », et non « Mar 2, 09:30 » : les deux clients montreraient sinon
        # la meme conversation de deux facons.
        "nom": "amis : la date d'un message suit la locale de l'appareil",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/SocialText.kt",
        "avant": '    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", FR)',
        "apres": '    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)',
        "tache": ":core:domain:test",
        "attendus": ["l'instant d'un message est ecrit au format de l'original"],
    },
    {
        # Le controle des etiquettes lit les constantes sur l'objet lui-meme. Une constante videe
        # doit donc tomber : c'est ce qui prouve que le controle mesure, et qu'il ne se contente pas
        # de parcourir une liste recopiee a la main.
        "nom": "amis : une etiquette est videe",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/SocialText.kt",
        "avant": '    const val CANCEL = "Annuler"',
        "apres": '    const val CANCEL = ""',
        "tache": ":core:domain:test",
        "attendus": ["toutes les etiquettes fixes sont renseignees"],
    },

    # --- Amis : le branchement de l'ecran ----------------------------------------------------
    {
        # Un rappel de geste a une valeur par defaut vide : l'oublier compile, s'affiche, et laisse
        # un bouton sans effet. C'est deja arrive sur l'accueil, ou trois rappels oublies faisaient
        # trois boutons morts.
        "nom": "amis : le geste d'administration n'est plus branche",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialScreen.kt",
        "avant": "        onAdminContact = viewModel::onOpenAdminContact,",
        "apres": "        onAdminContact = {},",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["l'ecran branche toutes les saisies et tous les gestes"],
    },
    {
        # Le depot charge a sa construction, mais il ne sait pas quand l'onglet s'ouvre. Sans cet
        # appel, la liste s'affiche et reste **perimee** : un ami accepte depuis l'autre appareil
        # n'apparaitrait jamais, et rien ne le dirait.
        "nom": "amis : l'ecran ne se relit plus a l'ouverture",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialScreen.kt",
        "avant": "    LaunchedEffect(Unit) { viewModel.onVisible() }",
        "apres": "    LaunchedEffect(Unit) { }",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["l'ecran se relit a l'ouverture"],
    },
    {
        # L'onglet « Amis » a longtemps rendu un panneau de phase. Le remplacer par un autre ecran
        # compile parfaitement : la route est servie, et ce n'est plus la liste d'amis.
        "nom": "amis : l'onglet ne rend plus l'ecran des amis",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        # La mutation remplace l'ecran **entier**, arguments compris : garder `SocialScreen(` en
        # changeant seulement le nom du composable ne compilerait pas — `onChallenge` n'existe pas
        # ailleurs —, et le cas prouverait une erreur de compilation au lieu d'un test rouge.
        "avant": "        composable(AppDestination.FRIENDS.route) {\n            SocialScreen(\n                onChallenge = { friendId ->\n                    navController.navigate(AppRoutes.quizRoute(friendId = friendId))\n                },\n            )\n        }",
        "apres": "        composable(AppDestination.FRIENDS.route) {\n            ProgressScreen()\n        }",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["l'onglet Amis rend l'ecran des amis"],
    },

    # --- Conversation : les regles de l'historique -------------------------------------------
    {
        # Une page courte est la **premiere** de la conversation. Ouvrir quand meme la porte
        # afficherait « Charger les messages precedents » sous une conversation dont on sait
        # qu'elle commence la : un chargement qui ne rend rien, et rien ne le dirait.
        "nom": "conversation : une page courte ouvre quand meme l'historique",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "            history.copy(hasOlder = history.hasOlder || size >= MESSAGE_PAGE)",
        "apres": "            history.copy(hasOlder = true)",
        "tache": ":core:domain:test",
        "attendus": ["une page pleine ouvre l'historique, une page incomplete ne l'ouvre pas"],
    },
    {
        # `exhausted` est ce qui empeche le bouton de **clignoter** : une fois le debut atteint,
        # une page recente redevient pleine des qu'un message arrive, et rouvrirait la porte.
        "nom": "conversation : un debut connu se rouvre a chaque message",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        if (history.exhausted) {",
        "apres": "        if (false) {",
        "tache": ":core:domain:test",
        "attendus": ["une page ancienne incomplete referme l'historique pour toujours"],
    },
    {
        # Fusionner sans fusionner : la page recue s'ajoute au lieu de remplacer par identifiant.
        # Deux consequences, et les deux sont mesurees — le meme message en double, et l'ordre
        # perdu.
        "nom": "conversation : la fusion empile la page au lieu de la fusionner",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        return byId.values.sortedBy { it.createdAt }",
        "apres": "        return previous + incoming",
        "tache": ":core:domain:test",
        "attendus": [
            "la fusion remplace par identifiant au lieu d'empiler",
            "la fusion trie par instant croissant, donc une page ancienne passe devant",
        ],
    },
    {
        # Le tri a l'envers : la page ancienne est lue **apres** la recente et doit se peindre
        # **avant** elle. Inverse, il met le debut de la conversation a la fin.
        "nom": "conversation : le tri des messages est decroissant",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        return byId.values.sortedBy { it.createdAt }",
        "apres": "        return byId.values.sortedByDescending { it.createdAt }",
        "tache": ":core:domain:test",
        "attendus": ["la fusion trie par instant croissant, donc une page ancienne passe devant"],
    },

    # --- Conversation : l'en-tete et les droits ----------------------------------------------
    {
        # L'activite de l'autre passe avant tout : c'est ce qui se passe **maintenant**. La
        # deplacer sous la nature du cercle ferait taire « Ecrit un message... » dans le seul
        # ecran ou l'on attend une reponse.
        "nom": "conversation : la presence passe avant l'activite de l'autre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        otherTyping -> SocialText.TYPING\n        adminContact -> SocialText.ADMIN_CONTACT_LABEL",
        "apres": "        adminContact -> SocialText.ADMIN_CONTACT_LABEL\n        otherTyping -> SocialText.TYPING",
        "tache": ":core:domain:test",
        "attendus": ["la ligne d'etat suit l'ordre activite, cercle, presence"],
    },
    {
        # Un moderateur qui peut exclure le proprietaire peut s'emparer du cercle.
        "nom": "conversation : un moderateur peut retirer le proprietaire",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "    ): Boolean = member.userId != me && member.role != GroupRole.OWNER && isManager(members, me)",
        "apres": "    ): Boolean = member.userId != me && isManager(members, me)",
        "tache": ":core:domain:test",
        "attendus": ["un moderateur ne peut pas retirer le proprietaire"],
    },
    {
        # Le proprietaire qui se demet laisse le cercle sans personne pour le gerer.
        "nom": "conversation : le proprietaire peut se demettre lui-meme",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        member.userId != me && member.acceptedAt != null && isOwner(members, me)",
        "apres": "        member.acceptedAt != null && isOwner(members, me)",
        "tache": ":core:domain:test",
        "attendus": ["le proprietaire ne peut pas se demettre lui-meme"],
    },
    {
        # « non vide » n'est pas « non blanc » : une ligne d'espaces passerait et ecrirait un
        # message vide dans la conversation de l'autre.
        "nom": "conversation : un message fait d'espaces part quand meme",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        !busy && draft.isNotBlank() && !suspended",
        "apres": "        !busy && draft.isNotEmpty() && !suspended",
        "tache": ":core:domain:test",
        "attendus": ["un message fait d'espaces ne part pas"],
    },
    {
        # Dans un cercle, l'etape partirait a plusieurs ; vers la moderation, on ne partage pas
        # sa progression. L'original ne l'a jamais permis.
        "nom": "conversation : le partage d'etape n'est plus reserve au tete-a-tete",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        isLink && !adminContact",
        "apres": "        true",
        "tache": ":core:domain:test",
        "attendus": ["le partage d'etape est reserve au tete-a-tete"],
    },
    {
        # Accepter sa propre proposition ecraserait l'attente de l'autre, et le bouton
        # « Accepter » s'afficherait sur ce qu'on vient soi-meme de proposer.
        "nom": "conversation : on peut accepter sa propre proposition",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        acceptedAt == null && proposedBy != me",
        "apres": "        acceptedAt == null",
        "tache": ":core:domain:test",
        "attendus": ["une proposition ne s'accepte ni deux fois ni la sienne"],
    },

    # --- Conversation : le depot ------------------------------------------------------------
    {
        # Le curseur doit etre l'instant du message **le plus ancien** affiche : la requete
        # demande ce qui lui est strictement anterieur. Partir du plus recent rend une page qui
        # **recouvre** celle qu'on a deja.
        "nom": "conversation : la page ancienne se demande a partir du mauvais message",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt",
        "avant": "        val oldest = room.messages.firstOrNull() ?: return",
        "apres": "        val oldest = room.messages.lastOrNull() ?: return",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la page ancienne se demande a partir du plus ancien message affiche"],
    },
    {
        # Ne pas appliquer la transition d'historique laisse le bouton ouvert sur un debut de
        # conversation deja atteint : un chargement qui ne rend rien, a chaque appui.
        "nom": "conversation : la page ancienne ne referme plus l'historique",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt",
        "avant": "                    history = Social.afterOlderPage(it.history, older.size),",
        "apres": "                    history = it.history,",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une page ancienne incomplete referme le bouton pour de bon"],
    },
    {
        # Garder l'etat de la piece precedente afficherait les propos d'un ami **sous le nom d'un
        # autre** — la faute la plus grave que cet ecran puisse commettre.
        "nom": "conversation : ouvrir une piece garde les messages de la precedente",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt",
        "avant": "        _state.value = _state.value.copy(room = RoomState(room = room, loading = true))",
        "apres": "        _state.value = _state.value.copy(room = (_state.value.room ?: RoomState(room)).copy(room = room, loading = true))",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["ouvrir une autre piece ne garde pas les messages de la precedente"],
    },
    {
        # Laisser le compteur de la liste inchange apres une lecture annonce des messages non lus
        # qui viennent d'etre lus.
        "nom": "conversation : lire une conversation ne remet pas le compteur a zero",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt",
        "avant": "                    summaries = _state.value.summaries + (linkId to summary.copy(unread = 0)),",
        "apres": "                    summaries = _state.value.summaries + (linkId to summary.copy(unread = summary.unread)),",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["marquer comme lu remet le compteur de la liste a zero"],
    },

    # --- Conversation : la source unique de la taille de page --------------------------------
    {
        # Recopier le nombre fait diverger la requete et la regle « en reste-t-il ? ». La
        # divergence est muette : la conversation s'affiche, et le bouton apparait au mauvais
        # moment. C'est ce controle de forme qui la voit, et ce cas qui le prouve vivant.
        #
        # **La valeur mutee est la meme, et c'est voulu.** `Social.MESSAGE_PAGE` est un
        # `const val` : `Social.MESSAGE_PAGE.toLong()` **s'inline** en `50L`, donc recopier `50L`
        # est exactement ce que ferait quelqu'un qui ignore la regle — et la classe compilee est
        # identique a l'octet pres. La mutation est donc invisible pour Gradle : seules les
        # **lettres** du source changent, et le controle de forme, qui lit le source a
        # l'execution, est le seul a pouvoir la voir.
        #
        # C'est pour cela que `lancer()` force `--rerun`. Sans lui, Gradle a servi
        # `:core:data:testDebugUnitTest FROM-CACHE`, le rapport n'a pas ete reecrit, et le cas a
        # rendu « FAUX : aucun test n'est tombe » — un verdict qui accusait le controle alors que
        # le controle n'avait pas tourne. Ce cas est donc aussi le **temoin** de cette
        # correction : si le drapeau disparait, il ne rendra plus « FAUX » mais « NON CONCLUANT »,
        # ce qui est la bonne lecture.
        "nom": "conversation : la taille de page est recopiee dans la source",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/SupabaseSocialSource.kt",
        "avant": "private val MESSAGE_PAGE = Social.MESSAGE_PAGE.toLong()",
        "apres": "private val MESSAGE_PAGE = 50L",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la source ne recopie pas la taille de page, elle la lit au domaine"],
    },

    # --- Conversation : ce qui la rend visible, et ce qu'elle doit dire -----------------------
    {
        # Le fait qui fait basculer l'ecran. Un `false` en dur laisserait la piece ouverte dans le
        # depot sans que rien ne la montre : la liste s'afficherait normalement, et la conversation
        # serait inatteignable. Aucun ecran ne peut le contredire — c'est un fait du depot, et non
        # un etat d'interface.
        "nom": "conversation : la piece ouverte ne fait plus basculer l'ecran",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialRenderer.kt",
        "avant": "            conversationOpen = state.room != null,",
        "apres": "            conversationOpen = false,",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["la piece ouverte du depot fait basculer l'ecran"],
    },
    {
        # L'avis du depot, ecrit pour personne. La conversation est l'ecran qui produit la plupart
        # des avis — « Etape partagee », « Aucun message a signaler », « Entre une date future » —,
        # et la liste d'amis n'est plus la quand ils paraissent : le geste semblerait n'avoir rien
        # fait, ce qui est exactement ce qu'un avis evite.
        "nom": "conversation : l'avis du depot n'arrive plus a la conversation",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/ConversationRenderer.kt",
        "avant": "            notice = state.notice,",
        "apres": "            notice = null,",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["l'avis du depot parvient a la conversation"],
    },
    {
        # Le **sens** du geste de moderation, inverse. Le bouton continuerait d'annoncer « Nommer
        # moderateur » ou « Retirer la moderation » et ferait le contraire : une promotion la ou
        # l'on croyait une retrogradation, sans que rien ne le signale. C'est precisement pour cela
        # que le sens voyage comme une **valeur**, et non comme une phrase a interpreter.
        "nom": "conversation : le sens du geste de moderation est inverse",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/ConversationRenderer.kt",
        "avant": "                grantsModerator = member.role != GroupRole.MODERATOR,",
        "apres": "                grantsModerator = member.role == GroupRole.MODERATOR,",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["le sens du geste de moderation suit le role actuel du membre"],
    },
    {
        # La bascule de l'ecran, supprimee. La conversation existe, le depot la porte, et
        # **aucun ecran ne la compose** : tout ce qu'elle contient serait ecrit pour personne.
        "nom": "conversation : l'ecran des amis ne bascule plus sur la conversation",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialScreen.kt",
        "avant": "    if (state.conversationOpen) {",
        "apres": "    if (false) {",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["la liste bascule sur la conversation quand une piece est ouverte"],
    },
    {
        # La troisieme porte, qui perd son genre : un cercle s'ouvrirait comme s'il etait un lien,
        # donc la source chercherait un lien qui n'existe pas et la piece resterait vide. Le geste
        # paraîtrait n'avoir rien fait — et c'est le seul chemin vers le contenu d'un cercle.
        "nom": "conversation : le bouton Ouvrir d'un cercle perd son genre",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/SocialScreen.kt",
        "avant": "                onClick = { onOpen(circle.id, true) },",
        "apres": "                onClick = { onOpen(circle.id, false) },",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["les trois portes de la conversation sont ouvertes"],
    },

    # --- Quiz : l'ordre, la file et le disque -------------------------------------------------
    {
        # Lire l'instantane avant de vider la file ferait lire un etat **anterieur** a l'envoi,
        # donc sans la reponse qu'on vient de faire : elle disparaitrait de l'ecran le temps d'un
        # rafraichissement, puis reviendrait. La mutation retire la purge ; l'ordre se mesure par
        # la suite des appels de la doublure, qui est justement ecrite pour cela.
        "nom": "quiz : la file d'attente n'est plus videe avant la lecture",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": "                for (entry in outbox.list(owner)) {",
        "apres": "                for (entry in outbox.list(owner).filter { false }) {",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la file est videe avant que l'instantane ne soit lu"],
    },
    {
        # Publier le reseau avant le disque afficherait un ecran vide sans connexion, alors que
        # tout le quiz est sur l'appareil. Le controle photographie l'etat publie **au moment** ou
        # la doublure est appelee : c'est la seule facon de voir cet ordre depuis un test.
        "nom": "quiz : l'instantane du disque n'est plus publie avant le reseau",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": "            snapshot = store.current(),",
        "apres": "            snapshot = null,",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["l'instantane du disque est publie avant le premier appel reseau"],
    },
    {
        # Remplacer la fusion par la seule reponse du serveur effacerait une reponse faite hors
        # ligne, que le serveur ne connait pas encore. C'est la perte de travail la plus discrete
        # du Quiz : rien ne leve, la reponse disparait simplement de l'ecran.
        "nom": "quiz : la fusion n'est plus faite avec l'instantane du disque",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": "                val merged = store.update { Quiz.mergeSnapshot(remote, it) }",
        "apres": "                val merged = store.update { remote }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la fusion garde une reponse locale que le serveur ne connait pas"],
    },
    {
        # Ne plus enfiler la reponse la perdrait definitivement : elle serait a l'ecran jusqu'au
        # prochain demarrage, puis n'aurait jamais existe pour le serveur. L'ecran ne le dirait
        # pas, puisque la reponse s'affiche.
        "nom": "quiz : la reponse faite hors ligne n'est plus enfilee",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": "        outbox.enqueue(owner, id = \"$owner:$day\", payload = AppJson.encodeToString(payload))",
        "apres": "        if (false) outbox.enqueue(owner, id = \"$owner:$day\", payload = AppJson.encodeToString(payload))",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une reponse faite hors ligne est rangee puis renvoyee"],
    },
    {
        # Acquitter **avant** d'envoyer perd la reponse des que l'envoi echoue : la file ne la
        # porte plus, et personne ne le saura. C'est la mutation qui coute le plus cher, et celle
        # dont le symptome est le plus tardif — la reponse manque une semaine plus loin.
        "nom": "quiz : la file est acquittee avant que l'envoi n'ait abouti",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": (
            "                    api.answerDaily(AppJson.decodeFromString<DailyAnswerPayload>(entry.payload))\n"
            "                    outbox.acknowledge(listOf(entry.id))"
        ),
        "apres": (
            "                    outbox.acknowledge(listOf(entry.id))\n"
            "                    api.answerDaily(AppJson.decodeFromString<DailyAnswerPayload>(entry.payload))"
        ),
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un echec d'envoi ne perd pas la reponse en attente"],
    },
    {
        # Le document est par compte, comme la table `quiz_cache(user_id, data)` de l'original.
        # Un fichier unique ferait apparaitre la reponse d'un compte sous le nom d'un autre : ce
        # n'est pas une gene d'affichage, c'est le travail de quelqu'un montre a quelqu'un d'autre.
        "nom": "quiz : le cache n'est plus range par compte",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/QuizCacheStore.kt",
        "avant": "                file = File(root, \"quiz_${LocalStateStore.fileToken(id)}.json\"),",
        "apres": "                file = File(root, \"quiz.json\"),",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["changer de compte efface le quiz du precedent"],
    },
    {
        # Se deconnecter laisserait le quiz a l'ecran, sous aucun compte : l'ecran de quelqu'un
        # d'autre, ou de personne, mais toujours lisible.
        "nom": "quiz : la deconnexion garde le quiz affiche",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        # L'ancre porte sur l'etat **vide**, et non sur un `signedIn` disparu : le champ a ete
        # remplace par `ownerId`, qui retombe a `null` par defaut. La mutation garde donc
        # l'instantane precedent en ne changeant que `loading` — c'est exactement le defaut que le
        # test surveille, et il compile.
        "avant": "        _state.value = QuizState(loading = false)",
        "apres": "        _state.value = _state.value.copy(loading = false)",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la deconnexion efface le quiz affiche"],
    },
    {
        # Une panne sans rien a l'ecran doit se lire comme une panne, et non comme un avis : un
        # avis se lit « c'est fait », et il n'y a rien a l'ecran pour le confirmer.
        "nom": "quiz : un echec sans rien a montrer devient un simple avis",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": "            failure = if (empty) text else null,",
        "apres": "            failure = null,",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un echec sans rien a montrer est une panne"],
    },
    {
        # Sans cette traduction, un appareil sans reseau afficherait le texte brut du moteur HTTP
        # au lieu de la phrase de l'original. Le fait est le meme, la phrase ne l'est plus.
        "nom": "quiz : un defi sans reseau perd sa phrase",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": "                notice = if (error.isOffline()) QuizText.CHALLENGE_OFFLINE else describe(error),",
        "apres": "                notice = describe(error),",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["creer un defi sans reseau dit qu'il faut une connexion"],
    },
    {
        # La garde de double geste, retiree : un second appui partirait pendant que le premier
        # n'est pas fini. L'ecran desactive ses boutons, mais c'est une intention d'interface, pas
        # une regle du depot — et un geste de Quiz ecrit chez le serveur.
        "nom": "quiz : la garde de double geste disparait",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/QuizRepository.kt",
        "avant": "        if (_state.value.busy) return false",
        "apres": "        if (false) return false",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un second geste pendant le premier est ignore"],
    },
    {
        # Le code PostgREST `PGRST202` ne dit pas la panne d'un appel : il dit que la fonction
        # n'existe pas dans le schema, donc que la migration du Quiz n'est pas deployee. Le
        # confondre avec un refus ordinaire enverrait chercher le reseau au lieu du schema.
        "nom": "quiz : le code de fonction absente n'est plus lu",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Quiz.kt",
        "avant": "        code == MISSING_FUNCTION_CODE -> QuizText.SERVICE_MISSING",
        "apres": "        false -> QuizText.SERVICE_MISSING",
        "tache": ":core:domain:test",
        "attendus": ["un code de fonction absente prime sur le texte du serveur"],
    },
    {
        # Un defi est du contenu : l'oublier dans « rien a montrer » ferait dire « Aucune question
        # publiee aujourd'hui » a quelqu'un qui a justement un defi en cours.
        "nom": "quiz : un defi ne compte plus comme du contenu",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Quiz.kt",
        "avant": "        snapshot.daily == null && snapshot.responses.isEmpty() && snapshot.challenges.isEmpty()",
        "apres": "        snapshot.daily == null && snapshot.responses.isEmpty()",
        "tache": ":core:domain:test",
        "attendus": ["un defi suffit a remplir l'ecran"],
    },

    # --- Quiz : la route, l'ecran et ses portes ----------------------------------------------
    {
        # Le Quiz est plein ecran : il n'a **pas** de barre de navigation, donc son bouton « <- »
        # est son seul geste de sortie. Le retirer de l'ensemble ne casse rien a l'affichage — la
        # route continue de fonctionner —, mais deux barres se posent par-dessus l'ecran, et le
        # defaut ne se voit qu'a l'execution, sur un Quiz ouvert depuis « Defier ».
        "nom": "quiz : le Quiz n'est plus plein ecran",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppDestination.kt",
        "avant": "    val fullScreen: Set<String> = setOf(READER, QUIZ, DAILY, RECITATIONS, REVIEW, ADMIN)",
        "apres": "    val fullScreen: Set<String> = setOf(READER, DAILY, RECITATIONS, REVIEW, ADMIN)",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["le Quiz reste plein ecran avec ses arguments"],
    },
    {
        # Servir la route **nue** laisserait les deux arguments sans effet : la navigation
        # ignorerait `?ami=…`, et « Defier cet ami » ouvrirait l'accueil du Quiz au lieu de la
        # creation d'un defi. Aucune erreur, aucun ecran vide — juste une intention perdue.
        "nom": "quiz : la coquille sert la route nue du Quiz",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "            route = AppRoutes.QUIZ_PATTERN,",
        "apres": "            route = AppRoutes.QUIZ,",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["la route du Quiz est servie par son motif"],
    },
    {
        # L'argument de la route porte l'**ami**, et non le defi : les echanger ferait chercher un
        # defi nomme comme un compte, et la creation s'ouvrirait sur personne.
        "nom": "quiz : la route du Quiz perd son ami",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppDestination.kt",
        "avant": "            if (friendId != null) add(\"$QUIZ_FRIEND=$friendId\")",
        "apres": "            if (friendId != null) add(\"$QUIZ_CHALLENGE=$friendId\")",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["la route du Quiz porte l'ami a defier"],
    },
    {
        # Le Quiz cree un defi entre deux **joueurs** : le serveur attend l'identifiant du compte,
        # et l'original cherche `links.find(...)?.other?.id` pour l'obtenir. Rendre l'identifiant du
        # **lien** ferait naitre un defi contre personne — et l'ecran s'ouvrirait normalement.
        "nom": "conversation : le destinataire du defi est le lien, et non le compte",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/ConversationRenderer.kt",
        "avant": "            challengeFriendId = if (!adminContact && isLink) link?.other?.id else null,",
        "apres": "            challengeFriendId = if (!adminContact && isLink) link?.id else null,",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["le destinataire d'un defi est le compte de l'ami, et non le lien"],
    },
    {
        # `onChallenge` a une valeur par defaut vide : un bouton qui n'appelle rien compile,
        # s'affiche, et ne fait rien. C'est le defaut que le controle de forme existe pour
        # attraper, et il n'est visible ni a la compilation ni a l'execution.
        "nom": "conversation : le bouton Defier n'agit plus",
        "fichier": "feature/social/src/main/kotlin/com/msoumaya/deepseekandroid/feature/social/ConversationScreen.kt",
        "avant": "            onClick = { onChallenge(friendId) },",
        "apres": "            onClick = {},",
        "tache": ":feature:social:testDebugUnitTest",
        "attendus": ["le bouton Defier suit l'etat et ouvre le Quiz sur l'ami"],
    },
    {
        # `done` et `available` ne disent pas la meme chose : `done` regarde les **reponses** au
        # jour courant, `available` regarde la **question publiee** dans un instantane du jour. Les
        # confondre ferait annoncer « terminee » sur toute question publiee, et la carte cesserait
        # d'inviter a jouer.
        "nom": "accueil : une question publiee passe pour terminee",
        "fichier": "feature/home/src/main/kotlin/com/msoumaya/deepseekandroid/feature/home/HomeRenderer.kt",
        "avant": "        val done = snapshot?.responses?.any { it.day == at } == true",
        "apres": "        val done = snapshot?.daily != null",
        "tache": ":feature:home:testDebugUnitTest",
        "attendus": ["une question publiee sans reponse ne dit pas terminee"],
    },
    {
        # Sans la borne du jour, un instantane de la veille annoncerait « disponible » sur une
        # question que le joueur ne peut pas ouvrir : la carte menerait a une vue du jour vide.
        "nom": "accueil : la question du jour est annoncee disponible meme la veille",
        "fichier": "feature/home/src/main/kotlin/com/msoumaya/deepseekandroid/feature/home/HomeRenderer.kt",
        "avant": "        val available = snapshot != null && snapshot.day == at && snapshot.daily != null",
        "apres": "        val available = snapshot != null && snapshot.daily != null",
        "tache": ":feature:home:testDebugUnitTest",
        "attendus": ["un instantane d'hier n'annonce pas la question du jour disponible"],
    },
    {
        # La pastille est le seul element qui ne soit pas du texte : elle signale une question
        # **a faire**. La laisser allumee sur une question deja repondue rappellerait un travail
        # fait — et le joueur ouvrirait le Quiz pour rien.
        "nom": "accueil : la pastille s'allume sur une question deja repondue",
        "fichier": "feature/home/src/main/kotlin/com/msoumaya/deepseekandroid/feature/home/HomeRenderer.kt",
        "avant": "            quizAlert = available && !done,",
        "apres": "            quizAlert = available,",
        "tache": ":feature:home:testDebugUnitTest",
        "attendus": ["une question repondue est annoncee terminee, pastille eteinte"],
    },
    {
        # Les deux cartes ne menent pas au meme endroit : l'une ouvre le Quiz, l'autre va chercher
        # un ami. Brancher la meme lambda sur les deux ferait de « Defie tes amis » un second
        # bouton vers le Quiz, sans que rien ne le signale.
        "nom": "accueil : les deux cartes de quiz partagent le meme geste",
        "fichier": "feature/home/src/main/kotlin/com/msoumaya/deepseekandroid/feature/home/HomeScreen.kt",
        "avant": "                        onQuiz = onOpenQuiz,",
        "apres": "                        onQuiz = onOpenFriends,",
        "tache": ":feature:home:testDebugUnitTest",
        "attendus": ["chaque carte recoit son propre geste"],
    },
    {
        # `render` accepte l'instantane **facultativement** : l'oublier compile, et les cartes
        # annoncent alors « Question du jour » pour toujours — y compris apres une reponse.
        "nom": "accueil : le ViewModel n'observe plus l'instantane du Quiz",
        "fichier": "feature/home/src/main/kotlin/com/msoumaya/deepseekandroid/feature/home/HomeViewModel.kt",
        "avant": "                    QuranState.Ready -> HomeRenderer.render(appState, today(), quiz.snapshot)",
        "apres": "                    QuranState.Ready -> HomeRenderer.render(appState, today())",
        "tache": ":feature:home:testDebugUnitTest",
        "attendus": ["le renderer recoit l'instantane, et pas seulement l'etat applicatif"],
    },
    {
        # « 0 bonne reponse / 0 » se lit « tu n'as jamais joue », ce qui est une affirmation sur la
        # personne — et elle serait fausse pendant la seconde qui suit le demarrage. Tant que rien
        # n'est lu, le bloc doit disparaitre.
        "nom": "progres : le bloc de quiz affiche des zeros sans instantane",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressRenderer.kt",
        "avant": "        if (snapshot == null) return null",
        "apres": "        if (snapshot == null) return QuizSummary(\"\", \"\", \"\", \"\")",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["sans instantane lu le bloc n'existe pas"],
    },
    {
        # L'identifiant decide **de quel cote** d'un defi on se trouve : `Quiz.statistics` compte
        # mes bonnes reponses et celles de l'autre. Le perdre inverserait victoires et egalites
        # sans que rien ne le dise — le total, lui, resterait juste.
        "nom": "progres : les defis sont comptes du mauvais cote",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressRenderer.kt",
        "avant": "        val stats = Quiz.statistics(snapshot, userId.orEmpty())",
        "apres": "        val stats = Quiz.statistics(snapshot, \"\")",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["le compte decide de quel cote se lit le defi"],
    },
    {
        # Le bloc de quiz est un **cumul**, et l'original le pose entre la carte d'objectif et les
        # compteurs. Le retirer compile, l'ecran s'affiche normalement, et une fonctionnalite
        # livree n'apparait simplement jamais.
        "nom": "progres : le bloc de quiz n'est plus compose",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressScreen.kt",
        "avant": "            state.quiz?.let { summary -> QuizStats(summary = summary) }",
        "apres": "            state.quiz?.let { QuizStats(summary = it) }",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["l'ecran compose le bloc de quiz quand l'etat le porte"],
    },
    {
        # Meme piege que pour l'accueil : `render` accepte l'instantane facultativement, donc
        # l'oublier compile et le bloc disparait pour toujours.
        "nom": "progres : le ViewModel oublie l'instantane du Quiz",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressViewModel.kt",
        "avant": "                        quiz = sources.quiz.snapshot,",
        "apres": "                        quiz = null,",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["le renderer recoit l'instantane et le compte"],
    },
    {
        # Sans le compte, les defis se lisent tous a zero victoire : le bloc reste affiche, et il
        # est faux — ce qui est pire qu'absent.
        "nom": "progres : le ViewModel oublie le compte",
        "fichier": "feature/progress/src/main/kotlin/com/msoumaya/deepseekandroid/feature/progress/ProgressViewModel.kt",
        "avant": "                        userId = sources.quiz.ownerId,",
        "apres": "                        userId = null,",
        "tache": ":feature:progress:testDebugUnitTest",
        "attendus": ["le renderer recoit l'instantane et le compte"],
    },
    {
        # Le detenteur ne retient plus les reglages : relancer le meme passage repart des valeurs
        # par defaut, et la personne retrouve ses reglages perdus sans qu'on le lui dise.
        #
        # L'attendu est le test de `updateSettings`, et **pas** celui dont le nom ressemble le
        # plus. Mesure : `les reglages sont retenus par le detenteur` ouvre la seance par `start`,
        # donc n'atteint jamais la ligne mutee, et ne tombe pas — le cas a rendu FAUX tant qu'il
        # l'annoncait. Le test qui tombe est celui qui pousse la vitesse par `updateSettings`.
        "nom": "seance : les reglages ne sont plus retenus par le detenteur",
        "fichier": "core/playback/src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/AudioSessionHolder.kt",
        "avant": "    fun updateSettings(value: AudioSession) {\n        settings = value",
        "apres": "    fun updateSettings(value: AudioSession) {\n        settings = AudioSession()",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["updateSettings pousse la vitesse au lecteur natif"],
    },
    {
        # Le meme reglage perdu, mais par l'autre porte : celle qui ouvre la seance.
        #
        # Ce cas comble un trou mesure. Le test `les reglages sont retenus par le detenteur`
        # existait, et **aucun** cas ne le faisait tomber : sa garde n'etait prouvee par rien, et
        # la suppression de la ligne mutee serait passee inapercue.
        "nom": "seance : la seance ouverte ne retient plus ses reglages",
        "fichier": "core/playback/src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/AudioSessionHolder.kt",
        "avant": "    fun start(range: Range, settings: AudioSession) {\n        this.settings = settings",
        "apres": "    fun start(range: Range, settings: AudioSession) {\n        this.settings = AudioSession()",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["les reglages sont retenus par le detenteur"],
    },
    {
        # La reprise perd la seance : relancer repart d'une plage vide au lieu de la seance
        # ouverte. Le bouton reste la, et ne fait plus ce qu'il annonce.
        "nom": "seance : la reprise repart d'une seance vide",
        "fichier": "core/playback/src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/AudioSessionHolder.kt",
        "avant": "    fun start(range: Range) = controller.start(range, settings)",
        "apres": "    fun start(range: Range) = controller.start(range, AudioSession())",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["start sans reglages reprend la derniere valeur connue"],
    },
    {
        # Le detenteur libere le lecteur natif des qu'une seance se ferme : c'est le defaut
        # d'origine, deplace d'un cran. Fermer une seance doit l'arreter, pas detruire le lecteur.
        "nom": "seance : fermer une seance libere le lecteur natif",
        "fichier": "core/playback/src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/AudioSessionHolder.kt",
        "avant": "    fun close() = controller.close()",
        "apres": "    fun close() {\n        controller.release()\n    }",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["close ferme la seance et arrete le lecteur natif"],
    },
    {
        # Le lecteur se remet a construire sa propre seance : quitter l'ecran la tuera de nouveau.
        # Le controle de forme est seul a pouvoir le voir — un `@Composable` ne s'ouvre pas en test.
        "nom": "seance : le lecteur reconstruit sa propre seance",
        "fichier": "feature/reader/src/main/kotlin/com/msoumaya/deepseekandroid/feature/reader/ReaderScreen.kt",
        "avant": "    val audioState = playback?.state?.collectAsState()?.value",
        "apres": "    val audioState = playback?.state?.collectAsState()?.value\n    val relache = { playback?.release() }\n    relache()",
        "tache": ":feature:reader:testDebugUnitTest",
        "attendus": ["le lecteur ne libere plus la seance en partant"],
    },
    {
        # Le type d'avant-plan disparait : le code compile, les tests passent, et l'application
        # s'arrete a la premiere lecture ecran eteint, sur Android 14. Rien d'autre ne le voit.
        "nom": "publication : le service perd son type d'avant-plan",
        "fichier": "app/src/main/AndroidManifest.xml",
        "avant": "            android:foregroundServiceType=\"mediaPlayback\">",
        "apres": "            >",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["le service est declare avec le type d'avant-plan de lecture"],
    },
    {
        # Le service cesse d'etre exporte : le systeme ne le lie plus pour l'ecran verrouille,
        # donc la notification et les commandes Bluetooth cessent d'exister.
        "nom": "publication : le service cesse d'etre exporte",
        "fichier": "app/src/main/AndroidManifest.xml",
        "avant": "            android:exported=\"true\"\n            android:foregroundServiceType=\"mediaPlayback\">",
        "apres": "            android:exported=\"false\"\n            android:foregroundServiceType=\"mediaPlayback\">",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["le service est declare exporte avec l'action de media3"],
    },
    {
        # Le service construit son propre lecteur : deux lecteurs jouent la meme recitation,
        # l'un par-dessus l'autre, decales de quelques millisecondes.
        #
        # La mutation ecrit `ExoAudioOutput(` et non `ExoPlayer.Builder(` : `core:playback` a
        # `core:audio` en `api`, donc ce nom compile sans ajouter de dependance. Ecrire
        # `ExoPlayer` demanderait `media3-exoplayer`, que ce module n'a pas — et l'ajouter
        # **pour faire passer le cas** aurait ete un contresens.
        "nom": "publication : le service construit son propre lecteur",
        "fichier": "core/playback/src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/PlaybackService.kt",
        "avant": "        val player = PlaybackBridge.player ?: return null",
        "apres": "        val player: Player = com.msoumaya.deepseekandroid.core.audio.ExoAudioOutput(this).player",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["le service reprend le lecteur au lieu d'en construire un"],
    },
    {
        # L'application cesse de deposer le lecteur : le service, reveille sans elle, ne trouve
        # plus rien a publier — et se contente d'une session vide, ou de rien.
        "nom": "publication : l'application ne depose plus le lecteur",
        "fichier": "app/src/main/kotlin/com/msoumaya/deepseekandroid/DeepSeekApplication.kt",
        "avant": "        container.playback?.player?.let { PlaybackBridge.publish(it) }",
        "apres": "        // depose retire",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["le pont est depose par l'application et non par un ecran"],
    },
    {
        # Le service se met a conduire la seance : deux conducteurs pour un seul lecteur, donc
        # deux seances concurrentes qui se disputent le verset suivant.
        "nom": "publication : le service conduit la seance",
        "fichier": "core/playback/src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/PlaybackService.kt",
        "avant": "        return MediaSession.Builder(this, player).build()",
        "apres": "        val suivant = com.msoumaya.deepseekandroid.core.domain.AudioQueue.next(\n            range = com.msoumaya.deepseekandroid.core.model.Range(1, 1),\n            current = com.msoumaya.deepseekandroid.core.model.AudioPosition(1, 1),\n            mode = com.msoumaya.deepseekandroid.core.model.RepeatMode.PASSAGE,\n            count = 1,\n            autoStop = false,\n            gapSeconds = 0,\n        )\n        require(suivant is com.msoumaya.deepseekandroid.core.domain.AudioStep)\n        return MediaSession.Builder(this, player).build()",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["le service ne conduit aucune decision de seance"],
    },
    {
        # La mise en forme du titre derive : l'ecran verrouille affiche « verset 1 » sans dire
        # de quelle application il s'agit. Le client d'origine prefixe toujours par « Coran · ».
        "nom": "publication : le titre perd le nom de l'application",
        "fichier": "core/playback/src/main/kotlin/com/msoumaya/deepseekandroid/core/playback/AudioNotification.kt",
        "avant": "    fun title(verseLabel: String): String = \"Coran · $verseLabel\"",
        "apres": "    fun title(verseLabel: String): String = verseLabel",
        "tache": ":core:playback:testDebugUnitTest",
        "attendus": ["le titre porte le passage, precede du nom de l'application"],
    },
    {
        # L'ordre des bornes n'est plus verifie : un passage inverse (5 -> 2) passe pour un
        # passage valide, et la lecture part dans le vide sans que rien ne le dise.
        "nom": "recitation : un passage inverse passe pour un passage valide",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "    fun validRange(start: Int, end: Int): Boolean = start >= 1 && end <= VERSE_COUNT && start <= end",
        "apres": "    fun validRange(start: Int, end: Int): Boolean = start >= 1 && end <= VERSE_COUNT",
        "tache": ":core:domain:test",
        "attendus": ["un intervalle inverse est refuse"],
    },
    {
        # Le verset zero passe pour un verset reel : les identifiants du Coran commencent a 1,
        # donc zero designe un verset que le referentiel ne peut pas rendre.
        "nom": "recitation : le verset zero passe pour un verset reel",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "    fun validRange(start: Int, end: Int): Boolean = start >= 1 && end <= VERSE_COUNT && start <= end",
        "apres": "    fun validRange(start: Int, end: Int): Boolean = start >= 0 && end <= VERSE_COUNT && start <= end",
        "tache": ":core:domain:test",
        "attendus": ["le premier verset est valide et zero ne l'est pas"],
    },
    {
        # Un verset de trop passe : 6237 n'existe pas, et la recitation annoncerait un passage
        # que le referentiel ne contient pas.
        "nom": "recitation : un verset de trop passe pour un verset reel",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "    fun validRange(start: Int, end: Int): Boolean = start >= 1 && end <= VERSE_COUNT && start <= end",
        "apres": "    fun validRange(start: Int, end: Int): Boolean = start >= 1 && end <= VERSE_COUNT + 1 && start <= end",
        "tache": ":core:domain:test",
        "attendus": ["le dernier verset est valide et le suivant ne l'est pas"],
    },
    {
        # La casse n'est plus ignoree : une adresse qui porte `.3GP` est prise pour du m4a, et
        # le fichier part avec un type de contenu qui ne correspond pas a son conteneur.
        "nom": "recitation : l'extension en majuscules n'est plus reconnue",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        if (sourceUri.lowercase().contains(EXTENSION_3GP)) EXTENSION_3GP else EXTENSION_MP4",
        "apres": "        if (sourceUri.contains(EXTENSION_3GP)) EXTENSION_3GP else EXTENSION_MP4",
        "tache": ":core:domain:test",
        "attendus": ["la casse de l'extension ne change rien"],
    },
    {
        # Les deux types de contenu s'echangent : le depot annonce du 3gpp pour un m4a, et le
        # fichier est refuse par le compartiment, ou servi avec un type que le lecteur rejette.
        "nom": "recitation : le type de contenu ne suit plus l'extension",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        if (extension == EXTENSION_3GP) \"audio/3gpp\" else \"audio/mp4\"",
        "apres": "        if (extension == EXTENSION_3GP) \"audio/mp4\" else \"audio/3gpp\"",
        "tache": ":core:domain:test",
        "attendus": ["le type de contenu suit l'extension"],
    },
    {
        # Le fichier n'est plus range sous son proprietaire : les politiques du compartiment ne
        # peuvent plus proteger quoi que ce soit, et deux comptes se marcheraient dessus.
        "nom": "recitation : le fichier n'est plus range sous son proprietaire",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "    fun storagePath(userId: String, id: String, extension: String): String = \"$userId/$id$extension\"",
        "apres": "    fun storagePath(userId: String, id: String, extension: String): String = \"$id$extension\"",
        "tache": ":core:domain:test",
        "attendus": ["le chemin range le fichier sous son proprietaire"],
    },
    {
        # Une invocation ecrit des bornes de versets : elle devient corrigeable verset par verset
        # alors qu'elle n'en contient aucun, et une correction sur un verset qu'elle ne porte pas
        # devient possible.
        "nom": "recitation : une invocation se met a porter des bornes",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        if (kind == RecitationKind.INVOCATION) null to null else start to end",
        "apres": "        if (kind == RecitationKind.INVOCATION) start to end else start to end",
        "tache": ":core:domain:test",
        "attendus": ["une invocation n'ecrit pas de bornes"],
    },
    {
        # L'ordre des controles change : quand plusieurs manques coexistent, c'est un autre
        # message qui est annonce. Le code reste juste par ailleurs — c'est le seul cas qui le voit.
        "nom": "recitation : l'ordre des controles d'enregistrement change",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        if (kind == RecitationKind.QURAN && !validRange(start, end)) {\n            return RecitationSaveProblem.RANGE_INVALID\n        }\n        if (userId.isEmpty()) return RecitationSaveProblem.OWNER_MISSING",
        "apres": "        if (userId.isEmpty()) return RecitationSaveProblem.OWNER_MISSING\n        if (kind == RecitationKind.QURAN && !validRange(start, end)) {\n            return RecitationSaveProblem.RANGE_INVALID\n        }",
        "tache": ":core:domain:test",
        "attendus": ["l'ordre des controles est celui du client d'origine"],
    },
    {
        # Les bornes redeviennent exigees pour une invocation : enregistrer une invocation
        # devient impossible, et le bouton reste la sans rien faire.
        "nom": "recitation : une invocation exige des bornes de versets",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        if (kind == RecitationKind.QURAN && !validRange(start, end)) {",
        "apres": "        if (!validRange(start, end)) {",
        "tache": ":core:domain:test",
        "attendus": ["une invocation est acceptee sans bornes de versets"],
    },
    {
        # Les bornes de la correction deviennent exclusives : corriger le premier ou le dernier
        # verset de la recitation devient impossible, alors que ce sont les plus faciles a viser.
        "nom": "recitation : les bornes de la correction deviennent exclusives",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "            if (verseId < startVerseId || verseId > endVerseId) return CorrectionProblem.VERSE_OUTSIDE",
        "apres": "            if (verseId <= startVerseId || verseId > endVerseId) return CorrectionProblem.VERSE_OUTSIDE",
        "tache": ":core:domain:test",
        "attendus": ["les bornes elles-memes sont corrigibles"],
    },
    {
        # Le commentaire n'est plus rogne : un retour fait d'espaces passe pour un vrai retour,
        # et la personne qui le recoit ne lit rien.
        "nom": "recitation : un retour fait d'espaces passe pour un vrai retour",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        comment.trim().isEmpty() && voicePath.isNullOrEmpty()",
        "apres": "        comment.isEmpty() && voicePath.isNullOrEmpty()",
        "tache": ":core:domain:test",
        "attendus": ["un commentaire fait d'espaces ne dit rien"],
    },
    {
        # L'adresse vocale est rognee comme le commentaire : c'est l'ecart assume avec le client
        # d'origine qui disparait, et les deux clients ne s'accordent plus sur ce qui est acceptable.
        "nom": "recitation : l'adresse vocale est rognee comme le commentaire",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        comment.trim().isEmpty() && voicePath.isNullOrEmpty()",
        "apres": "        comment.trim().isEmpty() && voicePath.isNullOrBlank()",
        "tache": ":core:domain:test",
        "attendus": ["une adresse faite d'espaces compte comme presente"],
    },
    {
        # N'importe qui peut supprimer n'importe quelle recitation : le fichier part du
        # compartiment, et la ligne avec lui.
        "nom": "recitation : n'importe qui peut supprimer une recitation",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "    fun mayDelete(ownerId: String, userId: String): Boolean = ownerId == userId",
        "apres": "    fun mayDelete(ownerId: String, userId: String): Boolean = true",
        "tache": ":core:domain:test",
        "attendus": ["un autre compte ne peut pas supprimer"],
    },
    {
        # Seule une ligne jamais envoyee repart : une application tuee en pleine transmission
        # laisse une ligne bloquee pour toujours, et la recitation ne quitte jamais l'appareil.
        "nom": "recitation : une ligne bloquee en plein depot ne repart plus",
        "fichier": "core/model/src/main/kotlin/com/msoumaya/deepseekandroid/core/model/Recitation.kt",
        "avant": "    val awaitsUpload: Boolean get() = this != SYNCED",
        "apres": "    val awaitsUpload: Boolean get() = this == PENDING",
        "tache": ":core:domain:test",
        "attendus": ["une ligne en cours de depot repart quand meme"],
    },
    {
        # Le motif du doublon se met a tout accepter : une panne reseau passe pour un fichier
        # deja depose, la ligne est ecrite, et la recitation est annoncee deposee alors que
        # l'audio n'a jamais quitte l'appareil.
        "nom": "recitation : le motif du doublon se met a tout accepter",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        return text.contains(\"already exists\") || text.contains(\"duplicate\")",
        "apres": "        return true",
        "tache": ":core:domain:test",
        "attendus": ["un refus sans rapport n'est pas tolere"],
    },
    {
        # Un message absent passe pour benin : le garde tombe, et un echec sans texte fait
        # ecrire une ligne vers un fichier que personne ne pourra ecouter.
        "nom": "recitation : un message absent passe pour benin",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        if (message.isNullOrEmpty()) return false",
        "apres": "        if (message.isNullOrEmpty()) return true",
        "tache": ":core:domain:test",
        "attendus": ["un message absent ne passe pas pour benin"],
    },
    {
        # Le fichier n'est plus copie : l'enregistreur ecrit dans un fichier temporaire que le
        # systeme peut effacer, et une recitation non deposee disparait au redemarrage.
        "nom": "recitation : le registre ne copie plus le fichier",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/RecitationStore.kt",
        "avant": "            File(sourcePath).copyTo(target, overwrite = true)",
        "apres": "            target.createNewFile()",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["l'enregistrement copie le fichier dans le dossier de l'application"],
    },
    {
        # La nature n'est plus deduite : une invocation est marquee « Coran », et le serveur la
        # refuse ensuite sans que l'appareil ait pu le prevoir.
        "nom": "recitation : la nature n'est plus deduite de l'invocation",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/RecitationStore.kt",
        "avant": "        val kind = if (invocationId == null) RecitationKind.QURAN else RecitationKind.INVOCATION",
        "apres": "        val kind = RecitationKind.QURAN",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une invocation se passe de bornes de versets"],
    },
    {
        # La liste est rendue dans l'ordre d'insertion : l'ecran s'ouvre sur la plus vieille
        # recitation, c'est-a-dire a l'oppose de ce qu'on vient voir.
        "nom": "recitation : la liste est rendue dans l'ordre d'insertion",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/RecitationStore.kt",
        "avant": "            .sortedByDescending { Dates.parseIsoMillis(it.createdAt) }",
        "apres": "            .sortedBy { Dates.parseIsoMillis(it.createdAt) }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la liste est rendue du plus recent au plus ancien"],
    },
    {
        # Le registre n'est plus filtre : les recitations d'un autre compte s'affichent sous le
        # sien, et un retrait pourrait partir sur l'audio de quelqu'un d'autre.
        "nom": "recitation : le registre n'est plus filtre par compte",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/RecitationStore.kt",
        "avant": "            .filter { it.userId == userId }",
        "apres": "            .filter { true }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la liste ne rend que les recitations du compte demande"],
    },
    {
        # Le retrait n'efface plus le fichier : la recitation disparait de la liste, et ses
        # megaoctets restent sur l'appareil sans que rien ne les reference.
        "nom": "recitation : le retrait n'efface plus le fichier",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/RecitationStore.kt",
        "avant": "        withContext(Dispatchers.IO) { File(item.uri).delete() }",
        "apres": "        withContext(Dispatchers.IO) { Unit }",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["le retrait efface le fichier et l'entree"],
    },
    {
        # Le compte n'est plus verifie avant d'effacer : l'audio d'une entree est efface pendant
        # que sa ligne survit — la recitation reste affichee, et ne se lit plus.
        "nom": "recitation : le retrait ne verifie plus le compte avant d'effacer",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/RecitationStore.kt",
        "avant": "        val connue = ledger.current().entries.any { it.id == item.id && it.userId == item.userId }",
        "apres": "        val connue = true",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["le retrait ne touche pas l'entree d'un autre compte"],
    },
    {
        # Les octets ne sont plus deposes du tout : la ligne part, et le serveur l'accepte —
        # rien ne lie la ligne au fichier, et la recitation n'est ecoutable par personne.
        "nom": "recitation : les octets ne sont plus deposes du tout",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "                api.upload(path, bytes, Recitations.contentType(extension))",
        "apres": "                Unit",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["le fichier est depose avant que la ligne ne soit ecrite"],
    },
    {
        # Une recitation deja deposee est renvoyee : ses megaoctets repartent a chaque tentative,
        # et une longue periode sans reseau les fait partir autant de fois qu'il y a d'echecs.
        "nom": "recitation : la recitation deja deposee est renvoyee",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "            val pending = store.list(owner).filter { it.syncStatus.awaitsUpload }",
        "apres": "            val pending = store.list(owner)",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une recitation deja deposee n'est pas renvoyee"],
    },
    {
        # La garde ne rend plus la main : un second depot concurrent demarre, les memes octets
        # partent deux fois, et deux marquages de statut s'ecrasent l'un l'autre.
        "nom": "recitation : la garde de concurrence ne rend plus la main",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "        if (!guard.tryLock()) return false",
        "apres": "        guard.tryLock()",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un second depot concurrent ne renvoie pas les memes octets"],
    },
    {
        # Le fichier manquant n'est plus detecte : on depose zero octet et on ecrit la ligne,
        # donc une recitation que personne ne pourra jamais ecouter.
        "nom": "recitation : le fichier manquant n'est plus detecte",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "                ?: throw IllegalStateException(RecitationText.LOCAL_FILE_MISSING)",
        "apres": "                ?: ByteArray(0)",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un fichier disparu marque un echec sans rien deposer"],
    },
    {
        # Les bornes d'une invocation sont envoyees : la contrainte `recitations_passage_type`
        # exige qu'elles soient nulles, et le serveur refuse la ligne.
        "nom": "recitation : les bornes d'une invocation sont envoyees",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "        val (start, end) = Recitations.remoteBounds(item.kind, item.start, item.end)",
        "apres": "        val (start, end) = item.start to item.end",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la ligne d'une invocation ne porte pas de bornes"],
    },
    {
        # L'instantane d'invocation revient dans la ligne : le declencheur du serveur ecrase
        # cette colonne a chaque insertion, et l'envoyer laisse croire que le client en decide.
        "nom": "recitation : l'instantane d'invocation revient dans la ligne",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/RecitationUpload.kt",
        "avant": "    @SerialName(\"created_at\") val createdAt: String,",
        "apres": "    @SerialName(\"created_at\") val createdAt: String,\n    @SerialName(\"invocation_snapshot\") val invocationSnapshot: String = \"{}\",",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la ligne envoyee ne porte ni instantane d'invocation ni champ nul"],
    },
    {
        # Un 3gp annonce partirait au compartiment avec le type MIME du mp4.
        "nom": "recitation : le format produit devient du 3gp",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "    val RECORDED_EXTENSION: String get() = Recitations.EXTENSION_MP4",
        "apres": "    val RECORDED_EXTENSION: String get() = Recitations.EXTENSION_3GP",
        "tache": ":core:domain:test",
        "attendus": ["le format annonce est celui que le depot saura nommer"],
    },
    {
        # Le debit de l'original est 64 kbit/s : le doubler paie des octets pour rien.
        "nom": "recitation : le debit de l'enregistrement double",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "    const val BIT_RATE: Int = 64_000",
        "apres": "    const val BIT_RATE: Int = 128_000",
        "tache": ":core:domain:test",
        "attendus": ["le debit et les canaux sont ceux que l'original demandait"],
    },
    {
        # L'original informe avant de reclamer l'acces au micro, et non l'inverse.
        "nom": "recitation : le microphone passe avant la notice",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        if (!noticeAccepted) return RecitationStartProblem.NOTICE_PENDING\n        if (!microphoneGranted) return RecitationStartProblem.MICROPHONE_DENIED",
        "apres": "        if (!microphoneGranted) return RecitationStartProblem.MICROPHONE_DENIED\n        if (!noticeAccepted) return RecitationStartProblem.NOTICE_PENDING",
        "tache": ":core:domain:test",
        "attendus": ["la notice passe avant le microphone"],
    },
    {
        # La veracite de l'original (`!userId`) refuse une chaine vide, comme ici.
        "nom": "recitation : un compte vide passe pour un compte",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        if (userId.isNullOrEmpty()) return RecitationStartProblem.OWNER_MISSING",
        "apres": "        if (userId == null) return RecitationStartProblem.OWNER_MISSING",
        "tache": ":core:domain:test",
        "attendus": ["un compte vide vaut une absence de compte"],
    },
    {
        # Terminer au repos tenterait d'arreter un enregistreur deja arrete.
        "nom": "recitation : terminer agit aussi hors capture",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "    fun canFinish(phase: RecitationPhase): Boolean =\n        phase == RecitationPhase.RECORDING || phase == RecitationPhase.PAUSED",
        "apres": "    fun canFinish(phase: RecitationPhase): Boolean =\n        phase != RecitationPhase.PREVIEW && phase != RecitationPhase.SAVED",
        "tache": ":core:domain:test",
        "attendus": ["terminer n'a d'effet que pendant la capture"],
    },
    {
        # Un passage en pleine page n'a pas d'apercu : sa reference est deja affichee.
        "nom": "recitation : le Coran passe aussi par l'apercu",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        if (isInvocation || compact) RecitationPhase.PREVIEW else RecitationPhase.SAVED",
        "apres": "        if (isInvocation || compact || !isInvocation) RecitationPhase.PREVIEW else RecitationPhase.SAVED",
        "tache": ":core:domain:test",
        "attendus": ["un passage du Coran en pleine page va droit a l'enregistre"],
    },
    {
        # Une barre d'actions est une suite : permuter deux boutons change le geste.
        "nom": "recitation : les gestes de la capture changent d'ordre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        RecitationPhase.RECORDING -> listOf(\n            RecitationAction.PAUSE,\n            RecitationAction.FINISH,\n            RecitationAction.CANCEL,\n        )",
        "apres": "        RecitationPhase.RECORDING -> listOf(\n            RecitationAction.FINISH,\n            RecitationAction.PAUSE,\n            RecitationAction.CANCEL,\n        )",
        "tache": ":core:domain:test",
        "attendus": ["pendant la capture on suspend, on termine ou on annule, dans cet ordre"],
    },
    {
        # Un bouton Partager qui ne partage rien vaut moins qu'un bouton absent.
        "nom": "recitation : le partage apparait sans destinataire",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        RecitationPhase.SAVED -> if (canShare) {",
        "apres": "        RecitationPhase.SAVED -> if (canShare || true) {",
        "tache": ":core:domain:test",
        "attendus": ["le partage s'ajoute en dernier, et seulement s'il y a de quoi et qui"],
    },
    {
        # Une fois la recitation gardee, il n'y a plus rien a achever.
        "nom": "recitation : une recitation gardee met un geste en avant",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "            listOf(RecitationAction.LISTEN, RecitationAction.RESTART)",
        "apres": "            listOf(RecitationAction.LISTEN, RecitationAction.SAVE)",
        "tache": ":core:domain:test",
        "attendus": ["une recitation gardee ne met rien en avant"],
    },
    {
        # Un compteur qui repart a zero ferait croire a un redemarrage.
        "nom": "recitation : les minutes sont repliees a soixante",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        val minutes = Math.floorDiv(durationMs, 60_000L)",
        "apres": "        val minutes = Math.floorMod(Math.floorDiv(durationMs, 60_000L), 60L)",
        "tache": ":core:domain:test",
        "attendus": ["les minutes ne sont pas repliees a soixante"],
    },
    {
        # L'original tronque : arrondir ferait afficher une seconde qui n'est pas finie.
        "nom": "recitation : les secondes sont arrondies au lieu d'etre tronquees",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        val seconds = Math.floorMod(Math.floorDiv(durationMs, 1_000L), 60L)",
        "apres": "        val seconds = Math.floorMod(Math.round(durationMs / 1_000.0), 60L)",
        "tache": ":core:domain:test",
        "attendus": ["les millisecondes sont tronquees et non arrondies"],
    },
    {
        # Le titre dit ce qu'on enregistre, pas la forme du panneau.
        "nom": "recitation : le titre suit la mise en page et non la nature",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        if (isInvocation) RecitationText.RECORDER_TITLE_INVOCATION else RecitationText.RECORDER_TITLE_QURAN",
        "apres": "        if (!isInvocation) RecitationText.RECORDER_TITLE_INVOCATION else RecitationText.RECORDER_TITLE_QURAN",
        "tache": ":core:domain:test",
        "attendus": ["le titre suit ce qu'on enregistre"],
    },
    {
        # Le `??` de l'original ne replie que sur null : une chaine vide est un choix.
        "nom": "recitation : un titre vide devient le mot invocation",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        isInvocation -> invocationTitle ?: RecitationText.INVOCATION_FALLBACK",
        "apres": "        isInvocation -> invocationTitle?.takeIf { it.isNotEmpty() } ?: RecitationText.INVOCATION_FALLBACK",
        "tache": ":core:domain:test",
        "attendus": ["un titre vide reste vide, il ne devient pas invocation"],
    },
    {
        # Une invocation porte une plage : c'est la nature qui doit decider de la ligne.
        "nom": "recitation : la plage passe avant la nature",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        isInvocation -> invocationTitle ?: RecitationText.INVOCATION_FALLBACK\n        range != null -> Quran.reference(range)",
        "apres": "        range != null -> Quran.reference(range)\n        isInvocation -> invocationTitle ?: RecitationText.INVOCATION_FALLBACK",
        "tache": ":core:domain:test",
        "attendus": ["la nature decide de la ligne, pas la presence d'un titre"],
    },
    {
        # La pleine page porte le symbole de pause, la barre reduite non.
        "nom": "recitation : les deux mises en page disent la meme chose en pause",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        RecitationPhase.PAUSED -> RecitationText.PAUSED_FULL",
        "apres": "        RecitationPhase.PAUSED -> RecitationText.PAUSED_COMPACT",
        "tache": ":core:domain:test",
        "attendus": ["les deux mises en page ne disent pas la meme chose en pause"],
    },
    {
        # La pleine page n'affiche la duree que pendant la capture.
        "nom": "recitation : la pleine page lit le compteur vivant hors capture",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "        if (phase == RecitationPhase.RECORDING || phase == RecitationPhase.PAUSED) liveMs else null",
        "apres": "        if (phase == RecitationPhase.RECORDING && phase == RecitationPhase.PAUSED) liveMs else null",
        "tache": ":core:domain:test",
        "attendus": ["la pleine page ne lit que le compteur vivant"],
    },
    {
        # Un 00:00 fige se lirait comme un enregistrement vide.
        "nom": "recitation : la barre reduite montre une duree au repos",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "    ): Long? = if (phase == RecitationPhase.IDLE) null else draftMs ?: itemMs ?: liveMs",
        "apres": "    ): Long? = draftMs ?: itemMs ?: liveMs",
        "tache": ":core:domain:test",
        "attendus": ["la barre reduite ne montre aucune duree au repos"],
    },
    {
        # Une invocation rangee avec des bornes passerait pour le premier verset.
        "nom": "recitation : les bornes locales d'une invocation deviennent celles du passage",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "        if (kind == RecitationKind.INVOCATION) 0 to 0 else start to end",
        "apres": "        start to end",
        "tache": ":core:domain:test",
        "attendus": ["une invocation se range avec des bornes a zero"],
    },
    {
        # Tout autre mot vaut « pas encore acceptee » : le doute protege la personne.
        "nom": "recitation : la notice accepte n'importe quoi",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationRecorder.kt",
        "avant": "    fun noticeAccepted(stored: String?): Boolean = stored == NOTICE_ACCEPTED",
        "apres": "    fun noticeAccepted(stored: String?): Boolean = true",
        "tache": ":core:domain:test",
        "attendus": ["seul le mot attendu vaut acceptation"],
    },
    {
        # Le temps suspendu compte : la duree ecrite dans la ligne serait fausse.
        "nom": "recitation : la pause ne retire plus le temps suspendu",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecordingStopwatch.kt",
        "avant": "        accumulatedMs += nowMs() - from",
        "apres": "        accumulatedMs += 0",
        "tache": ":core:domain:test",
        "attendus": ["une pause exclut le temps suspendu"],
    },
    {
        # Recommencer ne reconstruit pas le chronometre : le cumul repart de zero.
        "nom": "recitation : un nouveau depart garde le cumul",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecordingStopwatch.kt",
        "avant": "        accumulatedMs = 0\n        startedAt = nowMs()",
        "apres": "        accumulatedMs = accumulatedMs\n        startedAt = nowMs()",
        "tache": ":core:domain:test",
        "attendus": ["un nouveau depart repart de zero"],
    },
    {
        # La duree est lue avant l'arret, et elle ne doit plus bouger ensuite.
        "nom": "recitation : l'arret ne fige plus la duree",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecordingStopwatch.kt",
        "avant": "        pause()\n        stopped = true\n        return accumulatedMs",
        "apres": "        stopped = true\n        return elapsedMs()",
        "tache": ":core:domain:test",
        "attendus": ["l'arret fige la duree"],
    },
    {
        # Un arret est un arret : reprendre ensuite relancerait une mesure terminee.
        "nom": "recitation : reprendre apres l'arret relance le chronometre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecordingStopwatch.kt",
        "avant": "        if (startedAt != null || stopped) return",
        "apres": "        if (startedAt != null) return",
        "tache": ":core:domain:test",
        "attendus": ["reprendre apres l'arret ne relance rien"],
    },
    {
        # Deux appuis rapproches ne doivent pas fabriquer un second segment de temps.
        "nom": "recitation : pauser deux fois compte deux fois",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecordingStopwatch.kt",
        "avant": "        val from = startedAt ?: return",
        "apres": "        val from = startedAt ?: 0L",
        "tache": ":core:domain:test",
        "attendus": ["pauser deux fois ne compte qu'une fois"],
    },
    {
        # Une invocation se range a zero. Ecrire les bornes recues ferait porter a la ligne locale
        # les versets d'un passage, et le modele local - deux `Int` que le reste du code additionne
        # - la ferait passer pour un passage du Coran.
        "nom": "recitation : les bornes d'une invocation se rangent telles quelles",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/local/RecitationStore.kt",
        "avant": "        val (localStart, localEnd) = Recitations.localBounds(kind, start, end)",
        "apres": "        val (localStart, localEnd) = start to end",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["les bornes d'une invocation sont ramenees a zero, quoi qu'on donne"],
    },
    {
        # Les minutes du temps ecoule de la liste ne sont PAS remplies. Le format de
        # l'enregistreur, lui, les remplit : deux formateurs coexistent dans le client d'origine
        # (`RecitationsScreen.tsx` ligne 13 contre `RecitationRecorder.tsx` ligne 15). Remplir
        # ici ferait dire « 01:00 » a un ecran qui dit « 1:00 ».
        "nom": "recitation : le temps de la liste ne remplit pas les minutes",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        return \"$minutes:${seconds.toString().padStart(2, '0')}\"",
        "apres": "        return \"${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}\"",
        "tache": ":core:domain:test",
        "attendus": [
            "le format de la liste differe de celui de l'enregistreur",
            "soixante secondes se lisent une minute, sans zero de tete",
        ],
    },
    {
        # Une recitation sans copie locale est une recitation venue du serveur : c'est la preuve
        # la plus forte que le depot a abouti, puisque le depot CREE cette copie. La lire « En
        # attente » annoncerait un depot a faire sur une recitation qui est deja partie.
        "nom": "recitation : une recitation sans copie locale se lit Synchronise",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        null, RecitationSyncStatus.SYNCED -> RecitationText.SYNC_SYNCED",
        "apres": "        null, RecitationSyncStatus.SYNCED -> RecitationText.SYNC_PENDING",
        "tache": ":core:domain:test",
        "attendus": ["une recitation sans copie locale se lit Synchronise"],
    },
    {
        # Un retour GENERAL compte autant qu'une correction de verset : les deux sont des retours
        # du relecteur. N'en compter qu'un ferait lire « En attente de correction » sous une
        # recitation que quelqu'un a deja commentee.
        "nom": "recitation : un retour general seul suffit a lire Corrigee",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        correctionCount > 0 || feedbackCount > 0 -> RecitationText.STATUS_CORRECTED",
        "apres": "        correctionCount > 0 -> RecitationText.STATUS_CORRECTED",
        "tache": ":core:domain:test",
        "attendus": ["un retour general seul suffit a lire Corrigee"],
    },
    {
        # Le plus recent d'abord. L'inverse mettrait en tete la recitation la plus ancienne, donc
        # celle que la personne a le moins de raisons de rouvrir.
        "nom": "recitation : la liste se range de la plus recente a la plus ancienne",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        return toutes.sortedByDescending { it.createdAt }",
        "apres": "        return toutes.sortedBy { it.createdAt }",
        "tache": ":core:domain:test",
        "attendus": ["les lignes se rangent de la plus recente a la plus ancienne"],
    },
    {
        # Une ligne presente des deux cotes est prise du DISTANT : c'est lui qui porte ce que le
        # local ignore - l'ecoute par un relecteur. Ajouter la copie locale en double ferait
        # apparaitre deux fois la meme recitation, dont une sans son etat d'ecoute.
        "nom": "recitation : le distant l'emporte sur le local qui double la meme recitation",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "            if (toutes.none { it.id == item.id }) toutes.add(asRemote(item))",
        "apres": "            toutes.add(asRemote(item))",
        "tache": ":core:domain:test",
        "attendus": ["le distant l'emporte sur le local qui double la meme recitation"],
    },
    {
        # La barre ne deborde pas : une position au-dela de la duree - le lecteur peut en rendre
        # une en fin de piste - est ramenee a cent pour cent.
        "nom": "recitation : la barre de progression est plafonnee a cent",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        return minOf(100f, positionMs.toFloat() / durationMs.toFloat() * 100f)",
        "apres": "        return positionMs.toFloat() / durationMs.toFloat() * 100f",
        "tache": ":core:domain:test",
        "attendus": ["une position au-dela de la duree est plafonnee"],
    },
    {
        # Reculer depuis le debut reste au debut : sans plancher, la position deviendrait
        # negative, et le lecteur recevrait une adresse invalide.
        "nom": "recitation : reculer ne passe pas avant le debut",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        maxOf(0L, positionMs - stepMs)",
        "apres": "        positionMs - stepMs",
        "tache": ":core:domain:test",
        "attendus": ["reculer depuis le debut reste au debut"],
    },
    {
        # Le repli d'une invocation sans titre vaut « Ma prononciation » dans la LISTE, et
        # « Invocation » dans l'enregistreur. C'est l'original : les deux ecrans parlent de deux
        # moments differents, celui ou l'on enregistre et celui ou l'on relit.
        "nom": "recitation : le repli de la liste n'est pas celui de l'enregistreur",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "            (invocationTitle ?: RecitationText.LIST_INVOCATION_FALLBACK)",
        "apres": "            (invocationTitle ?: RecitationText.INVOCATION_FALLBACK)",
        "tache": ":core:domain:test",
        "attendus": [
            "une invocation sans titre se replie sur Ma prononciation",
            "le repli de la liste differe de celui de l'enregistreur",
        ],
    },
    {
        # Une nature ABSENTE compte comme un passage du Coran : l'original ecrit
        # `item.recording_type ?? 'quran'`. La ranger parmi les invocations la ferait disparaitre
        # du filtre « Coran », c'est-a-dire du seul filtre ou elle a sa place.
        "nom": "recitation : une nature absente compte comme un passage",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        Filter.QURAN -> kind != RecitationKind.INVOCATION",
        "apres": "        Filter.QURAN -> kind == RecitationKind.QURAN",
        "tache": ":core:domain:test",
        "attendus": ["une nature absente compte comme un passage du Coran"],
    },
    {
        # La colonne `recording_type` a ete ajoutee apres coup : une ligne ecrite avant n'en porte
        # pas, et l'original la replie sur un passage du Coran. La replier sur une invocation
        # ferait disparaitre ces lignes du filtre « Coran » - c'est-a-dire du seul filtre ou elles
        # ont leur place - et leur titre annoncerait une invocation qu'elles ne sont pas.
        "nom": "recitation : une nature absente ne se replie pas sur une invocation",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/RecitationSource.kt",
        "avant": "    @SerialName(\"recording_type\") val recordingType: RecitationKind = RecitationKind.QURAN,",
        "apres": "    @SerialName(\"recording_type\") val recordingType: RecitationKind = RecitationKind.INVOCATION,",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une nature absente compte comme un passage du Coran"],
    },
    {
        # Le nom de colonne est le contrat avec le serveur. Le client Postgrest est configure sans
        # conversion de propriete : un `@SerialName` faux ne leve rien, il rend `null` - donc une
        # ligne dont les bornes disparaissent, et un titre de recitation sans reference.
        "nom": "recitation : le nom de colonne des bornes de debut est celui de la table",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/RecitationSource.kt",
        "avant": "    @SerialName(\"start_verse_id\") val startVerseId: Int? = null,",
        "apres": "    @SerialName(\"start\") val startVerseId: Int? = null,",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une ligne complete se traduit champ par champ"],
    },
    {
        # La nature de la ligne distante est celle que le serveur a ecrite, pas une constante. La
        # figer ferait passer toute invocation pour un passage du Coran : son titre demanderait une
        # reference de versets qu'elle n'a pas.
        "nom": "recitation : la nature distante est celle du serveur",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/RecitationSource.kt",
        "avant": "        kind = recordingType,",
        "apres": "        kind = RecitationKind.QURAN,",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une invocation se traduit avec sa nature et son invocation"],
    },
    {
        # Le nom de colonne de l'instant de traitement, meme raison : un `@SerialName` faux rend
        # `null`, et une correction traitee se lirait comme non traitee.
        "nom": "recitation : le nom de colonne de l'instant de traitement est celui de la table",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/RecitationSource.kt",
        "avant": "    @SerialName(\"resolved_at\") val resolvedAt: String? = null,",
        "apres": "    @SerialName(\"resolu\") val resolvedAt: String? = null,",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["une correction se traduit champ par champ"],
    },
    {
        # La borne de la liste distante. L'original borne a 100 pour la personne et a 200 pour
        # l'administrateur ; porter 200 ferait demander a chaque ouverture de liste le double de ce
        # qu'un ecran affiche.
        "nom": "recitation : la liste distante reste bornee a cent",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Recitations.kt",
        "avant": "    const val REMOTE_LIST_LIMIT: Int = 100",
        "apres": "    const val REMOTE_LIST_LIMIT: Int = 200",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la liste distante est bornee a cent lignes"],
    },
    {
        # La lecture distante publie ce que le serveur a rendu. Rendre une liste vide ferait
        # disparaitre de l'ecran des recitations qui existent, et le depot a deja lu la liste.
        "nom": "recitation : la lecture distante publie ce que le serveur porte",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "            _state.value = _state.value.copy(remote = rows, notice = null)",
        "apres": "            _state.value = _state.value.copy(remote = emptyList(), notice = null)",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la lecture distante publie ce que le serveur porte"],
    },
    {
        # L'ordre local puis distant. Le distant seul laisserait l'ecran vide sur une panne du
        # serveur, alors que les fichiers sont sur l'appareil — et ceux-la ne se retelechargent pas.
        "nom": "recitation : le registre local est publie avant la liste distante",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "        val ouvert = lireRegistre()\n        if (ouvert) lireDistant()\n        ouvert",
        "apres": "        lireDistant()\n        val ouvert = lireRegistre()\n        ouvert",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["le registre local est publie avant que la liste distante ne soit demandee"],
    },
    {
        # Le compte est reverifie apres l'attente. Sans ce controle, la liste d'un compte se
        # publie sous le compte suivant : la personne voit les recitations de quelqu'un d'autre.
        "nom": "recitation : la liste distante d'un compte change en vol n'est pas publiee",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "            if (session.currentOwner() != owner) return\n",
        "apres": "            // mutation : le compte n'est plus reverifie\n",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["la liste distante d'un compte change en vol n'est pas publiee"],
    },
    {
        # Le retrait distant emporte la copie locale. Sans cela, la recitation reste listee, et le
        # prochain depot la renvoie au serveur — annulant la suppression que la personne a demandee.
        "nom": "recitation : le retrait distant efface la copie locale",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "            store.list(owner).firstOrNull { it.id == item.id }?.let { store.remove(it) }\n",
        "apres": "            // mutation : la copie locale n'est pas retiree\n",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["le retrait distant efface le fichier, la ligne et la copie locale"],
    },
    {
        # On ne retire que ce qui est a soi. Sans la garde, un identifiant d'autrui coute deux
        # allers-retours reseau pour se faire refuser au bout.
        "nom": "recitation : le retrait distant ne part pas pour la recitation d'un autre",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "        if (item.userId != owner) {",
        "apres": "        if (false) {",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["le retrait distant ne part pas pour la recitation d'un autre"],
    },
    {
        # Un retrait sans lecteur le dit. Rendre faux en silence ferait croire a un geste sans
        # effet plutot qu'a une impossibilite.
        "nom": "recitation : le retrait distant sans lecteur le dit",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "            _state.value = _state.value.copy(notice = RecitationText.CONNECTION_REQUIRED)",
        "apres": "            _state.value = _state.value.copy(notice = null)",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["le retrait distant sans lecteur le dit"],
    },
    {
        # Un echec sans message reste lisible. `restMessage()` peut rendre `null`, et un `notice`
        # nul serait un echec **muet** : la personne aurait appuye, rien ne serait arrive, et rien
        # ne le dirait.
        "nom": "recitation : un echec du retrait sans message ne laisse pas un mot vide",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "                notice = error.restMessage() ?: error.toString(),",
        "apres": "                notice = error.restMessage(),",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un echec du retrait sans message ne laisse pas un mot vide"],
    },
    {
        # Une adresse signee ne se fabrique pas hors ligne. La rendre vide ferait jouer un fichier
        # dont l'adresse ne mene nulle part, au lieu de dire que la lecture est impossible.
        "nom": "recitation : l'adresse signee sans lecteur le dit",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/RecitationRepository.kt",
        "avant": "        val api = source ?: throw IllegalStateException(RecitationText.AUDIO_UNAVAILABLE)",
        "apres": "        val api = source ?: return \"\"",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["l'adresse signee sans lecteur le dit"],
    },
    {
        # Le vide se juge AVANT le filtre. Juger apres ferait dire « aucune recitation
        # enregistree » a quelqu'un qui en a, sous un filtre qui les cache.
        "nom": "recitation : le vide de la liste se juge avant le filtre",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            empty = toutes.isEmpty() && connecte && message == null && !state.loading,",
        "apres": "            empty = lignes.isEmpty() && connecte && message == null && !state.loading,",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["un filtre qui ne laisse rien passer ne dit pas qu'il n'y a rien"],
    },
    {
        # Sans compte mais avec un projet configure, on renvoie au profil : c'est la que la
        # connexion se fait. L'autre phrase laisserait la personne sans piste.
        "nom": "recitation : le message sans compte renvoie au profil",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "                RecitationText.LIST_SIGNED_OUT_PROFILE",
        "apres": "                RecitationText.LIST_SIGNED_OUT",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["sans compte, le message renvoie au profil"],
    },
    {
        # Sans projet configure, il n'y a pas d'ecran de connexion a ouvrir : y renvoyer serait
        # une impasse, et la phrase le dit autrement.
        "nom": "recitation : sans projet configure, le message n'envoie pas au profil",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            !connecte -> if (configured) {",
        "apres": "            !connecte -> if (true) {",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["sans projet configure, le message n'envoie pas au profil"],
    },
    {
        # Le statut du sous-titre est celui de la copie LOCALE : c'est elle qui porte l'etat du
        # depot. Le figer a `null` ferait lire « Synchronise » sur une recitation en attente.
        "nom": "recitation : le statut du sous-titre est celui de la copie locale",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            subtitle = sousTitre(item, locale?.syncStatus),",
        "apres": "            subtitle = sousTitre(item, null),",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["une copie locale en attente se lit En attente"],
    },
    {
        # Une recitation que le serveur ne porte pas se supprime par l'appareil. Repondre faux
        # ferait appeler le serveur pour une ligne qu'il n'a pas, et le fichier resterait.
        #
        # L'ancre a ete reprise quand `ligne` a cesse de recalculer la presence sur le serveur :
        # elle la lit desormais **une seule fois** (`surLeServeur`), parce qu'elle decide de deux
        # choses — par ou l'on supprime, et si le partage peut aboutir. Le cas vise toujours la
        # meme decision, et la mutation garde le meme sens : tout croire sur le serveur.
        "nom": "recitation : une recitation absente du serveur est locale seule",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            localOnly = !surLeServeur,",
        "apres": "            localOnly = false,",
        "apres": "            localOnly = false,",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["une recitation que le serveur ne porte pas se supprime par l'appareil"],
    },
    {
        # Les cartes ne s'affichent que sous la ligne ouverte : les publier quand rien n'est
        # ouvert ferait porter a l'ecran des corrections que personne n'a demandees.
        "nom": "recitation : les cartes ne s'affichent que sous la ligne ouverte",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            feedback = if (ouverte == null) {",
        "apres": "            feedback = if (false) {",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["les cartes ne s'affichent pas quand rien n'est ouvert"],
    },
    {
        # Le bouton dit ce que le geste fera : « Pause » pendant la lecture, « Reecouter »
        # sinon. Les inverser ferait appuyer sur « Reecouter » pour arreter.
        "nom": "recitation : le bouton de lecture suit l'etat de lecture",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            playLabel = if (inputs.playing) RecitationText.LIST_PAUSE else RecitationText.LIST_PLAY,",
        "apres": "            playLabel = if (inputs.playing) RecitationText.LIST_PLAY else RecitationText.LIST_PAUSE,",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["le bouton de lecture suit l'etat de lecture"],
    },
    {
        # Une correction se date au JOUR : l'heure d'un commentaire n'aide personne, et
        # l'original ne la montre pas.
        "nom": "recitation : la carte de correction se date au jour",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "                        date = RecitationText.dateOnly(correction.createdAt),",
        "apres": "                        date = null,",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["les cartes de correction portent le verset, le repli et le jour"],
    },
    {
        # Les corrections arrivent apres un aller-retour reseau. Sans l'etiquette, la reponse de la
        # ligne qu'on vient de quitter s'afficherait sous celle qu'on vient d'ouvrir : le
        # commentaire d'une recitation attribue a une autre, et rien ne le dirait.
        "nom": "recitation : les details d'une autre ligne ne s'affichent pas",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "    if (id != null && id == openId) this else RecitationsDetails()\n",
        "apres": "    RecitationsDetails()\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["les details d'une autre ligne ne s'affichent pas"],
    },
    {
        # Le registre local est publie avant la liste distante : entre les deux, la liste est vide
        # parce qu'on attend. Annoncer « aucune recitation enregistree » est alors une affirmation
        # fausse sur ce que la personne a fait, et elle tombe au pire moment — a l'ouverture.
        "nom": "recitation : une lecture en vol ne dit pas que la liste est vide",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            empty = toutes.isEmpty() && connecte && message == null && !state.loading,\n",
        "apres": "            empty = toutes.isEmpty() && connecte && message == null,\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["une lecture en vol ne dit pas que la liste est vide"],
    },
    {
        # L'ecran est plein ecran : sans bouton de retour, il n'a plus aucune sortie. Le libelle
        # est substitue, et non la ligne retiree, pour que la mutation compile : un ecran qui ne
        # compile pas n'apprend rien sur le controle.
        "nom": "recitation : l'ecran porte le bouton de retour",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "                text = RecitationText.LIST_BACK,\n",
        "apres": "                text = RecitationText.LIST_TITLE,\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran porte le bouton de retour et laisse le contenu a part"],
    },
    {
        # La route est servie mais ne rend plus l'ecran : `composable(...) { }` compile, et la
        # route reste « servie ». L'appui ouvrirait une page vide — c'est pourquoi le controle
        # exige aussi que l'ecran soit rendu, et pas seulement la route declaree.
        "nom": "recitation : la route sert bien l'ecran",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "            RecitationsScreen(onClose = { navController.popBackStack() })\n",
        "apres": "            // mutation : la route ne rend plus l'ecran\n",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["les recitations sont servies et branchees"],
    },
    {
        # Le rappel du tableau de bord n'est plus branche : la route est servie, mais le bouton de
        # la carte « Suivi » ne mene nulle part. Un rappel a sa valeur par defaut compile et
        # s'affiche : rien d'autre ne le dirait.
        "nom": "recitation : le tableau de bord ouvre les recitations",
        "fichier": "navigation/src/main/kotlin/com/msoumaya/deepseekandroid/navigation/AppScaffold.kt",
        "avant": "                onRecitations = { navController.navigate(AppRoutes.RECITATIONS) { launchSingleTop = true } },\n",
        "apres": "                // mutation : le rappel n'est plus branche\n",
        "tache": ":navigation:testDebugUnitTest",
        "attendus": ["les recitations sont servies et branchees"],
    },
    {
        # La piste chargee appartient a la ligne ouverte. Sans la comparaison d'identite, reprendre
        # la piste d'une AUTRE recitation ferait entendre le son d'une autre sous la ligne ouverte —
        # et rien a l'ecran ne dirait laquelle on entend.
        "nom": "recitation : la piste d'une autre ligne ne se reprend pas",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        loadedId == openId && !ended -> PlaybackAction.RESUME\n",
        "apres": "        !ended -> PlaybackAction.RESUME\n",
        "tache": ":core:domain:test",
        "attendus": ["une piste chargee pour une autre recitation se recharge"],
    },
    {
        # Un lecteur arrive a la fin de sa piste y reste : reprendre ne rendrait aucun son, et le
        # bouton « Reecouter » serait muet — le defaut que ce portage s'interdit.
        "nom": "recitation : une piste terminee se relance au lieu de se reprendre",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        loadedId == openId && !ended -> PlaybackAction.RESUME\n",
        "apres": "        loadedId == openId -> PlaybackAction.RESUME\n",
        "tache": ":core:domain:test",
        "attendus": ["une piste terminee se relance au lieu de se reprendre"],
    },
    {
        # L'ordre des cas porte le sens : `openId == null` passe avant `playing`, sinon un appui
        # sans ligne ouverte demanderait une pause — un geste que personne n'a demande, sur une
        # piste dont l'ecran ne montre meme pas le bouton.
        "nom": "recitation : un appui sans ligne ouverte ne demande rien",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        openId == null -> PlaybackAction.NOTHING\n        playing -> PlaybackAction.PAUSE\n",
        "apres": "        playing -> PlaybackAction.PAUSE\n        openId == null -> PlaybackAction.NOTHING\n",
        "tache": ":core:domain:test",
        "attendus": ["sans ligne ouverte, un appui ne demande rien"],
    },
    {
        # Le rappel a une valeur par defaut vide : l'oublier compile, s'affiche, et laisse le bouton
        # de lecture sans effet. Rien d'autre ne le dirait.
        "nom": "recitation : l'ecran branche le geste de lecture",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "        onPlayPause = viewModel::onPlayPause,\n",
        "apres": "        onPlayPause = {},\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran branche toutes les saisies et tous les gestes"],
    },
    {
        # Les deux avances portent deux libelles distincts : le signe et le sens. Substituer l'un a
        # l'autre laisse deux boutons qui reculent tous les deux, et rien ne le dirait.
        "nom": "recitation : l'ecran offre l'avance de dix secondes",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "                        text = RecitationText.SEEK_FORWARD,\n",
        "apres": "                        text = RecitationText.SEEK_BACK,\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran offre l'ecoute, ses deux avances et sa barre"],
    },
    {
        # Sans la garde, l'ecran poserait un bouton de lecture alors qu'aucun lecteur n'a ete fourni
        # au conteneur : le bouton s'affiche, et le premier appui ne fait rien.
        "nom": "recitation : l'ecoute n'est pas offerte sans lecteur",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "                    ecoute = if (state.canListen && ligne.open) {\n",
        "apres": "                    ecoute = if (ligne.open) {\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecoute n'est offerte que si un lecteur existe"],
    },
    {
        # L'echec d'ecoute est le seul retour qu'on recoit quand un fichier ne s'ouvre pas. Le
        # retirer laisse un appui sans effet et sans explication.
        "nom": "recitation : l'echec d'ecoute est affiche",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            playbackError != null -> playbackError\n",
        "apres": "            // mutation : l'echec d'ecoute n'est plus affiche\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["une erreur d'ecoute remplace le message du depot"],
    },
    {
        # Le composant attend une fraction, l'etat un pourcentage. Sans la division, la barre
        # recevrait 50 au lieu de 0,5 : bornee a 1, elle paraîtrait toujours pleine.
        "nom": "recitation : la barre recoit une fraction, pas un pourcentage",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "                ProgressTrack(value = gestes.progressPercent / 100f)\n",
        "apres": "                ProgressTrack(value = gestes.progressPercent)\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran offre l'ecoute, ses deux avances et sa barre"],
    },
    # --- Le partage d'une recitation -----------------------------------------------------------
    # Le partage traverse quatre couches : une regle dans `RecitationsList` (ce qui est
    # partageable), une autre dans `Social` (a qui), un geste dans `SocialRepository` (par ou), et
    # un branchement dans l'ecran. Chaque cas ci-dessous neutralise un maillon, et nomme le test
    # qui doit tomber.
    {
        # L'original **retire** le bouton pour une invocation. Le rendre partageable offrirait un
        # bouton qui ne s'activerait jamais : le geste mort que ce depot s'interdit.
        "nom": "partage : une invocation se partage aussi",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "fun shareOffered(kind: RecitationKind): Boolean = kind != RecitationKind.INVOCATION",
        "apres": "fun shareOffered(kind: RecitationKind): Boolean = true",
        "tache": ":core:domain:test",
        "attendus": ["le partage n'est pas offert pour une invocation"],
    },
    {
        # Le partage ecrit l'identifiant d'une ligne **distante** dans un message. L'oublier
        # laisserait partir un enregistrement encore local, que le serveur refuserait.
        "nom": "partage : une recitation encore locale se partage",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": "        shareOffered(kind) && remote",
        "apres": "        shareOffered(kind)",
        "tache": ":core:domain:test",
        "attendus": ["une recitation encore locale ne se partage pas"],
    },
    {
        # Le corps du message est ce que l'ami lit. Perdre le prefixe laisserait « Al-Fatiha 1-7 »
        # tout seul, ce qui se lit comme une citation, et non comme un enregistrement.
        "nom": "partage : le message perd le prefixe de la recitation",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/RecitationsList.kt",
        "avant": '        "${RecitationText.SHARE_PREFIX} · $reference"',
        "apres": "        reference",
        "tache": ":core:domain:test",
        "attendus": ["le message partage nomme la recitation et la reference"],
    },
    {
        # La regle est celle du **serveur** : `can_play_shared_recitation` n'ouvre l'enregistrement
        # qu'a un lien accepte. Proposer une demande en attente enverrait un partage que l'ami ne
        # pourrait pas ecouter — et l'ecran aurait annonce un envoi reussi.
        "nom": "partage : un destinataire non accepte reste propose",
        "fichier": "core/domain/src/main/kotlin/com/msoumaya/deepseekandroid/core/domain/Social.kt",
        "avant": "        links.filter { it.status == FriendLinkStatus.ACCEPTED }",
        "apres": "        links",
        "tache": ":core:domain:test",
        "attendus": ["seules les amities acceptees recoivent une recitation partagee"],
    },
    {
        # Le partage part par un **lien** : c'est le lien que le message porte. Envoyer la
        # recitation a la place du lien deposerait le message dans la mauvaise conversation.
        "nom": "partage : le partage part vers la mauvaise conversation",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt",
        "avant": "            api.shareRecitation(linkId, recitationId, description)",
        "apres": "            api.shareRecitation(recitationId, recitationId, description)",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un partage depose la recitation dans le lien vise"],
    },
    {
        # Sans source, il n'y a personne a qui envoyer. Rendre `null` ferait croire a un envoi
        # reussi, et l'ecran afficherait « Recitation partagee » pour un partage qui n'a pas eu
        # lieu — le seul geste de ce depot qui **rend** son erreur au lieu de la publier.
        "nom": "partage : sans source, le partage se croit reussi",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt",
        "avant": "        val api = source ?: return SocialText.CONNECTION_NEEDED",
        "apres": "        val api = source ?: return null",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["sans source, un partage est refuse au lieu de partir dans le vide"],
    },
    {
        # Un refus doit rendre sa raison : l'ecran la pose dans son bandeau, et c'est le seul
        # retour qu'on recoit d'un partage qui n'est pas parti. La taire laisserait un appui sans
        # effet et sans explication.
        "nom": "partage : un refus ne dit plus pourquoi",
        "fichier": "core/data/src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt",
        "avant": "            describe(error)",
        "apres": "            null",
        "tache": ":core:data:testDebugUnitTest",
        "attendus": ["un partage refuse rend sa raison au lieu de la publier"],
    },
    {
        # La garde de capacite : sans elle, l'ecran poserait un bouton de partage alors qu'aucune
        # couche sociale n'a ete fournie au conteneur — il n'y aurait personne a qui envoyer.
        "nom": "partage : l'ecran offre le partage sans couche sociale",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "                    partage = if (state.canShare && ligne.open && ligne.shareOffered) {",
        "apres": "                    partage = if (ligne.open && ligne.shareOffered) {",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran branche le partage, du bouton a la confirmation"],
    },
    {
        # Le rappel a une valeur par defaut vide : l'oublier compile, s'affiche, et laisse le
        # bouton « Partager » de la confirmation sans effet.
        "nom": "partage : la confirmation n'est plus branchee",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "        onConfirmShare = viewModel::onConfirmShare,\n",
        "apres": "        onConfirmShare = {},\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran branche le partage, du bouton a la confirmation"],
    },
    {
        # Meme raison pour le refus : sans ce rappel, la phrase de confirmation resterait a
        # l'ecran et aucun geste ne la refermerait.
        "nom": "partage : le refus de la confirmation n'est plus branche",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "        onCancelShare = viewModel::onCancelShare,\n",
        "apres": "        onCancelShare = {},\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran branche le partage, du bouton a la confirmation"],
    },
    {
        # Le choix d'ami appartient a la ligne ouverte. Le publier sans cette garde ferait flotter
        # une liste d'amis sous une ligne que la personne vient de replier.
        "nom": "partage : le choix d'ami survit au repli de sa ligne",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "        val choixOuvert = inputs.sharingId?.takeIf { it == ouverte?.id }",
        "apres": "        val choixOuvert = inputs.sharingId",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["le choix d'ami n'est publie que pour la ligne ouverte"],
    },
    {
        # La confirmation nomme l'ami. Le repli generique ferait confirmer l'envoi a « Ami », et
        # la personne ne saurait pas a qui elle envoie.
        "nom": "partage : la confirmation ne nomme plus l'ami",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "                RecitationText.shareBody(ami.name, partageLabel(ouverte, reference))",
        "apres": "                RecitationText.shareBody(RecitationText.FRIEND_FALLBACK, partageLabel(ouverte, reference))",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["la confirmation nomme l'ami et la reference"],
    },
    {
        # Le bouton est **retire** pour une invocation. Le rendre toujours offert poserait un geste
        # qui ne s'activerait jamais.
        "nom": "partage : le bouton s'offre aussi pour une invocation",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            shareOffered = RecitationsList.shareOffered(item.kind),",
        "apres": "            shareOffered = true,",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["une invocation n'offre pas le partage"],
    },
    {
        # Le bouton est **desactive** tant que la recitation n'est pas arrivee. L'activer laisserait
        # partir un partage que le serveur refusera : l'identifiant de la ligne distante n'existe
        # pas encore.
        "nom": "partage : une recitation encore locale devient partageable",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            shareable = RecitationsList.shareable(item.kind, remote = surLeServeur),",
        "apres": "            shareable = RecitationsList.shareable(item.kind, remote = true),",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["une recitation encore locale offre le partage mais l'empeche"],
    },
    {
        # Le message du partage est le geste le plus recent : il passe devant ce que la lecture
        # avait laisse. Le retirer laisserait la personne sans le seul retour d'un envoi reussi.
        "nom": "partage : le message du partage n'est plus affiche",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            shareMessage != null -> shareMessage",
        "apres": "            // mutation : le message du partage n'est plus affiche",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["le message du partage prime sur celui du depot"],
    },
    {
        # L'ordre des branches porte le sens : sans compte, l'original s'arrete avant la lecture,
        # et son message est celui de la connexion. Mettre le partage en tete ferait lire
        # « Recitation partagee » a quelqu'un qui n'a pas de compte.
        "nom": "partage : le message du partage passe avant l'invitation a se connecter",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsRenderer.kt",
        "avant": "            !connecte -> if (configured) {\n                RecitationText.LIST_SIGNED_OUT_PROFILE\n            } else {\n                RecitationText.LIST_SIGNED_OUT\n            }\n\n            shareMessage != null -> shareMessage\n",
        "apres": "            shareMessage != null -> shareMessage\n            !connecte -> if (configured) {\n                RecitationText.LIST_SIGNED_OUT_PROFILE\n            } else {\n                RecitationText.LIST_SIGNED_OUT\n            }\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["sans compte, l'invitation a se connecter prime sur le partage"],
    },
    {
        # Le message d'absence d'ami est le seul mot que recoit une personne sans ami accepte :
        # substituer l'indice de choix lui ferait chercher des destinataires qui n'existent pas.
        "nom": "partage : l'absence d'ami n'est plus dite",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "                    text = RecitationText.SHARE_NO_FRIEND,\n",
        "apres": "                    text = RecitationText.SHARE_HINT,\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran branche le partage, du bouton a la confirmation"],
    },
    {
        # Le titre de la confirmation est ce qui distingue la question du geste : sans lui, la
        # phrase de confirmation et le bouton « Partager » se suivent sans que rien n'annonce une
        # question.
        "nom": "partage : la confirmation n'a plus de titre",
        "fichier": "feature/recitations/src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/RecitationsScreen.kt",
        "avant": "                    AppLabel(text = RecitationText.SHARE_TITLE, selectable = false)\n",
        "apres": "                    AppLabel(text = RecitationText.SHARE_HINT, selectable = false)\n",
        "tache": ":feature:recitations:testDebugUnitTest",
        "attendus": ["l'ecran branche le partage, du bouton a la confirmation"],
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
    """Joue une tache Gradle. `tache` peut porter des options (`--tests ...`).

    **Rejouer n'est pas un confort.** Une tache de test que Gradle sert sans la rejouer rend le
    verdict d'une execution **precedente** : le rapport XML n'est pas reecrit, `echecs_depuis` ne
    voit rien, et le harnais conclut a tort. Le cas s'est produit deux fois, et de deux facons
    differentes.

    1. `Social.MESSAGE_PAGE` est un `const val` : la mutation recopiait la **meme** valeur, la
       classe compilee etait identique a l'octet pres, et Gradle a repondu `FROM-CACHE`.

    2. Un cas a **deux** modules — `:core:model:test :core:domain:test` — ou la mutation ne
       tombait que dans le **premier**. `--rerun` rejouait bien `:core:domain:test` mais servait
       `:core:model:test FROM-CACHE`, donc la mutation n'etait **jamais** jouee, et le cas
       passait pour concluant en ne tombant que sur les tests du module ou la mutation avait
       ete lue a la compilation.

    **Les deux drapeaux sont necessaires, et c'est mesure.** `--rerun` seul laisse le cache de
    build servir la tache : `:core:model:test FROM-CACHE` avec `--rerun`. `--no-build-cache`
    seul laisse le controle de fraicheur : `:core:model:test UP-TO-DATE`. Il faut
    `--rerun-tasks --no-build-cache` pour obtenir `:core:model:test` sans suffixe, donc
    reellement executee.

    Pourquoi `--rerun-tasks` et non `--rerun`. `--rerun` ne s'applique qu'aux taches **nommees**
    sur la ligne de commande ; `--rerun-tasks` couvre le graphe demande, ce qui est exactement
    ce qu'un falsificateur veut — et ce qui reste borne, puisqu'on ne nomme que la tache de test
    du ou des modules vises. Les dependances lointaines restent incrementales, ce qui garde le
    harnais utilisable sur vingt-deux modules.

    La surete vis-a-vis de la concurrence vient de cette fonction elle-meme : elle impose
    `--max-workers=1` et `parallel=false`, donc aucun autre ouvrier Gradle ne lit l'arbre pendant
    qu'une source est mutee.
    """
    env = dict(os.environ)
    env["JAVA_HOME"] = JDK
    env["PATH"] = os.path.join(JDK, "bin") + os.pathsep + env.get("PATH", "")
    resultat = subprocess.run(
        commande_gradle() + shlex.split(tache) + [
            "--rerun-tasks",
            "--no-build-cache",
            # **`--continue` est indispensable des qu'un cas nomme deux modules.** Sans lui,
            # Gradle s'arrete au premier module dont les tests tombent : mesure faite sur le cas
            # « signets : les cles de source », qui joue `:core:model:test :core:domain:test` —
            # la sortie ne portait que `> Task :core:model:test FAILED`, et `:core:domain:test`
            # n'a **jamais tourne**. Les tombes du second module etaient donc invisibles, et le
            # cas accusait `BookmarksScreenRulesTest` de ne pas couvrir une regle qu'il couvre :
            # joue seul sous la meme mutation, ce test tombe.
            "--continue",
            "--max-workers=1",
            "-Dorg.gradle.parallel=false",
        ],
        cwd=PROJET,
        env=env,
        capture_output=True,
        text=True,
    )
    return resultat.returncode, (resultat.stdout or "") + (resultat.stderr or "")


#: Les etats par lesquels Gradle annonce qu'une tache **n'a pas ete executee**. `NO-SOURCE` en
#: fait partie : une tache sans source ne joue rien, donc son silence ne prouve rien non plus.
_ETATS_SANS_EXECUTION = ("UP-TO-DATE", "FROM-CACHE", "SKIPPED", "NO-SOURCE")


def taches_non_rejouees(sortie: str, tache: str) -> list[str]:
    """Les taches **demandees** que Gradle a servies sans les executer.

    La portee est la ligne de commande, et rien d'autre. Un graphe de test contient des taches
    voisines — `:core:domain:testClasses UP-TO-DATE` sort de **toutes** les executions — et les
    compter ici declarerait « non concluant » a chaque cas. Ce qui compte est uniquement la tache
    dont le rapport de tests est lu : c'est elle qui doit avoir tourne.
    """
    demandees = {jeton for jeton in tache.split() if jeton.startswith(":")}
    arretees: list[str] = []
    for ligne in sortie.splitlines():
        morceaux = ligne.split()
        if len(morceaux) < 3 or morceaux[0] != ">" or morceaux[1] != "Task":
            continue
        if morceaux[2] not in demandees:
            continue
        if morceaux[-1] in _ETATS_SANS_EXECUTION:
            arretees.append(f"{morceaux[2]} {morceaux[-1]}")
    return arretees


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


_SOURCES_DE_TEST: dict[str, str] = {}


def modules_de_la_tache(tache: str) -> list[str]:
    """Les modules Gradle que ce cas fait jouer, dans l'ordre.

    Un cas peut en nommer **plusieurs** — `:core:model:test :core:domain:test` —, et il faut
    les lire tous : ne garder que le premier ferait declarer introuvable un nom qui vit dans
    le second.
    """
    modules: list[str] = []
    for jeton in tache.split():
        if not jeton.startswith(":"):
            continue  # une option (`--tests`) ou son filtre
        module = "/".join(jeton.split(":")[1:-1])
        if module and module not in modules:
            modules.append(module)
    return modules


def sources_de_test(module: str) -> str:
    """Tout le texte des tests d'un module, lu une seule fois par module.

    **Pourquoi ce controle existe.** Une passe a annonce deux cas « FAUX » alors que le test
    tombe etait exactement le bon. Le nom fige dans `attendus` etait celui du test de
    `feature:social` — « la liste est bornee a cinq amis, puis deployee » — alors que le cas
    joue `:core:domain:test`, ou la **meme regle** est eprouvee sous un autre nom — « la liste
    est bornee a cinq entrees, et le pli la libere ». Le harnais refusait donc pour un mauvais
    pretexte, et son message envoyait chercher un test manquant qui existait deja.

    La portee est le **module de la tache**, et rien de plus. Chercher dans tout le projet
    rendrait ce controle complaisant : deux tests distincts portent ici le meme nom dans deux
    modules, et valider un nom qu'aucune tache de ce cas ne peut produire redonnerait
    exactement le faux verdict qu'on veut eviter.
    """
    if module not in _SOURCES_DE_TEST:
        racine = os.path.join(PROJET, module.replace("/", os.sep), "src", "test")
        morceaux: list[str] = []
        for dossier, sous_dossiers, fichiers in os.walk(racine):
            # On elague au lieu de filtrer apres coup : un `os.walk` qui descend dans les
            # `build/` de vingt et un modules traverse des dizaines de milliers d'artefacts
            # pour ne lire aucun `.kt`.
            sous_dossiers[:] = [d for d in sous_dossiers if d not in ("build", ".gradle", ".git")]
            for fichier in fichiers:
                if fichier.endswith(".kt"):
                    try:
                        morceaux.append(open(os.path.join(dossier, fichier), encoding="utf-8").read())
                    except OSError:
                        continue
        _SOURCES_DE_TEST[module] = "\n".join(morceaux)
    return _SOURCES_DE_TEST[module]


def precondition(cas: dict) -> str | None:
    """Ce qui empeche de jouer ce cas, ou `None` s'il est jouable.

    Un cas n'est jouable que si son texte a remplacer est present **une seule fois**. Zero fois
    veut dire que la source a bouge et que le cas ne falsifie plus rien ; deux fois veut dire que
    le remplacement toucherait un endroit qu'on n'a pas choisi. Dans les deux cas la tache
    Gradle tournerait, mais elle ne prouverait pas ce que le cas annonce — d'ou ce controle
    **avant** de depenser une execution.

    Le nom attendu est verifie **avant** de jouer, pour la meme raison : un nom qu'aucune tache
    du cas ne peut produire fait rendre « FAUX » a un cas pourtant concluant, et le verdict
    accuse alors le test au lieu du cas. Le controle porte sur les **modules des taches du
    cas** — voir `sources_de_test`.
    """
    chemin = os.path.join(PROJET, cas["fichier"].replace("/", os.sep))
    if not os.path.exists(chemin):
        return f"fichier introuvable : {cas['fichier']}"
    occurrences = open(chemin, encoding="utf-8").read().count(cas["avant"])
    if occurrences != 1:
        return (f"le texte a remplacer apparait {occurrences} fois (1 attendu) "
                f"dans {cas['fichier']} : {cas['avant'].strip()!r}")
    modules = modules_de_la_tache(cas["tache"])
    if not modules:
        return (f"la tache {cas['tache']!r} ne nomme aucun module : le nom attendu ne peut "
                f"pas etre verifie")
    texte = "\n".join(sources_de_test(module) for module in modules)
    for attendu in cas["attendus"]:
        if attendu not in texte:
            return (f"le nom attendu {attendu!r} n'existe dans aucun test de "
                    f"{' + '.join(modules)} : le cas ne peut pas conclure, et le verdict "
                    f"accuserait le test a tort")
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


#: Les verdicts d'un cas. Trois etats, et pas deux : un cas dont le test n'a **pas tourne** n'est
#: ni concluant ni faux — c'est le harnais qui ne sait pas, et le dire evite d'accuser un test.
CONCLUANT = "concluant"
FAUX = "faux"
NON_CONCLUANT = "non concluant"
HARNAIS = "echec du harnais"


def tache_atteinte(sortie: str, tache: str) -> bool:
    """Vrai si Gradle a **nomme** au moins une tache demandee : signe qu'il est alle jusque-la.

    Sans ce controle, un Gradle tombe avant la tache — option inconnue, configuration cassee —
    rend le meme silence qu'un test qui ne couvre pas la regle, et le verdict accuserait le test.
    """
    demandees = {jeton for jeton in tache.split() if jeton.startswith(":")}
    for ligne in sortie.splitlines():
        morceaux = ligne.split()
        if len(morceaux) >= 3 and morceaux[0] == ">" and morceaux[1] == "Task":
            if morceaux[2] in demandees:
                return True
    return False


def jouer(cas: dict) -> str:
    """Joue un cas et rend son verdict : `CONCLUANT`, `FAUX`, `NON_CONCLUANT` ou `HARNAIS`.

    **Pourquoi trois etats et pas deux.** Un « FAUX » affirme que le test ne couvre pas la regle.
    Cette affirmation n'est fondee que si le test a **tourne**. Quand Gradle annonce la tache de
    test `FROM-CACHE` ou `UP-TO-DATE`, le rapport lu date d'une execution precedente : le cas ne
    prouve rien, et le declarer faux enverrait chercher un test manquant qui existe peut-etre.
    """
    chemin = os.path.join(PROJET, cas["fichier"].replace("/", os.sep))
    avant = cas["avant"]
    apres = cas["apres"]
    origine = empreinte(chemin)
    texte = origine.decode("utf-8")

    probleme = precondition(cas)
    if probleme is not None:
        print(f"  ECHEC DU HARNAIS : {probleme}")
        return HARNAIS

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
        return HARNAIS

    # Le cache se controle **avant** le rapport : une tache servie sans etre rejouee laisse le
    # rapport intact, donc `echecs_depuis` ne rend rien, et l'ordre inverse ferait conclure
    # « FAUX » — c'est exactement le faux verdict qu'on veut rendre impossible.
    servies = taches_non_rejouees(sortie, cas["tache"])
    if servies:
        print(f"  NON CONCLUANT : {', '.join(servies)} — Gradle a servi cette tache sans la rejouer.")
        print("                  Le rapport de tests date d'une execution precedente : ce cas ne")
        print("                  prouve rien. Le relancer, et si cela se repete, c'est le harnais.")
        return NON_CONCLUANT

    tombes = echecs_depuis(instant)
    if not tombes:
        if not tache_atteinte(sortie, cas["tache"]):
            print("  ECHEC DU HARNAIS : la tache n'a jamais ete atteinte — Gradle est tombe avant.")
            print("  --- fin de sortie Gradle ---")
            for ligne in sortie.strip().splitlines()[-12:]:
                print("   " + ligne)
            return HARNAIS
        print("  FAUX : aucun test n'est tombe. Le test ne couvre pas cette regle,")
        print("         ou la tache n'a rien joue (compilation en echec, filtre trop etroit).")
        print("  --- fin de sortie Gradle ---")
        for ligne in sortie.strip().splitlines()[-12:]:
            print("   " + ligne)
        return FAUX

    correspond = [t for t in tombes if any(a in t for a in cas["attendus"])]
    if not correspond:
        print(f"  FAUX : les tests tombes ne sont pas ceux attendus ({len(tombes)} tombes).")
        for t in tombes:
            print(f"    - {t}")
        return FAUX

    # Chaque attendu doit avoir **son** test tombe, et pas seulement « au moins un ».
    #
    # Pourquoi ce controle en plus du precedent. Un cas peut nommer plusieurs attendus quand la
    # regle qu'il vise est tenue par plusieurs tests — c'est le cas de quatre cas ici. Avec un
    # simple « au moins un », un cas a deux attendus dont un seul tomberait serait declare
    # concluant : la moitie de la regle ne serait pas couverte, et personne ne le saurait. Le
    # compte des attendus doit donc etre confronte, nom par nom.
    #
    # Les tests tombes **en plus** ne rendent pas le cas faux : une mutation peut legitimement
    # faire tomber un test voisin qui partage l'invariant mute. Ce qui est exige, c'est que
    # **aucun** attendu ne reste sans test tombe.
    orphelins = [
        a for a in cas["attendus"]
        if not any(a in t for t in tombes)
    ]
    if orphelins:
        print("  FAUX : des attendus n'ont aucun test tombe :")
        for a in orphelins:
            print(f"    - {a!r} : annonce comme devant tomber, et rien n'est tombe.")
        print(f"  (tests tombes : {len(tombes)})")
        for t in tombes:
            print(f"    - {t}")
        return FAUX

    print(f"  OK : {len(tombes)} test(s) tombe(s), couvrant les {len(cas['attendus'])} attendu(s) :")
    for t in tombes:
        print(f"    - {t}")
    return CONCLUANT


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
    verdicts: dict[str, int] = {}
    for numero, cas in enumerate(choisis, 1):
        print()
        print(f"[{numero}/{len(choisis)}] {cas['nom']}")
        verdict = jouer(cas)
        verdicts[verdict] = verdicts.get(verdict, 0) + 1

    print()
    # Le compte est rendu **par verdict**, et pas en « reussis / total » : un cas non concluant
    # n'est pas un cas faux, et les fondre ferait perdre l'information qui dit quoi relancer.
    print(f"VERDICT : {verdicts.get(CONCLUANT, 0)}/{len(choisis)} falsification(s) concluante(s)")
    for etat, etiquette in ((NON_CONCLUANT, "non concluante(s)"),
                            (FAUX, "fausse(s)"),
                            (HARNAIS, "en echec du harnais")):
        if verdicts.get(etat):
            print(f"          {verdicts[etat]} {etiquette}")
    return 0 if verdicts.get(CONCLUANT, 0) == len(choisis) else 1


if __name__ == "__main__":
    raise SystemExit(main())
