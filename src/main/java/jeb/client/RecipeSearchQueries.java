package jeb.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class RecipeSearchQueries {
    private RecipeSearchQueries() {
    }

    public static String forResult(ItemStack stack) {
        String hoverName = stack.getHoverName().getString().toLowerCase(Locale.ROOT).trim();
        String itemName = hoverName.isEmpty()
                ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT)
                : hoverName;
        return "~" + itemName;
    }

    public static String forIngredient(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return "#" + itemId.toLowerCase(Locale.ROOT);
    }
}
