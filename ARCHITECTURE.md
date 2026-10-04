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
les 132 tests du domaine exécutables en quelques secondes, sans émulateur, sur les vraies
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

:feature:home           accueil
:feature:reader         lecteur de moushaf
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

---

## Sens des dépendances

```
app ─┬─> navigation ──> feature:* ──> core:design
     │                                 core:data ──> core:domain ──> core:model
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
| `core:domain` | 153 | Coran, dates, programme, révisions, consolidations, signets, audio, quiz, lecteur, fusion hors ligne, file d'attente, composition de synchronisation, décision d'ouverture, messages de connexion |
| `core:design` | 19 | asymétrie de l'accent, fond secondaire, distinction des cinq palettes, échelles de `tokens.ts`, résolution des polices, écran Apparence |
| `feature:home` | 22 | point de reprise, `scheduledDate` contre `date`, série de jours, période de chaque bandeau, objectif de la semaine, libellés de repli |
| `core:data` | 26 | lecture locale, hors ligne, premier chargement, isolation des comptes, fichier d'état illisible, file, idempotence, remise à zéro, règle du propriétaire |
| `feature:auth` | 9 | activation du formulaire : adresse, longueur du mot de passe, occupation, libellés |
| **total** | **229** | 19 classes de test |

Le domaine est éprouvé sur les **vraies données** — 6 236 versets, 114 sourates, 604 pages — et
non sur une maquette de trois versets, qui laisserait passer une erreur d'indexation ou une
borne fausse. La couche de données est éprouvée avec de **vrais fichiers** dans un dossier
temporaire et un serveur en mémoire capable de tomber en panne. Les règles de l'accueil sont
éprouvées sur le **vrai référentiel**, chargé par le même chemin de classes que sur l'appareil :
un test qui lirait les données depuis un chemin de fichier ne prouverait pas que le chargement
fonctionne dans l'APK.

**Le total ne se déduit pas d'un `grep @Test`.** `tools/compter-tests.py` lit les rapports XML du
coureur, et ne retient qu'une variante par classe : additionner `**/build/test-results/**` compte
chaque test deux fois (debug **et** release) et oublie les modules JVM purs, qui écrivent sous
`test-results/test/`. Ce piège a réellement fait publier « 171 » au lieu de « 161 ». Le script
affiche aussi le **nombre de classes lues** : un relevé vide signalerait que le motif n'a désigné
aucun test, et « tout vert » ne voudrait alors rien dire.

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
