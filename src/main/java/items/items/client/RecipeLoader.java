package items.items.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.display.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
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

import static com.mojang.datafixers.TypeRewriteRule.orElse;

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

                // Обработка template как CompositeSlotDisplay
                Matcher templateMatcher = Pattern.compile("template=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String templateItem = templateMatcher.find() ? fixResourceName(templateMatcher.group(2)) : "air";

                String baseItem = null;
                boolean isBaseTag = false;

                Matcher baseMatcher = Pattern.compile(
                        "base=(?:CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:[^\\]]+\\]=minecraft:([^}\\]]+)\\}\\]\\]\\]|TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([^\\]]+)\\]\\])"
                ).matcher(line);

                if (baseMatcher.find()) {
                    if (baseMatcher.group(1) != null) {
                        baseItem = fixResourceName(baseMatcher.group(1)); // это предмет
                        isBaseTag = true;
                    } else if (baseMatcher.group(2) != null) {
                        baseItem = baseMatcher.group(2); // это тег
                        isBaseTag = false;
                    }
                }

                if (baseItem == null) {
                    baseItem = "air";
                    isBaseTag = false;
                }

// Обработка addition как тега или предмета
                String additionTag = null;
                Matcher additionMatcher = Pattern.compile(
                        "addition=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:(.*?)\\]\\]"


                ).matcher(line);

                if (additionMatcher.find()) {
                    if (additionMatcher.group(1) != null) {
                        if (additionMatcher.group(1).contains("TagSlotDisplay")) {
                            additionTag = additionMatcher.group(1); // Это тег
                        } else {
                            additionTag = fixResourceName(additionMatcher.group(1)); // Это обычный предмет
                        }
                    } else if (additionMatcher.group(3) != null) {
                        if (additionMatcher.group(3).contains("TagSlotDisplay")) {
                            additionTag = additionMatcher.group(3); // Это тег
                        } else {
                            additionTag = fixResourceName(additionMatcher.group(3)); // Это обычный предмет
                        }
                    }
                }
                if (additionTag == null) additionTag = "air";


                SlotDisplay resultSlot = null;

// Пытаемся распарсить как SmithingTrimSlotDisplay
                Pattern trimPattern = Pattern.compile(
                        "result=SmithingTrimSlotDisplay\\[base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\],\\s*" +
                                "material=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\],\\s*" +
                                "pattern=Reference\\{ResourceKey\\[minecraft:trim_pattern / minecraft:([\\w_]+)\\]=ArmorTrimPattern\\[assetId=minecraft:([\\w_]+),\\s*" +
                                "description=translation\\{key='trim_pattern\\.minecraft\\.([\\w_]+)',\\s*args=\\[\\]\\},\\s*decal=false\\]\\}\\]"
                );
                Matcher trimResultMatcher = trimPattern.matcher(line);

                if (trimResultMatcher.find()) {
                    String baseTag = trimResultMatcher.group(1);
                    String materialTag = trimResultMatcher.group(2);
                    String patternId = trimResultMatcher.group(3);

                    TagKey<Item> baseTagKey = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", baseTag));
                    TagKey<Item> materialTagKey = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", materialTag));

                    Registry<ArmorTrimPattern> trimPatternRegistry = MinecraftClient.getInstance().world
                            .getRegistryManager()
                            .getOrThrow(RegistryKeys.TRIM_PATTERN);
                    RegistryEntry<ArmorTrimPattern> patternEntry = trimPatternRegistry
                            .getEntry(Identifier.of("minecraft", patternId))
                            .orElse(null);

                    if (patternEntry != null) {
                        resultSlot = new SlotDisplay.SmithingTrimSlotDisplay(
                                new SlotDisplay.TagSlotDisplay(baseTagKey),
                                new SlotDisplay.TagSlotDisplay(materialTagKey),
                                patternEntry
                        );
                    }
                }

// Если не удалось — пытаемся как StackSlotDisplay
                if (resultSlot == null) {
                    Pattern stackPattern = Pattern.compile("result=StackSlotDisplay\\[stack=\\d+ minecraft:([\\w_]+)]");
                    Matcher stackMatcher = stackPattern.matcher(line);
                    if (stackMatcher.find()) {
                        String itemId = fixResourceName(stackMatcher.group(1));
                        Item item = Registries.ITEM.get(Identifier.of("minecraft", itemId));
                        resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item));
                    }
                }

                if (resultSlot == null) return Optional.empty();

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) return Optional.empty();
                String stationName = fixResourceName(stationMatcher.group(2));

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

                // Для base и addition создаем SlotDisplay с учетом того, что они могут быть TagSlotDisplay или CompositeSlotDisplay
                SlotDisplay baseSlot = createSlotDisplay(baseItem, isBaseTag);
                SlotDisplay additionSlot = createSlotDisplay(additionTag, false);
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", stationName)));

                // Формируем рецепт с учетом base и addition как TagSlotDisplay или CompositeSlotDisplay
                NetworkRecipeId recipeId = new NetworkRecipeId(index);
                SmithingRecipeDisplay display = new SmithingRecipeDisplay(
                        new SlotDisplay.CompositeSlotDisplay(
                                Arrays.asList(new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", templateItem))))
                        ),
                        baseSlot,
                        additionSlot,
                        resultSlot,
                        stationSlot
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
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index\\s*=\\s*(\\d+)\\]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    int groupValue = Integer.parseInt(groupMatcher.group(1));
                    group = OptionalInt.of(groupValue);
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? Registries.RECIPE_BOOK_CATEGORY.get(Identifier.of("minecraft", categoryMatcher.group(1)))
                        : RecipeBookCategories.CRAFTING_MISC;

                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                String resultItem = null;
                int resultCount = 1; // По умолчанию результат - 1 экземпляр предмета

                if (!resultMatcher.find()) {
                    resultMatcher = Pattern.compile("result=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(\\w+)]=minecraft:(\\w+)}\\]").matcher(line);
                    if (!resultMatcher.find()) return Optional.empty();
                }

                if (resultMatcher.group(1) != null && resultMatcher.group(2) != null) {
                    try {
                        resultCount = Integer.parseInt(resultMatcher.group(1)); // Преобразуем только если это число
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

                // Пример: Извлечение слотов
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
                            nested = nested.trim();

                            if (nested.startsWith("WithRemainderSlotDisplay")) {
                                // Обрабатываем WithRemainderSlotDisplay
                                Matcher inputMatcher = Pattern.compile("input=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                Matcher remainderMatcher = Pattern.compile("remainder=StackSlotDisplay\\[stack=(\\d+) minecraft:([a-z0-9_]+)]").matcher(nested);

                                if (inputMatcher.find()) {
                                    String inputItemName = fixResourceName(inputMatcher.group(1));
                                    Item inputItem = Registries.ITEM.get(Identifier.of("minecraft", inputItemName));
                                    SlotDisplay.ItemSlotDisplay input = new SlotDisplay.ItemSlotDisplay(inputItem);

                                    if (remainderMatcher.find()) {
                                        int count = Integer.parseInt(remainderMatcher.group(1));
                                        String remainderItemName = fixResourceName(remainderMatcher.group(2));
                                        Item remainderItem = Registries.ITEM.get(Identifier.of("minecraft", remainderItemName));
                                        SlotDisplay.StackSlotDisplay remainder = new SlotDisplay.StackSlotDisplay(new ItemStack(remainderItem, count));

                                        compositeContents.add(new SlotDisplay.WithRemainderSlotDisplay(input, remainder));
                                    } else {
                                        // fallback: если вдруг нет remainder — только input
                                        compositeContents.add(input);
                                    }
                                }

                            } else if (nested.startsWith("ItemSlotDisplay")) {
                                Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                if (itemMatcher.find()) {
                                    String itemName = fixResourceName(itemMatcher.group(1));
                                    Item item = Registries.ITEM.get(Identifier.of("minecraft", itemName));
                                    compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                }
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
                }                // Получаем Crafting Requirements Items
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
                            nested = nested.trim();

                            if (nested.startsWith("WithRemainderSlotDisplay")) {
                                Matcher inputMatcher = Pattern.compile("input=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                Matcher remainderMatcher = Pattern.compile("remainder=StackSlotDisplay\\[stack=(\\d+) minecraft:([a-z0-9_]+)]").matcher(nested);

                                if (inputMatcher.find()) {
                                    String inputItemName = fixResourceName(inputMatcher.group(1));
                                    Item inputItem = Registries.ITEM.get(Identifier.of("minecraft", inputItemName));
                                    SlotDisplay.ItemSlotDisplay input = new SlotDisplay.ItemSlotDisplay(inputItem);

                                    if (remainderMatcher.find()) {
                                        int count = Integer.parseInt(remainderMatcher.group(1));
                                        String remainderItemName = fixResourceName(remainderMatcher.group(2));
                                        Item remainderItem = Registries.ITEM.get(Identifier.of("minecraft", remainderItemName));
                                        SlotDisplay.StackSlotDisplay remainder = new SlotDisplay.StackSlotDisplay(new ItemStack(remainderItem, count));

                                        compositeContents.add(new SlotDisplay.WithRemainderSlotDisplay(input, remainder));
                                    } else {
                                        // fallback: если вдруг нет remainder — только input
                                        compositeContents.add(input);
                                    }
                                }

                            } else if (nested.startsWith("ItemSlotDisplay")) {
                                Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                if (itemMatcher.find()) {
                                    String itemName = fixResourceName(itemMatcher.group(1));
                                    Item item = Registries.ITEM.get(Identifier.of("minecraft", itemName));
                                    compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                }
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



    // Метод для создания SlotDisplay в зависимости от типа (CompositeSlotDisplay или TagSlotDisplay)
    private static SlotDisplay createSlotDisplay(String itemOrTag, boolean isBase) {
        if (itemOrTag.equals("air")) {
            return new SlotDisplay.ItemSlotDisplay(Items.AIR);
        }

        // Если это ItemSlotDisplay, используем CompositeSlotDisplay или TagSlotDisplay в зависимости от isBase
        if (isBase) {
            // Если base это CompositeSlotDisplay, то возвращаем CompositeSlotDisplay
            return new SlotDisplay.CompositeSlotDisplay(
                    Arrays.asList(new SlotDisplay.ItemSlotDisplay(Registries.ITEM.get(Identifier.of("minecraft", itemOrTag))))
            );
        } else {
            // Если addition это TagSlotDisplay, то возвращаем TagSlotDisplay
            return new SlotDisplay.TagSlotDisplay(
                    TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", itemOrTag))
            );
        }
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
