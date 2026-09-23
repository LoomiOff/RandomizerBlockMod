package fr.loomi;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomizeBlockMod implements ModInitializer {

	public static final @NotNull String MOD_ID = "randomizer_block_mod";
	public static final @NotNull Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final @NotNull Block RANDOMIZE_BLOCK = new RandomizeBlock(BlockBehaviour.Properties.of()
		.setId(ResourceKey.create(Registries.BLOCK, id("randomizer_block")))
		.strength(1.5f, 6.0f).requiresCorrectToolForDrops());

	public static final @NotNull Item RANDOMIZE_BLOCK_ITEM = new BlockItem(RANDOMIZE_BLOCK, new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, id("randomizer_block"))));

	// ###############################################################
	// ----------------------- OVERRIDE METHODS ----------------------
	// ###############################################################

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Random Block Mod!");

		Registry.register(BuiltInRegistries.BLOCK, id("randomizer_block"), RANDOMIZE_BLOCK);
		Registry.register(BuiltInRegistries.ITEM, id("randomizer_block"), RANDOMIZE_BLOCK_ITEM);
	}

	// ###############################################################
	// ----------------------- PUBLIC METHODS ------------------------
	// ###############################################################

	public static @NotNull Identifier id(final @NotNull String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
