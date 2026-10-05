#!/usr/bin/env node
/**
 * Import des ressources de l'ecran immersif « Coran avec regles de Tajwid » depuis une copie
 * locale de `coran-memoire`.
 *
 * Le depot `Msoumaya2019/coran-memoire` est STRICTEMENT en lecture seule : ce script ne fait
 * que lire. Il copie vers `deepseekandroid` :
 *
 *   1. les donnees de page et l'index des versets -> core/domain/src/main/resources/coranTest/
 *   2. les polices couleur QCF v4 et leur provenance -> app/src/main/assets/coran-test/
 *
 * ## Pourquoi les donnees de page sont dans `core:domain`
 *
 * Les regles du decoupage (`testPageRange`, `testVersePage`) se derivent de `verse-index.json`,
 * et le generateur de document lit une page. Les placer dans `src/main/resources/` les rend
 * lisibles **aussi bien par les tests JVM que par l'application**, sans dupliquer 4,25 Mo — la
 * meme raison qui a mene `verses.json` dans `core/domain/src/main/resources/quran/`.
 *
 * ## Pourquoi les polices sont dans `assets/`
 *
 * Elles sont chargees par la WebView, qui n'a pas acces au classpath : il lui faut des fichiers
 * d'`assets/`. Chaque page porte sa propre police : le Tajwid vient des tables couleur
 * `COLR v0` / `CPAL` de la police, et non d'une recoloration du texte.
 *
 * ## Une police de la source est volontairement NON importee
 *
 * Le catalogue de l'original declare quatre polices nommees ; trois seulement y sont consommees,
 * et **deux** le sont par l'ecran immersif. `UthmanicHafs_V20.woff2` (105 520 octets) est
 * exportee par `coranTest/resources.ts` mais aucun module n'importe cet export : la police arabe
 * reellement utilisee ailleurs vient de `theme/fonts.ts`, ou c'est Amiri. Un fichier que
 * personne ne lit ne se justifie pas dans un paquet livre. Le script refuse neanmoins de
 * terminer si cette troisieme police disparait de la source — sans quoi le choix de l'ecarter
 * deviendrait un oubli qu'on ne verrait pas.
 *
 * ## Provenance et droits
 *
 * `provenance.json` est copie avec les polices : il porte le SHA-256 de l'archive source et de
 * chaque police, et c'est lui qui documente l'origine. L'analyse d'origine
 * (`docs/CORAN_TEST_ANALYSIS.md`) etablit que le proprietaire du projet a confirme ses droits
 * sur ces ressources, les ressources QCF/QUL ayant leurs propres conditions.
 *
 * Usage :
 *   node tools/import-coran-test-assets.mjs --source <chemin vers coran-memoire>
 *   node tools/import-coran-test-assets.mjs --source <...> --data-only
 *   node tools/import-coran-test-assets.mjs --source <...> --fonts-only
 *
 * Par defaut, tout est copie : 604 pages + 2 fichiers de donnees (4,25 Mo) et 606 polices +
 * leur provenance (48,85 Mo). Le script **refuse** de terminer si un compte ne tombe pas juste.
 */

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');

const DATA_SOURCE = 'src/coranTest/data';
const FONTS_SOURCE = 'assets/coran-test';

const DATA_TARGET = path.join(ROOT, 'core', 'domain', 'src', 'main', 'resources', 'coranTest');
const FONTS_TARGET = path.join(ROOT, 'app', 'src', 'main', 'assets', 'coran-test');

/** Les 604 pages du moushaf, plus les deux fichiers qui ne portent pas de numero. */
const PAGES = 604;
const DATA_EXTRAS = ['verse-index.json', 'ornament.json'];
const FONT_EXTRAS = ['provenance.json'];

/** Les deux polices nommees reellement consommees : le titre de sourate et la basmala. */
const NAMED_FONTS = ['surah-name-v4.woff2', 'vertopal.com_QCF_Bismillah-Regular.woff2'];

/** Celle qui est ecartee, nommee pour que son absence soit un choix verifie et non un oubli. */
const NAMED_FONT_UNUSED = 'UthmanicHafs_V20.woff2';

function fail(message) {
  console.error(`\n  ERREUR : ${message}\n`);
  process.exit(1);
}

function readArgs(argv) {
  const args = { source: null, dataOnly: false, fontsOnly: false };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--source') {
      args.source = argv[i + 1];
      i += 1;
    } else if (arg === '--data-only') {
      args.dataOnly = true;
    } else if (arg === '--fonts-only') {
      args.fontsOnly = true;
    } else {
      fail(`argument inconnu : ${arg}`);
    }
  }
  if (!args.source) fail('--source <chemin vers coran-memoire> est obligatoire');
  if (!fs.existsSync(args.source)) fail(`la source n'existe pas : ${args.source}`);
  return args;
}

/** Copie un fichier et rend son nombre d'octets. */
function copyOne(from, to) {
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
  return fs.statSync(to).size;
}

/**
 * Verifie qu'un dossier source contient exactement ce qu'on attend.
 *
 * Un compte qui ne tombe pas juste signifie que la source a change : mieux vaut s'arreter que
 * de livrer 603 pages en croyant en avoir 604.
 */
function assertCount(directory, predicate, expected, label) {
  const found = fs.readdirSync(directory).filter(predicate);
  if (found.length !== expected) {
    fail(`${label} : ${found.length} fichier(s) trouve(s), ${expected} attendu(s) dans ${directory}`);
  }
  return found;
}

function importData(source) {
  const from = path.join(source, DATA_SOURCE);
  if (!fs.existsSync(from)) fail(`donnees de page absentes de la source : ${from}`);

  const numbered = assertCount(from, (name) => /^\d+\.json$/.test(name), PAGES, 'pages');
  // Les numeros doivent couvrir 1..604 sans trou : un fichier manquant au milieu passerait un
  // simple compte si un doublon le compensait.
  const numbers = numbered.map((name) => Number(name.replace('.json', ''))).sort((a, b) => a - b);
  for (let page = 1; page <= PAGES; page += 1) {
    if (numbers[page - 1] !== page) fail(`la page ${page} manque (trouve ${numbers[page - 1]})`);
  }

  let bytes = 0;
  for (const name of numbered) {
    bytes += copyOne(path.join(from, name), path.join(DATA_TARGET, name));
  }
  for (const name of DATA_EXTRAS) {
    const file = path.join(from, name);
    if (!fs.existsSync(file)) fail(`fichier de donnees absent de la source : ${file}`);
    bytes += copyOne(file, path.join(DATA_TARGET, name));
  }
  console.log(`  donnees  : ${numbered.length + DATA_EXTRAS.length} fichiers, ${(bytes / 1048576).toFixed(2)} Mo -> ${path.relative(ROOT, DATA_TARGET)}`);
}

function importFonts(source) {
  const from = path.join(source, FONTS_SOURCE);
  if (!fs.existsSync(from)) fail(`polices absentes de la source : ${from}`);

  const numbered = assertCount(from, (name) => /^\d+\.woff2$/.test(name), PAGES, 'polices de page');
  // La source porte trois polices nommees : deux sont consommees, la troisieme est ecartee.
  const named = assertCount(
    from,
    (name) => name.endsWith('.woff2') && !/^\d+\.woff2$/.test(name),
    NAMED_FONTS.length + 1,
    'polices nommees (titre, basmala, texte)',
  );
  for (const name of [...NAMED_FONTS, NAMED_FONT_UNUSED]) {
    if (!named.includes(name)) fail(`police nommee absente de la source : ${name}`);
  }

  const numbers = numbered.map((name) => Number(name.replace('.woff2', ''))).sort((a, b) => a - b);
  for (let page = 1; page <= PAGES; page += 1) {
    if (numbers[page - 1] !== page) fail(`la police de la page ${page} manque`);
  }

  let bytes = 0;
  for (const name of [...numbered, ...NAMED_FONTS]) {
    bytes += copyOne(path.join(from, name), path.join(FONTS_TARGET, name));
  }
  for (const name of FONT_EXTRAS) {
    const file = path.join(from, name);
    if (!fs.existsSync(file)) fail(`fichier de provenance absent de la source : ${file}`);
    bytes += copyOne(file, path.join(FONTS_TARGET, name));
  }
  console.log(`  polices  : ${numbered.length + NAMED_FONTS.length + FONT_EXTRAS.length} fichiers, ${(bytes / 1048576).toFixed(2)} Mo -> ${path.relative(ROOT, FONTS_TARGET)}`);
  console.log(`  ecartee  : ${NAMED_FONT_UNUSED} — aucun module de l'original ne consomme son export.`);
}

function main() {
  const args = readArgs(process.argv.slice(2));
  const source = path.resolve(args.source);
  console.log(`Import depuis ${source}`);

  // La source est en lecture seule : on le dit une fois, et rien ici n'ecrit chez elle.
  if (path.resolve(source) === ROOT) fail('la source et la cible sont le meme dossier');

  if (!args.fontsOnly) importData(source);
  if (!args.dataOnly) importFonts(source);

  console.log('  termine.');
}

main();
