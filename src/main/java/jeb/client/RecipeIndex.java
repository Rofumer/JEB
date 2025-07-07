package jeb.client;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.recipebook.RecipeBookType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.book.RecipeBookGroup;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;

import java.lang.reflect.Field;
import java.util.*;

import static jeb.client.JEBClient.nonexistingResultItems;
import static net.minecraft.client.resource.language.I18n.translate;

public class RecipeIndex {
    // Индексы по категориям
    /*public final Map<RecipeBookCategory, Map<String, List<RecipeResultCollection>>> byResult = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeResultCollection>>> byMod = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeResultCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookCategory, Set<RecipeResultCollection>> allCollections = new HashMap<>();

    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();
    public static boolean jebIndexReady = false;

    // === Индексация ===
    public static void buildRecipeIndex() {
        long startTime = System.currentTimeMillis();
        System.out.println("[JEB] buildRecipeIndex started at " + new java.util.Date(startTime));

        jebIndexReady = false;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientRecipeBook book = client.player.getRecipeBook();

        GLOBAL_RECIPE_INDEX.byResult.clear();
        GLOBAL_RECIPE_INDEX.byMod.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
        GLOBAL_RECIPE_INDEX.allCollections.clear();

        ContextParameterMap context = SlotDisplayContexts.createParameters(Objects.requireNonNull(client.world));

        List<RecipeBookCategory> allCategories = new ArrayList<>();
        for (Field field : RecipeBookCategories.class.getFields()) {
            if (field.getType() == RecipeBookCategory.class) {
                try {
                    RecipeBookCategory category = (RecipeBookCategory) field.get(null);
                    allCategories.add(category);
                } catch (Exception ignored) {}
            }
        }

        int totalIndexedRecipes = 0;

        for (RecipeBookCategory category : allCategories) {
            List<RecipeResultCollection> collections = book.getResultsForCategory(category);
            if (collections.isEmpty()) continue;

            Set<RecipeResultCollection> categoryCollections = new LinkedHashSet<>(collections);
            GLOBAL_RECIPE_INDEX.allCollections.put(category, categoryCollections);

            Map<String, List<RecipeResultCollection>> resultIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> modIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> ingredientIndex = new HashMap<>();

            for (RecipeResultCollection collection : collections) {
                for (RecipeDisplayEntry recipe : collection.getAllRecipes()) {
                    totalIndexedRecipes++; // Считаем количество проиндексированных рецептов

                    ItemStack result = recipe.display().result().getFirst(context);
                    if (result == null || result.isEmpty()) continue;

                    // Индексация по ингредиентам
                    Optional<List<Ingredient>> opt = recipe.craftingRequirements();
                    if (opt.isPresent()) {
                        for (Ingredient ingredient : opt.get()) {
                            for (ItemStack stack : ingredient.toDisplay().getStacks(context)) {
                                String ingredientId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                                ingredientIndex.computeIfAbsent(ingredientId, k -> new ArrayList<>()).add(collection);
                            }
                        }
                    }

                    // Индексация по namespace (моду)
                    String mod = Registries.ITEM.getId(result.getItem()).getNamespace().toLowerCase(Locale.ROOT);
                    for (int i = 0; i < mod.length(); i++) {
                        for (int j = i + 1; j <= mod.length(); j++) {
                            String substr = mod.substring(i, j);
                            if (substr.isEmpty()) continue;
                            modIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                        }
                    }

                    // Индекс по подстрокам результата (id и имя)
                    String resultId = Registries.ITEM.getId(result.getItem()).toString().toLowerCase(Locale.ROOT);
                    String name = result.getItemName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
                    for (String source : List.of(resultId, name)) {
                        for (int i = 0; i < source.length(); i++) {
                            for (int j = i + 1; j <= source.length(); j++) {
                                String substr = source.substring(i, j);
                                if (substr.isEmpty()) continue;
                                resultIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                            }
                        }
                    }
                }
            }

            GLOBAL_RECIPE_INDEX.byResult.put(category, resultIndex);
            GLOBAL_RECIPE_INDEX.byMod.put(category, modIndex);
            GLOBAL_RECIPE_INDEX.byIngredientWord.put(category, ingredientIndex);
        }

        jebIndexReady = true;

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("[JEB] buildRecipeIndex done at " + new java.util.Date(endTime)
                + " (" + duration + " ms), total indexed recipes: " + totalIndexedRecipes);
    }*/

    // Индексы по категориям
    public final Map<RecipeBookCategory, Map<String, List<RecipeResultCollection>>> byResult = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeResultCollection>>> byMod = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeResultCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeResultCollection>>> byTooltipWord = new HashMap<>();
    public final Map<RecipeBookCategory, Set<RecipeResultCollection>> allCollections = new HashMap<>();
    public static final Map<RecipeBookCategory, Map<Item, RecipeResultCollection>> GLOBAL_COLLECTIONS_BY_RESULT = new HashMap<>();

    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();
    public static boolean jebIndexReady = false;

    // === Индексация ===
    public static void buildRecipeIndex() {
        long startTime = System.currentTimeMillis();
        JEBClient.LOGGER.info("[JEB] buildRecipeIndex started at {}", new Date(startTime));

        jebIndexReady = false;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientRecipeBook book = client.player.getRecipeBook();

        GLOBAL_RECIPE_INDEX.byResult.clear();
        GLOBAL_RECIPE_INDEX.byMod.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
        GLOBAL_RECIPE_INDEX.byTooltipWord.clear();
        GLOBAL_RECIPE_INDEX.allCollections.clear();
        GLOBAL_COLLECTIONS_BY_RESULT.clear();

        ContextParameterMap context = SlotDisplayContexts.createParameters(Objects.requireNonNull(client.world));

        List<RecipeBookCategory> allCategories = new ArrayList<>();
        for (Field field : RecipeBookCategories.class.getFields()) {
            if (field.getType() == RecipeBookCategory.class) {
                try {
                    RecipeBookCategory category = (RecipeBookCategory) field.get(null);
                    allCategories.add(category);
                } catch (Exception ignored) {}
            }
        }

        int totalIndexedRecipes = 0;

        for (RecipeBookCategory category : allCategories) {
            List<RecipeResultCollection> collections = book.getResultsForCategory(category);
            if (collections.isEmpty()) continue;

            Map<Item, RecipeResultCollection> collectionsByResult = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
            Set<RecipeResultCollection> categoryCollections = new LinkedHashSet<>();

            Map<String, List<RecipeResultCollection>> resultIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> modIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> ingredientIndex = new HashMap<>();
            Map<String, List<RecipeResultCollection>> tooltipIndex = new HashMap<>();

            for (RecipeResultCollection collection : collections) {
                List<RecipeDisplayEntry> entries = collection.getAllRecipes();
                for (RecipeDisplayEntry recipe : entries) {
                    totalIndexedRecipes++;

                    ItemStack result = recipe.display().result().getFirst(context);
                    if (result == null || result.isEmpty()) continue;
                    Item resultItem = result.getItem();

                    // Коллекция по результату
                    RecipeResultCollection realCollection = collectionsByResult.get(resultItem);
                    if (realCollection == null) {
                        realCollection = new RecipeResultCollection(new ArrayList<>());
                        collectionsByResult.put(resultItem, realCollection);
                        categoryCollections.add(realCollection);
                    }
                    if (!realCollection.getAllRecipes().contains(recipe)) {
                        realCollection.getAllRecipes().add(recipe);
                    }

                    // Индексация по ингредиентам
                    Optional<List<Ingredient>> opt = recipe.craftingRequirements();
                    if (opt.isPresent()) {
                        for (Ingredient ingredient : opt.get()) {
                            for (ItemStack stack : ingredient.toDisplay().getStacks(context)) {
                                String ingredientId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                                ingredientIndex.computeIfAbsent(ingredientId, k -> new ArrayList<>()).add(realCollection);
                            }
                        }
                    }

                    // Индексация по namespace (моду)
                    String mod = Registries.ITEM.getId(resultItem).getNamespace().toLowerCase(Locale.ROOT);
                    for (int i = 0; i < mod.length(); i++) {
                        for (int j = i + 1; j <= mod.length(); j++) {
                            String substr = mod.substring(i, j);
                            if (substr.isEmpty()) continue;
                            modIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(realCollection);
                        }
                    }

                    // Индекс по подстрокам результата (id и имя)
                    String resultId = Registries.ITEM.getId(resultItem).toString().toLowerCase(Locale.ROOT);
                    String name = result.getItemName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
                    for (String source : List.of(resultId, name)) {
                        for (int i = 0; i < source.length(); i++) {
                            for (int j = i + 1; j <= source.length(); j++) {
                                String substr = source.substring(i, j);
                                if (substr.isEmpty()) continue;
                                resultIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(realCollection);
                            }
                        }
                    }

                    // --- Индексация по подстрокам тултипов ---
                    List<String> tooltipLines = new ArrayList<>();
                    try {
                        RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
                        Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
                        TooltipType tooltipType = TooltipType.Default.BASIC;
                        List<Text> tooltip = result.getTooltip(tooltipContext, client.player, tooltipType);
                        for (Text line : tooltip) {
                            String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                            tooltipLines.add(clean);
                        }
                    } catch (Exception ignored) {}

                    for (String tooltipLine : tooltipLines) {
                        String[] words = tooltipLine.split("[\\s,;.:!\\-]+");
                        for (String word : words) {
                            if (word.length() < 3) continue;
                            for (int i = 0; i <= word.length() - 3; i++) {
                                for (int j = i + 3; j <= word.length(); j++) {
                                    String substr = word.substring(i, j);
                                    if (substr.isEmpty()) continue;
                                    tooltipIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(realCollection);
                                }
                            }
                        }
                    }
                }
            }

            GLOBAL_RECIPE_INDEX.allCollections.put(category, categoryCollections);
            GLOBAL_RECIPE_INDEX.byResult.put(category, resultIndex);
            GLOBAL_RECIPE_INDEX.byMod.put(category, modIndex);
            GLOBAL_RECIPE_INDEX.byIngredientWord.put(category, ingredientIndex);
            GLOBAL_RECIPE_INDEX.byTooltipWord.put(category, tooltipIndex);
        }

        jebIndexReady = true;

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        JEBClient.LOGGER.info("[JEB] buildRecipeIndex done at {} ({} ms), total indexed recipes: {}", new Date(endTime), duration, totalIndexedRecipes);
    }


    // === Универсальный быстрый поиск по списку категорий ===
    public static List<RecipeResultCollection> fastSearch(
            List<RecipeBookCategory> categories,
            String query,
            String modName,
            boolean searchIngredients
    ) {
        if (!jebIndexReady) return List.of();

        query = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        modName = modName == null ? "" : modName.toLowerCase(Locale.ROOT).trim();

        Set<RecipeResultCollection> result = new LinkedHashSet<>();

        for (RecipeBookCategory category : categories) {
            Map<String, List<RecipeResultCollection>> modIndex = GLOBAL_RECIPE_INDEX.byMod.getOrDefault(category, Map.of());
            Map<String, List<RecipeResultCollection>> ingredientIndex = GLOBAL_RECIPE_INDEX.byIngredientWord.getOrDefault(category, Map.of());
            Set<RecipeResultCollection> all = GLOBAL_RECIPE_INDEX.allCollections.getOrDefault(category, Set.of());
            Map<String, List<RecipeResultCollection>> resultIndex = GLOBAL_RECIPE_INDEX.byResult.getOrDefault(category, Map.of());
            // <<< ДОБАВЬ ЭТУ СТРОКУ ДЛЯ ТУЛТИПОВ >>>
            Map<String, List<RecipeResultCollection>> tooltipIndex = GLOBAL_RECIPE_INDEX.byTooltipWord.getOrDefault(category, Map.of());

            // Если нет фильтра по модулю и пустой запрос — вернуть все коллекции!
            if ((modName.isEmpty()) && query.isEmpty()) {
                result.addAll(all);
                continue;
            }

            // Поиск по модулю (namespace)
            if (!modName.isEmpty()) {
                List<RecipeResultCollection> modCollections = modIndex.getOrDefault(modName, List.of());
                if (query.isEmpty()) {
                    result.addAll(modCollections);
                    continue;
                }
                // Ищем среди коллекций по моду по словам
                for (String word : query.split("[\\s:_\\-]+")) {
                    List<RecipeResultCollection> byWord = resultIndex.getOrDefault(word, List.of());
                    for (RecipeResultCollection rc : byWord) {
                        if (modCollections.contains(rc))
                            result.add(rc);
                    }
                }
                continue;
            }

            // Поиск по ингредиенту
            if (searchIngredients && !query.isEmpty()) {
                List<RecipeResultCollection> byIng = ingredientIndex.getOrDefault(query, List.of());
                result.addAll(byIng);
                continue;
            }

            // Поиск по результату (id или имя)
            if (!query.isEmpty() && !searchIngredients) {
                List<RecipeResultCollection> byResult = resultIndex.getOrDefault(query, List.of());
                result.addAll(byResult);

                // <<< ДОБАВЬ ПОИСК ПО ТУЛТИПАМ >>>
                List<RecipeResultCollection> byTooltip = tooltipIndex.getOrDefault(query, List.of());
                result.addAll(byTooltip);
            }
        }

        return new ArrayList<>(result);
    }


    // Универсальная обёртка: принимает или одиночную категорию, или Type с его списком категорий
    public static List<RecipeResultCollection> fastSearch(
            Object categoryOrType, String query, String modName, boolean searchIngredients
    ) {
        List<RecipeBookCategory> categories;
        if (categoryOrType instanceof List) {
            // Прям список категорий
            categories = (List<RecipeBookCategory>) categoryOrType;
        } else if (categoryOrType instanceof RecipeBookType) {
            categories = ((RecipeBookType) categoryOrType).getCategories();
        } else if (categoryOrType instanceof RecipeBookCategory) {
            categories = List.of((RecipeBookCategory) categoryOrType);
        } else {
            categories = List.of();
        }
        return fastSearch(categories, query, modName, searchIngredients);
    }

    public static List<RecipeResultCollection> generateCustomRecipeList(String filter) {
        List<RecipeResultCollection> list = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();

        filter = filter.trim();
        String _modName = null;
        String _query = "";

        if (filter.startsWith("@")) {
            String[] parts = filter.substring(1).split(" ", 2);
            _modName = parts[0].toLowerCase(java.util.Locale.ROOT);
            if (parts.length > 1) {
                _query = parts[1].toLowerCase(java.util.Locale.ROOT);
            }
        } else {
            _query = filter.toLowerCase(java.util.Locale.ROOT);
        }

        final String modName = _modName;
        final String query = _query;

        RecipeIndex.ITEM_INDEX.stream()
                .filter(idx ->
                        (modName == null || idx.mod.contains(modName)) &&
                                (query.isEmpty() ||
                                        idx.name.contains(query) ||
                                        idx.id.contains(query) ||
                                        idx.key.contains(query) ||
                                        idx.tooltip.stream().anyMatch(line -> line.contains(query))
                                )
                )
                .forEach(idx -> {
                    var dummy = RecipeIndex.createDummyResultCollection(idx.item);
                    list.add(dummy);
                });

        return list;
    }


    // Выносим генерацию фейковой коллекции в отдельный метод для компактности:
    private static RecipeResultCollection createDummyResultCollection(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        NetworkRecipeId recipeId = new NetworkRecipeId(9999);

        List<SlotDisplay> slots = List.of(
                new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, id))
        );
        SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item, 1));
        SlotDisplay.ItemSlotDisplay stationSlot =
                new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", "crafting_table")));

        List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

        RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
        return new RecipeResultCollection(List.of(entry));
    }


    public static class IndexedItem {
        public final Item item;
        public final String id;
        public final String name;
        public final String mod;
        public final String key;
        public final List<String> tooltip;

        public IndexedItem(Item item, String id, String name, String mod, String key, List<String> tooltip) {
            this.item = item;
            this.id = id;
            this.name = name;
            this.mod = mod;
            this.key = key;
            this.tooltip = tooltip;
        }
    }

    // jeb.client.JebClient.java
    public static List<IndexedItem> ITEM_INDEX = new ArrayList<>();

    public static void fillItemIndex() {
        MinecraftClient client = MinecraftClient.getInstance();
        ITEM_INDEX.clear();
        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;
            String id = item.toString().toLowerCase(Locale.ROOT);
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String mod = Registries.ITEM.getId(item).getNamespace().toLowerCase(Locale.ROOT);
            String key = translate(item.getTranslationKey()).toLowerCase(Locale.ROOT);
            List<String> tooltipLines = new ArrayList<>(List.of());

            // Можно закэшировать тултипы заранее
            if (client.world != null) {
                try {
                    RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
                    Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
                    TooltipType tooltipType = TooltipType.Default.BASIC; // или ADVANCED, если нужен "расширенный" режим

                    List<Text> tooltip = item.getDefaultStack().getTooltip(tooltipContext, client.player, tooltipType);

                    for (Text line : tooltip) {
                        String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                        tooltipLines.add(clean);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            ITEM_INDEX.add(new IndexedItem(item, id, name, mod, key, tooltipLines));
        }
    }

    /**
     * Проверяет наличие рецепта с тем же id в индексе для категории.
     * Сравнивает по RecipeDisplayEntry.getId() — не по объекту!
     */
    public static boolean recipeIdExistsInIndex(RecipeBookCategory category, RecipeDisplayEntry recipeEntry) {
        Map<String, List<RecipeResultCollection>> resultIndex = GLOBAL_RECIPE_INDEX.byResult.get(category);
        if (resultIndex == null) return false;
        ContextParameterMap context = SlotDisplayContexts.createParameters(Objects.requireNonNull(MinecraftClient.getInstance().world));
        ItemStack result = recipeEntry.display().result().getFirst(context);
        if (result == null || result.isEmpty()) return false;
        String resultId = Registries.ITEM.getId(result.getItem()).toString().toLowerCase(Locale.ROOT);
        List<RecipeResultCollection> collections = resultIndex.get(resultId);
        if (collections == null) return false;

        String incomingId = recipeEntry.id().toString(); // или .asString() — смотри по типу

        for (RecipeResultCollection collection : collections) {
            for (RecipeDisplayEntry r : collection.getAllRecipes()) {
                if (r.id().toString().equals(incomingId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void updateIndexesWithRecipe(
            RecipeBookCategory category,
            RecipeResultCollection collection,
            RecipeDisplayEntry recipeEntry
    ) {
        GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(collection);

        ContextParameterMap context = SlotDisplayContexts.createParameters(Objects.requireNonNull(MinecraftClient.getInstance().world));
        ItemStack result = recipeEntry.display().result().getFirst(context);
        if (result == null || result.isEmpty()) return;
        Item resultItem = result.getItem();

        // Индексация по модам
        String mod = Registries.ITEM.getId(resultItem).getNamespace().toLowerCase(Locale.ROOT);
        Map<String, List<RecipeResultCollection>> modIndex =
                GLOBAL_RECIPE_INDEX.byMod.computeIfAbsent(category, k -> new HashMap<>());
        for (int i = 0; i < mod.length(); i++) {
            for (int j = i + 1; j <= mod.length(); j++) {
                String substr = mod.substring(i, j);
                if (substr.isEmpty()) continue;
                List<RecipeResultCollection> list = modIndex.computeIfAbsent(substr, k -> new ArrayList<>());
                if (!list.contains(collection)) {
                    list.add(collection);
                }
            }
        }

        // Индексация по результату
        String resultId = Registries.ITEM.getId(resultItem).toString().toLowerCase(Locale.ROOT);
        String name = result.getItemName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
        Map<String, List<RecipeResultCollection>> resultIndex =
                GLOBAL_RECIPE_INDEX.byResult.computeIfAbsent(category, k -> new HashMap<>());
        for (String source : List.of(resultId, name)) {
            for (int i = 0; i < source.length(); i++) {
                for (int j = i + 1; j <= source.length(); j++) {
                    String substr = source.substring(i, j);
                    if (substr.isEmpty()) continue;
                    List<RecipeResultCollection> list = resultIndex.computeIfAbsent(substr, k -> new ArrayList<>());
                    if (!list.contains(collection)) {
                        list.add(collection);
                    }
                }
            }
        }

        // Индексация по ингредиентам
        Map<String, List<RecipeResultCollection>> ingredientIndex =
                GLOBAL_RECIPE_INDEX.byIngredientWord.computeIfAbsent(category, k -> new HashMap<>());
        Optional<List<Ingredient>> opt = recipeEntry.craftingRequirements();
        if (opt.isPresent()) {
            for (Ingredient ingredient : opt.get()) {
                for (ItemStack stack : ingredient.toDisplay().getStacks(context)) {
                    String ingredientId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                    List<RecipeResultCollection> list = ingredientIndex.computeIfAbsent(ingredientId, k -> new ArrayList<>());
                    if (!list.contains(collection)) {
                        list.add(collection);
                    }
                }
            }
        }

        // Индексация по тултипам (подстроки длиной >=3)
        Map<String, List<RecipeResultCollection>> tooltipIndex =
                GLOBAL_RECIPE_INDEX.byTooltipWord.computeIfAbsent(category, k -> new HashMap<>());

        MinecraftClient client = MinecraftClient.getInstance();
        List<String> tooltipLines = new ArrayList<>();
        try {
            RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
            Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
            TooltipType tooltipType = TooltipType.Default.BASIC;
            List<Text> tooltip = result.getTooltip(tooltipContext, client.player, tooltipType);
            for (Text line : tooltip) {
                String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                tooltipLines.add(clean);
            }
        } catch (Exception ignored) {}

        for (String tooltipLine : tooltipLines) {
            String[] words = tooltipLine.split("[\\s,;.:!\\-]+");
            for (String word : words) {
                if (word.length() < 3) continue;
                for (int i = 0; i <= word.length() - 3; i++) {
                    for (int j = i + 3; j <= word.length(); j++) {
                        String substr = word.substring(i, j);
                        if (substr.isEmpty()) continue;
                        List<RecipeResultCollection> list = tooltipIndex.computeIfAbsent(substr, k -> new ArrayList<>());
                        if (!list.contains(collection)) {
                            list.add(collection);
                        }
                    }
                }
            }
        }
    }

    public static void addRecipeToCollectionIfAbsent(
            RecipeBookCategory category,
            RecipeDisplayEntry recipeEntry,
            ContextParameterMap context
    ) {
        Map<Item, RecipeResultCollection> byItem = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
        ItemStack result = recipeEntry.display().result().getFirst(context);
        if (result == null || result.isEmpty()) return;
        Item resultItem = result.getItem();

        RecipeResultCollection collection = byItem.get(resultItem);
        if (collection == null) {
            collection = new RecipeResultCollection(new ArrayList<>());
            byItem.put(resultItem, collection);
            RecipeIndex.GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(collection);
        }
        List<RecipeDisplayEntry> recipes = collection.getAllRecipes();

        if (recipes.contains(recipeEntry)) {
            return; // Уже был такой рецепт!
        }
        try {
            recipes.add(recipeEntry);
        } catch (UnsupportedOperationException e) {
            List<RecipeDisplayEntry> fixed = new ArrayList<>(recipes);
            fixed.add(recipeEntry);
            RecipeResultCollection newCollection = new RecipeResultCollection(fixed);
            byItem.put(resultItem, newCollection);
            Set<RecipeResultCollection> set = RecipeIndex.GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>());
            set.remove(collection);
            set.add(newCollection);
        }
    }

    public static void addAndIndexRecipeIfAbsent(
            RecipeBookCategory category,
            RecipeDisplayEntry recipeEntry,
            ContextParameterMap context
    ) {
        // Добавляем в коллекцию, если нет
        addRecipeToCollectionIfAbsent(category, recipeEntry, context);

        // Получаем коллекцию (она гарантированно есть!)
        Map<Item, RecipeResultCollection> byItem = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
        ItemStack result = recipeEntry.display().result().getFirst(context);
        if (result == null || result.isEmpty()) return;
        Item resultItem = result.getItem();
        RecipeResultCollection collection = byItem.get(resultItem);
        if (collection == null) return; // Уже не может быть, но на всякий случай

        // Индексируем (можно оптимизировать чтобы не индексировать дважды, но это уже детали)
        updateIndexesWithRecipe(category, collection, recipeEntry);
    }



}
