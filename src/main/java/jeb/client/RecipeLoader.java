package jeb.client;

import net.minecraft.SharedConstants;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static jeb.client.JEBClient.existingResultItems;

public class RecipeLoader {

    private static class ParsedStackResult {
        final String itemId;
        final int count;

        ParsedStackResult(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

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
        String name = "recipes_" + SharedConstants.getCurrentVersion().name() + ".txt";
        try (InputStream input = RecipeLoader.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                System.err.println("Не удалось найти файл " + name + " в ресурсах");
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
            if (line.contains("StonecutterRecipeDisplay")) {
                int index = parseRecipeIndex(line);
                OptionalInt group = parseGroup(line);
                RecipeBookCategory category = parseCategory(line);

                String inputItem = parseSingleCompositeItem(line, "input");
                if (inputItem == null) return Optional.empty();

                ParsedStackResult parsedResult = parseStackResult(line, "result");
                if (parsedResult == null) return Optional.empty();

                String stationName = parseStationItem(line);
                if (stationName == null) return Optional.empty();

                List<Ingredient> ingredients = parseIngredientsFromLine(line);
                if (ingredients == null) return Optional.empty();

                SlotDisplay.ItemSlotDisplay itemSlot = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", inputItem))
                );
                SlotDisplay.Composite inputSlot = new SlotDisplay.Composite(List.of(itemSlot));

                SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(
                        new ItemStackTemplate(
                                BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", parsedResult.itemId)),
                                parsedResult.count
                        )
                );

                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(
                        new RecipeDisplayId(index),
                        new StonecutterRecipeDisplay(inputSlot, resultSlot, stationSlot),
                        group,
                        category,
                        Optional.of(ingredients)
                );

                return Optional.of(entry);
            }

            if (line.contains("SmithingRecipeDisplay")) {
                int index = parseRecipeIndex(line);
                OptionalInt group = parseGroup(line);
                RecipeBookCategory category = parseCategory(line);

                String templateItem = parseSingleCompositeItem(line, "template");
                if (templateItem == null) templateItem = "air";

                SlotDisplay baseSlot;
                String baseCompositeItem = parseSingleCompositeItem(line, "base");
                String baseTag = parseTagId(line, "base");

                if (baseCompositeItem != null) {
                    baseSlot = new SlotDisplay.Composite(List.of(
                            new SlotDisplay.ItemSlotDisplay(
                                    BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", baseCompositeItem))
                            )
                    ));
                } else if (baseTag != null) {
                    baseSlot = new SlotDisplay.TagSlotDisplay(
                            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", baseTag))
                    );
                } else {
                    baseSlot = new SlotDisplay.ItemSlotDisplay(Items.AIR);
                }

                SlotDisplay additionSlot;
                String additionCompositeItem = parseSingleCompositeItem(line, "addition");
                String additionTag = parseTagId(line, "addition");

                if (additionCompositeItem != null) {
                    additionSlot = new SlotDisplay.Composite(List.of(
                            new SlotDisplay.ItemSlotDisplay(
                                    BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", additionCompositeItem))
                            )
                    ));
                } else if (additionTag != null) {
                    additionSlot = new SlotDisplay.TagSlotDisplay(
                            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", additionTag))
                    );
                } else {
                    additionSlot = new SlotDisplay.ItemSlotDisplay(Items.AIR);
                }

                SlotDisplay resultSlot = parseSmithingResultSlot(line);
                if (resultSlot == null) return Optional.empty();

                String stationName = parseStationItem(line);
                if (stationName == null) return Optional.empty();

                List<Ingredient> ingredients = parseIngredientsFromLine(line);
                if (ingredients == null) return Optional.empty();

                SlotDisplay templateSlot = new SlotDisplay.Composite(List.of(
                        new SlotDisplay.ItemSlotDisplay(
                                BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", templateItem))
                        )
                ));

                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                SmithingRecipeDisplay display = new SmithingRecipeDisplay(
                        templateSlot,
                        baseSlot,
                        additionSlot,
                        resultSlot,
                        stationSlot
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(
                        new RecipeDisplayId(index),
                        display,
                        group,
                        category,
                        Optional.of(ingredients)
                );

                return Optional.of(entry);
            }

            if (line.contains("FurnaceRecipeDisplay")) {
                int index = parseRecipeIndex(line);
                OptionalInt group = parseGroup(line);
                RecipeBookCategory category = parseCategory(line);

                SlotDisplay ingredientSlot;
                String ingredientTag = parseTagId(line, "ingredient");

                if (ingredientTag != null) {
                    ingredientSlot = new SlotDisplay.TagSlotDisplay(
                            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", ingredientTag))
                    );
                } else {
                    Matcher ingredientMatcher = Pattern.compile(
                            "ingredient=(?:CompositeSlotDisplay|Composite)\\[contents=\\[(.*?)]\\]"
                    ).matcher(line);

                    if (!ingredientMatcher.find()) return Optional.empty();

                    String ingredientsSection = ingredientMatcher.group(1);
                    List<SlotDisplay> ingredientSlots = new ArrayList<>();

                    Matcher itemMatcher = Pattern.compile(
                            "ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([^\\]]+)](?:=minecraft:[^}]+)?}\\]"
                    ).matcher(ingredientsSection);

                    while (itemMatcher.find()) {
                        String ingredientItem = fixResourceName(itemMatcher.group(1));
                        ingredientSlots.add(new SlotDisplay.ItemSlotDisplay(
                                BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", ingredientItem))
                        ));
                    }

                    ingredientSlot = new SlotDisplay.Composite(ingredientSlots);
                }

                Matcher fuelMatcher = Pattern.compile("fuel=<([^>]+)>").matcher(line);
                String fuel = null;
                if (fuelMatcher.find()) {
                    String fuelValue = fuelMatcher.group(1);
                    if (!"any fuel".equals(fuelValue)) {
                        fuel = fixResourceName(fuelValue);
                    }
                }

                SlotDisplay fuelSlot = fuel != null
                        ? new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", fuel))
                )
                        : SlotDisplay.AnyFuel.INSTANCE;

                ParsedStackResult parsedResult = parseStackResult(line, "result");
                if (parsedResult == null) return Optional.empty();

                String stationName = parseStationItem(line);
                if (stationName == null) return Optional.empty();

                Matcher durationMatcher = Pattern.compile("duration=(\\d+)").matcher(line);
                int duration = durationMatcher.find() ? Integer.parseInt(durationMatcher.group(1)) : 0;

                Matcher experienceMatcher = Pattern.compile("experience=(\\d+(?:\\.\\d+)?)").matcher(line);
                float experience = experienceMatcher.find() ? Float.parseFloat(experienceMatcher.group(1)) : 0.0f;

                List<Ingredient> ingredients = parseIngredientsFromLine(line);
                if (ingredients == null) return Optional.empty();

                SlotDisplay.ItemStackSlotDisplay result = new SlotDisplay.ItemStackSlotDisplay(
                        new ItemStackTemplate(
                                BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", parsedResult.itemId)),
                                parsedResult.count
                        )
                );

                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                FurnaceRecipeDisplay display = new FurnaceRecipeDisplay(
                        ingredientSlot,
                        fuelSlot,
                        result,
                        station,
                        duration,
                        experience
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(
                        new RecipeDisplayId(index),
                        display,
                        group,
                        category,
                        Optional.of(ingredients)
                );

                return Optional.of(entry);
            }

            if (line.contains("ShapelessCraftingRecipeDisplay")) {
                int index = parseRecipeIndex(line);
                OptionalInt group = parseGroup(line);
                RecipeBookCategory category = parseCategory(line);

                ParsedStackResult parsedResult = parseStackResult(line, "result");
                String resultItem;
                int resultCount = 1;
                boolean resultIsStack = false;

                if (parsedResult != null) {
                    resultItem = parsedResult.itemId;
                    resultCount = parsedResult.count;
                    resultIsStack = true;
                } else {
                    String singleResultItem = parseItemSlotField(line, "result");
                    if (singleResultItem == null) return Optional.empty();
                    resultItem = singleResultItem;
                }

                String stationName = parseStationItem(line);
                if (stationName == null) return Optional.empty();

                Matcher ingredientsMatcher = Pattern.compile("ingredients=\\[(.*)]").matcher(line);
                if (!ingredientsMatcher.find()) return Optional.empty();
                String ingredientsSection = ingredientsMatcher.group(1);

                List<SlotDisplay> slots = new ArrayList<>();
                for (String rawSlot : splitTopLevelSlotDisplays(ingredientsSection)) {
                    rawSlot = rawSlot.trim();

                    if (rawSlot.startsWith("<empty")) {
                        slots.add(SlotDisplay.Empty.INSTANCE);
                    } else if (rawSlot.startsWith("TagSlotDisplay")) {
                        String tagName = rawSlot.substring(rawSlot.indexOf("minecraft:") + "minecraft:".length(), rawSlot.indexOf("]")).trim();
                        String[] splitTag = tagName.split(":");
                        String lastWord = splitTag[splitTag.length - 1];
                        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", lastWord));
                        slots.add(new SlotDisplay.TagSlotDisplay(tagKey));
                    } else if (rawSlot.startsWith("CompositeSlotDisplay") || rawSlot.startsWith("Composite")) {
                        List<String> innerItems = extractItemSlotDisplays(rawSlot);
                        List<SlotDisplay> compositeContents = new ArrayList<>();

                        for (String nested : innerItems) {
                            nested = nested.trim();

                            if (nested.startsWith("WithRemainderSlotDisplay")) {
                                Matcher inputMatcher = Pattern.compile(
                                        "input=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([^\\]]+)](?:=minecraft:[^}]+)?}\\]"
                                ).matcher(nested);

                                ParsedStackResult remainderResult = parseStackResult(nested, "remainder");

                                if (inputMatcher.find()) {
                                    String inputItemName = fixResourceName(inputMatcher.group(1));
                                    Item inputItem = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", inputItemName));
                                    SlotDisplay.ItemSlotDisplay input = new SlotDisplay.ItemSlotDisplay(inputItem);

                                    if (remainderResult != null) {
                                        Item remainderItem = BuiltInRegistries.ITEM.getValue(
                                                Identifier.fromNamespaceAndPath("minecraft", remainderResult.itemId)
                                        );
                                        SlotDisplay.ItemStackSlotDisplay remainder = new SlotDisplay.ItemStackSlotDisplay(
                                                new ItemStackTemplate(remainderItem, remainderResult.count)
                                        );
                                        compositeContents.add(new SlotDisplay.WithRemainder(input, remainder));
                                    } else {
                                        compositeContents.add(input);
                                    }
                                }
                            } else if (nested.startsWith("ItemSlotDisplay")) {
                                String itemName = extractItemIdFromItemSlot(nested);
                                if (itemName != null) {
                                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", itemName));
                                    compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                }
                            }
                        }

                        slots.add(new SlotDisplay.Composite(compositeContents));
                    } else if (rawSlot.startsWith("ItemSlotDisplay")) {
                        String itemName = extractItemIdFromItemSlot(rawSlot);
                        if (itemName != null) {
                            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", itemName));
                            slots.add(new SlotDisplay.ItemSlotDisplay(item));
                        }
                    }
                }

                List<Ingredient> ingredients = parseIngredientsFromLine(line);
                if (ingredients == null) return Optional.empty();

                SlotDisplay resultDisplay;
                if (resultIsStack) {
                    resultDisplay = new SlotDisplay.ItemStackSlotDisplay(
                            new ItemStackTemplate(
                                    BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem)),
                                    resultCount
                            )
                    );
                } else {
                    resultDisplay = new SlotDisplay.ItemSlotDisplay(
                            BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem))
                    );
                }

                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultDisplay, station);

                RecipeDisplayEntry entry = new RecipeDisplayEntry(
                        new RecipeDisplayId(index),
                        display,
                        group,
                        category,
                        Optional.of(ingredients)
                );

                return Optional.of(entry);
            }

            if (line.contains("ShapedCraftingRecipeDisplay")) {
                int index = parseRecipeIndex(line);
                OptionalInt group = parseGroup(line);
                RecipeBookCategory category = parseCategory(line);

                Matcher dimMatcher = Pattern.compile("ShapedCraftingRecipeDisplay\\[width=(\\d+), height=(\\d+)").matcher(line);
                if (!dimMatcher.find()) return Optional.empty();
                int width = Integer.parseInt(dimMatcher.group(1));
                int height = Integer.parseInt(dimMatcher.group(2));

                ParsedStackResult parsedResult = parseStackResult(line, "result");
                String resultItem;
                int resultCount = 1;

                if (parsedResult != null) {
                    resultItem = parsedResult.itemId;
                    resultCount = parsedResult.count;
                } else {
                    String singleResultItem = parseItemSlotField(line, "result");
                    if (singleResultItem == null) return Optional.empty();
                    resultItem = singleResultItem;
                    resultCount = 1;
                }

                String stationName = parseStationItem(line);
                if (stationName == null) return Optional.empty();

                Matcher ingredientsMatcher = Pattern.compile("ingredients=\\[(.*)]").matcher(line);
                if (!ingredientsMatcher.find()) return Optional.empty();
                String ingredientsSection = ingredientsMatcher.group(1);

                List<SlotDisplay> slots = new ArrayList<>();
                for (String rawSlot : splitTopLevelSlotDisplays(ingredientsSection)) {
                    rawSlot = rawSlot.trim();

                    if (rawSlot.startsWith("<empty")) {
                        slots.add(SlotDisplay.Empty.INSTANCE);
                    } else if (rawSlot.startsWith("TagSlotDisplay")) {
                        String tagName = rawSlot.substring(rawSlot.indexOf("minecraft:") + "minecraft:".length(), rawSlot.indexOf("]")).trim();
                        String[] splitTag = tagName.split(":");
                        String lastWord = splitTag[splitTag.length - 1];
                        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", lastWord));
                        slots.add(new SlotDisplay.TagSlotDisplay(tagKey));
                    } else if (rawSlot.startsWith("CompositeSlotDisplay") || rawSlot.startsWith("Composite")) {
                        List<String> innerItems = extractItemSlotDisplays(rawSlot);
                        List<SlotDisplay> compositeContents = new ArrayList<>();

                        for (String nested : innerItems) {
                            nested = nested.trim();

                            if (nested.startsWith("WithRemainderSlotDisplay")) {
                                Matcher inputMatcher = Pattern.compile(
                                        "input=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([^\\]]+)](?:=minecraft:[^}]+)?}\\]"
                                ).matcher(nested);

                                ParsedStackResult remainderResult = parseStackResult(nested, "remainder");

                                if (inputMatcher.find()) {
                                    String inputItemName = fixResourceName(inputMatcher.group(1));
                                    Item inputItem = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", inputItemName));
                                    SlotDisplay.ItemSlotDisplay input = new SlotDisplay.ItemSlotDisplay(inputItem);

                                    if (remainderResult != null) {
                                        Item remainderItem = BuiltInRegistries.ITEM.getValue(
                                                Identifier.fromNamespaceAndPath("minecraft", remainderResult.itemId)
                                        );
                                        SlotDisplay.ItemStackSlotDisplay remainder = new SlotDisplay.ItemStackSlotDisplay(
                                                new ItemStackTemplate(remainderItem, remainderResult.count)
                                        );
                                        compositeContents.add(new SlotDisplay.WithRemainder(input, remainder));
                                    } else {
                                        compositeContents.add(input);
                                    }
                                }
                            } else if (nested.startsWith("ItemSlotDisplay")) {
                                String itemName = extractItemIdFromItemSlot(nested);
                                if (itemName != null) {
                                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", itemName));
                                    compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                }
                            }
                        }

                        slots.add(new SlotDisplay.Composite(compositeContents));
                    } else if (rawSlot.startsWith("ItemSlotDisplay")) {
                        String itemName = extractItemIdFromItemSlot(rawSlot);
                        if (itemName != null) {
                            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", itemName));
                            slots.add(new SlotDisplay.ItemSlotDisplay(item));
                        }
                    }
                }

                List<Ingredient> ingredients = parseIngredientsFromLine(line);
                if (ingredients == null) return Optional.empty();

                int total = width * height;
                while (slots.size() < total) {
                    slots.add(new SlotDisplay.Composite(List.of()));
                }
                if (slots.size() > total) {
                    slots = slots.subList(0, total);
                }

                SlotDisplay.ItemStackSlotDisplay result = new SlotDisplay.ItemStackSlotDisplay(
                        new ItemStackTemplate(
                                BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem)),
                                resultCount
                        )
                );

                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                ShapedCraftingRecipeDisplay display = new ShapedCraftingRecipeDisplay(width, height, slots, result, station);

                RecipeDisplayEntry entry = new RecipeDisplayEntry(
                        new RecipeDisplayId(index),
                        display,
                        group,
                        category,
                        Optional.of(ingredients)
                );
                return Optional.of(entry);
            }

        } catch (Exception e) {
            System.out.println("Ошибка при парсинге строки: " + e.getMessage());
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static int parseRecipeIndex(String line) {
        Matcher m = Pattern.compile("NetworkID:(?:NetworkRecipeId|RecipeDisplayId)\\[index=(\\d+)]").matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static OptionalInt parseGroup(String line) {
        Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
        if (groupMatcher.find()) {
            return OptionalInt.of(Integer.parseInt(groupMatcher.group(1)));
        }
        return OptionalInt.empty();
    }

    private static RecipeBookCategory parseCategory(String line) {
        Matcher categoryMatcher = Pattern.compile(
                "Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]"
        ).matcher(line);

        return categoryMatcher.find()
                ? BuiltInRegistries.RECIPE_BOOK_CATEGORY.getValue(
                Identifier.fromNamespaceAndPath("minecraft", categoryMatcher.group(1))
        )
                : RecipeBookCategories.CRAFTING_MISC;
    }

    private static String extractItemIdFromItemSlot(String text) {
        Matcher m = Pattern.compile(
                "ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([^\\]]+)](?:=minecraft:[^}]+)?}\\]"
        ).matcher(text);
        return m.find() ? fixResourceName(m.group(1)) : null;
    }

    private static String parseItemSlotField(String text, String fieldName) {
        Matcher m = Pattern.compile(
                fieldName + "=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([^\\]]+)](?:=minecraft:[^}]+)?}\\]"
        ).matcher(text);
        return m.find() ? fixResourceName(m.group(1)) : null;
    }

    private static String parseSingleCompositeItem(String text, String fieldName) {
        Matcher m = Pattern.compile(
                fieldName + "=(?:CompositeSlotDisplay|Composite)\\[contents=\\[(.*?)]\\]"
        ).matcher(text);
        if (!m.find()) return null;
        return extractItemIdFromItemSlot(m.group(1));
    }

    private static String parseTagId(String text, String fieldName) {
        Matcher m = Pattern.compile(
                fieldName + "=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([^\\]]+)]]"
        ).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String parseStationItem(String text) {
        Matcher m = Pattern.compile(
                "craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([^\\]]+)](?:=minecraft:[^}]+)?}\\]"
        ).matcher(text);
        return m.find() ? fixResourceName(m.group(1)) : null;
    }

    private static ParsedStackResult parseStackResult(String text, String fieldName) {
        Matcher newMatcher = Pattern.compile(
                fieldName + "=ItemStackSlotDisplay\\[stack=ItemStackTemplate\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([^\\]]+)](?:=minecraft:[^}]+)?}, count=(\\d+), components=\\{.*?}]]"
        ).matcher(text);
        if (newMatcher.find()) {
            return new ParsedStackResult(
                    fixResourceName(newMatcher.group(1)),
                    Integer.parseInt(newMatcher.group(2))
            );
        }

        Matcher oldMatcher = Pattern.compile(
                fieldName + "=StackSlotDisplay\\[stack=(\\d+) minecraft:([a-z0-9_./-]+)]"
        ).matcher(text);
        if (oldMatcher.find()) {
            return new ParsedStackResult(
                    fixResourceName(oldMatcher.group(2)),
                    Integer.parseInt(oldMatcher.group(1))
            );
        }

        return null;
    }

    private static List<Ingredient> parseIngredientsFromLine(String line) {
        Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
        if (!itemsMatcher.find()) return null;
        return parseIngredientsFromRequirements(itemsMatcher.group(1));
    }

    private static List<Ingredient> parseIngredientsFromRequirements(String itemsSection) {
        List<Ingredient> ingredients = new ArrayList<>();

        for (String rawItem : itemsSection.split(";")) {
            List<Item> alternatives = new ArrayList<>();

            for (String itemVariant : rawItem.split(",")) {
                String itemName = itemVariant.trim();
                if (!itemName.isBlank()) {
                    String[] splitItem = itemName.split(":");
                    String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                    String path = splitItem[splitItem.length - 1];

                    try {
                        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                        Item item = BuiltInRegistries.ITEM.getValue(id);
                        if (item != Items.AIR) {
                            alternatives.add(item);
                        }
                    } catch (Exception e) {
                        System.err.println("Ошибка при создании Identifier для ingredient: " + itemName);
                        e.printStackTrace();
                    }
                }
            }

            if (!alternatives.isEmpty()) {
                ingredients.add(Ingredient.of(alternatives.stream()));
            }
        }

        return ingredients;
    }

    private static SlotDisplay parseSmithingResultSlot(String line) {
        Matcher newTrimMatcher = Pattern.compile(
                "result=SmithingTrimDemoSlotDisplay\\[base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)]],\\s*" +
                        "material=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)]],\\s*" +
                        "pattern=Reference\\{ResourceKey\\[minecraft:trim_pattern / minecraft:([\\w_]+)]=TrimPattern\\[assetId=minecraft:([\\w_]+),\\s*" +
                        "description=translation\\{key='trim_pattern\\.minecraft\\.([\\w_]+)',\\s*args=\\[\\]},\\s*decal=false]}]"
        ).matcher(line);

        if (newTrimMatcher.find()) {
            String baseTag = newTrimMatcher.group(1);
            String materialTag = newTrimMatcher.group(2);
            String patternId = newTrimMatcher.group(3);

            TagKey<Item> baseTagKey = TagKey.create(
                    Registries.ITEM,
                    Identifier.fromNamespaceAndPath("minecraft", baseTag)
            );
            TagKey<Item> materialTagKey = TagKey.create(
                    Registries.ITEM,
                    Identifier.fromNamespaceAndPath("minecraft", materialTag)
            );

            Registry<TrimPattern> trimPatternRegistry = Minecraft.getInstance().level
                    .registryAccess()
                    .lookupOrThrow(Registries.TRIM_PATTERN);

            Holder<TrimPattern> patternEntry = trimPatternRegistry
                    .get(Identifier.fromNamespaceAndPath("minecraft", patternId))
                    .orElse(null);

            if (patternEntry != null) {
                return new SlotDisplay.SmithingTrimDemoSlotDisplay(
                        new SlotDisplay.TagSlotDisplay(baseTagKey),
                        new SlotDisplay.TagSlotDisplay(materialTagKey),
                        patternEntry
                );
            }
        }

        Matcher oldTrimMatcher = Pattern.compile(
                "result=SmithingTrimSlotDisplay\\[base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)]],\\s*" +
                        "material=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)]],\\s*" +
                        "pattern=Reference\\{ResourceKey\\[minecraft:trim_pattern / minecraft:([\\w_]+)]=ArmorTrimPattern\\[assetId=minecraft:([\\w_]+),\\s*" +
                        "description=translation\\{key='trim_pattern\\.minecraft\\.([\\w_]+)',\\s*args=\\[\\]},\\s*decal=false]}]"
        ).matcher(line);

        if (oldTrimMatcher.find()) {
            String baseTag = oldTrimMatcher.group(1);
            String materialTag = oldTrimMatcher.group(2);
            String patternId = oldTrimMatcher.group(3);

            TagKey<Item> baseTagKey = TagKey.create(
                    Registries.ITEM,
                    Identifier.fromNamespaceAndPath("minecraft", baseTag)
            );
            TagKey<Item> materialTagKey = TagKey.create(
                    Registries.ITEM,
                    Identifier.fromNamespaceAndPath("minecraft", materialTag)
            );

            Registry<TrimPattern> trimPatternRegistry = Minecraft.getInstance().level
                    .registryAccess()
                    .lookupOrThrow(Registries.TRIM_PATTERN);

            Holder<TrimPattern> patternEntry = trimPatternRegistry
                    .get(Identifier.fromNamespaceAndPath("minecraft", patternId))
                    .orElse(null);

            if (patternEntry != null) {
                return new SlotDisplay.SmithingTrimDemoSlotDisplay(
                        new SlotDisplay.TagSlotDisplay(baseTagKey),
                        new SlotDisplay.TagSlotDisplay(materialTagKey),
                        patternEntry
                );
            }
        }

        ParsedStackResult stackResult = parseStackResult(line, "result");
        if (stackResult != null) {
            Item item = BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("minecraft", stackResult.itemId)
            );
            return new SlotDisplay.ItemStackSlotDisplay(
                    new ItemStackTemplate(item, stackResult.count)
            );
        }

        String itemId = parseItemSlotField(line, "result");
        if (itemId != null) {
            Item item = BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("minecraft", itemId)
            );
            return new SlotDisplay.ItemSlotDisplay(item);
        }

        return null;
    }

    private static List<String> extractItemSlotDisplays(String rawSlot) {
        List<String> result = new ArrayList<>();

        Matcher contentsMatcher = Pattern.compile("contents=\\[(.*)]\\]?").matcher(rawSlot);
        if (!contentsMatcher.find()) return result;

        String contents = contentsMatcher.group(1).trim();

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

    static void sendToClient(RecipeDisplayEntry entry) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ClientRecipeBook clientRecipeBook = client.player.getRecipeBook();
            ClientPacketListener handler = client.getConnection();
            ClientLevel world = client.level;

            clientRecipeBook.add(entry);
            clientRecipeBook.rebuildCollections();
            handler.searchTrees().updateRecipes(clientRecipeBook, world);

            Screen screen = client.screen;
            if (screen instanceof RecipeUpdateListener recipeBookProvider) {
                recipeBookProvider.recipesUpdated();
            }
        }

        SlotDisplay resultSlot = entry.display().result();

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(client.level)
        );

        List<ItemStack> stacks = resultSlot.resolveForStacks(context);

        if (stacks.isEmpty()) {
            System.err.println("Warning: Empty stacks for resultSlot in recipe " + entry);
            return;
        }

        ItemStack stack = stacks.get(0);

        List<ItemStack> stationStacks = entry.display().craftingStation().resolveForStacks(context);
        if (!stationStacks.isEmpty() && stationStacks.get(0).getItem() == Items.CRAFTING_TABLE) {
            existingResultItems.add(stack.getItem());
        }
    }
}