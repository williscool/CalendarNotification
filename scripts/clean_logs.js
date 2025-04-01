#!/usr/bin/env node

/**
 * Script to clean log files for sharing
 * 
 * This script:
 * 1. Removes lines before eventsv9 or first test log tag (whichever comes first)
 * 2. Removes timestamps and other prefixes
 * 3. Trims stack traces to first test log tag line in the stacktrace if it exists or the error line plus 10 if not
 *    (except for important exceptions like AssertionError)
 */

import { Command } from 'commander';
import fs from 'fs';

// Constants
const EVENTS_DB_NAME = 'eventsv9';

// List of important exceptions that we want to keep in full
const IMPORTANT_EXCEPTIONS = [
    'AssertionError',
    'TestFailure',
    'Error:',
    'Exception:',
    'RuntimeException',
    'IllegalStateException',
    'IllegalArgumentException'
];

function isImportantException(line) {
    return IMPORTANT_EXCEPTIONS.some(exception => line.includes(exception));
}

const program = new Command();

program
    .name('clean-logs')
    .description('Clean log files for sharing by removing timestamps and trimming stack traces')
    .argument('<test_log_tag>', 'log tag to identify test logs')
    .argument('<input_file>', 'input log file to clean')
    .argument('[output_file]', 'output file for cleaned logs (defaults to input_file.cleaned)')
    .option('-v, --verbose', 'show detailed error messages')
    .action(async (testLogTag, inputFile, outputFile, options) => {
        outputFile = outputFile || inputFile + '.cleaned';

        try {
            // Read input file
            console.log(`Reading log file: ${inputFile}`);
            let content = fs.readFileSync(inputFile, 'utf8');
            let lines = content.split('\n');

            // First check if the test log tag exists in the log
            const hasTestLogTag = lines.some(line => line.includes(testLogTag));
            if (!hasTestLogTag) {
                console.error(`Error: Could not find any occurrences of test log tag '${testLogTag}' in the log file`);
                console.error('This might indicate you are cleaning logs for the wrong test or using the wrong log tag.');
                process.exit(1);
            }

            // 1. Find starting point (whichever comes first: eventsv9 or test log tag)
            let eventsv9Index = lines.findIndex(line => line.includes(EVENTS_DB_NAME));
            let testLogTagIndex = lines.findIndex(line => line.includes(testLogTag));
            
            let startIndex;
            if (eventsv9Index === -1 && testLogTagIndex === -1) {
                console.error(`Error: Could not find either '${EVENTS_DB_NAME}' or '${testLogTag}'`);
                process.exit(1);
            } else if (eventsv9Index === -1) {
                startIndex = testLogTagIndex;
            } else if (testLogTagIndex === -1) {
                startIndex = eventsv9Index;
            } else {
                startIndex = Math.min(eventsv9Index, testLogTagIndex);
            }
            
            console.log(`Starting at line ${startIndex + 1} (${eventsv9Index === startIndex ? EVENTS_DB_NAME : testLogTag} found first)`);
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
                    
                    if (line.includes(testLogTag)) {
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
            console.error(`Error: ${error.code || 'Unknown error'}`);
            if (options.verbose) {
                console.error('Details:', error.message);
            }
            process.exit(1);
        }
    });

program.parse(); 