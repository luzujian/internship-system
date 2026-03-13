import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

// 为ES模块获取__dirname
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// 定义需要更新的目录路径
const apiDir = path.join(__dirname, 'src', 'api');

// 定义要替换的导入路径
const replacements = [
  { from: "import request from './api'", to: "import request from '@/utils/request'" },
  { from: "import request from '@/services/api'", to: "import request from '@/utils/request'" }
];

// 递归遍历目录
async function traverseDirectory(dir) {
  try {
    const files = await fs.readdir(dir, { withFileTypes: true });
    
    for (const file of files) {
      const fullPath = path.join(dir, file.name);
      
      if (file.isDirectory()) {
        // 如果是目录，递归处理
        await traverseDirectory(fullPath);
      } else if (file.isFile() && path.extname(file.name) === '.js') {
        // 如果是.js文件，处理它
        await updateFile(fullPath);
      }
    }
  } catch (error) {
    console.error(`Error traversing directory ${dir}:`, error);
  }
}

// 更新文件中的导入路径
async function updateFile(filePath) {
  try {
    // 读取文件内容
    let content = await fs.readFile(filePath, 'utf8');
    
    // 检查是否需要更新
    let updated = false;
    
    // 应用所有替换
    replacements.forEach(replacement => {
      if (content.includes(replacement.from)) {
        content = content.replace(new RegExp(replacement.from, 'g'), replacement.to);
        updated = true;
      }
    });
    
    // 如果有更新，写回文件
    if (updated) {
      await fs.writeFile(filePath, content, 'utf8');
      console.log(`Updated: ${filePath}`);
    }
  } catch (error) {
    console.error(`Error updating file ${filePath}:`, error);
  }
}

// 开始处理
async function main() {
  console.log('Updating import paths in api directory...');
  await traverseDirectory(apiDir);
  console.log('Update completed!');
}

// 运行主函数
main();