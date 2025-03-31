#!/usr/bin/env node

/**
 * Script to clean log files for sharing
 * 
 * This script:
 * 1. Removes lines before eventsv9 or first CalMonitorSvcTest log
 * 2. Removes timestamps and other prefixes
 * 3. Trims stack traces to first CalMonitorSvcTest line in the stacktrace if it exists or the error line plus 10 if not
 *    (except for important exceptions like AssertionError)
 * 
 * Usage: node scripts/clean_logs.js <input_file> [output_file]
 */

import fs from 'fs';

// List of important exceptions that we want to keep in full
const IMPORTANT_EXCEPTIONS = [
    'AssertionError',
    'TestFailure',
    'Error:',
    'Exception:',
    'RuntimeException',
    // 'NullPointerException',
    'IllegalStateException',
    'IllegalArgumentException'
];

// Check arguments
if (process.argv.length < 3) {
    console.error('Usage: node clean_logs.js <input_file> [output_file]');
    process.exit(1);
}

const inputFile = process.argv[2];
const outputFile = process.argv[3] || inputFile + '.cleaned';

function isImportantException(line) {
    return IMPORTANT_EXCEPTIONS.some(exception => line.includes(exception));
}

try {
    // Read input file
    console.log(`Reading log file: ${inputFile}`);
    let content = fs.readFileSync(inputFile, 'utf8');
    let lines = content.split('\n');

    // 1. Find starting point (eventsv9 or CalMonitorSvcTest)
    let startIndex = lines.findIndex(line => 
        line.includes('eventsv9') || line.includes('CalMonitorSvcTest')
    );
    if (startIndex === -1) startIndex = 0;
    lines = lines.slice(startIndex);

    // 2. Clean timestamps and prefixes
    lines = lines.map(line => {
        // Match timestamp patterns and other common log prefixes
        return line.replace(/^\[?[\d\-:. ]+\]?\s*(\w+\/\w+\s*\(\s*\d+\))?\s*:?\s*/g, '');
    });

    // 3. Handle stack traces
    let cleanedLines = [];
    let inStackTrace = false;
    let errorLineCount = 0;
    let isImportantError = false;

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        
        if (line.includes('Exception') || line.includes('Error:')) {
            inStackTrace = true;
            errorLineCount = 0;
            isImportantError = isImportantException(line);
            cleanedLines.push(line);
        } else if (inStackTrace) {
            errorLineCount++;
            
            if (line.includes('CalMonitorSvcTest')) {
                cleanedLines.push(line);
                inStackTrace = false;
            } else if (isImportantError || errorLineCount <= 10) {
                cleanedLines.push(line);
            } else {
                inStackTrace = false;
            }
        } else {
            cleanedLines.push(line);
        }
    }

    // Write output
    console.log(`Writing cleaned log to: ${outputFile}`);
    fs.writeFileSync(outputFile, cleanedLines.join('\n'));
    console.log('Log cleaning completed successfully!');

} catch (error) {
    console.error('Failed to clean log file:', error.message);
    process.exit(1);
} 