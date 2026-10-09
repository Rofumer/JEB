package jeb.client;


import net.minecraft.ChatFormatting;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
//import net.minecraft.recipe.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import java.lang.reflect.Field;
import java.util.*;

import static jeb.client.JEBClient.nonexistingResultItems;
import static net.minecraft.client.resources.language.I18n.get;

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
                    String name = result.getHoverName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
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

    // Индексы по категориям.
    // Раньше byResult/byMod/byTooltipWord хранили все подстроки id/имени/мода/слов тултипа
    // (O(n²) ключей на строку) — на больших сборках это сотни МБ (issue #10).
    // Теперь на каждую коллекцию хранится одна компактная запись, а подстроки ищутся
    // через contains() при поиске — это линейный проход, единицы мс даже на тысячах коллекций.
    public final Map<RecipeBookCategory, Map<RecipeCollection, SearchEntry>> searchEntries = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, Set<RecipeCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookCategory, Set<RecipeCollection>> allCollections = new HashMap<>();
    public final Map<RecipeBookCategory, Set<RecipeDisplayId>> recipeIds = new HashMap<>();
    public static final Map<RecipeBookCategory, Map<String, RecipeCollection>> GLOBAL_COLLECTIONS_BY_RESULT = new HashMap<>();

    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();
    public static boolean jebIndexReady = false;

    private static final String TOOLTIP_WORD_SEPARATOR = "\n";
    private static final int MIN_TOOLTIP_QUERY_LENGTH = 3;

    /** Поисковые строки одной коллекции (все рецепты коллекции дают один и тот же результат). */
    public static final class SearchEntry {
        final String id;
        final String name;
        final String mod;
        // Уникальные слова тултипа (длиной >= 3), склеенные через '\n'. Запрос не содержит
        // разделителей слов, поэтому contains() по этой строке совпадает только внутри одного слова.
        String tooltipWords;

        SearchEntry(String id, String name, String mod, String tooltipWords) {
            this.id = id;
            this.name = name;
            this.mod = mod;
            this.tooltipWords = tooltipWords;
        }

        boolean matchesResult(String query) {
            return id.contains(query) || name.contains(query);
        }

        boolean matchesTooltip(String query) {
            return query.length() >= MIN_TOOLTIP_QUERY_LENGTH
                    && !query.contains(TOOLTIP_WORD_SEPARATOR)
                    && tooltipWords.contains(query);
        }

        void addTooltipWords(Set<String> words) {
            if (words.isEmpty()) return;
            Set<String> merged = new LinkedHashSet<>();
            if (!tooltipWords.isEmpty()) merged.addAll(Arrays.asList(tooltipWords.split(TOOLTIP_WORD_SEPARATOR)));
            if (merged.addAll(words)) {
                tooltipWords = String.join(TOOLTIP_WORD_SEPARATOR, merged);
            }
        }
    }

    private static String resultKey(Item item, ItemStack resolvedResult) {
        String signature = resolvedResult.getHoverName().getString().toLowerCase(Locale.ROOT).trim();
        return BuiltInRegistries.ITEM.getKey(item) + "|" + signature;
    }

    // === Индексация ===
    public static void buildRecipeIndex() {
        long startTime = System.currentTimeMillis();
        JEBClient.LOGGER.info("[JEB] buildRecipeIndex started at {}", new Date(startTime));

        jebIndexReady = false;
        Minecraft client = Minecraft.getInstance();
        ClientRecipeBook book = client.player.getRecipeBook();

        GLOBAL_RECIPE_INDEX.searchEntries.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
        GLOBAL_RECIPE_INDEX.allCollections.clear();
        GLOBAL_RECIPE_INDEX.recipeIds.clear();
        GLOBAL_COLLECTIONS_BY_RESULT.clear();

        ContextMap context = SlotDisplayContext.fromLevel(Objects.requireNonNull(client.level));

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
            List<RecipeCollection> collections = book.getCollection(category);
            if (collections.isEmpty()) continue;

            Map<String, RecipeCollection> collectionsByResult = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
            Set<RecipeCollection> categoryCollections = GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>());

            for (RecipeCollection collection : collections) {
                List<RecipeDisplayEntry> entries = collection.getRecipes();
                for (RecipeDisplayEntry recipe : entries) {
                    totalIndexedRecipes++;

                    ItemStack result = recipe.display().result().resolveForFirstStack(context);
                    if (result == null || result.isEmpty()) continue;
                    String resultKey = resultKey(result.getItem(), result);

                    // Коллекция по результату
                    RecipeCollection realCollection = collectionsByResult.get(resultKey);
                    if (realCollection == null) {
                        realCollection = new RecipeCollection(new ArrayList<>());
                        collectionsByResult.put(resultKey, realCollection);
                        categoryCollections.add(realCollection);
                    }
                    if (!realCollection.getRecipes().contains(recipe)) {
                        realCollection.getRecipes().add(recipe);
                    }

                    indexRecipe(category, realCollection, recipe, result, context);
                }
            }
        }

        jebIndexReady = true;

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        JEBClient.LOGGER.info("[JEB] buildRecipeIndex done at {} ({} ms), total indexed recipes: {}", new Date(endTime), duration, totalIndexedRecipes);
    }

    /** Общая индексация рецепта для buildRecipeIndex() и updateIndexesWithRecipe(). */
    private static void indexRecipe(
            RecipeBookCategory category,
            RecipeCollection collection,
            RecipeDisplayEntry recipe,
            ItemStack result,
            ContextMap context
    ) {
        GLOBAL_RECIPE_INDEX.recipeIds.computeIfAbsent(category, k -> new HashSet<>()).add(recipe.id());

        // Индексация по ингредиентам (точный id)
        Optional<List<Ingredient>> opt = recipe.craftingRequirements();
        if (opt.isPresent()) {
            Map<String, Set<RecipeCollection>> ingredientIndex =
                    GLOBAL_RECIPE_INDEX.byIngredientWord.computeIfAbsent(category, k -> new HashMap<>());
            for (Ingredient ingredient : opt.get()) {
                for (ItemStack stack : ingredient.display().resolveForStacks(context)) {
                    String ingredientId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                    ingredientIndex.computeIfAbsent(ingredientId, k -> new LinkedHashSet<>()).add(collection);
                }
            }
        }

        // Поисковые строки результата: id, имя, мод, слова тултипа
        Set<String> tooltipWords = tooltipWords(result);
        Map<RecipeCollection, SearchEntry> entries =
                GLOBAL_RECIPE_INDEX.searchEntries.computeIfAbsent(category, k -> new HashMap<>());
        SearchEntry entry = entries.get(collection);
        if (entry == null) {
            Identifier key = BuiltInRegistries.ITEM.getKey(result.getItem());
            String resultId = key.toString().toLowerCase(Locale.ROOT);
            String name = result.getHoverName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
            String mod = key.getNamespace().toLowerCase(Locale.ROOT);
            entries.put(collection, new SearchEntry(resultId, name, mod, String.join(TOOLTIP_WORD_SEPARATOR, tooltipWords)));
        } else {
            entry.addTooltipWords(tooltipWords);
        }
    }

    private static Set<String> tooltipWords(ItemStack result) {
        Set<String> words = new LinkedHashSet<>();
        Minecraft client = Minecraft.getInstance();
        try {
            HolderLookup.Provider lookup = client.level.registryAccess();
            Item.TooltipContext tooltipContext = Item.TooltipContext.of(lookup);
            TooltipFlag tooltipType = TooltipFlag.Default.NORMAL;
            List<Component> tooltip = result.getTooltipLines(tooltipContext, client.player, tooltipType);
            for (Component line : tooltip) {
                String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                for (String word : clean.split("[\\s,;.:!\\-]+")) {
                    if (word.length() >= MIN_TOOLTIP_QUERY_LENGTH) words.add(word);
                }
            }
        } catch (Exception ignored) {}
        return words;
    }


    // === Универсальный быстрый поиск по списку категорий ===
    public static List<RecipeCollection> fastSearch(
            List<RecipeBookCategory> categories,
            String query,
            String modName,
            boolean searchIngredients
    ) {
        if (!jebIndexReady) return List.of();

        query = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        modName = modName == null ? "" : modName.toLowerCase(Locale.ROOT).trim();

        Set<RecipeCollection> result = new LinkedHashSet<>();

        for (RecipeBookCategory category : categories) {
            Map<String, Set<RecipeCollection>> ingredientIndex = GLOBAL_RECIPE_INDEX.byIngredientWord.getOrDefault(category, Map.of());
            Set<RecipeCollection> all = GLOBAL_RECIPE_INDEX.allCollections.getOrDefault(category, Set.of());
            Map<RecipeCollection, SearchEntry> entries = GLOBAL_RECIPE_INDEX.searchEntries.getOrDefault(category, Map.of());

            // Если нет фильтра по модулю и пустой запрос — вернуть все коллекции!
            if ((modName.isEmpty()) && query.isEmpty()) {
                result.addAll(all);
                continue;
            }

            // Поиск по модулю (namespace)
            if (!modName.isEmpty()) {
                List<RecipeCollection> modCollections = new ArrayList<>();
                List<SearchEntry> modEntries = new ArrayList<>();
                for (RecipeCollection rc : all) {
                    SearchEntry entry = entries.get(rc);
                    if (entry != null && entry.mod.contains(modName)) {
                        modCollections.add(rc);
                        modEntries.add(entry);
                    }
                }
                if (query.isEmpty()) {
                    result.addAll(modCollections);
                    continue;
                }
                // Ищем среди коллекций по моду по словам
                for (String word : query.split("[\\s:_\\-]+")) {
                    if (word.isEmpty()) continue;
                    for (int i = 0; i < modCollections.size(); i++) {
                        if (modEntries.get(i).matchesResult(word))
                            result.add(modCollections.get(i));
                    }
                }
                continue;
            }

            // Поиск по ингредиенту
            if (searchIngredients) {
                result.addAll(ingredientIndex.getOrDefault(query, Set.of()));
                continue;
            }

            // Поиск по результату (id или имя), затем по тултипам
            List<RecipeCollection> byTooltip = new ArrayList<>();
            for (RecipeCollection rc : all) {
                SearchEntry entry = entries.get(rc);
                if (entry == null) continue;
                if (entry.matchesResult(query)) {
                    result.add(rc);
                } else if (entry.matchesTooltip(query)) {
                    byTooltip.add(rc);
                }
            }
            result.addAll(byTooltip);
        }

        return new ArrayList<>(result);
    }


    // Универсальная обёртка: принимает или одиночную категорию, или Type с его списком категорий
    public static List<RecipeCollection> fastSearch(
            Object categoryOrType, String query, String modName, boolean searchIngredients
    ) {
        List<RecipeBookCategory> categories;
        if (categoryOrType instanceof List) {
            // Прям список категорий
            categories = (List<RecipeBookCategory>) categoryOrType;
        } else if (categoryOrType instanceof SearchRecipeBookCategory) {
            categories = ((SearchRecipeBookCategory) categoryOrType).includedCategories();
        } else if (categoryOrType instanceof RecipeBookCategory) {
            categories = List.of((RecipeBookCategory) categoryOrType);
        } else {
            categories = List.of();
        }
        return fastSearch(categories, query, modName, searchIngredients);
    }

    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> list = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();

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
    private static RecipeCollection createDummyResultCollection(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        RecipeDisplayId recipeId = new RecipeDisplayId(9999);

        List<SlotDisplay> slots = List.of(
                new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, id))
        );
        SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(item, 1));
        SlotDisplay.ItemSlotDisplay stationSlot =
                new SlotDisplay.ItemSlotDisplay(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "crafting_table")));

        List<Ingredient> ingredients = List.of(Ingredient.of(item));

        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

        RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
        return new RecipeCollection(List.of(entry));
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
        Minecraft client = Minecraft.getInstance();
        ITEM_INDEX.clear();
        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;
            String id = item.toString().toLowerCase(Locale.ROOT);
            String name = item.getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT);
            String mod = BuiltInRegistries.ITEM.getKey(item).getNamespace().toLowerCase(Locale.ROOT);
            String key = get(item.getDescriptionId()).toLowerCase(Locale.ROOT);
            List<String> tooltipLines = new ArrayList<>(List.of());

            // Можно закэшировать тултипы заранее
            if (client.level != null) {
                try {
                    HolderLookup.Provider lookup = client.level.registryAccess();
                    Item.TooltipContext tooltipContext = Item.TooltipContext.of(lookup);
                    TooltipFlag tooltipType = TooltipFlag.Default.NORMAL; // или ADVANCED, если нужен "расширенный" режим

                    List<Component> tooltip = item.getDefaultInstance().getTooltipLines(tooltipContext, client.player, tooltipType);

                    for (Component line : tooltip) {
                        String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
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
     * Сравнивает по RecipeDisplayEntry.id() — не по объекту!
     */
    public static boolean recipeIdExistsInIndex(RecipeBookCategory category, RecipeDisplayEntry recipeEntry) {
        Set<RecipeDisplayId> ids = GLOBAL_RECIPE_INDEX.recipeIds.get(category);
        return ids != null && ids.contains(recipeEntry.id());
    }

    public static void updateIndexesWithRecipe(
            RecipeBookCategory category,
            RecipeCollection collection,
            RecipeDisplayEntry recipeEntry
    ) {
        GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(collection);

        ContextMap context = SlotDisplayContext.fromLevel(Objects.requireNonNull(Minecraft.getInstance().level));
        ItemStack result = recipeEntry.display().result().resolveForFirstStack(context);
        if (result == null || result.isEmpty()) return;

        indexRecipe(category, collection, recipeEntry, result, context);
    }

    public static void addRecipeToCollectionIfAbsent(
            RecipeBookCategory category,
            RecipeDisplayEntry recipeEntry,
            ContextMap context
    ) {
        Map<String, RecipeCollection> byItem = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
        ItemStack result = recipeEntry.display().result().resolveForFirstStack(context);
        if (result == null || result.isEmpty()) return;
        Item resultItem = result.getItem();
        String resultKey = resultKey(resultItem, result);

        RecipeCollection collection = byItem.get(resultKey);
        if (collection == null) {
            collection = new RecipeCollection(new ArrayList<>());
            byItem.put(resultKey, collection);
            RecipeIndex.GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(collection);
        }
        List<RecipeDisplayEntry> recipes = collection.getRecipes();

        if (recipes.contains(recipeEntry)) {
            return; // Уже был такой рецепт!
        }
        try {
            recipes.add(recipeEntry);
        } catch (UnsupportedOperationException e) {
            List<RecipeDisplayEntry> fixed = new ArrayList<>(recipes);
            fixed.add(recipeEntry);
            RecipeCollection newCollection = new RecipeCollection(fixed);
            byItem.put(resultKey, newCollection);
            Set<RecipeCollection> set = RecipeIndex.GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>());
            set.remove(collection);
            set.add(newCollection);
        }
    }

    public static void addAndIndexRecipeIfAbsent(
            RecipeBookCategory category,
            RecipeDisplayEntry recipeEntry,
            ContextMap context
    ) {
        // Добавляем в коллекцию, если нет
        addRecipeToCollectionIfAbsent(category, recipeEntry, context);

        // Получаем коллекцию (она гарантированно есть!)
        Map<String, RecipeCollection> byItem = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
        ItemStack result = recipeEntry.display().result().resolveForFirstStack(context);
        if (result == null || result.isEmpty()) return;
        Item resultItem = result.getItem();
        RecipeCollection collection = byItem.get(resultKey(resultItem, result));
        if (collection == null) return; // Уже не может быть, но на всякий случай

        // Индексируем (можно оптимизировать чтобы не индексировать дважды, но это уже детали)
        updateIndexesWithRecipe(category, collection, recipeEntry);
    }



}
