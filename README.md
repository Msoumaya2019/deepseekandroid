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
| **B** | Coran, lecteur, cache, audio | **en cours** — 604 pages embarquées, lecteur complet, **écoute verset par verset** avec mini-lecteur, **paquet « Coran 1441 »** (téléchargement, reprise, installation) et son panneau, et **l'écran immersif « Coran Test »** : la page est peinte par sa police et mesurée par le document lui-même, et **la mémoire du lecteur** — il rouvre là où l'on s'est arrêté, et la sortie enregistre la position, et **la séance d'étude** : le lecteur porte le bandeau de la séance — titre, progression, page — et la feuille de validation, qui propose son point d'arrêt et **écrit** la progression ; l'accueil ouvre la séance du jour, et ses trois arguments voyagent **ensemble** dans la route, faute de quoi la séance s'ouvrirait sans bornes |
| **C** | Apprentissage, révisions, consolidation, programme | **en cours** — le **programme** autonome (séance du jour, objectif, reprises, rattrapage, séances à venir, historique), dont le calcul est pur et **falsifié**, et la feuille de validation de séance dans le lecteur ; le tableau de bord des révisions, l'écran d'objectif et la barre d'action de révision restent à faire |
| **D** | Amis, Quiz, notifications, progrès | à venir |
| **E** | Hors ligne, optimisation, tests, nettoyage | à venir |

Ce qui est **fait et éprouvé** aujourd'hui — **946 tests**, tous verts :

| Module | Tests | Ce qu'ils couvrent |
|---|---|---|
| `core:model` | **3** | les clés persistées des sources : le `@SerialName` de chacune est **lu sur le descripteur** au lieu d'être recopié dans une seconde table, et les huit valeurs sont figées par un test — ce sont elles que le client React Native écrit dans `sourcePages`, donc les renommer romprait la relecture d'un état déjà synchronisé |
| `core:domain` | **607** | le domaine porté de `src/core/*.ts`, exécuté sur les **vraies données** (6 236 versets, 114 sourates, 604 pages) — dont la géométrie du lecteur, les gestes, la fenêtre de préchargement, les règles de silence entre deux versets, la relecture des préférences d'écoute, **les libellés des écrans du lecteur** (titre de la feuille aligné sur la ligne qui l'ouvre, vitesses en virgule française, refus de la saisie libre), et les règles du paquet « Coran 1441 » : décision d'entrée, taille exacte, dimensions d'image, témoin d'installation, **forme de la page selon la source**, **rectangles des versets**, **transition de source**, et **les mots du panneau**, et **la traduction française du panneau** : la plage est ramenée au corpus au lieu de faire tomber l'écran, l'accord des 6 236 versets avec la traduction est vérifié un par un, et le titre du panneau est celui de la ligne qui l'ouvre, et **les marques du lecteur** — ce qui compte comme marqué, et **quelle couleur l'emporte** quand un verset en porte deux, et **les règles de l'écran des signets** — une ligne bâtie sur le verset et non sur les champs enregistrés du signet, un signet hors corpus **omis** au lieu de faire tomber l'écran, « Dernière reprise » **retriée** au lieu d'être lue en tête de liste, et la page suivant le **découpage de la source affichée** (56 versets sur 6 236 changent de page entre Médine et le paquet 1441), et **le toucher de la page** — le dé-zoom puis le dé-centrage, sans quoi un appui sur une page agrandie désigne un verset d'autant plus éloigné que l'agrandissement est fort, et **les décisions du panneau des marques-pages** — quelles entrées il propose, et quelle notice l'emporte quand la pose et la confirmation se chevauchent, et **les mots du panneau des actions d'un verset** — le libellé du marquage suit le marqueur de l'**élève** seul, alors que la teinte de la page compte aussi celui du professeur, parce que la bascule ne touche jamais à ce dernier, et **les entrées de ce panneau** — une action sans destination est retirée au lieu d'être grisée, et « Sélectionner un passage » l'est faute de geste porté, et **la préférence de récitateur** — l'état du compte prime sur la mémoire de l'appareil, cette mémoire est tenue **par utilisateur** et non globalement, et le report vers le compte est **à sens unique** et conditionné à l'absence de choix du côté du compte, et **l'écran immersif du paquet « Coran Test »** — la table des pages de composition (604 pages, celle-là même que le client React Native écrit dans `testPage`), le chargeur de ses deux fichiers de données, et le document qui peint la page et **se mesure lui-même** —, et **la mémoire bornée** de cinq documents : la page qu'on vient de lire est celle qui survit à l'éviction suivante, et c'est la page jamais relue qui tombe —, et **les décisions de l'écran immersif** : un message ne vient pas de n'importe quelle page — une page qui s'annonce n'est crue que si elle nomme la page **demandée**, une panne n'est crue que si elle vient de la page **en cours**, et un appui, un appui long ou un balayage ne comptent que sur la page **affichée** —, et **la surimpression** : la clé d'un verset est « sourate:verset » et non son identifiant global, une marque hors du corpus est **omise** au lieu de faire tomber l'écran, et le texte produit ne contient que des caractères sûrs dans un littéral JavaScript, et **le découpage de la source affichée** — les deux sources s'accordent à la première page et divergent ensuite : 36 pages sur 604, à partir de la page 120, et **le découpage des pages d'étude** — les trois règles qui résolvent une page d'étude suivent, elles aussi, la source affichée : la page d'un verset, la plage d'une page et la dernière page d'un verset ; et la clé d'étude ne replie plus la source composée sur le moushaf — l'hypothèse qui avait expiré le jour où cette source a reçu son découpage, et qui annonçait sinon la page de Médine pour un verset qui n'y est pas, et **la mémoire du lecteur** — la page quittée rejoint les pages lues, la dernière lecture retient un verset **choisi** : celui déjà noté s'il tient encore sur la page, sinon celui de l'ouverture, sinon le premier de la page ; la page n'est conservée pour la source composée **que** pour elle, et l'état est migré **avant** la décision — sans quoi la page serait mémorisée pour une source qui n'en a jamais reçu —, et **la page d'ouverture** d'une source en images, qui **reprojette** le dernier verset lu au lieu de relire une page écrite dans un autre découpage, et **la séance d'étude** : ses trois plages — **demandée** (l'adresse), **ouverte** (le reste d'une reprise interrompue) et **prévue** (la séance de l'état, ou l'enregistrement d'une révision) — que confondre ferait perdre toutes les validations suivantes **en silence**, puisque l'écriture est refusée quand les bornes diffèrent de celles de la plage ; le dernier verset validé, qui vaut `début - 1` et non `début`, sans quoi le premier verset ne serait jamais compté comme appris ; la garde de la **lecture libre**, qui n'écrit rien et se rend **telle quelle** — vérifiée par identité de référence, parce que deux requêtes égales passeraient pour la même ; le bandeau de séance (son titre, sa ligne de progression **absente** pour une consolidation, et son critère de `Page`/`Pages` qui n'est **pas** celui de la feuille) ; la feuille de validation (le point d'arrêt proposé, la plage validée, les deux libellés) ; et les consolidations J+1, J+3, J+7, dont la première **incomplète** est celle qui s'annonce, et **le compte des repères de marge** — les versets faits de la plage **prévue**, borné des deux côtés : un dernier verset validé au-delà de la plage ne compte pas plus de versets qu'il n'y en a, et un dernier verset validé avant elle n'en compte pas un négatif |
| `core:data` | **73** | le magasin JSON local, la file hors ligne, la fusion à trois voies, la règle du propriétaire — sur de **vrais fichiers** ; **le dépôt des réglages d'écoute** (premier démarrage, document complet, champ hors bornes isolé, document illisible mis de côté, relecture depuis le disque, échec d'écriture qui ne publie rien) ; l'installation du paquet 1441 (reprise, témoin écrit en dernier, refus d'une archive douteuse) sur un **vrai ZIP** ; le transport HTTP contre un **vrai serveur** local, en-tête `Range` compris ; et le **branchement du conteneur**, tenu par un contrôle de forme, et **le dépôt des réglages d'écoute qui retient le propriétaire du récitateur** (relecture depuis le disque, document sans propriétaire, réécriture seulement si la valeur change) |
| `feature:home` | **23** | les règles de l'accueil : point de reprise, `scheduledDate` contre `date`, série de jours, période de chaque bandeau, objectif de la semaine, et **la page annoncée**, qui suit le découpage de la source affichée (le verset 5:77 est en page 120 dans la composition et en page 121 à Médine) |
| `core:design` | **23** | les règles du thème : asymétrie de l'accent, fond secondaire, distinction des cinq palettes, résolution des polices |
| `feature:reader` | **81** | le nommage des pages (une page blanche est une panne silencieuse), les bornes du geste, **ce que le mini-lecteur annonce** (le nombre d'écoutes tel que le moteur le jouera, saisie abîmée comprise), et **la pose des quinze bandes** d'une page du paquet, et **le branchement du panneau de traduction** (contrôle de forme : la ligne de la feuille l'ouvre, ses lignes sont calculées, et il est rendu), et **le branchement des marques** (contrôle de forme : les deux ensembles reçus par le lecteur, et transmis à la page), et **le branchement du mode de pose des signets** (contrôle de forme : le bouton de la coquille et sa marque d'activité, le panneau rendu, l'armement qui le referme, la garde du point qui ne désigne rien, et le verset rapporté à l'appelant), et **le branchement du panneau des actions du verset** (contrôle de forme : l'appui long l'ouvre, la fiche lui cède la place, « Écouter » joue la plage d'**un seul** verset avec les trois réglages de l'original, « Répéter » ouvre les réglages d'écoute sans lancer, le marquage ne referme rien, et « Sélectionner un passage » reste absent faute de destination), et **l'écran des marques-pages** (contrôle de forme : une **fenêtre** et non un remplacement — le lecteur reste monté, donc l'écoute en cours ne s'arrête pas —, la confirmation avant la suppression, le texte coranique borné à deux lignes, et la vue qui ne calcule aucune ligne), et **la reprise d'un signet dans le lecteur** (contrôle de forme : le verset demandé est désigné, et le panneau quitté est refermé, comme le `setSessionPanel(null)` du source), et **le document immersif côté vue** — le nom de police d'une page (le numéro **sans** complément sur trois chiffres, faute de quoi chaque page s'afficherait en carrés vides) et les trois polices qu'une page demande, et **la couleur de papier** : la forme à six chiffres que le document valide, l'alpha **écarté**, et les quatre papiers de l'application qui font l'aller-retour, et **le suivi d'une page demandée de l'extérieur** (contrôle de forme : le lecteur obéit à la page qu'on lui demande au lieu de n'obéir qu'à sa première composition — sans quoi l'appelant enregistrerait une position que personne n'a vue —, sans rejouer au premier rendu, et sans effacer le verset désigné d'une reprise), et **la coquille d'étude du lecteur** (contrôle de forme : le bandeau n'existe **que** si la validation est branchée — sinon il s'annoncerait comme une séance dont le geste n'ouvrirait rien —, il est posé **dans** la colonne et **avant** la page, la feuille reçoit la **page affichée** pour proposer son point d'arrêt, et elle est gardée par la même condition que le bandeau), et **les trois champs de séance du document immersif** (contrôle de forme : la plage prévue, le compte des versets faits et la teinte transmis **ensemble** — les trois ont une valeur par défaut vide, donc une omission ne se verrait nulle part), et **les repères de marge du lecteur standard** (contrôle de forme : la règle du domaine appelée, les groupes transmis à la page, et la gouttière reçue — sans quoi les pastilles seraient placées sans savoir de quelle place la fenêtre dispose) |
| `feature:program` | **42** | le programme : la séance du jour et ses **deux formes de repli**, le repli **non symétrique** de la séance du jour (une séance en retard n'est pas « à venir »), les **trois périodes**, la ligne à venir, le rattrapage, l'historique qui **renverse** au lieu de trier, les reprises (le **reste** de la séance, et le refus d'une révision), les deux formes de la ligne de validation, les trois états d'une ligne, l'objectif et ses **douze** rythmes, et la carte de révision qui disparaît quand les révisions sont éteintes |
| `core:audio` | **16** | l'enchaînement réel d'une séance : silence technique observé, silence choisi sur les reprises seulement, arrêt en fin de passage, répétition illimitée, changement de récitateur, fin oubliée après fermeture, **réglages appliqués à la séance en cours**, **saisie d'écoutes illisible qui ne fige pas la séance** — sur une **horloge virtuelle** |
| `feature:auth` | **9** | l'activation du formulaire de connexion : adresse, longueur du mot de passe, occupation |
| `navigation` | **69** | le calcul des marques par la route du lecteur (contrôle de forme : l'état du compte observé, les deux règles du domaine appelées, les deux ensembles transmis), et **l'écriture d'un signet par la route** (contrôle de forme : la règle du domaine appelée, le magasin de l'état et non une copie, la page de la **source affichée**, et l'écriture protégée), et **l'écriture du marqueur de difficulté par la route** (contrôle de forme : la règle du domaine appelée, le sous-ensemble de l'élève calculé par la règle du domaine et **non** confondu avec les versets difficiles — les deux diffèrent dès qu'un professeur marque un verset —, ce sous-ensemble transmis au lecteur, le magasin de l'état, et l'écriture protégée), et **l'écran des marques-pages par la route** (contrôle de forme : la condition **et** l'appel, les lignes de la règle du domaine, la page de la **source affichée** pour la reprise, la suppression, le verset rappelé au lecteur, la remise à zéro du verset en attente, et l'écran posé **par-dessus** un lecteur qui reste monté — sans `return` entre les deux), et **la préférence de récitateur par la route** (contrôle de forme : la résolution appelée avec ses quatre sources, la mémoire locale écrite avec son propriétaire, le report au compte conditionné, et le récitateur initial transmis), et **la mémoire du lecteur par la route** (contrôle de forme : le verset d'ouverture déclaré, la règle du domaine appelée, l'écriture **avant** la fermeture — sinon la portée meurt avec l'écran et l'écriture est annulée en vol, pendant que l'écran se ferme normalement —, le retour système branché sur la même sortie, et la page d'ouverture tenue par la règle du domaine), et **les règles de route du lecteur**, éprouvées **pour de vrai** (un argument facultatif, la base d'une route paramétrée, et le plein écran reconnu **derrière** l'argument : la pile rend le motif, et non la route concrète), et **la porte du lecteur depuis l'accueil** (contrôle de forme : c'est la seule entrée du lecteur, et sa disparition le rendrait inatteignable sans qu'aucun autre contrôle ne le dise), et **la séance d'étude par la route** (contrôle de forme : la page d'ouverture suit la séance au lieu de la lecture mémorisée, le bandeau et la plage sont résolus par le domaine, la source est **repliée** par `sourceKey` et non à la main, la validation **écrit puis ferme** — et la mesure est bornée au **chemin d'écriture**, faute de quoi le `quitter()` de la garde passait pour une fermeture prématurée sur un code juste —, et le verset retenu suit la séance) |

`feature:sources` — le module qui porte le téléchargement, le panneau et le choix de présentation — n'a **pas** de tests à lui : il ne fait que disposer à l'écran des règles qui vivent dans `core:domain`, où elles sont éprouvées. C'est le même partage que pour les autres écrans. `navigation` en a soixante-neuf : cinquante-huit **contrôles de forme** — ce module ne fait que relier, et ce qu'il relie ne s'exécute pas hors d'un appareil — et onze qui s'exécutent **pour de vrai**, parce que les règles de route sont du Kotlin pur, sans dépendance Android. Ils vivent là, et non ailleurs, parce qu'un contrôle qui lit le source d'un autre module ne serait **pas rejoué** quand ce source change — il resterait vert par oubli.

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
  Ces réglages se changent depuis la feuille « Réglages audio » du lecteur, et **s'appliquent à la
  séance en cours** — changer le nombre d'écoutes pendant une leçon vaut pour cette leçon, sans la
  relancer et sans couper le verset qui joue. Ils sont **enregistrés sur l'appareil**, hors des comptes, et relus au démarrage de
  l'application. Le mini-lecteur est **dans le flux**, lui aussi : la page reste entière. La décision
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
c'est de là qu'on change de sourate — les 114 sourates, ou une page précise —, qu'on règle
l'écoute (récitateur, nombre d'écoutes, mode, vitesse, silence) et qu'on choisit la présentation
des pages. Les réglages d'écoute **s'appliquent à la séance en cours** : changer le nombre
d'écoutes pendant une leçon vaut pour cette leçon, sans la relancer, et sans couper le verset qui
joue. Une destination dont l'écran n'est pas écrit **n'apparaît pas** dans la feuille : une ligne
grisée laisserait croire que l'écran existe mais qu'il est indisponible.

Ce qui **reste** : la coquille d'étude (bandeau de séance, marqueurs de
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
variante Android et sa tâche s'appelle `test`. Lancer la seconde laisse ses 610 tests hors de
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
| `falsifier.py` | qu'un test **détecte** ce qu'il prétend couvrir. Chaque cas casse volontairement une règle, joue la suite, et vérifie que les tests qui tombent sont ceux prévus — puis **restaure la source** et le prouve par empreinte. Seuls les rapports écrits après le lancement sont lus : un rapport périmé ferait passer un test supprimé pour vert. `--verifier` contrôle que les 157 cas sont encore jouables, sans lancer Gradle |
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
