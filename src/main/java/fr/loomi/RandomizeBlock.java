package fr.loomi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RandomizeBlock extends Block {

  private static final List<BlockState> VALID_STATES = new ArrayList<>();
  private static final Random RANDOM = new Random();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public RandomizeBlock(Properties properties) {
    super(properties);
  }

  // ###############################################################
  // ----------------------- OVERRIDE METHODS ----------------------
  // ###############################################################

  @Override
  protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
    if (!level.isClientSide()) {
      initValidStates();

      if (!VALID_STATES.isEmpty()) {
        final var randomState = VALID_STATES.get(RANDOM.nextInt(VALID_STATES.size()));

        level.setBlockAndUpdate(pos, randomState);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
    }

    return InteractionResult.SUCCESS;
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
