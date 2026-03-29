package jeb.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeBookPage.class)
public interface RecipeBookResultsAccessor {
    @Accessor("parent")
    RecipeBookComponent<?> getRecipeBookWidget();
    @Accessor("hoveredButton")
    RecipeButton getHoveredResultButton();
}