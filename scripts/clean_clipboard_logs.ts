#!/usr/bin/env node

/**
 * Script to clean logs from clipboard
 * Gets logs from clipboard, cleans them, and puts them back in clipboard
 * 
 * Usage: npx ts-node scripts/clean_clipboard_logs.ts <test_log_tag> [-v|--verbose] [-t|--test-name <name>]
 */

import { Command } from 'commander';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
// Will use dynamic import for execa

// Temporary file paths
const TEMP_INPUT = path.join(os.tmpdir(), 'clipboard_logs_input.txt');
const TEMP_OUTPUT = path.join(os.tmpdir(), 'clipboard_logs_output.txt');

interface ProgramOptions {
    verbose?: boolean;
    testName?: string;
}

const program = new Command();

program
    .name('clean-clipboard-logs')
    .description('Clean logs from clipboard and put them back')
    .argument('<test_log_tag>', 'log tag to identify test logs')
    .option('-v, --verbose', 'show detailed error messages')
    .option('-t, --test-name <name>', 'test name for filtering exceptions (e.g., CalendarMonitorServiceTest)')
    .action(async (testLogTag: string, options: ProgramOptions) => {
        console.log('Temporary files will be created at:');
        console.log('Input:', TEMP_INPUT);
        console.log('Output:', TEMP_OUTPUT);

        try {
            // Dynamically import execa (ESM module)
            const { execa } = await import('execa');
            
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
            const scriptDir = __dirname;
            const cleanLogsArgs = [path.join(scriptDir, 'clean_logs.ts'), testLogTag, TEMP_INPUT, TEMP_OUTPUT];
            if (options.testName) {
                cleanLogsArgs.push('-t', options.testName);
            }
            
            await execa('npx', ['ts-node', ...cleanLogsArgs], {
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

        } catch (error: any) {
            if (error.code === 'E2BIG') {
                console.error(`Output too big for clip.exe. Get it from: ${TEMP_OUTPUT}`);
                try {
                    console.log('Trying to open file in windows...');
                    const { execa } = await import('execa');
                    await execa('wsl-open', [TEMP_OUTPUT]);
                } catch (openError: any) {
                    console.error('Failed to open file:', openError.message);
                }
            } else {
                console.error(`Error: ${error.code || 'Unknown error'}`);
                if (options.verbose) {
                    console.error('Details:', error.message);
                }
            }
            process.exit(1);
        }
    });

program.parse(); 