package jeb.mixin;

import jeb.client.JEBClient;
import net.minecraft.client.gui.screen.recipebook.CraftingRecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(CraftingRecipeBookWidget.class)
public class AbstractCraftingRecipeBookWidgetMixin {

    @Inject(method = "populateRecipes", at = @At("HEAD"), cancellable = true)
    private void alwaysDisplayRecipes(RecipeResultCollection recipeResultCollection, RecipeFinder recipeFinder, CallbackInfo ci) {
        // вызываем вручную с заменённым фильтром

        // Безопасно работаем с searchField
        var searchField = ((RecipeBookWidgetAccessor) this).getSearchField();
        if (searchField != null && searchField.isActive() && searchField.isVisible() && searchField.isFocused()) {
            ci.cancel();
        }

        if (JEBClient.customToggleEnabled) {

            recipeResultCollection.populateRecipes(recipeFinder, recipe -> true);

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
    private static List<RecipeBookWidget.Tab> jeb$replaceTabs(
            Object o1, Object o2, Object o3, Object o4, Object o5
    ) {
        List<RecipeBookWidget.Tab> tabs = new ArrayList<>();
        tabs.add((RecipeBookWidget.Tab) o1);
        tabs.add((RecipeBookWidget.Tab) o2);
        tabs.add((RecipeBookWidget.Tab) o3);
        tabs.add((RecipeBookWidget.Tab) o4);
        tabs.add((RecipeBookWidget.Tab) o5);

        // Добавим свою вкладку
        RecipeBookWidget.Tab customTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        tabs.add(1, customTab); // в начало

        return tabs;
    }

}
