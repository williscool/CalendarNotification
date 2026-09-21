#!/usr/bin/env node

/**
 * Live progress for a running Gradle unit-test build.
 *
 * @description Gradle's Test task prints nothing per-test by default, so a long
 *              run looks identical to a hung one: `--console=plain` shows
 *              `> Task :app:testX8664DebugUnitTest` and then silence for many
 *              minutes. This reads the JUnit XML files as they land instead,
 *              which works on a build that is ALREADY RUNNING -- no config
 *              change and no restart needed.
 *
 *              The build.gradle `testLogging` block covers new runs. This is for
 *              the case where a build is already in flight, or where you want a
 *              compact rolling summary rather than a full Gradle log.
 *
 * @usage node scripts/watch_test_progress.js [--results <dir>] [--interval <seconds>] [--once]
 *
 * @param {string} --results  - Directory holding JUnit XML results.
 *                              Defaults to the Windows-side x86_64 debug output.
 * @param {number} --interval - Seconds between polls (default 15).
 * @param {boolean} --once    - Print a single snapshot and exit.
 */

const fs = require('fs');
const path = require('path');

const DEFAULT_RESULTS =
  '/mnt/c/dev/CN/android/app/build/test-results/testX8664DebugUnitTest';
const DEFAULT_INTERVAL_SECONDS = 15;

/** Parse `--flag value` and bare `--flag` pairs out of argv. */
function parseArgs(argv) {
  const options = { results: DEFAULT_RESULTS, interval: DEFAULT_INTERVAL_SECONDS, once: false };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--once') {
      options.once = true;
    } else if (arg === '--results') {
      options.results = argv[++i];
    } else if (arg === '--interval') {
      options.interval = Number(argv[++i]) || DEFAULT_INTERVAL_SECONDS;
    }
  }
  return options;
}

/**
 * Pull the counts off a JUnit `<testsuite>` element.
 *
 * Deliberately regex rather than an XML library: the only thing needed is a
 * handful of attributes on the root element, and this repo has no direct XML
 * parser dependency worth adding for it. Files being written concurrently are
 * expected and simply yield null until complete.
 *
 * @returns {{name: string, tests: number, failures: number, time: string}|null}
 */
function readSuite(file) {
  let xml;
  try {
    xml = fs.readFileSync(file, 'utf8');
  } catch {
    return null;                       // vanished or unreadable mid-write
  }

  // Skip the leading `<?xml ...?>` declaration; the element we want is next.
  const start = xml.indexOf('<testsuite');
  if (start === -1) return null;                       // still being written
  const header = xml.slice(start, xml.indexOf('>', start) + 1);

  const attr = (key) => {
    const match = header.match(new RegExp(`${key}="([^"]*)"`));
    return match ? match[1] : '';
  };

  return {
    name: (attr('name').split('.').pop()) || '?',
    tests: Number(attr('tests')) || 0,
    failures: (Number(attr('failures')) || 0) + (Number(attr('errors')) || 0),
    time: attr('time'),
  };
}

/** Read every result file in the directory, oldest-written first. */
function collectSuites(resultsDir) {
  return fs
    .readdirSync(resultsDir)
    .filter((f) => f.endsWith('.xml'))
    .map((f) => path.join(resultsDir, f))
    .sort((a, b) => fs.statSync(a).mtimeMs - fs.statSync(b).mtimeMs)
    .map(readSuite)
    .filter(Boolean);
}

function printSnapshot(resultsDir) {
  const suites = collectSuites(resultsDir);

  const tests = suites.reduce((sum, s) => sum + s.tests, 0);
  const failures = suites.reduce((sum, s) => sum + s.failures, 0);

  const stamp = new Date().toTimeString().slice(0, 8);
  const failed = failures ? `  FAILURES=${failures}` : '';
  console.log(`[${stamp}] classes=${suites.length} tests=${tests}${failed}`);

  for (const suite of suites.slice(-2)) {
    console.log(`           ${suite.name}: ${suite.tests} tests, ${suite.time}s`);
  }
}

function main() {
  const options = parseArgs(process.argv.slice(2));

  if (!fs.existsSync(options.results)) {
    console.error(`No results directory yet: ${options.results}`);
    process.exit(1);
  }

  printSnapshot(options.results);
  if (options.once) return;

  setInterval(() => printSnapshot(options.results), options.interval * 1000);
}

main();
