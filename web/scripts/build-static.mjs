import { cpSync, existsSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const dist = join(root, "dist");
const entries = [
  "index.html",
  "app.css",
  "db.js",
  "main.js",
  "palette.js",
  "search.js",
  "store.js",
  "ui-gallery.js",
  "ui-main.js",
  "ui-settings.js",
  "assets",
  "data",
];

rmSync(dist, { recursive: true, force: true });
mkdirSync(dist, { recursive: true });

for (const entry of entries) {
  const source = join(root, entry);
  if (!existsSync(source)) {
    throw new Error(`Missing web build entry: ${entry}`);
  }
  cpSync(source, join(dist, entry), { recursive: true });
}

const files = readdirSync(dist);
if (!files.includes("index.html")) {
  throw new Error("Web build did not produce dist/index.html");
}

console.log(`Keyxif Web static build complete: ${dist}`);
