import sharp from 'sharp';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { readFileSync, writeFileSync, existsSync, unlinkSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const assetsDir = join(__dirname, 'src', 'assets');

const files = ['2.png', '3.png', 'register.png', 'schoollogo.png'];

async function compressImage(filename) {
  const inputPath = join(assetsDir, filename);
  const outputPath = join(assetsDir, filename + '.compressed');
  
  if (!existsSync(inputPath)) {
    console.log(`文件不存在: ${filename}`);
    return;
  }

  console.log(`正在压缩: ${filename}`);
  
  try {
    await sharp(inputPath)
      .png({ 
        quality: 80,
        compressionLevel: 9,
        effort: 10
      })
      .toFile(outputPath);
    
    const inputStats = existsSync(inputPath) ? readFileSync(inputPath).length : 0;
    const outputStats = existsSync(outputPath) ? readFileSync(outputPath).length : 0;
    
    const savedBytes = inputStats - outputStats;
    const savedPercent = ((savedBytes / inputStats) * 100).toFixed(2);
    
    console.log(`  原始大小: ${(inputStats / 1024).toFixed(2)} KB`);
    console.log(`  压缩后: ${(outputStats / 1024).toFixed(2)} KB`);
    console.log(`  节省: ${savedPercent}%`);
    
    unlinkSync(inputPath);
    const fs = await import('fs');
    fs.renameSync(outputPath, inputPath);
    
    console.log(`  ✓ 已替换原文件`);
  } catch (error) {
    console.error(`  ✗ 压缩失败: ${error.message}`);
  }
}

async function main() {
  console.log('开始压缩图片...\n');
  
  for (const file of files) {
    await compressImage(file);
    console.log('');
  }
  
  console.log('压缩完成！');
}

main().catch(console.error);
