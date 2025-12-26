package de.samthedev.jscomputers.block.entity;

import de.samthedev.jscomputers.JSComputers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JSComputers.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("computer_block_entity", () ->
                    BlockEntityType.Builder.of(ComputerBlockEntity::new, JSComputers.COMPUTER_BLOCK.get()).build(null));
}

