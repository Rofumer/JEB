package jeb.mixin;

import jeb.client.JEBClient;
import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    // JEB показывает все рецепты, включая 3x3 в инвентаре 2x2. Не отправляем такие рецепты на сервер,
    // иначе он может разложить в сетку только часть ингредиентов.
    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void jeb$skipTooBigRecipe(int containerId, RecipeDisplayId recipe, boolean useMaxItems, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu.containerId != containerId || !(menu instanceof RecipeBookMenu bookMenu)) return;
        RecipeDisplayEntry entry = ((ClientRecipeBookAccessor) client.player.getRecipeBook()).getRecipes().get(recipe);
        if (entry != null && !JEBClient.fitsCraftingGrid(bookMenu, entry.display())) {
            ci.cancel();
        }
    }
}
