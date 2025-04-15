package items.items.client;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text; // Исправленный импорт
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;


import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static items.items.client.RecipeLoader.loadRecipesFromLog;
import static net.minecraft.client.recipebook.RecipeBookType.CRAFTING;

public class RecipeListScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Создаем новый поток

    public RecipeListScreen() {
        super(Text.of("Recipe List"));
    }

    static public Boolean sent = false;

    @Override
    protected void init() {
        super.init();

        // Используем builder для создания кнопки
        this.addDrawableChild(ButtonWidget.builder(Text.of("Show All Recipes"), button -> {
                    try {
                        showAllRecipes();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).position(this.width / 2 - 100, this.height / 2 - 20)
                .size(200, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.of("Load All Recipes"), button -> {
                    try {
                        loadAllRecipes();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).position(this.width / 2 - 100, this.height / 2 - 40)
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






                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    ClientRecipeBook recipeBook = client.player.getRecipeBook();
                    List<RecipeResultCollection> recipes = recipeBook.getOrderedResults();


                    // Проходим по всем коллекциям рецептов
                    for (RecipeResultCollection collection : recipes) {
                        List<RecipeDisplayEntry> entries = collection.getAllRecipes();

                        // Преобразуем в строку и выводим подробности для каждого рецепта
                        for (RecipeDisplayEntry entry : entries) {
                            StringBuilder entryString = new StringBuilder("Display:" + entry.display().toString() + ";");
                            entryString.append("Category:").append(Registries.RECIPE_BOOK_CATEGORY.getKey(entry.category()).toString()).append(";");
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
                                    List<RegistryEntry<Item>> matchingItems = ingredient.getMatchingItems().collect(Collectors.toList());

                                    if (matchingItems.isEmpty()) {
                                        entryString.append("<empty>");
                                    } else {
                                        List<String> itemNames = new ArrayList<>();
                                        for (RegistryEntry<Item> entryItem : matchingItems) {
                                            Identifier id = Registries.ITEM.getId(entryItem.value());
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
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Рисуем текст или другие элементы на экране, если нужно
    }

    // Закрытие ExecutorService при выходе
    @Override
    public void close() {
        executorService.shutdownNow();
        super.close();
    }
}
