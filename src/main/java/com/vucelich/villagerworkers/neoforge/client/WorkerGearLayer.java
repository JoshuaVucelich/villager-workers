package com.vucelich.villagerworkers.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vucelich.villagerworkers.neoforge.ModItems;
import com.vucelich.villagerworkers.neoforge.WorkerAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.model.VillagerModel;

/**
 * Draws the yellow hard hat on a worker, and the orange hat, clipboard, and rolled blueprint on a foreman.
 * The rank comes from the synced look attachment. The trusted list is not on this attachment.
 */
public class WorkerGearLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    public WorkerGearLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack pose,
            MultiBufferSource buffer,
            int light,
            Villager villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!villager.hasData(WorkerAttachments.LOOK)) {
            return;
        }
        String rank = villager.getData(WorkerAttachments.LOOK);
        boolean foreman = "foreman".equals(rank);
        ItemStack hat = new ItemStack(foreman ? ModItems.FOREMAN_HAT.get() : ModItems.HARD_HAT.get());
        ModelPart root = this.getParentModel().root();
        pose.pushPose();
        part(root, "head").translateAndRotate(pose);
        pose.translate(0.0F, -0.62F, 0.0F);
        pose.scale(0.85F, 0.85F, 0.85F);
        renderItem(hat, pose, buffer, light, villager);
        pose.popPose();
        if (!foreman) {
            return;
        }
        pose.pushPose();
        part(root, "arms").translateAndRotate(pose);
        pose.translate(0.35F, 0.15F, -0.35F);
        pose.scale(0.55F, 0.55F, 0.55F);
        renderItem(new ItemStack(ModItems.CLIPBOARD.get()), pose, buffer, light, villager);
        pose.popPose();
        pose.pushPose();
        part(root, "body").translateAndRotate(pose);
        pose.translate(-0.32F, 0.35F, -0.05F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(80.0F));
        pose.scale(0.45F, 0.45F, 0.45F);
        renderItem(new ItemStack(ModItems.BLUEPRINT_ROLL.get()), pose, buffer, light, villager);
        pose.popPose();
    }

    private static ModelPart part(ModelPart root, String name) {
        return root.hasChild(name) ? root.getChild(name) : root;
    }

    private static void renderItem(ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light, Villager villager) {
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                light,
                OverlayTexture.NO_OVERLAY,
                pose,
                buffer,
                villager.level(),
                0);
    }
}
