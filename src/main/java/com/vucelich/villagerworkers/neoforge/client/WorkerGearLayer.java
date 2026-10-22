package com.vucelich.villagerworkers.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vucelich.villagerworkers.common.CosmeticGear;
import com.vucelich.villagerworkers.common.ForemanLabel;
import com.vucelich.villagerworkers.common.HatStyle;
import com.vucelich.villagerworkers.common.WorkerLook;
import com.vucelich.villagerworkers.neoforge.WorkerAttachments;
import com.vucelich.villagerworkers.neoforge.WorkerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Matrix4f;

/**
 * Hard hat, clipboard, and blueprint. None of these are items in the villager inventory.
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
        if (!villager.hasData(WorkerAttachments.LOOK) || !WorkerConfig.renderWorkerGear()) {
            return;
        }
        WorkerLook look = villager.getData(WorkerAttachments.LOOK);
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        int hat = HatStyle.color(look.foreman());
        pose.pushPose();
        getParentModel().root().translateAndRotate(pose);
        getParentModel().getHead().translateAndRotate(pose);
        box(pose, consumer, -6.0F, -11.0F, -6.0F, 6.0F, -9.2F, 6.0F, HatStyle.BRIM);
        box(pose, consumer, -4.2F, -15.0F, -4.2F, 4.2F, -10.2F, 4.2F, hat);
        pose.popPose();
        if (look.foreman()) {
            pose.pushPose();
            getParentModel().root().translateAndRotate(pose);
            getParentModel().root().getChild("arms").translateAndRotate(pose);
            if (CosmeticGear.renderedProps(true).contains(CosmeticGear.CLIPBOARD)) {
                box(pose, consumer, 1.0F, 0.0F, -3.2F, 5.2F, 6.4F, -2.6F, 0x8B5A2B);
                box(pose, consumer, 1.4F, 0.6F, -3.35F, 4.8F, 5.6F, -3.15F, 0xF7F4EA);
                box(pose, consumer, 2.4F, -0.2F, -3.5F, 3.8F, 0.8F, -2.5F, 0xC0C0C0);
            }
            if (CosmeticGear.renderedProps(true).contains(CosmeticGear.BLUEPRINT)) {
                box(pose, consumer, -7.2F, 1.0F, -1.0F, -5.0F, 3.2F, 6.0F, 0x2E5A88);
                box(pose, consumer, -7.0F, 1.4F, 2.2F, -5.2F, 2.8F, 3.4F, 0xD8C7A1);
            }
            pose.popPose();
        }
        if (look.foreman() && WorkerConfig.showForemanLabels()) {
            String text = ForemanLabel.text(look.name(), look.crew(), true);
            if (!text.isEmpty()) {
                drawLabel(pose, buffer, light, villager, text);
            }
        }
    }

    private static void drawLabel(PoseStack pose, MultiBufferSource buffer, int light, Villager villager, String text) {
        pose.pushPose();
        pose.translate(0.0, villager.getBbHeight() + 0.45, 0.0);
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Font font = Minecraft.getInstance().font;
        float width = font.width(text);
        font.drawInBatch(
                text,
                -width / 2.0F,
                0.0F,
                0xFFFFFF,
                false,
                pose.last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0,
                light);
        pose.popPose();
    }

    private static void box(PoseStack pose, VertexConsumer consumer, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        Matrix4f matrix = pose.last().pose();
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        face(consumer, matrix, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, red, green, blue);
        face(consumer, matrix, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, red, green, blue);
        face(consumer, matrix, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, red, green, blue);
        face(consumer, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, red, green, blue);
        face(consumer, matrix, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, red, green, blue);
        face(consumer, matrix, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, red, green, blue);
    }

    private static void face(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            int red, int green, int blue) {
        consumer.addVertex(matrix, x0, y0, z0).setColor(red, green, blue, 255);
        consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, 255);
        consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, 255);
        consumer.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, 255);
    }
}
