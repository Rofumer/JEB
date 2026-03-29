package jeb.mixin;

import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;

import static net.minecraft.client.resources.language.I18n.get;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetMixin {

    @Inject(method = "tryPlaceRecipe", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V",
            shift = At.Shift.AFTER
    ))
    private void onRecipeClicked(RecipeCollection results, RecipeDisplayId recipeId, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        ClientRecipeBook recipeBook = client.player.getRecipeBook();

        Map<RecipeDisplayId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipeId);

        Screen screen = client.screen;
        if (screen instanceof RecipeUpdateListener provider && entry != null) {
            provider.fillGhostRecipe(entry.display());
        }
    }

    @Inject(
            method = "updateCollections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void items$beforeSetResults(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci,
                                        List<RecipeCollection> list,
                                        List<RecipeCollection> list2,
                                        String string) {

        //System.out.println("list2 содержит " + list2.size() + " рецептов");

        // Получаем доступ к searchField через наш accessor
        RecipeBookWidgetAccessor accessor = (RecipeBookWidgetAccessor) this;
        String searchText = accessor.getSearchField().getValue();  // Получаем текст из поля поиска

        // 🔹 Собираем все предметы, уже встречающиеся в list2 как результат
        Set<Item> existingResultItems = new HashSet<>();
        for (RecipeCollection collection : list2) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                getItemFromSlotDisplay(entry.display().result()).ifPresent(existingResultItems::add);
            }
        }


        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            if (existingResultItems.contains(item)) continue;

            if (!get(item.getDescriptionId()).toLowerCase().contains(searchText.toLowerCase())) continue;

            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            //System.out.println("Item: " + id);

            RecipeDisplayId recipeId = new RecipeDisplayId(9999);

            List<SlotDisplay> slots = new ArrayList<>();
            slots.add(new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", id.getPath()))));

            SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(item, 1));

            SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                    BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "crafting_table"))
            );

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.of(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry recipeDisplayEntry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            RecipeCollection myCustomRecipeResultCollection = new RecipeCollection(List.of(recipeDisplayEntry));

            list2.add(myCustomRecipeResultCollection);
        }

        if (!searchText.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                if (existingResultItems.contains(item)) continue;

                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                String idString = id.toString().toLowerCase(); // без Locale
                String name = item.getDefaultInstance().getHoverName().getString().toLowerCase(); // без Locale
                String searchLower = searchText.toLowerCase(); // без Locale

                // Если id или имя содержит текст поиска
                if (!idString.contains(searchLower) && !name.contains(searchLower)) continue;

                RecipeDisplayId recipeId = new RecipeDisplayId(9999);

                List<SlotDisplay> slots = List.of(
                        new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, id))
                );

                SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(item));
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);

                OptionalInt group = OptionalInt.empty();
                RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
                List<Ingredient> ingredients = List.of(Ingredient.of(item));

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
                RecipeCollection resultCollection = new RecipeCollection(List.of(entry));

                list2.add(resultCollection);
            }
        }



        //System.out.println("2: list2 содержит " + list2.size() + " рецептов");
        //System.out.println("Текст в поисковом поле: " + searchText);
    }


    @Unique
    private static Optional<Item> getItemFromSlotDisplay(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate stack)) {
            return Optional.of(stack.item().value());
        }

        if (slot instanceof SlotDisplay.ItemSlotDisplay(Holder<Item> item)) {
            return Optional.of(item.value());
        }

        if (slot instanceof SlotDisplay.TagSlotDisplay(TagKey<Item> tag)) {

            // В 1.21.5 можно безопасно использовать iterateEntries
            for (Holder<Item> entry : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                return Optional.of(entry.value());
            }
        }

        if (slot instanceof SlotDisplay.Composite(List<SlotDisplay> contents)) {
            for (SlotDisplay inner : contents) {
                Optional<Item> maybeItem = getItemFromSlotDisplay(inner);
                if (maybeItem.isPresent()) return maybeItem;
            }
        }

        return Optional.empty();
    }



}
