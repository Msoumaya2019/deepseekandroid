# deepseekandroid

Application **Android native** de mémorisation du Coran, écrite en **Kotlin** et **Jetpack Compose**,
qui partage le **même projet Supabase** que l'application React Native existante
(`Msoumaya2019/coran-memoire`).

Ce dépôt est **indépendant**. Il ne modifie pas, ne référence pas et ne dépend pas du dépôt
React Native : les deux clients se rencontrent uniquement par la base partagée, ce qui laisse
libre de conserver l'un, l'autre, ou les deux.

---

## État du projet

| Phase | Contenu | État |
|---|---|---|
| **A** | Analyse, architecture, Supabase, authentification, navigation, accueil | livrée — porte d'entrée, coquille de navigation et accueil |
| **B** | Coran, lecteur, cache, audio | **en cours** — 604 pages embarquées, lecteur complet, **écoute verset par verset** avec mini-lecteur |
| **C** | Apprentissage, révisions, consolidation, programme | à venir |
| **D** | Amis, Quiz, notifications, progrès | à venir |
| **E** | Hors ligne, optimisation, tests, nettoyage | à venir |

Ce qui est **fait et éprouvé** aujourd'hui — **312 tests**, tous verts :

| Module | Tests | Ce qu'ils couvrent |
|---|---|---|
| `core:domain` | **211** | le domaine porté de `src/core/*.ts`, exécuté sur les **vraies données** (6 236 versets, 114 sourates, 604 pages) — dont la géométrie du lecteur, les gestes, la fenêtre de préchargement, les règles de silence entre deux versets et la relecture des préférences d'écoute |
| `core:data` | **26** | le magasin JSON local, la file hors ligne, la fusion à trois voies, la règle du propriétaire — sur de **vrais fichiers** |
| `feature:home` | **22** | les règles de l'accueil : point de reprise, `scheduledDate` contre `date`, série de jours, période de chaque bandeau, objectif de la semaine |
| `core:design` | **19** | les règles du thème : asymétrie de l'accent, fond secondaire, distinction des cinq palettes, résolution des polices |
| `core:audio` | **14** | l'enchaînement réel d'une séance : silence technique observé, silence choisi sur les reprises seulement, arrêt en fin de passage, répétition illimitée, changement de récitateur, fin oubliée après fermeture — sur une **horloge virtuelle** |
| `feature:reader` | **11** | le nommage des pages (une page blanche est une panne silencieuse), les bornes du geste, et le repli d'affichage quand une préférence d'écoute est illisible |
| `feature:auth` | **9** | l'activation du formulaire de connexion : adresse, longueur du mot de passe, occupation |

En détail :

- **Domaine complet** porté depuis `src/core/*.ts`, en Kotlin pur, sans dépendance Android.
- **Couche de données** : magasin JSON local, chiffrement du jeton de session par le magasin de
  clés Android, accès Supabase, file d'attente hors ligne, fusion à trois voies.
- **Composition de synchronisation** (`AccountSync`), la pièce où une erreur coûte des données,
  couverte par 7 tests dont un **témoin** qui prouve que la composition est nécessaire.
- **Jetons et composants de design** : les cinq palettes, les quatre accents et les échelles de
  `tokens.ts`, avec les polices OFL embarquées. La fidélité au dépôt d'origine n'est pas
  supposée : `tools/verifier-jetons-design.py` compare les 48 couleurs clé par clé au source
  React Native, et ce contrôle a lui-même été falsifié pour prouver qu'il détecte un écart.
- **Coquille de navigation** : les cinq onglets, la barre basse, la barre supérieure avec sa
  rangée d'onglets et sa forme « retour », et les règles qui décident quelles barres sont
  visibles selon la route.
- **Porte d'entrée** : l'écran d'attente, l'écran de bienvenue, le formulaire de connexion et
  d'inscription, et la règle qui refuse d'ouvrir un compte dont l'état n'a pas encore été
  rapatrié. C'est cette dernière qui empêche un programme vide d'être poussé au serveur.
- **Écran d'accueil** : bande d'en-tête, « Continuer ma lecture », les deux tâches du jour,
  « Ma semaine ». Le calcul est séparé du `ViewModel` — il est pur, donc éprouvable sans
  coroutine ni horloge — et **falsifié** : trois mutations de ce calcul ont été détectées par
  exactement le test prévu.
- **APK de test et APK de version** produits : `:app:assembleDebug` et `:app:assembleRelease`.
  Le référentiel coranique et les polices sont **vérifiés dans le binaire**, pas supposés présents.
- **Intégration continue** : `.github/workflows/android.yml` — tests, lint et APK de test. Elle
  reste **verte sans aucun secret** ; si le dépôt porte `SUPABASE_ANON_KEY`, l'APK publié se
  connecte au projet, et la clé ne passe jamais par l'historique Git.
- **Corpus coranique complet** : les **604 pages** du Coran de Médine (114 Mo) sont embarquées
  dans `app/src/main/assets/quran/pages/`, à côté des 12 fichiers JSON du référentiel.
- **Lecteur de moushaf** : page centrée dans l'espace sûr (barres système, découpes, navigation
  par geste), jamais déformée ; **un seul** gestionnaire de gestes, donc aucun conflit entre
  balayage, pincement, appui et appui long ; **trois pages en mémoire au maximum** ; coquille
  discrète **dans le flux**, qui ne recouvre jamais le dernier verset ; fiche du verset à
  l'appui long, avec la traduction française du sens. Il s'ouvre **en avion**.
- **Écoute verset par verset** : sept récitateurs, répétition du passage ou de chaque verset,
  1 à 999 écoutes ou sans fin, silence réglable entre deux écoutes, vitesse 0,75× / 1× / 1,25×.
  Le mini-lecteur est **dans le flux**, lui aussi : la page reste entière. La décision
  d'enchaînement est dans le domaine, donc éprouvée ; le silence technique de 200 ms entre deux
  versets est **observé par un test**, pas attendu.

Ce qui **reste** : le téléchargement de la source « Coran 1441 », l'écran des réglages d'écoute
(récitateur, nombre d'écoutes, silence, vitesse — les valeurs existent déjà et sont appliquées),
la coquille d'étude (bandeau de séance, marqueurs de marge), le mode signet, le sélecteur de
sourate, les écrans Programme / Progrès / Amis / Quiz / Profil, et les notifications.

---

## Compiler

Prérequis : **JDK 17 ou 21**, le **SDK Android** (plateforme 36), et une connexion à Maven
Central la première fois.

```bash
# Le JDK de l'IDE Android convient :
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew test                                   # toute la suite, toutes variantes
python tools/compter-tests.py                    # le total, lu dans les rapports du coureur
./gradlew lint                                   # ce que les tests ne voient pas
./gradlew :app:assembleDebug                     # l'APK de test
./gradlew :app:assembleRelease                   # l'APK de version (minifié)
```

`test` et non `testDebugUnitTest` : `core:domain` est un module Kotlin/JVM pur, il n'a pas de
variante Android et sa tâche s'appelle `test`. Lancer la seconde laissait ses 153 tests hors de
la mesure.

Le chemin du SDK se règle dans `local.properties` (`sdk.dir=...`).

> **Un build vert ne prouve pas que les tests ont tourné.** Le compte se lit dans les rapports
> XML du coureur, pas dans la sortie de Gradle : `tools/compter-tests.py` existe pour cela, et il
> dit aussi combien de classes il a vues — un relevé vide signalerait que le motif n'a désigné
> aucun test. Il **refuse** de donner un total quand les variantes `debug` et `release` d'une
> même classe n'annoncent pas le même nombre de tests : c'est le signe qu'un rapport est périmé.

La même suite tourne en intégration continue (`.github/workflows/android.yml`) : tests, lint, et
APK de test joint à l'exécution. Aucun secret n'y est nécessaire — sans clé Supabase,
l'application se compile en mode hors ligne.

---

## Outils de vérification

Trois scripts, dans `tools/`, servent à **prouver** ce que la documentation affirme. Ils ne
supposent rien : ils mesurent.

| Script | Ce qu'il établit |
|---|---|
| `compter-tests.py` | le nombre de tests réellement exécutés, lu dans les rapports XML. Ne compte qu'une variante par classe : additionner `**/build/test-results/**` compte chaque test deux fois (debug **et** release) et oublie les modules JVM purs, qui écrivent sous `test-results/test/` |
| `verifier-jetons-design.py` | que les 48 couleurs et les 5 jeux de pastilles sont **identiques** à ceux de `coran-memoire`. À lancer avec `--source <copie locale de coran-memoire>`, ouverte en lecture seule |
| `import-quran-assets.mjs` | l'import des données coraniques et des pages du moushaf depuis la même copie en lecture seule |

---

## Configurer Supabase

L'application fonctionne **sans configuration** : tout le Coran, la progression, le programme et
les réglages sont locaux. Seule la synchronisation est alors indisponible, et elle le dit.

Pour l'activer, renseigner la clé **publique** du projet dans `local.properties` :

```properties
supabase.url=https://npbwnvrqmajwqtnncuyv.supabase.co
supabase.anonKey=<clé anon / publishable>
```

> **La clé `service_role` ne doit jamais figurer ici.** Elle contourne les politiques RLS :
> quiconque l'extrairait du binaire aurait un accès total à toutes les données de tous les
> utilisateurs. Seule la clé `anon` a sa place dans un client.

### Pour l'intégration continue

`local.properties` n'est pas versionné, donc l'exécutant de la CI ne l'a pas. La clé y est fournie
par un **secret de dépôt** — jamais par un fichier :

```bash
gh secret set SUPABASE_ANON_KEY --repo Msoumaya2019/deepseekandroid --body "<clé publishable>"
gh secret set SUPABASE_URL      --repo Msoumaya2019/deepseekandroid --body "https://npbwnvrqmajwqtnncuyv.supabase.co"
```

Sans ces secrets, le flux reste **vert** : l'APK publié se compile simplement en mode hors ligne.
Avec eux, il se connecte. Dans les deux cas la clé reste hors de l'historique Git.

### Ce qui a été vérifié contre le serveur

| Contrôle | Résultat |
|---|---|
| `/auth/v1/health` avec la clé | `200` — la clé est acceptée |
| Le même appel **sans** clé | `401` — le refus est bien dû à l'absence de clé |
| Inscription avec un mot de passe de 3 caractères | `422 weak_password`, « at least 6 characters » — la longueur minimale du serveur est **6**, celle de `AuthInput.MIN_PASSWORD` |
| `select` sur `user_state` en rôle `anon` | `42501 permission denied` — conforme au schéma d'origine, qui révoque tout à `anon` |

---

## Organisation du dépôt

```
app/                    point d'entrée Android, injection des dépendances, thème
core/model/             types persistés et sérialisables (aucune dépendance Android)
core/domain/            règles métier en Kotlin pur — programme, révisions, Coran, audio, fusion
core/data/              stockage local, chiffrement, Supabase, dépôts
core/design/            jetons de design et composants Compose
navigation/             graphe de navigation
feature/auth/           la porte d'entrée : écran d'attente, bienvenue, connexion
feature/home|reader|program|progress|social|quiz|profile/
```

`core/model` et `core/domain` sont des modules **Kotlin/JVM purs** : ils ne connaissent ni
Android ni Supabase, et se testent donc en quelques secondes sur la machine de développement.
Voir [ARCHITECTURE.md](ARCHITECTURE.md) pour les raisons de ce découpage.

---

## Documentation

| Fichier | Contenu |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | modules, couches, flux de données, décisions et leurs raisons |
| [ANDROID_MIGRATION.md](ANDROID_MIGRATION.md) | fonctionnalité par fonctionnalité : source React Native, équivalent Android, état |
| [SUPABASE_COMPATIBILITY.md](SUPABASE_COMPATIBILITY.md) | le backend partagé, ce qui est lu, ce qui est écrit, ce qui ne doit pas changer |
| [LOCAL_DATA_MIGRATION.md](LOCAL_DATA_MIGRATION.md) | données locales du client React Native et leur équivalent Android |

---

## Règle de sécurité du dépôt

Le dépôt `Msoumaya2019/coran-memoire` est en **lecture seule** : il est analysé, jamais modifié.
Aucun secret (clé `service_role`, mot de passe, clé privée, jeton, certificat, clé de signature,
secret Google) n'est versionné ici. `local.properties` et `google-services.json` sont ignorés.
