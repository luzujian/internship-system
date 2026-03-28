// 图片优化脚本配置
// 这个脚本包含了项目中大图片资源的优化建议

// 大图片资源列表及优化建议
const largeImages = [
  {
    path: 'src/assets/register.png',
    size: '~4MB',
    currentFormat: 'PNG',
    recommendedFormat: 'WebP',
    compressionSuggestion: '使用PNG压缩工具（如pngquant）压缩至100-200KB',
    usage: '注册页面背景图'
  },
  {
    path: 'src/assets/school.jpg',
    size: '~1.57MB',
    currentFormat: 'JPG',
    recommendedFormat: 'WebP',
    compressionSuggestion: '压缩至200-300KB，并考虑降低分辨率',
    usage: '可能用于学校介绍或首页背景'
  }
];

// 图片优化命令建议
const optimizationCommands = [
  '# 使用imagemagick压缩图片（需要先安装：https://imagemagick.org/）',
  'magick convert src/assets/register.png -resize 80% -quality 80 src/assets/register-optimized.webp',
  'magick convert src/assets/school.jpg -resize 80% -quality 75 src/assets/school-optimized.webp',
  '',
  '# 如果没有imagemagick，也可以使用在线工具：',
  '# - https://tinypng.com/ (支持PNG和JPG压缩)',
  '# - https://squoosh.app/ (支持WebP转换)'
];

// 导出配置，便于在其他地方使用
module.exports = {
  largeImages,
  optimizationCommands
};

// 如果直接运行此脚本，打印优化建议
if (require.main === module) {
  console.log('=== 图片优化建议 ===');
  console.log('以下图片文件较大，建议进行优化：\n');
  
  largeImages.forEach((image, index) => {
    console.log(`${index + 1}. ${image.path}`);
    console.log(`   当前大小: ${image.size}`);
    console.log(`   当前格式: ${image.currentFormat}`);
    console.log(`   建议格式: ${image.recommendedFormat}`);
    console.log(`   优化建议: ${image.compressionSuggestion}`);
    console.log(`   用途: ${image.usage}\n`);
  });
  
  console.log('=== 优化命令示例 ===');
  optimizationCommands.forEach(command => {
    console.log(command);
  });
  
  console.log('\n=== 前端代码优化建议 ===');
  console.log('1. 在组件中实现图片懒加载');
  console.log('2. 使用v-lazy指令或IntersectionObserver API');
  console.log('3. 替换大图片引用为优化后的WebP版本');
}