package se.deversity.skill3;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import se.deversity.skill3.cli.LearnCommand;
import se.deversity.skill3.cli.SetupCommand;

/** Entry point. Dispatches to the {@code setup} and {@code learn} subcommands. */
@Command(name = "skill3",
        mixinStandardHelpOptions = true,
        version = "skill3 0.1.0",
        description = "A fully local AI Skill Relearner.",
        subcommands = {SetupCommand.class, LearnCommand.class})
public class Skill3App implements Runnable {

    @Override
    public void run() {
        // No subcommand: show usage.
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new Skill3App()).execute(args);
        System.exit(exit);
    }
}
