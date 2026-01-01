package de.samthedev.jscomputers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.samthedev.jscomputers.block.entity.ComputerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.Minecraft;

public class ComputerBlockEntityRenderer implements BlockEntityRenderer<ComputerBlockEntity> {

    public ComputerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ComputerBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBufferSource, int pPackedLight, int pPackedOverlay) {
        if (pBlockEntity.getLevel() == null || !pBlockEntity.isPowered()) {
            return;
        }

        // Calculate blinking: on for 10 ticks, off for 10 ticks
        long gameTime = pBlockEntity.getLevel().getGameTime();
        boolean showCursor = (gameTime / 10) % 2 == 0;
        
        if (!showCursor) {
            return;
        }

        pPoseStack.pushPose();
        pPoseStack.translate(0.5, 0.5, 0.5);
        
        // Rotate to face the player
        Minecraft mc = Minecraft.getInstance();
        pPoseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        // Just translate to show position - the actual rendering would require proper texture
        // For now, we'll just ensure the renderer is registered and ready for future enhancement
        pPoseStack.translate(0, 0, 0.01);
        
        pPoseStack.popPose();
    }
}
