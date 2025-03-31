#!/usr/bin/env node

/**
 * Script to clean logs from clipboard
 * Gets logs from clipboard, cleans them, and puts them back in clipboard
 * 
 * Usage: node scripts/clean_clipboard_logs.js
 */

import { execa } from 'execa';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { fileURLToPath } from 'url';

// Get __dirname equivalent in ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Temporary file paths
const TEMP_INPUT = path.join(os.tmpdir(), 'clipboard_logs_input.txt');
const TEMP_OUTPUT = path.join(os.tmpdir(), 'clipboard_logs_output.txt');

console.log('Temporary files will be created at:');
console.log('Input:', TEMP_INPUT);
console.log('Output:', TEMP_OUTPUT);

async function main() {
    try {
        // Get logs from clipboard
        console.log('Reading logs from clipboard...');
        const { stdout: clipboardContent } = await execa('powershell.exe', ['-command', 'Get-Clipboard']);
        console.log('Clipboard content length:', clipboardContent.length);
        
        // Save to temporary file
        console.log('Writing to temporary input file...');
        fs.writeFileSync(TEMP_INPUT, clipboardContent);
        console.log('Input file size:', fs.statSync(TEMP_INPUT).size, 'bytes');
        
        // Clean the logs using our existing script
        console.log('Cleaning logs...');
        await execa('node', [path.join(__dirname, 'clean_logs.js'), TEMP_INPUT, TEMP_OUTPUT], {
            stdio: 'inherit'
        });
        
        // Read cleaned content
        console.log('Reading cleaned output file...');
        const cleanedContent = fs.readFileSync(TEMP_OUTPUT, 'utf8');
        console.log('Cleaned content length:', cleanedContent.length);
        
        // Put cleaned content back in clipboard using shell command
        console.log('Copying cleaned logs back to clipboard...');
        await execa('sh', ['-c', `echo "${cleanedContent.replace(/"/g, '\\"')}" | clip.exe`]);
        
        console.log('\nTemporary files available for inspection:');
        console.log('Input file:', TEMP_INPUT);
        console.log('Output file:', TEMP_OUTPUT);
        console.log('\nDone! Cleaned logs are now in your clipboard.');

    } catch (error) {
        console.error('Failed to process clipboard:', error.message);
        process.exit(1);
    }
}

main().catch(error => {
    console.error('Unhandled error:', error);
    process.exit(1);
}); 