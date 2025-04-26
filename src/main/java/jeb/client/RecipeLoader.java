package jeb.client;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
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
import net.minecraft.util.context.ContextParameterMap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mojang.datafixers.TypeRewriteRule.orElse;
import static jeb.client.JEBClient.existingResultItems;

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
        String name = "recipes_" + SharedConstants.getGameVersion().getName() + ".txt";
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


        //this fragment I will publish after a couple of months//


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

    static void sendToClient(RecipeDisplayEntry entry) {
        //System.out.println("Засылаем пакет:"+entry);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {

            ClientRecipeBook clientRecipeBook = client.player.getRecipeBook();
            ClientPlayNetworkHandler handler = client.getNetworkHandler();
            ClientWorld world = client.world;

            clientRecipeBook.add(entry);
            clientRecipeBook.refresh();
            handler.getSearchManager().addRecipeOutputReloader(clientRecipeBook, world);
            Screen var3 = client.currentScreen;
            if (var3 instanceof RecipeBookProvider recipeBookProvider) {
                recipeBookProvider.refreshRecipeBook();
            }



        }


        SlotDisplay resultSlot = entry.display().result();

        ContextParameterMap context = SlotDisplayContexts.createParameters(
                Objects.requireNonNull(client.world)
        );

        List<ItemStack> stacks = resultSlot.getStacks(context);


        ItemStack stack = stacks.get(0);

        // Добавляем в Set
        if(entry.display().craftingStation().getStacks(context).getFirst().getItem() == Items.CRAFTING_TABLE) {
            existingResultItems.add(stack.getItem());
        }

        /*RecipeBookAddS2CPacket.Entry packetEntry = new RecipeBookAddS2CPacket.Entry(entry, (byte) 3);
        RecipeBookAddS2CPacket packet = new RecipeBookAddS2CPacket(List.of(packetEntry), false);
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        networkHandler.onRecipeBookAdd(packet);*/
    }
}
