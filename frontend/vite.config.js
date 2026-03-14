import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path
      }
    }
  },
  // 构建优化配置
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          echarts: ['echarts'],
          axios: ['axios'],
          xlsx: ['xlsx']
        },
        assetFileNames: 'assets/[name].[hash:8][extname]',
        chunkFileNames: 'assets/[name].[hash:8].js',
        entryFileNames: 'assets/[name].[hash:8].js'
      }
    },
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    },
    // 图片压缩配置
    assetsInlineLimit: 4096, // 4kb 以下的图片内联为 base64
  },
  optimizeDeps: {
    include: ['element-plus', 'echarts', 'axios']
  },
  // 图片压缩插件
  esbuild: {
    // 启用更多树摇优化
    treeShaking: true,
  }
})
