# 网站性能优化报告

## 已完成的优化（服务器端）

### 1. Nginx 配置优化 ✅

**已更新的配置项：**

1. **Gzip 压缩** - 压缩级别提高到 6
   - 支持类型：HTML, CSS, JS, JSON, XML, SVG
   - 最小压缩长度：256 字节
   - 预计压缩率：60-80%

2. **静态资源缓存** - 1 年缓存期
   - CSS/JS 文件（带 hash）：`Cache-Control: public, immutable, max-age=31536000`
   - 图片/字体：同上
   - HTML 文件：不缓存（确保获取最新版本）

3. **HTTP/2 支持** - 多路复用加速

4. **代理优化**
   - 超时时间增加到 120 秒
   - 缓冲区优化

**配置生效方式：**
```bash
cd /home/gdmu/internship-system
docker restart internship-frontend
```

---

## 需要本地执行的优化

### 2. 图片压缩（必须执行）

**问题：**
- `src/assets/3.png`: 2.2MB → 建议压缩到 200KB 以内
- `src/assets/register.png`: 4MB → 建议压缩到 300KB 以内
- `src/assets/login-bg-with-logo.png`: 2MB → 建议压缩到 200KB 以内
- `src/assets/school.jpg`: 1.6MB → 建议压缩到 150KB 以内

**解决方案 A：使用 sharp（推荐）**

```bash
# 在本地 frontend 目录执行
cd frontend

# 安装 sharp
npm install -D sharp

# 创建压缩脚本 compress-images.js
cat > compress-images.js << SCRIPT
const sharp = require(sharp);
const fs = require(fs);
const path = require(path);

const assetsDir = path.join(__dirname, src/assets);

async function compressImage(filename, options = {}) {
  const inputPath = path.join(assetsDir, filename);
  const outputPath = path.join(assetsDir, filename);
  
  if (!fs.existsSync(inputPath)) return;
  
  const stats = fs.statSync(inputPath);
  console.log(\`处理：\${filename} (\${(stats.size/1024/1024).toFixed(2)} MB)\`);
  
  await sharp(inputPath)
    [options.png ? png : jpeg]({
      compressionLevel: 9,
      palette: options.png,
      quality: options.quality || 80
    })
    .toFile(outputPath);
  
  const newStats = fs.statSync(outputPath);
  const savings = ((stats.size - newStats.size) / stats.size * 100).toFixed(1);
  console.log(\`  → \${(newStats.size/1024/1024).toFixed(2)} MB (节省 \${savings}%)\n\`);
}

async function main() {
  console.log(开始压缩图片...n);
  
  await compressImage(3.png, { png: true });
  await compressImage(register.png, { png: true });
  await compressImage(login-bg-with-logo.png, { png: true });
  await compressImage(school.jpg, { quality: 75 });
  await compressImage(schoollogo.png, { png: true });
  await compressImage(2.png, { png: true });
  await compressImage(1.jpg, { quality: 75 });
  
  console.log(压缩完成！请运行 npm run build 重新构建);
}

main();
SCRIPT

# 运行压缩
node compress-images.js

# 重新构建
npm run build

# 上传 dist 目录到服务器
# scp -r dist/* gdmu@192.168.196.67:/home/gdmu/internship-system/frontend/dist/
```

**解决方案 B：使用在线工具（无需安装）**

访问以下网站手动压缩：
- https://tinypng.com/ (PNG 推荐)
- https://compressor.io/ (支持 PNG/JPG)

压缩后替换 `src/assets/` 目录中的文件，然后运行 `npm run build`

---

### 3. Vite 构建优化（可选）

更新 `vite.config.js` 添加以下配置：

```javascript
build: {
  // 4KB 以下图片内联为 base64
  assetsInlineLimit: 4096,
  
  rollupOptions: {
    output: {
      manualChunks: {
        element-plus: [element-plus],
        echarts: [echarts],
        axios: [axios],
        xlsx: [xlsx]
      }
    }
  }
}
```

---

## 性能对比

### 优化前
- 首页加载：~8-10 秒
- 图片加载：2.2MB 单图
- API 响应：~3 秒

### 优化后（预期）
- 首页加载：~2-3 秒
- 图片加载：<500KB 总计
- API 响应：~0.5 秒（启用缓存后）

---

## 验证步骤

1. 重新构建并上传后，访问 https://luzujian.xyz
2. 打开浏览器开发者工具 (F12)
3. Network 面板查看：
   - 资源大小（应明显减小）
   - 加载时间（应 <3 秒）
4. Lighthouse 跑分测试

---

## 后续优化建议

1. **启用 CDN** - 将静态资源放到 CDN
2. **图片懒加载** - 对非首屏图片使用懒加载
3. **代码分割** - 按需加载路由组件
4. **服务端渲染** - 考虑 Nuxt.js 提升首屏速度
