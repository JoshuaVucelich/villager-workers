package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.common.ValuableCatalog;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.neoforge.MenuIds;
import com.vucelich.villagerworkers.neoforge.ModPayloads;
import com.vucelich.villagerworkers.neoforge.WorkerMenu;
import com.vucelich.villagerworkers.neoforge.WorkerView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class WorkerScreen extends AbstractContainerScreen<WorkerMenu> {
    private static final int TAB_JOB = 0;
    private static final int TAB_AREA = 1;
    private static final int TAB_STORAGE = 2;
    private static final int TAB_INVENTORY = 3;
    private static final int TAB_SETTINGS = 4;
    private static final int PAGE = 6;

    private int tab = TAB_JOB;
    private int valuablePage;
    private ModPayloads.PermissionsView permissionsView;

    public WorkerScreen(WorkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WorkerMenu.PANEL_WIDTH;
        this.imageHeight = WorkerMenu.PANEL_HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    public void applySnapshot(WorkerView view) {
        this.menu.setView(view);
        rebuildWidgets();
    }

    public void applyPermissions(ModPayloads.PermissionsView view) {
        this.permissionsView = view;
        this.tab = TAB_SETTINGS;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + 8;
        int y = this.topPos + 36;
        int tabWidth = 84;
        int tabStep = 86;
        addRenderableWidget(tabButton("Job", "Choose the job and start, stop, or call the worker.", x, y, tabWidth, TAB_JOB));
        addRenderableWidget(tabButton("Area", "Mark where this worker works, and set the size.", x + tabStep, y, tabWidth, TAB_AREA));
        addRenderableWidget(tabButton("Storage", "Link the chests this worker uses.", x + tabStep * 2, y, tabWidth, TAB_STORAGE));
        addRenderableWidget(tabButton("Inventory", "Items this worker is carrying. Shift-click to move them.", x + tabStep * 3, y, tabWidth, TAB_INVENTORY));
        addRenderableWidget(tabButton("Settings", "Who else may use this worker, and extra blocks it may break.", x + tabStep * 4, y, tabWidth, TAB_SETTINGS));
        WorkerView view = this.menu.view();
        int content = this.topPos + 56;
        switch (tab) {
            case TAB_AREA -> initArea(view, x, content);
            case TAB_STORAGE -> initStorage(view, x, content);
            case TAB_SETTINGS -> initSettings(view, x, content);
            case TAB_INVENTORY -> {
            }
            default -> initJob(x, content);
        }
    }

    private void initJob(int x, int y) {
        addRenderableWidget(action("None", "No job. This worker waits.", x, y, 54, MenuIds.JOB_NONE));
        addRenderableWidget(action("Lumberjack", "Cuts trees inside the marked area.", x + 56, y, 74, MenuIds.JOB_LUMBERJACK));
        addRenderableWidget(action("Miner", "Digs the marked area, tunnel, branch, or shaft.", x + 132, y, 52, MenuIds.JOB_MINER));
        addRenderableWidget(action("Builder", "Places a linked Create schematic.", x + 186, y, 62, MenuIds.JOB_BUILDER));
        y += 20;
        addRenderableWidget(action("Start", "Begin the selected job.", x, y, 54, MenuIds.START));
        addRenderableWidget(action("Stop", "Stop and stay stopped until you start again.", x + 56, y, 54, MenuIds.STOP));
        addRenderableWidget(action("Come", "Walk to you, then wait.", x + 112, y, 54, MenuIds.COME));
    }

    private void initArea(WorkerView view, int x, int y) {
        switch (view.job()) {
            case "lumberjack" -> {
                addRenderableWidget(action("Mark area", "Use the area wand on two corners.", x, y, 88, MenuIds.MARK));
                addRenderableWidget(action(view.clearLeaves() ? "Leaves: on" : "Leaves: off", "Break leaves inside the area.", x + 92, y, 84, MenuIds.TOGGLE_LEAVES));
                addRenderableWidget(action(view.replant() ? "Replant: on" : "Replant: off", "Plant a sapling after a tree is cut.", x + 180, y, 88, MenuIds.TOGGLE_REPLANT));
            }
            case "miner" -> initMinerArea(view, x, y);
            case "builder" -> {
            }
            default -> {
            }
        }
    }

    private void initMinerArea(WorkerView view, int x, int y) {
        String mark = "clear_box".equals(view.minerMode()) ? "Mark area" : "Mark origin";
        String markTip = "clear_box".equals(view.minerMode())
                ? "Use the area wand on two corners."
                : "Use the area wand on the first block. Facing follows where you look.";
        addRenderableWidget(action("Mode: " + pretty(view.minerMode()), "Switch between clear box, tunnel, branch, and shaft.", x, y, 100, MenuIds.CYCLE_MODE));
        addRenderableWidget(action(mark, markTip, x + 102, y, 68, MenuIds.MARK));
        addRenderableWidget(action(
                view.useValuables() ? "Valuables: on" : "Valuables: off",
                "On: matching drops go to the valuables chest. Off: everything goes to the regular chest.",
                x + 172,
                y,
                76,
                MenuIds.TOGGLE_VALUABLES));
        y += 18;
        int cursor = x;
        cursor += pair(cursor, y, "W- " + view.width(), "Narrower tunnel. Width stays an odd number from 1 to 7.", MenuIds.WIDTH_DOWN, "W+", "Wider tunnel.", MenuIds.WIDTH_UP, 44);
        cursor += pair(cursor, y, "H- " + view.height(), "Lower the corridor. Height is 2 to 5.", MenuIds.HEIGHT_DOWN, "H+", "Raise the corridor.", MenuIds.HEIGHT_UP, 44);
        pair(cursor, y, "L- " + view.length(), "Shorter corridor. Length is 1 to 128.", MenuIds.LENGTH_DOWN, "L+", "Longer corridor.", MenuIds.LENGTH_UP, 52);
        y += 18;
        String wall = view.wallBlock() == null || view.wallBlock().isEmpty() ? "none" : shortName(view.wallBlock());
        addRenderableWidget(action("Wall: " + wall, "Cycle the block placed on the sides and ceiling. None means dig only.", x, y, 100, MenuIds.CYCLE_WALL));
        if ("branch".equals(view.minerMode())) {
            cursor = x + 102;
            cursor += pair(cursor, y, "Gap- " + view.branchSpacing(), "Closer branches.", MenuIds.BRANCH_SPACING_DOWN, "Gap+", "Blocks between side branches.", MenuIds.BRANCH_SPACING_UP, 48);
            pair(cursor, y, "Arm- " + view.branchLength(), "Shorter branches.", MenuIds.BRANCH_LENGTH_DOWN, "Arm+", "How far each branch reaches.", MenuIds.BRANCH_LENGTH_UP, 48);
        } else if ("shaft".equals(view.minerMode())) {
            cursor = x + 102;
            cursor += pair(cursor, y, "S- " + view.shaftWidth(), "Narrower shaft.", MenuIds.SHAFT_WIDTH_DOWN, "S+", "Shaft width, from 1 to 5.", MenuIds.SHAFT_WIDTH_UP, 40);
            pair(cursor, y, "D- " + view.shaftDepth(), "Shallower shaft.", MenuIds.SHAFT_DEPTH_DOWN, "D+", "How far the shaft goes.", MenuIds.SHAFT_DEPTH_UP, 44);
        }
        y += 18;
        if ("shaft".equals(view.minerMode())) {
            addRenderableWidget(action(view.shaftUp() ? "Shaft: up" : "Shaft: down", "Dig upward or downward from the origin.", x, y, 80, MenuIds.TOGGLE_SHAFT));
            y += 18;
        }
        initValuables(view, x, y);
    }

    private void initValuables(WorkerView view, int x, int y) {
        List<ResourceLocation> catalog = ValuableCatalog.entries();
        int pages = Math.max(1, (catalog.size() + PAGE - 1) / PAGE);
        if (valuablePage >= pages) {
            valuablePage = pages - 1;
        }
        if (valuablePage < 0) {
            valuablePage = 0;
        }
        int start = valuablePage * PAGE;
        addRenderableWidget(Button.builder(Component.literal("Prev"), ignored -> changePage(-1))
                .bounds(x, y, 40, 16)
                .tooltip(Tooltip.create(Component.literal("Previous ores and valuables.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal((valuablePage + 1) + "/" + pages), ignored -> {})
                .bounds(x + 42, y, 36, 16)
                .tooltip(Tooltip.create(Component.literal("Checked items go to the valuables chest. Ore blocks are listed for mods whose ore drops itself.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Next"), ignored -> changePage(1))
                .bounds(x + 80, y, 40, 16)
                .tooltip(Tooltip.create(Component.literal("Next ores and valuables.")))
                .build());
        y += 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 2; column++) {
                int index = start + row * 2 + column;
                if (index >= catalog.size()) {
                    return;
                }
                ResourceLocation id = catalog.get(index);
                boolean on = listed(view.valuables(), id.toString());
                String label = (on ? "[x] " : "[ ] ") + shortName(id.toString());
                int bx = x + column * 124;
                addRenderableWidget(action(label, id.toString(), bx, y + row * 18, 120, MenuIds.VALUABLE_BASE + index));
            }
        }
    }

    private void initStorage(WorkerView view, int x, int y) {
        switch (view.job()) {
            case "lumberjack" -> {
                addRenderableWidget(action("Link logs", "Then right-click a chest or barrel near the worker.", x, y, 88, MenuIds.LINK_LOG));
                addRenderableWidget(action("Link saplings", "Chest the replanted saplings are taken from.", x + 92, y, 96, MenuIds.LINK_SAPLING));
                addRenderableWidget(action("Link tools", "Chest that holds the axe.", x + 192, y, 76, MenuIds.LINK_LUMBER_TOOL));
            }
            case "miner" -> {
                addRenderableWidget(action("Valuables chest", "Where checked drops are delivered.", x, y, 90, MenuIds.LINK_VALUABLES));
                addRenderableWidget(action("Regular chest", "Where other drops, and everything when valuables are off, are delivered.", x + 92, y, 80, MenuIds.LINK_REGULAR));
                addRenderableWidget(action("Tool chest", "Chest that holds the pickaxe.", x + 174, y, 74, MenuIds.LINK_MINER_TOOL));
            }
            case "builder" -> {
                addRenderableWidget(action("Link schematic", "Right-click a placed schematicannon that already has a schematic.", x, y, 110, MenuIds.LINK_SCHEMATIC));
                addRenderableWidget(action("Link chest", "Right-click a chest of building materials. You can link several.", x + 114, y, 90, MenuIds.LINK_MATERIAL));
            }
            default -> {
            }
        }
    }

    private void initSettings(WorkerView view, int x, int y) {
        addRenderableWidget(action(
                "Allow held block",
                "Hold a block, then press this to let the worker break it. Press again to remove it.",
                x,
                y,
                120,
                MenuIds.WHITELIST_HELD));
        addRenderableWidget(action("Clear extra", "Remove every extra block this worker was allowed to break.", x + 124, y, 80, MenuIds.WHITELIST_CLEAR));
        if (!view.viewerIsOwner()) {
            return;
        }
        y += 36;
        if (permissionsView == null) {
            addRenderableWidget(action("Show people", "List online players so you can trust one or give them the worker.", x, y, 100, MenuIds.SHOW_PERMISSIONS));
            return;
        }
        int shown = 0;
        for (ModPayloads.Person person : permissionsView.trusted()) {
            if (shown >= 2) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal("Remove " + person.name()), ignored ->
                            PacketDistributor.sendToServer(new ModPayloads.RemoveTrustedPayload(view.villagerId(), person.id())))
                    .bounds(x, y, 160, 16)
                    .tooltip(Tooltip.create(Component.literal("Take control away from " + person.name() + ".")))
                    .build());
            y += 18;
            shown++;
        }
        shown = 0;
        for (ModPayloads.Person person : permissionsView.online()) {
            if (person.name().equalsIgnoreCase(permissionsView.ownerName()) || shown >= 2) {
                continue;
            }
            addRenderableWidget(Button.builder(Component.literal("Trust " + person.name()), ignored ->
                            PacketDistributor.sendToServer(new ModPayloads.AddTrustedPayload(view.villagerId(), person.name())))
                    .bounds(x, y, 130, 16)
                    .tooltip(Tooltip.create(Component.literal(person.name() + " may use this worker.")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Give"), ignored ->
                            PacketDistributor.sendToServer(new ModPayloads.TransferOwnerPayload(view.villagerId(), person.id())))
                    .bounds(x + 134, y, 50, 16)
                    .tooltip(Tooltip.create(Component.literal("Make " + person.name() + " the owner. You give the worker up.")))
                    .build());
            y += 18;
            shown++;
        }
    }

    private int pair(
            int x, int y, String downLabel, String downTip, int downId, String upLabel, String upTip, int upId, int downWidth) {
        addRenderableWidget(action(downLabel, downTip, x, y, downWidth, downId));
        addRenderableWidget(action(upLabel, upTip, x + downWidth + 2, y, 26, upId));
        return downWidth + 30;
    }

    private Button tabButton(String label, String tip, int x, int y, int width, int index) {
        String text = tab == index ? "[" + label + "]" : label;
        return Button.builder(Component.literal(text), ignored -> selectTab(index))
                .bounds(x, y, width, 16)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
    }

    private Button action(String label, String tip, int x, int y, int width, int id) {
        return Button.builder(Component.literal(label), ignored -> click(id))
                .bounds(x, y, width, 16)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
    }

    private void selectTab(int next) {
        this.tab = next;
        if (next == TAB_SETTINGS && this.menu.view().viewerIsOwner()) {
            click(MenuIds.SHOW_PERMISSIONS);
        }
        rebuildWidgets();
    }

    private void changePage(int delta) {
        this.valuablePage += delta;
        rebuildWidgets();
    }

    private void click(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xF0201610);
        graphics.fill(this.leftPos + 2, this.topPos + 2, this.leftPos + this.imageWidth - 2, this.topPos + this.imageHeight - 2, 0xF03C2C20);
        int index = 0;
        for (Slot slot : this.menu.slots) {
            int border = index == WorkerData.TOOL_SLOT ? 0xFFC4A15A : 0xFF1A120C;
            graphics.fill(this.leftPos + slot.x - 1, this.topPos + slot.y - 1, this.leftPos + slot.x + 17, this.topPos + slot.y + 17, border);
            graphics.fill(this.leftPos + slot.x, this.topPos + slot.y, this.leftPos + slot.x + 16, this.topPos + slot.y + 16, 0xFF2A1C14);
            index++;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        WorkerView view = this.menu.view();
        graphics.drawString(this.font, view.name(), 8, 6, 0xFFFFFF, false);
        String health = "Health " + view.health() + "/" + view.maxHealth();
        graphics.drawString(this.font, health, 8, 16, 0xDDDDDD, false);
        int activityX = 8 + this.font.width(health) + 6;
        graphics.drawString(this.font, view.activity(), activityX, 16, activityColor(view.activity()), false);
        String rested = view.rested() ? "rested" : "not rested";
        graphics.drawString(this.font, rested, activityX + this.font.width(view.activity()) + 6, 16, view.rested() ? 0x55FF55 : 0xFFAA55, false);
        String problem = view.problem();
        int problemColor = "No problems.".equals(problem) ? 0x55FF55 : 0xFF5555;
        graphics.drawString(this.font, "Job " + view.job(), 8, 26, 0xDDDDDD, false);
        graphics.drawString(this.font, problem, 8 + this.font.width("Job " + view.job() + "  "), 26, problemColor, false);
    }

    private static String detail(WorkerView view) {
        return switch (view.job()) {
            case "lumberjack" -> trim("Area " + view.areaText() + "  logs " + view.logChest() + "  saplings " + view.saplingChest());
            case "miner" -> trim("clear_box".equals(view.minerMode())
                    ? "Area " + view.areaText() + "  regular " + view.regularChest() + "  valuables " + view.valuablesChest()
                    : "Origin " + view.originText() + "  regular " + view.regularChest() + "  valuables " + view.valuablesChest());
            case "builder" -> trim((view.createLoaded() ? "Schematic " : "Create is required. Schematic ")
                    + view.schematic() + "  chests " + view.materialChests());
            default -> "Pick a job. Nothing is broken or built yet.";
        };
    }

    private static String trim(String text) {
        return text.length() > 40 ? text.substring(0, 37) + "..." : text;
    }

    private static int activityColor(String activity) {
        return switch (activity) {
            case "working" -> 0x55FF55;
            case "stopped" -> 0xFF5555;
            case "coming" -> 0xFFD27A;
            default -> 0xDDDDDD;
        };
    }

    private static boolean listed(String valuables, String id) {
        if (valuables == null || valuables.isEmpty()) {
            return false;
        }
        for (String part : valuables.split(",")) {
            if (part.trim().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String pretty(String mode) {
        return switch (mode) {
            case "clear_box" -> "clear box";
            case "tunnel" -> "tunnel";
            case "branch" -> "branch";
            case "shaft" -> "shaft";
            default -> mode;
        };
    }

    private static String shortName(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.length() > 16 ? path.substring(0, 15) + "." : path;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawHelp(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawHelp(GuiGraphics graphics) {
        if (tab == TAB_INVENTORY) {
            graphics.drawString(this.font, "27 carry slots. The gold slot is the tool.", this.leftPos + 8, this.topPos + 62, 0xFFD27A, false);
            graphics.drawString(this.font, "Shift-click moves items in or out.", this.leftPos + 8, this.topPos + 74, 0xC8BBA8, false);
            graphics.drawString(this.font, "Mined items wait here for a chest.", this.leftPos + 8, this.topPos + 86, 0xC8BBA8, false);
        } else if (tab == TAB_JOB) {
            graphics.drawString(this.font, "Opening this menu holds the worker still.", this.leftPos + 8, this.topPos + 100, 0xC8BBA8, false);
            graphics.drawString(this.font, detail(this.menu.view()), this.leftPos + 8, this.topPos + 112, 0xC8BBA8, false);
        } else if (tab == TAB_AREA && "lumberjack".equals(this.menu.view().job())) {
            graphics.drawString(this.font, detail(this.menu.view()), this.leftPos + 8, this.topPos + 82, 0xC8BBA8, false);
        } else if (tab == TAB_STORAGE) {
            graphics.drawString(this.font, detail(this.menu.view()), this.leftPos + 8, this.topPos + 82, 0xC8BBA8, false);
        } else if (tab == TAB_SETTINGS) {
            WorkerView view = this.menu.view();
            String extra = view.whitelist() == null || view.whitelist().isEmpty() ? "none" : view.whitelist();
            graphics.drawString(this.font, trim("Extra blocks: " + extra), this.leftPos + 8, this.topPos + 78, 0xC8BBA8, false);
            if (!view.viewerIsOwner()) {
                graphics.drawString(this.font, "Only the owner can change who uses this worker.", this.leftPos + 8, this.topPos + 96, 0xFFAA55, false);
            }
        } else if (tab == TAB_AREA && "builder".equals(this.menu.view().job())) {
            String line = this.menu.view().createLoaded()
                    ? "The builder uses the linked schematic. Link chests on Storage."
                    : "Create is required before this worker can build.";
            graphics.drawString(this.font, line, this.leftPos + 8, this.topPos + 62, 0xC8BBA8, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
