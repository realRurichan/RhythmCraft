package io.github.rurichan.rhythmcraft;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RhythmCraft.MOD_ID)
public class RhythmCraft {
    public static final String MOD_ID = "rhythmcraft";
    public static final Logger LOGGER = LogManager.getLogger();

    // Creative Tab
    public static final ItemGroup CREATIVE_TAB = new ItemGroup("rhythmcraft") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(BEATMANIA_ARCADE_ITEM.get());
        }
    };

    // Registers
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    // Register Block & Item
    public static final RegistryObject<Block> BEATMANIA_ARCADE_BLOCK = BLOCKS.register("beatmania_arcade",
            () -> new BeatmaniaArcadeBlock(AbstractBlock.Properties.of(Material.METAL).strength(2.0f).noOcclusion().lightLevel((state) -> 15)));

    public static final RegistryObject<Item> BEATMANIA_ARCADE_ITEM = ITEMS.register("beatmania_arcade",
            () -> new BlockItem(BEATMANIA_ARCADE_BLOCK.get(), new Item.Properties().tab(CREATIVE_TAB)));

    public static final RegistryObject<Block> TAIKO_ARCADE_BLOCK = BLOCKS.register("taiko_arcade",
            () -> new TaikoArcadeBlock(AbstractBlock.Properties.of(Material.METAL).strength(2.0f).noOcclusion().lightLevel((state) -> 15)));

    public static final RegistryObject<Item> TAIKO_ARCADE_ITEM = ITEMS.register("taiko_arcade",
            () -> new BlockItem(TAIKO_ARCADE_BLOCK.get(), new Item.Properties().tab(CREATIVE_TAB)));

    public RhythmCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::doClientStuff);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("RhythmCraft common setup starting...");
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        LOGGER.info("RhythmCraft client setup starting...");
        RenderTypeLookup.setRenderLayer(BEATMANIA_ARCADE_BLOCK.get(), RenderType.cutout());
        RenderTypeLookup.setRenderLayer(TAIKO_ARCADE_BLOCK.get(), RenderType.cutout());
    }
}
