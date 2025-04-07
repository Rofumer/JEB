package items.items.mixin;


import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.RecipeDisplayEntry;
import items.items.accessor.ClientRecipeBookAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetMixin {

    @Inject(method = "select", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickRecipe(ILnet/minecraft/recipe/NetworkRecipeId;Z)V",
            shift = At.Shift.AFTER
    ))
    private void onRecipeClicked(RecipeResultCollection results, NetworkRecipeId recipeId, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientRecipeBook recipeBook = client.player.getRecipeBook();

        Map<NetworkRecipeId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipeId);

        Screen screen = client.currentScreen;
        if (screen instanceof RecipeBookProvider provider && entry != null) {
            provider.onCraftFailed(entry.display());
        }
    }
}
