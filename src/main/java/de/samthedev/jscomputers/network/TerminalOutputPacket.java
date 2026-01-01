package de.samthedev.jscomputers.network;

import de.samthedev.jscomputers.screen.ComputerScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record TerminalOutputPacket(BlockPos pos, List<String> output) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TerminalOutputPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jscomputers", "terminal_output"));

    public static final StreamCodec<ByteBuf, TerminalOutputPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TerminalOutputPacket::pos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), TerminalOutputPacket::output,
            TerminalOutputPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalOutputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof ComputerScreen screen)) {
                return;
            }
            if (screen.getMenu().blockEntity == null || !screen.getMenu().blockEntity.getBlockPos().equals(packet.pos())) {
                return;
            }
            screen.applyRemoteOutput(packet.output());
        });
    }
}
