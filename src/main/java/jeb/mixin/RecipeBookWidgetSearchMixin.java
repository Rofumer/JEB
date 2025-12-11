package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.FavoritesManager;
import jeb.client.JEBClient;
import jeb.client.RecipeIndex;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.client.recipebook.RecipeBookType;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.book.RecipeBookGroup;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;
import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static jeb.client.JEBClient.*;

@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetSearchMixin<T extends AbstractRecipeScreenHandler> implements RecipeBookWidgetBridge {

    @Shadow protected AbstractRecipeScreenHandler craftingScreenHandler;

    // приватный метод vanilla
    @Shadow
    public abstract void refresh();

    @Override
    public void jeb$refresh() {
        this.refresh();
    }

    @Shadow @Final
    private ClientRecipeBook recipeBook;

    @Shadow @Final
    private RecipeFinder recipeFinder;

    @Shadow
    private RecipeGroupButtonWidget currentTab;

    @Shadow
    private MinecraftClient client;

    @Shadow
    private RecipeBookResults recipesArea;

    @Shadow
    private TextFieldWidget searchField;

    @Shadow
    protected CyclingButtonWidget<Boolean> toggleCraftableButton;

    @Shadow @Final
    private List<RecipeBookWidget.Tab> tabs;

    @Shadow @Final
    private List<RecipeGroupButtonWidget> tabButtons;

    @Unique
    private CyclingButtonWidget<Boolean> jeb$customToggleButton;

    @Unique
    private boolean jeb$customToggleState = false;

    @Unique
    private static final ButtonTextures TEXTURES_ALT = new ButtonTextures(
            Identifier.ofVanilla("recipe_book/crafting_overlay"),
            Identifier.ofVanilla("recipe_book/crafting_overlay_highlighted")
    );

    @Unique
    private static final ButtonTextures TEXTURES_DEFAULT = new ButtonTextures(
            Identifier.ofVanilla("recipe_book/crafting_overlay_disabled"),
            Identifier.ofVanilla("recipe_book/crafting_overlay_disabled_highlighted")
    );

    // ===== кастомная кнопка (CyclingButtonWidget) =====

    @Inject(method = "reset", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.toggleCraftableButton.getX();
        int y = this.toggleCraftableButton.getY() + 125;

        // true → включён наш 3x3-режим
        jeb$customToggleButton = CyclingButtonWidget
                .onOffBuilder(JEBClient.customToggleEnabled)
                .tooltip(value -> Tooltip.of(Text.of(value ? "Show 3x3" : "Show 2x2")))
                .icon((button, value) -> {
                    ButtonTextures textures = value ? TEXTURES_ALT : TEXTURES_DEFAULT;
                    return textures.get(true, button.isSelected());
                })
                .labelType(CyclingButtonWidget.LabelType.HIDE)
                .build(x, y, 20, 16, Text.of("!"), (button, value) -> {
                    jeb$customToggleState = value;
                    JEBClient.customToggleEnabled = value;
                    JEBClient.saveConfig();
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                });

        jeb$customToggleButton.visible = true;
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/CyclingButtonWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.render(context, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "populateAllRecipes", at = @At("HEAD"), cancellable = true)
    private void populateAllRecipes(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
    private void jeb$clickCustomToggle(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(click, doubled)) {
            // всё уже обработано в callback у builder
            cir.setReturnValue(true);
        }
    }

    // ===== Favorites / хоткей на рецепт =====

    @Unique
    private boolean isFavoritesTabActive() {
        return currentTab != null && currentTab.getCategory() == RecipeBookCategories.CAMPFIRE;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (JEBClient.keyBinding2.matchesKey(input)) {
            AnimatedResultButton hovered = ((RecipeBookResultsAccessor) recipesArea).getHoveredResultButton();
            if (hovered != null) {
                if (isFavoritesTabActive()) {
                    FavoritesManager.removeFavorite(hovered.getDisplayStack());
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                } else {
                    FavoritesManager.saveFavorite(hovered.getDisplayStack());
                }
                ((AnimatedResultButtonExtension) hovered).jeb$flash();
                cir.setReturnValue(true);
            }
        }
    }

    // хук после клика по рецепту
    @Inject(
            method = "select",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickRecipe(ILnet/minecraft/recipe/NetworkRecipeId;Z)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = false,
            require = 0
    )
    private void onRecipeClicked(
            RecipeResultCollection results, NetworkRecipeId recipeId, boolean craftAll, CallbackInfoReturnable<Boolean> cir
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ClientRecipeBook recipeBook = client.player.getRecipeBook();
        Map<NetworkRecipeId, RecipeDisplayEntry> recipes =
                ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipeId);
        Screen screen = client.currentScreen;

        if (screen instanceof RecipeBookProvider provider && entry != null) {
            if (!results.isCraftable(recipeId) && recipeId.index() != 9999) {
                provider.onCraftFailed(entry.display());
            }
        }
    }

    // ===== поиск по ингредиентам в entry.craftingRequirements() =====
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

    // ===== поиск по результату + мод + тултипы =====
    @Unique
    private boolean recipeResultMatchesQuery(RecipeDisplayEntry entry, String query, String modName) {
        if (entry.display() == null || entry.display().result() == null) return false;

        SlotDisplay resultSlot = entry.display().result();

        ContextParameterMap context = SlotDisplayContexts.createParameters(
                Objects.requireNonNull(this.client.world)
        );

        List<ItemStack> stacks = resultSlot.getStacks(context);
        if (stacks.isEmpty()) return false;

        ItemStack stack = stacks.get(0);
        if (stack == null || stack.isEmpty()) return false;

        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = stack.getItem().getTranslationKey().toLowerCase(Locale.ROOT);

        if (modName != null && !modName.isEmpty()
                && !Registries.ITEM.getId(stack.getItem()).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
        TooltipType tooltipType = TooltipType.Default.BASIC;

        try {
            List<Text> tooltip = stack.getTooltip(tooltipContext, client.player, tooltipType);
            for (Text line : tooltip) {
                String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                if (clean.contains(query)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private static RecipeDisplayEntry createDummySingleItemRecipe(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        NetworkRecipeId recipeId = new NetworkRecipeId(9999);

        List<SlotDisplay> slots = List.of(
                new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, id))
        );
        SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(stack.copy());
        SlotDisplay.ItemSlotDisplay stationSlot =
                new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", "crafting_table")));

        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
        List<Ingredient> ingredients = List.of(Ingredient.ofItems(stack.getItem()));

        return new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
    }

    // ===== основной перехват refreshResults (кастомный поиск) =====

    //@Unique
    //private String string = "";
    //@Unique
    //private List<RecipeResultCollection> filtered = new ArrayList<>();

    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String rawInput = searchField.getText();

        boolean searchIngredients = rawInput.startsWith("#");
        boolean searchByResult = rawInput.startsWith("~");
        String query = (searchIngredients || searchByResult ? rawInput.substring(1) : rawInput).toLowerCase();

        String modName = null;
        if (rawInput.startsWith("@")) {
            int endIndex = rawInput.indexOf(" ");
            if (endIndex != -1) {
                modName = rawInput.substring(1, endIndex).trim();
                query = rawInput.substring(endIndex + 1).toLowerCase();
            } else {
                modName = rawInput.substring(1).trim();
                query = "";
            }
        }

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> collections = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = new ArrayList<>();

        // ===== режим "~item_id" → показать рецепты ингредиентов результата =====
        if (rawInput.startsWith("~") && !isFavoritesTabActive()) {
            ContextParameterMap context = SlotDisplayContexts.createParameters(
                    Objects.requireNonNull(this.client.world)
            );
            List<RecipeResultCollection> ingredientsList = new ArrayList<>();

            for (RecipeResultCollection collection : recipeBook.getResultsForCategory(currentTab.getCategory())) {
                for (RecipeDisplayEntry recipe1 : collection.getAllRecipes()) {
                    SlotDisplay resultSlot = recipe1.display().result();
                    List<ItemStack> stacks = resultSlot.getStacks(context);
                    if (stacks.isEmpty()) continue;

                    ItemStack result = stacks.get(0);
                    String resultName = Registries.ITEM.getId(result.getItem()).getPath().toLowerCase(Locale.ROOT);

                    if (resultName.equals(query)) {
                        for (Ingredient ingredient : recipe1.craftingRequirements().get()) {
                            for (ItemStack stack : ingredient.toDisplay().getStacks(context)) {
                                if (!stack.isEmpty()) {
                                    boolean foundReal = false;
                                    for (RecipeResultCollection subCollection : recipeBook.getResultsForCategory(RecipeBookType.CRAFTING)) {
                                        for (RecipeDisplayEntry subRecipe : subCollection.getAllRecipes()) {

                                            resultSlot = subRecipe.display().result();
                                            stacks = resultSlot.getStacks(context);
                                            if (stacks.isEmpty()) continue;

                                            ItemStack subResult = stacks.get(0);
                                            if (!subResult.isEmpty() && ItemStack.areItemsEqual(subResult, stack)) {
                                                subCollection.populateRecipes(recipeFinder, recipe -> true);
                                                ingredientsList.add(subCollection);
                                                foundReal = true;
                                                break;
                                            }
                                        }
                                        if (foundReal) break;
                                    }

                                    if (!foundReal) {
                                        RecipeDisplayEntry fakeRecipe = createDummySingleItemRecipe(stack);
                                        RecipeResultCollection fakeCollection = new RecipeResultCollection(List.of(fakeRecipe));
                                        fakeCollection.populateRecipes(recipeFinder, recipe -> true);
                                        ingredientsList.add(fakeCollection);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            filteredList.addAll(ingredientsList);
            recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        // ===== вкладка избранного =====
        if (isFavoritesTabActive()) {
            Set<Identifier> favoriteItems = FavoritesManager.loadFavoriteItemIds();
            List<RecipeResultCollection> craftingCollections = recipeBook.getResultsForCategory(RecipeBookType.CRAFTING);

            for (RecipeResultCollection collection : craftingCollections) {
                boolean hasFavorite = collection.getAllRecipes().stream()
                        .flatMap(entry -> entry.getStacks(SlotDisplayContexts.createParameters(MinecraftClient.getInstance().world)).stream())
                        .map(stack -> Registries.ITEM.getId(stack.getItem()))
                        .anyMatch(favoriteItems::contains);

                if (hasFavorite) {
                    collection.populateRecipes(recipeFinder, recipe -> true);
                    filteredList.add(collection);
                }
            }

            recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        final String finalQuery = query;
        final String finalModName = modName;

        // ===== быстрый индексированный поиск =====
        filteredList = new ArrayList<>(RecipeIndex.fastSearch(currentTab.getCategory(), query, modName, searchIngredients));

        for (RecipeResultCollection col : filteredList) {
            if (JEBClient.customToggleEnabled) {
                col.populateRecipes(recipeFinder, recipe -> true);
            } else {
                col.populateRecipes(recipeFinder, this::canDisplay);
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }

        if (!Objects.equals(string, rawInput)) {
            filtered = RecipeIndex.generateCustomRecipeList(rawInput);
        }

        Screen screen = client.currentScreen;

        if (!toggleCraftableButton.getValue()) {
            filteredList.addAll(filtered);
        }

        string = rawInput;

        recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }

    @Unique
    private boolean canDisplay(RecipeDisplay display) {
        if (!(this.craftingScreenHandler instanceof AbstractCraftingScreenHandler craftingHandler)) {
            return true;
        }

        int i = craftingHandler.getWidth();
        int j = craftingHandler.getHeight();

        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return i >= shaped.width() && j >= shaped.height();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return i * j >= shapeless.ingredients().size();
        } else {
            return false;
        }
    }

    @Unique
    private static Optional<Item> getItemFromSlotDisplay(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.StackSlotDisplay stackSlot) {
            ItemStack stack = stackSlot.stack();
            return Optional.of(stack.getItem());
        }

        if (slot instanceof SlotDisplay.ItemSlotDisplay itemSlot) {
            RegistryEntry<Item> item = itemSlot.item();
            return Optional.of(item.value());
        }

        if (slot instanceof SlotDisplay.TagSlotDisplay tagSlot) {
            TagKey<Item> tag = tagSlot.tag();
            for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
                return Optional.of(entry.value());
            }
        }

        if (slot instanceof SlotDisplay.CompositeSlotDisplay composite) {
            List<SlotDisplay> contents = composite.contents();
            for (SlotDisplay inner : contents) {
                Optional<Item> maybeItem = getItemFromSlotDisplay(inner);
                if (maybeItem.isPresent()) return maybeItem;
            }
        }

        return Optional.empty();
    }
}
