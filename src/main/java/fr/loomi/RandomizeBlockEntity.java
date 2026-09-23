package fr.loomi;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.UUID;

public class RandomizeBlockEntity extends BlockEntity {

  private static final int MAX_TICKS = 45; // ~2.25 secondes

  private boolean isAnimating = false;
  private int animationTick = 0;
  private UUID displayEntityUuid = null;
  private float currentYaw = 0.0f;
  private BlockState originalState = null;

  public RandomizeBlockEntity(BlockPos pos, BlockState state) {
    super(RandomizeBlockMod.RANDOMIZE_BLOCK_ENTITY, pos, state);
  }

  public boolean isAnimating() {
    return isAnimating;
  }

  public void startAnimation(BlockState state) {
    if (isAnimating || level == null || level.isClientSide()) {
      return;
    }

    this.isAnimating = true;
    this.animationTick = 0;
    this.originalState = state;
    this.currentYaw = state.hasProperty(RandomizeBlock.FACING)
        ? state.getValue(RandomizeBlock.FACING).toYRot()
        : 0.0f;

    // Faire apparaître l'entité d'affichage temporaire
    Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
    display.setPos(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
    display.setBlockState(state.setValue(RandomizeBlock.ANIMATING, false));
    display.setBrightnessOverride(Brightness.FULL_BRIGHT);

    // Centrer le bloc sur l'entité
    display.setTransformation(new Transformation(
        new Vector3f(-0.5f, 0.0f, -0.5f),
        null,
        new Vector3f(1.0f, 1.0f, 1.0f),
        null
    ));
    display.setYRot(currentYaw);

    level.addFreshEntity(display);
    this.displayEntityUuid = display.getUUID();

    // Rendre le bloc physique invisible pendant l'animation
    level.setBlock(worldPosition, state.setValue(RandomizeBlock.ANIMATING, true), 3);

    setChanged();
  }

  public static void serverTick(Level level, BlockPos pos, BlockState state, RandomizeBlockEntity be) {
    if (!be.isAnimating) {
      return;
    }

    be.animationTick++;

    Display.BlockDisplay display = null;
    if (be.displayEntityUuid != null && level instanceof ServerLevel serverLevel) {
      Entity entity = serverLevel.getEntity(be.displayEntityUuid);
      if (entity instanceof Display.BlockDisplay bd) {
        display = bd;
      }
    }

    float progress = (float) be.animationTick / (float) MAX_TICKS;

    // Rotation accélérée : vitesse angulaire qui augmente quadratiquement
    float speed = 8.0f + 65.0f * (progress * progress);
    be.currentYaw = (be.currentYaw + speed) % 360.0f;

    // Réduction progressive de taille vers la fin pour "disparaître"
    float scale = 1.0f;
    if (progress > 0.45f) {
      float shrinkProgress = (progress - 0.45f) / 0.55f;
      scale = Math.max(0.01f, 1.0f - shrinkProgress);
    }
    float yOffset = (1.0f - scale) * 0.5f;

    if (display != null) {
      display.setYRot(be.currentYaw);
      display.setPosRotInterpolationDuration(1);
      display.setTransformation(new Transformation(
          new Vector3f(-0.5f * scale, yOffset, -0.5f * scale),
          null,
          new Vector3f(scale, scale, scale),
          null
      ));
      display.setTransformationInterpolationDuration(1);
    }

    // Compte à rebours sonore avec des sons de note block (pling) à tempo et pitch ascendants
    playCountdownSound(be.animationTick, level, pos);

    // Particules magiques d'ambiance pendant que le bloc tourne
    if (level instanceof ServerLevel serverLevel) {
      serverLevel.sendParticles(
          ParticleTypes.PORTAL,
          pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
          2,
          0.2, 0.2, 0.2,
          0.5
      );
    }

    // Fin de l'animation : disparition totale et métamorphose
    if (be.animationTick >= MAX_TICKS) {
      if (display != null) {
        display.discard();
      }
      be.isAnimating = false;

      BlockState stateToTransform = be.originalState != null ? be.originalState : state;
      RandomizeBlock.finishRandomization(level, pos, stateToTransform);
    }
  }

  private static void playCountdownSound(int tick, Level level, BlockPos pos) {
    float pitch = switch (tick) {
      case 1 -> 0.6f;
      case 12 -> 0.8f;
      case 22 -> 1.0f;
      case 30 -> 1.2f;
      case 36 -> 1.4f;
      case 40 -> 1.6f;
      case 42 -> 1.8f;
      case 44 -> 2.0f;
      default -> -1.0f;
    };

    if (pitch > 0.0f) {
      level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.RECORDS, 1.0f, pitch);
    }
  }

  @Override
  public void setRemoved() {
    if (isAnimating && displayEntityUuid != null && level instanceof ServerLevel serverLevel) {
      Entity entity = serverLevel.getEntity(displayEntityUuid);
      if (entity != null) {
        entity.discard();
      }
    }
    super.setRemoved();
  }
}
