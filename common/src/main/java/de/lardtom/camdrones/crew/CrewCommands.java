package de.lardtom.camdrones.crew;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.lardtom.camdrones.command.CamCommands;
import de.lardtom.camdrones.crew.model.CrewBook;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

public final class CrewCommands {
    public static final String ROOT = CamCommands.ROOT;
    public static final String CREW = "crew";

    private CrewCommands() {
    }

    public static void extend() {
        CamCommands.extend(root -> root.then(crew()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> crew() {
        return Commands.literal(CREW)
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> result(CrewService.inviteByCommand(ctx.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("accept").executes(ctx -> result(CrewService.accept(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("decline").executes(ctx -> result(CrewService.decline(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("leave").executes(ctx -> result(CrewService.leave(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("release").executes(ctx -> result(CrewService.releaseByCommand(ctx.getSource().getPlayerOrException()))));
    }

    private static int result(CrewBook.Verdict verdict) {
        return verdict.ok() ? 1 : 0;
    }
}
