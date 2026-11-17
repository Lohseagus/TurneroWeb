/**
 * Collects every Material Symbols ligature name the app can render.
 * Used by the subsetter and by the production build check.
 *
 * Patterns covered:
 * - text inside .material-symbols-outlined elements (including @if blocks)
 * - quoted names inside those elements (ternaries)
 * - icon: "name" / icon="name" bindings
 * - return / fallback strings from functions whose name contains Icon or icono
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const ICON_NAME = /^[a-z][a-z0-9_]*$/;
const SRC_EXT = new Set([".html", ".ts"]);

export function repoSrcRoot() {
  return fileURLToPath(new URL("../src", import.meta.url));
}

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      if (entry === "node_modules" || entry === "dist") continue;
      walk(path, out);
    } else if (SRC_EXT.has(path.slice(path.lastIndexOf(".")))) {
      out.push(path);
    }
  }
  return out;
}

function add(set, name) {
  if (ICON_NAME.test(name)) set.add(name);
}

function elementBodies(text) {
  const bodies = [];
  const re = /<([a-zA-Z][\w-]*)\b([^>]*\bmaterial-symbols-outlined\b[^>]*)>([\s\S]*?)<\/\1>/g;
  let match;
  while ((match = re.exec(text))) {
    bodies.push(match[3]);
  }
  return bodies;
}

function namesFromMustache(expr, set) {
  // Icon results only. Comparison literals such as === 'asc' are not icons.
  const re = /(?:\?|:|\|\|)\s*['"]([a-z][a-z0-9_]*)['"]/g;
  let match;
  while ((match = re.exec(expr))) add(set, match[1]);
}

function namesFromElementBody(body, set) {
  const mustaches = body.matchAll(/\{\{([\s\S]*?)\}\}/g);
  for (const match of mustaches) namesFromMustache(match[1], set);

  let plain = body
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/\{\{[\s\S]*?\}\}/g, " ")
    .replace(/@if\s*\([^)]*\)/g, " ")
    .replace(/@else\s+if\s*\([^)]*\)/g, " ")
    .replace(/@else\b/g, " ")
    .replace(/@for\s*\([^)]*\)/g, " ")
    .replace(/<[^>]+>/g, " ");
  for (const token of plain.split(/[^a-z0-9_]+/)) add(set, token);
}

function functionBodiesNamedIcon(text) {
  const bodies = [];
  const re = /\b([A-Za-z0-9_]*[Ii]con[A-Za-z0-9_]*)\s*\([^)]*\)\s*(?::\s*[^{=]+)?\{/g;
  let match;
  while ((match = re.exec(text))) {
    const start = match.index + match[0].length;
    let depth = 1;
    let i = start;
    while (i < text.length && depth > 0) {
      const ch = text[i];
      if (ch === "{") depth++;
      else if (ch === "}") depth--;
      i++;
    }
    bodies.push(text.slice(start, i - 1));
  }
  return bodies;
}

function namesFromIconFunction(body, set) {
  const re = /(?:return|\|\||:)\s*['"]([a-z][a-z0-9_]*)['"]/g;
  let match;
  while ((match = re.exec(body))) add(set, match[1]);
}

export function collectMaterialSymbolIcons(srcRoot) {
  const icons = new Set();
  for (const file of walk(srcRoot)) {
    const text = readFileSync(file, "utf8");
    if (text.includes("material-symbols")) {
      for (const body of elementBodies(text)) namesFromElementBody(body, icons);
    }
    const iconProps = text.matchAll(/\bicon\s*[:=]\s*['"]([a-z][a-z0-9_]*)['"]/g);
    for (const match of iconProps) add(icons, match[1]);
    if (/[Ii]con/.test(text)) {
      for (const body of functionBodiesNamedIcon(text)) namesFromIconFunction(body, icons);
    }
  }
  return [...icons].sort();
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  const icons = collectMaterialSymbolIcons(repoSrcRoot());
  process.stdout.write(icons.join("\n") + "\n");
  console.error(`material symbols: ${icons.length}`);
}
