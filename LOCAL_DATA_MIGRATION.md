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
