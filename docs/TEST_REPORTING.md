# Test Reporting in Calendar Notifications Plus

Calendar Notifications Plus uses [dorny/test-reporter](https://github.com/dorny/test-reporter) for displaying test results directly in GitHub. This document explains how our test reporting is configured and how to interpret the results.

## Overview

The test reporting system provides:

1. Immediate feedback on test results for each pull request
2. Detailed views of which tests passed/failed
3. Historical test data to track progress
4. Integration with GitHub Checks API
5. Code coverage reporting via JaCoCo

## Workflow Configuration

Our test reporting is integrated into three key parts of our CI workflow:

### 1. In-line Test Reporting

In the main workflow (`.github/workflows/actions.yml`), test-reporter is configured to run directly after tests complete:

- **Unit Tests**: Reports are generated from JUnit XML files in the unit test job
- **Integration Tests**: Reports are generated from instrumentation test XML files 

These reports provide immediate feedback on test results and appear as GitHub Checks for each commit.

### 2. Code Coverage Processing

A dedicated job in the main workflow processes code coverage data:

- Downloads coverage artifacts from unit and integration tests
- Generates detailed coverage reports using JaCoCo
- Creates coverage badges for line and branch coverage
- Posts coverage summary as a PR comment
- Archives HTML reports for detailed inspection

### 3. Consolidated Test Summary

A separate workflow (`.github/workflows/test-summary.yml`) runs when the main workflow completes to provide:

- Unit test summary
- Integration test summary
- Combined test overview
- Coverage report summary with badges
- GitHub Actions Summary with badges

This provides a consolidated view of all test results and coverage across the build.

## Reading Test Reports

Test reports appear in several places:

1. **Pull Request Checks**: Test results appear as check runs in the PR
2. **GitHub Actions Summary**: Look for the summary tab in the Actions run
3. **PR Comments**: Each PR gets a comment with coverage metrics
4. **Artifacts**: Raw test results and coverage reports are available as artifacts

## Code Coverage

Our CI pipeline generates comprehensive code coverage data:

### Coverage Metrics

Two primary metrics are tracked:

1. **Line Coverage**: Percentage of code lines executed during tests
2. **Branch Coverage**: Percentage of decision branches executed during tests

### Coverage Reports

Coverage data is available in multiple formats:

1. **HTML Report**: Interactive HTML report for detailed analysis
2. **XML Report**: Raw JaCoCo XML data
3. **Badges**: SVG badges with current coverage percentages
4. **PR Comment**: Summary of coverage posted to each PR

### Coverage Thresholds

We've established baseline coverage requirements:

- **Overall Project**: Minimum 30% line coverage
- **Changed Files**: Minimum 60% line coverage for newly modified files

These thresholds help maintain and improve code quality over time.

## Test Failure Analysis

When tests fail:

1. Check the test name and failure message in the report
2. Look for the specific test file and method involved
3. Examine the stack trace for the root cause
4. Review the test coverage report for related code areas

## Local Testing

To replicate the CI test environment locally:

```bash
# Run unit tests
cd android && ./gradlew :app:testX8664DebugUnitTest

# Generate XML reports
cd android && ./gradlew :app:testX8664DebugUnitTest --tests="*" -i

# Generate coverage report
cd android && ./gradlew :app:jacocoAndroidTestReport
```

## Test Report Formats

The test-reporter uses two primary formats from our build:

1. **JUnit XML**: Generated by Android's standard test runners
2. **Jacoco XML**: For code coverage reporting

## Troubleshooting

Common issues with test reporting:

- **Missing Reports**: Check the test task output paths
- **Path Patterns**: Verify glob patterns match the actual file locations
- **Report Format**: Ensure the selected reporter matches the XML format
- **Coverage Data**: Verify that `testCoverageEnabled true` is set in the build.gradle file

## Further Reading

- [dorny/test-reporter Documentation](https://github.com/dorny/test-reporter)
- [JaCoCo Code Coverage](https://www.eclemma.org/jacoco/)
- [GitHub Checks API](https://docs.github.com/en/rest/checks)
- [JUnit XML Format](https://github.com/junit-team/junit5/blob/main/platform-tests/src/test/resources/jenkins-junit.xsd) 