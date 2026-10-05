# Migration React Native → Android natif

Tableau de correspondance, fonctionnalité par fonctionnalité.

**Statuts utilisés**

| Statut | Signification |
|---|---|
| **Porté** | la logique existe en Kotlin et est couverte par des tests |
| **Écrite** | le code existe, compile, mais n'a pas encore été exercé contre le serveur ni à l'écran |
| **À faire** | identifié, non commencé |
| **Hors périmètre** | volontairement exclu de l'application Android |

Source analysée : `Msoumaya2019/coran-memoire`, version **0.9.38**, paquet `fr.coranmemoire.app`.
Elle est en **lecture seule** : elle est lue pour comprendre, jamais modifiée.

---

## 1. Socle

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| État applicatif complet | `src/core/program.ts` (`AppState`, `defaultState`) | `core/model/AppState.kt` | `user_state.data` | **Porté** | champs optionnels rendus **nullables** : un `null` du serveur ne doit jamais écraser une valeur locale |
| Sérialisation compatible | `JSON.stringify` / `JSON.parse` | `core/model/AppJson.kt` | — | **Porté** | `ignoreUnknownKeys`, `explicitNulls = false`, `encodeDefaults = true` : sans quoi un aller-retour serveur perdrait des champs |
| Dates locales | `dateKey`, `addDays`, `dayOf`, `todayLocal` | `core/domain/Dates.kt` | — | **Porté** | arithmétique ancrée à midi, pour survivre aux changements d'heure |
| Stockage local | `src/services/storage.ts` (SQLite) | `core/data/local/*` | — | **Porté** | voir [LOCAL_DATA_MIGRATION.md](LOCAL_DATA_MIGRATION.md) |
| File d'attente | `src/services/storage.ts`, `src/core/offlineQueue.ts` | `core/data/local/OutboxStore.kt`, `core/domain/OfflineQueue.kt` | — | **Porté** | une seule entrée par type et par compte ; acquittement après confirmation |
| Fusion hors ligne | `src/core/offlineMerge.ts` | `core/domain/OfflineMerge.kt` | — | **Porté** | fusion sur l'**arbre JSON** pour préserver les champs inconnus |
| Synchronisation | `src/services/sync.ts` | `core/data/repository/UserRepository.kt`, `core/domain/AccountSync.kt` | `user_state` | **Écrite** | on ne pousse jamais sans avoir lu |
| Session chiffrée | `src/services/authStorage.ts` (SecureStore par fragments) | `core/data/security/SecretVault.kt`, `remote/VaultSessionManager.kt` | — | **Porté** | AES/GCM, clé dans `AndroidKeyStore` |
| Détection de connectivité | `src/services/connectivity.ts` | — | — | **À faire** | phase E |
| Remise à zéro | `resetAllProgress` | `Program.resetAllProgress`, `UserRepository.resetProgress` | `user_state` | **Porté** | conserve identité, signets, profil, apparence |
| Assemblage et injection | `App.tsx` + variables d'environnement | `core/data/AppContainer.kt` + `app/DeepSeekApplication.kt` | — | **Porté** | conteneur écrit à la main, publié par `CompositionLocal`. Construit une fois, dans `Application.onCreate` : une rotation ne doit pas relire le référentiel coranique |
| Chargement du référentiel coranique | import statique des données | `core/data/AppContainer.kt` (`QuranState`) | — | **Porté** | **asynchrone** : lire 6 236 versets sur le fil principal retarderait la première image. Trois états — en cours, prêt, échoué — et non un booléen, pour que l'échec ne s'affiche pas comme une attente sans fin |
| Thème appliqué au démarrage | `applyTheme()` (mutation d'un objet global) | `app/MainActivity.kt` + `DeepSeekTheme` | — | **Porté** | les couleurs sont fournies par l'environnement Compose, pas mutées : un composant mémoïsé voit le changement, ce que la mutation globale ne permettait pas |
| Couleur des icônes système | `<StatusBar barStyle="dark-content" />` | `MainActivity.SystemBars` | — | **Écart assumé** | l'original imposait des icônes sombres en toutes circonstances, ce qui les rend invisibles sur le thème « Bleu Nuit ». Ici elles suivent la clarté du thème. Seul endroit où le portage corrige un défaut visible plutôt que de le reproduire |
| Sauvegarde système | — | `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | — | **Ajout Android** | la progression suit l'utilisateur (mois de travail), le jeton de session non : il est chiffré par une clé qui ne quitte pas l'appareil |

### Compte et connexion

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Décision d'ouverture | `initialAccountAccess`, `accountIntro` dans `src/App.tsx` | `core/domain/AccountAccess.kt` | — | **Porté** | trois états comme l'original (`checking` / `show` / `done`), mais **tous atteignables** : `setAccountIntro('checking')` n'est écrit nulle part côté React Native, où `loadState()` lit le disque de façon synchrone. Ici la lecture est asynchrone, et un second cas s'y ajoute — un compte connu dont l'état n'a pas encore été rapatrié |
| Témoin d'état local | `loadAccountState(user.id)` rend `null` | `LocalStateStore.hasStoredStateFor`, `UserRepository.hasStoredState` | — | **Ajout Android** | le magasin rend **toujours** un document : rien, dans la valeur, ne distingue « compte jamais rapatrié » de « progression vide ». Seul le fichier témoigne. Sans lui, la porte ouvrirait un programme vide, et la première séance validée dedans écraserait le vrai compte |
| Lecture du serveur avant d'ouvrir | `pullState()` dans `activateAccount` | `AuthViewModel.fetch`, `UserRepository.sync` | `user_state` | **Porté** | un tirage réussi écrit l'état local, donc la porte se rouvre d'elle-même ; un échec laisse l'écran de bienvenue affiché **avec la raison**, au lieu d'ouvrir sur du vide |
| Propriétaire courant | `state.userId` du fichier d'état | `AuthRepository` + `SessionPreferences` (DataStore) | — | **Écart assumé** | une session **absente** n'efface pas le propriétaire : un jeton expiré, un téléphone sans réseau et un projet injoignable produisent tous « pas de session », et l'original les traitait comme une déconnexion. Seul `signOut()` efface, explicitement |
| Écran d'attente | `accountIntro === 'checking'` | `feature/auth/AccountGate.kt` | — | **Porté** | fond peint explicitement : le fond de fenêtre Android est fixé en clair, et un lancement en thème sombre commencerait sinon par un écran crème |
| Écran de bienvenue | `AccountWelcome` dans `src/App.tsx` | `feature/auth/AccountWelcomeScreen.kt` | — | **Porté** | les trois choix, puis le formulaire. Le glyphe ۞ est rendu avec la police arabe embarquée (Amiri) et non avec la police d'interface, qui n'a pas de couverture arabe |
| Formulaire de connexion / inscription | `AccountWelcome`, `submit()` | `feature/auth/AuthViewModel.kt`, `AuthUiState.kt` | `auth.users` | **Porté** | l'activation des boutons reprend la règle de l'original, **volontairement lâche** (présence d'un `@`) : un bouton grisé n'explique pas ce qui manque, alors qu'un appui déclenche le contrôle complet |
| Connexion avec un mot de passe court | `signIn(email, password)` | `AuthInput.emptyPasswordProblem` | `auth.users` | **Écart assumé** | la connexion ne juge **pas** la longueur : le serveur partagé est seul juge, et un compte créé sous une politique plus permissive doit pouvoir se connecter, comme il le fait encore côté React Native. Seul le vide est refusé localement |
| Messages d'échec | deux phrases seulement | `core/domain/AuthFeedback.kt` | — | **Écart assumé** | l'original répondait « Connexion impossible. Vérifie ton adresse et ton mot de passe. » à **toutes** les causes, y compris une absence de réseau. Chaque cause a désormais sa phrase ; les deux phrases d'origine sont conservées mot pour mot là où elles étaient justes, et un test vérifie qu'aucune cause ne partage son message avec une autre |
| « Mot de passe oublié » | `requestPasswordLink(email)` | `AuthRepository.requestPasswordReset` | `auth.users` | **Porté** | `resetPasswordForEmail` du SDK. Le message reste neutre : le serveur répond « succès » même pour une adresse inconnue, pour empêcher l'énumération des comptes |
| « Renvoyer la confirmation » | `resendSignupConfirmation(email)` | `AuthRepository.resendSignUpConfirmation` | `auth.users` | **Porté** | `resendEmail(OtpType.Email.SIGNUP, …)`. Le bouton n'apparaît qu'après un premier message, comme dans l'original |
| Photo de profil à l'inscription | `chooseAvatar()` + `stageAvatar()` | — | `storage.objects` | **À faire** | phase D. Demande un sélecteur d'images et un envoi vers le stockage ; un bouton inerte serait pire que son absence |
| Lien profond du courriel | `consumeAuthLink`, schéma `coranmemoire://` | — | — | **À faire** | phase D. Sans schéma vérifié, le lien envoyé ouvre la page du projet Supabase et non l'application ; `redirectUrl` est donc laissé nul |
| Récupération du mot de passe | `passwordRecovery`, `changePassword` | — | `auth.users` | **À faire** | phase D, dépend du lien profond ci-dessus |

---

## 2. Coran

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Référentiel (6 236 versets, 114 sourates, 30 juz, 60 hizb, 120 nisf, 604 pages) | `src/core/quran.ts`, `src/data/verses.json`, `pages.json` | `core/domain/Quran.kt`, `QuranDataLoader.kt` | — | **Porté** | identifiants **globaux contigus** 1–6236 : la continuité entre les clients en dépend |
| Sources de moushaf | `src/core/quranSources.ts` | `core/model/Enums.kt` (`MushafSource`), `core/domain/ZipQuranSource.kt` | — | **Porté** | `coran_1441` et `coranTest` démarrés ; sources héritées reconnues et migrées |
| Changement de source | `src/core/quranSourceTransition.ts` | `core/domain/QuranSourceTransition.kt` | — | **Porté** | sérialisé par verrou ; on ne valide qu'après préparation |
| Découpage de page | `src/core/readerData.ts` | `core/domain/Reader.kt` (`ReaderLayout`) | — | **Porté** | 1920 × 3106, cadre de 4 px, mise à l'échelle sans déformation |
| Zoom | `src/core/readerZoom.ts` | `core/domain/Reader.kt` (`ReaderZoomGeometry`) | — | **Porté** | 1× à 3×, ancré sous les doigts |
| Navigation par balayage | `src/core/pageNavigation.ts` | `core/domain/Reader.kt` (`PageNavigation`) | — | **Porté** | seuil 60 px, rapport horizontal 1,5 |
| Numéros de versets | `src/core/ayahMarker.ts` | `core/domain/Reader.kt` (`AyahMarker`) | — | **Porté** | chiffres arabes, corps 19 ou 16 selon le nombre de chiffres |
| Annotations de marge | `src/core/marginAnnotations.ts` | `core/domain/Reader.kt` (`MarginAnnotations`) | — | **Porté** | |
| Apparence de lecture | `src/core/readerAppearance.ts` | `AppState.reader`, `AppState.theme` | `user_state` | **Porté** | |
| Rendu de page | `src/MushafPage.tsx`, `src/ui/ZoomableReader.tsx` | `feature/reader/MushafPageView.kt` | — | **Livré** | image ajustée par `ReaderLayout.fitMushafPage`, jamais déformée ; surlignages du verset sélectionné, des signets et des versets difficiles ; **signet dessiné en marge** (14 dp, bord droit de la page intérieure, à la hauteur du haut du verset). L'ordre des teintes — difficile, puis signet, puis lecture — est celui de `MushafPage.tsx`, et il est **éprouvé** dans `core:domain` (`ReaderTint`), non écrit dans la vue |
| Gestes du lecteur | `src/ui/ZoomableReader.tsx` (PanResponder) | `feature/reader/ReaderGestures.kt`, `core/domain/ReaderInteraction.kt` | — | **Livré** | **un seul** gestionnaire de pointeurs : balayage, pincement, appui et appui long ne peuvent pas se disputer les événements |
| Centrage de la page | `onLayout` + `centerContent` | `ReaderScreen` (`BoxWithConstraints` + `WindowInsets.safeDrawing`) | — | **Livré** | la page se centre dans l'espace **sûr** ; le fond va jusqu'aux bords ; aucune marge fixe |
| Coquille du lecteur | `src/ui/ImmersiveReaderChrome.tsx` | `feature/reader/ReaderChrome.kt` | — | **Livré** | **dans le flux**, pas flottante : une barre flottante masquerait le dernier verset de la page |
| Fiche du verset | `ReaderMoreSheet`, `sessionPanel==='verse'` | `feature/reader/ReaderScreen.kt` (`VerseCard`) | `user_state` | **Livré** | appui long → référence et traduction française du sens |
| Préchargement des voisins | `Image.prefetch` sur `page-1` / `page+1` | `core/domain/ReaderInteraction.kt` (`ReaderPreload`), `MushafAssets.kt` | — | **Livré** | **trois pages au maximum**, la courante en tête ; borné et éprouvé |
| Téléchargement de sources | `src/services/quranDownload.ts` | `core/domain/QuranArchive.kt`, `core/data/local/QuranArchiveInstaller.kt` | — | **Porté** | archive de 102 608 011 octets, reprise, témoin de 9 060 fichiers écrit **en dernier** |
| Écran de téléchargement | `src/ui/QuranDownload.tsx` | `feature:sources/QuranDownloadPanel.kt`, `core/domain/QuranDownloadText.kt` | `user_state` | **Livré** | titre, sous-titre, phase et pourcentage, **un seul bouton à la fois** et **aucun pendant l'écriture des pages** — trois règles éprouvées, pas trois choix d'affichage. La pause est posée à l'arrière-plan **et** au démontage, comme l'original |
| Choix de la source « Coran 1441 » | `DownloadSourceChoice` (`QuranDownload.tsx`) | `feature:sources/QuranSourceChoice.kt` | `user_state` | **Livré** | paquet installé → la source est adoptée ; sinon le panneau se déplie, et c'est **la fin de l'installation** qui adopte |
| Porte du lecteur | `App.tsx:491` | `navigation/ReaderRoute.kt` | `user_state` | **Livré** | une source en paquet non installée **remplace la page** par le panneau. Limite dite : comme l'original, la porte ne vérifie que le **témoin**, pas le contenu installé |
| Sélecteur de présentation | `Modal` de `App.tsx:515` | `feature:sources/QuranSourcePicker.kt` | `user_state` | **Livré**, à deux réserves | les trois présentations simples et le paquet ; il manque les deux boutons qui dépendent de la séance (« Actions de la séance », « Retour aux options »), qui viendront avec la coquille d'étude |
| Transition de source | `src/core/quranSourceTransition.ts` | `core/domain/QuranSourceTransition.kt` | — | **Porté** | une transition à la fois, **refusée** et non mise en attente ; validation **attendue** (`commit` suspend, parce qu'ici elle écrit l'état) |
| Source « Coran Test » | `src/coranTest/*` | `core/model/TestPage.kt`, `core/domain/TestPage{Index,Loader,Html,Session,Overlay}.kt`, `feature/reader/TestPage{Colors,Fonts,Documents,Surface,View}.kt` | — | **Livré** | l'écran immersif, monté **dans** le lecteur par `ReaderScreen` quand la source est `coranTest`. Le document qu'il affiche peint la page par sa police et **se mesure lui-même** : les rectangles des versets ne traversent donc **pas** la frontière — l'original les rangeait dans un dictionnaire qu'il ne relisait jamais, ce qui a été mesuré avant de décider. Les décisions de l'original sont portées mot pour mot : l'annonce d'une page n'est crue que si elle nomme la page **demandée**, une panne que si elle vient de la page **en cours**, un geste que sur la page **affichée**. **Écart assumé** : le **bandeau** de séance n'est pas porté dans le document — ce client le rend en Compose, au-dessus de la page, **pour toutes les sources** ; le porter aussi ici ferait deux bandeaux pour une seule séance. Les **repères de marge**, eux, y sont portés : ils appartiennent au document, qui est le seul à connaître les rectangles de ses mots. **Depuis** : l'original retient la page atteinte dans `reader.testPage` à la fermeture du lecteur ; le portage l'écrit désormais (`ReaderMemory`), et la route rouvre à la page mémorisée — voir « Points d'attention transverses » |
| Sélecteur de sourate | `src/SurahPicker.tsx` | `feature/reader/SurahPickerScreen.kt`, `core/domain/SurahPickerText.kt` | — | **Livré** | les 114 sourates, et l'accès direct à une page. La page demandée est **validée avant d'être suivie** : une saisie refusée laisse la fenêtre ouverte avec sa raison, au lieu de la refermer sur rien. La sourate ne dit pas où aller — la conversion appartient à l'appelant, seul à connaître le découpage de la source affichée |
| Feuille d'options du lecteur | `src/ui/ReaderMoreSheet.tsx` | `feature/reader/ReaderOptionsSheet.kt`, `core/domain/ReaderOptionsText.kt` | — | **Livré** | le carrefour du lecteur, ouvert par le bouton « ⋯ » de la coquille. Les quatre lignes mènent quelque part : changer de sourate, la traduction française, les réglages d'écoute, et le choix de présentation quand l'appelant en propose un |
| Panneau de traduction | `App.tsx:509` (`sessionPanel==='translation'`) | `feature/reader/TranslationPanelSheet.kt`, `core/domain/TranslationPanel.kt` | — | **Livré** | la page affichée, verset par verset : la référence en doré, puis la traduction, séparées d'un filet. Le renvoi de note `[1]` reste dans le texte et la note n'est pas déroulée, comme dans l'original. Une plage hors du corpus est **ramenée** aux versets qui existent au lieu de faire tomber l'écran — l'original, lui, lève. Quels versets exactement (la séance, sinon la page) est une règle de `core:domain`, éprouvée sur les 6 236 versets |
| Marques de la page | `App.tsx:485` (`visibleBookmarks`, `difficultyMarkers`), `MushafPage.tsx:51` | `core/domain/ReaderTint.kt`, `navigation/ReaderRoute.kt` | `user_state` | **Livré** | la route observe l'état du compte et en tire les deux ensembles ; ce qui compte comme marqué — suppression logique du signet, marqueur posé par le professeur — est une règle de `core:domain`, éprouvée là où elle vit. **Écart assumé** : la source se contredit entre ses deux rendus (`MushafPage.tsx` donne le signet gagnant sur la lecture, `coranTest/html.ts` l'inverse) ; le portage suit le premier, et le dit dans `ReaderTint` |

### Un seul gestionnaire de gestes, et une règle unique

Le lecteur n'empile pas `detectTransformGestures`, `detectTapGestures` et
`detectHorizontalDragGestures` : ces trois-là se disputent les mêmes événements, et Compose
donne la main au premier qui consomme. Il en sort des gestes qui marchent « sauf quand » — un
balayage qui zoome, un appui long qui tourne la page. `ReaderGestures` traduit donc **tous** les
événements en intentions, au même endroit :

| Ce que fait le doigt | Ce qui se passe |
|---|---|
| deux doigts | zoom et déplacement, jamais de changement de page |
| un doigt, page agrandie | la page se déplace, elle ne tourne pas |
| un doigt, page entière, mouvement court | appui : la coquille s'affiche ou disparaît |
| un doigt, page entière, appui long (450 ms) | fiche du verset sous le doigt |
| un doigt, page entière, glissement franc | page suivante ou précédente |

Un pincement **neutralise le reste du geste** jusqu'au lever : après avoir zoomé, le doigt qui
reste ne doit ni faire défiler la page ni la tourner.

La **décision** n'est pas dans l'écran : `ReaderGesture.dragIntent` dit l'intention,
`PageNavigation` dit le numéro. Un test vérifie que les deux **s'accordent** — et il a servi :
la première version de `dragIntent` inversait le sens par rapport à `pageAfterSwipe`, qui est
le portage fidèle du client d'origine et qui était déjà éprouvé. Sans ce test, l'intention
aurait annoncé « suivante » pendant que le numéro calculé disait « précédente ».

**Données déjà importées :** 11 fichiers JSON (8,7 Mo) dans `core/domain/src/main/resources/quran/`
et **les 604 pages du moushaf** (118,2 Mo, 195,7 Ko en moyenne) dans `app/src/main/assets/quran/pages/`,
copiées par `tools/import-quran-assets.mjs --all-pages`.

> **Les poids de ce document sont mesurés, et « Mo » y vaut 10⁶ octets.** Les chiffres annoncés
> plus tôt — « 114 Mo » pour les pages, « 12 fichiers JSON », « 134 Mo » pour le Tajweed — ne
> correspondaient à aucune mesure : la somme réelle est **118 203 707 octets** pour les 604 pages,
> **8 725 535** pour le dossier de données, et **138 546 362** pour le Tajweed.
> `tools/verifier-parite-donnees.py` les recalcule à chaque exécution plutôt que de les recopier.
> En unités binaires — celles que montre l'Explorateur Windows — cela fait 112,7 Mio, 8,3 Mio
> et 132,1 Mio.

Le client React Native **versionne** ces 604 pages et **télécharge** l'archive 1441 : la même
règle est suivie ici. Les 604 pages du Tajweed (138,6 Mo) ne sont pas importées — elles
s'ajoutent avec `--with-tajweed` le jour où la source « Tawjeed » sera ouverte.

### Les quatre sources de lecture, telles que la source les définit

`src/services/quranSourceReady.ts` est le point qui décide, et il distingue quatre cas — ils
n'ont ni le même support, ni le même coût :

| Source | Support réel | Où vivent les images | Coût |
|---|---|---|---|
| **Coran de Médine** (défaut) | 604 PNG, une par page | `assets/mushaf/pageXXX.png`, **versionnées** | 118,2 Mo dans le dépôt |
| **Coran 1441** (`coran_1441`) | 9 060 PNG, **15 lignes par page** | `https://files.quran.app/hafs/madani_1441/zips/images_1440.zip`, **téléchargées** | archive de **102 608 011 octets** |
| **Tajweed** (`tajweedPages`) | 604 PNG, une par page | `assets/mushaf-tajweed/`, non importées | 138,5 Mo, non repris |
| **Coran Test** (`coranTest`) | 607 polices `.woff2` : 604 (une par page) et 3 nommées | `assets/coran-test/`, **versionnées** — les 604 et les 2 nommées que l'original consomme | 48,85 Mo ; la troisième nommée, qu'**aucun** module de l'original n'importe, est **écartée** (105 520 octets, soit l'écart exact entre les deux dossiers). Rendu par police, pas par image |

Trois conséquences qui décident de l'architecture du lecteur :

1. **La source 1441 n'est pas une page, c'est une ligne.** Le lecteur ne peut pas se contenter
   d'afficher une image : il doit assembler 15 lignes, et les bornes de versets
   (`coran_1441-bounds.json`, déjà importé) donnent la position de chacune. Le nom du fichier est
   `page-ligne`, `001-01.png`, avec page sur 3 chiffres et ligne sur 2.
2. **L'archive 1441 est vérifiée par sa taille exacte** (`zip.size === 102608011`) et par un
   fichier témoin qui doit annoncer **9 060 fichiers** — soit 604 × 15. Un téléchargement
   tronqué est donc détecté, et il est **reprisable** : l'état est publié
   (`idle` → `downloading` → `extracting` → `ready`, ou `paused`, ou `error`).
3. **`coranTest` ne se rend pas comme les autres.** C'est une police, pas une image : le même
   écran doit savoir peindre du texte coranique aussi bien qu'une page. C'est la raison pour
   laquelle `feature/reader` ne peut pas être un simple `ImageView` zoomable — et c'est un cas
   plus fort que prévu : ce n'est pas le portage qui peint la page, c'est un **document**
   embarqué qui la peint, la mesure, et renvoie ses mesures. D'où une `WebView`, et d'où des
   polices embarquées que le document s'inline lui-même en base64.

Ce que le portage Android change, et pourquoi :

- **La reprise ne tient pas de fichier annexe.** Le client d'origine écrit un `resume.json` parce
  que son gestionnaire de fichiers garde les octets repris ailleurs que dans le fichier cible.
  Ici la **longueur du partiel est la position de reprise** : rien ne peut se désynchroniser.
- **Une réponse `200` jette le partiel, une `206` s'y ajoute.** Écrire la suite d'un partiel à
  partir d'un flux qui repart de zéro donnerait une archive de **taille correcte et de contenu
  faux** : elle passerait tous les contrôles de taille et ne se verrait qu'à l'écran, sur une page
  décalée. C'est le pire défaut possible ici, et il a son test dédié.
- **La taille annoncée par le serveur n'est pas lue.** En réponse `206`, un `Content-Length` vaut
  la longueur du **fragment** : la comparer à la taille de l'archive refuserait chaque reprise,
  c'est-à-dire exactement le cas qu'on veut rendre possible. Le juge est la longueur du fichier
  écrit, et elle seule.
- **Le plafond de 2 Mio par image est appliqué en lisant**, pas en regardant la taille annoncée
  par l'entrée ZIP : une archive peut annoncer 1 ko et en fournir 200 Mo, et lire d'abord pour
  vérifier ensuite ne protège de rien.
- **Une seule installation à la fois.** Deux écritures concurrentes dans le même fichier
  produiraient une archive que rien ne signalerait comme fausse avant l'affichage.
- **La règle d'archive est séparée du réseau.** `QuranArchive` ne connaît ni fichier ni socket, et
  `ArchiveTransport` est une interface : l'installation s'éprouve sans réseau, sans serveur et
  sans fabriquer 102 Mo — la taille attendue est injectée, et c'est la même valeur qui règle la
  reprise, l'avancement et le contrôle final.

### Deux référentiels, deux espaces — mesuré, pas supposé

Les deux fichiers de bornes de versets ne se lisent pas de la même façon, et le croire coûte un
surlignage décalé que rien ne signale. Mesuré sur les fichiers livrés :

| | `bounds.json` (Médine) | `coran_1441-bounds.json` (paquet) |
|---|---|---|
| Espace des coordonnées | 1920 × 3106 | 1440 × 2320 |
| Bornes observées | x 25…1887, y 42…3068 | x 0…1440, y 0…2320 |
| **Index de ligne** | **1 à 15** | **0 à 14** |
| Lignes de versets | 13 766 | 13 273 |
| Format d'une ligne | entiers | décimaux (`352.08`) |

Les deux espaces sont **ceux de leur propre page** : diviser un rectangle par la largeur de la
page affichée est donc juste pour l'un comme pour l'autre, et c'est ce que fait
`MushafPageView`. Ce qui ne se transpose pas, c'est l'**index de ligne** — 1-based d'un côté,
0-based de l'autre. Aucun code ne s'en sert pour positionner, et c'est délibéré : la position
verticale vient de `y1`/`y2`, et la position de la **bande** vient du rang de l'image, pas de
l'index du référentiel.

Le même contrôle a montré que le **maximum de l'index n'est pas le nombre de lignes** : la page 1
a pour maximum 11 et ne porte que **sept** lignes distinctes ; une page complète a pour maximum
14 pour **quinze** bandes. Deux pages seulement (la 1 et la 2) ont un maximum de 11, les 602
autres de 14. C'est ce qui a fait renommer `lineCount` — qui rendait le maximum en promettant le
nombre — en `lastLineIndex`, avec `null` pour une page sans ligne : zéro est un index valide.

Enfin, les coordonnées du paquet **confirment la géométrie d'affichage au pixel** : la bande 0
occupe `y 0…232`, la bande 3 `y 447,43…679,43`, la bande 11 `y 1640,57…1872,57`. Le pas mesuré
est `(2320 − 232) / 14 = 149,142857`, soit exactement la formule de `MushafPageGeometry`. Les
bandes se **recouvrent** — quinze bandes de 232 pixels en couvrent 3 480 sur une page qui en
mesure 2 320 — et c'est la disposition d'origine, pas une erreur d'assemblage.

---

## 3. Audio

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Catalogue des récitants | `src/core/audio.ts` (7 récitants) | `core/domain/Texts.kt`, `core/domain/Audio.kt` | `user_state` | **Porté** | défaut = `ar.shaatree`, comme l'original |
| URL audio | `verseAudioUrl`, deux familles (CDN et everyayah) | `core/domain/Audio.kt` | — | **Porté** | |
| Position audio | `nextAudioPosition`, `continuousAudioPosition` | `core/domain/Audio.kt` | — | **Porté** | |
| Chronologie de chapitre | `parseChapterAudio`, `src/services/quranAudioTimeline.ts` | `core/domain/Audio.kt` | — | **Porté** | les horodatages sont en **millisecondes**, puis divisés par 1000 — piège vérifié par test |
| Pause entre versets | `DEFAULT_AYAH_GAP_MS = 200` | `core/domain/Texts.kt` | — | **Porté** | |
| Cache audio par verset | `src/services/verseAudioCache.ts` | `core/data/local/ResourceFiles.kt` | — | **Écrite** | |
| Lecture audio | `src/PassageAudioPlayer.tsx` | `core/audio` — `ExoAudioOutput`, `AudioSessionController` | — | **Livré** | un seul lecteur, focus audio délégué à `ExoPlayer` ; l'écoute s'arrête en quittant le lecteur tant qu'aucun service d'avant-plan n'existe (phase D) |
| Mini-lecteur | intégré au lecteur | `feature/reader/MiniPlayer.kt` | — | **Livré** | **dans le flux** : il prend sa hauteur au lieu de recouvrir le dernier verset |
| Silence entre deux écoutes | `settingsRef.current.gap * 1000`, plancher de 200 ms | `core/domain/AudioQueue.kt` | — | **Livré** | le silence choisi ne s'applique **qu'aux reprises** ; entre deux versets voisins, seule la marge technique joue. C'est le plus grand des deux, jamais leur somme — trois mutations le prouvent |
| Répétition 1x/2x/3x/5x, plage X→Y | `audio-repeat-preferences` (local) | `core/domain/AudioSession.kt` | — | **Livré** | 1/2/3/5/10, « Autre » (1 à 999), « ∞ » ; vitesse 0,75×/1×/1,25×. Un champ enregistré hors bornes est ignoré **sans emporter ses voisins** |
| Écran des réglages d'écoute | `PassageAudioPlayer.tsx`, panneau avancé | `feature/reader/AudioSettingsSheet.kt`, `feature/reader/SheetChrome.kt`, `core/domain/AudioSettingsText.kt`, `core/data/AudioSettingsRepository.kt` | — | **Livré**, réglages **enregistrés** | récitateur, répétitions (1/2/3/5/10/Autre/∞), mode passage ou verset, vitesse, silence, arrêt automatique. Chaque appui **s'applique à la séance en cours**, comme dans l'original. L'écran refuse une saisie libre hors bornes avec le message d'origine ; le moteur, lui, la ramène à 1 plutôt que de faire échouer la lecture. Les réglages sont **relus au démarrage** et **écrits à chaque changement** dans un document unique, comme les deux clés d'`AsyncStorage` de l'original — réunies, elles, en un seul fichier. **Écart assumé** : le récitateur, lui, **est synchronisé** avec le compte, comme dans l'original — la clé locale y est d'ailleurs **par utilisateur** (`audio-reciter-hafs:<utilisateur>`) et non globale, la valeur du compte prime sur celle de l'appareil **même quand elle est inconnue**, et le report vers le compte n'a lieu qu'**une fois**, quand le compte n'a rien choisi. Les répétitions, la vitesse et le silence, eux, restent propres à l'appareil |
| Enregistrement de récitation | `src/RecitationRecorder.tsx`, `src/services/recitations.ts` | — | `recitations`, `recitation_corrections`, `recitation_feedback` | **À faire** | phase D |

---

## 4. Apprentissage

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Objectif (sourates, hizb, juz) | `goalFromPreset`, `goalIds` | `core/domain/Program.kt` | `user_state` | **Porté** | le « volume » d'un objectif est un **nombre de lettres**, pas un nombre de versets |
| Ordre d'apprentissage | `learningOrderIds` | `core/domain/Program.kt` | — | **Porté** | par défaut croissant (depuis An-Nâs) ; `fromNas` seulement si un objectif le demande |
| Génération du programme | `generateProgram` | `core/domain/Program.kt` | `user_state` | **Porté** | |
| Séance du jour | `nextChunk`, `splitContiguous` | `core/domain/Program.kt` | — | **Porté** | |
| Validation d'une séance | `completeSession` | `core/domain/Program.kt` | `user_state` | **Porté** | |
| Report d'une séance | `postponeSession` | `core/domain/Program.kt` | — | **Porté** | |
| **Dates planifiées vs réalisées** | `scheduledDate`, `completedAt` | `core/model/AppState.kt` (`Session`) | `user_state` | **Porté** | séance prévue mardi, faite lundi : `scheduledDate` reste mardi, `completedAt` devient lundi ; la séance suivante ne prend **jamais** automatiquement la date du jour |
| Progression par unité d'étude | `src/core/studyProgress.ts` | `core/domain/StudyProgressCalculator.kt` | `user_state` | **Porté** | |
| Connaissances | `markKnowledge`, `toggleKnownRange` | `core/domain/Program.kt` | — | **Porté** | |
| Statistiques | `stats`, `completedHizbs` | `core/domain/Program.kt` | — | **Porté** | |
| **Requête de séance** (`Reader`) | `src/App.tsx:66` | `core/domain/StudySession.kt` (`StudySession.Request`) | — | **Porté** | le type `Reader` de l'original — `range`, `sessionId`, `revisionId`, `reviewTask`, `consolidation` — est porté tel quel, et ses trois lectures dérivées le sont aussi : `learning` (une séance d'apprentissage), `reviewing` (une révision, une consolidation, ou une tâche de révision) et `focused` (l'une ou l'autre). C'est lui qui décide de ce que le lecteur sert : un bandeau, un geste, une écriture. **Écart assumé** : `initialLanguage` n'est pas porté — le lecteur de ce client n'a pas de bascule de langue de traduction, et un champ qui ne changerait rien serait un mensonge |
| Écran de séance | `src/ui/StudySession.tsx`, `src/ui/QuranSessionHeader.tsx` | `core/domain/StudySession.kt`, `feature/reader/StudyChrome.kt`, `feature/reader/StudyCompletionSheet.kt` | `user_state` | **Livré** (côté lecteur) | le **bandeau** de séance et la **feuille de validation** sont portés : titre (`Consolidation · J+n`, `Apprentissage du jour`, `Révision du jour`), ligne de progression **absente** pour une consolidation, page ou plage selon la source affichée, et point d'arrêt proposé à la fin de la **page affichée**. Le geste central de l'original — achever une séance et **écrire** la progression — vit désormais dans le lecteur, là où la personne lit, et l'accueil ouvre la séance du jour. L'écran **autonome** de `feature:program` est **livré** — voir la ligne « Écran de programme » —, le **tableau de bord des révisions** reste à faire (phase C), et les **repères de marge** sont rendus dans les **deux** lecteurs : le document immersif les peint dans son propre script, et le lecteur standard les peint en Compose à partir de `MarginAnnotations` — une seule règle, appelée d'un côté et recopiée de l'autre. **Écart assumé** : les listes de choix de la feuille sont **défilables dans la feuille**, là où l'original ouvre une liste superposée — un seul geste au lieu de deux, et la même information |
| Écran de programme | `src/ui/MainScreens.tsx:35` (`ProgramScreen`), `src/ui/StudySession.tsx:29` (`StudyResumeCard`) | `feature/program/ProgramRenderer.kt` + `ProgramScreen.kt` | — | **Porté** | la séance du jour, la carte « Mon objectif », les reprises, le rattrapage, les séances à venir et l'historique. Le calcul est **pur** et séparé du `ViewModel` — une fonction de `(état, jour, période)` —, donc éprouvable sans coroutine, sans horloge et sans appareil. Trois règles portent à conséquence : la séance du jour **retombe** sur la première à venir, mais une séance **en retard** n'est pas « à venir » — elle va au rattrapage ; la période « Jour » prend pour référence la date de la **première séance à venir**, et non celle du jour ; la tâche de révision est transmise au lecteur **avec sa catégorie**, qui décide de la consolidation — sans elle, la carte ouvrirait une lecture libre et la révision faite ne serait enregistrée nulle part. Cinq mesures relevées à la source : la bande d'en-tête **déborde** la marge des autres blocs, le sélecteur de période occupe **65 %** de la ligne contre 35 % pour « À venir », la pastille de date fait **42 px** de large pour une hauteur libre, deux cartes **surchargent leur marge interne** (4 px pour la liste « À venir », 12 px pour le bandeau de la semaine), et le nom arabe est **borné** à 21 % de la ligne au lieu d'être large d'une valeur fixe. La **reprise** ouvre le **reste** de la séance, jamais la séance entière. **Écart assumé** : les deux couples de replis sont conservés tels quels — « Aucune séance » / « Objectif atteint » et « Aucun passage dû » / « À jour » —, parce que deux absences différentes ne se peignent pas pareil et que c'est la source qui le dit. `onOpenReviews` n'est pas encore branché : la route du tableau de bord est nommée, mais aucun écran ne la sert |
| Écran d'objectif | `src/ui/GoalScreen.tsx` | `feature:program` | — | **À faire** | phase C |

---

## 5. Révisions et consolidations

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Cycles 7 / 14 / 21 / 30 jours | `setReviewCycle` | `core/domain/Review.kt` | `user_state` | **Porté** | changer pour la durée déjà en place est sans effet : aucun cycle n'est créé |
| Quantités (1 nisf, 1 hizb, 1 juz, 2 juz) | `setReviewQuantity`, `partitionReviewCorpus` | `core/domain/Review.kt` | — | **Porté** | le découpage ne perd ni ne duplique aucun verset (test) |
| Consolidations J+1, J+3, J+7 | `consolidationFor`, `prepareReviewSchedule` | `core/domain/Review.kt` | `user_state` | **Porté** | échéances **ancrées** sur la date d'apprentissage, pas sur celle de la consultation |
| Versets difficiles | `toggleDifficulty` | `core/domain/Review.kt` | `user_state` | **Porté** | le marqueur posé par le professeur survit au retrait par l'utilisateur |
| Notation d'une révision | `gradeReviewTask` | `core/domain/Review.kt` | — | **Porté** | |
| Historique | `reviewCycleHistory`, `consolidationHistory` | `core/model/AppState.kt` | `user_state` | **Porté** | jamais raccourci : la fusion les réunit |
| Tableau de bord des révisions | `src/ReviewDashboard.tsx` | `feature:progress` | — | **À faire** | phase C |
| Barre d'action de révision | `src/ui/RevisionBottomActionBar.tsx` | `feature:progress` | — | **À faire** | phase C |

**Point de conception à ne pas perdre :** un passage à consolider doit être **cliquable** et
ouvrir la bonne page, la bonne sourate et le bon passage, puis proposer « J'ai consolidé ».
C'est la différence entre une liste de rappels et un outil de mémorisation.

---

## 6. Objectif hebdomadaire

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Semaine du lundi 00:01 au dimanche 23:59 | `weeklyProgress` | `core/domain/WeeklyProgress.kt` | — | **Porté** | un dimanche appartient à la semaine du lundi précédent (test) |
| Nouvelle semaine à 0 % | `weeklyProgress` | `core/domain/WeeklyProgress.kt` | — | **Porté** | l'affichage repart de zéro **sans effacer l'historique** |
| Programme à venir | `upcomingSessions` | `core/domain/WeeklyProgress.kt` | — | **Porté** | **10 jours** seulement, et rien n'est supprimé au-delà |
| Statut d'une séance | `sessionStatus` | `core/domain/WeeklyProgress.kt` | — | **Porté** | distingue fait, reporté, partiel, en attente |

---

## 7. Signets et lecture

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Signets | `src/core/bookmarks.ts` | `core/domain/Bookmarks.kt` | `user_state` | **Porté** | suppression **douce** (`deletedAt`) : un signet supprimé sur un appareil ne ressuscite pas depuis l'autre ; l'écran a ses règles ici aussi — page **dans la source affichée** (clé de lecture : le `@SerialName` de la source, celui-là même que la source écrit), « Dernière reprise » **retriée** et non lue en tête de liste, signet hors corpus **omis** au lieu de faire tomber l'écran. **Écart assumé** : le nom de sourate et le numéro de verset sont lus sur le **verset**, là où `BookmarksScreen.tsx` affiche les champs enregistrés du signet — les deux ne peuvent diverger que pour un état venu d'ailleurs, et dans ce cas c'est le référentiel qui a raison |
| Dernière lecture | `closeReader` d'`App.tsx:212` | `core/domain/ReaderMemory.kt` | `user_state` | **Livré** | la fermeture du lecteur est portée, et elle écrit les trois choses de l'original : `lastRead` — la page quittée et un verset **choisi**, celui déjà noté s'il tient encore sur la page, sinon celui de l'ouverture, sinon le premier de la page —, la page ajoutée à `readPages`, et `reader.testPage` pour la source composée **seule**. Le lecteur **rouvre** à la page mémorisée au lieu de repartir de la page 1, et l'adoption cesse dès qu'une page est tournée. La sortie par le bouton et par le retour système passent par la même écriture, et l'écriture **précède** la fermeture : la portée de la route meurt avec l'écran, donc fermer d'abord annulerait l'écriture en vol, et l'écran se fermerait normalement — un défaut sans symptôme. **Écart assumé** : l'original relit `lastRead.page` telle quelle pour toutes les sources ; le portage **reprojette** le dernier verset lu dans le découpage de la source affichée, parce que la page enregistrée a été écrite dans celui d'une autre — 36 pages sur 604 divergent entre les deux tables |
| Mots de l'écran des signets | `src/BookmarksScreen.tsx`, `App.tsx:503-512` | `core/domain/BookmarksText.kt` | — | **Porté** | titre, sous-titre, carte d'explication, état vide, « Dernière reprise », « Reprendre », et les deux entrées du panneau (« Placer un marque-page sur un verset », « Mes marques-pages ») — repris caractère pour caractère, guillemets et trait d'union compris |
| Panneau « Marques-pages » et mode de pose | `App.tsx:503`, `App.tsx:485`, `App.tsx:504` | `feature/reader/BookmarksSheet.kt`, `core/domain/BookmarksText.kt`, `core/domain/ReaderTouch.kt` | — | **Livré** | le bouton de la coquille ouvre le **panneau**, qui propose les deux entrées du client d'origine — poser (bouton plein) puis consulter (secondaire) — et c'est la première qui **arme** le mode de pose : ce détour est ce qui empêche un appui malencontreux de basculer le lecteur. La notice du mode de pose s'affiche **au-dessus de la page** et non dans la coquille — elle décrit un geste à faire sur la page, et une coquille masquée l'emporterait avec elle. Le verset touché est converti par `ReaderTouch`, la **même** règle que l'appui long : dé-zoom, dé-centrage, puis passage à l'espace de la source. **Écart assumé** : le bouton de fermeture est décrit comme « Fermer les marques-pages », là où l'original écrit « Fermer le panneau » pour ses cinq panneaux — le portage a une feuille par panneau, et chacune nomme ce qu'elle ferme |
| Panneau « Actions du verset » | `App.tsx:510` (`sessionPanel==='verse'`), `PassageAudioPlayer.tsx:228` | `feature/reader/VerseActionsSheet.kt`, `core/domain/VerseActionsText.kt` | `user_state` (le marquage) | **Livré**, sauf une entrée | l'appui long pose le verset **et** ouvre le panneau, comme l'original (`setSelectedVerse(id); setSessionPanel('verse')`) ; la fiche de traduction lui cède alors la place, et réapparaît à la fermeture — le panneau répète le numéro du verset, donc rien n'est perdu. « Écouter ce verset » force `count:1`, `mode:'passage'` et `autoStop:true` sur une plage d'**un seul** verset, puis démarre : les trois valeurs de l'original, et elles sont **écrites**, comme `setCountChoice(1)` le fait là-bas. « Répéter ce verset » met le mode sur « passage » et ouvre les réglages d'écoute **sans lancer** — c'est « Écouter » qui lance. Le marquage, lui, ne referme ni le panneau ni la fiche : c'est ce qui laisse le libellé basculer sous les yeux. **Écart assumé** : le libellé suit le marqueur de l'**élève** seul, là où la teinte de la page compte aussi celui du professeur — la bascule ne touche jamais à ce dernier, et suivre `isDifficult` ferait annoncer un retrait sur un verset dont l'appui **ajoute** un marqueur. « Sélectionner un passage » **n'est pas porté** : le geste de désignation d'une plage n'existe pas ici, et l'entrée disparaît au lieu de mener nulle part |
| Écran des signets | `src/BookmarksScreen.tsx` | `feature/reader/BookmarksScreen.kt` | — | **Livré** | en-tête avec retour (« Retour à la lecture »), titre et sous-titre, carte d'explication **sur fond doux**, état vide, puis une carte par signet : nom de sourate, « Page *n* · Verset *n* », texte arabe aligné à droite, « Dernière reprise » sur le signet le plus récemment repris, et « Reprendre ». La suppression passe par une confirmation, et elle est **logique** — le message le dit d'ailleurs : « Le verset restera disponible dans le Coran. » Reprendre marque le signet comme repris, ramène à la **page de la source affichée** et rouvre la fiche du verset. **Écart assumé** : le client d'origine **remplace** le lecteur par cet écran ; le portage le pose en **fenêtre** par-dessus, comme le sélecteur de sourate — remplacer le lecteur le **démonterait**, ce qui libérerait le lecteur audio et **arrêterait l'écoute** au moment précis où l'on consulte ses marques-pages. La reprise referme aussi le panneau qu'on avait quitté, comme le `setSessionPanel(null)` d'`App.tsx`. Deux tailles en dur du source — 18 pour le nom de sourate, 23 pour le texte coranique — sont ramenées au jeton le plus proche (`card`, `arabic`), la règle déjà suivie par le sélecteur de sourate |

---

## 8. Quiz

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Question du jour | `src/core/quiz.ts`, `quizDay` | `core/domain/Quiz.kt` | `quiz_questions`, `quiz_daily_responses` | **Porté** | une seule participation par question et par jour |
| Catégories | `quizCategories` | `core/domain/Texts.kt` | — | **Porté** | |
| Défis entre amis | `challengeStatus` | `core/domain/Quiz.kt` | `quiz_challenges`, `quiz_challenge_questions`, `quiz_challenge_answers` | **Porté** | 5 ou 10 questions, **10 par défaut** ; les deux joueurs reçoivent les mêmes questions |
| Statistiques | `quizStatistics` | `core/domain/Quiz.kt` | — | **Porté** | seules les réponses confirmées comptent |
| Fusion d'instantané | `mergeQuizSnapshot` | `core/domain/Quiz.kt` | — | **Porté** | une réponse en attente d'envoi n'est pas effacée par un instantané serveur qui l'ignore encore |
| Écran de quiz | `src/ui/QuizScreen.tsx`, `src/services/quiz.ts` | `feature:quiz` | — | **À faire** | phase D |
| Écrans d'administration du quiz | `src/ui/AdminQuiz.tsx`, `AdminQuizSets.tsx` | — | `quiz_sets` | **Hors périmètre** | l'administration reste sur le web |

---

## 9. Amis et messagerie

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Profils d'amis | `src/services/social.ts` | `feature:social` | `friend_profiles` | **À faire** | phase D |
| Demandes d'amis | `friend_links` | `feature:social` | `friend_links` | **À faire** | phase D |
| Avatars | `src/services/avatars.ts` | `feature:social` | `friend_profiles` | **À faire** | phase D |
| Messagerie | `src/ui/MessagingButton.tsx`, `SocialScreens.tsx` | `feature:social` | `friend_messages`, `friend_message_reads`, `friend_message_hidden`, `friend_message_reports` | **À faire** | phase D |
| Groupes | — | `feature:social` | `friend_groups`, `friend_group_members` | **À faire** | phase D |
| Partage de progression | `publishSocialProgress` | `feature:social` | `friend_progress`, `friend_shared_goals` | **À faire** | phase D |
| Rendez-vous de révision entre amis | — | `feature:social` | `friend_review_appointments` | **À faire** | phase D |
| Temps réel | `src/services/social.ts` (Realtime) | `core/data` (Realtime installé) | — | **À faire** | phase D — le module Realtime est déjà branché sur le client |

---

## 10. Notifications

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut | Points d'attention |
|---|---|---|---|---|---|
| Préférences | `NotificationPreferences` | `core/model/AppState.kt` | `user_state` | **Porté** | messages, demandes d'amis, progression partagée, corrections, messages d'administration, aperçu des messages |
| Appareils d'envoi | `src/services/notifications.ts` | — | `push_devices` | **À faire** | phase D — l'association `user_id` doit être conservée |
| Rappel d'apprentissage à 19:00 | `syncLearningReminder` | — | — | **À faire** | phase D |
| Canaux | — | — | — | **À faire** | messages, apprentissage, corrections, administration |
| Messages d'administration | `src/services/adminNotifications.ts` | — | `admin_notifications` | **À faire** | phase D |
| Corrections de récitation | `notification-corrections.sql` | — | `recitation_corrections` | **À faire** | phase D |
| Envoi (FCM) | `src/services/notifications.ts` | — | `push_devices` | **À faire** | nécessite `google-services.json` — **intervention requise** |

---

## 11. Contenus, récitations, signalements

| Fonctionnalité RN | Source RN | Équivalent Android | Tables | Statut |
|---|---|---|---|---|
| Contenus quotidiens | `src/DailyContentsScreen.tsx`, `src/services/dailyContents.ts` | `feature:home` | `daily_contents`, `daily_content_schedule`, `content_categories`, `content_favorites` | **À faire** (phase D) |
| Médias de contenus | `src/services/dailyContentMedia.ts` | — | `daily_contents` | **À faire** (phase D) |
| Récitations partagées | `src/RecitationsScreen.tsx`, `src/services/recitations.ts` | — | `recitations` | **À faire** (phase D) |
| Signalement de problème | `src/ui/ProblemReport.tsx`, `src/services/problemReports.ts` | — | `app_problem_reports` | **À faire** (phase E) |

---

## 12. Écrans d'administration

| Fonctionnalité RN | Source RN | Équivalent Android | Statut |
|---|---|---|---|
| Comptes d'administration | `src/AdminAccounts.tsx`, `src/services/adminAccounts.ts` | — | **Hors périmètre** |
| Notifications d'administration | `src/AdminNotifications.tsx` | — | **Hors périmètre** |
| Contenus quotidiens | `src/AdminDailyContents.tsx` | — | **Hors périmètre** |
| Récitations et enregistrement vocal | `src/AdminRecitations.tsx`, `src/AdminVoiceRecorder.tsx` | — | **Hors périmètre** |
| Signalements | `src/ui/AdminProblemReports.tsx` | — | **Hors périmètre** |

Ces écrans s'adressent à un usage de bureau. Les reproduire sur téléphone ajouterait de la
surface à maintenir pour un usage marginal. Le client React Native et l'administration web
restent les outils d'administration.

---

## 13. Design

| Élément RN | Source RN | Équivalent Android | Statut | Points d'attention |
|---|---|---|---|---|
| Jetons de design | `src/theme/tokens.ts` | `core/design/theme/Tokens.kt` | **Porté** | espacements, rayons, échelle typographique, ombre de carte et accents repris **à l'identique** ; `button = 15` remonté depuis le composant `Button`, où il était en dur |
| Palettes | `src/ui/theme.tsx` (`palettes`) | `core/design/theme/Palettes.kt` | **Porté** | les 5 palettes × 18 clés et les 6 couleurs communes sont **mesurées** identiques au source par `tools/verifier-jetons-design.py` (11 contrôles) |
| Règle d'accent | `applyTheme(theme, accent)` | `resolveColors(theme, accent)` | **Porté** | règle **asymétrique** : un accent explicite écrase la palette, un accent absent ne l'écrase que pour le thème blanc. Un test dédié protège cette dissymétrie |
| Écran Apparence | `themeOptions` | `appThemeOptions` | **Porté** | 5 thèmes avec libellés accentués (« Thème blanc », « Bleu Nuit & Or ») et 4 pastilles chacun |
| Polices | `src/theme/fonts.ts` | `core/design/theme/Fonts.kt` | **Porté** | Cormorant Garamond variable (axe `wght` **300..700**, défaut **300** → chaque graisse est demandée explicitement) + Amiri statique ; licences OFL embarquées dans les `assets` |
| Thème Compose | `useTheme()` | `DeepSeekTheme` + `AppTheme.colors` | **Porté** | palette immuable fournie par l'environnement au lieu d'un objet global muté ; `ColorScheme` Material 3 dérivé pour que les composants Material se fondent dans l'écran |
| Composants de base | `Label`, `Title`, `Card`, `Button`, `Choice`, `CheckChoice`, `Field` | `AppLabel`, `AppTitle`, `AppCard`, `AppButton`, `AppChoice`, `AppCheckChoice`, `AppField` | **Porté** | `Button` redessiné (géométrie exacte) ; `Choice` / `CheckChoice` / `Field` passent aux composants Material natifs pour l'accessibilité et les cibles tactiles |
| Zone tactile minimale | — (implicite) | `Modifier.minimumTouchTarget()` | **Porté** | 48 px garantis autour d'un petit pictogramme, exigence explicite du cahier des charges |
| Système de composants | `src/ui/DesignSystem.tsx`, `src/ui/Premium.tsx` | `core/design/component/` | **Partiel** | porté : `Heading`, `SectionHeader`, `IconButton`, `DailyTaskCard`, `StatCard`, `ArabicLabel`, `ProfileHeaderButton`, `ProgressTrack`, `ProgressRing`, `IslamicHero` (bande d'en-tête — dont l'opacité de fond **dépend du thème**, `white ? 0.68 : 0.28`, une asymétrie que rien ne laisse deviner) et `SegmentedControl` (`selectableGroup` + `Role.Tab`, sans quoi TalkBack annoncerait trois boutons ordinaires là où l'écran présente un choix exclusif). Restent `QuranNumberMedallion`, `ThemeSelector`, `AccentSelector` : ils seront portés avec les écrans qui les utilisent, pour ne pas écrire de code sans appelant |
| Navigation basse | `src/ui/Premium.tsx` (`BottomNavigation`) | `navigation/AppBottomBar.kt` | **Porté** | 5 onglets : Accueil, Coran, Programme, Progrès, Amis — **aucun autre**. Padding 5/3, hauteur 56, icône 23, libellé 10, pastille active 4 px, trait haut 1 px **à l'intérieur** du composant. La barre absorbe elle-même l'encoche de navigation gestuelle |
| Navigation haute | `src/ui/Premium.tsx` (`AppTopNavigation`) | `navigation/AppTopBar.kt` | **Porté** | deux formes : « onglet » (livre doré, titre, profil, réglages, rangée d'onglets) et « outil » (retour, titre centré). Deux mesures relevées à la source : `titleFont()` renvoie la famille **600 SemiBold**, donc les deux formes rendent la même graisse ; les `fontWeight` 500/700 des onglets sont **sans effet** (une seule graisse enregistrée) — c'est le rendu qui est reproduit, pas l'intention |
| Double rangée d'onglets | `AppTopNavigation` + `BottomNavigation` | idem | **Porté tel quel** | le dépôt d'origine affiche **les cinq mêmes destinations deux fois**, en haut et en bas. Ce n'est pas une erreur de portage : c'est le comportement actuel. Signalé ici pour qu'une simplification reste une décision et non un oubli |
| Coquille et règles de visibilité | `App.tsx` (sept booléens : `reader`, `quizOpen`, `utilityView`, `reviewOpen`…) | `navigation/AppScaffold.kt` + `AppRoutes` | **Porté** | les barres visibles se **déduisent** de la route : onglet → les deux, écran d'outil → haute avec retour seulement, plein écran → aucune. Un écran d'outil garde son en-tête, sinon il n'aurait plus de retour |
| Écran d'accueil | `src/ui/MainScreens.tsx` (`Home`, `activity`, `TinyWeek`) | `feature/home/HomeRenderer.kt` + `HomeScreen.kt` | **Porté** | bande d'en-tête, « Continuer ma lecture », les deux tâches du jour, « Ma semaine ». Le calcul est **pur** et séparé du `ViewModel` : 23 tests, dont deux sur `scheduledDate` et un sur la période de chaque bandeau. Le **compte des versets** est délégué à `core:domain` (`ProgramText`) : l'accueil et le programme écrivent le même compte, et deux copies finiraient par diverger d'un mot |
| Cartes de quiz, contenus du jour, messages non lus, signalement | `QuizHomeCards`, `TodayContents`, `ProblemReportCard` | — | **À faire** (phase D) | absents de l'accueil et **non remplacés** par un équivalent local : un quiz hors ligne n'aurait pas de question du jour à poser. L'emplacement est nommé dans `HomeViewModel` |
| Thèmes (blanc, classique, féminin, lilas, nuit) | `AppTheme` | `core/model/Enums.kt` | **Porté** (les valeurs) | l'esprit visuel est conservé, adapté aux usages Android |

**Le principe retenu :** garder l'identité visuelle — blanc et crème, vert, touches d'or, thèmes
existants — et **adopter les comportements Android** plutôt que de copier l'aspect iOS. Un
retour système, une zone tactile Material, un bouton de retour de la barre d'état : ce sont des
attentes des utilisateurs Android, et les contredire coûte plus cher que la fidélité au pixel.

---

## 14. Ce qui est hors périmètre et pourquoi

| Élément | Raison |
|---|---|
| Écrans d'administration (5 écrans) | usage de bureau |
| Version web | le client React Native la couvre déjà |
| Abonnement / Premium (`src/ui/Premium.tsx`) | **à clarifier** : le fichier porte des composants d'interface partagés autant qu'un écran d'abonnement — la partie abonnement devra être confirmée avant d'être portée ou écartée |

---

## Points d'attention transverses

1. **Les identifiants de versets sont globaux et contigus (1–6236).** Toute la continuité entre
   React Native, iOS et Android repose dessus. Une erreur d'indexation d'un seul verset
   décalerait silencieusement la progression de l'utilisateur. C'est pourquoi le domaine est
   éprouvé sur les vraies données et non sur une maquette.

2. **Le volume d'un objectif est un nombre de lettres**, pas un nombre de versets. Un calcul en
   versets donnerait un programme d'une durée fausse, sans qu'aucun écran ne paraisse en faute.

3. **Les horodatages audio sont en millisecondes.** Le portage les divise par 1000 ; lire la
   source comme des secondes produirait des positions 1000 fois trop grandes.

4. **`scheduledDate` et `completedAt` sont deux choses distinctes.** Les confondre ferait
   disparaître la notion de retard, et avec elle tout le suivi du rythme.

5. **Les journaux ne se raccourcissent jamais.** Validations et historiques sont réunis par la
   fusion, jamais remplacés. Un `merge` naïf qui prendrait « le plus récent » effacerait
   l'historique d'un des deux appareils.

6. **La position du lecteur est enregistrée, et reprojetée plutôt que relue.** La fermeture
   du lecteur écrit les trois choses de l'original (`closeReader`, `App.tsx:212`) : `lastRead`,
   la page ajoutée à `readPages`, et `reader.testPage` pour la source composée seule. Le portage
   s'en écarte sur un point : l'original relit `lastRead.page` telle quelle pour toutes les
   sources, alors que cette page a été écrite dans le découpage de la source qui était affichée.
   Le portage **reprojette** le dernier verset lu — 36 pages sur 604 divergent entre les deux
   tables —, sans quoi ouvrir une source en images après la composition afficherait la page d'un
   autre découpage. Relevé en écrivant l'écran immersif, puis **livré avec la mémoire du
   lecteur**.

7. **La coquille d'étude est portée en entier, et les repères de marge sur deux chemins.** Le
   **bandeau de séance** de `QuranSessionHeader.tsx` et la **feuille de validation** de
   `StudySession.tsx` sont livrés : c'est là qu'une séance s'achève et que la progression
   s'écrit, donc là où le geste compte. Les **repères de marge** de `MarginAnnotations` sont
   désormais rendus **dans les deux lecteurs** : le document immersif les peint dans son propre
   script, où la règle est recopiée parce que le document est une île, et le lecteur standard
   les peint en Compose, en appelant la règle du domaine. L'écran autonome de
   `feature:program` — programme, objectif, tableau de bord — reste à faire (phase C) ; le
   lecteur, lui, n'attend plus la phase C pour servir une séance.

8. **Une séance a trois plages, et les confondre perd les validations en silence.**
   `request.range` est la plage **demandée** — l'adresse par laquelle on entre ; `opening` est la
   plage **ouverte** — le reste d'une reprise interrompue ; `plannedRange` est la plage **prévue**
   — la séance présente dans l'état, ou l'enregistrement d'une révision. La validation **refuse
   d'écrire** quand les bornes enregistrées diffèrent de celles de la plage : une reprise
   partielle qui écrirait ses propres bornes perdrait donc toutes les validations suivantes, et
   le symptôme serait celui d'une progression qui ne monte plus. Les trois sont nommées dans
   `StudySession`, et deux tests échouent si `plannedRange` se remet à confondre avec
   `request.range`. **Corollaire mesuré** : le dernier verset validé vaut `début - 1` et non
   `début` — annoncer `début` ferait commencer la première validation un verset trop tard, et le
   premier verset de la plage ne serait jamais compté comme appris.

9. **Une règle recopiée se tient par un contrôle, des deux côtés.** `MarginAnnotations` existe
   deux fois : en Kotlin dans `core:domain`, et en JavaScript dans le script du document
   immersif — parce que le document est une île, et que rien ne lui donne les rectangles de
   ses propres mots. Les deux copies sont éprouvées séparément (`ReaderTest` pour la Kotlin,
   `TestPageHtmlTest` pour le JavaScript), et le **contrat entre les deux** l'est aussi :
   `TestPageOverlayTest` fixe ce que l'application **envoie**, et `TestPageHtmlTest` fixe ce
   que le document **lit**. Un champ envoyé et jamais lu laisserait croire que quelque chose
   s'en sert — c'est pourquoi `sessionThrough`, que l'original envoie et que son document ne
   lit **nulle part**, n'est pas envoyé. **Corollaire** : le lecteur standard et la vue
   immersive lisent le même état, donc une omission dans l'un des deux chemins ne se verrait
   que si un contrôle la nomme — c'est le rôle de `ReaderScreenMarksTest`.
