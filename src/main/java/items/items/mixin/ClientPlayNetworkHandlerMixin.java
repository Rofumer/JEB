package items.items.mixin;

import items.items.client.RecipeLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.util.context.ContextParameterMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static items.items.client.ItemsClient.existingResultItems;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(
            method = "onRecipeBookAdd",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/recipebook/ClientRecipeBook;add(Lnet/minecraft/recipe/RecipeDisplayEntry;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectOnRecipeBookAdd(RecipeBookAddS2CPacket packet, CallbackInfo ci, ClientRecipeBook clientRecipeBook, Iterator var3, RecipeBookAddS2CPacket.Entry entry) {

        SlotDisplay resultSlot = entry.contents().display().result();

        MinecraftClient client = MinecraftClient.getInstance();

        ContextParameterMap context = SlotDisplayContexts.createParameters(
                Objects.requireNonNull(client.world)
        );

        List<ItemStack> stacks = resultSlot.getStacks(context);


        ItemStack stack = stacks.get(0);

        // Добавляем в Set
        existingResultItems.add(stack.getItem());

        // Можно сделать что угодно с display — лог, анализ, модификация
        // System.out.println("Получен display: " + display);
    }



        @Inject(method = "onRecipeBookAdd", at = @At("TAIL"))
        private void afterRecipeBookAdd(RecipeBookAddS2CPacket packet, CallbackInfo ci) {
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

            if (knownRecipeCount < 1358 && craftingStationId == 259) {

                try {
                    RecipeLoader.loadRecipesFromLog();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

        }

}

