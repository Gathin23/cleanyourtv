/**
 * Check the English dictionary against the screens: every string passed to t() must have an
 * English entry, and no entry may be left over after a screen changed its wording.
 *
 *   node check-i18n.cjs
 */
const fs = require("fs");
const path = require("path");
const ts = require("typescript");

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return walk(p);
    return /\.tsx?$/.test(e.name) ? [p] : [];
  });
}

// Keys used by the screens.
const used = new Set();
for (const file of walk("src")) {
  if (file.includes("/i18n/")) continue;
  const src = fs.readFileSync(file, "utf8");
  const sf = ts.createSourceFile(file, src, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const visit = (node) => {
    if (
      ts.isCallExpression(node) &&
      node.expression.getText(sf) === "t" &&
      node.arguments.length &&
      (ts.isStringLiteral(node.arguments[0]) || ts.isNoSubstitutionTemplateLiteral(node.arguments[0]))
    ) {
      used.add(node.arguments[0].text);
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
}

// Keys the dictionary defines.
const defined = new Map();
for (const file of walk("src/i18n/en")) {
  const src = fs.readFileSync(file, "utf8");
  const sf = ts.createSourceFile(file, src, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const visit = (node) => {
    if (ts.isPropertyAssignment(node) && ts.isStringLiteral(node.name)) {
      const key = node.name.text;
      if (defined.has(key)) {
        console.log(`DUPLICATE  ${JSON.stringify(key)} in ${file} and ${defined.get(key)}`);
      }
      defined.set(key, path.basename(file));
      // A placeholder in the Turkish key must survive into the English one.
      const value = ts.isStringLiteral(node.initializer) ? node.initializer.text : null;
      if (value !== null) {
        const want = (key.match(/\{(\w+)\}/g) || []).sort().join(",");
        const got = (value.match(/\{(\w+)\}/g) || []).sort().join(",");
        if (want !== got) {
          console.log(`PLACEHOLDER ${JSON.stringify(key)} → ${JSON.stringify(value)}`);
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
}

const missing = [...used].filter((k) => !defined.has(k)).sort((a, b) => a.localeCompare(b, "tr"));
const extra = [...defined.keys()].filter((k) => !used.has(k)).sort((a, b) => a.localeCompare(b, "tr"));

console.log(`\nused ${used.size} · defined ${defined.size}`);
for (const k of missing) console.log(`MISSING ${JSON.stringify(k)}`);
for (const k of extra) console.log(`EXTRA   ${JSON.stringify(k)} (${defined.get(k)})`);
console.log(`\nmissing ${missing.length} · extra ${extra.length}`);
process.exit(missing.length ? 1 : 0);
