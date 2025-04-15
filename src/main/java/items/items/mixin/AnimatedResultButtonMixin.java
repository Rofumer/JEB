package items.items.mixin;

import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.util.context.ContextParameterMap;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;





@Mixin(AnimatedResultButton.class)
public class AnimatedResultButtonMixin {

    @Redirect(
            method = "showResultCollection",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;filter(Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection$RecipeFilterMode;)Ljava/util/List;"
            )
    )
    private List<RecipeDisplayEntry> redirectFilter(RecipeResultCollection instance, RecipeResultCollection.RecipeFilterMode filterMode) {
        // Возвращаем все рецепты, без фильтрации
        return instance.getAllRecipes();
    }
}
