package jeb.accessor;

import java.util.Map;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

public interface ClientRecipeBookAccessor {
    Map<RecipeDisplayId, RecipeDisplayEntry> getRecipes();
}
