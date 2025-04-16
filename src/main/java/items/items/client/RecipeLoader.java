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
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public static void loadRecipesFromLog() throws IOException {
        try (InputStream input = RecipeLoader.class.getClassLoader().getResourceAsStream("recipes_output.txt")) {
            if (input == null) {
                System.err.println("Не удалось найти файл recipes_output.txt в ресурсах");
                return;
            }

            List<String> lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());

            for (String line : lines) {
                if (line.startsWith("Display:")) {
                    Optional<RecipeDisplayEntry> recipeEntry = parseLineToRecipeEntry(line);
                    recipeEntry.ifPresent(RecipeLoader::sendToClient);
                }
            }
        }
    }


    private static Optional<RecipeDisplayEntry> parseLineToRecipeEntry(String line) {
        try {


            // Извлекаем тип отображения
            if (line.contains("StonecutterRecipeDisplay")) {

                // Извлекаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                // Извлекаем группу (может быть OptionalInt.empty или OptionalInt[значение])
                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    group = OptionalInt.of(Integer.parseInt(groupMatcher.group(1)));
                }

                // Извлекаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                // Извлекаем ингредиент (CompositeSlotDisplay > ItemSlotDisplay)
                Matcher inputMatcher = Pattern.compile(
                        "input=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)]=minecraft:(.*?)\\}\\]\\]]"
                ).matcher(line);
                if (!inputMatcher.find()) return Optional.empty();
                String inputItem = fixResourceName(inputMatcher.group(2));

                // Извлекаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                // Извлекаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(2));

                // Извлекаем Crafting Requirements Items
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>();

                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.of(namespace, path);
                            Item item = Registries.ITEM.get(id);
                            if (item != Items.AIR) {
                                alternatives.add(item);
                            }
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.ofItems(alternatives.stream()));
                    }
                }

                // Собираем слоты
                SlotDisplay.ItemSlotDisplay itemSlot = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", inputItem))
                );
                SlotDisplay.CompositeSlotDisplay inputSlot = new SlotDisplay.CompositeSlotDisplay(List.of(itemSlot));

                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );

                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", stationName))
                );

                // Собираем объект рецепта
                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                StonecutterRecipeDisplay display = new StonecutterRecipeDisplay(
                        inputSlot,
                        resultSlot,
                        stationSlot
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(
                        recipeId,
                        display,
                        group,
                        category,
                        Optional.of(ingredients)
                );

                return Optional.of(entry);
            }


// Извлекаем тип отображения
            if (line.contains("SmithingRecipeDisplay")) {

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                // Получаем группу (если есть)
                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    int groupValue = Integer.parseInt(groupMatcher.group(1));
                    group = OptionalInt.of(groupValue);
                }

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                // Получаем ингредиенты для шаблона, базы и добавления
                Matcher templateMatcher = Pattern.compile("template=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String templateItem = templateMatcher.find() ? fixResourceName(templateMatcher.group(2)) : null;

                // Получаем базовый элемент (base)
                String baseItem = null;
                Matcher baseMatcher = Pattern.compile("base=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                if (baseMatcher.find()) {
                    baseItem = fixResourceName(baseMatcher.group(2));
                } else {
                    // Если не нашли в CompositeSlotDisplay, проверяем для TagSlotDisplay
                    Matcher tagBaseMatcher = Pattern.compile("base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:(.*?)\\]\\]").matcher(line);
                    baseItem = tagBaseMatcher.find() ? fixResourceName(tagBaseMatcher.group(1)) : null;
                }

                // Если baseItem все еще null, значит нет базового элемента, пропускаем его
                if (baseItem == null) {
                    baseItem = "minecraft:air"; // или можно поставить null, если не хотите добавлять базовый элемент
                }

                // Получаем добавление (addition)
                String additionTag = null;
                Matcher additionMatcher = Pattern.compile("addition=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:(.*?)\\]\\]").matcher(line);
                additionTag = additionMatcher.find() ? fixResourceName(additionMatcher.group(1)) : "minecraft:air"; // аналогично baseItem

                // Получаем результат (результат может быть как StackSlotDisplay, так и SmithingTrimSlotDisplay)
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                Matcher trimResultMatcher = Pattern.compile("result=SmithingTrimSlotDisplay\\[base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\],.*?material=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\]").matcher(line);

                String resultItem = null;
                int resultCount = 1; // По умолчанию результат = 1

                if (trimResultMatcher.find()) {
                    resultItem = trimResultMatcher.group(1);  // Извлекаем item из SmithingTrimSlotDisplay
                    System.out.println("resultItem (SmithingTrimSlotDisplay): " + resultItem);
                }
                else if (resultMatcher.find()) {
                    resultCount = Integer.parseInt(resultMatcher.group(1));
                    resultItem = fixResourceName(resultMatcher.group(2));
                } else {
                    resultItem = "minecraft:air"; // если не найден результат, ставим "air"
                }

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(2));

                // Получаем Crafting Requirements Items
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();

                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>(); // <- варианты для одного ингредиента

                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.of(namespace, path);
                            Item item = Registries.ITEM.get(id);
                            if (item != Items.AIR) { // для надёжности
                                alternatives.add(item);
                            }
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.ofItems(alternatives.stream()));
                    }
                }

                // Собираем объект рецепта
                SlotDisplay.ItemSlotDisplay templateSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", templateItem)));
                SlotDisplay.ItemSlotDisplay baseSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", baseItem)));
                SlotDisplay.ItemSlotDisplay additionSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", additionTag)));
                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", stationName)));

                // Собираем объект рецепта
                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                SmithingRecipeDisplay display = new SmithingRecipeDisplay(
                        templateSlot, baseSlot, additionSlot, resultSlot, stationSlot
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }


            if (line.contains("FurnaceRecipeDisplay")) {

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

// Получаем группу (если есть)
                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    int groupValue = Integer.parseInt(groupMatcher.group(1));
                    group = OptionalInt.of(groupValue);
                }


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

// Получаем Crafting Requirements Items
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();

                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>(); // <- варианты для одного ингредиента

                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.of(namespace, path);
                            Item item = Registries.ITEM.get(id);
                            if (item != Items.AIR) { // для надёжности
                                alternatives.add(item);
                            }
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.ofItems(alternatives.stream()));
                    }
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
                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    int groupValue = Integer.parseInt(groupMatcher.group(1));
                    group = OptionalInt.of(groupValue);
                }


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

                // Получаем Crafting Requirements Items
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();

                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>(); // <- варианты для одного ингредиента

                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.of(namespace, path);
                            Item item = Registries.ITEM.get(id);
                            if (item != Items.AIR) { // для надёжности
                                alternatives.add(item);
                            }
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.ofItems(alternatives.stream()));
                    }
                }

                SlotDisplay resultDisplay;
                if (line.contains("result=StackSlotDisplay")) {
                    resultDisplay = new SlotDisplay.StackSlotDisplay(
                            new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                    );
                } else {
                    resultDisplay = new SlotDisplay.ItemSlotDisplay(
                            Registries.ITEM.get(Identifier.of("minecraft", resultItem))
                    );
                }


                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", stationName))
                );

                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultDisplay, station);

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }





// Извлекаем тип отображения
            if (line.contains("ShapedCraftingRecipeDisplay")) {
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    group = OptionalInt.of(Integer.parseInt(groupMatcher.group(1)));
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                Matcher dimMatcher = Pattern.compile("ShapedCraftingRecipeDisplay\\[width=(\\d+), height=(\\d+)").matcher(line);
                if (!dimMatcher.find()) return Optional.empty();
                int width = Integer.parseInt(dimMatcher.group(1));
                int height = Integer.parseInt(dimMatcher.group(2));

                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                String resultItem = null;
                int resultCount = 1;
                if (!resultMatcher.find()) {
                    resultMatcher = Pattern.compile("result=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(\\w+)]=minecraft:(\\w+)}\\]").matcher(line);
                    if (!resultMatcher.find()) return Optional.empty();
                }
                if (resultMatcher.group(1) != null && resultMatcher.group(2) != null) {
                    try {
                        resultCount = Integer.parseInt(resultMatcher.group(1));
                    } catch (NumberFormatException e) {
                        resultCount = 1;
                    }
                    resultItem = fixResourceName(resultMatcher.group(2));
                } else if (resultMatcher.group(2) != null) {
                    resultItem = fixResourceName(resultMatcher.group(2));
                    resultCount = 1;
                }

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(1));

                Matcher ingredientsMatcher = Pattern.compile("ingredients=\\[(.*)]").matcher(line);
                if (!ingredientsMatcher.find()) return Optional.empty();
                String ingredientsSection = ingredientsMatcher.group(1);

                List<SlotDisplay> slots = new ArrayList<>();
                for (String rawSlot : splitTopLevelSlotDisplays(ingredientsSection)) {
                    rawSlot = rawSlot.trim();

                    if (rawSlot.startsWith("<empty")) {
                        slots.add(SlotDisplay.EmptySlotDisplay.INSTANCE);
                    } else if (rawSlot.startsWith("TagSlotDisplay")) {
                        String tagName = rawSlot.substring(rawSlot.indexOf("minecraft:") + "minecraft:".length(), rawSlot.indexOf("]")).trim();
                        String[] splitTag = tagName.split(":");
                        String lastWord = splitTag[splitTag.length - 1];
                        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", lastWord));
                        slots.add(new SlotDisplay.TagSlotDisplay(tagKey));
                    } else if (rawSlot.startsWith("CompositeSlotDisplay")) {
                        List<String> innerItems = extractItemSlotDisplays(rawSlot);
                        List<SlotDisplay> compositeContents = new ArrayList<>();

                        for (String nested : innerItems) {
                            Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([a-z0-9_]+:[a-z0-9_/]+)").matcher(nested);
                            if (itemMatcher.find()) {
                                String itemName = fixResourceName(itemMatcher.group(1));
                                Item item = Registries.ITEM.get(Identifier.of("minecraft", itemName));
                                compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                            }
                        }

                        slots.add(new SlotDisplay.CompositeSlotDisplay(compositeContents));
                    } else if (rawSlot.startsWith("ItemSlotDisplay")) {
                        Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(rawSlot);
                        if (itemMatcher.find()) {
                            String itemName = fixResourceName(itemMatcher.group(1));
                            Item item = Registries.ITEM.get(Identifier.of("minecraft", itemName));
                            slots.add(new SlotDisplay.ItemSlotDisplay(item));
                        }
                    }
                }

                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>();
                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.of(namespace, path);
                            Item item = Registries.ITEM.get(id);
                            if (item != Items.AIR) {
                                alternatives.add(item);
                            }
                        }
                    }
                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.ofItems(alternatives.stream()));
                    }
                }

                int total = width * height;
                while (slots.size() < total) {
                    slots.add(new SlotDisplay.CompositeSlotDisplay(List.of()));
                }
                if (slots.size() > total) {
                    slots = slots.subList(0, total);
                }

                SlotDisplay.StackSlotDisplay result = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );
                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", stationName))
                );

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

    private static List<String> extractItemSlotDisplays(String rawSlot) {
        List<String> result = new ArrayList<>();

        // Находим всю строку внутри contents=[...]
        Matcher contentsMatcher = Pattern.compile("contents=\\[(.*)]\\]?").matcher(rawSlot);
        if (!contentsMatcher.find()) return result;

        String contents = contentsMatcher.group(1).trim();

        // Парсим вложенные элементы с учётом скобок
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < contents.length(); i++) {
            char c = contents.charAt(i);

            if (c == '[') depth++;
            if (c == ']') depth--;

            current.append(c);

            if ((c == ',' && depth == 0) || i == contents.length() - 1) {
                String part = current.toString().trim();
                if (!part.isEmpty() && !part.equals(",")) {
                    result.add(part.replaceAll(",$", ""));
                }
                current.setLength(0);
            }
        }

        return result;
    }


    private static List<String> splitTopLevelSlotDisplays(String input) {
        List<String> result = new ArrayList<>();
        int bracketLevel = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '[') bracketLevel++;
            else if (c == ']') bracketLevel--;

            if (c == ',' && bracketLevel == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (!current.toString().isBlank()) {
            result.add(current.toString().trim());
        }

        return result;
    }




    private static void sendToClient(RecipeDisplayEntry entry) {
        //System.out.println("Засылаем пакет:"+entry);

        //MinecraftClient client = MinecraftClient.getInstance();
        //if (client.player != null) {
        //    client.player.getRecipeBook().add(entry);
       // }

        RecipeBookAddS2CPacket.Entry packetEntry = new RecipeBookAddS2CPacket.Entry(entry, (byte) 3);
        RecipeBookAddS2CPacket packet = new RecipeBookAddS2CPacket(List.of(packetEntry), false);
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        networkHandler.onRecipeBookAdd(packet);
    }
}
