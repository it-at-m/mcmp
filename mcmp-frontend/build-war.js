import fs from "fs";
import path from "path";
import archiver from "archiver";
import { fileURLToPath } from "url";

// Recreate __dirname because it is not automatically available in ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Target folder for the WAR file
const buildFolder = path.resolve(__dirname, "build");
const distFolder = path.resolve(__dirname, "dist");
const warFilePath = path.resolve(buildFolder, "mcmp-frontend.war");

// Create the folder if it doesn't exist
if (!fs.existsSync(buildFolder)) {
  fs.mkdirSync(buildFolder);
}

// Delete the existing WAR file if it exists
if (fs.existsSync(warFilePath)) {
  console.log("Existing WAR file found. Deleting...");
  fs.unlinkSync(warFilePath); // Remove the file
}

if (!fs.existsSync(distFolder)) {
  console.error("Please run `npm run build` first to create the dist folder.");
  process.exit(1);
}

// Get the version from package.json
const packageJsonPath = path.resolve(__dirname, "package.json");
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8"));
const version = packageJson.version || "0.0.0";

// Create a version.txt file in the META-INF folder
const metaInfFolder = path.resolve(__dirname, "dist", "META-INF");
if (!fs.existsSync(metaInfFolder)) {
  fs.mkdirSync(metaInfFolder, { recursive: true });
}
const versionFilePath = path.resolve(metaInfFolder, "version.txt");
fs.writeFileSync(versionFilePath, `Version: ${version}\nBuild-Time: ${new Date().toISOString()}`, "utf8");
console.log(version);
console.log("Version file created: META-INF/version.txt");

// Create the WAR file
const output = fs.createWriteStream(warFilePath);
const archive = archiver("zip", {
  zlib: { level: 9 }, // Use maximum compression
});

output.on("close", () => {
  console.log(`${archive.pointer()} total bytes were packed into ${warFilePath}.`);
  console.log("WAR archive has been successfully created.");
});

archive.on("error", (err) => {
  throw err;
});

archive.pipe(output);

// Add everything from the "dist" folder into the WAR archive
archive.directory(distFolder, false);
archive.finalize();



