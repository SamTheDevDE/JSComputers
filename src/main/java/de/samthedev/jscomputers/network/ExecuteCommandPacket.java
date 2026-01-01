package de.samthedev.jscomputers.network;

import de.samthedev.jscomputers.block.entity.ComputerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ExecuteCommandPacket(BlockPos pos, String command, boolean requestOutput) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExecuteCommandPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jscomputers", "execute_command"));

    public static final StreamCodec<ByteBuf, ExecuteCommandPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ExecuteCommandPacket::pos,
            ByteBufCodecs.STRING_UTF8, ExecuteCommandPacket::command,
            ByteBufCodecs.BOOL, ExecuteCommandPacket::requestOutput,
            ExecuteCommandPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExecuteCommandPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            if (serverPlayer.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64) {
                return;
            }

            BlockEntity blockEntity = serverPlayer.level().getBlockEntity(packet.pos());
            if (!(blockEntity instanceof ComputerBlockEntity computerEntity)) {
                return;
            }

            List<String> output = computerEntity.getTerminal().executeCommand(packet.command());
            if (packet.requestOutput()) {
                PacketDistributor.sendToPlayer(serverPlayer, new TerminalOutputPacket(packet.pos(), output));
            }
        });
    }
}
