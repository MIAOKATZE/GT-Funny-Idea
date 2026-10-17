package com.miaokatze.gtfi.testlab;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

/** OP4/console entry. Every world mutation is delegated to the bounded fixture runtime. */
final class TestCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtfi";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtfi test build|reset|run all|status|report|actors|enter|preview|visual|camera node|effects|leave|stop";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            if (args.length < 2 || !"test".equals(args[0])) throw new IllegalArgumentException(getCommandUsage(sender));
            TestRuntime runtime = GtfiTestLab.runtime();
            String action = args[1];
            String result;
            if ("status".equals(action)) result = runtime.status();
            else if ("report".equals(action)) result = runtime.report();
            else if ("actors".equals(action)) result = runtime.actors();
            else if ("build".equals(action) || "reset".equals(action)) result = runtime.build();
            else if ("run".equals(action) && args.length == 3) result = runtime.run(args[2]);
            else if ("camera".equals(action) && args.length == 3)
                result = runtime.camera(getCommandSenderAsPlayer(sender), args[2]);
            else if ("enter".equals(action)) result = runtime.enter(getCommandSenderAsPlayer(sender));
            else if ("visual".equals(action)) result = runtime.visual(getCommandSenderAsPlayer(sender));
            else if ("preview".equals(action)) result = runtime.preview(getCommandSenderAsPlayer(sender));
            else if ("leave".equals(action)) result = runtime.leave(getCommandSenderAsPlayer(sender));
            else if ("stop".equals(action)) result = runtime.stop();
            else throw new IllegalArgumentException(getCommandUsage(sender));
            sender.addChatMessage(new ChatComponentText("[GTFI-TEST] " + result));
        } catch (Exception failure) {
            sender.addChatMessage(new ChatComponentText("[GTFI-TEST] " + failure.getMessage()));
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "test");
        if (args.length == 2) return getListOfStringsMatchingLastWord(
            args,
            "build",
            "reset",
            "run",
            "status",
            "report",
            "actors",
            "enter",
            "preview",
            "visual",
            "camera",
            "leave",
            "stop");
        if (args.length == 3 && "camera".equals(args[1]))
            return getListOfStringsMatchingLastWord(args, "node", "effects");
        if (args.length == 3 && "run".equals(args[1])) return getListOfStringsMatchingLastWord(args, "all");
        return null;
    }
}
