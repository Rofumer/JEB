package jeb.client;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FavoritesManager {
    private static final Path FAVORITES_PATH = Paths.get(MinecraftClient.getInstance().runDirectory.getAbsolutePath(), "config", "JEBfavorites.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Set<Identifier> loadFavoriteItemIds() {
        Set<Identifier> result = new HashSet<>();
        try {
            if (!Files.exists(FAVORITES_PATH)) return result;

            JsonArray array = GSON.fromJson(Files.newBufferedReader(FAVORITES_PATH), JsonArray.class);
            String server = getServerName();

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
            String server = getServerName();
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            String nbtString = getSerializedNbt(stack);

            if (!Files.exists(FAVORITES_PATH)) return;

            JsonArray favorites = GSON.fromJson(Files.newBufferedReader(FAVORITES_PATH), JsonArray.class);
            JsonArray newFavorites = new JsonArray();
            boolean removed = false;

            for (JsonElement element : favorites) {
                JsonObject obj = element.getAsJsonObject();
                boolean sameServer = server.equals(obj.get("server").getAsString());
                boolean sameItem = itemId.toString().equals(obj.get("item").getAsString());
                String nbtInFile = obj.has("nbt") ? obj.get("nbt").getAsString() : "";
                boolean sameNbt = nbtString.equals(nbtInFile);

                if (sameServer && sameItem && sameNbt && !removed) {
                    removed = true;
                    continue;
                }
                newFavorites.add(obj);
            }

            Files.createDirectories(FAVORITES_PATH.getParent());
            try (FileWriter writer = new FileWriter(FAVORITES_PATH.toFile())) {
                GSON.toJson(newFavorites, writer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveFavorite(ItemStack stack) {
        try {
            String server = getServerName();
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            String nbtString = getSerializedNbt(stack);

            JsonArray favorites = Files.exists(FAVORITES_PATH)
                    ? GSON.fromJson(Files.newBufferedReader(FAVORITES_PATH), JsonArray.class)
                    : new JsonArray();

            boolean alreadyExists = false;

            for (JsonElement element : favorites) {
                JsonObject obj = element.getAsJsonObject();
                boolean sameServer = server.equals(obj.get("server").getAsString());
                boolean sameItem = itemId.toString().equals(obj.get("item").getAsString());
                String nbtInFile = obj.has("nbt") ? obj.get("nbt").getAsString() : "";
                boolean sameNbt = nbtString.equals(nbtInFile);

                if (sameItem && sameServer && sameNbt) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                JsonObject favoriteEntry = new JsonObject();
                favoriteEntry.addProperty("server", server);
                favoriteEntry.addProperty("item", itemId.toString());
                if (!nbtString.isEmpty()) {
                    favoriteEntry.addProperty("nbt", nbtString);
                }
                favorites.add(favoriteEntry);
                favorites = sortFavorites(favorites);

                Files.createDirectories(FAVORITES_PATH.getParent());
                try (FileWriter writer = new FileWriter(FAVORITES_PATH.toFile())) {
                    GSON.toJson(favorites, writer);
                }
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

    private static String getServerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) {
            return client.getCurrentServerEntry().address;
        } else if (client.getServer() != null) {
            return client.getServer().getSaveProperties().getLevelName();
        } else {
            return "unknown_local_world";
        }
    }


    private static String getSerializedNbt(ItemStack stack) {
        NbtCompound result = new NbtCompound();

        var blockEntityData = stack.get(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA);
        if (blockEntityData != null && !blockEntityData.copyNbt().isEmpty()) {
            result.copyFrom(blockEntityData.copyNbt());
        }

        var customData = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
        if (customData != null && !customData.getString().isEmpty()) {
            return customData.getString();
        }

        var bucketEntityData = stack.get(net.minecraft.component.DataComponentTypes.BUCKET_ENTITY_DATA);
        if (bucketEntityData != null && !bucketEntityData.copyNbt().isEmpty()) {
            result.copyFrom(bucketEntityData.copyNbt());
        }

        var entityData = stack.get(net.minecraft.component.DataComponentTypes.ENTITY_DATA);
        if (entityData != null && !entityData.copyNbt().isEmpty()) {
            result.copyFrom(entityData.copyNbt());
        }

        //return result.isEmpty() ? "" : result.toString();
        return "";
    }



}
