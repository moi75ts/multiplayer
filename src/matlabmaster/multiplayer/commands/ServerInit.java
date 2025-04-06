package matlabmaster.multiplayer.commands;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class ServerInit implements BaseCommand
{
    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        Console.showMessage(args);
        return CommandResult.SUCCESS;
    }
}