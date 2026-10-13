import http from 'node:http';
import fs from 'node:fs';
import crypto from 'node:crypto';

const payload = fs.readFileSync(process.argv[2]);
const port = Number(process.argv[3] || 37123);
const sha256 = crypto.createHash('sha256').update(payload).digest('hex');
const metadata = { size: payload.length, sha256, versionName: '1.3.0' };
http.createServer((request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  if (request.url === '/meta') {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify(metadata));
    return;
  }
  if (request.url?.startsWith('/api/')) {
    response.setHeader('Content-Type', 'application/json');
    if (request.url === '/api/missing') { response.writeHead(404); response.end('{}'); return; }
    const version = request.url === '/api/current' ? 'v1.3.0' : 'v99.0.0';
    response.end(JSON.stringify({ tag_name: version, name: 'QA update', body: 'QA fixture', assets: request.url === '/api/no-apk' ? [] : [
      { name: 'yijing.apk', size: payload.length, digest: `sha256:${sha256}`, browser_download_url: `http://127.0.0.1:${port}/fixture.apk` }
    ] }));
    return;
  }
  if (request.url === '/broken.apk') {
    response.setHeader('Content-Length', '7'); response.end('invalid'); return;
  }
  if (request.url === '/fixture.apk' || request.url === '/slow.apk') {
    response.setHeader('Content-Type', 'application/vnd.android.package-archive');
    response.setHeader('Content-Length', String(payload.length));
    if (request.url === '/slow.apk') setTimeout(() => response.end(payload), 5000);
    else response.end(payload);
    return;
  }
  response.writeHead(404); response.end();
}).listen(port, '127.0.0.1', () => console.log(`QA APK server pid=${process.pid} port=${port} bytes=${payload.length} sha256=${sha256}`));
