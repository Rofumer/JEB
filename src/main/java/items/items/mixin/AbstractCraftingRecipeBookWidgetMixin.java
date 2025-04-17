package items.items.mixin;

import net.minecraft.client.gui.screen.recipebook.AbstractCraftingRecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.RecipeFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractCraftingRecipeBookWidget.class)
public class AbstractCraftingRecipeBookWidgetMixin {

    @Inject(method = "populateRecipes", at = @At("HEAD"), cancellable = true)
    private void alwaysDisplayRecipes(RecipeResultCollection recipeResultCollection, RecipeFinder recipeFinder, CallbackInfo ci) {
        // вызываем вручную с заменённым фильтром
        recipeResultCollection.populateRecipes(recipeFinder, recipe -> true);

        // отменяем оригинальный вызов
        ci.cancel();
    }
}
