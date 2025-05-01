const express = require('express');
const fetch = require('node-fetch');
const path = require('path');
const app = express();
const port = 3002;

// Serve static files
app.use(express.static('public'));

// Proxy API requests to the Coral Server
app.get('/api/*', async (req, res) => {
  try {
    const coralServerUrl = 'http://host.docker.internal:3001';
    const apiPath = req.path.replace('/api', '');
    const response = await fetch(`${coralServerUrl}${apiPath}`);
    const data = await response.json();
    res.json(data);
  } catch (error) {
    console.error('Error proxying request:', error);
    res.status(500).json({ error: 'Failed to fetch data from Coral Server' });
  }
});

// Create public directory
app.listen(port, () => {
  console.log(`Coral Monitor running at http://localhost:${port}`);
});
