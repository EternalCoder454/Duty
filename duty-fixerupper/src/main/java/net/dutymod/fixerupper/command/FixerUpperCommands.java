package net.dutymod.fixerupper.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.dutymod.framework.DutyConfig;
import net.dutymod.framework.DutyLog;
import net.dutymod.framework.DutyReport;
import net.dutymod.framework.DutyMetrics;
import net.dutymod.fixerupper.duck.IProfilingServerFunctionManager;

import static net.minecraft.commands.Commands.literal;

public class FixerUpperCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("duty")
                .then(literal("mcfunctions").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            if(level == null) {
                                context.getSource().sendFailure(Component.literal("Couldn't find server level"));
                                return 0;
                            }
                            if (level.getServer().getFunctions() instanceof IProfilingServerFunctionManager profiler) {
                                context.getSource().sendSuccess(() -> Component.literal("mcfunction runtime breakdown:"), false);
                                for(String line : profiler.duty$getProfilingResults().split("\n")) {
                                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                                }

                                return 1;
                            } else {
                                context.getSource().sendFailure(Component.literal("Duty mcfunction profiling is not enabled on this server."));
                                return 0;
                            }
                        }))
                .then(literal("reload").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> {
                            int changed = DutyConfig.reload();
                            context.getSource().sendSuccess(() -> Component.literal(changed == 0
                                    ? "Duty config re-read; nothing changed."
                                    : "Duty config re-read; " + changed
                                            + (changed == 1 ? " setting changed." : " settings changed.")), false);
                            return 1;
                        }))
                .then(literal("report").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> {
                            // To the log and a file rather than chat: the report is long and
                            // fixed-width, and the point of it is to be handed to somebody else.
                            for (String line : DutyReport.generate().split("\n")) {
                                DutyLog.info(line);
                            }
                            java.nio.file.Path path = DutyReport.writeToFile();
                            context.getSource().sendSuccess(() -> Component.literal(path == null
                                    ? "Duty report written to the log."
                                    : "Duty report written to the log and to " + path), false);
                            return 1;
                        })
                        .then(literal("findings").executes(context -> {
                            // Just the conclusions, short enough to read in chat.
                            for (DutyReport.Finding finding : DutyReport.findings()) {
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "[" + finding.severity() + "] " + finding.title()), false);
                            }
                            return 1;
                        })))
                .then(literal("metrics").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> sendReport(context.getSource()))
                        .then(literal("on").executes(context -> {
                            DutyMetrics.setEnabled(true);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Duty measurement on. Only what happens from now is counted."), false);
                            return 1;
                        }))
                        .then(literal("off").executes(context -> {
                            DutyMetrics.setEnabled(false);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Duty measurement off. What was collected is kept."), false);
                            return 1;
                        }))
                        .then(literal("reset").executes(context -> {
                            DutyMetrics.reset();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Duty measurements cleared."), false);
                            return 1;
                        }))
                        .then(literal("log").executes(context -> {
                            DutyMetrics.reportToLog();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Duty performance report written to the log."), false);
                            return 1;
                        })))
        );
    }

    /**
     * Prints the report into chat, a line at a time.
     *
     * <p>Split rather than sent as one component because the report is a fixed-width table and chat
     * wraps: one line per message keeps the columns lined up.
     */
    private static int sendReport(CommandSourceStack source) {
        for (String line : DutyMetrics.report().split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
