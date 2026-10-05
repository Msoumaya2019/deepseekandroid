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
