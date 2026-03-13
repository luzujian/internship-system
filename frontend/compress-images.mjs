import sharp from 'sharp';
import fs from 'fs';
import path from 'path';

const assetsDir = '/home/gdmu/internship-system/frontend/src/assets';

async function compressImage(filename, options = {}) {
    const inputPath = path.join(assetsDir, filename);
    if (!fs.existsSync(inputPath)) {
        console.log();
        return;
    }
    
    const stats = fs.statSync(inputPath);
    console.log();
    
    try {
        let pipeline = sharp(inputPath);
        
        if (filename.endsWith('.png')) {
            pipeline = pipeline.png({ compressionLevel: 9, palette: true });
        } else if (filename.endsWith('.jpg') || filename.endsWith('.jpeg')) {
            pipeline = pipeline.jpeg({ quality: options.quality || 75, progressive: true });
        }
        
        await pipeline.toFile(inputPath + '.tmp');
        fs.renameSync(inputPath + '.tmp', inputPath);
        
        const newStats = fs.statSync(inputPath);
        const savings = ((stats.size - newStats.size) / stats.size * 100).toFixed(1);
        console.log();
    } catch (e) {
        console.log();
    }
}

async function main() {
    console.log('Starting image compression...\n');
    
    await compressImage('3.png');
    await compressImage('register.png');
    await compressImage('login-bg-with-logo.png');
    await compressImage('schoollogo.png');
    await compressImage('2.png');
    await compressImage('school.jpg', { quality: 70 });
    await compressImage('1.jpg', { quality: 70 });
    
    console.log('\nDone!');
}

main().catch(console.error);
