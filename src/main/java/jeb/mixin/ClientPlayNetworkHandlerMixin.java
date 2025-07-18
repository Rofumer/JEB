package jeb.mixin;

import jeb.client.RecipeIndex;
import jeb.client.RecipeLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.util.context.ContextParameterMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.util.*;

import static jeb.client.JEBClient.*;
import static jeb.client.RecipeIndex.buildRecipeIndex;
import static jeb.client.RecipeIndex.fillItemIndex;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Unique
    private static final Map<String, Integer> VANILLA_RECIPE_COUNTS = Map.of(
            "1.21.4", 1358,
            "1.21.5", 1361,
            "1.21.6", 1395,
            "1.21.7", 1395,
            "1.21.8", 1395
    );

    @Unique
    private static final Map<String, Integer> VANILLA_CT_ID = Map.of(
            "1.21.4", 259,
            "1.21.5", 259,
            "1.21.6", 262,
            "1.21.7", 262,
            "1.21.8", 262
    );


    @Inject(
            method = "onRecipeBookAdd",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/recipebook/ClientRecipeBook;add(Lnet/minecraft/recipe/RecipeDisplayEntry;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectOnRecipeBookAdd(RecipeBookAddS2CPacket packet, CallbackInfo ci, ClientRecipeBook clientRecipeBook, Iterator var3, RecipeBookAddS2CPacket.Entry entry) {

        ContextParameterMap context = SlotDisplayContexts.createParameters(
                Objects.requireNonNull(MinecraftClient.getInstance().world)
        );

        if (recipesLoaded) {
            long startTime = System.currentTimeMillis();
            RecipeBookCategory category = entry.contents().category();
            LOGGER.info("[JEB] checking recipe {} started at {}", entry.contents().display().result().getFirst(context).toString() ,new Date(startTime));
            // Проверяем по id (по новому методу!)
            if (!RecipeIndex.recipeIdExistsInIndex(category, entry.contents())) {
                RecipeIndex.addAndIndexRecipeIfAbsent(category, entry.contents(), context);
                LOGGER.info("[JEB] The recipe has been added: {}", entry.contents().display().result().getFirst(context).toString());
            }


            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("[JEB] checking recipe done at {} ({} ms)", new Date(endTime), duration);

            return;
        }


        SlotDisplay resultSlot = entry.contents().display().result();

        List<ItemStack> stacks = resultSlot.getStacks(context);

        ItemStack stack = stacks.get(0);

        // Добавляем в Set
        if(entry.contents().display().craftingStation().getStacks(context).getFirst().getItem() == Items.CRAFTING_TABLE) {
            existingResultItems.add(stack.getItem());
        }

        // Можно сделать что угодно с display — лог, анализ, модификация
        // System.out.println("Получен display: " + display);
    }



        @Inject(method = "onRecipeBookAdd", at = @At("TAIL"))
        private void afterRecipeBookAdd(RecipeBookAddS2CPacket packet, CallbackInfo ci) {

            if(recipesLoaded) return;

            String version = SharedConstants.getGameVersion().name(); // примерная функция
            //String version = SharedConstants.getGameVersion().getName(); // примерная функция

            int vanillaMaxRecipes = VANILLA_RECIPE_COUNTS.getOrDefault(version, 1358);
            int vanillaCTID = VANILLA_CT_ID.getOrDefault(version, 259);



            MinecraftClient client = MinecraftClient.getInstance();

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
                for (Item item : Registries.ITEM) {
                    if (item == Items.AIR) continue;
                    if (existingResultItems.contains(item)) continue;
                    nonexistingResultItems.add(item);
                }
                fillItemIndex();
            }

        }

}

