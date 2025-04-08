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
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecipeLoader {

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

                System.out.println("Обрабатывается строка для рецепта камнереза:" + line);

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                System.out.println("индекс: " + index);

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\.empty").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.empty() : OptionalInt.empty();

                System.out.println("группа: " + group);

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                System.out.println("категория: " + category);

                // Получаем ингредиент (вход)
                Matcher inputMatcher = Pattern.compile("input=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String inputItem = inputMatcher.find() ? fixResourceName(inputMatcher.group(2)) : null;

                System.out.println("входной элемент: " + inputItem);

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                System.out.println("результат: " + resultCount + " " + resultItem);

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(2));

                System.out.println("станция: " + stationName);

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

                System.out.println("Результат обработки:" + entry);

                return Optional.of(entry);
            }


            // Извлекаем тип отображения
            if (line.contains("SmithingRecipeDisplay")) {

                System.out.println("Обрабатывается строка для кузнечного рецепта:" + line);

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                System.out.println("индекс: " + index);

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\.empty").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.empty() : OptionalInt.empty();

                System.out.println("группа: " + group);

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                System.out.println("категория: " + category);

                // Получаем ингредиенты для шаблона, базы и добавления
                Matcher templateMatcher = Pattern.compile("template=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String templateItem = templateMatcher.find() ? fixResourceName(templateMatcher.group(2)) : null;

                Matcher baseMatcher = Pattern.compile("base=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String baseItem = baseMatcher.find() ? fixResourceName(baseMatcher.group(2)) : null;

                Matcher additionMatcher = Pattern.compile("addition=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:(.*?)\\]\\]").matcher(line);
                String additionTag = additionMatcher.find() ? fixResourceName(additionMatcher.group(1)) : null;

                System.out.println("шаблон: " + templateItem);
                System.out.println("база: " + baseItem);
                System.out.println("добавление: " + additionTag);

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                System.out.println("результат: " + resultCount + " " + resultItem);

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(2));

                System.out.println("станция: " + stationName);

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
                        templateSlot, // шаблон
                        baseSlot, // база
                        additionSlot, // добавление
                        resultSlot, // результат
                        stationSlot // станция
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                System.out.println("Результат обработки:" + entry);

                return Optional.of(entry);
            }


            // Извлекаем тип отображения
            if (line.contains("FurnaceRecipeDisplay")) {

                System.out.println("Обрабатывается строка для печи:" + line);

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                System.out.println("индекс");

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.of(Integer.parseInt(groupMatcher.group(1))) : OptionalInt.empty();

                System.out.println("группа");

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                System.out.println("категория");

                // Получаем ингредиент
                Matcher ingredientMatcher = Pattern.compile("ingredient=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / (minecraft:[^\\]]+)\\]=minecraft:[^\\]]+\\}]]").matcher(line);
                if (!ingredientMatcher.find()) return Optional.empty();
                String ingredientItem = fixResourceName(ingredientMatcher.group(1));

                System.out.println("ингредиент: " + ingredientItem);


// Получаем топливо (если указано)
                Matcher fuelMatcher = Pattern.compile("fuel=<([^>]+)>").matcher(line);
                String fuel = null;
                if (fuelMatcher.find()) {
                    String fuelValue = fuelMatcher.group(1);
                    // Если топливо - это 'any fuel', то игнорируем его или устанавливаем как пустое значение
                    if (!"any fuel".equals(fuelValue)) {
                        fuel = fixResourceName(fuelValue); // обработка корректного идентификатора
                    } else {
                        fuel = null; // топливо не задано
                    }
                }

                System.out.println("топливо: " + (fuel != null ? fuel : "не задано"));

                // Если топлива нет, используем пустой слот
                SlotDisplay.AnyFuelSlotDisplay fuelSlot = null;

                fuelSlot = SlotDisplay.AnyFuelSlotDisplay.INSTANCE;
                ;
                ; // Если топлива нет, используем пустой слот

                System.out.println("топливо: " + (fuel != null ? fuel : "нет"));

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                System.out.println("результат");

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(1));

                System.out.println("станция");

                // Получаем время работы и опыт
                Matcher durationMatcher = Pattern.compile("duration=(\\d+)").matcher(line);
                int duration = durationMatcher.find() ? Integer.parseInt(durationMatcher.group(1)) : 0;

                Matcher experienceMatcher = Pattern.compile("experience=(\\d+\\.\\d+)").matcher(line);
                float experience = experienceMatcher.find() ? Float.parseFloat(experienceMatcher.group(1)) : 0.0f;

                System.out.println("время и опыт");

                // Получаем список ингредиентов (расшифрованный)
                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) return Optional.empty();
                String itemsSection = itemsMatcher.group(1);

                List<Ingredient> ingredients = new ArrayList<>();

                // Каждая строка — один слот (с множеством вариантов)
                List<SlotDisplay> slotVariants = null;
                for (String rawSlot : itemsSection.split(";")) {
                    String[] variants = rawSlot.split(",");

                    slotVariants = new ArrayList<>();
                    List<Item> ingredientItems = new ArrayList<>();
                    for (String variant : variants) {
                        variant = fixResourceName(variant.trim());
                        Item item = Registries.ITEM.get(Identifier.of("minecraft", variant));
                        slotVariants.add(new SlotDisplay.ItemSlotDisplay(item));
                        ingredientItems.add(item);
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

                // Теперь мы передаем все необходимые параметры
                FurnaceRecipeDisplay display = new FurnaceRecipeDisplay(
                        new SlotDisplay.CompositeSlotDisplay(slotVariants), // ingredien)
                        fuelSlot, // fuel slot
                        result, // result
                        station, // station
                        duration, // duration
                        experience // experience
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                System.out.println("Результат обработки:" + entry);

                return Optional.of(entry);
            }




            // Извлекаем тип отображения
            if (line.contains("ShapelessCraftingRecipeDisplay")) {

                System.out.println("Обрабатывается строка:" + line);

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                System.out.println("индекс");

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.of(Integer.parseInt(groupMatcher.group(1))) : OptionalInt.empty();

                System.out.println("группа");

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                System.out.println("категория");

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                System.out.println("результат");

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(1));

                System.out.println("станция");

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

                // Создаем результат и станцию
                SlotDisplay.StackSlotDisplay result = new SlotDisplay.StackSlotDisplay(
                        new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", resultItem)), resultCount)
                );

                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        Registries.ITEM.get(Identifier.of("minecraft", stationName))
                );

                // Собираем объект
                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, result, station);
                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                System.out.println("Результат обработки:" + entry);

                return Optional.of(entry);
            }



            // Извлекаем тип отображения
            if (line.contains("ShapedCraftingRecipeDisplay")) {

                System.out.println("Обрабатывается строка:" + line);

                // Получаем индекс
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                System.out.println("индекс");

                // Получаем группу (если есть)
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                OptionalInt group = groupMatcher.find() ? OptionalInt.of(Integer.parseInt(groupMatcher.group(1))) : OptionalInt.empty();

                System.out.println("группа");

                // Получаем категорию
                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                System.out.println("категория");

                // Получаем ширину и высоту
                Matcher dimMatcher = Pattern.compile("ShapedCraftingRecipeDisplay\\[width=(\\d+), height=(\\d+)").matcher(line);
                if (!dimMatcher.find()) return Optional.empty();
                int width = Integer.parseInt(dimMatcher.group(1));
                int height = Integer.parseInt(dimMatcher.group(2));

                System.out.println("ширина-высота");

                // Получаем результат
                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) return Optional.empty();
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                System.out.println("результат");

                // Получаем станцию
                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(1));

                System.out.println("станция");

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

                // Убедимся, что у нас есть width * height слотов
                int total = width * height;
                while (slots.size() < total) slots.add(new SlotDisplay.CompositeSlotDisplay(List.of()));
                if (slots.size() > total) slots = slots.subList(0, total);

                System.out.println("ингридиенты");

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

                System.out.println("Результат обработки:" + entry);

                return Optional.of(entry);
            }



        } catch (Exception e) {
            System.out.println("Ошибка при парсинге строки: " + e.getMessage());
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static void sendToClient(RecipeDisplayEntry entry) {
        System.out.println("Засылаем пакет:"+entry);
        RecipeBookAddS2CPacket.Entry packetEntry = new RecipeBookAddS2CPacket.Entry(entry, (byte) 3);
        RecipeBookAddS2CPacket packet = new RecipeBookAddS2CPacket(List.of(packetEntry), false);
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        networkHandler.onRecipeBookAdd(packet);
    }
}
