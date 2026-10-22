package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.MenuIds;
import com.vucelich.villagerworkers.neoforge.ModPayloads;
import com.vucelich.villagerworkers.neoforge.WorkerMenu;
import com.vucelich.villagerworkers.neoforge.WorkerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private boolean crew;
    private ModPayloads.PermissionsView permissionsView;
    private ModPayloads.CrewView crewView;
    private final Set<UUID> ticked = new HashSet<>();
    private String crewTarget = "FOREMAN";
    private EditBox moveBox;
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
    private EditBox ownerBox;
    private EditBox transferBox;

    public WorkerScreen(WorkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 284;
        this.imageHeight = 292;
        this.inventoryLabelY = 10000;
    }

    public void applySnapshot(WorkerView view) {
        this.menu.setView(view);
        this.permissions = false;
        this.crew = false;
        rebuildWidgets();
    }

    public void applyPermissions(ModPayloads.PermissionsView view) {
        this.permissionsView = view;
        this.permissions = true;
        this.crew = false;
        rebuildWidgets();
    }

    public void applyCrew(ModPayloads.CrewView view) {
        this.crewView = view;
        this.crew = true;
        this.permissions = false;
        Set<UUID> still = new HashSet<>();
        still.add(view.villagerId());
        for (ModPayloads.Person person : view.crew()) {
            still.add(person.id());
        }
        this.ticked.retainAll(still);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        this.imageHeight = crew ? 400 : 292;
        super.init();
        if (crew) {
            initCrew();
            return;
        }
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
        addRenderableWidget(button(view.foreman() ? "Demote" : "Promote to Foreman", x, y, 150, view.foreman() ? MenuIds.DEMOTE : MenuIds.PROMOTE));
        addRenderableWidget(button("Crew", x + 154, y, 60, MenuIds.SHOW_CREW));
        y += 24;
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
        y += 24;
        addRenderableWidget(button("Held schematic", x, y, 110, MenuIds.TAKE_SCHEMATIC));
        addRenderableWidget(button("Mark anchor", x + 114, y, 90, MenuIds.MARK));
        addRenderableWidget(button("Build now", x + 208, y, 60, MenuIds.BUILD_NOW));
        y += 24;
        addRenderableWidget(button("Scaffold: " + view.scaffold(), x, y, 160, MenuIds.SCAFFOLD));
    }

    private void initPermissions(int x, int y) {
        if (permissionsView != null && permissionsView.owners().size() > 1) {
            int shown = 0;
            for (ModPayloads.Person person : permissionsView.owners()) {
                if (shown >= 3) {
                    break;
                }
                UUID id = person.id();
                addRenderableWidget(Button.builder(Component.literal("Remove owner " + person.name()), button ->
                                PacketDistributor.sendToServer(new ModPayloads.RemoveOwnerPayload(menu.view().villagerId(), id)))
                        .bounds(x, y, 200, 20)
                        .build());
                y += 22;
                shown++;
            }
        }
        ownerBox = box(x, y, 120, "");
        ownerBox.setHint(Component.literal("Add owner"));
        addRenderableWidget(ownerBox);
        addRenderableWidget(Button.builder(Component.literal("Add owner"), button -> sendOwner())
                .bounds(x + 124, y, 80, 20)
                .build());
        y += 24;
        trustedBox = box(x, y, 120, "");
        trustedBox.setHint(Component.literal("Trusted player"));
        addRenderableWidget(trustedBox);
        addRenderableWidget(Button.builder(Component.literal("Add trusted"), button -> sendTrusted())
                .bounds(x + 124, y, 90, 20)
                .build());
        y += 24;
        if (permissionsView != null) {
            int shown = 0;
            for (ModPayloads.Person person : permissionsView.trusted()) {
                if (shown >= 2) {
                    break;
                }
                UUID id = person.id();
                addRenderableWidget(Button.builder(Component.literal("Remove " + person.name()), button ->
                                PacketDistributor.sendToServer(new ModPayloads.RemoveTrustedPayload(menu.view().villagerId(), id)))
                        .bounds(x, y, 180, 20)
                        .build());
                y += 22;
                shown++;
            }
        }
        transferBox = box(x, y, 120, "");
        transferBox.setHint(Component.literal("Sole owner"));
        addRenderableWidget(transferBox);
        addRenderableWidget(Button.builder(Component.literal("Sole owner"), button -> transfer())
                .bounds(x + 124, y, 90, 20)
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

    private void sendOwner() {
        if (ownerBox == null || ownerBox.getValue().isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new ModPayloads.AddOwnerPayload(menu.view().villagerId(), ownerBox.getValue().trim()));
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

    private void initCrew() {
        WorkerView view = this.menu.view();
        int x = this.leftPos + 8;
        int y = this.topPos + 58;
        addRenderableWidget(button("Work", x, y, 60, MenuIds.SHOW_CREW));
        ModPayloads.CrewView snapshot = this.crewView;
        if (snapshot == null) {
            return;
        }
        if (!snapshot.foreman()) {
            addRenderableWidget(Button.builder(Component.literal(snapshot.overrideForeman() ? "Own rules" : "Foreman rules"), ignored ->
                            PacketDistributor.sendToServer(new ModPayloads.SetOverridePayload(view.villagerId(), !snapshot.overrideForeman())))
                    .bounds(x + 64, y, 120, 20)
                    .build());
            if (!"none".equals(snapshot.assignedName())) {
                addRenderableWidget(button("Leave crew", x + 188, y, 80, -2));
            }
            y += 24;
            int shown = 0;
            for (ModPayloads.Person person : snapshot.foremen()) {
                if (shown >= 8) {
                    break;
                }
                UUID id = person.id();
                addRenderableWidget(Button.builder(Component.literal("Join " + person.name()), ignored ->
                                PacketDistributor.sendToServer(new ModPayloads.AssignCrewPayload(view.villagerId(), id)))
                        .bounds(x, y, 260, 20)
                        .build());
                y += 22;
                shown++;
            }
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("Apply to: " + targetLabel()), ignored -> {
            crewTarget = switch (crewTarget) {
                case "FOREMAN" -> "CREW";
                case "CREW" -> "TICKED";
                default -> "FOREMAN";
            };
            rebuildWidgets();
        }).bounds(x + 64, y, 160, 20).build());
        y += 24;
        addRenderableWidget(orderButton("Work", x, y, 60, "WORK"));
        addRenderableWidget(orderButton("Stay", x + 64, y, 60, "STAY"));
        addRenderableWidget(orderButton("Follow", x + 128, y, 70, "FOLLOW"));
        y += 24;
        addRenderableWidget(orderButton("No job", x, y, 60, "JOB_NONE"));
        addRenderableWidget(orderButton("Lumberjack", x + 64, y, 80, "JOB_LUMBERJACK"));
        addRenderableWidget(orderButton("Miner", x + 148, y, 50, "JOB_MINER"));
        addRenderableWidget(orderButton("Builder", x + 202, y, 66, "JOB_BUILDER"));
        y += 24;
        moveBox = box(x, y, 110, moveBox == null ? "" : moveBox.getValue());
        moveBox.setHint(Component.literal("Foreman or player"));
        addRenderableWidget(moveBox);
        addRenderableWidget(Button.builder(Component.literal("Move ticked"), ignored -> moveTicked(false))
                .bounds(x + 114, y, 90, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Give"), ignored -> moveTicked(true))
                .bounds(x + 208, y, 50, 20)
                .build());
        y += 24;
        int shown = 0;
        for (ModPayloads.Person person : snapshot.unassigned()) {
            if (shown >= 4) {
                break;
            }
            UUID id = person.id();
            addRenderableWidget(Button.builder(Component.literal("Add " + person.name()), ignored ->
                            PacketDistributor.sendToServer(new ModPayloads.AssignCrewPayload(id, view.villagerId())))
                    .bounds(x, y, 260, 20)
                    .build());
            y += 22;
            shown++;
        }
        int crewShown = 0;
        for (ModPayloads.Person person : snapshot.crew()) {
            if (crewShown >= 8) {
                break;
            }
            UUID id = person.id();
            boolean on = ticked.contains(id);
            addRenderableWidget(Button.builder(Component.literal((on ? "[x] " : "[ ] ") + person.name()), ignored -> {
                if (!ticked.add(id)) {
                    ticked.remove(id);
                }
                rebuildWidgets();
            }).bounds(x, y, 130, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Unassign"), ignored ->
                            PacketDistributor.sendToServer(new ModPayloads.UnassignCrewPayload(id)))
                    .bounds(x + 134, y, 70, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Come"), ignored ->
                            PacketDistributor.sendToServer(new ModPayloads.CrewOrderPayload(view.villagerId(), "TICKED", List.of(id), "FOLLOW")))
                    .bounds(x + 208, y, 50, 20)
                    .build());
            y += 22;
            crewShown++;
        }
    }

    private Button orderButton(String label, int x, int y, int width, String order) {
        return Button.builder(Component.literal(label), ignored -> sendOrder(order))
                .bounds(x, y, width, 20)
                .build();
    }

    private void sendOrder(String order) {
        PacketDistributor.sendToServer(new ModPayloads.CrewOrderPayload(
                menu.view().villagerId(), crewTarget, List.copyOf(ticked), order));
    }

    private void moveTicked(boolean toPlayer) {
        if (ticked.isEmpty()) {
            note("Tick a crew member first.");
            return;
        }
        String typed = moveBox == null ? "" : moveBox.getValue().trim();
        if (typed.isEmpty()) {
            note(toPlayer ? "Type the player name." : "Type the foreman name.");
            return;
        }
        UUID foremanId = new UUID(0L, 0L);
        if (!toPlayer) {
            foremanId = null;
            if (crewView != null) {
                for (ModPayloads.Person person : crewView.foremen()) {
                    if (person.name().equalsIgnoreCase(typed) && !person.id().equals(menu.view().villagerId())) {
                        foremanId = person.id();
                        break;
                    }
                }
            }
            if (foremanId == null) {
                note("That foreman is not in the list.");
                return;
            }
        }
        for (UUID id : List.copyOf(ticked)) {
            PacketDistributor.sendToServer(new ModPayloads.MoveCrewPayload(id, foremanId, toPlayer, typed));
        }
    }

    private void note(String text) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal(text), true);
        }
    }

    private String targetLabel() {
        return switch (crewTarget) {
            case "CREW" -> "whole crew";
            case "TICKED" -> "ticked";
            default -> "foreman";
        };
    }

    private void click(int id) {
        if (id == -2) {
            PacketDistributor.sendToServer(new ModPayloads.UnassignCrewPayload(menu.view().villagerId()));
            return;
        }
        if (id == MenuIds.SHOW_CREW && crew) {
            crew = false;
            rebuildWidgets();
            return;
        }
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
        String title = view.foreman() ? "Foreman " + view.name() : view.name();
        graphics.drawString(this.font, title, 8, 6, 0xFFFFFF, false);
        graphics.drawString(this.font, "Health " + view.health() + "/" + view.maxHealth()
                + "  " + view.activity()
                + "  " + (view.rested() ? "rested" : "not rested"), 8, 18, 0xDDDDDD, false);
        graphics.drawString(this.font, "Job " + view.job() + "  " + view.problem(), 8, 30, 0xDDDDDD, false);
        if (crew && crewView != null) {
            String line = crewView.foreman()
                    ? "Crew " + crewView.crew().size() + "  unassigned " + crewView.unassigned().size()
                    : "Assigned: " + crewView.assignedName();
            graphics.drawString(this.font, line, 8, 46, 0xFFD27A, false);
            return;
        }
        if (permissions && view.viewerIsOwner()) {
            String owner = permissionsView == null ? "..." : permissionsView.ownerName();
            graphics.drawString(this.font, "Owners: " + owner, 8, 46, 0xFFD27A, false);
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
