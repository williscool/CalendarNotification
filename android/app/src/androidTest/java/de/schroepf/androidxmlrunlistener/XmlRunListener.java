package de.schroepf.androidxmlrunlistener;

import android.app.Instrumentation;
import android.os.Build;
import android.util.Log;
import android.util.Xml;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import androidx.test.internal.runner.listener.InstrumentationRunListener;

/**
 * An InstrumentationRunListener which writes the test results to JUnit style XML files to the
 * {@code /storage/emulated/0/Android/data/<package-name>/files/} directory on the device.
 * <p>
 * This listener will not override existing XML reports and instead will generate unique file names
 * for the report file (report-0.xml, report-1.xml ...).
 * <p>
 * This is useful for running within a orchestrated setup where each test runs in a separate process.
 * <p>
 * Note: It is necessary to uninstall the app from previous runs (clean up the report directory manually)
 * before running the orchestrator or previous files will persist.
 *
 * @see <a href="https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator">https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator</a>
 */
public class XmlRunListener extends InstrumentationRunListener {
    private static final String TAG = XmlRunListener.class.getSimpleName();

    private static final String ENCODING_UTF_8 = "utf-8";
    private static final String NAMESPACE = null;

    private static final String TAG_SUITE = "testsuite";
    private static final String TAG_PROPERTIES = "properties";
    private static final String TAG_PROPERTY = "property";
    private static final String TAG_CASE = "testcase";
    private static final String TAG_FAILURE = "failure";
    private static final String TAG_SKIPPED = "skipped";

    private static final String ATTRIBUTE_CLASS = "classname";
    private static final String ATTRIBUTE_ERRORS = "errors";
    private static final String ATTRIBUTE_FAILURES = "failures";
    private static final String ATTRIBUTE_MESSAGE = "message";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_SKIPPED = "skipped";
    private static final String ATTRIBUTE_TESTS = "tests";
    private static final String ATTRIBUTE_TIME = "time";
    private static final String ATTRIBUTE_TIMESTAMP = "timestamp";
    private static final String ATTRIBUTE_TYPE = "type";
    private static final String ATTRIBUTE_VALUE = "value";

    private FileOutputStream outputStream;

    private final XmlSerializer xmlSerializer;

    private TestRunResult runResult;


    public XmlRunListener() {
        this(Xml.newSerializer());
    }

    public XmlRunListener(XmlSerializer xmlSerializer) {
        this.xmlSerializer = xmlSerializer;
    }

    @Override
    public void setInstrumentation(Instrumentation instr) {
        super.setInstrumentation(instr);

        try {
            File outputFile = getOutputFile(instr);

            Log.d(TAG, "setInstrumentation: outputFile: " + outputFile);
            outputStream = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open report file", e);
            throw new RuntimeException("Unable to open report file: " + e.getMessage(), e);
        }

        try {
            xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xmlSerializer.setOutput(outputStream, ENCODING_UTF_8);
            xmlSerializer.startDocument(ENCODING_UTF_8, true);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open serializer", e);
            throw new RuntimeException("Unable to open serializer: " + e.getMessage(), e);
        }
    }

    /**
     * Get a {@link File} for the test report.
     * <p>
     * Override this to change the default file location.
     *
     * @param instrumentation the {@link Instrumentation} for this test run
     * @return the file which should be used to store the XML report of the test run
     */
    protected File getOutputFile(Instrumentation instrumentation) {
        // First try to get the location from instrumentation arguments
        String resultFile = instrumentation.getArguments().getString("resultFile");
        if (resultFile != null && !resultFile.isEmpty()) {
            // Check if the directory exists and is writable
            File file = new File(resultFile);
            File directory = file.getParentFile();
            
            Log.d(TAG, "resultFile specified in arguments: " + resultFile);
            
            if (directory != null && (directory.exists() || directory.mkdirs())) {
                try {
                    // Try to create the file to verify it's writable
                    boolean fileCreated = file.createNewFile();
                    if (fileCreated || file.exists()) {
                        Log.d(TAG, "Successfully verified write access to resultFile: " + resultFile);
                        return file;
                    } else {
                        Log.w(TAG, "Could not create file at resultFile: " + resultFile);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "IOException when trying to create resultFile: " + e.getMessage());
                } catch (SecurityException e) {
                    Log.w(TAG, "SecurityException when trying to create resultFile: " + e.getMessage());
                }
            } else {
                Log.w(TAG, "Cannot access directory for resultFile: " + resultFile);
            }
        }
        
        // Try several alternative locations that are writable
        String[] potentialPaths = new String[] {
            "/data/local/tmp/test-results.xml",
            "/sdcard/test-results.xml",
            "/storage/emulated/0/test-results.xml"
        };
        
        for (String path : potentialPaths) {
            try {
                File file = new File(path);
                boolean created = file.createNewFile(); // Will fail if file exists or no permission
                if (created || file.exists()) {
                    Log.d(TAG, "Using writable location: " + path);
                    return file;
                }
            } catch (Exception e) {
                Log.d(TAG, "Cannot use location " + path + ": " + e.getMessage());
            }
        }
        
        // Final fallback - use the app's cache directory which should always be writable
        try {
            File cacheDir = instrumentation.getTargetContext().getCacheDir();
            File outputFile = new File(cacheDir, "test-results.xml");
            Log.d(TAG, "Using app cache directory: " + outputFile.getAbsolutePath());
            
            // Create the file to verify permissions
            outputFile.createNewFile();
            
            // Also copy to the /data/local/tmp location if possible (permissions may not allow)
            File dataLocalTmpFile = new File("/data/local/tmp/test-results.xml");
            // This line gets the Context.MODE_WORLD_READABLE constant value (1) since it's deprecated
            final int MODE_WORLD_READABLE = 1;
            
            try {
                // Create a symbolic link or use ProcessBuilder if possible
                Process process = Runtime.getRuntime().exec(
                    "ln -sf " + outputFile.getAbsolutePath() + " " + dataLocalTmpFile.getAbsolutePath());
                process.waitFor();
                Log.d(TAG, "Created symlink to test results at: " + dataLocalTmpFile.getAbsolutePath());
            } catch (Exception e) {
                Log.d(TAG, "Could not create symlink: " + e.getMessage());
            }
            
            return outputFile;
        } catch (Exception e) {
            Log.w(TAG, "Failed to use app cache directory: " + e.getMessage());
        }
        
        // Last resort - use external files dir
        Log.d(TAG, "Using external files directory as last resort");
        return new File(instrumentation.getTargetContext().getExternalFilesDir(null), getFileName(instrumentation));
    }

    /**
     * Get a file name for the test report.
     * <p>
     * Override this to create different file patterns.
     *
     * @param instrumentation the {@link Instrumentation} for this test run
     * @return the file name which should be used to store the XML report of the test run
     */
    protected String getFileName(Instrumentation instrumentation) {
        return findFile("report", 0, ".xml", instrumentation);
    }

    private String findFile(String fileNamePrefix, int iterator, String fileNamePostfix, Instrumentation instr) {
        String fileName = fileNamePrefix + "-" + iterator + fileNamePostfix;
        File file = new File(instr.getTargetContext().getExternalFilesDir(null), fileName);

        if (file.exists()) {
            return findFile(fileNamePrefix, iterator + 1, fileNamePostfix, instr);
        } else {
            return file.getName();
        }
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        runResult = new TestRunResult();
        runResult.runStarted(description);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        runResult.runFinished(result);

        printTestResults();
    }

    @Override
    public void testStarted(Description description) throws Exception {
        runResult.testStarted(description);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        runResult.testFinished(description);
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        runResult.testFailure(failure);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        runResult.testAssumptionFailure(failure);
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        runResult.testIgnored(description);
    }

    private void printTestResults() throws IOException {

        xmlSerializer.startTag(NAMESPACE, TAG_SUITE);
        String name = runResult.getTestSuiteName();
        if (name != null && name.isEmpty()) {
            xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_NAME, name);
        }
        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_TESTS, Integer.toString(runResult.getAllTests().size()));
        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_FAILURES, Integer.toString(runResult.getFailedTests().size() + runResult.getAssumptionFailedTests().size()));

        // legacy - there are no errors in JUnit4
        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_ERRORS, "0");
        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_SKIPPED, Integer.toString(runResult.getIgnoredTests().size()));

        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_TIME, Double.toString((double) runResult.getElapsedTime() / 1000.f));
        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_TIMESTAMP, runResult.startTimeAsIso());

        xmlSerializer.startTag(NAMESPACE, TAG_PROPERTIES);
        printProperty("device.manufacturer", Build.MANUFACTURER);
        printProperty("device.model", Build.MODEL);
        printProperty("device.apiLevel", String.valueOf(Build.VERSION.SDK_INT));
        xmlSerializer.endTag(NAMESPACE, TAG_PROPERTIES);

        Map<Description, TestResult> testResults = runResult.getAllTests();
        for (Map.Entry<Description, TestResult> testEntry : testResults.entrySet()) {
            xmlSerializer.startTag(NAMESPACE, TAG_CASE);
            xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_NAME, testEntry.getKey().getDisplayName());
            xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_CLASS, testEntry.getKey().getClassName());
            long elapsedTimeMs = testEntry.getValue().getElapsedTime();
            xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_TIME, Double.toString((double) elapsedTimeMs / 1000.f));

            switch (testEntry.getValue().getStatus()) {
                case FAILURE:
                    Failure failure = testEntry.getValue().getFailure();
                    xmlSerializer.startTag(NAMESPACE, TAG_FAILURE);

                    String type = failure.getException().getClass().getName();
                    if (type != null && !type.isEmpty()) {
                        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_TYPE, type);
                    }

                    String message = failure.getMessage();
                    if (message != null && !message.isEmpty()) {
                        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_MESSAGE, message);
                    }

                    xmlSerializer.text(sanitize(testEntry.getValue().getFailure().getTrace()));
                    xmlSerializer.endTag(NAMESPACE, TAG_FAILURE);
                    break;


                case ASSUMPTION_FAILURE:
                case IGNORED:
                    xmlSerializer.startTag(NAMESPACE, TAG_SKIPPED);
                    xmlSerializer.endTag(NAMESPACE, TAG_SKIPPED);
                    break;
            }

            xmlSerializer.endTag(NAMESPACE, TAG_CASE);
        }

        xmlSerializer.endTag(NAMESPACE, TAG_SUITE);
        xmlSerializer.endDocument();
        xmlSerializer.flush();
    }

    private void printProperty(String name, String value) throws IOException {
        xmlSerializer.startTag(NAMESPACE, TAG_PROPERTY);
        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_NAME, name);
        xmlSerializer.attribute(NAMESPACE, ATTRIBUTE_VALUE, value);
        xmlSerializer.endTag(NAMESPACE, TAG_PROPERTY);
    }

    /**
     * Returns the text in a format that is safe for use in an XML document.
     */
    private String sanitize(String text) {
        return text.replace("\0", "<\\0>");
    }
}