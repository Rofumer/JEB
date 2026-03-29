package jeb.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeCollection.class)
public class RecipeResultCollectionMixin {
    @Inject(method = "hasAnySelected", at = @At("HEAD"), cancellable = true)
    private void showAllRecipes(CallbackInfoReturnable<Boolean> cir) {
        // Принудительно возвращаем true, чтобы рецепт считался отображаемым
        cir.setReturnValue(true);
    }
}