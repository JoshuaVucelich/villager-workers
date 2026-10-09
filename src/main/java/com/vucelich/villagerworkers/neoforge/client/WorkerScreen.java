package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.MenuIds;
import com.vucelich.villagerworkers.neoforge.ModPayloads;
import com.vucelich.villagerworkers.neoforge.WorkerMenu;
import com.vucelich.villagerworkers.neoforge.WorkerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class WorkerScreen extends AbstractContainerScreen<WorkerMenu> {
    private boolean permissions;
    private ModPayloads.PermissionsView permissionsView;
    private EditBox widthBox;
    private EditBox heightBox;
    private EditBox lengthBox;
    private EditBox wallBox;
    private EditBox branchSpacingBox;
    private EditBox branchLengthBox;
    private EditBox shaftWidthBox;
    private EditBox shaftDepthBox;
    private EditBox valuablesBox;
    private EditBox whitelistBox;
    private EditBox trustedBox;
    private EditBox transferBox;

    public WorkerScreen(WorkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 284;
        this.imageHeight = 236;
        this.inventoryLabelY = 10000;
    }

    public void applySnapshot(WorkerView view) {
        this.menu.setView(view);
        this.permissions = false;
        rebuildWidgets();
    }

    public void applyPermissions(ModPayloads.PermissionsView view) {
        this.permissionsView = view;
        this.permissions = true;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        WorkerView view = this.menu.view();
        int x = this.leftPos + 8;
        int y = this.topPos + 62;
        addRenderableWidget(button("None", x, y, 60, MenuIds.JOB_NONE));
        addRenderableWidget(button("Lumberjack", x + 64, y, 70, MenuIds.JOB_LUMBERJACK));
        addRenderableWidget(button("Miner", x + 138, y, 60, MenuIds.JOB_MINER));
        addRenderableWidget(button("Builder", x + 202, y, 66, MenuIds.JOB_BUILDER));
        y += 24;
        addRenderableWidget(button("Start", x, y, 60, MenuIds.START));
        addRenderableWidget(button("Stop", x + 64, y, 60, MenuIds.STOP));
        addRenderableWidget(button("Come", x + 128, y, 60, MenuIds.COME));
        if (view.viewerIsOwner()) {
            addRenderableWidget(button(permissions ? "Work" : "Permissions", x + 192, y, 76, MenuIds.SHOW_PERMISSIONS));
        }
        if (permissions && view.viewerIsOwner()) {
            initPermissions(x, y + 26);
        } else {
            initWork(view, x, y + 26);
        }
    }

    private void initWork(WorkerView view, int x, int y) {
        switch (view.job()) {
            case "lumberjack" -> initLumberjack(view, x, y);
            case "miner" -> initMiner(view, x, y);
            case "builder" -> initBuilder(view, x, y);
            default -> {
            }
        }
    }

    private void initLumberjack(WorkerView view, int x, int y) {
        addRenderableWidget(button("Mark area", x, y, 80, MenuIds.MARK));
        addRenderableWidget(button(view.clearLeaves() ? "Leaves: on" : "Leaves: off", x + 84, y, 90, MenuIds.TOGGLE_LEAVES));
        addRenderableWidget(button(view.replant() ? "Replant: on" : "Replant: off", x + 178, y, 90, MenuIds.TOGGLE_REPLANT));
        y += 24;
        addRenderableWidget(button("Link logs", x, y, 80, MenuIds.LINK_LOG));
        addRenderableWidget(button("Link saplings", x + 84, y, 90, MenuIds.LINK_SAPLING));
        addRenderableWidget(button("Link tools", x + 178, y, 90, MenuIds.LINK_LUMBER_TOOL));
    }

    private void initMiner(WorkerView view, int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("Mode: " + view.minerMode()), button -> saveMiner(nextMode(view.minerMode())))
                .bounds(x, y, 120, 20)
                .build());
        if ("clear_box".equals(view.minerMode())) {
            addRenderableWidget(button("Mark area", x + 124, y, 80, MenuIds.MARK));
        } else {
            addRenderableWidget(button("Mark origin", x + 124, y, 90, MenuIds.MARK));
        }
        y += 24;
        if ("shaft".equals(view.minerMode())) {
            shaftWidthBox = box(x, y, 40, Integer.toString(view.shaftWidth()));
            shaftDepthBox = box(x + 44, y, 40, Integer.toString(view.shaftDepth()));
            addRenderableWidget(shaftWidthBox);
            addRenderableWidget(shaftDepthBox);
            addRenderableWidget(Button.builder(Component.literal(view.shaftUp() ? "Up" : "Down"), button -> saveMiner(view.minerMode(), !view.shaftUp()))
                    .bounds(x + 90, y, 50, 20)
                    .build());
        } else if (!"clear_box".equals(view.minerMode())) {
            widthBox = box(x, y, 36, Integer.toString(view.width()));
            heightBox = box(x + 40, y, 36, Integer.toString(view.height()));
            lengthBox = box(x + 80, y, 40, Integer.toString(view.length()));
            wallBox = box(x + 124, y, 140, view.wallBlock());
            addRenderableWidget(widthBox);
            addRenderableWidget(heightBox);
            addRenderableWidget(lengthBox);
            addRenderableWidget(wallBox);
            if ("branch".equals(view.minerMode())) {
                y += 22;
                branchSpacingBox = box(x, y, 40, Integer.toString(view.branchSpacing()));
                branchLengthBox = box(x + 44, y, 40, Integer.toString(view.branchLength()));
                addRenderableWidget(branchSpacingBox);
                addRenderableWidget(branchLengthBox);
            }
        }
        y += 22;
        valuablesBox = box(x, y, 180, view.valuables());
        addRenderableWidget(valuablesBox);
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveMiner(view.minerMode()))
                .bounds(x + 184, y, 50, 20)
                .build());
        y += 22;
        addRenderableWidget(button("Valuables", x, y, 70, MenuIds.LINK_VALUABLES));
        addRenderableWidget(button("Regular", x + 74, y, 60, MenuIds.LINK_REGULAR));
        addRenderableWidget(button("Tools", x + 138, y, 50, MenuIds.LINK_MINER_TOOL));
        whitelistBox = box(x + 192, y, 70, view.whitelist());
        addRenderableWidget(whitelistBox);
    }

    private void initBuilder(WorkerView view, int x, int y) {
        addRenderableWidget(button("Link schematic", x, y, 110, MenuIds.LINK_SCHEMATIC));
        addRenderableWidget(button("Link chest", x + 114, y, 80, MenuIds.LINK_MATERIAL));
    }

    private void initPermissions(int x, int y) {
        trustedBox = box(x, y, 140, "");
        trustedBox.setHint(Component.literal("Online player"));
        addRenderableWidget(trustedBox);
        addRenderableWidget(Button.builder(Component.literal("Add"), button -> sendTrusted())
                .bounds(x + 144, y, 50, 20)
                .build());
        y += 24;
        if (permissionsView != null) {
            int row = y;
            int shown = 0;
            for (ModPayloads.Person person : permissionsView.trusted()) {
                if (shown >= 4) {
                    break;
                }
                UUID id = person.id();
                addRenderableWidget(Button.builder(Component.literal("Remove " + person.name()), button ->
                                PacketDistributor.sendToServer(new ModPayloads.RemoveTrustedPayload(menu.view().villagerId(), id)))
                        .bounds(x, row, 180, 20)
                        .build());
                row += 22;
                shown++;
            }
            y = row;
        }
        transferBox = box(x, y, 140, "");
        transferBox.setHint(Component.literal("New owner"));
        addRenderableWidget(transferBox);
        addRenderableWidget(Button.builder(Component.literal("Make owner"), button -> transfer())
                .bounds(x + 144, y, 90, 20)
                .build());
    }

    private void saveMiner(String mode) {
        saveMiner(mode, menu.view().shaftUp());
    }

    private void saveMiner(String mode, boolean shaftUp) {
        WorkerView view = menu.view();
        PacketDistributor.sendToServer(new ModPayloads.UpdateMinerPayload(
                view.villagerId(),
                mode,
                number(widthBox, view.width()),
                number(heightBox, view.height()),
                number(lengthBox, view.length()),
                wallBox == null ? view.wallBlock() : wallBox.getValue(),
                number(branchSpacingBox, view.branchSpacing()),
                number(branchLengthBox, view.branchLength()),
                number(shaftWidthBox, view.shaftWidth()),
                number(shaftDepthBox, view.shaftDepth()),
                shaftUp,
                valuablesBox == null ? view.valuables() : valuablesBox.getValue()));
        if (whitelistBox != null) {
            PacketDistributor.sendToServer(new ModPayloads.UpdateWhitelistPayload(view.villagerId(), splitIds(whitelistBox.getValue())));
        }
    }

    private void sendTrusted() {
        if (trustedBox == null || trustedBox.getValue().isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new ModPayloads.AddTrustedPayload(menu.view().villagerId(), trustedBox.getValue().trim()));
    }

    private void transfer() {
        if (transferBox == null || permissionsView == null) {
            return;
        }
        String typed = transferBox.getValue().trim();
        for (ModPayloads.Person person : permissionsView.online()) {
            if (person.name().equalsIgnoreCase(typed)) {
                PacketDistributor.sendToServer(new ModPayloads.TransferOwnerPayload(menu.view().villagerId(), person.id()));
                return;
            }
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal("That player is not online in this dimension."), true);
        }
    }

    private Button button(String label, int x, int y, int width, int id) {
        return Button.builder(Component.literal(label), ignored -> click(id))
                .bounds(x, y, width, 20)
                .build();
    }

    private EditBox box(int x, int y, int width, String value) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.empty());
        box.setMaxLength(256);
        box.setValue(value == null ? "" : value);
        return box;
    }

    private void click(int id) {
        if (id == MenuIds.SHOW_PERMISSIONS && permissions) {
            permissions = false;
            rebuildWidgets();
            return;
        }
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private static int number(EditBox box, int fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static List<String> splitIds(String raw) {
        List<String> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                ids.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }

    private static String nextMode(String mode) {
        return switch (mode) {
            case "clear_box" -> "tunnel";
            case "tunnel" -> "branch";
            case "branch" -> "shaft";
            default -> "clear_box";
        };
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xF0201610);
        graphics.fill(this.leftPos + 2, this.topPos + 2, this.leftPos + this.imageWidth - 2, this.topPos + this.imageHeight - 2, 0xF03C2C20);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        WorkerView view = this.menu.view();
        graphics.drawString(this.font, view.name(), 8, 6, 0xFFFFFF, false);
        graphics.drawString(this.font, "Health " + view.health() + "/" + view.maxHealth()
                + "  " + view.activity()
                + "  " + (view.rested() ? "rested" : "not rested"), 8, 18, 0xDDDDDD, false);
        graphics.drawString(this.font, "Job " + view.job() + "  " + view.problem(), 8, 30, 0xDDDDDD, false);
        if (permissions && view.viewerIsOwner()) {
            String owner = permissionsView == null ? "..." : permissionsView.ownerName();
            graphics.drawString(this.font, "Owner: " + owner, 8, 46, 0xFFD27A, false);
            return;
        }
        graphics.drawString(this.font, detail(view), 8, 46, 0xC8BBA8, false);
    }

    private static String detail(WorkerView view) {
        return switch (view.job()) {
            case "lumberjack" -> "Area " + view.areaText()
                    + "  logs " + view.logChest()
                    + "  saplings " + view.saplingChest()
                    + "  tools " + view.lumberToolChest();
            case "miner" -> "clear_box".equals(view.minerMode())
                    ? "Area " + view.areaText()
                    : "Origin " + view.originText();
            case "builder" -> (view.createLoaded()
                            ? "Places the linked schematic from the material chests. "
                            : "Create is required before this worker can build. ")
                    + "Schematic " + view.schematic()
                    + "  chests " + view.materialChests();
            default -> "Pick a job. Nothing is broken or built yet.";
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
