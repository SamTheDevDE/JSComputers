package de.samthedev.jscomputers.network;

import de.samthedev.jscomputers.block.entity.ComputerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleComputerPacket(BlockPos pos) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<ToggleComputerPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jscomputers", "toggle_computer"));
    
    public static final StreamCodec<ByteBuf, ToggleComputerPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        ToggleComputerPacket::pos,
        ToggleComputerPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleComputerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // Validate the block is within interaction distance
                if (serverPlayer.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64) {
                    return; // Too far away
                }
                
                BlockEntity blockEntity = serverPlayer.level().getBlockEntity(packet.pos());
                if (blockEntity instanceof ComputerBlockEntity computerEntity) {
                    // Toggle the powered state
                    boolean newState = !computerEntity.isPowered();
                    computerEntity.setPowered(newState);
                }
            }
        });
    }
}
