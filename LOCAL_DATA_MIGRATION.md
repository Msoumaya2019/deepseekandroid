# Migration des données locales

## Ce qu'il faut comprendre d'abord

**Il n'y a pas de transfert automatique possible entre les deux applications.** Chaque
application Android possède son propre bac à sable : le système interdit à `com.msoumaya.deepseekandroid`
de lire la base SQLite, le Keychain ou les fichiers de `fr.coranmemoire.app`. Ce n'est pas une
limitation technique contournable, c'est la garantie qui protège les données des utilisateurs.

Ce document est donc une **cartographie fonctionnelle** : pour chaque donnée locale du client
React Native, où elle vit aujourd'hui, quel est son équivalent Android, et ce qu'un utilisateur
retrouve ou non en installant l'application Android.

La bonne nouvelle est que la quasi-totalité de la progression **ne vit pas localement** : elle
est dans `user_state`, côté Supabase. Elle revient donc toute seule à la première connexion.

---

## Où le client React Native stocke ses données locales

| Emplacement | Contenu |
|---|---|
| **SQLite** (`expo-sqlite`, base `coran-memoire.db`) | l'état applicatif, les états par compte, la file d'attente |
| **AsyncStorage** | réglages d'audio, favoris d'invité, identifiant d'installation des notifications |
| **SecureStore** (Keychain / Keystore), par fragments | la session Supabase |

### Détail de la base SQLite

```sql
CREATE TABLE app_state      (id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE account_state  (user_id TEXT PRIMARY KEY, data TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE pending_sync   (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, payload TEXT NOT NULL,
                             created_at TEXT NOT NULL, base TEXT);
```

Ces trois tables sont reproduites **fidèlement**, table par table :

| Table React Native | Équivalent Android | Remarque |
|---|---|---|
| `app_state` (id=1) | `state_anonymous.json` | l'état hors connexion |
| `account_state` (par `user_id`) | `state_account_<jeton>.json`, **un fichier par compte** | un fichier unique aurait fait perdre la progression locale d'un compte à chaque changement de compte |
| `pending_sync` | `outbox.json` | la file d'attente |
| colonne `base` de `pending_sync` | `state_base_<jeton>.json`, **une base par compte** | la base de la fusion à trois voies |

**Une différence assumée sur la base de fusion.** Le client React Native enregistre la base
*dans chaque opération* en file (`base = loadState()` au moment de la modification). Android la
garde dans un fichier par compte, avancé après chaque synchronisation réussie. Les deux sont
équivalents dans le cas courant — après une synchronisation, l'état local est identique à la
base — mais la version Android ne fait pas grossir la file avec une copie de la base par
opération.

**Ce qui a été conservé à l'identique :** la validation de l'état au chargement. Le client
d'origine refuse un état dont `schema !== 1` et retombe sur l'état par défaut ; Android fait de
même (`Program.isValidPersistedState`), et **archive** le fichier refusé au lieu de l'écraser.

### La base du Quiz, qui est à part dès l'origine

Le client React Native ouvre une **seconde** base — `coran-quiz.db`, et non la base principale —,
avec deux tables :

```sql
CREATE TABLE quiz_cache  (user_id TEXT PRIMARY KEY, data TEXT NOT NULL);
CREATE TABLE quiz_outbox (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, payload TEXT NOT NULL,
                          created_at TEXT NOT NULL);
```

| Table React Native | Équivalent Android | Remarque |
|---|---|---|
| `quiz_cache` (par `user_id`) | `quiz_<jeton>.json`, **un document par compte** | le **jour** est porté par le document et non par le nom du fichier : l'écran compare `data.day` à `quizDay()`, et un fichier par jour aurait fait disparaître cette comparaison — donc la règle « un instantané d'hier ne vaut pas pour aujourd'hui » |
| `quiz_outbox` | `quiz_outbox.json`, **une file unique** | la clé primaire de l'original est `« <compte>:<jour> »`, et cette forme est **reproduite** telle quelle : elle rend l'enfilement idempotent, une seconde réponse pour le même jour ne pouvant pas produire une seconde entrée |

**Pourquoi une file séparée, et pas `outbox.json`.** `OutboxStore` rend ses opérations par compte
**sans filtrer leur nature**, et le worker de synchronisation les envoie toutes comme des
instantanés d'état. Verser une réponse de Quiz dans la file commune la ferait donc pousser comme
un état complet : le serveur l'**accepterait**, et une progression serait écrasée — un envoi
réussi qui détruit des données. L'original a deux tables distinctes pour la même raison, et il
n'aurait pas suffi de renommer les choses.

**Ce qui n'est pas recopié.** Le catalogue des questions ne quitte pas le serveur. Ce qui est mis
en cache est l'**instantané** rendu par `quiz_snapshot` : la question du jour, **dépouillée de sa
solution** — le serveur en retire `correctAnswerId`, l'explication et la source —, les réponses du
compte, ses défis et les quiz thématiques. La correction ne se lit donc que dans la **réponse
enregistrée**, qui porte la question complète ; c'est pourquoi le repli de l'écran est
`response?.question ?? daily`, et non `daily` seul.

### La file des signalements, qui a sa propre base — et qui ne se migre pas

Le client React Native ouvre une **troisième** base — `coran-problem-reports.db` —, avec une seule
table :

```sql
CREATE TABLE problem_report_queue(id TEXT PRIMARY KEY, user_id TEXT NOT NULL, payload TEXT NOT NULL,
                                  local_uri TEXT, mime TEXT);
```

| Table React Native | Équivalent Android | Remarque |
|---|---|---|
| `problem_report_queue` | `problem_reports.json`, **une file unique** | la file est **commune** à tous les comptes, comme la table, et chaque entrée porte son compte. `QueuedProblemReport.userId` est **lu sur la ligne** et non recopié à côté : la table d'origine porte `id` et `user_id` en colonnes **et** dans la charge utile, et deux copies d'un même fait finissent par diverger — ici, une divergence retirerait de la file l'entrée d'un autre compte |
| `local_uri` | le chemin absolu, sous `filesDir/state/problem-reports/` | l'original range une URI `file://`, le portage un chemin **sans schéma** : `java.io.File` attend un chemin, et un préfixe `file://` construirait un fichier qui n'existe pas — silencieusement, puisqu'un `File` inexistant ne lève qu'à la lecture |

**Ce qui n'est pas migré, et pourquoi c'est sans conséquence.** Un signalement qui n'est pas encore
parti est un **geste en attente sur cet appareil**, et non une donnée de compte : rien ne le
rattache à une progression, et la capture vit dans le dossier de documents de l'appareil qui l'a
choisie. La file reste donc sur l'appareil, et le client React Native garde la sienne — c'est la
même règle que pour les réglages de répétition audio.

**Une différence de forme assumée.** L'original range le signalement dans une colonne `payload TEXT`
et le relit par `JSON.parse`. Ici la charge utile est un objet `@Serializable` : il n'y a plus de
chaîne à analyser, donc plus d'analyse qui puisse échouer, et un document illisible est **mis de
côté** par le magasin au lieu d'être perdu — c'est ce qui remplace la garantie qu'un `JSON.parse`
réussi donnait.

**Un brouillon dans le cache, qui n'existe que le temps du geste.** Le sélecteur d'images d'Android
ne rend pas un fichier mais une adresse `content://` — une autorisation de lecture, révocable, dont
la taille peut être inconnue. Le portage lit donc le flux, le **copie** sous
`cacheDir/problem-report-pick/<uuid>.<extension>`, puis mesure **la copie** : c'est la taille sur le
disque qui décide, comme au moment de l'envoi. Ce brouillon est jetable — le système peut l'effacer,
et un refus de format ou de taille le supprime aussitôt —, et il n'est **pas** retiré après un envoi
réussi : c'est un cache, Android le récupère quand la place manque, et rien ne s'y rattache. La copie
qui compte est celle que le dépôt range ensuite sous `filesDir/state/problem-reports/`, par un
`copyTo` et non un déplacement, et c'est elle que porte `QueuedProblemReport.localPath`.

---

## Les clés AsyncStorage

| Clé React Native | Ce qu'elle porte | Équivalent Android | Synchronisée ? |
|---|---|---|---|
| `audio-reciter-hafs` | le récitant choisi | `state/audio.json` (local) — `AppState.audioPreferences.reciterId` une fois le chemin branché | **pas encore** |
| `audio-repeat-preferences` | répétition (1x/2x/3x/5x), plage X→Y, pause entre versets | `state/audio.json` (local) | **non** — comme dans le client d'origine |
| `guest-content-favorites` | favoris de contenus créés sans compte | `content_favorites` (Supabase) une fois connecté | non en mode invité |
| `notification-installation-id` | identifiant d'installation pour les notifications | **recréé** sur Android | non — il est propre à l'installation |
| `notifications-requested-on-device` | « a-t-on déjà demandé l'autorisation sur cet appareil ? » | indicateur local | non — propre à l'appareil |
| `audio-reciter-legacy-owner` | marqueur de migration d'un ancien format | sans objet | — |

**Les deux clés d'écoute tiennent dans un seul document.** `audio-repeat-preferences` et
`audio-reciter-hafs` sont réunies dans `state/audio.json`, et non dans deux fichiers : elles
répondent à la même question — comment j'écoute — et deux fichiers écrits au même moment
finiraient par diverger sur l'appareil de quelqu'un qui change souvent de récitateur. Ce document
n'est **pas** par compte, comme les clés d'origine : la façon d'écouter tient à l'appareil et à
l'oreille de celui qui le tient. Un récitateur enregistré puis retiré des récitateurs disponibles
retombe sur le défaut, et un champ hors bornes ne fait pas perdre ses voisins.

Deux points méritent d'être notés.

**Le récitant a sa place dans l'état synchronisé, mais Android ne l'y écrit pas encore.** Il fait
partie de `AppState.audioPreferences`, donc il peut voyager avec l'état : un utilisateur qui
choisit `ar.shaatree` sur React Native le retrouve dans l'état rapatrié. Ce qui manque côté
Android, c'est le **chemin inverse** — le choix fait dans le lecteur est enregistré localement,
mais pas encore rapporté à l'état synchronisé. C'est une ligne de la phase B.

**Les réglages de répétition ne le sont pas** — ni dans un sens ni dans l'autre, puisque le
client d'origine les garde en local. Android reproduit ce comportement plutôt que d'inventer
une synchronisation : c'est le choix fidèle, et il évite d'ajouter un champ au contrat partagé
pour un réglage que le client React Native n'utiliserait pas. Si l'envie venait plus tard de les
synchroniser, l'ajout serait **additif** (un champ optionnel dans `data`) et sans risque pour
l'autre client, qui préserve les champs qu'il ne connaît pas.

---

## La session Supabase

Le client React Native écrit la session par **fragments** dans le Keychain / Keystore, sous une
clé dérivée de `sb-<référence-du-projet>-auth-token`, avec un repli historique sur AsyncStorage.

Android fait plus simple et au moins aussi solide : **un seul fichier chiffré**
(`session.bin`), en AES/GCM avec une clé créée dans le `AndroidKeyStore` — donc non exportable.
L'identifiant du propriétaire courant est conservé à part, en clair (`session.preferences_pb`),
parce qu'il faut savoir *qui* est connecté dès le démarrage, avant tout déchiffrement.

Le découpage en fragments du client d'origine répond à une limite du Keychain iOS sur la taille
des valeurs. `AndroidKeyStore` n'a pas cette contrainte : garder un fichier unique supprime un
mécanisme, donc une source de pannes.

L'identifiant du propriétaire, lui, est **durable** : seule une session ouverte l'écrit, et seule
une déconnexion explicite l'efface. Le client d'origine traitait l'absence de session comme une
déconnexion ; or un jeton expiré, un téléphone sans réseau et un projet injoignable produisent
tous « pas de session ». Les confondre retirerait de l'écran la progression locale au moment
précis où elle est le seul contenu disponible. Ce fichier est donc aussi ce qui permet
d'ouvrir l'application sans réseau, avec le bon compte.

**Conséquence pour l'utilisateur :** il se reconnecte une fois sur Android. C'est inévitable —
un secret du Keychain n'est pas lisible par une autre application — et c'est immédiat.

---

## Ce que l'utilisateur retrouve, et ce qu'il ne retrouve pas

### Retrouvé automatiquement, dès la première connexion

Tout ce qui vit dans `user_state` :

- maîtrise des versets, sourates connues, hizb, juz ;
- programme d'apprentissage, séances, dates planifiées et dates de réalisation ;
- révisions : cycles, historique, échéances, consolidations J+1 / J+3 / J+7 ;
- versets difficiles, y compris le marqueur posé par le professeur ;
- signets et dernière position de lecture ;
- progression fine par unité d'étude ;
- apparence, thème, police, accent, réglages de lecture, récitant ;
- profil, préférences de notification.

### À refaire une fois, parce que c'est propre à l'appareil

- **se reconnecter** (le secret n'est pas transférable) ;
- **autoriser les notifications** (l'autorisation est accordée par appareil) ;
- **réglages de répétition audio** (locaux dans les deux clients) ;
- **favoris de contenus créés en mode invité** (locaux, sans compte).

### Rien à migrer pour les ressources

Les pages de moushaf et l'audio ne sont pas des données utilisateur : elles sont embarquées ou
téléchargées. Android embarque les pages dans `assets/quran/pages/` et télécharge l'audio dans
son propre dossier, avec son propre cache. Aucun transfert n'est nécessaire ni souhaitable.

---

## Ce que l'application Android ne fait pas au client React Native

Elle ne lit pas, ne modifie pas et ne supprime rien dans l'application React Native. Aucun
accès au système de fichiers de l'autre application n'est tenté, aucune tentative de lecture du
Keychain, aucune migration forcée. Les deux applications cohabitent sur l'appareil sans se
connaître, et se rencontrent uniquement par la base Supabase partagée.
