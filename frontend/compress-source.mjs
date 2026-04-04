import sharp from 'sharp';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { existsSync, unlinkSync, renameSync, statSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const assetsDir = join(__dirname, 'src', 'assets');

async function compressImage(filename, options = {}) {
  const inputPath = join(assetsDir, filename);
  const outputPath = join(assetsDir, filename + '.compressed');
  
  if (!existsSync(inputPath)) {
    console.log(`文件不存在: ${filename}`);
    return;
  }

  const inputSize = statSync(inputPath).size;
  console.log(`正在压缩: ${filename} (${(inputSize / 1024).toFixed(2)} KB)`);
  
  try {
    let image = sharp(inputPath);
    
    if (options.resize) {
      image = image.resize(options.resize);
    }
    
    if (options.quality) {
      image = image.png({ 
        compressionLevel: 9,
        effort: 10,
        quality: options.quality
      });
    } else {
      image = image.png({ 
        compressionLevel: 9,
        effort: 10
      });
    }
    
    await image.toFile(outputPath);
    
    const outputSize = statSync(outputPath).size;
    const savedPercent = ((1 - outputSize / inputSize) * 100).toFixed(1);
    
    if (outputSize < inputSize) {
      console.log(`  压缩后: ${(outputSize / 1024).toFixed(2)} KB (节省 ${savedPercent}%)`);
      unlinkSync(inputPath);
      renameSync(outputPath, inputPath);
      console.log(`  已替换原文件`);
    } else {
      console.log(`  压缩后变大，保留原文件`);
      unlinkSync(outputPath);
    }
  } catch (error) {
    console.error(`  压缩失败: ${error.message}`);
  }
}

async function main() {
  console.log('开始压缩源图片...\n');
  
  await compressImage('2.png');
  console.log('');
  
  await compressImage('3.png');
  console.log('');
  
  await compressImage('register.png');
  console.log('');
  
  await compressImage('schoollogo.png');
  console.log('');
  
  console.log('压缩完成！');
}

main().catch(console.error);
