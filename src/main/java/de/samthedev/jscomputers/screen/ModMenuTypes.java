package de.samthedev.jscomputers.screen;

import de.samthedev.jscomputers.JSComputers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, JSComputers.MODID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<MenuType<?>, MenuType<ComputerMenu>> COMPUTER_MENU =
            MENUS.register("computer_menu", () -> IMenuTypeExtension.create(ComputerMenu::new));
}

