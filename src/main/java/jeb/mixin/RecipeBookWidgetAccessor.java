package jeb.mixin;

import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookWidget.class)
public interface RecipeBookWidgetAccessor {
    @Invoker("reset")
    void invokeReset();

    @Accessor("searchField")
    TextFieldWidget getSearchField();


}