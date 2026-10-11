# Ressources tierces embarquées, et leurs conditions

Ce fichier ne dit **pas** sous quelle licence le code de ce dépôt est publié : c'est une décision
de son propriétaire. Il dit ce que le paquet **embarque** de tiers, où le trouver, et à quelles
conditions — parce que ces conditions-là ne sont pas un choix, elles s'imposent à qui
redistribue.

Il est là pour une raison précise : les ressources les plus lourdes du paquet viennent d'une
**archive tierce**, et la justification de leur présence vivait jusqu'ici **dans le dépôt
d'origine** — pas ici, où sont les octets. Une déclaration qui ne suit pas les fichiers ne suit
rien.

`tools/verifier-licences.py` tient ce fichier : il refuse de sortir en 0 si une famille de
ressources embarquées n'est pas nommée ci-dessous, si la provenance ne couvre plus les polices
livrées, ou si une dépendance publicitaire apparaît dans le projet.

---

## 1. Polices QCF v4 — `app/src/main/assets/coran-test/`

**606 fichiers, 51 218 693 octets (48,85 Mio)** — 604 polices de page (`1.woff2` … `604.woff2`),
la police des titres de sourate (`surah-name-v4.woff2`) et celle de la basmala
(`vertopal.com_QCF_Bismillah-Regular.woff2`), plus `provenance.json`.

Ce ne sont pas des ornements : **les polices sont le corpus**. Cette source n'affiche pas
d'images — elle compose une page avec les vrais glyphes d'une police par page, et le Tajwid vient
des tables couleur `COLR v0` / `CPAL` de la police, sans recoloration heuristique du texte.

### Provenance

`provenance.json` porte le SHA-256 de l'archive source et de chaque police. L'archive est une
**IPA tierce déchiffrée** :

| | |
|---|---|
| Archive | `com.muath.manar-2026.9.24-eeveedecrypter.ipa` |
| SHA-256 | `c59a7f603fb20a4a4ba9e58cc98bf75210a6d441b67d535d2a8cc1b96b31c884` |
| Format conservé | WOFF2 sans perte, tables `COLR` + `CPAL` préservées |
| Entrées couvertes | **604**, les pages 1 à 604 (`fonts/quran_fonts_qfc4/QCF4NNN_COLOR-Regular.ttf.gz`) |

Les deux polices nommées **n'ont pas d'empreinte source** : le catalogue de l'archive ne les
déclare pas sous la même forme que les 604. C'est un écart **connu**, pas un oubli — il est
déclaré ici et vérifié comme tel, plutôt que comblé par une empreinte inventée.

La déclaration du propriétaire est consignée dans `docs/CORAN_TEST_ANALYSIS.md`, **section
« Sécurité et provenance »**, dans le dépôt d'origine `Msoumaya2019/coran-memoire` :

> « L'utilisateur a confirmé ses droits sur les ressources de l'IPA. La bibliothèque publie du
> code sous MIT ; les ressources QCF/QUL ont leurs propres conditions, documentées par son
> NOTICE. Seules les ressources indépendantes nécessaires au Moushaf ont été importées. »

### Conditions, recopiées de la source qui les énonce

Le code de la bibliothèque d'origine est sous **MIT** ; ce grant **ne couvre pas** les polices.
Les conditions ci-dessous sont celles de son `NOTICE`, et sont recopiées ici parce qu'un renvoi
vers un dépôt tiers ne survit pas à sa disparition.

> **Additional Terms for QCF Fonts and QUL Resources**
>
> The code in this repository is licensed under the MIT License (see LICENSE file). This grant
> does not apply to Quranic fonts (QCF) and any tafsir/translation files or other resources
> obtained from Quranic Universal Library (QUL) or King Fahd Glorious Quran Printing Complex
> (KFGQPC). Those assets are governed by their respective terms. If you bundle or distribute
> these assets with your app, you must comply with the following conditions (summarized from QUL
> guidance):
>
> 1. **No distracting ads**: Apps or projects must not include advertisements (even
>    halal/Islamic ads) that could distract users while reading the Quran.
> 2. **Follow upstream terms**: Always comply with KFGQPC and QUL terms when distributing
>    applications that include or bundle these assets.
>
> References:
> - KFGQPC Developer Portal: <https://qurancomplex.gov.sa/quran-dev/>
> - Quranic Universal Library (QUL): <https://qul.tarteel.ai/>

### Ce que la condition 1 demande, et ce qui est mesuré

La condition « aucune publicité » n'est pas une intention : elle se lit dans les dépendances
déclarées, et c'est le seul chemin par lequel un régie publicitaire peut entrer dans le paquet.

**Mesuré :** aucune coordonnée publicitaire n'est déclarée — ni dans `gradle/libs.versions.toml`,
ni dans un `build.gradle.kts` de module. Le projet déclare Firebase **Messaging** (notifications)
et rien d'autre de la famille Google Play. `tools/verifier-licences.py` refait cette lecture et
fait échouer la vérification si une telle dépendance apparaît.

### Pourquoi ces 48,85 Mio ne se réduisent pas — mesuré, pas supposé

Trois voies avaient été envisagées avant d'ouvrir l'écran immersif : (a) la `WebView` avec les
ressources telles quelles ; (b) une `WebView` avec **une** police au lieu de 604 sous-ensembles ;
(c) un rendu natif Compose des mêmes lignes. La voie (a) a été retenue. Ce qui suit est ce qui
reste à dire contre (b), et il est mesuré sur les fichiers livrés :

| Mesure | Valeur |
|---|---|
| 604 polices de page, sur disque | **51 039 264** octets |
| Leurs tables décompressées | **167 166 086** octets — soit **×3,28** de compression déjà acquise |
| Part des contours de glyphes (`glyf`) | **96,9 %** |
| Part des tables couleur (`COLR` + `CPAL`) | **0,9 %** (1 571 574 octets) |
| Polices de page **sans** `COLR` ou **sans** `CPAL` | **0** sur 604 |

Deux conclusions, et la seconde corrige la première intuition :

1. **Le poids est le dessin, pas la couleur.** Les tables couleur sont négligeables (0,9 %) :
   supprimer le Tajwid ne ferait pas maigrir le paquet. Le poids, ce sont les contours de la
   calligraphie, page par page — et chaque police ne porte **déjà** que les glyphes de sa page.
   Il n'y a donc rien à sous-ensembler davantage : le seul levier restant est de **retirer des
   glyphes**, c'est-à-dire de retirer le rendu.
2. **L'alternative « une seule police » n'est pas équivalente.** Le candidat de la source,
   `UthmanicHafs_V20.woff2` (105 520 octets), ne porte **ni `COLR` ni `CPAL`** : c'est une police
   de texte, pas une police de composition. L'adopter remplacerait le rendu, pas la taille. C'est
   la raison pour laquelle la comparaison « 105 ko contre 48,85 Mo » est fausse, et c'est
   mesurable sur les deux fichiers.

`UthmanicHafs_V20.woff2` n'est **pas** livrée : aucun module de la source ne consomme son export,
et un fichier que personne ne lit ne se justifie pas dans un paquet. L'outil d'import refuse de
terminer si elle disparaît de la source, pour que ce choix ne devienne pas un oubli.

---

## 2. Données du corpus — `core/domain/src/main/resources/quran/`

Les onze fichiers JSON du référentiel et la licence qui les accompagne :

| Fichier | Rôle |
|---|---|
| `TANZIL-LICENSE.txt` | la licence des données textuelles, telle que livrée |
| `import-manifest.json` | l'empreinte de chaque fichier importé |
| `verses.json`, `bounds.json`, `coran_1441-bounds.json`, `tajweed-*.json`, `translation-fr-rashid.json`, … | le corpus |

`tools/verifier-parite-donnees.py` vérifie, à chaque exécution, que ces fichiers sont **octet
pour octet** ceux du dépôt d'origine. Il ne choisit pas la licence à leur place : il constate que
la copie est la bonne.

---

## 3. Polices OFL — `core/design/src/main/res/font/`

Deux polices sous **SIL Open Font License 1.1**, avec leur texte de licence embarqué à côté des
fichiers qu'elles couvrent :

| Police | Fichier de licence |
|---|---|
| Cormorant Garamond (variable, axe `wght` 300..700) | `core/design/src/main/assets/licenses/OFL-cormorantgaramond.txt` |
| Amiri (statique) | `core/design/src/main/assets/licenses/OFL-amiri.txt` |

L'OFL exige que la licence accompagne les fichiers : c'est la raison de leur présence dans
`assets/`, et non dans un dossier de documentation.

---

## Ce que ce fichier ne couvre pas

- Le **code** de ce dépôt, qui n'a pas de licence déclarée ici — c'est un choix de son
  propriétaire, pas une omission de ce fichier.
- Les **sources** des ressources ci-dessus : elles ne sont pas redistribuées, seules les copies
  importées le sont.
- Les bibliothèques tierces du graphe de dépendances (Compose, Media3, Supabase, Ktor…), dont les
  licences sont portées par leurs propres artefacts Maven.
