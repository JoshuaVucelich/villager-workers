package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Ores and drops the miner screen can check off. No ids are typed.
 * Vanilla entries are always offered. Modded ores come from the {@code c:ores} block tag.
 */
public final class ValuableCatalog {
    public static final List<String> VANILLA_IDS = List.of(
            "minecraft:coal_ore",
            "minecraft:deepslate_coal_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:redstone_ore",
            "minecraft:deepslate_redstone_ore",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:nether_gold_ore",
            "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris",
            "minecraft:coal",
            "minecraft:raw_iron",
            "minecraft:raw_copper",
            "minecraft:raw_gold",
            "minecraft:diamond",
            "minecraft:emerald",
            "minecraft:lapis_lazuli",
            "minecraft:redstone",
            "minecraft:quartz",
            "minecraft:amethyst_shard");

    private ValuableCatalog() {}

    public static List<ResourceLocation> entries() {
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        for (String id : VANILLA_IDS) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null && BuiltInRegistries.ITEM.containsKey(parsed)) {
                ids.add(parsed);
            }
        }
        TagKey<Block> ores = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));
        BuiltInRegistries.BLOCK.getTagOrEmpty(ores).forEach(holder -> {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(holder.value());
            if (blockId != null && BuiltInRegistries.ITEM.containsKey(blockId)) {
                ids.add(blockId);
            }
        });
        return List.copyOf(ids);
    }

    public static List<String> labels() {
        List<String> labels = new ArrayList<>();
        for (ResourceLocation id : entries()) {
            labels.add(id.toString());
        }
        return labels;
    }
}
