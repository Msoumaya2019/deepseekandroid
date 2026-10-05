# Architecture

## Le principe qui commande tout le reste

Trois exigences du cahier des charges se contredisent si on les traite séparément :

1. l'application doit **s'ouvrir sans réseau**, avec toutes ses données ;
2. elle doit **synchroniser** avec le projet Supabase partagé ;
3. elle ne doit **jamais perdre** le travail de l'utilisateur, ni celui fait sur l'autre appareil.

La réponse tient en une phrase : **le domaine est du Kotlin pur, l'état local est la source de
vérité de l'affichage, et le réseau n'est jamais sur le chemin critique.**

Concrètement, cela veut dire que ni `core/model` ni `core/domain` ne connaissent Android,
Supabase, le réseau ou le disque. Ils manipulent des valeurs et des règles. C'est ce qui rend
les 548 tests du domaine exécutables en quelques secondes, sans émulateur, sur les vraies
données du Coran.

---

## Modules

```
:app                    point d'entrée, injection des dépendances, thème Material 3
:navigation             graphe de navigation Compose

:core:model             types persistés, sérialisables (kotlinx.serialization) — JVM pur
:core:domain            règles métier — JVM pur
:core:data              stockage local, chiffrement, Supabase, dépôts (Android)
:core:design            jetons et composants Compose
:core:audio             lecture audio (Media3) et enchaînement des versets (Android)

:feature:home           accueil
:feature:reader         lecteur de moushaf
:feature:sources        téléchargement du paquet « Coran 1441 » et choix de présentation
:feature:program        programme et séances
:feature:progress       progrès, révisions, consolidations
:feature:social         amis et messagerie
:feature:quiz           quiz et défis
:feature:profile        profil et réglages
```

**Pourquoi `core:model` et `core:domain` sont séparés.** `core:model` ne contient que des
formes de données : il est consommé par le domaine, la couche de données et l'interface, et
peut être lu sans comprendre la moindre règle. `core:domain` contient les règles et dépend de
`core:model`. Les séparer évite qu'un écran importe une règle métier en croyant importer un
type.

**Pourquoi `core:data` est un module Android.** Il a besoin du magasin de clés matériel
(`AndroidKeyStore`) et d'un `Context` pour localiser les fichiers. Tout le reste du module
n'en dépend pas : les magasins prennent un `File`, ce qui les rend éprouvables sur la JVM.

**Pourquoi `core:audio` est un module, et pas du code dans le lecteur.** `ExoPlayer` a besoin
d'un `Context`, donc ce module est Android — mais **la décision d'enchaînement n'y est pas**.
Elle est dans `core:domain` (`AudioQueue`), où elle s'éprouve sans appareil. Ce qui reste ici
est le **moment** : attendre le silence, puis demander le verset suivant. Le module ne dépend
pas de `core:data` : il ne se connecte à rien, il lit des URL qu'on lui donne, et c'est ce qui
permet au lecteur de s'ouvrir en avion.

`AudioOutput` est une interface pour une raison précise : `ExoPlayer` ne tourne pas sur la JVM.
Le contrôleur s'éprouve donc contre une doublure, et les règles de silence — les seules qu'on
peut se tromper à écrire — sont mesurées, pas supposées.

**Pourquoi `feature:sources` existe, alors que `feature:reader` porte déjà le Coran.** Le
lecteur a une contrainte écrite dans son propre `build.gradle.kts` : **ni réseau, ni stockage**.
C'est ce qui garantit qu'il s'ouvre en avion. Or télécharger 102 Mo, écrire 9 060 fichiers et
choisir une présentation demande les deux. Mettre cela dans le lecteur aurait défait la
contrainte qui fait sa raison d'être ; le laisser dans `navigation` aurait chargé le graphe de
navigation d'un travail de fond. Le partage est donc : le lecteur **affiche** une page qu'on lui
décrit, `feature:sources` **obtient** ce qu'il faut pour la décrire.

`navigation` porte la **porte** — la décision de montrer le panneau plutôt que la page — et non
le panneau lui-même. Une décision de trois lignes se relit là où elle est prise ; un écran de
téléchargement, non.

---

## Sens des dépendances

```
app ─┬─> navigation ──> feature:* ──> core:design
     │                                 core:audio ──> core:domain ──> core:model
     │                                 core:data  ──> core:domain ──> core:model
     └──────────────────────────────────────────────────────────────┘
```

Une seule direction, jamais de retour. `core:data` expose `core:domain` et `core:model` par
`api(...)` : un dépôt rend un `AppState` et applique des règles, les appelants n'ont donc pas à
redéclarer ces types. `feature:*` ne dépend jamais d'un autre `feature:*` — deux écrans qui
doivent partager quelque chose le font par `core:domain` ou `navigation`, jamais l'un par
l'autre.

---

## Le flux de données

### Ouverture de l'application

```
lire le fichier d'état local ──> émettre sur `UserRepository.state` ──> la porte décide
                                                                          │
      ┌───────────────────────────────────────────────────────────────────┤
      │                                   │                               │
  état pas encore lu                 aucun compte                  compte + état écrit
      │                                   │                               │
  ÉCRAN D'ATTENTE                  ÉCRAN DE BIENVENUE                LA COQUILLE
                                          │                               │
                                          │                               └── (en parallèle)
                                          │                                   détecter le réseau ──> sync()
                                    compte connu, mais
                                    état pas rapatrié
                                          │
                                  ÉCRAN D'ATTENTE ──> lire le serveur ──> la coquille s'ouvre
                                          │
                                          └── échec ──> ÉCRAN DE BIENVENUE, avec la raison
```

`UserRepository.state` est un flux alimenté par le disque. Le premier collecteur reçoit l'état
**immédiatement** : `JsonFileStore.changes` déclenche une lecture avant l'abonnement, donc
jamais de `null` transitoire ni d'écran vide.

**La porte est le seul endroit qui décide de l'affichage**, et elle *enveloppe* la coquille au
lieu d'être appelée depuis elle (`AccountGate` dans `feature:auth`, appelée par `MainActivity`).
Aucun écran de l'application ne peut donc être atteint sans compte, et l'ordre se lit d'un coup
d'œil dans `MainActivity`.

La règle complète tient en trois lignes (`AccountAccess.stage`) :

| état local | témoin de fichier | affichage |
|---|---|---|
| pas encore lu | — | écran d'attente |
| sans compte | — | écran de bienvenue |
| compte | fichier présent | la coquille |
| compte | fichier absent | écran d'attente, puis lecture du serveur |

**Pourquoi le témoin de fichier est indispensable.** Un magasin rend toujours un document : un
compte sans fichier reçoit `defaultState().copy(userId = id)`. Rien, dans la valeur rendue, ne
distingue donc « ce compte n'a jamais été rapatrié » de « ce compte a une progression vide ».
Ouvrir dans le premier cas afficherait un programme vide, et la première séance validée dans ce
programme vide serait ensuite poussée au serveur — écrasant le vrai compte, car `reconcileState`
donne raison au local quand son `updatedAt` est le plus récent. Seul le fichier témoigne, et
c'est pourquoi `UserRepository.hasStoredState` existe.

Le témoin est aussi ce qui rend le garde-fou **durable** : un drapeau gardé en mémoire
disparaîtrait au redémarrage, et la deuxième ouverture aurait lieu sans aucune lecture du
serveur — exactement la séquence dangereuse.

### Une modification de l'utilisateur

```
mutate { … } ──> Program.touch (fait avancer `updatedAt`)
             ──> écriture atomique sur le disque
             ──> estampillage du `userId`
             ──> mise en file (une seule entrée par type et par utilisateur)
```

`Program.touch` n'est pas décoratif : c'est lui qui fait avancer `updatedAt`, sans quoi une
modification faite avec une horloge en retard serait vue comme plus ancienne que le serveur et
écrasée. L'écriture passe par un fichier temporaire suivi d'un renommage atomique : une
coupure en pleine écriture laisse l'ancien contenu intact, jamais un fichier à moitié écrit.

### La synchronisation

```
1. LIRE le serveur           ── échec réseau ──> on s'arrête, RIEN n'est modifié
2. FUSIONNER et écrire local
3. POUSSER si nécessaire     ── échec réseau ──> la base n'avance pas, on réessaiera
4. faire avancer la base, vider la file
```

L'étape 1 avant l'étape 3 est la règle structurante : **on ne pousse jamais sans avoir lu**.
Un envoi à l'aveugle écraserait le travail fait sur l'autre appareil. C'est la faute la plus
coûteuse possible dans ce système, et l'ordre des étapes la rend impossible plutôt que de
compter sur la vigilance.

---

## La fusion : pourquoi deux règles et non une

Le domaine porte deux fusions, écrites pour deux situations différentes. Les enchaîner
naïvement perd des données ; c'est `AccountSync` qui fixe l'ordre.

### `Program.accountState` — l'identité et le premier chargement

C'est la seule règle qui sait qu'un appareil neuf (`cached == null`) doit **adopter** l'état du
serveur au lieu de pousser le sien. L'inverser viderait le compte d'un utilisateur qui
réinstalle l'application : le scénario le plus destructeur possible. C'est elle qui décide de
l'ordre.

### `OfflineMerge.mergeOfflineState` — la fusion à trois voies

Elle n'a de sens qu'avec une **base**, c'est-à-dire un état serveur déjà confirmé. Sa force est
d'être **indépendante de l'horloge** : elle compare le local à la base pour savoir ce que
l'utilisateur a modifié, au lieu de comparer deux horodatages. Elle réunit aussi les journaux
qu'on ne doit jamais raccourcir — validations, historiques — et recalcule le statut d'une unité
d'étude à partir de son avancement plutôt que de le fusionner.

### Ce que la composition évite

`accountState` tranche à l'horodatage près. Une horloge d'appareil en retard — un téléphone mal
réglé, ou simplement resté longtemps hors ligne — ferait donc perdre à elle seule les
validations faites hors ligne. `AccountSync` repasse par la fusion à trois voies dès qu'une
base existe, et ces modifications l'emportent alors sans dépendre d'une horloge.

Un test porte un **témoin** explicite : il vérifie que `Program.accountState` seul perd
effectivement le verset, puis que la composition le conserve. Sans ce témoin, le test pourrait
passer alors que la fusion à trois voies ne sert à rien.

### La fusion travaille sur l'arbre JSON

`OfflineMerge` compare et assemble des `JsonElement`, pas des `AppState`. C'est délibéré : un
client Android plus ancien doit pouvoir fusionner un état écrit par un client plus récent, dont
il ignore certains champs, **sans les perdre**. Une fusion typée ferait disparaître les champs
inconnus — c'est-à-dire précisément le travail de la version la plus récente.

---

## Le stockage local

### Les documents locaux, un par usage

| Fichier | Rôle |
|---|---|
| `state_anonymous.json` | l'état avant toute connexion ; il survit à l'installation |
| `state_account_<jeton>.json` | l'état d'un compte, avec son `userId` à l'intérieur. **Un fichier par compte**, comme la table `account_state` du client d'origine |
| `state_base_anonymous.json`, `state_base_<jeton>.json` | l'état serveur de référence, base de la fusion à trois voies |
| `outbox.json` | les opérations en attente d'envoi |
| `audio.json` | les réglages d'écoute — répétitions et récitateur, réunis dans un seul document. **Hors des comptes** : la façon d'écouter tient à l'appareil |
| `session.preferences_pb` | l'identifiant du propriétaire courant, en clair et sans plus |
| `session.bin` | le jeton de session, **chiffré** par le magasin de clés |

`<jeton>` est `LocalStateStore.fileToken(userId)` : l'identifiant rendu utilisable comme nom de
fichier. Un fichier unique partagé aurait deux conséquences fâcheuses — un utilisateur qui se
reconnecte hors ligne retrouverait un état vide au lieu de sa progression, et la base de fusion
d'un compte servirait à un autre, ce qui désactiverait silencieusement la fusion à trois voies
au pire moment, juste après un changement de compte.

### Un fichier d'état illisible n'est pas une remise à zéro

`JsonFileStore` **met de côté** un document illisible (`<nom>.corrupt-<horodatage>`) et
redémarre sur une valeur par défaut, au lieu de l'écraser. La donnée reste inspectable.

Mais il rend alors un état par défaut, et c'est là que le piège se referme : `AccountSync.merge`
reçoit ce défaut comme « état local ». La fusion à trois voies compare alors un local **vide** à
une base **pleine**, conclut que tout a été supprimé, et écrit ce vide — la base de fusion est
écrasée à son tour. La donnée finit par revenir du serveur au tour suivant, mais l'application
affiche un programme vide en attendant, et une séance validée dans cet intervalle se fusionne
contre une base détruite.

`UserRepository.sync` lit donc l'état **puis** interroge l'existence du fichier, dans cet ordre
précis : c'est la lecture qui archive un document illisible, et `BlobFile.exists()` répond faux
pour un fichier vide. Tester avant donnerait « vrai » pour un fichier qu'on vient d'archiver.
Un fichier absent fait passer `cached = null`, `Program.accountState` retrouve la règle
« appareil neuf » et **adopte le serveur en une seule synchronisation**.

Le test `un fichier d'etat illisible fait adopter le serveur en une seule synchronisation`
échoue si l'on retire ce `takeIf` : il attend `{1, 2}` et reçoit `{}`.

### Pourquoi pas `DataStore` typé

`androidx.datastore` expose bien un `Serializer<T>`, mais son gestionnaire de corruption
(`CorruptionHandler`) est déclaré **`internal`** en Kotlin : public dans le bytecode,
inaccessible depuis un autre module. La seule implémentation fournie,
`ReplaceFileCorruptionHandler`, est **`final`** et **écrase** le fichier fautif par une valeur
par défaut.

Pour une progression d'apprentissage, cela signifie qu'un octet de travers fait disparaître des
mois de mémorisation sans trace. `JsonFileStore` fait l'inverse : il **met le fichier de côté**
sous `<nom>.corrupt-<horodatage>` et redémarre sur un état vide, la donnée restant inspectable.
Un second bénéfice, non prévu mais réel : le magasin ne dépend plus que d'un `File`, donc il
s'éprouve sur la JVM avec de vrais fichiers, sans Android ni émulateur.

`DataStore Preferences` reste utilisé pour le seul identifiant de propriétaire, où la perte
éventuelle est réparable — le dépôt le réécrit depuis la session Supabase au démarrage.

### Pourquoi pas `Room`

Room exige **KSP**, et KSP n'a pas de version publiée pour Kotlin 2.4.20 (la dernière suit
Kotlin 2.3.x). La progression n'est pas une base relationnelle : c'est un document qu'on lit et
réécrit en entier, et dont l'unité d'échange avec le serveur est l'instantané complet — ce qui
est aussi ce qui rend l'envoi idempotent. Aucun SQL, aucune génération de code.

---

## Le garde-fou des comptes

L'état porte son `userId`, et **ce n'est pas une décoration** : c'est ce qui empêche deux
comptes de se mélanger sur un même appareil.

Le client d'origine ouvre sa base avec `WHERE user_id = ?` et refuse une ligne qui ne correspond
pas. Ici, `LocalStateStore.accountFor` et `accountChangesFor` tiennent ce rôle, et
`Program.accountState` le reprend côté domaine. Sans ce filtre, un second utilisateur connecté
sur le même appareil verrait la progression du premier **avant** la première synchronisation —
c'est-à-dire exactement pendant le moment où il n'y a rien d'autre à l'écran.

La déconnexion n'efface donc **pas** l'état du compte : c'est le `userId` qui protège, pas la
purge, et conserver l'état permet à une reconnexion hors ligne de retrouver la progression.
`LocalStateStore.clearAccount()` existe pour une remise à zéro explicite demandée par
l'utilisateur.

### Une session absente n'est pas une déconnexion

Le propriétaire courant vit dans `SessionPreferences`, et **seule une session ouverte l'écrit**
(`AuthRepository.remember`). Les trois autres états — `INITIALIZING`, `SIGNED_OUT`,
`REFRESH_FAILURE` — n'y touchent pas.

C'est un écart délibéré avec le client d'origine, où `restoreSession` traitait l'absence de
session comme une déconnexion. Or « pas de session » est produit par quatre situations très
différentes : une déconnexion voulue, un jeton expiré, un téléphone sans réseau, et un projet
injoignable. Les confondre ferait disparaître la progression locale de l'écran exactement quand
elle est le seul contenu disponible — c'est-à-dire le contraire de l'exigence « le lancement ne
doit jamais être bloqué par le réseau ».

La déconnexion est donc **explicite** : `AuthRepository.signOut()` efface le propriétaire, et
rien d'autre ne le fait. `refreshOwner()` ne l'écrase que s'il y a réellement un utilisateur à
adopter, jamais pour le vider.

---

## La file d'attente

Une seule entrée par type d'opération et par utilisateur : une longue période hors ligne
n'accumule pas des dizaines d'instantanés dont seul le dernier compte. La file survit à la
fermeture de l'application, donc une validation faite dans le métro arrive au serveur une fois
le réseau revenu, même si l'application a été fermée entre-temps.

L'absence de doublons est assurée à **trois** niveaux, ce qui la rend difficile à casser par
accident :

1. la file remplace au lieu d'empiler ;
2. l'envoi ne porte que sur la dernière opération, et n'acquitte qu'après confirmation ;
3. l'écriture serveur est un `upsert` sur la clé primaire `user_id` — une seconde écriture ne
   peut pas créer une seconde ligne.

---

## Les secrets

Le jeton de session est chiffré en **AES/GCM/NoPadding** avec une clé créée dans le
`AndroidKeyStore`, donc non exportable. Le chiffrement est stocké sous enveloppe versionnée
(octet de version, longueur du vecteur d'initialisation, vecteur, texte chiffré) pour qu'un
changement de format reste détectable plutôt que de produire un déchiffrement silencieusement
faux.

Un jeton indéchiffrable n'est pas une panne bloquante : la session est supprimée et
l'utilisateur se reconnecte. Sa progression locale, elle, est intacte.

La clé `service_role` n'apparaît nulle part, et son interdiction est écrite dans le code
lui-même (`SupabaseConfig`), pas seulement dans la documentation.

---

## L'interface : reprendre l'apparence sans la copier

L'application d'origine est en React Native, donc rendue par le même moteur sur iOS et Android.
La nouvelle est en Compose, rendue par Android. L'objectif n'est pas de simuler l'ancienne, mais
de **retrouver son apparence** avec les moyens d'Android. Trois points le montrent.

### Les couleurs ne sont pas réécrites, elles sont vérifiées

Les cinq palettes, les quatre accents et les échelles de `tokens.ts` sont portés **à
l'identique**, jusqu'aux codes hexadécimaux. Mais une promesse de fidélité ne vaut que si elle
est mesurée : `tools/verifier-jetons-design.py` extrait les valeurs des deux sources et les
compare clé par clé — 11 contrôles, 48 couleurs. Ce contrôle a lui-même été **falsifié** en
mutilant quatre valeurs (une palette, une couleur commune, un accent, une pastille) : il a
signalé les quatre, puis les fichiers ont été restaurés à l'octet.

### La règle d'accent du dépôt d'origine n'est pas symétrique

`applyTheme()` n'écrase la palette que dans deux cas : quand l'utilisateur a **choisi** un accent,
ou quand le thème est **blanc**. Un thème coloré sans accent explicite garde donc ses propres
fonds teintés. Ce n'est pas un détail : appliquer l'accent par défaut à tous les thèmes les
ferait tous ressembler au thème blanc, et les cinq choix de l'écran Apparence n'en seraient plus
qu'un. La règle est reproduite telle quelle et protégée par un test dédié.

### La police de titre est une police variable, et son défaut n'est pas 400

Le dépôt d'origine chargeait deux fichiers statiques de Cormorant Garamond (400 et 600) via les
paquets `@expo-google-fonts/*`. Android embarque **un seul fichier variable** sous licence OFL,
dont l'axe `wght` va de 300 à 700 — avec **300 comme instance par défaut**. Utiliser la police
sans préciser l'axe afficherait du *Light* : chaque graisse est donc demandée explicitement.

La vérification n'est pas faite sur le fichier téléchargé mais sur **l'artefact compilé** : la
table `fvar` (300..700) est bien présente dans l'AAR, donc la compilation des ressources n'a pas
aplati la police. Les deux fichiers de licence OFL sont embarqués dans les `assets`.

### Ce qui est repris tel quel, ce qui est remplacé

| Composant d'origine | Équivalent Android | Pourquoi |
|---|---|---|
| `Card` | `AppCard` | même géométrie ; la marge basse de 12 px est conservée par défaut, l'espacement vertical se gérant d'habitude chez le parent en Compose |
| `Button` | `AppButton` | **redessiné** : hauteur minimale 44, rayon 15 et variante secondaire bordée que Material n'offre pas. Un `Box` cliquable garde la géométrie exacte et récupère l'ondulation |
| `Choice` | `AppChoice` + `RadioButton` Material | React Native dessinait les glyphes `●` / `○`. Le composant natif ajoute l'état vocalisé par TalkBack et une cible tactile de 48 px |
| `CheckChoice` | `AppCheckChoice` + `Checkbox` Material | même raison |
| `Field` | `AppField` + `OutlinedTextField` | le champ natif ajoute l'état de focus, le curseur, les poignées de sélection et de copier-coller. Contrepartie : il est un peu plus haut que le champ d'origine |
| `Label`, `Title` | `AppLabel`, `AppTitle` | même rôle ; le texte est **sélectionnable par défaut**, copier un verset étant un geste courant |

### Pourquoi pas de collections immuables

`kotlinx-collections-immutable` est souvent recommandé en Compose parce que le compilateur
considère `List` comme instable, ce qui empêcherait un composable d'être sauté. Ce n'est plus
vrai : depuis Compose 2.0.20 le **saut fort** est activé par défaut et compare les paramètres
instables par égalité structurelle. Ajouter la dépendance aurait imposé de convertir les listes
de tout le domaine — déjà testé — pour un gain nul. Elle n'est donc pas là.

---

## Les tests

| Suite | Nombre | Ce qu'elle couvre |
|---|---|---|
| `core:model` | 3 | les clés persistées des sources : le `@SerialName` de chacune est **lu sur le descripteur** au lieu d'être recopié dans une seconde table, et les huit valeurs sont figées par un test — ce sont elles que le client React Native écrit dans `sourcePages` |
| `core:domain` | 548 | Coran, dates, programme, révisions, consolidations, signets, audio, file d'écoute et silences, quiz, lecteur et gestes, fusion hors ligne, file d'attente, composition de synchronisation, décision d'ouverture, messages de connexion, **libellés des écrans du lecteur** (feuille d'options, sélecteur de sourate, réglages d'écoute : titre aligné sur la ligne qui l'ouvre, vitesses en virgule française, note de marge technique, refus de la saisie libre), **règles du paquet « Coran 1441 »** (décision d'entrée, taille exacte, dimensions d'image, témoin d'installation, forme de la page selon la source, rectangles des versets, transition de source, mots du panneau), **panneau de traduction** (plage de séance prioritaire, plage ramenée au corpus au lieu de faire tomber l'écran, accord des 6 236 versets avec la traduction, référence d'un verset, titre aligné sur la ligne qui l'ouvre), **marques du lecteur** (le marqueur du professeur compte autant que celui de l'élève, un signet supprimé ne colore plus la page, une marque hors corpus est conservée telle quelle) et **priorité des teintes** (difficile, puis signet, puis lecture — l'ordre du rendu principal, quand le rendu immersif de la source ordonne autrement), et **règles de l'écran des signets** (une ligne bâtie sur le verset et non sur les champs enregistrés, un signet hors corpus **omis** au lieu de faire tomber l'écran, « Dernière reprise » **retriée**, et la page suivant le **découpage de la source affichée** : 56 versets sur 6 236 changent de page entre Médine et le paquet 1441), et **le toucher de la page** (le dé-zoom puis le dé-centrage, sans quoi un appui sur une page agrandie désigne un verset d'autant plus éloigné que l'agrandissement est fort) et **les décisions du panneau des marques-pages** (quelles entrées il propose, et quelle notice l'emporte quand la pose et la confirmation se chevauchent), et **mots du panneau des actions d'un verset** (le libellé du marquage suit le marqueur de l'**élève** seul — la bascule ne touche jamais à celui du professeur —, les entrées sont filtrées par destination, et « Sélectionner un passage » l'est faute de geste porté), et **préférence de récitateur** (l'état du compte prime, la mémoire locale est par utilisateur, le report vers le compte est à sens unique et conditionné à l'absence de choix du côté du compte), et **l'écran immersif du paquet « Coran Test »** (la table des pages de composition, le chargeur de ses deux fichiers, le document qui se mesure lui-même, la mémoire bornée à cinq documents, les décisions de routage — page **demandée** pour l'annonce, page **en cours** pour la panne, page **affichée** pour les gestes —, la surimpression dont les clés sont « sourate:verset » et dont les marques hors corpus sont **omises**, le découpage de la source affichée : 36 pages sur 604 divergent, à partir de la page 120, et **découpage des pages d'étude** — la page d'un verset, la plage d'une page, la dernière page d'un verset et la clé d'étude suivent la source affichée, et la source composée n'est plus repliée sur le moushaf, et **mémoire du lecteur** — la page quittée rejoint les pages lues, le verset retenu est **choisi** (celui déjà noté s'il tient encore sur la page, sinon celui de l'ouverture, sinon le premier de la page), la page n'est conservée que pour la source composée, l'état est migré avant la décision, et la page d'ouverture d'une source en images **reprojette** le dernier verset lu au lieu de relire une page écrite dans un autre découpage) |
| `core:data` | 73 | lecture locale, hors ligne, premier chargement, isolation des comptes, fichier d'état illisible, file, idempotence, remise à zéro, règle du propriétaire, **dépôt des réglages d'écoute** (premier démarrage, document complet, champ hors bornes isolé, document illisible mis de côté, relecture depuis le disque, échec d'écriture qui ne publie rien), **branchement du conteneur** (contrôle de forme : la relecture au démarrage et l'emplacement du document), **installation du paquet 1441** (reprise, témoin écrit en dernier, refus d'une archive douteuse) et **transport HTTP** (en-tête `Range`, `200` contre `206`, refus), et **récitateur retenu avec son propriétaire** (relecture, document sans propriétaire, réécriture seulement si la valeur change) |
| `feature:home` | 23 | point de reprise, `scheduledDate` contre `date`, série de jours, période de chaque bandeau, objectif de la semaine, libellés de repli, et **la page annoncée** qui suit le découpage de la source affichée |
| `core:design` | 19 | asymétrie de l'accent, fond secondaire, distinction des cinq palettes, échelles de `tokens.ts`, résolution des polices, écran Apparence |
| `feature:reader` | 72 | nommage des pages, bornes du geste, **affichage du nombre d'écoutes** (ce que le moteur jouera, saisie abîmée comprise), **pose des quinze bandes** d'une page du paquet (hauteur, premier et dernière bande, chevauchement), et **branchement du panneau de traduction** (contrôle de forme : la ligne de la feuille l'ouvre, ses lignes sont calculées, et il est rendu), et **branchement des marques** (contrôle de forme : les deux ensembles reçus, les deux transmis à la page, et l'ensemble vide écrit en dur qui ne revient pas), et **branchement du mode de pose des signets** (contrôle de forme : le bouton de la coquille et sa marque d'activité, le panneau rendu, l'armement qui le referme, la garde du point qui ne désigne rien, et le verset rapporté à l'appelant), et **branchement du panneau des actions du verset** (contrôle de forme : l'appui long l'ouvre, la fiche lui cède la place, « Écouter » joue un seul verset avec les trois réglages de l'original, « Répéter » ouvre les réglages d'écoute sans lancer, le marquage ne referme rien, et « Sélectionner un passage » reste absent), et **l'écran des marques-pages** (contrôle de forme : une **fenêtre** et non un remplacement — le lecteur reste monté, donc l'écoute en cours ne s'arrête pas —, la confirmation avant la suppression, le texte coranique borné à deux lignes, et la vue qui ne calcule aucune ligne), et **la reprise d'un signet dans le lecteur** (contrôle de forme : le verset demandé est désigné, et le panneau quitté est refermé, comme le `setSessionPanel(null)` du source), et **le document immersif côté vue** (le nom de police d'une page, **sans** complément sur trois chiffres, et les trois polices qu'une page demande ; la couleur de papier à six chiffres, alpha **écarté**, et les quatre papiers qui font l'aller-retour, et **suivi d'une page demandée de l'extérieur** (contrôle de forme : le lecteur obéit à la page demandée au lieu de n'obéir qu'à sa première composition, sans rejouer au premier rendu, et sans effacer le verset désigné d'une reprise)) |
| `core:audio` | 16 | conduite d'une séance sur horloge virtuelle : silence observé, reprises, arrêt, répétition illimitée, changement de récitateur, fin oubliée après fermeture, fichier illisible, **réglages appliqués à la séance en cours**, et **saisie d'écoutes illisible qui ne fige pas la séance** |
| `feature:auth` | 9 | activation du formulaire : adresse, longueur du mot de passe, occupation, libellés |
| `navigation` | 57 | calcul des marques par la route du lecteur (contrôle de forme : l'état du compte est observé, les deux règles du domaine sont appelées, les deux ensembles sont transmis au lecteur), et **l'écriture d'un signet par la route** (contrôle de forme : la règle du domaine appelée, le magasin de l'état, la page de la source affichée, l'écriture protégée), et **écriture du marqueur de difficulté** (contrôle de forme : la règle du domaine, le sous-ensemble de l'élève distinct des versets difficiles, transmis au lecteur, le magasin de l'état, et l'écriture protégée), et **l'écran des marques-pages par la route** (contrôle de forme : la condition **et** l'appel, les lignes de la règle du domaine, la page de la **source affichée** pour la reprise, la suppression, le verset rappelé au lecteur, la remise à zéro du verset en attente, et l'écran posé **par-dessus** un lecteur qui reste monté — sans `return` entre les deux), et **préférence de récitateur par la route** (contrôle de forme : les quatre sources de la résolution, la mémoire locale écrite avec son propriétaire, le report conditionné, et le récitateur initial), et **mémoire du lecteur par la route** (contrôle de forme : le verset d'ouverture, la règle du domaine, l'écriture **avant** la fermeture, le retour système sur la même sortie, et la page d'ouverture tenue par la règle), et **règles de route du lecteur**, éprouvées **pour de vrai** (argument facultatif, base d'une route paramétrée, plein écran reconnu derrière l'argument), et **porte du lecteur depuis l'accueil** (contrôle de forme : seule entrée du lecteur) |
| **total** | **820** | 76 classes de test |

Le domaine est éprouvé sur les **vraies données** — 6 236 versets, 114 sourates, 604 pages — et
non sur une maquette de trois versets, qui laisserait passer une erreur d'indexation ou une
borne fausse. La couche de données est éprouvée avec de **vrais fichiers** dans un dossier
temporaire et un serveur en mémoire capable de tomber en panne. Les règles de l'accueil sont
éprouvées sur le **vrai référentiel**, chargé par le même chemin de classes que sur l'appareil :
un test qui lirait les données depuis un chemin de fichier ne prouverait pas que le chargement
fonctionne dans l'APK.

**La conduite d'une séance d'écoute s'éprouve sur une horloge virtuelle**, jamais en dormant :
un test qui attend 200 ms pour de vrai échoue sur une machine chargée, et fait douter du code
plutôt que de lui. Une mesure utile à connaître : `advanceUntilIdle()` **n'exécute pas** le
travail d'un `backgroundScope`, où vivent les collecteurs du contrôleur audio. Un test qui s'en
servirait verrait un enchaînement qui ne se produit jamais — et, pire, il passerait quand on lui
demande de constater une **absence**. Ces tests avancent donc l'horloge explicitement. Le
corollaire est une règle de conception, et non de test : **le moteur ne lève jamais sur un
réglage**. Une préférence abîmée y fait ramener le nombre d'écoutes à 1 ; c'est l'**écran**, et lui
seul, qui refuse une saisie, avec le message du client d'origine. L'écran empêche d'y arriver, le
moteur empêche qu'une préférence abîmée interrompe l'écoute — et les deux règles vivent à deux
endroits distincts pour ne pas se confondre.

**L'installation du paquet « Coran 1441 » s'éprouve sans réseau et sans fabriquer 102 Mo.** La
règle d'archive vit dans le domaine, où elle ne connaît ni fichier ni socket ; l'installeur reçoit
un `ArchiveTransport`, une taille attendue et une horloge. Les tests construisent donc un **vrai
ZIP** de 9 060 entrées minuscules et injectent sa taille : le chemin éprouvé reste celui de la
production, puisque c'est **la même valeur** qui règle la reprise, l'avancement et le contrôle
final. Le transport HTTP, lui, est éprouvé contre un **vrai serveur** sur la boucle locale : ce qui
peut se tromper à cet endroit est la forme de la requête — l'en-tête `Range` — et la lecture du
code de réponse, et une doublure qui rendrait directement un flux n'en dirait rien.

**Le total ne se déduit pas d'un `grep @Test`.** `tools/compter-tests.py` lit les rapports XML du
coureur, et ne retient qu'une variante par classe : additionner `**/build/test-results/**` compte
chaque test deux fois (debug **et** release) et oublie les modules JVM purs, qui écrivent sous
`test-results/test/`. Ce piège a réellement fait publier « 171 » au lieu de « 161 ». Le script
affiche aussi le **nombre de classes lues** : un relevé vide signalerait que le motif n'a désigné
aucun test, et « tout vert » ne voudrait alors rien dire.

**Un test vert ne dit pas qu'il détecte quoi que ce soit.** `tools/falsifier.py` casse
volontairement une règle — cent vingt-deux fois, chacune sur une règle différente — relance la suite, et
vérifie que les tests qui tombent sont **ceux qui devaient tomber**. Il restaure ensuite le fichier
et le prouve par empreinte, pas par la bonne volonté d'un `finally`. Deux pièges y sont traités
nommément : les rapports XML restent sur le disque d'une exécution à l'autre, donc seuls ceux
**écrits après le lancement** sont lus — sinon un test supprimé continuerait de paraître vert ; et
un filtre Gradle trop étroit donne un « BUILD SUCCESSFUL » sans avoir rien joué, donc un résultat
vide est un échec et non un succès. Ce harnais a réellement servi : il a montré qu'une classe de
tests ne passait que **parce qu'une autre avait installé le référentiel avant elle**, et que deux
garde-fous de `MushafPageShape` se recouvraient si exactement qu'aucun n'était éprouvable seul.

**Un défaut peut ne faire tomber aucun test.** Une exception levée dans le collecteur qui conduit
une séance d'écoute était **absorbée par la portée de coroutines** : le collecteur mourait, la
leçon se figeait, et rien ne le disait — ni message, ni erreur, ni test rouge. Le défaut n'a été
établi qu'en écrivant un test qui demande explicitement à la séance de **survivre** : après une fin
de verset sur des réglages abîmés, il faut qu'une séance neuve enchaîne encore. Un test qui se
serait contenté de constater l'absence d'erreur serait passé au vert sur le code fautif.

**Tout ne s'éprouve pas par le comportement.** Le conteneur ne se construit qu'avec un `Context`
Android : dans une épreuve JVM, rien de ce qu'il fait au démarrage ne peut être exécuté. Or deux
choses y sont invisibles à l'exécution — **la relecture des réglages d'écoute au démarrage**, et
**l'emplacement de leur document**. Si la première disparaît, le lecteur part des valeurs par
défaut et le premier réglage **écrase le choix enregistré sans que rien ne le dise**. C'est
pourquoi `AppContainerWiringTest` lit le source du conteneur et vérifie que l'appel est écrit. Ce
contrôle est **de forme** : il dit que le branchement existe, pas que la relecture aboutit — le
comportement, lui, est éprouvé par `AudioSettingsRepositoryTest`, sur de vrais fichiers.

**Le même raisonnement vaut pour le branchement du lecteur.** Le panneau de traduction
est rendu par une fonction `@Composable` : rien de ce qu'il branche ne s'exécute dans une
épreuve JVM. Or trois choses y seraient silencieuses — la ligne de la feuille d'options
qui ouvre le panneau, le calcul de ses lignes, et son rendu. Si l'une disparaissait, le
domaine resterait vert, la compilation passerait, et le seul symptôme serait une ligne
absente d'une feuille ou un panneau vide. `ReaderPanelWiringTest` lit donc le source du
lecteur et vérifie que ces trois branchements sont écrits. Il ne dit pas que le panneau
s'affiche correctement : cela se lit dans un diff, et c'est écrit dans le code concerné.

**Les marques de la page ont suivi le même chemin, avec une nuance.** Le lecteur reçoit les versets en signet et les versets difficiles, avec pour valeur par défaut l'ensemble **vide** : si le passage disparaissait, la page compilerait et perdrait simplement ses marques, sans qu'aucun test ne rougisse — `ReaderScreenMarksTest` le tient donc, dans le module du lecteur. Le calcul, lui, vit dans la route, seul endroit à connaître le conteneur : `ReaderRouteMarksTest` le tient, et c'est **pour cela que `navigation` a reçu son premier `src/test`**. Un contrôle qui lirait le source d'un autre module ne serait pas rejoué quand ce source change — la tâche `Test` ne suit que les entrées de son propre module — et resterait vert par oubli.

**La priorité des teintes, elle, n'est pas restée dans la vue.** Elle y était écrite sous forme de `when` sur des couleurs, donc inatteignable : une fonction privée, dans un composable. Or c'est une règle, et une règle mesurable — un verset peut porter deux marques, une seule couleur l'emporte. Elle vit maintenant dans `ReaderTint`, où `ReaderTintTest` l'éprouve, et la vue n'a gardé que la traduction d'un cas en couleur. Le gain n'est pas cosmétique : **la source se contredit** entre ses deux rendus — `MushafPage.tsx` donne le signet gagnant sur la lecture, `coranTest/html.ts` donne l'inverse — et c'est exactement le cas qu'un test peut figer, là où un `when` privé ne se serait jamais laissé interroger. L'ordre retenu est celui du rendu principal, et le fait que l'autre existe est écrit dans `ReaderTint`, pas seulement ici.

**Les règles de l'écran des signets ont été portées avant l'écran, et une d'entre elles est
mesurée inerte.** Ce qui décide d'une ligne — le nom de sourate, la page, l'ordre, la marque
« Dernière reprise », l'omission d'un signet hors corpus — vit dans `Bookmarks`, et
`BookmarksScreenRulesTest` l'éprouve. Une règle n'a **pas** pu être éprouvée par son effet, et le
dire vaut mieux que d'écrire un test qui la simulerait : la branche « conserver la page notée »
de `MushafSourceNavigation.versePage` est **inatteignable** avec les bornes livrées, un verset
n'y occupant qu'une seule page par découpage. Elle est tout de même mesurée — par le test `dans
le decoupage livre, un verset n'occupe qu'une seule page`, qui échouera le jour où les données
changeront et forcera à relire la règle au lieu de la réparer.

Le choix de la clé persistée, lui, avait sa **date de péremption écrite** dans le KDoc de
`Bookmarks.pageFor` : « l'écart apparaîtra le jour où `coranTest` aura son propre découpage ».
Ce jour est arrivé avec l'écran immersif, et l'échéance a été **honorée** : le repli qui ramenait
cette source sur le découpage de Médine est retiré, et **trois règles d'étude** qui le
prolongeaient — la page d'un verset, la plage d'une page, la dernière page d'un verset — ont reçu
la branche qui leur manquait, avec l'appelant qui annonçait la page. C'est la leçon des teintes —
une règle qu'on ne peut pas interroger finit par mentir — prise au sérieux : ici la règle était
interrogeable, et c'est le fait qu'elle ait été **datée** qui a dit quand la relire.

**Le mode de pose a été porté en deux temps, et le second a mesuré une erreur.** Le panneau, la
notice et le verset touché vivent dans le lecteur, mais la conversion d'une position d'écran en
verset y était écrite **en clair**, dans l'appui long. Le mode de pose avait besoin d'exactement
la même : elle est devenue `ReaderTouch`, éprouvée dans `core:domain`, et les deux appelants y
passent désormais. Deux copies auraient fini par désigner deux versets différents, et l'écart ne
se serait pas lu comme une erreur de calcul mais comme un **verset faux** — la fiche en décrivant
un, le signet en posant un autre, et les deux étant plausibles. Deux corrections s'y succèdent,
le dé-zoom puis le dé-centrage, et chacune a son cas qui **échoue** si elle disparaît. Le harnais
de falsification a d'ailleurs rendu là son verdict le plus utile : un de ses cas ne compilait
pas — remplacer la garde `if (touched != null)` par `if (true)` laisse `touched` en `Int?` là où
`save` attend un `Int` — et un cas qui ne compile pas mesure le compilateur, pas la règle. Il
porte maintenant sur deux lignes, avec un `?: 1` qui le rend compilable tout en supprimant la
garde. Le levier, lui, est resté : le panneau est une **fenêtre de dialogue**, donc son voile
couvre la page ; armer le mode de pose sans le refermer demanderait de toucher un verset à
travers ce voile, c'est-à-dire de ne rien pouvoir faire. C'est l'erreur la plus probable de ce
branchement, et elle a son cas.

**Ce qui reste hors de tout contrôle automatique, et qu'il faut donc lire dans un diff** : le
lecteur adopte la valeur relue tant que rien n'a été réglé à la main, et il rapporte chaque
changement à la route, qui l'écrit. Aucune épreuve ne traverse ce trajet — le lecteur est un
`@Composable`, et le projet n'a pas d'outillage de test d'interface. Les deux bouts sont tenus
(la règle d'adoption est dans `ReaderScreen`, l'écriture est éprouvée), le fil qui les relie ne
l'est pas.
Les deux constats sont écrits dans le code concerné, pas seulement dans ce document.

**Les données embarquées se prouvent, elles aussi.** `tools/verifier-parite-donnees.py` recalcule
l'empreinte des 11 fichiers de données et des 604 pages, la confronte à celle que le manifeste
d'import a enregistrée, et retrouve chaque fichier dans une copie locale de `coran-memoire` en
lecture seule. Il a servi dès sa première exécution : le manifeste portait une entrée pour
**lui-même**, héritée d'un second import, et cette entrée désignait l'empreinte du manifeste
**précédent**. Un fichier ne peut pas porter sa propre empreinte — la liste vient maintenant de ce
qui a été importé, et non d'un `readdirSync` sur le dossier.

**Ce qui a été appris en écrivant ces tests mérite d'être noté** : sur les dix premiers échecs
du domaine, huit étaient des **attentes de test fausses**, pas des défauts du portage. Le
réflexe correct est de relire la source, pas d'assouplir l'assertion.

---

## Assembler sans bibliothèque

Le graphe d'objets est écrit à la main dans `core/data/AppContainer.kt`. Hilt ou Koin
ajouteraient une génération de code, une version de plugin à suivre et une couche d'indirection
à franchir à chaque lecture de trace — pour remplacer un graphe d'une dizaine d'objets qui se lit
d'un seul écran.

**Le conteneur vit dans `core:data`, pas dans `:app`.** Aucune fonctionnalité ne peut dépendre de
`:app` — ce serait un cycle —, et les fonctionnalités ont besoin des dépôts. Il est publié par un
`staticCompositionLocalOf`, le seul mécanisme qui traverse la frontière d'un `@Composable` sans
faire passer le graphe en paramètre de chaque écran. `staticCompositionLocalOf` et non
`compositionLocalOf` : la valeur est posée une fois et ne change jamais, donc l'observer à chaque
accès ne servirait à rien. La valeur par défaut **lève une erreur explicite** : un
`CompositionLocal` oublié produirait sinon un `null` silencieux, puis un plantage loin de sa cause.

Le conteneur est construit dans `Application.onCreate` et non dans l'activité. Une rotation, un
changement de thème ou un passage en écran partagé ne doivent pas reconstruire le graphe — et
surtout pas relancer la lecture du référentiel coranique.

**Le chargement du référentiel est asynchrone.** Lire et analyser `verses.json` (6 236 versets)
sur le fil principal retarderait la première image de plusieurs centaines de millisecondes. Il
part donc sur `Dispatchers.Default` et l'avancement est publié sous forme de trois états —
`Loading`, `Ready`, `Failed` — et non d'un booléen : un booléen confondrait « en cours » et
« échoué », et l'échec s'afficherait comme une attente sans fin. Tant que l'état n'est pas
`Ready`, `Quran` rend un référentiel **vide** : aucune règle du domaine n'est utilisable, et
l'écran doit le dire plutôt que d'afficher « aucun verset ».

## Le calcul hors de l'interface

`HomeRenderer` est un objet pur : `render(état, jour) → HomeUiState`. Le `ViewModel` ne fait que
relier deux flux et l'appeler.

Cette séparation n'est pas cosmétique. Les règles de l'accueil — l'ordre du point de reprise, la
distinction entre `date` et `scheduledDate`, le calcul de la série, la période couverte par
chaque bandeau — se vérifient en quelques millisecondes, sans coroutine, sans horloge et sans
appareil. Enfouies dans un `init` de `ViewModel`, elles demanderaient de piloter
`Dispatchers.Main` pour être atteintes, et le calcul serait rejoué à chaque recomposition : il
appelle `Program.stats` **huit fois** par affichage.

**Ces tests ont été falsifiés avant d'être crus.** Trois mutations du calcul — `date` au lieu de
`scheduledDate`, les pastilles sur la semaine au lieu des sept derniers jours, une série qui
démarre toujours aujourd'hui — ont chacune été détectée par exactement le test prévu, et la
source a été restaurée à l'octet près.

> **Piège rencontré.** Le premier falsificateur a annoncé « 0/3 détectée » alors que le calcul
> était bien couvert. La faute était dans **la mesure**, pas dans le code : lire le rapport XML
> par expression régulière attribue l'échec d'un test au test précédent quand celui-ci est
> auto-fermant (`<testcase …/>`), et le test réellement rouge disparaît du relevé. Un
> analyseur XML corrige cela. Le réflexe est le même que pour les jetons de design : **quand un
> contrôle accuse, suspecter d'abord le contrôle.**

> **Piège Kotlin.** Les commentaires bloc **s'imbriquent**. Écrire un chemin comme
> `quran` suivi d'une barre oblique et d'une étoile à l'intérieur d'un KDoc ouvre un second
> commentaire ; le premier reste ouvert et la compilation échoue sur un « Unclosed comment » qui
> ne désigne pas la bonne ligne.
