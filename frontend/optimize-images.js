// 图片优化脚本 - 在本地运行以压缩图片
// 使用方法：在本地项目 frontend 目录运行 node optimize-images.js

const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

const assetsDir = path.join(__dirname, "src/assets");

console.log("图片优化脚本");
console.log("==============");
console.log("");
console.log("请在本地项目 frontend 目录运行以下命令:");
console.log("");
console.log("1. 安装 sharp:");
console.log("   npm install -D sharp");
console.log("");
console.log("2. 运行优化脚本:");
console.log("   node optimize-images.js");
console.log("");
console.log("3. 重新构建:");
console.log("   npm run build");
console.log("");
console.log("图片文件位置：");

const images = fs.readdirSync(assetsDir).filter(f => /\.(png|jpg|jpeg)$/i.test(f));
images.forEach(img => {
  const stat = fs.statSync(path.join(assetsDir, img));
  console.log(`  ${img}: ${(stat.size / 1024 / 1024).toFixed(2)} MB`);
});
