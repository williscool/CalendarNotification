#!/usr/bin/env node

/**
 * Script to clean log files for sharing
 * 
 * This script:
 * 1. Removes lines before eventsv9 or first test log tag (whichever comes first)
 * 2. Removes timestamps and other prefixes
 * 3. Trims stack traces based on exception type and test context:
 *    - Collapses specific known exceptions to a single line.
 *    - Keeps full stack traces for important common/test-specific exceptions.
 *    - Trims others to the first test log tag line or 10 lines, indicating collapsed count.
 */

import { Command } from 'commander';
import * as fs from 'fs';

// Constants
const EVENTS_DB_NAME = 'eventsv9';

// List of important exceptions that we want to keep in full
const IMPORTANT_EXCEPTIONS = [
    'AssertionError',
    'TestFailure',
    'Error:',
    'Exception:', // Keep general exceptions by default unless overridden
    'RuntimeException'
];

// Default keywords to filter out
const DEFAULT_FILTER_KEYWORDS = [
    'bluetooth',
    'bt_stack',
    'network',
    'wifi',
    'connectivity',
    'connection',
    'disconnected',
    'reconnected',
    'AdapterServiceConfig',
    'Accessing hidden field'
];

// Test-specific exceptions to keep in full
const TEST_SPECIFIC_EXCEPTIONS: Record<string, string[]> = {
    'CalendarMonitorServiceTest': [
        'NullPointerException'
    ],
    'CalendarMonitorServiceEventReminderTest': [
        'NullPointerException'
    ]
};

// Exceptions to display only the first line for
const KNOWN_EXCEPTIONS = [
    'java.util.NoSuchElementException: null, stack: com.github.quarck.calnotify.calendarmonitor.CalendarMonitorManual.scanNextEvent'
];

// Patterns that can appear in stack trace parentheses
const STACK_TRACE_PATTERNS = [
    '[^)]+\\.(kt|java):\\d+',  // File:line number pattern
    'Native Method',            // Native method reference
    'Unknown Source:\\d+',      // Unknown source with line number
    'D\\d+SyntheticClass:\\d+'  // Synthetic class patterns with D prefix
];

function isImportantException(line: string, testName?: string): boolean {
    // Check common important exceptions
    if (IMPORTANT_EXCEPTIONS.some(exception => line.includes(exception))) {
        return true;
    }
    
    // Check test-specific exceptions
    if (testName && TEST_SPECIFIC_EXCEPTIONS[testName]) {
        return TEST_SPECIFIC_EXCEPTIONS[testName].some(exception => line.includes(exception));
    }
    
    return false;
}

function isKnownException(line: string): boolean {
    return KNOWN_EXCEPTIONS.some(exception => line.includes(exception));
}

function isStackTraceLine(line: string): boolean {
    // First check if it's a known exception line
    if (isKnownException(line)) {
        return true;
    }

    // Check for patterns in parentheses
    const parenPattern = `\\((${STACK_TRACE_PATTERNS.join('|')})\\)`;
    if (new RegExp(parenPattern).test(line)) {
        return true;
    }
    
    // Check for file:line pattern after method name - more flexible pattern
    const fileLinePattern = /[a-zA-Z0-9_$]+\([^)]+\.(kt|java):\d+\)/;
    if (fileLinePattern.test(line)) {
        return true;
    }

    // Check for lines that look like they're part of a stack trace
    // (class names with dots, followed by method names)
    const stackTracePattern = /^[a-zA-Z0-9_$]+\.[a-zA-Z0-9_$]+/;
    return stackTracePattern.test(line);
}

interface ProgramOptions {
    verbose?: boolean;
    testName?: string;
    filterKeywords?: string[];
}

const program = new Command();

program
    .name('clean-logs')
    .description('Clean log files for sharing by removing timestamps and trimming stack traces')
    .argument('<test_log_tag>', 'log tag to identify test logs')
    .argument('<input_file>', 'input log file to clean')
    .argument('[output_file]', 'output file for cleaned logs (defaults to input_file.cleaned)')
    .option('-v, --verbose', 'show detailed error messages')
    .option('-t, --test-name <name>', 'test name for filtering exceptions (e.g., CalendarMonitorServiceTest)')
    .option('-f, --filter-keywords <keywords>', 'comma-separated list of keywords to filter out (e.g., "bluetooth,network")')
    .action((testLogTag: string, inputFile: string, outputFile: string | undefined, options: ProgramOptions) => {
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
            
            let startIndex: number;
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

            // Filter out lines containing specified keywords if any
            const userKeywords = options.filterKeywords || [];
            const keywords = [...DEFAULT_FILTER_KEYWORDS, ...userKeywords];
            
            console.log(`Filtering out lines containing the following keywords:`);
            keywords.forEach(keyword => console.log(`- ${keyword}`));
            
            if (keywords.length > 0) {
                // Count filtered lines for summary
                const originalLineCount = lines.length;
                lines = lines.filter(line => !keywords.some(keyword => line.toLowerCase().includes(keyword.toLowerCase())));
                const filteredLineCount = originalLineCount - lines.length;
                
                console.log(`Filtered out ${filteredLineCount} lines containing keywords.`);
            }

            // 3. Handle stack traces
            let cleanedLines: string[] = [];
            let inStackTrace = false;
            let errorLineCount = 0;
            let isImportantError = false;
            let linesToCollapse = 0; // Count lines collapsed due to limit

            for (let i = 0; i < lines.length; i++) {
                const line = lines[i];
                
                // Start of a potential stack trace
                if (!inStackTrace && (line.includes('Exception') || line.includes('Error:'))) {
                    // Check if it's a known exception first
                    if (isKnownException(line)) {
                        cleanedLines.push(line + ' ... [known issue stack trace collapsed]'); // Add only the first line
                        // Skip the rest of the stack trace
                        let nextLine = lines[i + 1];
                        while (nextLine && (isStackTraceLine(nextLine) || nextLine.includes('E CalendarMonitor:'))) {
                            i++;
                            nextLine = lines[i + 1];
                        }
                    } else {
                        // Start regular exception handling
                        inStackTrace = true;
                        errorLineCount = 0;
                        linesToCollapse = 0; // Reset collapse count for this trace
                        isImportantError = isImportantException(line, options.testName);
                        cleanedLines.push(line);
                    }
                } 
                // Inside a stack trace
                else if (inStackTrace) {
                    errorLineCount++;
                    
                    // Case 1: Found the test log tag - end trace here
                    if (line.includes(testLogTag)) {
                        cleanedLines.push(line);
                        // Collapse message handled when trace ends below
                    } 
                    // Case 2: Keep the line (important or within limit)
                    else if (isImportantError || errorLineCount <= 10) {
                        cleanedLines.push(line);
                    } 
                    // Case 3: Collapse the line (past limit for non-important)
                    else {
                        linesToCollapse++;
                    }

                    // Determine if the stack trace ends *after* this line
                    const nextLineExists = i + 1 < lines.length;
                    // Simple heuristic: ends if test tag found, or if next line doesn't look like a trace line
                    const endTraceDetected = line.includes(testLogTag) || 
                                            !nextLineExists || 
                                            (nextLineExists && !/^\s+at|^Caused by:/.test(lines[i + 1]));

                    if (endTraceDetected) {
                        // If lines were collapsed, add the message
                        if (linesToCollapse > 0) {
                             cleanedLines.push(`        ... [${linesToCollapse} lines collapsed]`); // Indented message
                        }
                        // Reset state for the next potential trace
                        inStackTrace = false;
                        linesToCollapse = 0; 
                    }
                } 
                // Regular line outside any stack trace
                else {
                    cleanedLines.push(line);
                }
            }

            // Write output
            console.log(`Writing cleaned log to: ${outputFile}`);
            fs.writeFileSync(outputFile, cleanedLines.join('\n'));
            console.log('Log cleaning completed successfully!');

        } catch (error: any) {
            console.error(`Error: ${error.code || 'Unknown error'}`);
            if (options.verbose) {
                console.error('Details:', error.message);
            }
            process.exit(1);
        }
    });

if (require.main === module) {
    program.parse();
} 