package jeb.mixin;

import jeb.client.JEBClient;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(CraftingRecipeBookComponent.class)
public class AbstractCraftingRecipeBookWidgetMixin {

    @Inject(method = "selectMatchingRecipes", at = @At("HEAD"), cancellable = true)
    private void alwaysDisplayRecipes(RecipeCollection recipeResultCollection, StackedItemContents recipeFinder, CallbackInfo ci) {
        // вызываем вручную с заменённым фильтром

        // Безопасно работаем с searchField
        var searchField = ((RecipeBookWidgetAccessor) this).getSearchField();
        if (searchField != null && searchField.canConsumeInput() && searchField.isVisible() && searchField.isFocused()) {
            ci.cancel();
        }

        if (JEBClient.customToggleEnabled) {

            recipeResultCollection.selectRecipes(recipeFinder, recipe -> true);

            // отменяем оригинальный вызов
            ci.cancel();
        }
        else {
            //recipeResultCollection.populateRecipes(recipeFinder, recipe -> false);
            //ci.cancel();
        }

    }

    @Redirect(
            method = "<clinit>", // static initializer
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"
            )
    )
    private static List<RecipeBookComponent.TabInfo> jeb$replaceTabs(
            Object o1, Object o2, Object o3, Object o4, Object o5
    ) {
        List<RecipeBookComponent.TabInfo> tabs = new ArrayList<>();
        tabs.add((RecipeBookComponent.TabInfo) o1);
        tabs.add((RecipeBookComponent.TabInfo) o2);
        tabs.add((RecipeBookComponent.TabInfo) o3);
        tabs.add((RecipeBookComponent.TabInfo) o4);
        tabs.add((RecipeBookComponent.TabInfo) o5);

        // Добавим свою вкладку
        RecipeBookComponent.TabInfo customTab = new RecipeBookComponent.TabInfo(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        tabs.add(1, customTab); // в начало

        return tabs;
    }

}
