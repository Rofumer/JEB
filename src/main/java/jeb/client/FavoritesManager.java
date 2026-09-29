package jeb.client;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class FavoritesManager {
    private static final Path FAVORITES_PATH = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "JEBfavorites.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Set<Identifier> loadFavoriteItemIds() {
        Set<Identifier> result = new HashSet<>();
        try {
            if (!Files.exists(FAVORITES_PATH)) return result;

            JsonArray array = readFavorites();
            String server = getServerName();

            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                if (server.equals(obj.get("server").getAsString())) {
                    result.add(Identifier.parse(obj.get("item").getAsString()));
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
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String nbtString = getSerializedNbt(stack);

            if (!Files.exists(FAVORITES_PATH)) return;

            JsonArray favorites = readFavorites();
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

            writeFavorites(newFavorites);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveFavorite(ItemStack stack) {
        try {
            String server = getServerName();
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String nbtString = getSerializedNbt(stack);

            JsonArray favorites = readFavorites();

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

                writeFavorites(favorites);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Старые версии писали файл через FileWriter в системной кодировке (на Windows с Java 17 это cp1251),
    // поэтому кириллические имена миров ломали чтение в UTF-8. Читаем UTF-8, иначе — системную кодировку.
    private static JsonArray readFavorites() throws IOException {
        if (!Files.exists(FAVORITES_PATH)) return new JsonArray();

        byte[] bytes = Files.readAllBytes(FAVORITES_PATH);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, nativeCharset());
        }

        JsonArray array = GSON.fromJson(text, JsonArray.class);
        return array != null ? array : new JsonArray();
    }

    private static void writeFavorites(JsonArray favorites) throws IOException {
        Files.createDirectories(FAVORITES_PATH.getParent());
        Files.writeString(FAVORITES_PATH, GSON.toJson(favorites), StandardCharsets.UTF_8);
    }

    private static Charset nativeCharset() {
        try {
            String name = System.getProperty("native.encoding");
            if (name != null) return Charset.forName(name);
        } catch (Exception ignored) {}
        return Charset.defaultCharset();
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
        Minecraft client = Minecraft.getInstance();
        if (client.getCurrentServer() != null) {
            return client.getCurrentServer().ip;
        } else if (client.getSingleplayerServer() != null) {
            return client.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            return "unknown_local_world";
        }
    }


    private static String getSerializedNbt(ItemStack stack) {
        CompoundTag result = new CompoundTag();

        var blockEntityData = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData != null && !blockEntityData.copyTagWithoutId().isEmpty()) {
            result.merge(blockEntityData.copyTagWithoutId());
        }

        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (customData != null && !customData.getString().isEmpty()) {
            return customData.getString();
        }

        var bucketEntityData = stack.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (bucketEntityData != null && !bucketEntityData.copyTag().isEmpty()) {
            result.merge(bucketEntityData.copyTag());
        }

        var entityData = stack.get(net.minecraft.core.component.DataComponents.ENTITY_DATA);
        if (entityData != null && !entityData.copyTagWithoutId().isEmpty()) {
            result.merge(entityData.copyTagWithoutId());
        }

        //return result.isEmpty() ? "" : result.toString();
        return "";
    }



}
