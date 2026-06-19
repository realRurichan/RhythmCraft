package io.github.rurichan.rhythmcraft;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RhythmCraft.MOD_ID)
public class RhythmCraft {
    public static final String MOD_ID = "rhythmcraft";
    public static final Logger LOGGER = LogManager.getLogger();

    public RhythmCraft() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("RhythmCraft common setup starting...");
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        LOGGER.info("RhythmCraft client setup starting...");
    }
}
