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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RandomizeBlock extends Block implements EntityBlock {

  public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
  public static final BooleanProperty ANIMATING = BooleanProperty.create("animating");

  private static final List<BlockState> VALID_STATES = new ArrayList<>();
  private static final Random RANDOM = new Random();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public RandomizeBlock(Properties properties) {
    super(properties);
    this.registerDefaultState(this.stateDefinition.any()
        .setValue(FACING, Direction.NORTH)
        .setValue(ANIMATING, false));
  }

  // ###############################################################
  // ----------------------- OVERRIDE METHODS ----------------------
  // ###############################################################

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return this.defaultBlockState()
        .setValue(FACING, context.getHorizontalDirection())
        .setValue(ANIMATING, false);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, ANIMATING);
  }

  @Override
  protected @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
    return Shapes.empty();
  }

  @Override
  protected @NonNull VoxelShape getShape(BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
    if (state.getValue(ANIMATING)) {
      return Shapes.empty();
    }
    return Shapes.block();
  }

  @Override
  protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
    return Shapes.block();
  }

  @Override
  public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
    return new RandomizeBlockEntity(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
    if (level.isClientSide()) {
      return null;
    }
    return type == RandomizeBlockMod.RANDOMIZE_BLOCK_ENTITY
        ? (lvl, p, s, be) -> RandomizeBlockEntity.serverTick(lvl, p, s, (RandomizeBlockEntity) be)
        : null;
  }

  @Override
  protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
    if (!state.getValue(ANIMATING)) {
      triggerAnimation(level, pos, state);
    }

    return InteractionResult.SUCCESS;
  }

  @Override
  protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
    if (!level.isClientSide() && !state.getValue(ANIMATING) && level.hasNeighborSignal(pos)) {
      triggerAnimation(level, pos, state);
    }
  }

  @Override
  protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
    if (!oldState.is(state.getBlock())) {
      if (!level.isClientSide() && !state.getValue(ANIMATING) && level.hasNeighborSignal(pos)) {
        triggerAnimation(level, pos, state);
      }
    }
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public static void triggerAnimation(Level level, BlockPos pos, BlockState state) {
    if (!level.isClientSide()) {
      BlockEntity be = level.getBlockEntity(pos);
      if (be instanceof RandomizeBlockEntity rbe) {
        rbe.startAnimation(state);
      }
    }
  }

  public static void finishRandomization(Level level, BlockPos pos, BlockState state) {
    initValidStates();

    if (!VALID_STATES.isEmpty()) {
      var randomState = VALID_STATES.get(RANDOM.nextInt(VALID_STATES.size()));

      // Orienter le bloc vers le joueur (sens opposé au regard du joueur)
      if (state.hasProperty(FACING)) {
        Direction targetFacing = state.getValue(FACING).getOpposite();
        Rotation rotation = switch (targetFacing) {
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
          25,
          0.6, 0.6, 0.6,
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
