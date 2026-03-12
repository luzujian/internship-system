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
    chunkSizeWarningLimit: 1000, // 增加chunk大小警告限制
    rollupOptions: {
      output: {
        // 代码分割 - 将大型依赖库分离成独立的chunk
        manualChunks: {
          'element-plus': ['element-plus'],
          'echarts': ['echarts'],
          'axios': ['axios'],
          'xlsx': ['xlsx']
        },
        // 静态资源打包策略
        assetFileNames: 'assets/[name].[hash:8][extname]',
        chunkFileNames: 'assets/[name].[hash:8].js',
        entryFileNames: 'assets/[name].[hash:8].js'
      }
    },
    // 压缩CSS和JS
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true, // 移除console
        drop_debugger: true // 移除debugger
      }
    }
  },
  // 优化开发体验
  optimizeDeps: {
    include: ['element-plus', 'echarts', 'axios']
  }
})