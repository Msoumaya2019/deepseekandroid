package com.msoumaya.deepseekandroid.feature.sources

import com.msoumaya.deepseekandroid.core.data.repository.QuranArchiveStore
import com.msoumaya.deepseekandroid.core.domain.archiveMushafPages
import com.msoumaya.deepseekandroid.core.model.MushafPageSource

/**
 * Les images du paquet installé, telles que le lecteur les reçoit.
 *
 * C'est ici que se referme la couture : le lecteur ne connaît ni le stockage ni le paquet, et
 * le magasin ne connaît pas le lecteur. Le nommage des fichiers — page sur trois chiffres,
 * ligne sur deux — appartient au domaine (`zipLineFileName`), et l'adresse au magasin.
 *
 * Rien n'est vérifié ici : savoir si une page a le droit de s'afficher est une règle de
 * `QuranSourceReady`, et c'est l'écran qui la consulte. Un fournisseur d'images qui refuserait
 * de décrire une page ferait tomber le lecteur au lieu de laisser l'écran dire ce qui manque.
 */
fun archiveMushafPages(store: QuranArchiveStore): MushafPageSource =
    archiveMushafPages { page, line -> store.lineUri(page, line) }
