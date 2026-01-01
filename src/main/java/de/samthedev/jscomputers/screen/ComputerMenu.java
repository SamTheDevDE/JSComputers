package de.samthedev.jscomputers.screen;

import de.samthedev.jscomputers.block.entity.ComputerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ComputerMenu extends AbstractContainerMenu {
    public final ComputerBlockEntity blockEntity;
    private final Level level;
    private final ContainerData containerData;

    // Client-side constructor
    public ComputerMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, 
            extraData != null ? inv.player.level().getBlockEntity(extraData.readBlockPos()) : null, 
            new SimpleContainerData(1));
    }

    // Server-side constructor
    public ComputerMenu(int pContainerId, Inventory inv, BlockEntity entity, ContainerData containerData) {
        super(ModMenuTypes.COMPUTER_MENU.get(), pContainerId);
        
        checkContainerDataCount(containerData, 1);
        
        this.blockEntity = (ComputerBlockEntity) entity;
        this.level = inv.player.level();
        this.containerData = containerData;
        
        // Add data slots for syncing
        this.addDataSlots(containerData);
    }

    public boolean isPowered() {
        return this.containerData.get(0) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                pPlayer, de.samthedev.jscomputers.JSComputers.COMPUTER_BLOCK.get());
    }
}

