package jeb.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.client.resource.language.I18n.translate;

public class JEBClient implements ClientModInitializer {

    public static boolean customToggleEnabled = true;

    private static final Path CONFIG_PATH = Paths.get(
            MinecraftClient.getInstance().runDirectory.getAbsolutePath(),
            "config", "JEB.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    public static final Logger LOGGER = LoggerFactory.getLogger("JEB");


    private static KeyBinding keyBinding;
    public static KeyBinding keyBinding2;

    public static Set<Item> existingResultItems = new HashSet<>();

    public static Set<Item> nonexistingResultItems = new HashSet<>();

    public static String string = "-";
    public static List<RecipeResultCollection> filtered = new ArrayList<>();
    public static List<RecipeResultCollection> emptysearch = new ArrayList<>();

    public static boolean recipesLoaded = false;

    public static List<RecipeResultCollection> PREGENERATED_RECIPES = generateCustomRecipeList("");

    public static List<RecipeResultCollection> generateCustomRecipeList(String filter) {
        List<RecipeResultCollection> result = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();

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
            if (modName != null && !modName.isEmpty() && !Registries.ITEM.getId(item).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }

            // Проверка совпадения по имени, id или ключу
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String idString = item.toString().toLowerCase(Locale.ROOT);
            String key = translate(item.getTranslationKey()).toLowerCase(Locale.ROOT);

            boolean matches = query.isEmpty()
                    || name.contains(query)
                    || idString.contains(query)
                    || key.contains(query);

            // Проверка по тултипам, если не нашли раньше
            if (!matches && query.length() >= 3 && client.world != null) {
                try {
                    RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
                    Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
                    TooltipType tooltipType = TooltipType.Default.BASIC;
                    List<Text> tooltip = item.getDefaultStack().getTooltip(tooltipContext, client.player, tooltipType);
                    for (Text line : tooltip) {
                        String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
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

    @Override
    public void onInitializeClient() {

        loadConfig();
        Runtime.getRuntime().addShutdownHook(new Thread(JEBClient::saveConfig));

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jeb.optional_recipes_loading_screen", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_APOSTROPHE, // The keycode of the key
                "JEB (Just Enough Book)" // The translation key of the keybinding's category.
        ));

        keyBinding2 = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jeb.add_remove_favorite_recipes", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_A, // The keycode of the key
                "JEB (Just Enough Book)" // The translation key of the keybinding's category.
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

            while (keyBinding.wasPressed()) {

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
