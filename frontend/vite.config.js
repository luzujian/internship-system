import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  base: '/',
  plugins: [vue()],
  // 预加载配置
  preloadStrategy: 'preload-prefetch',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  },
  // 构建优化配置
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 保留按路由和动态 import 自动分块，避免文档预览、导出等依赖进入首屏。
        assetFileNames: 'assets/[name].[hash:8]-v2[extname]',
        chunkFileNames: 'assets/[name].[hash:8]-v2.js',
        entryFileNames: 'assets/[name].[hash:8]-v2.js'
      }
    },
    minify: 'esbuild',
    // 图片压缩配置
    assetsInlineLimit: 4096, // 4kb 以下的图片内联为 base64
  },
  optimizeDeps: {
    include: [
      'element-plus',
      'echarts',
      'axios',
      'vue',
      'vue-router',
      'pinia'
    ]
  },
  // 启用更激进的 Tree Shaking
  esbuild: {
    treeShaking: true,
    // 移除 console.log（生产环境）
    drop: ['console', 'debugger']
  },
  // CSS 代码分割
  cssCodeSplit: true,
})
