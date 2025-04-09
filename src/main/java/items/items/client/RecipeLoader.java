package items.items.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.display.*;
import net.minecraft.recipe.display.SlotDisplay.AnyFuelSlotDisplay;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecipeLoader {

    private static List<String> parseBracketedList(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketLevel = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ',' && bracketLevel == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                if (c == '[') bracketLevel++;
                else if (c == ']') bracketLevel--;
                current.append(c);
            }
        }
        if (!current.isEmpty()) result.add(current.toString().trim());
        return result;
    }


    private static String fixResourceName(String resourceName) {
        if (resourceName.startsWith("minecraft:")) {
            return resourceName.substring("minecraft:".length());
        }
        return resourceName;
    }

    public static void loadRecipesFromLog(File logFile) throws IOException {
        List<String> lines = Files.readAllLines(logFile.toPath());

        for (String line : lines) {
            if (line.startsWith("Display:")) {
                Optional<RecipeDisplayEntry> recipeEntry = parseLineToRecipeEntry(line);
                recipeEntry.ifPresent(RecipeLoader::sendToClient);
            }
        }
    }

    private static Optional<RecipeDisplayEntry> parseLineToRecipeEntry(String line) {
        try {


            // Извлекаем тип отображения
            if (line.contains("StonecutterRecipeDisplay")) {

                //System.out.println("Обрабатывается строка для рецепта камнереза:" + line);

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                //System.out.println("индекс: " + index);

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\.empty").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.empty() : OptionalInt.empty();

                //System.out.println("группа: " + group);

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                //System.out.println("категория: " + category);

                // Получаем ингредиент (вход)
                Matcher inputMatcher = Pattern.compile("input=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String inputItem = inputMatcher.find() ? fixResourceName(inputMatcher.group(2)) : null;

                //System.out.println("входной элемент: " + inputItem);

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                //System.out.println("результат: " + resultCount + " " + resultItem);

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(2));

                //System.out.println("станция: " + stationName);

                // Получаем список ингредиентов (расшифрованный)
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);

                List<SlotDisplay> slots = new ArrayList<>();
                List<Ingredient> ingredients = new ArrayList<>();

                // Каждая строка — один слот (с множеством вариантов)
                for (String rawSlot : itemsSection.split(";")) {
                    String[] variants = rawSlot.split(",");
                    if (variants.length == 0 || (variants.length == 1 && variants[0].isBlank())) {
                        // пустой слот
                        slots.add(new SlotDisplay.CompositeSlotDisplay(List.of()));
                        continue;
                    }

                    List<SlotDisplay> slotVariants = new ArrayList<>();
                    List<Item> ingredientItems = new ArrayList<>();
                    for (String variant : variants) {
                        variant = fixResourceName(variant.trim());
                        Item item = Registries.ITEM.get(Identifier.of("minecraft", variant));
                        slotVariants.add(new SlotDisplay.ItemSlotDisplay(item));
                        ingredientItems.add(item);
                    }

                    slots.add(new SlotDisplay.CompositeSlotDisplay(slotVariants));
                    ingredients.add(Ingredient.ofItems(ingredientItems.toArray(new Item[0])));
                }


                // Собираем объект рецепта
                SlotDisplay.ItemSlotDisplay inputSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", inputItem)));
                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", stationName)));

                // Собираем объект рецепта
                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                StonecutterRecipeDisplay display = new StonecutterRecipeDisplay(
                        inputSlot, // входной элемент
                        resultSlot, // результат
                        stationSlot // станция
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                //System.out.println("Результат обработки:" + entry);

                return Optional.of(entry);
            }


// Извлекаем тип отображения
            if (line.contains("SmithingRecipeDisplay")) {

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\.empty").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.empty() : OptionalInt.empty();

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                // Получаем ингредиенты для шаблона, базы и добавления
                Matcher templateMatcher = Pattern.compile("template=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String templateItem = templateMatcher.find() ? fixResourceName(templateMatcher.group(2)) : null;

// Сначала проверяем для CompositeSlotDisplay (существующий вариант)
                Matcher baseMatcher = Pattern.compile("base=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String baseItem = baseMatcher.find() ? fixResourceName(baseMatcher.group(2)) : null;

// Если не нашли в CompositeSlotDisplay, проверяем для TagSlotDisplay
                if (baseItem == null) {
                    Matcher tagBaseMatcher = Pattern.compile("base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:(.*?)\\]\\]").matcher(line);
                    baseItem = tagBaseMatcher.find() ? fixResourceName(tagBaseMatcher.group(1)) : null;
                }

// Если baseItem все еще null, возвращаем Optional.empty()
                if (baseItem == null) {
                    return Optional.empty();
                }


                Matcher additionMatcher = Pattern.compile("addition=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:(.*?)\\]\\]").matcher(line);
                String additionTag = additionMatcher.find() ? fixResourceName(additionMatcher.group(1)) : null;

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                Matcher trimResultMatcher = Pattern.compile("result=SmithingTrimSlotDisplay\\[base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\],.*?material=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\]").matcher(line);


                String resultItem = null;
                int resultCount = 1; // По умолчанию результат = 1

                if (trimResultMatcher.find()) {
                    System.out.println("Захватили SmithingTrimSlotDisplay: " + trimResultMatcher.group(1));
                    resultItem = trimResultMatcher.group(1);  // Извлекаем item из SmithingTrimSlotDisplay
                    System.out.println("resultItem: " + resultItem);
                }
                else if (resultMatcher.find()) {
                    // Обрабатываем обычный StackSlotDisplay
                    resultCount = Integer.parseInt(resultMatcher.group(1));
                    resultItem = fixResourceName(resultMatcher.group(2));
                } else {
                    return Optional.empty(); // Если не нашли ни того, ни другого, возвращаем Optional.empty
                }

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(2));

                System.out.println("test1");

                // Получаем список ингредиентов (расшифрованный)
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);

                System.out.println("test2");

                List<SlotDisplay> slots = new ArrayList<>();
                List<Ingredient> ingredients = new ArrayList<>();

                // Каждая строка — один слот (с множеством вариантов)
                for (String rawSlot : itemsSection.split(";")) {
                    String[] variants = rawSlot.split(",");
                    if (variants.length == 0 || (variants.length == 1 && variants[0].isBlank())) {
                        // Пустой слот
                        slots.add(new SlotDisplay.CompositeSlotDisplay(List.of()));
                        continue;
                    }

                    List<SlotDisplay> slotVariants = new ArrayList<>();
                    List<Item> ingredientItems = new ArrayList<>();
                    for (String variant : variants) {
                        variant = fixResourceName(variant.trim());
                        Item item = Registries.ITEM.get(Identifier.of("minecraft", variant));
                        slotVariants.add(new SlotDisplay.ItemSlotDisplay(item));
                        ingredientItems.add(item);
                    }

                    slots.add(new SlotDisplay.CompositeSlotDisplay(slotVariants));
                    ingredients.add(Ingredient.ofItems(ingredientItems.toArray(new Item[0])));
                }

                System.out.println("test3");

                // Собираем объект рецепта
                SlotDisplay.ItemSlotDisplay templateSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", templateItem)));
                System.out.println("test3.1");
                SlotDisplay.ItemSlotDisplay baseSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", baseItem)));
                System.out.println("test3.2");
                SlotDisplay.ItemSlotDisplay additionSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", additionTag)));
                System.out.println("test3.3");
                System.out.println("test4");
                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );
                System.out.println("test5");
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", stationName)));

                System.out.println("test6");
                // Собираем объект рецепта
                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                SmithingRecipeDisplay display = new SmithingRecipeDisplay(
                        templateSlot, // Шаблон
                        baseSlot, // База
                        additionSlot, // Добавление
                        resultSlot, // Результат
                        stationSlot // Станция
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }


            if (line.contains("FurnaceRecipeDisplay")) {

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.of(Integer.parseInt(groupMatcher.group(1))) : OptionalInt.empty();

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                // --- ИНГРЕДИЕНТЫ ---
                SlotDisplay ingredientSlot = null;

                // Пытаемся найти TagSlotDisplay
                Matcher tagMatcher = Pattern.compile("ingredient=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)]]").matcher(line);
                if (tagMatcher.find()) {
                    String tag = tagMatcher.group(1);
                    ingredientSlot = new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", tag)));
                } else {
                    // Иначе ищем CompositeSlotDisplay
                    Matcher ingredientMatcher = Pattern.compile("ingredient=CompositeSlotDisplay\\[contents=\\[(.*?)\\]\\]").matcher(line);
                    if (!ingredientMatcher.find()) return Optional.empty();

                    String ingredientsSection = ingredientMatcher.group(1);
                    List<SlotDisplay> ingredientSlots = new ArrayList<>();

                    // Парсим все ItemSlotDisplay внутри Composite
                    Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]=]+)]=").matcher(ingredientsSection);
                    while (itemMatcher.find()) {
                        String ingredientItem = fixResourceName(itemMatcher.group(1));
                        ingredientSlots.add(new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", ingredientItem))));
                    }

                    ingredientSlot = new SlotDisplay.CompositeSlotDisplay(ingredientSlots);
                }

                // Получаем топливо (если указано)
                Matcher fuelMatcher = Pattern.compile("fuel=<([^>]+)>").matcher(line);
                String fuel = null;
                if (fuelMatcher.find()) {
                    String fuelValue = fuelMatcher.group(1);
                    if (!"any fuel".equals(fuelValue)) {
                        fuel = fixResourceName(fuelValue);
                    }
                }

                SlotDisplay fuelSlot = fuel != null ? new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", fuel))) : SlotDisplay.AnyFuelSlotDisplay.INSTANCE;

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(1));

                // Получаем время работы и опыт
                Matcher durationMatcher = Pattern.compile("duration=(\\d+)").matcher(line);
                int duration = durationMatcher.find() ? Integer.parseInt(durationMatcher.group(1)) : 0;

                Matcher experienceMatcher = Pattern.compile("experience=(\\d+\\.\\d+)").matcher(line);
                float experience = experienceMatcher.find() ? Float.parseFloat(experienceMatcher.group(1)) : 0.0f;

                // Получаем список ингредиентов для структуры Crafting Requirements Items
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);

                List<Ingredient> ingredients = new ArrayList<>();
                for (String rawSlot : itemsSection.split(";")) {
                    String[] variants = rawSlot.split(",");
                    List<Item> ingredientItems = new ArrayList<>();
                    for (String variant : variants) {
                        variant = fixResourceName(variant.trim());
                        ingredientItems.add(Registries.ITEM.get(Identifier.of("minecraft", variant)));
                    }
                    ingredients.add(Ingredient.ofItems(ingredientItems.toArray(new Item[0])));
                }

                // Создаем результат
                SlotDisplay.StackSlotDisplay result = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );

                // Создаем станцию
                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", stationName))
                );

                // Собираем объект
                NetworkRecipeId recipeId = new NetworkRecipeId(index);

                FurnaceRecipeDisplay display = new FurnaceRecipeDisplay(
                        ingredientSlot,
                        fuelSlot,
                        result,
                        station,
                        duration,
                        experience
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }




            if (line.contains("ShapelessCraftingRecipeDisplay")) {

                //System.out.println("Обрабатывается строка:" + line);

                //Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index\\s*=\\s*(\\d+)\\]").matcher(line);

                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + line);
                }

                //Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[.*?\\]").matcher(line);
                //OptionalInt group = groupMatcher.find() ? OptionalInt.empty() : OptionalInt.empty();

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)\\]").matcher(line);
                OptionalInt group = groupMatcher.find()
                        ? OptionalInt.of(Integer.parseInt(groupMatcher.group(1)))  // Извлекаем значение внутри []
                        : OptionalInt.empty();

                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + group);
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + category);
                }

// Получаем результат
// Ищем StackSlotDisplay, как обычно
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                String resultItem = null;
                int resultCount = 1;  // По умолчанию результат - 1 экземпляр предмета

// Если не нашли StackSlotDisplay, пробуем найти ItemSlotDisplay
                if (!resultMatcher.find()) {
                    resultMatcher = Pattern.compile("result=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(\\w+)]=minecraft:(\\w+)}\\]").matcher(line);
                    if (!resultMatcher.find()) return Optional.empty();  // Если не нашли ни того, ни другого, возвращаем Optional.empty
                }

// Если нашли StackSlotDisplay
                if (resultMatcher.group(1) != null && resultMatcher.group(2) != null) {
                    // Обрабатываем StackSlotDisplay
                    try {
                        resultCount = Integer.parseInt(resultMatcher.group(1));  // Преобразуем только если это число
                    } catch (NumberFormatException e) {
                        // Если не удалось преобразовать в число, значит это не StackSlotDisplay с количеством
                        resultCount = 1;  // Устанавливаем по умолчанию
                    }
                    resultItem = fixResourceName(resultMatcher.group(2));  // Применяем fixResourceName
                }
// Если нашли ItemSlotDisplay
                else if (resultMatcher.group(2) != null) {
                    // Обрабатываем ItemSlotDisplay
                    resultItem = fixResourceName(resultMatcher.group(2));  // Извлекаем имя предмета (например, "black_bundle")
                    resultCount = 1;  // Для ItemSlotDisplay всегда устанавливаем 1, так как это всегда один предмет
                }




                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + resultItem);
                }

                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + resultCount);
                }

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(1));

                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + stationName);
                }

                // Получаем строку с ингредиентами (slots)
                int start = line.indexOf("ingredients=[");
                if (start == -1) return Optional.empty();
                start += "ingredients=[".length();

                int depth = 1;
                int end = start;
                while (end < line.length() && depth > 0) {
                    char ch = line.charAt(end);
                    if (ch == '[') depth++;
                    else if (ch == ']') depth--;
                    end++;
                }

                if (depth != 0) return Optional.empty();

                String ingredientsSection = line.substring(start, end - 1);

                List<SlotDisplay> slots = new ArrayList<>();

                // Разбиваем ингредиенты, учитывая вложенность скобок
                int ingredientDepth = 0;
                StringBuilder current = new StringBuilder();
                List<String> rawSlots = new ArrayList<>();

                for (char c : ingredientsSection.toCharArray()) {
                    if (c == '[') ingredientDepth++;
                    if (c == ']') ingredientDepth--;
                    if (c == ',' && ingredientDepth == 0) {
                        rawSlots.add(current.toString().trim());
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                }
                if (!current.isEmpty()) rawSlots.add(current.toString().trim());

                for (String rawSlot : rawSlots) {
                    if (rawSlot.startsWith("<empty")) {
                        slots.add(SlotDisplay.EmptySlotDisplay.INSTANCE);
                    } else if (rawSlot.startsWith("TagSlotDisplay")) {
                        String tagName = rawSlot.substring(rawSlot.indexOf("minecraft:") + "minecraft:".length(), rawSlot.indexOf("]")).trim();
                        String[] splitTag = tagName.split(":");
                        String lastWord = splitTag[splitTag.length - 1];
                        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", lastWord));
                        slots.add(new SlotDisplay.TagSlotDisplay(tagKey));
                    } else if (rawSlot.startsWith("CompositeSlotDisplay")) {
                        Matcher compositeMatcher = Pattern.compile("CompositeSlotDisplay\\[contents=\\[(.*)]").matcher(rawSlot);
                        if (compositeMatcher.find()) {
                            String contents = compositeMatcher.group(1);
                            List<SlotDisplay> compositeContents = new ArrayList<>();

                            // Парсим содержимое CompositeSlotDisplay
                            int cDepth = 0;
                            StringBuilder cCurrent = new StringBuilder();
                            List<String> nestedSlots = new ArrayList<>();

                            for (char cc : contents.toCharArray()) {
                                if (cc == '[') cDepth++;
                                if (cc == ']') cDepth--;
                                if (cc == ',' && cDepth == 0) {
                                    nestedSlots.add(cCurrent.toString().trim());
                                    cCurrent.setLength(0);
                                } else {
                                    cCurrent.append(cc);
                                }
                            }
                            if (!cCurrent.isEmpty()) nestedSlots.add(cCurrent.toString().trim());

                            for (String nestedSlot : nestedSlots) {
                                if (nestedSlot.startsWith("ItemSlotDisplay")) {
                                    Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nestedSlot);
                                    if (itemMatcher.find()) {
                                        String itemName = fixResourceName(itemMatcher.group(1));
                                        Item item = Registries.ITEM.get(Identifier.of("minecraft", itemName));
                                        compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                    }
                                }
                            }

                            slots.add(new SlotDisplay.CompositeSlotDisplay(compositeContents));
                        }
                    }
                }

                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + slots);
                }

                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);

                if(index == 66)
                {

                    System.out.println("Обрабатывается строка:" + itemsSection);
                }

                List<Ingredient> ingredients = new ArrayList<>();
                for (String rawItem : itemsSection.split(";")) {
                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String lastWord = splitItem[splitItem.length - 1];
                            Item item = Registries.ITEM.get(Identifier.of("minecraft", lastWord));
                            ingredients.add(Ingredient.ofItems(item));
                        }
                    }
                }

                SlotDisplay.StackSlotDisplay result = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );

                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", stationName))
                );

                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, result, station);
                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }





// Извлекаем тип отображения
// Извлекаем тип отображения
            if (line.contains("ShapedCraftingRecipeDisplay")) {

                //System.out.println("Обрабатывается строка:" + line);

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[.*?\\]").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.empty() : OptionalInt.empty(); // Если группа отсутствует, то остаемся с пустым OptionalInt

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                // Получаем ширину и высоту
                Matcher dimMatcher = Pattern.compile("ShapedCraftingRecipeDisplay\\[width=(\\d+), height=(\\d+)").matcher(line);
                if (!dimMatcher.find()) return Optional.empty();
                int width = Integer.parseInt(dimMatcher.group(1));
                int height = Integer.parseInt(dimMatcher.group(2));

// Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                String resultItem = null;
                int resultCount = 1;  // По умолчанию результат - 1 экземпляр предмета

// Если не нашли StackSlotDisplay, пробуем найти ItemSlotDisplay
                if (!resultMatcher.find()) {
                    resultMatcher = Pattern.compile("result=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(\\w+)]=minecraft:(\\w+)}\\]").matcher(line);
                    if (!resultMatcher.find()) return Optional.empty();  // Если не нашли ни того, ни другого, возвращаем Optional.empty
                }

// Если нашли StackSlotDisplay
                if (resultMatcher.group(1) != null && resultMatcher.group(2) != null) {
                    // Обрабатываем StackSlotDisplay
                    try {
                        resultCount = Integer.parseInt(resultMatcher.group(1));  // Преобразуем только если это число
                    } catch (NumberFormatException e) {
                        // Если не удалось преобразовать в число, значит это не StackSlotDisplay с количеством
                        resultCount = 1;  // Устанавливаем по умолчанию
                    }
                    resultItem = fixResourceName(resultMatcher.group(2));  // Применяем fixResourceName
                }
// Если нашли ItemSlotDisplay
                else if (resultMatcher.group(2) != null) {
                    // Обрабатываем ItemSlotDisplay
                    resultItem = fixResourceName(resultMatcher.group(2));  // Извлекаем имя предмета (например, "black_bundle")
                    resultCount = 1;  // Для ItemSlotDisplay всегда устанавливаем 1, так как это всегда один предмет
                }


                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(1)); // Применяем fixResourceName

                // Получаем строку с ингредиентами и слотами
                Matcher ingredientsMatcher = Pattern.compile("ingredients=\\[(.*)]").matcher(line);
                if (!ingredientsMatcher.find()) return Optional.empty();
                String ingredientsSection = ingredientsMatcher.group(1);

// Разбиваем строку на компоненты для тегов и CompositeSlotDisplay
                List<SlotDisplay> slots = new ArrayList<>();
                for (String rawSlot : ingredientsSection.split(", ")) {
                    System.out.println("Обработка rawSlot: '" + rawSlot + "'");

                    rawSlot = rawSlot.trim();

                    if (rawSlot.startsWith("<empty")) {
                        slots.add(SlotDisplay.EmptySlotDisplay.INSTANCE);
                        System.out.println("Добавлен EmptySlotDisplay");
                        continue;
                    }

                    if (rawSlot.startsWith("TagSlotDisplay")) {
                        // Извлекаем имя тега
                        String tagName = rawSlot.substring(rawSlot.indexOf("minecraft:") + "minecraft:".length(), rawSlot.indexOf("]")).trim();
                        String[] splitTag = tagName.split(":");
                        String lastWord = splitTag[splitTag.length - 1];
                        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", lastWord));
                        slots.add(new SlotDisplay.TagSlotDisplay(tagKey));
                        System.out.println("Добавлен TagSlotDisplay: " + lastWord);
                    } else if (rawSlot.startsWith("CompositeSlotDisplay")) {
                        // Обрабатываем CompositeSlotDisplay, извлекая вложенные слоты
                        Matcher compositeMatcher = Pattern.compile("CompositeSlotDisplay\\[contents=\\[(.*)]").matcher(rawSlot);
                        if (compositeMatcher.find()) {
                            String contents = compositeMatcher.group(1);
                            System.out.println("Найден CompositeSlotDisplay, contents: " + contents);
                            List<SlotDisplay> compositeContents = new ArrayList<>();
                            for (String nestedSlot : contents.split(", ")) {
                                System.out.println("Обработка вложенного слота: '" + nestedSlot + "'");
                                if (nestedSlot.startsWith("ItemSlotDisplay")) {
                                    Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nestedSlot);
                                    if (itemMatcher.find()) {
                                        String itemName = fixResourceName(itemMatcher.group(1));
                                        Item item = Registries.ITEM.get(Identifier.of("minecraft", itemName));
                                        compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                        System.out.println("Добавлен ItemSlotDisplay: " + itemName);
                                    }
                                }
                            }
                            slots.add(new SlotDisplay.CompositeSlotDisplay(compositeContents));
                        } else {
                            System.out.println("Не удалось найти contents для CompositeSlotDisplay в: " + rawSlot);
                        }
                    }
                }

                // Получаем Crafting Requirements Items
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);

                // Формируем список ингредиентов
                List<Ingredient> ingredients = new ArrayList<>();
                for (String rawItem : itemsSection.split(";")) {
                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            // Оставляем только последнее слово (после последнего "/")
                            String[] splitItem = itemName.split(":");
                            String lastWord = splitItem[splitItem.length - 1];
                            // Проверка на пустой элемент
                            Item item = Registries.ITEM.get(Identifier.of("minecraft", lastWord));
                            ingredients.add(Ingredient.ofItems(item));  // Заполняем ингредиенты на основе предметов
                        }
                    }
                }

                // Убедимся, что у нас есть width * height слотов
                int total = width * height;
                while (slots.size() < total) slots.add(new SlotDisplay.CompositeSlotDisplay(List.of()));
                if (slots.size() > total) slots = slots.subList(0, total);

                // Создаем результат и станцию
                SlotDisplay.StackSlotDisplay result = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );

                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", stationName))
                );

                // Собираем объект
                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                ShapedCraftingRecipeDisplay display = new ShapedCraftingRecipeDisplay(width, height, slots, result, station);
                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }







        } catch (Exception e) {
            System.out.println("Ошибка при парсинге строки: " + e.getMessage());
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static void sendToClient(RecipeDisplayEntry entry) {
        //System.out.println("Засылаем пакет:"+entry);
        RecipeBookAddS2CPacket.Entry packetEntry = new RecipeBookAddS2CPacket.Entry(entry, (byte) 3);
        RecipeBookAddS2CPacket packet = new RecipeBookAddS2CPacket(List.of(packetEntry), false);
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        networkHandler.onRecipeBookAdd(packet);
    }
}
