import sharp from "sharp";
import fs from "fs";

const files = ["3.png", "register.png", "login-bg-with-logo.png", "schoollogo.png", "2.png", "school.jpg", "1.jpg"];

for (const file of files) {
    if (!fs.existsSync(file)) continue;
    const stats = fs.statSync(file);
    console.log("Processing:", file, Math.round(stats.size/1024/1024*100)/100, "MB");
    
    try {
        const img = sharp(file);
        if (file.endsWith(".png")) {
            await img.png({ compressionLevel: 9, palette: true }).toFile(file + ".tmp");
        } else {
            await img.jpeg({ quality: 70, progressive: true }).toFile(file + ".tmp");
        }
        
        fs.renameSync(file + ".tmp", file);
        const newStats = fs.statSync(file);
        const saved = Math.round((stats.size - newStats.size) / stats.size * 1000) / 10;
        console.log("  Result:", Math.round(newStats.size/1024/1024*100)/100, "MB (saved", saved + "_%)");
    } catch(e) {
        console.log("  Error:", e.message);
    }
}
console.log("Done!");

