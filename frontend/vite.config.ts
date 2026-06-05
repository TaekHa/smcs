import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      // Attachment images are served at /files/** (FileController, no /api prefix).
      // Without this the dev server returns index.html for /files/* and the
      // AuthImage preview breaks — same gap nginx had in prod (UT-003).
      '/files': 'http://localhost:8080',
    },
  },
  build: {
    rollupOptions: {
      output: {
        // architecture §9.7 Vendor Splitting. antd is intentionally NOT a single
        // forced chunk — Rollup splits it along the import graph so heavy
        // lazy-route widgets (Table/DatePicker) stay out of the eager payload.
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'query-vendor': ['@tanstack/react-query'],
        },
      },
    },
  },
});
