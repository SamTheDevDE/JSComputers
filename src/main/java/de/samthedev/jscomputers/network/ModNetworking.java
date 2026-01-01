package de.samthedev.jscomputers.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {
    
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::onRegisterPayloadHandlers);
    }
    
    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        
        // Register client-to-server packet
        registrar.playToServer(
            ToggleComputerPacket.TYPE,
            ToggleComputerPacket.STREAM_CODEC,
            ToggleComputerPacket::handle
        );

        registrar.playToServer(
            ExecuteCommandPacket.TYPE,
            ExecuteCommandPacket.STREAM_CODEC,
            ExecuteCommandPacket::handle
        );

        // Register server-to-client responses
        registrar.playToClient(
            TerminalOutputPacket.TYPE,
            TerminalOutputPacket.STREAM_CODEC,
            TerminalOutputPacket::handle
        );
    }
}
