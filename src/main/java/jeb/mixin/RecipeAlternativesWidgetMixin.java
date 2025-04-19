package jeb.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screen.recipebook.RecipeAlternativesWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.util.context.ContextParameterMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
//target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;filter(Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection$RecipeFilterMode;)Ljava/util/List;",
@Mixin(RecipeAlternativesWidget.class)
public class RecipeAlternativesWidgetMixin {
    @Redirect(
            method = "showAlternativesForResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;filter(Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection$RecipeFilterMode;)Ljava/util/List;",
                    ordinal = 1
            )
    )
    private List<RecipeDisplayEntry> redirectList2Filter(RecipeResultCollection instance, RecipeResultCollection.RecipeFilterMode filterMode) {
        return instance.getAllRecipes();
    }

}
