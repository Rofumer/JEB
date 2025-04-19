package jeb.mixin;

import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeResultCollection.class)
public class RecipeResultCollectionMixin {
    @Inject(method = "hasDisplayableRecipes", at = @At("HEAD"), cancellable = true)
    private void showAllRecipes(CallbackInfoReturnable<Boolean> cir) {
        // Принудительно возвращаем true, чтобы рецепт считался отображаемым
        cir.setReturnValue(true);
    }
}