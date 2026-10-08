package io.github.dubthree.mutantkiller.build;

/**
 * A build tool invocation failed, timed out or could not be started.
 */
public class BuildException extends Exception {

    private final BuildResult result;

    public BuildException(String message) {
        super(message);
        this.result = null;
    }

    public BuildException(String message, Throwable cause) {
        super(message, cause);
        this.result = null;
    }

    public BuildException(String message, BuildResult result) {
        super(message);
        this.result = result;
    }

    /** The build output, if the process ran at all. */
    public BuildResult result() {
        return result;
    }
}
