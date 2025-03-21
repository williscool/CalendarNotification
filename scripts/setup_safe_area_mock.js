/**
 * Script to create a mock implementation of react-native-safe-area-context
 * Run this script with: node scripts/setup_safe_area_mock.js
 */

const fs = require('fs');
const path = require('path');

// Define paths
const rootDir = path.resolve(__dirname, '..');
const mockDir = path.join(rootDir, 'node_modules', 'react-native-safe-area-context');
const mockLibDir = path.join(mockDir, 'lib');
const mockLibModuleDir = path.join(mockLibDir, 'module');
const mockLibCommonjsDir = path.join(mockLibDir, 'commonjs');
const mockLibTypescriptDir = path.join(mockLibDir, 'typescript');
const templatesDir = path.join(__dirname, 'safe-area-mock-templates');

// Create directories if they don't exist
const createDirIfNotExists = (dir) => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
    console.log(`Created directory: ${dir}`);
  }
};

// Read a file from templates
const readTemplateFile = (fileName) => {
  const filePath = path.join(templatesDir, fileName);
  try {
    return fs.readFileSync(filePath, 'utf8');
  } catch (error) {
    console.error(`Error reading template file ${fileName}:`, error);
    process.exit(1);
  }
};

// Create package.json
const createPackageJson = () => {
  try {
    const packageJsonContent = readTemplateFile('package.json');
    fs.writeFileSync(path.join(mockDir, 'package.json'), packageJsonContent);
    console.log('Created package.json');
  } catch (error) {
    console.error('Error creating package.json:', error);
  }
};

// Create module index.js
const createModuleIndex = () => {
  try {
    const moduleCode = readTemplateFile('module-index.js');
    fs.writeFileSync(path.join(mockLibModuleDir, 'index.js'), moduleCode);
    console.log('Created module/index.js');
  } catch (error) {
    console.error('Error creating module/index.js:', error);
  }
};

// Create commonjs index.js
const createCommonjsIndex = () => {
  try {
    const commonjsCode = readTemplateFile('commonjs-index.js');
    fs.writeFileSync(path.join(mockLibCommonjsDir, 'index.js'), commonjsCode);
    console.log('Created commonjs/index.js');
  } catch (error) {
    console.error('Error creating commonjs/index.js:', error);
  }
};

// Create TypeScript definition
const createTypeScriptDefinition = () => {
  try {
    const typeScriptCode = readTemplateFile('typescript-index.d.ts');
    fs.writeFileSync(path.join(mockLibTypescriptDir, 'index.d.ts'), typeScriptCode);
    console.log('Created typescript/index.d.ts');
  } catch (error) {
    console.error('Error creating typescript/index.d.ts:', error);
  }
};

// Ensure templates exist before proceeding
const validateTemplates = () => {
  const requiredTemplates = [
    'package.json',
    'module-index.js',
    'commonjs-index.js',
    'typescript-index.d.ts'
  ];
  
  const missingTemplates = requiredTemplates.filter(template => {
    const filePath = path.join(templatesDir, template);
    return !fs.existsSync(filePath);
  });
  
  if (missingTemplates.length > 0) {
    console.error('Error: Missing template files:', missingTemplates.join(', '));
    console.error(`Please ensure all template files exist in ${templatesDir}`);
    process.exit(1);
  }
};

// Main function to create the mock
const createMock = () => {
  console.log('Setting up react-native-safe-area-context mock...');
  
  // First validate that all templates exist
  validateTemplates();
  
  // Create directories
  createDirIfNotExists(mockDir);
  createDirIfNotExists(mockLibDir);
  createDirIfNotExists(mockLibModuleDir);
  createDirIfNotExists(mockLibCommonjsDir);
  createDirIfNotExists(mockLibTypescriptDir);
  
  // Create files from templates
  createPackageJson();
  createModuleIndex();
  createCommonjsIndex();
  createTypeScriptDefinition();
  
  console.log('Successfully set up react-native-safe-area-context mock!');
};

// Execute
createMock(); 