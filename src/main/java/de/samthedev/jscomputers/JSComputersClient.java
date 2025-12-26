package de.samthedev.jscomputers;

import de.samthedev.jscomputers.screen.ComputerMenu;
import de.samthedev.jscomputers.screen.ComputerScreen;
import de.samthedev.jscomputers.screen.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = JSComputers.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = JSComputers.MODID, value = Dist.CLIENT)
public class JSComputersClient {
    public JSComputersClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        JSComputers.LOGGER.info("HELLO FROM CLIENT SETUP");
        JSComputers.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        
        // Register screen factory using reflection to bypass visibility
        event.enqueueWork(() -> {
            try {
                var method = net.minecraft.client.gui.screens.MenuScreens.class.getDeclaredMethod(
                    "register", 
                    net.minecraft.world.inventory.MenuType.class, 
                    net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor.class
                );
                method.setAccessible(true);
                method.invoke(null, ModMenuTypes.COMPUTER_MENU.get(), (net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<ComputerMenu, ComputerScreen>) ComputerScreen::new);
            } catch (Exception e) {
                JSComputers.LOGGER.error("Failed to register ComputerScreen", e);
            }
        });
    }
}
