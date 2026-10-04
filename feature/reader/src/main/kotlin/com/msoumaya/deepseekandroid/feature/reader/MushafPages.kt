package com.msoumaya.deepseekandroid.feature.reader

import com.msoumaya.deepseekandroid.core.domain.embeddedMushafPages
import com.msoumaya.deepseekandroid.core.model.MushafPageSource

/**
 * Le moushaf embarqué, tel que le lecteur le reçoit par défaut.
 *
 * Le nommage des images est une affaire d'`android_asset` — donc de cette couche. La
 * description d'une page, elle, est dans le domaine : c'est une règle, et une règle s'éprouve
 * sans écran.
 */
val EmbeddedMushafPages: MushafPageSource = embeddedMushafPages { page -> MushafAssets.uri(page) }
