package jeb.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.lwjgl.glfw.GLFW;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.client.resources.language.I18n.get;

public class JEBClient implements ClientModInitializer {

    public static boolean customToggleEnabled = true;

    private static final Path CONFIG_PATH = Paths.get(
            Minecraft.getInstance().gameDirectory.getAbsolutePath(),
            "config", "JEB.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    public static final Logger LOGGER = LoggerFactory.getLogger("JEB");


    private static KeyMapping keyBinding;
    public static KeyMapping keyBinding2;

    public static Set<Item> existingResultItems = new HashSet<>();

    public static Set<Item> nonexistingResultItems = new HashSet<>();

    public static String string = "-";
    public static List<RecipeCollection> filtered = new ArrayList<>();
    public static List<RecipeCollection> emptysearch = new ArrayList<>();

    public static boolean recipesLoaded = false;

    public static List<RecipeCollection> PREGENERATED_RECIPES = generateCustomRecipeList("");

    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> result = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();

        String query = "";
        String modName = null;
        if (filter.startsWith("@")) {
            int endIndex = filter.indexOf(" ");
            if (endIndex != -1) {
                modName = filter.substring(1, endIndex).trim();
                query = filter.substring(endIndex + 1).toLowerCase(Locale.ROOT);
            } else {
                modName = filter.substring(1).trim();
            }
        } else {
            query = filter.toLowerCase(Locale.ROOT);
        }

        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;

            // Проверка на мод
            if (modName != null && !modName.isEmpty() && !BuiltInRegistries.ITEM.getKey(item).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }

            // Проверка совпадения по имени, id или ключу
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String idString = item.toString().toLowerCase(Locale.ROOT);
            String key = get(item.getDescriptionId()).toLowerCase(Locale.ROOT);

            boolean matches = query.isEmpty()
                    || name.contains(query)
                    || idString.contains(query)
                    || key.contains(query);

            // Проверка по тултипам, если не нашли раньше
            if (!matches && query.length() >= 3 && client.level != null) {
                try {
                    HolderLookup.Provider lookup = client.level.registryAccess();
                    Item.TooltipContext tooltipContext = Item.TooltipContext.of(lookup);
                    TooltipFlag tooltipType = TooltipFlag.Default.NORMAL;
                    List<Component> tooltip = item.getDefaultInstance().getTooltipLines(tooltipContext, client.player, tooltipType);
                    for (Component line : tooltip) {
                        String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)) {
                            matches = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!matches) continue;

            result.add(createDummyResultCollection(item));
        }

        return result;
    }

    // Выносим генерацию фейковой коллекции в отдельный метод для компактности:
    private static RecipeCollection createDummyResultCollection(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        RecipeDisplayId recipeId = new RecipeDisplayId(9999);

        List<SlotDisplay> slots = List.of(
                new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, id))
        );
        SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(new ItemStack(item, 1));
        SlotDisplay.ItemSlotDisplay stationSlot =
                new SlotDisplay.ItemSlotDisplay(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "crafting_table")));

        List<Ingredient> ingredients = List.of(Ingredient.of(item));

        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

        RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
        return new RecipeCollection(List.of(entry));
    }



    private boolean waitingForR = false; // Добавляем флаг для проверки, нужно ли устанавливать экран

    //int getCraftingStationId() {
    //    Identifier id = Registries.ITEM.getId(Items.CRAFTING_TABLE);
    //    return NetworkRecipeIdEncoder.encode(id);
    //}

    public static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("customToggleEnabled")) {
                        customToggleEnabled = json.get("customToggleEnabled").getAsBoolean();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("customToggleEnabled", customToggleEnabled);

            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // id категории
    public static final Identifier JEB_CATEGORY_ID = Identifier.fromNamespaceAndPath("jeb", "key_category");

    // сама категория
    private static final KeyMapping.Category JEB_CATEGORY =
            KeyMapping.Category.register(JEB_CATEGORY_ID);

    @Override
    public void onInitializeClient() {

        loadConfig();
        Runtime.getRuntime().addShutdownHook(new Thread(JEBClient::saveConfig));

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.jeb.optional_recipes_loading_screen", // The translation key of the keybinding's name
                InputConstants.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_APOSTROPHE, // The keycode of the key
                JEB_CATEGORY // The translation key of the keybinding's category.
        ));

        keyBinding2 = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.jeb.add_remove_favorite_recipes", // The translation key of the keybinding's name
                InputConstants.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_A, // The keycode of the key
                JEB_CATEGORY // The translation key of the keybinding's category.
        ));


        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            recipesLoaded = false;
            //existingResultItems = new HashSet<>();
            //nonexistingResultItems = new HashSet<>();
            existingResultItems.clear();
            nonexistingResultItems.clear();
            string = "-";
            emptysearch.clear();
        });


        /*ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            //MyCache.cacheItemsOnce();


            int knownRecipeCount = 0;

            ClientRecipeBook recipeBook = null;

            int craftingStationId = 0;

            //if (client.player != null) {
                recipeBook = client.player.getRecipeBook();
                List<RecipeResultCollection> recipes = recipeBook.getOrderedResults();


                // Проходим по всем коллекциям рецептов
                for (RecipeResultCollection collection : recipes) {
                    List<RecipeDisplayEntry> entries = collection.getAllRecipes();

                    // Преобразуем в строку и выводим подробности для каждого рецепта
                    for (RecipeDisplayEntry entry : entries) {


                        SlotDisplay resultSlot = entry.display().result();

                        ContextParameterMap context = SlotDisplayContexts.createParameters(
                                Objects.requireNonNull(client.world)
                        );

                        List<ItemStack> stacks = resultSlot.getStacks(context);


                        ItemStack stack = stacks.getFirst();


                        if (stack.getItem() == Items.CRAFTING_TABLE) craftingStationId=entry.id().index();


                        knownRecipeCount++;

                    }

                }
           // }

            if (knownRecipeCount < 1358 && craftingStationId == 259) {

                try {
                    RecipeLoader.loadRecipesFromLog();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

        });*/

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            /*RecipeListScreen recipeListScreen = new RecipeListScreen();

            if (client.player != null && !RecipeListScreen.sent) {
                try {
                    RecipeListScreen.sent = true;
                    recipeListScreen.loadAllRecipes();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }*/

            while (keyBinding.consumeClick()) {

                client.setScreen(new RecipeListScreen());

                /*RecipeListScreen recipeListScreen = new RecipeListScreen();

                try {
                    RecipeListScreen.sent = true;
                    recipeListScreen.loadAllRecipes();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }*/

            }

            /*if (waitingForR) {
                // Добавляем небольшую задержку, чтобы избежать чрезмерной нагрузки
                if (org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                    if (client.currentScreen == null) {
                        client.setScreen(new RecipeListScreen());
                    }
                    waitingForR = false; // Закрываем флаг, чтобы экран не переключался снова
                }
            } else {
                // Проверяем нажатие клавиши R
                if (org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                    waitingForR = true; // Устанавливаем флаг, чтобы экран переключился
                }
            }*/
        });
    }
}
