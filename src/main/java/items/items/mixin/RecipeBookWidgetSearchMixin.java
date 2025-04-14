package items.items.mixin;

import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Locale;

@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetSearchMixin<T extends AbstractRecipeScreenHandler> {

    @Shadow @Final
    private ClientRecipeBook recipeBook;

    @Shadow
    private RecipeGroupButtonWidget currentTab;

    @Shadow
    private MinecraftClient client;

    @Shadow
    private RecipeBookResults recipesArea;

    @Shadow
    private TextFieldWidget searchField;

    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomIngredientSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        if (!string.startsWith("#")) return;

        String query = string.substring(1).toLowerCase(Locale.ROOT);
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                if (recipeDisplayMatchesIngredientQuery(entry, query)) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }

        recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }

    @Unique
    private boolean recipeDisplayMatchesIngredientQuery(RecipeDisplayEntry entry, String query) {
        if (entry.craftingRequirements().isEmpty()) return false;

        return entry.craftingRequirements().get().stream().anyMatch(ingredient ->
                ingredient.getMatchingItems().anyMatch(regEntry -> {
                    ItemStack stack = new ItemStack(regEntry.value());
                    String itemName = stack.getItem().getName().getString().toLowerCase(Locale.ROOT);
                    return itemName.contains(query);
                })
        );
    }

}
