package quickcarpet.helper;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import quickcarpet.utils.RayTracing;
import quickcarpet.utils.extensions.ActionPackOwner;

import java.util.*;

public class PlayerActionPack {
    private ServerPlayerEntity player;
    private EnumMap<ActionType, Action> actions = new EnumMap<>(ActionType.class);
    public static HashMap<UUID,Float> reach = new HashMap<>();

    private BlockPos currentBlock;
    private int blockHitDelay;
    private boolean isHittingBlock;
    private float curBlockDamageMP;

    private boolean sneaking;
    private boolean sprinting;
    private float forward;
    private float sideways;

    public PlayerActionPack(ServerPlayerEntity player) {
        this.player = player;
        stop();
    }

    public void copyFrom(PlayerActionPack other) {
        actions.putAll(other.actions);
        currentBlock = other.currentBlock;
        blockHitDelay = other.blockHitDelay;
        isHittingBlock = other.isHittingBlock;
        curBlockDamageMP = other.curBlockDamageMP;

        sneaking = other.sneaking;
        sprinting = other.sprinting;
        forward = other.forward;
        sideways = other.sideways;
    }

    public PlayerActionPack toggleSneaking() {
        return setSneaking(!sneaking);
    }

    public PlayerActionPack setSneaking(boolean doSneak) {
        sneaking = doSneak;
        player.setSneaking(doSneak);
        if (sprinting && sneaking)
            setSprinting(false);
        return this;
    }

    public PlayerActionPack toggleSprinting() {
        return setSprinting(!sprinting);
    }

    public PlayerActionPack setSprinting(boolean doSprint) {
        sprinting = doSprint;
        player.setSprinting(doSprint);
        if (sneaking && sprinting)
            setSneaking(false);
        return this;
    }

    public PlayerActionPack setForward(float speed) {
        forward = speed;
        return this;
    }

    public PlayerActionPack setSideways(float speed) {
        sideways = speed;
        return this;
    }

    public PlayerActionPack look(Direction direction) {
        switch (direction) {
            case NORTH: return look(180, 0);
            case SOUTH: return look(0, 0);
            case EAST: return look(-90, 0);
            case WEST: return look(90, 0);
            case UP: return look(player.yaw, -90);
            case DOWN: return look(player.yaw, 90);
        }
        return this;
    }

    public PlayerActionPack look(Vec2f rotation) {
        return look(rotation.x, rotation.y);
    }

    public PlayerActionPack look(float yaw, float pitch) {
        player.yaw = yaw % 360;
        player.pitch = MathHelper.clamp(pitch, -90, 90);
        return this;
    }

    public PlayerActionPack turn(Vec2f rotation) {
        return turn(rotation.x, rotation.y);
    }

    public PlayerActionPack turn(float yaw, float pitch) {
        return look(player.yaw + yaw, player.pitch + pitch);
    }

    public PlayerActionPack mount() {
        if (player.getVehicle() != null) return this;
        List<Entity> entities = player.world.getOtherEntities(player, player.getBoundingBox().expand(3, 1, 3), other -> !(other instanceof PlayerEntity));
        if (entities.isEmpty()) return this;
        Entity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity e : entities) {
            double dist = player.distanceTo(e);
            if (dist < closestDistance) {
                closestDistance = dist;
                closest = e;
            }
        }
        if (closest != null) player.startRiding(closest);
        return this;
    }

    public PlayerActionPack dismount() {
        player.stopRiding();
        return this;
    }

    public PlayerActionPack start(ActionType type, Action action) {
        Action previous = actions.put(type, action);
        if (previous != null) type.stop(player, previous);
        type.start(player, action);
        return this;
    }

    public PlayerActionPack stop() {
        for (ActionType type : ActionType.values()) start(type, null);
        setSneaking(false);
        setSprinting(false);
        forward = 0.0F;
        sideways = 0.0F;
        return this;
    }

    public void onUpdate() {
        for (Map.Entry<ActionType, Action> e : actions.entrySet()) {
            Action action = e.getValue();
            if (action == null) continue;
            if (!action.tick(this, e.getKey())) e.setValue(null);
        }

        float speedMultiplier = sneaking ? 0.3f : 1f;
        if (forward != 0) player.forwardSpeed = forward * speedMultiplier;
        if (sideways != 0) player.sidewaysSpeed = sideways * speedMultiplier;
    }

    static HitResult getTarget(ServerPlayerEntity player) {
        double distance = reach.containsKey(player.getUuid()) ? reach.get(player.getUuid()) : (player.interactionManager.isCreative() ? 5 : 4.5f);
        return RayTracing.rayTrace(player, 1, distance, false);
    }

    public enum ActionType {
        USE(true) {
            @Override
            void execute(ServerPlayerEntity player, Action action) {
                HitResult hit = getTarget(player);
                for (Hand hand : Hand.values()) {
                    switch (hit.getType()) {
                        case BLOCK: {
                            player.updateLastActionTime();
                            ServerWorld world = player.getServerWorld();
                            BlockHitResult blockHit = (BlockHitResult) hit;
                            BlockPos pos = blockHit.getBlockPos();
                            Direction side = blockHit.getSide();
                            if (pos.getY() < player.server.getWorldHeight() - (side == Direction.UP ? 1 : 0) && world.canPlayerModifyAt(player, pos)) {
                                ActionResult result = player.interactionManager.interactBlock(player, world, player.getStackInHand(hand), hand, blockHit);
                                if (result.shouldSwingHand()) player.swingHand(hand);
                                if (result != ActionResult.PASS) return;
                            }
                            break;
                        }
                        case ENTITY: {
                            player.updateLastActionTime();
                            EntityHitResult entityHit = (EntityHitResult) hit;
                            Entity entity = entityHit.getEntity();
                            Vec3d relativeHitPos = entityHit.getPos().subtract(entity.getPos());
                            ActionResult result = entity.interactAt(player, relativeHitPos, hand);
                            if (result.shouldSwingHand()) player.swingHand(hand);
                            if (result != ActionResult.PASS) return;
                            result = player.interact(entity, hand);
                            if (result.shouldSwingHand()) player.swingHand(hand);
                            if (result != ActionResult.PASS) return;
                            break;
                        }
                    }
                    ActionResult result = player.interactionManager.interactItem(player, player.getServerWorld(), player.getStackInHand(hand), hand);
                    if (result.shouldSwingHand()) player.swingHand(hand);
                    if (result != ActionResult.PASS) return;
                }
            }

            @Override
            void stop(ServerPlayerEntity player, Action action) {
                player.stopUsingItem();
            }
        },
        ATTACK(true) {
            @Override
            void execute(ServerPlayerEntity player, Action action) {
                HitResult hit = getTarget(player);
                switch (hit.getType()) {
                    case ENTITY: {
                        EntityHitResult entityHit = (EntityHitResult) hit;
                        player.attack(entityHit.getEntity());
                        player.resetLastAttackedTicks();
                        player.updateLastActionTime();
                        player.swingHand(Hand.MAIN_HAND);
                        break;
                    }
                    case BLOCK: {
                        PlayerActionPack ap = ((ActionPackOwner) player).getActionPack();
                        if (ap.blockHitDelay > 0) {
                            ap.blockHitDelay--;
                            return;
                        }
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        BlockPos pos = blockHit.getBlockPos();
                        Direction side = blockHit.getSide();
                        if (player.isBlockBreakingRestricted(player.world, pos, player.interactionManager.getGameMode()))
                            return;
                        if (ap.currentBlock != null && player.world.isAir(ap.currentBlock)) {
                            ap.currentBlock = null;
                            return;
                        }
                        BlockState state = player.world.getBlockState(pos);
                        if (ap.currentBlock == null || !ap.currentBlock.equals(pos)) {
                            if (ap.currentBlock != null) {
                                player.interactionManager.processBlockBreakingAction(ap.currentBlock, PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, side, player.server.getWorldHeight());
                            }
                            player.interactionManager.processBlockBreakingAction(pos, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, side, player.server.getWorldHeight());
                            boolean notAir = !state.isAir();
                            if (notAir && ap.curBlockDamageMP == 0) {
                                state.onBlockBreakStart(player.world, pos, player);
                            }
                            if (notAir && state.calcBlockBreakingDelta(player, player.world, pos) >= 1) {
                                ap.currentBlock = null;
                            } else {
                                ap.currentBlock = pos;
                                ap.curBlockDamageMP = 0;
                            }
                        } else {
                            ap.curBlockDamageMP += state.calcBlockBreakingDelta(player, player.world, pos);
                            if (ap.curBlockDamageMP >= 1) {
                                player.interactionManager.processBlockBreakingAction(pos, PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, side, player.server.getWorldHeight());
                                ap.curBlockDamageMP = 0;
                            }
                            player.world.setBlockBreakingInfo(-1, pos, (int) (ap.curBlockDamageMP * 10));
                        }
                        player.updateLastActionTime();
                        player.swingHand(Hand.MAIN_HAND);
                        break;
                    }
                }
            }

            @Override
            void stop(ServerPlayerEntity player, Action action) {
                PlayerActionPack ap = ((ActionPackOwner) player).getActionPack();
                if (ap.currentBlock == null) return;
                player.world.setBlockBreakingInfo(-1, ap.currentBlock, -1);
                player.interactionManager.processBlockBreakingAction(ap.currentBlock, PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, Direction.DOWN, player.server.getWorldHeight());
                ap.curBlockDamageMP = 0;
                ap.currentBlock = null;
            }

            @Override
            void inactiveTick(ServerPlayerEntity player, Action action) {
                HitResult hit = getTarget(player);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    PlayerActionPack ap = ((ActionPackOwner) player).getActionPack();
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockPos pos = blockHit.getBlockPos();
                    if (pos.equals(ap.currentBlock)) {
                        stop(player, action);
                    }
                }
            }
        },
        JUMP(true) {
            @Override
            void execute(ServerPlayerEntity player, Action action) {
                if (action.limit == 1) {
                    if (player.isOnGround()) player.jump(); // onGround
                } else {
                    player.setJumping(true);
                }
            }

            @Override
            void inactiveTick(ServerPlayerEntity player, Action action) {
                player.setJumping(false);
            }
        }, DROP_ITEM(true) {
            @Override
            void execute(ServerPlayerEntity player, Action action) {
                player.dropSelectedItem(false);
            }
        }, DROP_STACK(true) {
            @Override
            void execute(ServerPlayerEntity player, Action action) {
                player.dropSelectedItem(true);
            }
        }, SWAP_HANDS(true) {
            @Override
            void execute(ServerPlayerEntity player, Action action) {
                ItemStack itemStack_1 = player.getStackInHand(Hand.OFF_HAND);
                player.setStackInHand(Hand.OFF_HAND, player.getStackInHand(Hand.MAIN_HAND));
                player.setStackInHand(Hand.MAIN_HAND, itemStack_1);
            }
        };

        public final boolean preventSpectator;

        ActionType(boolean preventSpectator) {
            this.preventSpectator = preventSpectator;
        }

        void start(ServerPlayerEntity player, Action action) {}
        abstract void execute(ServerPlayerEntity player, Action action);
        void inactiveTick(ServerPlayerEntity player, Action action) {}
        void stop(ServerPlayerEntity player, Action action) {
            inactiveTick(player, action);
        }
    }

    public static class Action {
        public final int limit;
        public final int interval;
        public final int offset;
        private int perTick;
        private int count;
        private int next;

        private Action(int limit, int interval, int offset, int perTick) {
            this.limit = limit;
            this.interval = interval;
            this.offset = offset;
            this.perTick = perTick;
            next = interval + offset;
        }

        public static Action once() {
            return new Action(1, 1, 0, 1);
        }

        public static Action continuous() {
            return new Action(-1, 1, 0, 1);
        }

        public static Action interval(int interval) {
            return new Action(-1, interval, 0, 1);
        }

        public static Action interval(int interval, int offset) {
            return new Action(-1, interval, offset, 1);
        }

        public static Action perTick(int amount) { return new Action(-1, 1, 0, amount); }

        boolean tick(PlayerActionPack actionPack, ActionType type) {
            next--;
            if (next <= 0) {
                if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                    for (int i = 0; i < perTick; i++) {
                        type.execute(actionPack.player, this);
                    }
                }
                count++;
                if (count == limit) return false;
                next = interval;
            } else {
                if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                    type.inactiveTick(actionPack.player, this);
                }
            }
            return true;
        }
    }
}
