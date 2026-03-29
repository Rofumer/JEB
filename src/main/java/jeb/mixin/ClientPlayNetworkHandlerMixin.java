package jeb.mixin;

import jeb.client.RecipeIndex;
import jeb.client.RecipeLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import java.io.IOException;
import java.util.*;

import static jeb.client.JEBClient.*;
import static jeb.client.RecipeIndex.buildRecipeIndex;
import static jeb.client.RecipeIndex.fillItemIndex;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Unique
    private static final Map<String, Integer> VANILLA_RECIPE_COUNTS = Map.of(
            "1.21.4", 1358,
            "1.21.5", 1361,
            "1.21.6", 1395,
            "1.21.7", 1395,
            "1.21.8", 1395,
            "1.21.9", 1449,
            "1.21.10", 1449,
            "1.21.11", 1459,
            "26.1", 1498
    );

    @Unique
    private static final Map<String, Integer> VANILLA_CT_ID = Map.of(
            "1.21.4", 259,
            "1.21.5", 259,
            "1.21.6", 262,
            "1.21.7", 262,
            "1.21.8", 262,
            "1.21.9", 283,
            "1.21.10", 283,
            "1.21.11", 284,
            "26.1", 293
    );


    @Inject(
            method = "handleRecipeBookAdd",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientRecipeBook;add(Lnet/minecraft/world/item/crafting/display/RecipeDisplayEntry;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectOnRecipeBookAdd(ClientboundRecipeBookAddPacket packet, CallbackInfo ci, ClientRecipeBook clientRecipeBook, Iterator var3, ClientboundRecipeBookAddPacket.Entry entry) {

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(Minecraft.getInstance().level)
        );

        if (recipesLoaded) {
            long startTime = System.currentTimeMillis();
            RecipeBookCategory category = entry.contents().category();
            LOGGER.info("[JEB] checking recipe {} started at {}", entry.contents().display().result().resolveForFirstStack(context).toString() ,new Date(startTime));
            // Проверяем по id (по новому методу!)
            if (!RecipeIndex.recipeIdExistsInIndex(category, entry.contents())) {
                RecipeIndex.addAndIndexRecipeIfAbsent(category, entry.contents(), context);
                LOGGER.info("[JEB] The recipe has been added: {}", entry.contents().display().result().resolveForFirstStack(context).toString());
            }


            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("[JEB] checking recipe done at {} ({} ms)", new Date(endTime), duration);

            return;
        }


        SlotDisplay resultSlot = entry.contents().display().result();

        List<ItemStack> stacks = resultSlot.resolveForStacks(context);

        ItemStack stack = stacks.get(0);

        // Добавляем в Set
        if(entry.contents().display().craftingStation().resolveForStacks(context).getFirst().getItem() == Items.CRAFTING_TABLE) {
            existingResultItems.add(stack.getItem());
        }

        // Можно сделать что угодно с display — лог, анализ, модификация
        // System.out.println("Получен display: " + display);
    }



        @Inject(method = "handleRecipeBookAdd", at = @At("TAIL"))
        private void afterRecipeBookAdd(ClientboundRecipeBookAddPacket packet, CallbackInfo ci) {

            if(recipesLoaded) return;

            String version = SharedConstants.getCurrentVersion().name(); // примерная функция
            //String version = SharedConstants.getGameVersion().getName(); // примерная функция

            int vanillaMaxRecipes = VANILLA_RECIPE_COUNTS.getOrDefault(version, 1358);
            int vanillaCTID = VANILLA_CT_ID.getOrDefault(version, 259);



            Minecraft client = Minecraft.getInstance();

            int knownRecipeCount = 0;

            ClientRecipeBook recipeBook = null;

            int craftingStationId = 0;

            //if (client.player != null) {
            recipeBook = client.player.getRecipeBook();
            List<RecipeCollection> recipes = recipeBook.getCollections();


            // Проходим по всем коллекциям рецептов
            for (RecipeCollection collection : recipes) {
                List<RecipeDisplayEntry> entries = collection.getRecipes();

                // Преобразуем в строку и выводим подробности для каждого рецепта
                for (RecipeDisplayEntry entry : entries) {


                    SlotDisplay resultSlot = entry.display().result();

                    ContextMap context = SlotDisplayContext.fromLevel(
                            Objects.requireNonNull(client.level)
                    );

                    List<ItemStack> stacks = resultSlot.resolveForStacks(context);


                    ItemStack stack = stacks.getFirst();


                    if (stack.getItem() == Items.CRAFTING_TABLE) craftingStationId=entry.id().index();


                    knownRecipeCount++;

                }

            }
            // }

            //if (knownRecipeCount < 1358 && craftingStationId == 259) {
            if (knownRecipeCount < vanillaMaxRecipes && craftingStationId == vanillaCTID) { //for 1.21.6

                try {
                    RecipeLoader.loadRecipesFromLog();
                    recipesLoaded = true;
                    buildRecipeIndex();

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            //if(knownRecipeCount >= 1358 || (craftingStationId != 262 && craftingStationId !=0)) {  //for 1.21.6
            if(knownRecipeCount >= vanillaMaxRecipes || (craftingStationId != vanillaCTID && craftingStationId !=0)) {
                recipesLoaded = true;
                buildRecipeIndex();
            }

            if(recipesLoaded) {
                nonexistingResultItems.clear();
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR) continue;
                    if (existingResultItems.contains(item)) continue;
                    nonexistingResultItems.add(item);
                }
                fillItemIndex();
            }

        }

}

