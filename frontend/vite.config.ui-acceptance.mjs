import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { fileURLToPath } from 'url'
const dirname = path.dirname(fileURLToPath(import.meta.url))
export default defineConfig({
  base: '/',
  plugins: [vue()],
  resolve: { alias: { '@': path.resolve(dirname, 'src') } },
  server: { host: '127.0.0.1', port: 5192, strictPort: true, proxy: {
    '/api': { target: 'http://127.0.0.1:18091', changeOrigin: true },
    '/ws': { target: 'ws://127.0.0.1:18091', ws: true, changeOrigin: true },
  } },
})