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
        "avant": "            readPages = (base.effectiveReadPages + page).distinct(),",
        "apres": "            readPages = base.effectiveReadPages,",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMemoryTest"],
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
        "avant": "            reader = if (source == MushafSource.CORAN_TEST) {\n                (base.reader ?: ReaderPreferences()).copy(\n                    mushaf = MushafSource.CORAN_TEST,\n                    followAudio = base.reader?.followAudio != false,\n                    testPage = page,\n                )\n            } else {\n                base.reader\n            },",
        "apres": "            reader = (base.reader ?: ReaderPreferences()).copy(\n                mushaf = MushafSource.CORAN_TEST,\n                followAudio = base.reader?.followAudio != false,\n                testPage = page,\n            ),",
        "tache": ":core:domain:test",
        "attendus": ["ReaderMemoryTest"],
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
