# Log Cleaning Scripts Documentation

## Original Prompt
```
can you write script inspired by bundle android that usese these 2 commands 

to clean logs for me to share with you

1. chop off all lines before the line with eventsv9 or before the first test log tag
2. get rid of the time stamp and other cruft before the line status 
3. cut any exceptions to the first test log tag line in the stacktrace if it exists or the error line plus 10 if not
```

## Scripts Overview

Two scripts have been created to handle log cleaning:

1. `scripts/clean_logs.js` - Core log cleaning functionality
2. `scripts/clean_clipboard_logs.js` - Clipboard integration wrapper

### Core Log Cleaning Script (`clean_logs.js`)

This script handles the actual log cleaning process with the following features:

1. **Starting Point Detection**
   - Removes all lines before the first occurrence of "eventsv9" or the specified test log tag
   - Requires either "eventsv9" or the test log tag to be found (exits with error if neither is found)

2. **Timestamp and Prefix Cleaning**
   - Removes timestamps and other log prefixes
   - Cleans up common log formatting cruft

3. **Stack Trace Handling**
   - For important exceptions (see list below), keeps the full stack trace
   - For other exceptions, trims to either:
     - First occurrence of the test log tag in the stack trace, or
     - Error line plus 10 lines

#### Important Exceptions (Full Stack Trace Preserved)
- AssertionError
- TestFailure
- Error:
- Exception:
- RuntimeException
- NullPointerException
- IllegalStateException
- IllegalArgumentException

### Clipboard Integration Script (`clean_clipboard_logs.js`)

This script provides a convenient way to clean logs directly from the clipboard:

1. **Input**
   - Reads content from Windows clipboard using PowerShell
   - Saves to temporary file for processing

2. **Processing**
   - Uses `clean_logs.js` to clean the content
   - Saves cleaned output to temporary file

3. **Output**
   - Copies cleaned content back to Windows clipboard
   - Preserves temporary files for inspection

#### Temporary Files
- Input: `/tmp/clipboard_logs_input.txt`
- Output: `/tmp/clipboard_logs_output.txt`

## Usage

### Direct File Processing
```bash
node scripts/clean_logs.js <test_log_tag> <input_file> [output_file]
```

### Clipboard Processing
```bash
node scripts/clean_clipboard_logs.js <test_log_tag>
# or
yarn clean-logs <test_log_tag>
```

## Dependencies
- Node.js
- execa (for process execution)
- Windows PowerShell (for clipboard access)
- Windows clip.exe (for clipboard output)
