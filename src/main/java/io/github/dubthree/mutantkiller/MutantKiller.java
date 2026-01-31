package io.github.dubthree.mutantkiller;

import io.github.dubthree.mutantkiller.cli.AnalyzeCommand;
import io.github.dubthree.mutantkiller.cli.KillCommand;
import io.github.dubthree.mutantkiller.cli.RunCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Mutant Killer - An autonomous agent that analyzes PIT mutation test results
 * and improves tests to kill surviving mutants.
 */
@Command(
    name = "mutant-killer",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "Autonomous agent that kills surviving mutants by improving your tests.",
    subcommands = {
        RunCommand.class,
        AnalyzeCommand.class,
        KillCommand.class
    }
)
public class MutantKiller implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MutantKiller()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
