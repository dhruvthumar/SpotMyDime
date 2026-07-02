const express = require('express');

const PORT = process.env.PORT || 3000;
const GEMINI_URL = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent';
const GEMINI_KEY = process.env.GEMINI_API_KEY;

if (!GEMINI_KEY) {
  const rl = require('readline').createInterface({ input: process.stdin, output: process.stdout });
  rl.question('Paste your Gemini API key: ', key => {
    rl.close();
    if (!key) { console.error('No key provided'); process.exit(1); }
    process.env.GEMINI_API_KEY = key.trim();
    start();
  });
} else {
  start();
}

function start() {
  const key = process.env.GEMINI_API_KEY;
  const app = express();
  app.use(express.json());

  app.post('/api/gemini/classify', async (req, res) => {
    try {
      const geminiRes = await fetch(`${GEMINI_URL}?key=${key}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req.body),
      });
      res.status(geminiRes.status).json(await geminiRes.json());
    } catch (err) {
      res.status(502).json({ error: 'Upstream failed' });
    }
  });

  app.get('/', (_req, res) => res.json({ ok: true }));
  app.get('/api/health', (_req, res) => res.json({ ok: true }));

  app.listen(PORT, () => console.log(`Backend on port ${PORT}`));
}
