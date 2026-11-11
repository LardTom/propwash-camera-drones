package de.lardtom.camdrones.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.AutonomyPlan;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.DroneSelector;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerTablets;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class CamCommands {
    public static final String ROOT = CamDrones.MOD_ID;
    public static final String KEY = CamDrones.MOD_ID + ".command.";
    public static final List<String> SUBCOMMANDS = List.of("mission list", "mission start", "mission pause", "mission resume", "mission stop",
            "hold", "follow me", "follow <target>", "follow stop", "follow accept", "follow deny");
    public static final List<String> STYLES = Arrays.stream(FollowController.Style.values()).map(s -> s.name().toLowerCase(Locale.ROOT)).toList();

    private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> EXTENSIONS = new CopyOnWriteArrayList<>();
    private static final SuggestionProvider<CommandSourceStack> OWN_DRONES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(DroneSelector.suggestions(candidates(ctx.getSource(), false)), builder);
    private static final SuggestionProvider<CommandSourceStack> ANY_DRONES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(DroneSelector.suggestions(candidates(ctx.getSource(), true)), builder);
    private static final SuggestionProvider<CommandSourceStack> STYLE_NAMES = (ctx, builder) -> SharedSuggestionProvider.suggest(STYLES, builder);

    private CamCommands() {
    }

    public static void extend(Consumer<LiteralArgumentBuilder<CommandSourceStack>> extension) {
        EXTENSIONS.add(Objects.requireNonNull(extension, "extension"));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT)
                .then(Commands.literal("mission")
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("start")
                                .executes(ctx -> mission(ctx.getSource(), MissionCommand.Command.START, Optional.empty(), MissionCommand.AUTO_INDEX))
                                .then(Commands.argument("drone", StringArgumentType.word()).suggests(OWN_DRONES)
                                        .executes(ctx -> mission(ctx.getSource(), MissionCommand.Command.START, drone(ctx), MissionCommand.AUTO_INDEX))
                                        .then(Commands.argument("waypoint", IntegerArgumentType.integer(1, MissionLimits.MAX_WAYPOINTS))
                                                .executes(ctx -> mission(ctx.getSource(), MissionCommand.Command.START, drone(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "waypoint") - 1)))))
                        .then(simple("pause", MissionCommand.Command.PAUSE, ANY_DRONES))
                        .then(simple("resume", MissionCommand.Command.RESUME, OWN_DRONES))
                        .then(simple("stop", MissionCommand.Command.STOP, ANY_DRONES)))
                .then(simple("hold", MissionCommand.Command.HOLD, OWN_DRONES))
                .then(Commands.literal("follow")
                        .then(Commands.literal("stop").executes(ctx -> followStop(ctx.getSource())))
                        .then(Commands.literal("accept").executes(ctx -> answer(ctx.getSource(), true)))
                        .then(Commands.literal("deny").executes(ctx -> answer(ctx.getSource(), false)))
                        .then(Commands.literal("me")
                                .executes(ctx -> followMe(ctx.getSource(), Optional.empty(), Optional.empty()))
                                .then(Commands.argument("drone", StringArgumentType.word()).suggests(OWN_DRONES)
                                        .executes(ctx -> followMe(ctx.getSource(), drone(ctx), Optional.empty()))
                                        .then(Commands.argument("style", StringArgumentType.word()).suggests(STYLE_NAMES)
                                                .executes(ctx -> followMe(ctx.getSource(), drone(ctx),
                                                        Optional.of(StringArgumentType.getString(ctx, "style")))))))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> followTarget(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), Optional.empty()))
                                .then(Commands.argument("drone", StringArgumentType.word()).suggests(OWN_DRONES)
                                        .executes(ctx -> followTarget(ctx.getSource(), EntityArgument.getEntity(ctx, "target"), drone(ctx))))));
        for (Consumer<LiteralArgumentBuilder<CommandSourceStack>> extension : EXTENSIONS) {
            extension.accept(root);
        }
        dispatcher.register(root);
        CamDrones.LOG.info("{}: Befehl /{} angemeldet ({} Unterbefehle, {} Erweiterungen)", CamDrones.MOD_NAME, ROOT, SUBCOMMANDS.size(),
                EXTENSIONS.size());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> simple(String name, MissionCommand.Command command,
                                                                     SuggestionProvider<CommandSourceStack> suggestions) {
        return Commands.literal(name)
                .executes(ctx -> mission(ctx.getSource(), command, Optional.empty(), MissionCommand.AUTO_INDEX))
                .then(Commands.argument("drone", StringArgumentType.word()).suggests(suggestions)
                        .executes(ctx -> mission(ctx.getSource(), command, drone(ctx), MissionCommand.AUTO_INDEX)));
    }

    private static Optional<String> drone(CommandContext<CommandSourceStack> ctx) {
        return Optional.of(StringArgumentType.getString(ctx, "drone"));
    }

    static boolean operator(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static List<DroneSelector.Candidate<Entity>> candidates(CommandSourceStack source, boolean any) {
        ServerPlayer player = source.getPlayer();
        boolean all = player == null || any && operator(source);
        return AutonomyServer.drones(source.getServer(), all ? Optional.empty() : Optional.of(player.getUUID()));
    }

    static Optional<UUID> held(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        for (InteractionHand hand : InteractionHand.values()) {
            Optional<PlannerLink> link = PlannerTablets.link(player.getItemInHand(hand));
            if (link.isPresent()) {
                return Optional.of(link.get().serial());
            }
        }
        return Optional.empty();
    }

    static Optional<Entity> resolve(CommandSourceStack source, Optional<String> query, boolean any) {
        ServerPlayer player = source.getPlayer();
        DroneSelector.Result<Entity> result = DroneSelector.select(candidates(source, false), query, held(player));
        if (result.outcome() == DroneSelector.Outcome.NONE && player != null && any && operator(source)) {
            result = DroneSelector.select(candidates(source, true), query, Optional.empty());
        }
        switch (result.outcome()) {
            case FOUND -> {
                return result.chosen().map(DroneSelector.Candidate::handle);
            }
            case NONE -> source.sendFailure((query.isPresent() ? Component.translatable(KEY + "drone.no_match", query.get())
                    : Component.translatable(KEY + "drone.none")).withStyle(ChatFormatting.RED));
            case AMBIGUOUS -> {
                List<String> labels = new ArrayList<>();
                for (DroneSelector.Candidate<Entity> candidate : result.matches()) {
                    labels.add(candidate.label());
                }
                source.sendFailure(Component.translatable(KEY + "drone.ambiguous", String.join(", ", labels)).withStyle(ChatFormatting.RED));
            }
        }
        return Optional.empty();
    }

    private static int feedback(CommandSourceStack source, AutonomyServer.Reply reply) {
        Component text = AutonomyServer.text(reply.status(), reply.drone(), reply.detail());
        if (reply.ok()) {
            source.sendSuccess(() -> text, false);
            return 1;
        }
        source.sendFailure(text);
        return 0;
    }

    static int mission(CommandSourceStack source, MissionCommand.Command command, Optional<String> query, int index) {
        boolean any = command == MissionCommand.Command.STOP || command == MissionCommand.Command.PAUSE;
        Optional<Entity> drone = resolve(source, query, any);
        if (drone.isEmpty()) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        return feedback(source, AutonomyServer.execute(Optional.ofNullable(player), operator(source), drone.get(), command, index));
    }

    static int list(CommandSourceStack source) {
        List<DroneSelector.Candidate<Entity>> drones = candidates(source, true);
        if (drones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "list.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "list.header", drones.size()).withStyle(ChatFormatting.GOLD), false);
        for (DroneSelector.Candidate<Entity> candidate : drones) {
            Entity drone = candidate.handle();
            MissionStatus status = AutonomyServer.missionStatus(drone);
            Optional<Mission> mission = AutonomyServer.mission(drone);
            Component state = Component.translatable(KEY + "state." + status.state().name().toLowerCase(Locale.ROOT));
            String progress = mission.map(m -> (Math.min(status.index(), m.size() - 1) + 1) + "/" + m.size()).orElse("-");
            Component flight = AutonomyServer.activePlan(drone).map(AutonomyPlan::label).<Component>map(Component::literal)
                    .orElseGet(() -> Component.translatable(KEY + "list.no_flight"));
            String position = Math.round(drone.getX()) + " " + Math.round(drone.getY()) + " " + Math.round(drone.getZ());
            source.sendSuccess(() -> Component.translatable(KEY + "list.entry", candidate.label(), state, progress, flight, position), false);
        }
        return drones.size();
    }

    static int followStop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int stopped = AutonomyServer.stopFollowing(player);
        source.sendSuccess(() -> Component.translatable(KEY + "follow.stopped", stopped), false);
        return stopped;
    }

    static int answer(CommandSourceStack source, boolean accept) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return feedback(source, AutonomyServer.answer(player, OptionalInt.empty(), accept));
    }

    static Optional<FollowController.Style> style(CommandSourceStack source, Optional<String> name) {
        if (name.isEmpty()) {
            return Optional.of(FollowController.Style.BEHIND);
        }
        for (FollowController.Style style : FollowController.Style.values()) {
            if (style.name().equalsIgnoreCase(name.get())) {
                return Optional.of(style);
            }
        }
        source.sendFailure(Component.translatable(KEY + "follow.style", name.get(), String.join(", ", STYLES)).withStyle(ChatFormatting.RED));
        return Optional.empty();
    }

    static int followMe(CommandSourceStack source, Optional<String> query, Optional<String> styleName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<FollowController.Style> style = style(source, styleName);
        if (style.isEmpty()) {
            return 0;
        }
        Optional<Entity> drone = resolve(source, query, false);
        return drone.isEmpty() ? 0 : feedback(source, AutonomyServer.follow(player, drone.get(), (Entity) player, style.get()));
    }

    static int followTarget(CommandSourceStack source, Entity target, Optional<String> query) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Entity> drone = resolve(source, query, false);
        return drone.isEmpty() ? 0 : feedback(source, AutonomyServer.follow(player, drone.get(), target, FollowController.Style.BEHIND));
    }
}
