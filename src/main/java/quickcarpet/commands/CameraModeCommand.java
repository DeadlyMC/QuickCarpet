package quickcarpet.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.GameMode;
import quickcarpet.QuickCarpetServer;
import quickcarpet.settings.Settings;
import quickcarpet.utils.CameraData;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CameraModeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> camera = literal("c").
                requires(s -> s.hasPermissionLevel(Settings.commandCameramode)).
                executes((c) -> cameraMode(c.getSource(), c.getSource().getPlayer())).
                then(argument("player", EntityArgumentType.player()).
                        executes( (c) -> cameraMode(c.getSource(), EntityArgumentType.getPlayer(c, "player"))));
        dispatcher.register(camera);

        LiteralArgumentBuilder<ServerCommandSource> survival = literal("s").
                requires(s -> s.hasPermissionLevel(Settings.commandCameramode)).
                executes((c) -> serverMode(
                        c.getSource(),
                        c.getSource().getPlayer())).
                then(argument("player", EntityArgumentType.player()).
                        executes( (c) -> serverMode(c.getSource(), EntityArgumentType.getPlayer(c, "player"))));
        dispatcher.register(survival);

        LiteralArgumentBuilder<ServerCommandSource> toggle = literal("cs").
                requires(s -> s.hasPermissionLevel(Settings.commandCameramode)).
                executes(c -> toggle(c.getSource(), c.getSource().getPlayer()));
        dispatcher.register(toggle);
    }

    private static boolean hasPermission(ServerCommandSource source, PlayerEntity target) {
        try {
            return source.hasPermissionLevel(2) || source.getPlayer() == target;
        } catch (CommandSyntaxException e) {
            return true; // shoudn't happen because server has all permissions anyways
        }
    }

    private static int cameraMode(ServerCommandSource source, PlayerEntity target) {
        if (!(hasPermission(source, target)) || target.isSpectator()) return 0;
        QuickCarpetServer.getInstance().cameraData.put(target.getUuid(), new CameraData(target));
        target.setGameMode(GameMode.SPECTATOR);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 999999, 0, false, false));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.CONDUIT_POWER, 999999, 0, false, false));
        return 1;
    }

    private static int serverMode(ServerCommandSource source, PlayerEntity target) {
        if (!(hasPermission(source, target))) return 0;
        GameMode mode = source.getMinecraftServer().getDefaultGameMode();
        if (mode == GameMode.SPECTATOR) mode = GameMode.SURVIVAL;
        CameraData data = QuickCarpetServer.getInstance().cameraData.remove(target.getUuid());
        if (Settings.cameraModeRestoreLocation && data != null) {
            data.restore(target);
        }
        target.setGameMode(mode);
        target.removeStatusEffect(StatusEffects.NIGHT_VISION);
        target.removeStatusEffect(StatusEffects.CONDUIT_POWER);
        return 1;
    }

    private static int toggle(ServerCommandSource source, PlayerEntity target) {
        if (target.isSpectator()) {
            return serverMode(source, target);
        } else {
            return cameraMode(source, target);
        }
    }
}
