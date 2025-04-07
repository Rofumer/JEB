package items.items.accessor;

import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;

import java.util.Map;

public interface ClientRecipeBookAccessor {
    Map<NetworkRecipeId, RecipeDisplayEntry> getRecipes();
}
