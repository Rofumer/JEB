package jeb.mixin;

import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeBookResults.class)
public interface RecipeBookResultsAccessor {
    @Accessor("recipeBookWidget")
    RecipeBookWidget<?> getRecipeBookWidget();
    @Accessor("hoveredResultButton")
    AnimatedResultButton getHoveredResultButton();
}