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
| **B** | Coran, lecteur, cache, audio | **en cours** — 604 pages embarquées, lecteur complet, **écoute verset par verset** avec mini-lecteur, **paquet « Coran 1441 »** (téléchargement, reprise, installation) et son panneau |
| **C** | Apprentissage, révisions, consolidation, programme | à venir |
| **D** | Amis, Quiz, notifications, progrès | à venir |
| **E** | Hors ligne, optimisation, tests, nettoyage | à venir |

Ce qui est **fait et éprouvé** aujourd'hui — **430 tests**, tous verts :

| Module | Tests | Ce qu'ils couvrent |
|---|---|---|
| `core:domain` | **292** | le domaine porté de `src/core/*.ts`, exécuté sur les **vraies données** (6 236 versets, 114 sourates, 604 pages) — dont la géométrie du lecteur, les gestes, la fenêtre de préchargement, les règles de silence entre deux versets, la relecture des préférences d'écoute, et les règles du paquet « Coran 1441 » : décision d'entrée, taille exacte, dimensions d'image, témoin d'installation, **forme de la page selon la source**, **rectangles des versets**, **transition de source**, et **les mots du panneau** |
| `core:data` | **57** | le magasin JSON local, la file hors ligne, la fusion à trois voies, la règle du propriétaire — sur de **vrais fichiers** ; l'installation du paquet 1441 (reprise, témoin écrit en dernier, refus d'une archive douteuse) sur un **vrai ZIP** ; et le transport HTTP contre un **vrai serveur** local, en-tête `Range` compris |
| `feature:home` | **22** | les règles de l'accueil : point de reprise, `scheduledDate` contre `date`, série de jours, période de chaque bandeau, objectif de la semaine |
| `core:design` | **19** | les règles du thème : asymétrie de l'accent, fond secondaire, distinction des cinq palettes, résolution des polices |
| `feature:reader` | **17** | le nommage des pages (une page blanche est une panne silencieuse), les bornes du geste, le repli d'affichage quand une préférence d'écoute est illisible, et **la pose des quinze bandes** d'une page du paquet |
| `core:audio` | **14** | l'enchaînement réel d'une séance : silence technique observé, silence choisi sur les reprises seulement, arrêt en fin de passage, répétition illimitée, changement de récitateur, fin oubliée après fermeture — sur une **horloge virtuelle** |
| `feature:auth` | **9** | l'activation du formulaire de connexion : adresse, longueur du mot de passe, occupation |

`feature:sources` — le module qui porte le téléchargement, le panneau et le choix de présentation — n'a **pas** de tests à lui : il ne fait que disposer à l'écran des règles qui vivent dans `core:domain`, où elles sont éprouvées. C'est le même partage que pour les autres écrans.

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
- **Corpus coranique complet** : les **604 pages** du Coran de Médine (118,2 Mo, mesurés) sont
  embarquées dans `app/src/main/assets/quran/pages/`, à côté des **11 fichiers JSON** du
  référentiel et de la licence TANZIL — le dossier en compte 13, mais `import-manifest.json`
  décrit les autres, il n'en fait pas partie. Les pages sont **octet pour octet** celles de
  `coran-memoire` ; `tools/verifier-parite-donnees.py` le mesure sur les 604 pages et sur chaque
  fichier de données, en lecture seule.
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
- **Paquet « Coran 1441 »** : l'archive de **102 608 011 octets** — 9 060 images, 604 pages ×
  15 lignes — se télécharge, **se reprend** après une coupure et se vérifie avant d'être déclarée
  installée. Le témoin d'installation est écrit **en dernier**, donc sa présence est une preuve et
  non une intention. Une reprise qui reçoit une réponse `200` **jette** le partiel au lieu de s'y
  ajouter : sans cela l'archive aurait la bonne taille et un contenu faux, et rien ne le dirait
  avant l'écran.
- **Panneau de téléchargement et choix de présentation** : le paquet se télécharge depuis un
  panneau qui annonce la phase et le pourcentage, propose **un seul** bouton à la fois, et met
  l'installation en pause dès que l'écran disparaît — un partiel reste lisible, une écriture
  interrompue au milieu d'un fichier non. Le choix de présentation est **gardé par une porte** :
  une source en paquet dont l'installation n'est pas en place **remplace la page** par le panneau
  au lieu d'afficher une page blanche. Et une source n'est **adoptée qu'une fois ses images
  vérifiées** : si la préparation échoue, la présentation précédente est conservée et un message
  le dit.

Le lecteur est un **carrefour** : le bouton « ⋯ » de sa coquille ouvre une feuille d'options, et
c'est de là qu'on change de sourate — les 114 sourates, ou une page précise — et qu'on choisit la
présentation des pages. Une destination dont l'écran n'est pas écrit **n'apparaît pas** dans la
feuille : une ligne grisée laisserait croire que l'écran existe mais qu'il est indisponible.

Ce qui **reste** : l'écran des réglages d'écoute (récitateur, nombre d'écoutes, silence, vitesse
— les valeurs existent déjà et sont appliquées, et c'est la ligne « Réglages audio » de la feuille
qui l'attend), la traduction française, la coquille d'étude (bandeau de séance, marqueurs de
marge), le mode signet, les écrans Programme / Progrès / Amis / Quiz / Profil, et les
notifications.

---

## Compiler

Prérequis : **JDK 17 ou 21**, le **SDK Android** (plateforme 36), et une connexion à Maven
Central la première fois.

```bash
# Le JDK de l'IDE Android convient :
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew test                                   # toute la suite, toutes variantes
python tools/compter-tests.py                    # le total, lu dans les rapports du coureur
python tools/falsifier.py                        # les tests détectent-ils ce qu'ils annoncent ?
python tools/falsifier.py --verifier             # les cas sont-ils encore jouables ? (sans Gradle)
./gradlew lint                                   # ce que les tests ne voient pas
./gradlew :app:assembleDebug                     # l'APK de test
./gradlew :app:assembleRelease                   # l'APK de version (minifié)
```

`test` et non `testDebugUnitTest` : `core:domain` est un module Kotlin/JVM pur, il n'a pas de
variante Android et sa tâche s'appelle `test`. Lancer la seconde laisse ses 292 tests hors de
la mesure.

Le chemin du SDK se règle dans `local.properties` (`sdk.dir=...`).

> **Le lint sort en `BUILD SUCCESSFUL` avec 22 avertissements, et il faut le savoir avant de
> croire à un zéro.** Mesuré sur les 14 rapports `build/reports/lint-results-*.xml` : 22
> avertissements, **tous** de la famille « une version plus récente est disponible » — 3 sur le
> plugin Android et le lanceur Gradle, 19 sur les dépendances du catalogue. **Aucun** ne porte sur
> du code. Ils sont laissés en place volontairement : passer à AGP 9.4.1 est une migration, pas
> une mise à jour. Le chiffre est noté ici pour qu'un relevé **supérieur** se remarque : sans
> repère, vingt-deux avertissements de bruit finissent par cacher le vingt-troisième.

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

Cinq scripts, dans `tools/`, servent à **prouver** ce que la documentation affirme. Ils ne
supposent rien : ils mesurent.

| Script | Ce qu'il établit |
|---|---|
| `compter-tests.py` | le nombre de tests réellement exécutés, lu dans les rapports XML. Ne compte qu'une variante par classe : additionner `**/build/test-results/**` compte chaque test deux fois (debug **et** release) et oublie les modules JVM purs, qui écrivent sous `test-results/test/` |
| `falsifier.py` | qu'un test **détecte** ce qu'il prétend couvrir. Chaque cas casse volontairement une règle, joue la suite, et vérifie que les tests qui tombent sont ceux prévus — puis **restaure la source** et le prouve par empreinte. Seuls les rapports écrits après le lancement sont lus : un rapport périmé ferait passer un test supprimé pour vert. `--verifier` contrôle que les 22 cas sont encore jouables, sans lancer Gradle |
| `verifier-parite-donnees.py` | que les **604 pages** et les **11 fichiers de données** embarqués sont **octet pour octet** ceux de `coran-memoire` — et que l'empreinte du manifeste d'import décrit bien les fichiers présents. À lancer avec `--source <copie locale de coran-memoire>`, ouverte en lecture seule |
| `verifier-jetons-design.py` | que les 48 couleurs et les 5 jeux de pastilles sont **identiques** à ceux de `coran-memoire`. Même `--source`, même lecture seule |
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
