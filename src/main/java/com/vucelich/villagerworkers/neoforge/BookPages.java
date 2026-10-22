package com.vucelich.villagerworkers.neoforge;

import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

/** Text of a written book or a book and quill. Used to read schematic piece lines. */
public final class BookPages {
    private BookPages() {}

    public static String text(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            StringBuilder builder = new StringBuilder();
            for (Component page : written.getPages(false)) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(page.getString());
            }
            return builder.toString();
        }
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null) {
            return writable.getPages(false).collect(Collectors.joining("\n"));
        }
        return null;
    }
}
