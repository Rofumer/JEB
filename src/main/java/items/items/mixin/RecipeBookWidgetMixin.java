package items.items.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.recipe.book.RecipeBookGroup;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.RecipeDisplayEntry;
import items.items.accessor.ClientRecipeBookAccessor;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;

import static net.minecraft.client.resource.language.I18n.translate;

@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetMixin {

    @Inject(method = "select", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickRecipe(ILnet/minecraft/recipe/NetworkRecipeId;Z)V",
            shift = At.Shift.AFTER
    ))
    private void onRecipeClicked(RecipeResultCollection results, NetworkRecipeId recipeId, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientRecipeBook recipeBook = client.player.getRecipeBook();

        Map<NetworkRecipeId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipeId);

        Screen screen = client.currentScreen;
        if (screen instanceof RecipeBookProvider provider && entry != null) {
            provider.onCraftFailed(entry.display());
        }
    }

    @Inject(
            method = "refreshResults",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeBookResults;setResults(Ljava/util/List;ZZ)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void items$beforeSetResults(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci,
                                        List<RecipeResultCollection> list,
                                        List<RecipeResultCollection> list2,
                                        String string) {

        System.out.println("list2 содержит " + list2.size() + " рецептов");

        // Получаем доступ к searchField через наш accessor
        RecipeBookWidgetAccessor accessor = (RecipeBookWidgetAccessor) this;
        String searchText = accessor.getSearchField().getText();  // Получаем текст из поля поиска

        // 🔹 Собираем все предметы, уже встречающиеся в list2 как результат
        Set<Item> existingResultItems = new HashSet<>();
        for (RecipeResultCollection collection : list2) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                SlotDisplay.StackSlotDisplay result = (SlotDisplay.StackSlotDisplay) entry.display().result();
                existingResultItems.add(result.stack().getItem());
            }
        }

        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            if (existingResultItems.contains(item)) continue;

            if (!translate(item.getTranslationKey()).toLowerCase().contains(searchText.toLowerCase())) continue;

            Identifier id = Registries.ITEM.getId(item);
            System.out.println("Item: " + id);

            NetworkRecipeId recipeId = new NetworkRecipeId(9999);

            List<SlotDisplay> slots = new ArrayList<>();
            slots.add(new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", id.getPath()))));

            SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item, 1));

            SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                    Registries.ITEM.get(Identifier.of("minecraft", "crafting_table"))
            );

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry recipeDisplayEntry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(recipeDisplayEntry));

            list2.add(myCustomRecipeResultCollection);
        }

        System.out.println("2: list2 содержит " + list2.size() + " рецептов");
        System.out.println("Текст в поисковом поле: " + searchText);
    }




}
