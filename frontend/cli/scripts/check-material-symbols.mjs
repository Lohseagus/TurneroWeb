/**
 * Fails the production build when a Material Symbol was added in source
 * and the self-hosted subset was not regenerated.
 *
 *   python3 scripts/subset-material-symbols.py
 */
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { collectMaterialSymbolIcons, repoSrcRoot } from "./material-symbol-icons.mjs";

const manifestPath = new URL("../src/fonts/material-symbols.manifest.json", import.meta.url);
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const used = collectMaterialSymbolIcons(repoSrcRoot());
const shipped = manifest.icons;

const missing = used.filter((name) => !shipped.includes(name));
const extra = shipped.filter((name) => !used.includes(name));
if (missing.length || extra.length) {
  console.error("El subset de Material Symbols no coincide con los iconos del código.");
  if (missing.length) console.error("Faltan en el subset:", missing.join(", "));
  if (extra.length) console.error("Sobran en el manifiesto:", extra.join(", "));
  console.error("Regenerá el font con: python3 scripts/subset-material-symbols.py");
  process.exit(1);
}

const fontPath = new URL(`../public/fonts/${manifest.file}`, import.meta.url);
const bytes = readFileSync(fontPath);
const sha256 = createHash("sha256").update(bytes).digest("hex");
if (sha256 !== manifest.sha256) {
  console.error(`SHA-256 de public/fonts/${manifest.file} no coincide con el manifiesto.`);
  process.exit(1);
}

const indexHtml = readFileSync(new URL("../src/index.html", import.meta.url), "utf8");
const styles = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");
if (!indexHtml.includes(manifest.file) || !styles.includes(manifest.file)) {
  console.error(`index.html y styles.css tienen que referenciar ${manifest.file}.`);
  process.exit(1);
}
if (indexHtml.includes("fonts.googleapis.com/css2?family=Material+Symbols")) {
  console.error("index.html todavía pide Material Symbols a Google Fonts.");
  process.exit(1);
}
if (indexHtml.includes("font-awesome/4.7.0")) {
  console.error("index.html todavía carga Font Awesome 4 desde el CDN.");
  process.exit(1);
}

console.log(
  `Material Symbols: ${used.length} iconos, ${manifest.file} (${bytes.length} bytes).`,
);
