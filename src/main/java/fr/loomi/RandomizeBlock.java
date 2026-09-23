package fr.loomi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RandomizeBlock extends Block {

  public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

  private static final List<BlockState> VALID_STATES = new ArrayList<>();
  private static final Random RANDOM = new Random();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public RandomizeBlock(Properties properties) {
    super(properties);
    this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
  }

  // ###############################################################
  // ----------------------- OVERRIDE METHODS ----------------------
  // ###############################################################

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
    if (!level.isClientSide()) {
      randomize(level, pos, state);
    }

    return InteractionResult.SUCCESS;
  }

  @Override
  protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
    if (!level.isClientSide() && level.hasNeighborSignal(pos)) {
      randomize(level, pos, state);
    }
  }

  @Override
  protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
    if (!oldState.is(state.getBlock())) {
      if (!level.isClientSide() && level.hasNeighborSignal(pos)) {
        randomize(level, pos, state);
      }
    }
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public static void randomize(Level level, BlockPos pos, BlockState state) {
    initValidStates();

    if (!VALID_STATES.isEmpty()) {
      var randomState = VALID_STATES.get(RANDOM.nextInt(VALID_STATES.size()));

      // Conserver l'orientation du bloc pour le nouveau bloc
      if (state.hasProperty(FACING)) {
        final var facing = state.getValue(FACING);
        final var rotation = switch (facing) {
          case EAST -> Rotation.CLOCKWISE_90;
          case SOUTH -> Rotation.CLOCKWISE_180;
          case WEST -> Rotation.COUNTERCLOCKWISE_90;
          default -> Rotation.NONE;
        };
        randomState = randomState.rotate(rotation);
      }

      level.setBlockAndUpdate(pos, randomState);
      level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);

      if (level instanceof ServerLevel serverLevel) {
        serverLevel.sendParticles(
          ParticleTypes.HAPPY_VILLAGER,
          pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
          15,
          0.5, 0.5, 0.5,
          0.1
        );
      }
    }
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private static void initValidStates() {
    if (!VALID_STATES.isEmpty()) return;

    for (var block : BuiltInRegistries.BLOCK) {
      final var state = block.defaultBlockState();

      if (state.isAir() || !state.getFluidState().isEmpty())
        continue;

      final var isIncomplete = state.getProperties().stream().anyMatch(p -> {
        String name = p.getName();
        return name.equals("half") || name.equals("part") || name.equals("hinge") || name.equals("layers");
      });

      if (isIncomplete) continue;

      VALID_STATES.add(state);
    }
  }
}
