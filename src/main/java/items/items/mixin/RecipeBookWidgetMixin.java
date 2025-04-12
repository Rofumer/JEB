package items.items.mixin;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
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
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;

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


        for (Item item : Registries.ITEM) {

            if (item == Items.AIR ) { continue; }

        NetworkRecipeId recipeId = new NetworkRecipeId(9999);
        // Пример добавления кастомного рецепта (если есть подходящий объект)
        //Display:ShapelessCraftingRecipeDisplay[ingredients=[TagSlotDisplay[tag=TagKey[minecraft:item / minecraft:acacia_logs]]], result=StackSlotDisplay[stack=4 minecraft:acacia_planks], craftingStation=ItemSlotDisplay[item=Reference{ResourceKey[minecraft:item / minecraft:crafting_table]=minecraft:crafting_table}]];Category:Optional[ResourceKey[minecraft:recipe_book_category / minecraft:crafting_building_blocks]];NetworkID:NetworkRecipeId[index=7];Group:OptionalInt[7];Crafting Requirements Items:minecraft:acacia_log, minecraft:acacia_wood, minecraft:stripped_acacia_log, minecraft:stripped_acacia_wood;

        List<SlotDisplay> slots = new ArrayList<>();

        slots.add(new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", Registries.ITEM.getId(item).getPath()))));

        SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(
                new ItemStack(Registries.ITEM.get(Identifier.of("minecraft",Registries.ITEM.getId(item).getPath())), 1)
        );

        SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                Registries.ITEM.get(Identifier.of("minecraft", "crafting_table"))
        );


            System.out.println("Item: " + item);


            OptionalInt group = OptionalInt.empty();

            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = new ArrayList<>();
            List<Item> alternatives = new ArrayList<>();
            alternatives.add(item);
            ingredients.add(Ingredient.ofItems(alternatives.stream()));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry recipeDisplayEntry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(recipeDisplayEntry));
            list2.add(myCustomRecipeResultCollection);
        }
        System.out.println("2: list2 содержит " + list2.size() + " рецептов");
    }

}
