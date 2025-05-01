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

import static net.minecraft.client.resource.language.I18n.translate;

public class JEBClient implements ClientModInitializer {

    public static boolean customToggleEnabled = true;

    private static final Path CONFIG_PATH = Paths.get(
            MinecraftClient.getInstance().runDirectory.getAbsolutePath(),
            "config", "JEB.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();



    private static KeyBinding keyBinding;

    public static Set<Item> existingResultItems = new HashSet<>();

    public static boolean recipesLoaded = false;

    public static List<RecipeResultCollection> PREGENERATED_RECIPES = generateCustomRecipeList("");

    public static List<RecipeResultCollection> generateCustomRecipeList(String filter) {
        List<RecipeResultCollection> list = new ArrayList<>();

        MinecraftClient client = MinecraftClient.getInstance();

        String query;

        String modName = null;
        if (filter.startsWith("@")) {
            // Извлекаем имя мода, если оно присутствует в начале строки
            int endIndex = filter.indexOf(" ");
            if (endIndex != -1) {
                modName = filter.substring(1, endIndex).trim();  // Извлекаем имя мода
                query = filter.substring(endIndex + 1).toLowerCase();  // Остальная часть это обычный запрос
            } else {
                modName = filter.substring(1).trim();  // Имя мода без строки запроса
                query = "";  // Если нет строки запроса, то фильтровать только по имени мода
            }
        }
        else
        {
            query = filter.toLowerCase();
        }

        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            if (existingResultItems.contains(item)) continue;


            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String id_item = item.toString().toLowerCase(Locale.ROOT);
            String key = translate(item.getTranslationKey()).toLowerCase(Locale.ROOT);

            if (modName != null && !modName.isEmpty() && !Registries.ITEM.getId(item).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }


            boolean tooltip_bool = false;


            if (client.world != null)
            {
            // Поиск по тултипам
            RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
            Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
            TooltipType tooltipType = TooltipType.Default.BASIC;


                try {
                    List<Text> tooltip = item.getDefaultStack().getTooltip(tooltipContext, client.player, tooltipType);
                    for (Text line : tooltip) {
                        String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)) {
                            tooltip_bool = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Можно также записать лог или безопасно проигнорировать ошибку
                }

            }

            if (!(name.contains(query) || id_item.contains(query) || key.contains(query) || tooltip_bool)) continue;
            ///////if (!(name.contains(query) || id_item.contains(query) || key.contains(query))) continue;


            ///////if (!translate(item.getTranslationKey()).toLowerCase().contains(filter.toLowerCase())) continue;


            Identifier id = Registries.ITEM.getId(item);
            NetworkRecipeId recipeId = new NetworkRecipeId(9999);

            List<SlotDisplay> slots = List.of(
                    new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", id.getPath())))
            );

            SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item, 1));
            SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                    Registries.ITEM.get(Identifier.of("minecraft", "crafting_table"))
            );

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            list.add(new RecipeResultCollection(List.of(entry)));
        }

        return list;
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
                "Optional recipes loading screen", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_APOSTROPHE, // The keycode of the key
                "JEB (Just Enough Book)" // The translation key of the keybinding's category.
        ));


        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            recipesLoaded = false;
            existingResultItems = new HashSet<>();
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
