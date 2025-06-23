package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.FavoritesManager;
import jeb.client.JEBClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ToggleButtonWidget;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.client.recipebook.RecipeBookType;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.book.RecipeBookGroup;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;
import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.recipebook.RecipeBookType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static jeb.client.JEBClient.*;

@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetSearchMixin<T extends AbstractRecipeScreenHandler> implements RecipeBookWidgetBridge {

    // Это будет вызов приватного метода
    @Shadow
    private void refresh() {}

    @Override
    public void jeb$refresh() {
        this.refresh();
    }

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


    @Shadow
    @Final
    private List<RecipeBookWidget.Tab> tabs;

    @Shadow
    @Final
    private List<RecipeGroupButtonWidget> tabButtons;

    @Shadow
    protected ToggleButtonWidget toggleCraftableButton;

    @Unique
    private ToggleButtonWidget jeb$customToggleButton;

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


    @Inject(method = "reset", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.toggleCraftableButton.getX();
        int y = this.toggleCraftableButton.getY()+125;

        jeb$customToggleButton = new ToggleButtonWidget(x, y, 20, 16, false);
        if(JEBClient.customToggleEnabled){
            jeb$customToggleButton.setTooltip(Tooltip.of(Text.of("Show 3x3")));
            jeb$customToggleButton.setTextures(TEXTURES_ALT);
        }
        else
        {
            jeb$customToggleButton.setTooltip(Tooltip.of(Text.of("Show 2x2")));
            jeb$customToggleButton.setTextures(TEXTURES_DEFAULT);
        }
        jeb$customToggleButton.setMessage(Text.of("!"));
        jeb$customToggleButton.visible = true;

    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/ToggleButtonWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    ordinal = 0, // если их несколько, выбирай нужный
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.render(context, mouseX, mouseY, delta);
        }
    }


    @Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
    private void jeb$clickCustomToggle(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(mouseX, mouseY, button)) {
            jeb$customToggleState = !jeb$customToggleState;
            jeb$customToggleButton.setToggled(jeb$customToggleState);
            JEBClient.customToggleEnabled = !JEBClient.customToggleEnabled;

            JEBClient.saveConfig();
            // Меняем текстуру в зависимости от состояния
            jeb$customToggleButton.setTextures(JEBClient.customToggleEnabled ? TEXTURES_ALT : TEXTURES_DEFAULT);

            jeb$customToggleButton.setTooltip(JEBClient.customToggleEnabled ? Tooltip.of(Text.of("Show 3x3")):Tooltip.of(Text.of("Show 2x2")));

            //System.out.println("Кастомная кнопка: " + (jeb$customToggleState ? "включена" : "выключена"));

            // Рефреш через reflection
            /*try {
                Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                e.printStackTrace();
            }*/

            ((RecipeBookWidgetBridge) this).jeb$refresh();

            cir.setReturnValue(true);
        }
    }


    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;clear()V",
                    shift = At.Shift.AFTER
            )
    )
    private void injectCustomTab(CallbackInfo ci) {


        // Создаём кнопку вкладки
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        tabButton.setMessage(Text.of("Favorites"));


        this.tabButtons.add(tabButton);

    }*/

    /*@Inject(method = "<init>", at = @At("RETURN"))
    private void injectAfterConstructor(T craftingScreenHandler, List<RecipeBookWidget.Tab> tabs, CallbackInfo ci) {

        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        //RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        //tabButton.setMessage(Text.of("Favorites"));
        this.tabs.add(newTab);

    }*/

    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeGroupButtonWidget;setToggled(Z)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void jeb$replaceFavoritesAsDefaultTab(CallbackInfo ci) {
        if (this.currentTab == tabButtons.get(0) && tabButtons.size() > 1) {
            RecipeGroupButtonWidget maybeFavorites = tabButtons.get(0);
            //if ("Favorites".equals(maybeFavorites.getMessage().getString())) {
                // Сбросить подсветку со старой
                //maybeFavorites.setToggled(true);
                maybeFavorites.setToggled(true);

            //this.refreshTabButtons(bl);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);

                // Назначить новую
                this.currentTab = tabButtons.get(1);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);
            //}
        }
    }*/

    /*@Unique
    private boolean isFavoritesTabActive() {
        if (currentTab == null) return false;

        return tabButtons.stream()
                .filter(button -> button.isSelected())
                .anyMatch(button -> "Favorites".equals(button.getMessage().getString()));
    }*/

    @Unique
    private boolean isFavoritesTabActive() {
        //return currentTab != null
        //        && currentTab.getMessage() != null
        //        && "Favorites".equals(currentTab.getMessage().getString());
        return currentTab.getCategory() == RecipeBookCategories.CAMPFIRE;
    }


    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Проверка на нужную клавишу (например, клавиша G, keyCode = 71)
        //if (keyCode == GLFW.GLFW_KEY_A) {
        if (JEBClient.keyBinding2.matchesKey(keyCode, scanCode)) {
            AnimatedResultButton hovered = ((RecipeBookResultsAccessor) recipesArea).getHoveredResultButton();
            if (hovered != null) {
                //System.out.println("Над кнопкой: " + hovered.getDisplayStack().getItem().toString());
                //ItemStack stack = hovered.getDisplayStack();
                if (isFavoritesTabActive()) {
                    FavoritesManager.removeFavorite(hovered.getDisplayStack());
                    // Рефреш через reflection
                    /*try {
                        Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                        method.setAccessible(true);
                        method.invoke(this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                } else {
                    FavoritesManager.saveFavorite(hovered.getDisplayStack());
                }
                //FavoritesManager.saveFavorite(stack);
                ((AnimatedResultButtonExtension) hovered).jeb$flash();
                // Здесь можно выполнить любое действие, например, выбрать рецепт, показать информацию и т.д.
                cir.setReturnValue(true);
            }
        }
    }

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
            //System.out.println("РецептL " + entry.display().toString());
            if(!results.isCraftable(recipeId) && recipeId.index()!=9999) {
                provider.onCraftFailed(entry.display());
            }
        }
    }


    /*@Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
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
    }*/

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

    /****@Unique
    private boolean recipeResultMatchesQuery(RecipeDisplayEntry entry, String query) {
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

        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по тултипам
        RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
        TooltipType tooltipType = TooltipType.Default.BASIC;

        List<Text> tooltip = stack.getTooltip(tooltipContext, client.player, tooltipType);
        for (Text line : tooltip) {
            String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
            if (clean.contains(query)) return true;
        }

        return false;
    }



    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        //if (string.isEmpty()) return;

        boolean searchIngredients = string.startsWith("#");
        String query = (searchIngredients ? string.substring(1) : string).toLowerCase();

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                boolean match =
                        recipeResultMatchesQuery(entry, query) ||
                                (searchIngredients && recipeDisplayMatchesIngredientQuery(entry, query));

                if (match) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }****/

        //System.out.println("filteredList содержит " + filteredList.size() + " рецептов");

        // Получаем доступ к searchField через наш accessor

        // Получаем текст из поля поиска

        // 🔹 Собираем все предметы, уже встречающиеся в filteredList как результат
        /*Set<Item> existingResultItems = new HashSet<>();
        for (RecipeResultCollection collection : filteredList) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                getItemFromSlotDisplay(entry.display().result()).ifPresent(existingResultItems::add);
            }
        }*/


        /// ////////
        /*for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            //if (existingResultItems.contains(item)) continue;

            if (!translate(item.getTranslationKey()).toLowerCase().contains(string.toLowerCase())) continue;

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

            filteredList.add(myCustomRecipeResultCollection);
        }*/

        /****filteredList.addAll(JEBClient.generateCustomRecipeList(string));****/

        //if (!string.isEmpty()) {
            /*for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                if (existingResultItems.contains(item)) continue;

                Identifier id = Registries.ITEM.getId(item);
                String idString = id.toString().toLowerCase(); // без Locale
                String name = item.getName().getString().toLowerCase(); // без Locale
                String searchLower = string.toLowerCase(); // без Locale

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

                filteredList.add(resultCollection);
            }*/
        //}



        //System.out.println("2: filteredList содержит " + filteredList.size() + " рецептов");
        //System.out.println("Текст в поисковом поле: " + string);
        
    /****    recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }****/

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

        // Проверка на имя мода
        if (modName != null && !modName.isEmpty() && !Registries.ITEM.getId(stack.getItem()).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
            return false;  // Не принадлежит указанному моду
        }

        // Обычный поиск по строкам
        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по тултипам
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
            // Можно также записать лог или безопасно проигнорировать ошибку
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


    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String rawInput = searchField.getText();

        if (rawInput != null && rawInput.trim().isEmpty() && emptysearch != null && !emptysearch.isEmpty())
        {
            recipesArea.setResults(emptysearch, resetCurrentPage, filteringCraftable);
            ci.cancel();
        }

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

        if (rawInput.startsWith("~") && !isFavoritesTabActive()) {
            ContextParameterMap context = SlotDisplayContexts.createParameters(
                    Objects.requireNonNull(this.client.world)
            );
            List<RecipeResultCollection> ingredientsList = new ArrayList<>();
            // query уже без ~, приведён к lowerCase

            // Проходим по всем коллекциям рецептов из выбранной вкладки
            for (RecipeResultCollection collection : recipeBook.getResultsForCategory(currentTab.getCategory())) {
                for (RecipeDisplayEntry recipe : collection.getAllRecipes()) {
                    // Новый способ получения результата рецепта в 1.21.5:
                    SlotDisplay resultSlot = recipe.display().result();


                    List<ItemStack> stacks = resultSlot.getStacks(context);
                    ItemStack result = stacks.get(0);
                    //String resultName = result.getItem().toString().toLowerCase(Locale.ROOT);
                    String resultName = Registries.ITEM.getId(result.getItem()).getPath().toLowerCase(Locale.ROOT);

                    if (resultName.equals(query)) {
                        for (Ingredient ingredient : recipe.craftingRequirements().get()) {
                            // getItems() — возвращает ItemStack[]
                            for (ItemStack stack : ingredient.toDisplay().getStacks(context)) {
                                if (!stack.isEmpty()) {
                                    // Проверяем: есть ли коллекция рецептов, где результат — этот ингредиент?
                                    boolean foundReal = false;
                                    for (RecipeResultCollection subCollection : recipeBook.getResultsForCategory(RecipeBookType.CRAFTING)) {
                                        for (RecipeDisplayEntry subRecipe : subCollection.getAllRecipes()) {

                                            resultSlot = subRecipe.display().result();


                                            stacks = resultSlot.getStacks(context);
                                            ItemStack subResult = stacks.get(0);

                                            //ItemStack subResult = subRecipe.getResultItem(client.world != null ? client.world.registryAccess() : null);
                                            if (!subResult.isEmpty() && ItemStack.areItemsEqual(subResult, stack)) {
                                                ingredientsList.add(subCollection);
                                                foundReal = true;
                                                break;
                                            }
                                        }
                                        if (foundReal) break;
                                    }
                                    // Если не нашли реального рецепта — добавляем фейковую коллекцию
                                    if (!foundReal) {
                                        RecipeDisplayEntry fakeRecipe = createDummySingleItemRecipe(stack);
                                        ingredientsList.add(new RecipeResultCollection(List.of(fakeRecipe)));
                                    }
                                    break; // только один stack из одного ingredient
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


        // ===== Favorites Tab =====
        if (isFavoritesTabActive()) {
            Set<Identifier> favoriteItems = FavoritesManager.loadFavoriteItemIds();
            List<RecipeResultCollection> craftingCollections = recipeBook.getResultsForCategory(RecipeBookType.CRAFTING);

            for (RecipeResultCollection collection : craftingCollections) {
                // Быстрая проверка: есть ли в коллекции хоть один рецепт из избранных?
                boolean hasFavorite = collection.getAllRecipes().stream()
                        .flatMap(entry -> entry.getStacks(SlotDisplayContexts.createParameters(MinecraftClient.getInstance().world)).stream())
                        .map(stack -> Registries.ITEM.getId(stack.getItem()))
                        .anyMatch(favoriteItems::contains);

                if (hasFavorite) {
                    filteredList.add(collection);
                }
            }

            recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        final String finalQuery = query;
        final String finalModName = modName;


        // ===== Обычный поиск =====
        for (RecipeResultCollection collection : collections) {
            if (!collection.hasDisplayableRecipes()) continue;

            boolean found = collection.getAllRecipes().stream().anyMatch(entry ->
                    searchIngredients
                            ? recipeDisplayMatchesIngredientQuery(entry, finalQuery)
                            : recipeResultMatchesQuery(entry, finalQuery, finalModName)
            );

            if (found) {
                filteredList.add(collection);
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }

        //filteredList.addAll(JEBClient.generateCustomRecipeList(rawInput));
        if(!Objects.equals(string,rawInput))
        {
            filtered = JEBClient.generateCustomRecipeList(rawInput);
        }

        Screen screen = client.currentScreen;

        //if (screen instanceof CraftingScreen || screen instanceof InventoryScreen provider)
        //{
            if (!toggleCraftableButton.isToggled()) {
                filteredList.addAll(filtered);
            }
        //}

        //if(!(((RecipeBookWidgetAccessor) this).getSearchField().isActive() && ((RecipeBookWidgetAccessor) this).getSearchField().isVisible() && ((RecipeBookWidgetAccessor) this).getSearchField().isFocused())) {
        //    filteredList.forEach(result ->
        //            result.computeCraftables(recipeFinder,
        //                    craftingScreenHandler.getCraftingWidth(),
        //                    craftingScreenHandler.getCraftingHeight(),
        //                    recipeBook)
        //    );
        //}

        if (rawInput != null && rawInput.trim().isEmpty() && emptysearch.isEmpty())
        {
            emptysearch = filteredList;
        }

        string=rawInput;

        recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
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

    /*@Inject(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", shift = At.Shift.AFTER))
    private void addNewTab(CallbackInfo ci) {
        RecipeBookWidget<?> recipeBookWidget = (RecipeBookWidget<?>) (Object) this;

        // Получаем список вкладок через @Accessor
        List<RecipeBookWidget.Tab> tabs = ((RecipeBookWidgetAccessor) recipeBookWidget).gettabs();

        // Создаем изменяемую копию списка tabs
        List<RecipeBookWidget.Tab> newTabs = new ArrayList<>(tabs);

        // Создаем новую вкладку (Tab) с иконкой и категорией
        ItemStack primaryIcon = new ItemStack(Items.WRITABLE_BOOK);  // Иконка из алмаза
        RecipeBookCategory category = RecipeBookCategories.CAMPFIRE; // Категория рецептов
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(primaryIcon.getItem(), category);

        // Добавляем новую кнопку вкладки в список tabButtons
        newTabs.add(newTab);  // Добавляем новую кнопку вкладки

        try {
            java.lang.reflect.Field tabsField = RecipeBookWidget.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);  // Даем доступ к приватному полю
            tabsField.set(recipeBookWidget, newTabs);  // Устанавливаем новое значение
        } catch (Exception e) {
            e.printStackTrace();
        }

    }*/

}
