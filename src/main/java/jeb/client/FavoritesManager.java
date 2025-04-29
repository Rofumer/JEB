package jeb.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoritesManager {
    private static final Path FAVORITES_PATH = Paths.get(MinecraftClient.getInstance().runDirectory.getAbsolutePath(), "config", "favorites.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Set<Identifier> loadFavoriteItemIds() {
        Set<Identifier> result = new HashSet<>();
        try {
            if (!Files.exists(FAVORITES_PATH)) return result;

            JsonArray array = GSON.fromJson(Files.newBufferedReader(FAVORITES_PATH), JsonArray.class);
            String server = MinecraftClient.getInstance().getCurrentServerEntry() != null
                    ? MinecraftClient.getInstance().getCurrentServerEntry().address
                    : MinecraftClient.getInstance().getServer() != null
                    ? MinecraftClient.getInstance().getServer().getSaveProperties().getLevelName()
                    : "singleplayer";

            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                if (server.equals(obj.get("server").getAsString())) {
                    result.add(Identifier.of(obj.get("item").getAsString()));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }


    public static void removeFavorite(ItemStack stack) {
        try {
            // Определяем имя сервера или мира
            String server;
            if (MinecraftClient.getInstance().getCurrentServerEntry() != null) {
                server = MinecraftClient.getInstance().getCurrentServerEntry().address;
            } else if (MinecraftClient.getInstance().getServer() != null) {
                server = MinecraftClient.getInstance().getServer().getSaveProperties().getLevelName();
            } else {
                server = "unknown_local_world";
            }

            // Определяем ID предмета
            Identifier itemId = Registries.ITEM.getId(stack.getItem());

            // Читаем файл
            if (!Files.exists(FAVORITES_PATH)) {
                return; // Нечего удалять
            }

            JsonArray favorites = GSON.fromJson(Files.newBufferedReader(FAVORITES_PATH), JsonArray.class);
            JsonArray newFavorites = new JsonArray();

            boolean removed = false;

            for (JsonElement element : favorites) {
                JsonObject obj = element.getAsJsonObject();
                boolean sameServer = server.equals(obj.get("server").getAsString());
                boolean sameItem = itemId.toString().equals(obj.get("item").getAsString());

                if (sameServer && sameItem && !removed) {
                    removed = true; // Пропустить первую найденную запись
                    continue;
                }
                newFavorites.add(obj);
            }

            // Пишем обратно
            Files.createDirectories(FAVORITES_PATH.getParent());
            try (FileWriter writer = new FileWriter(FAVORITES_PATH.toFile())) {
                GSON.toJson(newFavorites, writer);
            }

            if (removed) {
                System.out.println("Удалено из избранного: " + itemId.toString() + " на сервере/мире: " + server);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void saveFavorite(ItemStack stack) {
        try {
            // Определяем имя сервера или мира
            String server;
            if (MinecraftClient.getInstance().getCurrentServerEntry() != null) {
                server = MinecraftClient.getInstance().getCurrentServerEntry().address;
            } else if (MinecraftClient.getInstance().getServer() != null) {
                server = MinecraftClient.getInstance().getServer().getSaveProperties().getLevelName();
            } else {
                server = "unknown_local_world";
            }

            // Определяем ID предмета
            Identifier itemId = Registries.ITEM.getId(stack.getItem());

            // Читаем текущие избранные или создаём новый массив
            JsonArray favorites;
            if (Files.exists(FAVORITES_PATH)) {
                favorites = GSON.fromJson(Files.newBufferedReader(FAVORITES_PATH), JsonArray.class);
            } else {
                favorites = new JsonArray();
            }

            // Проверка на наличие дубликата
            boolean alreadyExists = false;
            for (JsonElement element : favorites) {
                JsonObject obj = element.getAsJsonObject();
                if (server.equals(obj.get("server").getAsString()) &&
                        itemId.toString().equals(obj.get("item").getAsString())) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                // Добавляем новую запись
                JsonObject favoriteEntry = new JsonObject();
                favoriteEntry.addProperty("server", server);
                favoriteEntry.addProperty("item", itemId.toString());
                favorites.add(favoriteEntry);

                // Сортируем для удобства
                favorites = sortFavorites(favorites);

                // Пишем обратно в файл
                Files.createDirectories(FAVORITES_PATH.getParent());
                try (FileWriter writer = new FileWriter(FAVORITES_PATH.toFile())) {
                    GSON.toJson(favorites, writer);
                }

                System.out.println("Добавлен в избранное: " + itemId.toString() + " на сервере/мире: " + server);
            } else {
                System.out.println("Уже есть в избранном: " + itemId.toString() + " на сервере/мире: " + server);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JsonArray sortFavorites(JsonArray favorites) {
        List<JsonObject> sortedList = favorites.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .sorted(Comparator
                        .comparing((JsonObject o) -> o.get("server").getAsString())
                        .thenComparing(o -> o.get("item").getAsString()))
                .toList();

        JsonArray sortedArray = new JsonArray();
        for (JsonObject obj : sortedList) {
            sortedArray.add(obj);
        }
        return sortedArray;
    }
}
