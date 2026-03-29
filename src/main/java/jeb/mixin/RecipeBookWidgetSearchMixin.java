package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.FavoritesManager;
import jeb.client.JEBClient;
import jeb.client.RecipeIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
//import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import com.google.common.collect.Lists;
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

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetSearchMixin<T extends RecipeBookMenu> implements RecipeBookWidgetBridge {

    @Shadow protected RecipeBookMenu menu;

    // приватный метод vanilla
    @Shadow
    public abstract void recipesUpdated();

    @Override
    public void jeb$refresh() {
        this.recipesUpdated();
    }

    @Shadow @Final
    private ClientRecipeBook book;

    @Shadow @Final
    private StackedItemContents stackedContents;

    @Shadow
    private RecipeBookTabButton selectedTab;

    @Shadow
    private Minecraft minecraft;

    @Shadow
    private RecipeBookPage recipeBookPage;

    @Shadow
    private EditBox searchBox;

    @Shadow
    protected CycleButton<Boolean> filterButton;

    @Shadow @Final
    private List<RecipeBookComponent.TabInfo> tabInfos;

    @Shadow @Final
    private List<RecipeBookTabButton> tabButtons;

    @Unique
    private CycleButton<Boolean> jeb$customToggleButton;

    @Unique
    private boolean jeb$customToggleState = false;

    @Unique
    private static final WidgetSprites TEXTURES_ALT = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay"),
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay_highlighted")
    );

    @Unique
    private static final WidgetSprites TEXTURES_DEFAULT = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay_disabled"),
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay_disabled_highlighted")
    );

    // ===== кастомная кнопка (CyclingButtonWidget) =====

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.filterButton.getX();
        int y = this.filterButton.getY() + 125;

        // true → включён наш 3x3-режим
        jeb$customToggleButton = CycleButton
                .onOffBuilder(JEBClient.customToggleEnabled)
                .withTooltip(value -> Tooltip.create(Component.nullToEmpty(value ? "Show 3x3" : "Show 2x2")))
                .withSprite((button, value) -> {
                    WidgetSprites textures = value ? TEXTURES_ALT : TEXTURES_DEFAULT;
                    return textures.get(true, button.isHoveredOrFocused());
                })
                .displayState(CycleButton.DisplayState.HIDE)
                .create(x, y, 20, 16, Component.nullToEmpty("!"), (button, value) -> {
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
                    target = "Lnet/minecraft/client/gui/components/CycleButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.render(context, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "selectMatchingRecipes()V", at = @At("HEAD"), cancellable = true)
    private void populateAllRecipes(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
    private void jeb$clickCustomToggle(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(click, doubled)) {
            // всё уже обработано в callback у builder
            cir.setReturnValue(true);
        }
    }

    // ===== Favorites / хоткей на рецепт =====

    @Unique
    private boolean isFavoritesTabActive() {
        return selectedTab != null && selectedTab.getCategory() == RecipeBookCategories.CAMPFIRE;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (JEBClient.keyBinding2.matches(input)) {
            RecipeButton hovered = ((RecipeBookResultsAccessor) recipeBookPage).getHoveredResultButton();
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
            method = "tryPlaceRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = false,
            require = 0
    )
    private void onRecipeClicked(
            RecipeCollection results, RecipeDisplayId recipeId, boolean craftAll, CallbackInfoReturnable<Boolean> cir
    ) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        ClientRecipeBook recipeBook = client.player.getRecipeBook();
        Map<RecipeDisplayId, RecipeDisplayEntry> recipes =
                ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipeId);
        Screen screen = client.screen;

        if (screen instanceof RecipeUpdateListener provider && entry != null) {
            if (!results.isCraftable(recipeId) && recipeId.index() != 9999) {
                provider.fillGhostRecipe(entry.display());
            }
        }
    }

    // ===== поиск по ингредиентам в entry.craftingRequirements() =====
    @Unique
    private boolean recipeDisplayMatchesIngredientQuery(RecipeDisplayEntry entry, String query) {
        if (entry.craftingRequirements().isEmpty()) return false;

        return entry.craftingRequirements().get().stream().anyMatch(ingredient ->
                ingredient.items().anyMatch(regEntry -> {
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

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(this.minecraft.level)
        );

        List<ItemStack> stacks = resultSlot.resolveForStacks(context);
        if (stacks.isEmpty()) return false;

        ItemStack stack = stacks.get(0);
        if (stack == null || stack.isEmpty()) return false;

        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = stack.getItem().getDescriptionId().toLowerCase(Locale.ROOT);

        if (modName != null && !modName.isEmpty()
                && !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        HolderLookup.Provider lookup = minecraft.level.registryAccess();
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(lookup);
        TooltipFlag tooltipType = TooltipFlag.Default.NORMAL;

        try {
            List<Component> tooltip = stack.getTooltipLines(tooltipContext, minecraft.player, tooltipType);
            for (Component line : tooltip) {
                String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                if (clean.contains(query)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private static RecipeDisplayEntry createDummySingleItemRecipe(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        RecipeDisplayId recipeId = new RecipeDisplayId(9999);

        List<SlotDisplay> slots = List.of(
                new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, id))
        );
        SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(stack.copy());
        SlotDisplay.ItemSlotDisplay stationSlot =
                new SlotDisplay.ItemSlotDisplay(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "crafting_table")));

        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
        List<Ingredient> ingredients = List.of(Ingredient.of(stack.getItem()));

        return new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
    }

    // ===== основной перехват refreshResults (кастомный поиск) =====

    //@Unique
    //private String string = "";
    //@Unique
    //private List<RecipeResultCollection> filtered = new ArrayList<>();

    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String rawInput = searchBox.getValue();

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

        ClientPacketListener handler = minecraft.getConnection();
        if (handler == null) return;

        List<RecipeCollection> collections = book.getCollection(selectedTab.getCategory());
        List<RecipeCollection> filteredList = new ArrayList<>();

        // ===== режим "~item_id" → показать рецепты ингредиентов результата =====
        if (rawInput.startsWith("~") && !isFavoritesTabActive()) {
            ContextMap context = SlotDisplayContext.fromLevel(
                    Objects.requireNonNull(this.minecraft.level)
            );
            List<RecipeCollection> ingredientsList = new ArrayList<>();

            for (RecipeCollection collection : book.getCollection(selectedTab.getCategory())) {
                for (RecipeDisplayEntry recipe1 : collection.getRecipes()) {
                    SlotDisplay resultSlot = recipe1.display().result();
                    List<ItemStack> stacks = resultSlot.resolveForStacks(context);
                    if (stacks.isEmpty()) continue;

                    ItemStack result = stacks.get(0);
                    String resultName = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().toLowerCase(Locale.ROOT);

                    if (resultName.equals(query)) {
                        for (Ingredient ingredient : recipe1.craftingRequirements().get()) {
                            for (ItemStack stack : ingredient.display().resolveForStacks(context)) {
                                if (!stack.isEmpty()) {
                                    boolean foundReal = false;
                                    for (RecipeCollection subCollection : book.getCollection(SearchRecipeBookCategory.CRAFTING)) {
                                        for (RecipeDisplayEntry subRecipe : subCollection.getRecipes()) {

                                            resultSlot = subRecipe.display().result();
                                            stacks = resultSlot.resolveForStacks(context);
                                            if (stacks.isEmpty()) continue;

                                            ItemStack subResult = stacks.get(0);
                                            if (!subResult.isEmpty() && ItemStack.isSameItem(subResult, stack)) {
                                                subCollection.selectRecipes(stackedContents, recipe -> true);
                                                ingredientsList.add(subCollection);
                                                foundReal = true;
                                                break;
                                            }
                                        }
                                        if (foundReal) break;
                                    }

                                    if (!foundReal) {
                                        RecipeDisplayEntry fakeRecipe = createDummySingleItemRecipe(stack);
                                        RecipeCollection fakeCollection = new RecipeCollection(List.of(fakeRecipe));
                                        fakeCollection.selectRecipes(stackedContents, recipe -> true);
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
            recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        // ===== вкладка избранного =====
        if (isFavoritesTabActive()) {
            Set<Identifier> favoriteItems = FavoritesManager.loadFavoriteItemIds();
            List<RecipeCollection> craftingCollections = book.getCollection(SearchRecipeBookCategory.CRAFTING);

            for (RecipeCollection collection : craftingCollections) {
                boolean hasFavorite = collection.getRecipes().stream()
                        .flatMap(entry -> entry.resultItems(SlotDisplayContext.fromLevel(Minecraft.getInstance().level)).stream())
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .anyMatch(favoriteItems::contains);

                if (hasFavorite) {
                    collection.selectRecipes(stackedContents, recipe -> true);
                    filteredList.add(collection);
                }
            }

            recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        final String finalQuery = query;
        final String finalModName = modName;

        // ===== быстрый индексированный поиск =====
        filteredList = new ArrayList<>(RecipeIndex.fastSearch(selectedTab.getCategory(), query, modName, searchIngredients));

        for (RecipeCollection col : filteredList) {
            if (JEBClient.customToggleEnabled) {
                col.selectRecipes(stackedContents, recipe -> true);
            } else {
                col.selectRecipes(stackedContents, this::canDisplay);
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftable());
        }

        if (!Objects.equals(string, rawInput)) {
            filtered = RecipeIndex.generateCustomRecipeList(rawInput);
        }

        Screen screen = minecraft.screen;

        if (!filterButton.getValue()) {
            filteredList.addAll(filtered);
        }

        string = rawInput;

        recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }

    @Unique
    private boolean canDisplay(RecipeDisplay display) {
        if (!(this.menu instanceof AbstractCraftingMenu craftingHandler)) {
            return true;
        }

        int i = craftingHandler.getGridWidth();
        int j = craftingHandler.getGridHeight();

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
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackSlot) {
            ItemStack stack = stackSlot.stack();
            return Optional.of(stack.getItem());
        }

        if (slot instanceof SlotDisplay.ItemSlotDisplay itemSlot) {
            Holder<Item> item = itemSlot.item();
            return Optional.of(item.value());
        }

        if (slot instanceof SlotDisplay.TagSlotDisplay tagSlot) {
            TagKey<Item> tag = tagSlot.tag();
            for (Holder<Item> entry : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                return Optional.of(entry.value());
            }
        }

        if (slot instanceof SlotDisplay.Composite composite) {
            List<SlotDisplay> contents = composite.contents();
            for (SlotDisplay inner : contents) {
                Optional<Item> maybeItem = getItemFromSlotDisplay(inner);
                if (maybeItem.isPresent()) return maybeItem;
            }
        }

        return Optional.empty();
    }
}
