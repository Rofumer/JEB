package jeb.client;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import static jeb.client.JEBClient.generateCustomRecipeList;
import static jeb.client.RecipeLoader.loadRecipesFromLog;

public class RecipeListScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Создаем новый поток

    public RecipeListScreen() {
        super(Component.nullToEmpty("Recipe List"));
    }

    static public Boolean sent = false;

    @Override
    protected void init() {
        super.init();


        /// ---
        // Используем builder для создания кнопки
        /*this.addRenderableWidget(Button.builder(
                        Component.literal("Show All Recipes"),
                        button -> {
                            try {
                                showAllRecipes();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .pos(this.width / 2 - 100, this.height / 2 - 20)
                .size(200, 20)
                .build()
        );*/
        /// ---

        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Load All Recipes"), button -> {
                    try {
                        loadAllRecipes();
                        JEBClient.PREGENERATED_RECIPES = generateCustomRecipeList("");
                        minecraft.setScreen(null);
                        minecraft.player.sendSystemMessage(
                                Component.literal("All recipes have been loaded")
                        );

                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).pos(this.width / 2 - 100, this.height / 2 - 40)
                .size(200, 20)
                .build());
    }

    private static void printObjectDetails(Object obj) {
        // Получаем класс объекта
        Class<?> clazz = obj.getClass();

        // Получаем все поля класса (включая приватные)
        var fields = clazz.getDeclaredFields();

        for (var field : fields) {
            field.setAccessible(true); // Делаем поле доступным для чтения

        //    try {
                // Печатаем имя поля и его значение
                //System.out.println(field.getName() + " = " + field.get(obj));
            //} catch (IllegalAccessException e) {
            //    e.printStackTrace();
            //}
        }
    }

    // Метод для вывода подробностей категории рецепта
    private static void printCategoryDetails(RecipeBookCategory category) {
        // Пример, как получить информацию о категории, если она имеет метод для этого
        // Это зависит от того, что есть в RecipeBookCategory
        //System.out.println("Category Details:");
        try {
            // Выводим название категории, если есть такой метод
            //System.out.println("Category Name: " + Registries.RECIPE_BOOK_CATEGORY.getKey(category)); // Пример, если метод getName() существует
        } catch (Exception e) {
            // Если метода нет, выводим по-другому
            //System.out.println("Category hashcode: " + category.hashCode());
        }
    }

    public void loadAllRecipes() throws InterruptedException {
        // Запускаем задачу в отдельном потоке, чтобы не блокировать главный поток

        try {

            loadRecipesFromLog();

            // Здесь можно добавить логику для получения всех рецептов или другого действия
            // Например, задержка, чтобы увидеть результат
            Thread.sleep(1000); // Просто пример ожидания, это можно удалить или заменить на реальную логику

            LOGGER.info("Recipes loaded successfully.");
        } catch (Exception e) {
            // Логируем исключения, если они возникнут
            LOGGER.error("Error occurred while loading recipes", e);
        }


        // Здесь можно добавить логику для получения всех рецептов или другого действия
        // Например, задержка, чтобы увидеть результат
        Thread.sleep(1000); // Просто пример ожидания, это можно удалить или заменить на реальную логику


    }

    private void showAllRecipes() throws InterruptedException {
        // Запускаем задачу в отдельном потоке, чтобы не блокировать главный поток

            try {






                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    ClientRecipeBook recipeBook = client.player.getRecipeBook();
                    List<RecipeCollection> recipes = recipeBook.getCollections();


                    // Проходим по всем коллекциям рецептов
                    for (RecipeCollection collection : recipes) {
                        List<RecipeDisplayEntry> entries = collection.getRecipes();

                        // Преобразуем в строку и выводим подробности для каждого рецепта
                        for (RecipeDisplayEntry entry : entries) {
                            StringBuilder entryString = new StringBuilder("Display:" + entry.display().toString() + ";");
                            entryString.append("Category:").append(BuiltInRegistries.RECIPE_BOOK_CATEGORY.getResourceKey(entry.category()).toString()).append(";");
                            entryString.append("NetworkID:").append(entry.id().toString()).append(";");
                            entryString.append("Group:").append(entry.group().toString()).append(";");
                            //entryString.append("Crafting Requirements Structure:").append(entry.craftingRequirements().toString()).append(";");
                            entryString.append("Crafting Requirements Items:");

                            //System.out.println(entryString);

                            // Выводим подробности категории, если это необходимо
                            //RecipeBookCategory category = entry.category();
                            //printCategoryDetails(category); // Печатаем все детали категории

                            // Выводим подробности самого рецепта
                            //System.out.println("Recipe Details:");
                            //printObjectDetails(entry);

                            Optional<List<Ingredient>> optionalIngredients = entry.craftingRequirements();

                            if (optionalIngredients.isPresent()) {
                                List<Ingredient> ingredients = optionalIngredients.get();
                                //System.out.println("Crafting Requirements:");

                                for (Ingredient ingredient : ingredients) {
                                    List<Holder<Item>> matchingItems = ingredient.items().collect(Collectors.toList());

                                    if (matchingItems.isEmpty()) {
                                        entryString.append("<empty>");
                                    } else {
                                        List<String> itemNames = new ArrayList<>();
                                        for (Holder<Item> entryItem : matchingItems) {
                                            Identifier id = BuiltInRegistries.ITEM.getKey(entryItem.value());
                                            itemNames.add(id.toString());
                                        }
                                        entryString.append(String.join(", ", itemNames));
                                    }
                                    entryString.append(";");
                                }

                            } else {
                                entryString.append("<empty>");
                            }

                            //entryString.append("\n---\n");

                            // Записываем строку с рецептом в файл
                            try {
                                Files.write(
                                        Paths.get("recipes_output.txt"),
                                        (entryString + System.lineSeparator()).getBytes(),
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.APPEND
                                );
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }



                // Создаём идентификатор рецепта
                /*NetworkRecipeId recipeId = new NetworkRecipeId(779);

// Создаём отображение рецепта (2x2, дубовые брёвна → дубовые доски)
                ShapedCraftingRecipeDisplay display = new ShapedCraftingRecipeDisplay(
                        2, 2, // Ширина и высота рецепта
                        List.of(
                                new SlotDisplay.CompositeSlotDisplay(List.of(
                                        new SlotDisplay.ItemSlotDisplay(Items.OAK_LOG)
                                )),
                                new SlotDisplay.CompositeSlotDisplay(List.of(
                                        new SlotDisplay.ItemSlotDisplay(Items.OAK_LOG)
                                )),
                                new SlotDisplay.CompositeSlotDisplay(List.of(
                                        new SlotDisplay.ItemSlotDisplay(Items.OAK_LOG)
                                )),
                                new SlotDisplay.CompositeSlotDisplay(List.of(
                                        new SlotDisplay.ItemSlotDisplay(Items.OAK_LOG)
                                ))
                        ),
                        new SlotDisplay.StackSlotDisplay(new ItemStack(Items.OAK_WOOD, 3)), // Результат: 3 дубовые древесины
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE) // Крафтится на верстаке
                );

// Создаём объект RecipeDisplayEntry
                RecipeDisplayEntry recipeEntry = new RecipeDisplayEntry(recipeId, display, OptionalInt.of(13),
                        new RecipeBookCategory(), Optional.of(List.of(
                        Ingredient.ofItems(Items.OAK_LOG),
                        Ingredient.ofItems(Items.OAK_LOG),
                        Ingredient.ofItems(Items.OAK_LOG),
                        Ingredient.ofItems(Items.OAK_LOG)
                ))
                );

                // Создаём Entry с этим рецептом
                RecipeBookAddS2CPacket.Entry entry = new RecipeBookAddS2CPacket.Entry(recipeEntry, (byte) 2);

                // Создаём сам пакет
                RecipeBookAddS2CPacket packet = new RecipeBookAddS2CPacket(List.of(entry), false);

                // Получаем ссылку на ClientPlayNetworkHandler
                ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();

                // Передаём в обработчик
                networkHandler.onRecipeBookAdd(packet);*/


                // Здесь можно добавить логику для получения всех рецептов или другого действия
                // Например, задержка, чтобы увидеть результат
                Thread.sleep(1000); // Просто пример ожидания, это можно удалить или заменить на реальную логику

                LOGGER.info("Recipes loaded successfully.");
            } catch (Exception e) {
                // Логируем исключения, если они возникнут
                LOGGER.error("Error occurred while loading recipes", e);
            }


                // Здесь можно добавить логику для получения всех рецептов или другого действия
                // Например, задержка, чтобы увидеть результат
                Thread.sleep(1000); // Просто пример ожидания, это можно удалить или заменить на реальную логику


    }

    // Исправленный метод render с использованием DrawContext вместо MatrixStack
    /*@Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Рисуем текст или другие элементы на экране, если нужно
    }*/

    // Закрытие ExecutorService при выходе
    @Override
    public void onClose() {
        executorService.shutdownNow();
        super.onClose();
    }
}
