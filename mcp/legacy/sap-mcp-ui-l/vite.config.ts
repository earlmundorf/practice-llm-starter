import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// SAP Commerce redirects HTTP:9001 → HTTPS:9002, so proxy directly to HTTPS.
// Must strip Origin header — SAP Commerce CORS filter rejects unknown origins.
const sapProxy = {
  target: process.env.VITE_API_URL || 'https://localhost:9002',
  changeOrigin: true,
  secure: false,
  configure: (proxy: import('http-proxy').Server) => {
    proxy.on('proxyReq', (proxyReq) => {
      proxyReq.removeHeader('origin');
    });
  },
};

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/occ': sapProxy,
      '/authorizationserver': sapProxy,
      // Product/CMS image URLs come back from OCC as /medias/?context=...
      // proxy them to the backend so <img src> works in dev.
      '/medias': sapProxy,
    }
  }
})
