package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.ModPayloads;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class HireScreen extends Screen {
    private final UUID villagerId;
    private final String villagerName;

    public HireScreen(UUID villagerId, String villagerName) {
        super(Component.literal("Name tag"));
        this.villagerId = villagerId;
        this.villagerName = villagerName;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 30;
        addRenderableWidget(Button.builder(Component.literal("Named villager"), button -> choose(false))
                .bounds(x, y, 200, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Hire as worker"), button -> choose(true))
                .bounds(x, y + 28, 200, 20)
                .build());
    }

    private void choose(boolean hire) {
        PacketDistributor.sendToServer(new ModPayloads.HireChoicePayload(villagerId, hire));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, villagerName, this.width / 2, this.height / 2 - 62, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Keep the name, or hire them as your worker.", this.width / 2, this.height / 2 - 48, 0xCCCCCC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
