const fs = require('fs');
const path = require('path');
const http = require('http');

const PORT = 38488;
const ROOT = path.resolve(__dirname, '../..');
const MAX_ITEMS = 100;

const changes = [];

function notifyChange(filePath, eventType) {
  const relative = path.relative(ROOT, filePath);
  if (relative.startsWith('node_modules') || relative.includes('.git')) return;
  const item = {
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    path: relative,
    type: eventType || 'change'
  };
  changes.unshift(item);
  if (changes.length > MAX_ITEMS) changes.pop();
}

function watchDir(dir) {
  try {
    fs.watch(dir, { recursive: true }, (eventType, filename) => {
      if (filename) notifyChange(path.join(dir, filename), eventType);
    });
  } catch (e) {
    console.error('watch error:', e.message);
  }
}

const mime = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.json': 'application/json' };

const server = http.createServer((req, res) => {
  const url = req.url === '/' ? '/index.html' : req.url;
  const filePath = path.join(__dirname, url.split('?')[0]);

  if (url === '/api/changes') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ changes }));
    return;
  }

  const ext = path.extname(filePath);
  const type = mime[ext] || 'text/plain';
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); res.end('Not Found'); return; }
    res.writeHead(200, { 'Content-Type': type });
    res.end(data);
  });
});

watchDir(ROOT);
server.listen(PORT, '127.0.0.1', () => {
  const url = 'http://127.0.0.1:' + PORT;
  console.log('Vault pi watch UI: ' + url);
  try {
    require('child_process').exec(process.platform === 'darwin' ? 'open "' + url + '"' : 'start "' + url + '"');
  } catch (_) {}
});
