package jeb.mixin;

import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.book.RecipeBookCategories;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeGroupButtonWidget.class)
public abstract class RecipeGroupButtonWidgetMixin {

    @Final
    @Shadow
    private RecipeBookWidget.Tab tab;

    @Inject(method = "hasKnownRecipes", at = @At("HEAD"), cancellable = true)
    public void updateVisibilityMethod(ClientRecipeBook recipeBook, CallbackInfoReturnable<Boolean> cir)
    {
        if(tab.category() == RecipeBookCategories.CAMPFIRE) {
            ((ClickableWidgetAccessor)(Object)this).setVisible(true);
            cir.setReturnValue(true); // или visible, если так логичнее
            cir.cancel();
        }
    }

}
