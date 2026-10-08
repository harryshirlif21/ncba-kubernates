import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const diagramsDir = join(root, 'docs', 'diagrams');
const pngDir = join(diagramsDir, 'png');
const pdfPath = join(root, 'docs', 'Architecture-Diagrams.pdf');
const edgePaths = [
  process.env.EDGE_PATH,
  join(process.env['ProgramFiles(x86)'] ?? 'C:\\Program Files (x86)', 'Microsoft', 'Edge', 'Application', 'msedge.exe'),
  join(process.env.ProgramFiles ?? 'C:\\Program Files', 'Microsoft', 'Edge', 'Application', 'msedge.exe'),
].filter(Boolean);
const edgePath = edgePaths.find(existsSync);

const diagrams = [
  {
    id: '01-kubernetes-architecture',
    title: 'Kubernetes deployment architecture',
    caption: 'Shows ingress and service routing, the application Deployment and policies, MySQL persistence, and the external SOAP boundary.',
  },
  {
    id: '02-request-flow',
    title: 'Request flow: POST /api/v1/countries',
    caption: 'Follows validation, the two sequential SOAP calls, persistence after SOAP resolution, and the 201 or 200 response.',
  },
  {
    id: '03-resilience-flow',
    title: 'Resilience and failure handling',
    caption: 'Shows SOAP caching, timeouts, retries and circuit breaking, plus the stored-country fallback and 404 or 503 outcomes.',
  },
  {
    id: '04-data-model',
    title: 'Data model',
    caption: 'Shows the one-to-many country-to-language relationship, its foreign key, and cascading language deletion.',
  },
  {
    id: '05-mvc-layers',
    title: 'Application layers (MVC)',
    caption: 'Maps the request filter, controller, service, repository and SOAP adapter to their DTOs, entities and cross-cutting services.',
  },
  {
    id: '06-deployment-pipeline',
    title: 'Deployment pipeline (scripts/deploy.sh)',
    caption: 'Shows the script’s image and Kubernetes resource rollout. Port-forwarding and smoke testing are separate manual follow-up steps.',
  },
  {
    id: '07-api-overview',
    title: 'REST API endpoints',
    caption: 'Summarizes the country CRUD routes and the exposed liveness, readiness and Prometheus endpoints.',
  },
];

function run(command, args, env = process.env) {
  const result = spawnSync(command, args, {
    cwd: root,
    env,
    stdio: 'inherit',
    shell: process.platform === 'win32' && command === 'npx',
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} exited with status ${result.status}`);
}

function escapeHtml(value) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

if (!edgePath) {
  throw new Error('Microsoft Edge was not found. Set EDGE_PATH to the msedge.exe path and retry.');
}

mkdirSync(pngDir, { recursive: true });
for (const diagram of diagrams) {
  const input = join('docs', 'diagrams', `${diagram.id}.mmd`);
  const output = join('docs', 'diagrams', 'png', `${diagram.id}.png`);
  if (!existsSync(join(root, input))) throw new Error(`Missing Mermaid source: ${input}`);
  run('npx', ['--yes', '--package', '@mermaid-js/mermaid-cli', 'mmdc', '-i', input, '-o', output, '-s', '2', '-b', 'white'], {
    ...process.env,
    PUPPETEER_EXECUTABLE_PATH: edgePath,
  });
}

const pages = diagrams.map((diagram, index) => {
  const image = readFileSync(join(pngDir, `${diagram.id}.png`)).toString('base64');
  return `<section class="page diagram-page">
  <h1>${escapeHtml(diagram.title)}</h1>
  <figure><img src="data:image/png;base64,${image}" alt="${escapeHtml(diagram.title)}"></figure>
  <p class="caption">${escapeHtml(diagram.caption)}</p>
  <footer>Page ${index + 2} of ${diagrams.length + 1}</footer>
</section>`;
}).join('\n');

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Country Integration Service: Architecture and Flow Diagrams</title>
<style>
  @page { size: A4 landscape; margin: 15mm; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; font-family: Arial, sans-serif; color: #172554; }
  .page { position: relative; display: flex; flex-direction: column; width: 267mm; height: 180mm; overflow: hidden; page-break-after: always; break-after: page; padding-bottom: 8mm; }
  .page:last-child { page-break-after: auto; break-after: auto; }
  .title-page { justify-content: center; align-items: center; text-align: center; padding: 0 18mm 8mm; background: #f8fafc; }
  .title-page h1 { max-width: 220mm; margin: 0 0 8mm; font-size: 29pt; line-height: 1.2; color: #1d4ed8; }
  .subtitle { margin: 0 0 20mm; font-size: 17pt; color: #475569; }
  .author, .date { margin: 3mm 0; font-size: 12pt; }
  .repo { margin-top: 11mm; font-size: 10pt; color: #2563eb; }
  h1 { flex: 0 0 auto; margin: 0 0 4mm; font-size: 20pt; line-height: 1.15; color: #1d4ed8; }
  figure { flex: 1 1 auto; min-height: 0; width: 100%; margin: 0 0 3mm; display: flex; align-items: center; justify-content: center; }
  figure img { display: block; width: 100%; height: 100%; object-fit: contain; }
  .caption { flex: 0 0 auto; min-height: 12mm; margin: 0; padding-right: 15mm; font-size: 10.5pt; line-height: 1.35; color: #334155; }
  footer { position: absolute; right: 0; bottom: 0; font-size: 9pt; color: #64748b; }
  @media print { body { print-color-adjust: exact; -webkit-print-color-adjust: exact; } }
</style>
</head>
<body>
<section class="page title-page">
  <h1>Country Integration Service: Architecture and Flow Diagrams</h1>
  <p class="subtitle">NCBA LOOP case study</p>
  <p class="author">Hillary Mwendi Kimaiga</p>
  <p class="date">8 October 2026</p>
  <p class="repo">https://github.com/harryshirlif21/ncba-kubernates</p>
  <footer>Page 1 of ${diagrams.length + 1}</footer>
</section>
${pages}
</body>
</html>`;

const tempDir = mkdtempSync(join(tmpdir(), 'country-integration-diagrams-'));
try {
  const htmlPath = join(tempDir, 'diagrams.html');
  const profilePath = join(tempDir, 'edge-profile');
  writeFileSync(htmlPath, html, 'utf8');
  run(edgePath, [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    `--user-data-dir=${profilePath}`,
    '--no-pdf-header-footer',
    `--print-to-pdf=${pdfPath}`,
    pathToFileURL(htmlPath).href,
  ]);
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}

console.log(`Created ${pdfPath}`);