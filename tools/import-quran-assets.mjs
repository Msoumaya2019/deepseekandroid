#!/usr/bin/env node
/**
 * Import des ressources coraniques depuis une copie locale de `coran-memoire`.
 *
 * Le dépôt `Msoumaya2019/coran-memoire` est STRICTEMENT en lecture seule : ce script ne
 * fait que lire. Il copie vers `deepseekandroid` :
 *
 *   1. les données JSON du référentiel et du lecteur -> core/domain/src/main/resources/quran/
 *   2. les pages du moushaf (images)                   -> app/src/main/assets/quran/pages/
 *   3. les pages Tajweed en images (facultatif)        -> app/src/main/assets/quran/tajweed/
 *
 * Usage :
 *   node tools/import-quran-assets.mjs --source <chemin vers coran-memoire>
 *   node tools/import-quran-assets.mjs --source <...> --pages 1-3,580-604
 *   node tools/import-quran-assets.mjs --source <...> --data-only
 *   node tools/import-quran-assets.mjs --source <...> --all-pages
 *
 * Par défaut, seules les données JSON et un jeu réduit de pages (utile au développement)
 * sont copiés : les 604 pages complètes pèsent 118,2 Mo — mesuré sur les fichiers livrés, et
 * non « environ 114 Mo » comme l'annonçait une première estimation.
 */

import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');

const DATA_TARGET = path.join(ROOT, 'core', 'domain', 'src', 'main', 'resources', 'quran');
const PAGES_TARGET = path.join(ROOT, 'app', 'src', 'main', 'assets', 'quran', 'pages');
const TAJWEED_TARGET = path.join(ROOT, 'app', 'src', 'main', 'assets', 'quran', 'tajweed');

/** Jeu de pages copié par défaut : début du moushaf et juz’ ‘Amma. */
const DEFAULT_PAGES = [...range(1, 3), ...range(580, 604)];

/** Fichiers JSON copiés vers les ressources du module de domaine. */
const DATA_FILES = [
  ['src/data/verses.json', 'verses.json'],
  ['src/data/meta.json', 'meta.json'],
  ['src/data/pages.json', 'pages.json'],
  ['src/data/bounds.json', 'bounds.json'],
  ['src/data/tajweed-text.json', 'tajweed-text.json'],
  ['src/data/tajweed-rules.json', 'tajweed-rules.json'],
  ['src/data/translation-fr-rashid.json', 'translation-fr-rashid.json'],
  ['src/data/toumoun.json', 'toumoun.json'],
  ['src/data/ipa-audio-source.json', 'ipa-audio-source.json'],
  ['src/data/quran-tests/coran_1441-bounds.json', 'coran_1441-bounds.json'],
  ['src/data/quran-tests/coran_1441-dimensions.json', 'coran_1441-dimensions.json'],
  ['src/data/TANZIL-LICENSE.txt', 'TANZIL-LICENSE.txt'],
];

function range(from, to) {
  const out = [];
  for (let i = from; i <= to; i += 1) out.push(i);
  return out;
}

function parseArgs(argv) {
  const args = { source: null, pages: DEFAULT_PAGES, allPages: false, dataOnly: false, withTajweed: false };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === '--source') args.source = argv[++i];
    else if (token === '--all-pages') args.allPages = true;
    else if (token === '--data-only') args.dataOnly = true;
    else if (token === '--with-tajweed') args.withTajweed = true;
    else if (token === '--pages') {
      args.pages = argv[++i].split(',').flatMap((part) => {
        const [a, b] = part.split('-').map(Number);
        return Number.isFinite(b) ? range(a, b) : [a];
      });
    } else if (token === '--help' || token === '-h') {
      printUsage();
      process.exit(0);
    }
  }
  if (!args.source) {
    printUsage();
    process.exit(2);
  }
  return args;
}

function printUsage() {
  process.stdout.write(
    [
      'Import des ressources coraniques.',
      '',
      '  --source <dossier>   copie locale de coran-memoire (obligatoire)',
      '  --pages 1-3,580-604  pages du moushaf à copier (défaut : 1-3 et 580-604)',
      '  --all-pages          copier les 604 pages (118,2 Mo)',
      '  --data-only          ne copier que les données JSON',
      '  --with-tajweed       copier aussi les pages Tajweed (images)',
      '',
    ].join('\n'),
  );
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function copyIfChanged(from, to) {
  const src = fs.statSync(from);
  if (fs.existsSync(to)) {
    const dst = fs.statSync(to);
    if (dst.size === src.size) return false;
  }
  ensureDir(path.dirname(to));
  fs.copyFileSync(from, to);
  return true;
}

function sha256(file) {
  return createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 12);
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const source = path.resolve(args.source);

  if (!fs.existsSync(path.join(source, 'src', 'data', 'verses.json'))) {
    process.stderr.write(`Ce dossier ne ressemble pas à coran-memoire : ${source}\n`);
    process.exit(1);
  }

  // --- 1. Données JSON -----------------------------------------------------
  let copied = 0;
  for (const [rel, name] of DATA_FILES) {
    const from = path.join(source, rel);
    if (!fs.existsSync(from)) {
      process.stderr.write(`  absent, ignoré : ${rel}\n`);
      continue;
    }
    if (copyIfChanged(from, path.join(DATA_TARGET, name))) copied += 1;
  }
  const bytes = fs
    .readdirSync(DATA_TARGET)
    .reduce((sum, name) => sum + fs.statSync(path.join(DATA_TARGET, name)).size, 0);
  process.stdout.write(
    `Données : ${copied} fichier(s) mis à jour, ${(bytes / 1024 / 1024).toFixed(1)} Mo dans ${path.relative(ROOT, DATA_TARGET)}\n`,
  );

  if (args.dataOnly) return;

  // --- 2. Pages du moushaf (Coran de Médine) -------------------------------
  const pages = args.allPages ? range(1, 604) : args.pages;
  let pageCount = 0;
  for (const page of pages) {
    const name = `page${String(page).padStart(3, '0')}.png`;
    const from = path.join(source, 'assets', 'mushaf', name);
    if (!fs.existsSync(from)) {
      process.stderr.write(`  page absente : ${name}\n`);
      continue;
    }
    if (copyIfChanged(from, path.join(PAGES_TARGET, name))) pageCount += 1;
  }
  process.stdout.write(
    `Moushaf : ${pageCount} page(s) copiée(s) sur ${pages.length} demandée(s) -> ${path.relative(ROOT, PAGES_TARGET)}\n`,
  );

  // --- 3. Pages Tajweed (facultatif) ---------------------------------------
  if (args.withTajweed) {
    let tajweedCount = 0;
    for (const page of pages) {
      const name = `page${String(page).padStart(3, '0')}.png`;
      const from = path.join(source, 'assets', 'mushaf-tajweed', name);
      if (!fs.existsSync(from)) continue;
      if (copyIfChanged(from, path.join(TAJWEED_TARGET, name))) tajweedCount += 1;
    }
    process.stdout.write(`Tajweed : ${tajweedCount} page(s) copiée(s)\n`);
  }

  // --- 4. Empreintes -------------------------------------------------------
  //
  // La liste vient de DATA_FILES — ce qui a été **importé** — et non d'un `readdirSync` sur le
  // dossier. La différence n'est pas cosmétique : au deuxième import, `readdirSync` trouve
  // `import-manifest.json` écrit par le premier, et enregistre donc l'empreinte du manifeste
  // **précédent**. L'entrée est alors fausse à jamais — un fichier ne peut pas porter sa propre
  // empreinte — et elle donne à un contrôleur l'illusion d'avoir vérifié quelque chose. Mesuré :
  // `8437f2daae05` déclarée pour un fichier qui donne `fab6e4bd3e48`. Le dossier peut aussi
  // contenir un JSON qu'aucun import n'a posé ; il n'a rien à faire dans le manifeste.
  const manifest = {
    importedAt: new Date().toISOString(),
    source,
    dataFiles: Object.fromEntries(
      DATA_FILES.map(([, target]) => target)
        .filter((name) => name.endsWith('.json'))
        .filter((name) => fs.existsSync(path.join(DATA_TARGET, name)))
        .map((name) => [name, sha256(path.join(DATA_TARGET, name))]),
    ),
    pages: fs.existsSync(PAGES_TARGET) ? fs.readdirSync(PAGES_TARGET).length : 0,
  };
  ensureDir(DATA_TARGET);
  fs.writeFileSync(
    path.join(DATA_TARGET, 'import-manifest.json'),
    `${JSON.stringify(manifest, null, 2)}\n`,
  );
  process.stdout.write('Empreintes écrites dans import-manifest.json\n');
}

main();
