package com.salesforce.bazel.eclipse.command.internal;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.command.Command;
import com.salesforce.bazel.eclipse.command.CommandBuilder;

/**
 * Utility class that checks the version of the Bazel executable to make sure it is in supported range.
 */
public class BazelVersionChecker {

    /**
     * Builder for Bazel commands, which may be a ShellCommandBuilder (for real Eclipse use) or a MockCommandBuilder 
     * (for simulations during functional tests).
     */
    private CommandBuilder commandBuilder;

    /**
     * Minimum bazel version needed to work with this plugin (currently 1.0.0)
     */
    public static final int[] MINIMUM_BAZEL_VERSION = { 1, 0, 0 };

    private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)([^0-9].*)?$");

    public BazelVersionChecker(CommandBuilder commandBuilder) {
        this.commandBuilder = commandBuilder;
    }
    
    /**
     * Checks the version of the bazel binary installed at the path specified in the Preferences.
     *
     * @param bazelExecutablePath
     *            the proposed file system path to the Bazel executable
     * @param bazelWorkspaceRootDirectory
     *            optional directory in which to run the bazel command
     * @throws BazelCommandLineToolConfigurationException
     */
    public void runBazelVersionCheck(File bazelExecutable, File bazelWorkspaceRootDirectory) throws BazelCommandLineToolConfigurationException {
        if (!bazelExecutable.exists()) {
            throw new BazelCommandLineToolConfigurationException.BazelNotFoundException(bazelExecutable.getAbsolutePath());
        }
        if (!bazelExecutable.canExecute()) {
            throw new BazelCommandLineToolConfigurationException.BazelNotExecutableException(bazelExecutable.getAbsolutePath());
        }
        File execDir = bazelWorkspaceRootDirectory;
        if (execDir == null || !execDir.exists()) {
            // for getting version, we don't need an actual bazel workspace
            String tmpdir = System.getProperty("java.io.tmpdir");
            execDir = new File(tmpdir);
        }
        try {
            Command command = commandBuilder.setConsoleName(null).setDirectory(bazelWorkspaceRootDirectory)
                    .addArguments(bazelExecutable.getAbsolutePath(), "version")
                    .setStdoutLineSelector((s) -> s.startsWith("Build label:") ? s.substring(13) : null).build();
            if (command.run() != 0) {
                throw new BazelCommandLineToolConfigurationException.BazelNotExecutableException(
                    bazelExecutable.getAbsolutePath());
            }
            List<String> result = command.getSelectedOutputLines();
            if (result.size() != 1) {
                throw new BazelCommandLineToolConfigurationException.BazelTooOldException("unknown",
                    bazelExecutable.getAbsolutePath());
            }
            String version = result.get(0);
            Matcher versionMatcher = VERSION_PATTERN.matcher(version);
            if (versionMatcher == null || !versionMatcher.matches()) {
                throw new BazelCommandLineToolConfigurationException.BazelTooOldException(version,
                    bazelExecutable.getAbsolutePath());
            }
            int[] versionNumbers = { Integer.parseInt(versionMatcher.group(1)),
                    Integer.parseInt(versionMatcher.group(2)), Integer.parseInt(versionMatcher.group(3)) };
            if (compareVersion(versionNumbers, MINIMUM_BAZEL_VERSION) < 0) {
                throw new BazelCommandLineToolConfigurationException.BazelTooOldException(version,
                    bazelExecutable.getAbsolutePath());
            }
        } catch (IOException | InterruptedException e) {
            throw new BazelCommandLineToolConfigurationException.BazelNotFoundException(bazelExecutable.getAbsolutePath());
        }
    }

    private static int compareVersion(int[] version1, int[] version2) {
        for (int i = 0; i < Math.min(version1.length, version2.length); i++) {
            if (version1[i] < version2[i]) {
                return -1;
            } else if (version1[i] > version2[i]) {
                return 1;
            }
        }
        return Integer.compare(version1.length, version2.length);
    }

}