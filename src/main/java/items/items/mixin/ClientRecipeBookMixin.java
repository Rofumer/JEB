package items.items.mixin;

import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin implements items.items.accessor.ClientRecipeBookAccessor {

    @Accessor("recipes")
    @Override
    public abstract Map<NetworkRecipeId, RecipeDisplayEntry> getRecipes();
}
