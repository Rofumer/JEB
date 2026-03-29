package jeb.mixin;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookTabButton.class)
public abstract class RecipeGroupButtonWidgetMixin {

    @Final
    @Shadow
    private RecipeBookComponent.TabInfo tabInfo;

    @Inject(method = "updateVisibility", at = @At("HEAD"), cancellable = true)
    public void updateVisibilityMethod(ClientRecipeBook recipeBook, CallbackInfoReturnable<Boolean> cir)
    {
        if(tabInfo.category() == RecipeBookCategories.CAMPFIRE) {
            ((ClickableWidgetAccessor)(Object)this).setVisible(true);
            cir.setReturnValue(true); // или visible, если так логичнее
            cir.cancel();
        }
    }

}
