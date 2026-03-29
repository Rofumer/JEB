package jeb.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookWidgetAccessor {
    @Invoker("initVisuals")
    void invokeReset();

    @Accessor("searchBox")
    EditBox getSearchField();
    @Accessor("tabButtons")
    List<?> getTabButtons();
    @Accessor("tabInfos")
    List<RecipeBookComponent.TabInfo> getTabs();
    @Invoker("updateTabs")
    void jeb$refreshTabButtons(boolean filteringCraftable);
    @Invoker("selectMatchingRecipes")
    void jeb$populateAllRecipes();
    @Accessor("selectedTab")
    RecipeBookTabButton getSelectedTab();
    @Accessor("selectedTab")
    void setSelectedTab(RecipeBookTabButton tab);
}