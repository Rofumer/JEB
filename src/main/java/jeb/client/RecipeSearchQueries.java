package jeb.client;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.Locale;

public final class RecipeSearchQueries {
    private RecipeSearchQueries() {
    }

    public static String forResult(ItemStack stack) {
        String hoverName = stack.getName().getString().toLowerCase(Locale.ROOT).trim();
        String itemName = hoverName.isEmpty()
                ? Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT)
                : hoverName;
        return "~" + itemName;
    }

    public static String forIngredient(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        return "#" + itemId.toLowerCase(Locale.ROOT);
    }
}
