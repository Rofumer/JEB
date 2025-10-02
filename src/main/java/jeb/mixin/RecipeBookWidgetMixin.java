package jeb.mixin;

import net.minecraft.item.Item;
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
import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
    private void onRecipeClicked(RecipeResultCollection results, NetworkRecipeId recipeId, boolean bl, CallbackInfoReturnable<Boolean> cir) {
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

        //System.out.println("list2 содержит " + list2.size() + " рецептов");

        // Получаем доступ к searchField через наш accessor
        RecipeBookWidgetAccessor accessor = (RecipeBookWidgetAccessor) this;
        String searchText = accessor.getSearchField().getText();  // Получаем текст из поля поиска

        // 🔹 Собираем все предметы, уже встречающиеся в list2 как результат
        Set<Item> existingResultItems = new HashSet<>();
        for (RecipeResultCollection collection : list2) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                getItemFromSlotDisplay(entry.display().result()).ifPresent(existingResultItems::add);
            }
        }


        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            if (existingResultItems.contains(item)) continue;

            if (!translate(item.getTranslationKey()).toLowerCase().contains(searchText.toLowerCase())) continue;

            Identifier id = Registries.ITEM.getId(item);
            //System.out.println("Item: " + id);

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

        if (!searchText.isEmpty()) {
            for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                if (existingResultItems.contains(item)) continue;

                Identifier id = Registries.ITEM.getId(item);
                String idString = id.toString().toLowerCase(); // без Locale
                String name = item.getName().getString().toLowerCase(); // без Locale
                String searchLower = searchText.toLowerCase(); // без Locale

                // Если id или имя содержит текст поиска
                if (!idString.contains(searchLower) && !name.contains(searchLower)) continue;

                NetworkRecipeId recipeId = new NetworkRecipeId(9999);

                List<SlotDisplay> slots = List.of(
                        new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, id))
                );

                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item));
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);

                OptionalInt group = OptionalInt.empty();
                RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
                List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
                RecipeResultCollection resultCollection = new RecipeResultCollection(List.of(entry));

                list2.add(resultCollection);
            }
        }



        //System.out.println("2: list2 содержит " + list2.size() + " рецептов");
        //System.out.println("Текст в поисковом поле: " + searchText);
    }


    @Unique
    private static Optional<Item> getItemFromSlotDisplay(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.StackSlotDisplay(ItemStack stack)) {
            return Optional.of(stack.getItem());
        }

        if (slot instanceof SlotDisplay.ItemSlotDisplay(RegistryEntry<Item> item)) {
            return Optional.of(item.value());
        }

        if (slot instanceof SlotDisplay.TagSlotDisplay(TagKey<Item> tag)) {

            // В 1.21.5 можно безопасно использовать iterateEntries
            for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
                return Optional.of(entry.value());
            }
        }

        if (slot instanceof SlotDisplay.CompositeSlotDisplay(List<SlotDisplay> contents)) {
            for (SlotDisplay inner : contents) {
                Optional<Item> maybeItem = getItemFromSlotDisplay(inner);
                if (maybeItem.isPresent()) return maybeItem;
            }
        }

        return Optional.empty();
    }



}
