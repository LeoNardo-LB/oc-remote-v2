const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 52341;
const CONTENT_DIR = path.join(__dirname, 'content');
const STATE_DIR = path.join(__dirname, 'state');

// Ensure dirs exist
if (!fs.existsSync(CONTENT_DIR)) fs.mkdirSync(CONTENT_DIR, { recursive: true });
if (!fs.existsSync(STATE_DIR)) fs.mkdirSync(STATE_DIR, { recursive: true });

const server = http.createServer((req, res) => {
  // CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  
  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  const url = new URL(req.url, `http://localhost:${PORT}`);

  // POST /events — record browser interactions
  if (req.method === 'POST' && url.pathname === '/events') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      fs.appendFileSync(path.join(STATE_DIR, 'events'), body + '\n');
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"ok":true}');
    });
    return;
  }

  // GET / — serve newest HTML file from content dir
  if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/index.html')) {
    const files = fs.readdirSync(CONTENT_DIR)
      .filter(f => f.endsWith('.html'))
      .map(f => ({ name: f, time: fs.statSync(path.join(CONTENT_DIR, f)).mtimeMs }))
      .sort((a, b) => b.time - a.time);

    if (files.length === 0) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end('<html><body style="display:flex;align-items:center;justify-content:center;min-height:100vh;font-family:system-ui"><h2>等待内容...</h2></body></html>');
      return;
    }

    const content = fs.readFileSync(path.join(CONTENT_DIR, files[0].name), 'utf-8');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(content);
    return;
  }

  res.writeHead(404);
  res.end('Not found');
});

server.listen(PORT, () => {
  const info = { type: 'server-started', port: PORT, url: `http://localhost:${PORT}` };
  fs.writeFileSync(path.join(STATE_DIR, 'server-info'), JSON.stringify(info, null, 2));
  console.log(JSON.stringify(info));
});
