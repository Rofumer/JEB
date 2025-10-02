package jeb.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.RecipeBookDataC2SPacket;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.util.context.ContextParameterMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mixin(RecipeBookResults.class)
public class RecipeBookResultsMixin {

    @Shadow
    private RecipeBookWidget<?> recipeBookWidget;
    @Shadow
    private RecipeAlternativesWidget alternatesWidget;

    @Shadow
    private NetworkRecipeId lastClickedRecipe;



    @Shadow
    @Nullable
    private RecipeResultCollection resultCollection;

    @Shadow
    private AnimatedResultButton hoveredResultButton;

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/AnimatedResultButton;mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onRightClickInject(
            Click click, int left, int top, int width, int height, boolean bl, CallbackInfoReturnable<Boolean> cir, @Local ContextParameterMap contextParameterMap, @Local AnimatedResultButton animatedResultButton
    ) {

        //if (!(MinecraftClient.getInstance().player.currentScreenHandler instanceof AbstractCraftingScreenHandler)) {
            // Не наш контейнер — не трогаем, пусть работает обычный код!
        //    return;
        //}

        animatedResultButton = hoveredResultButton;
        if (animatedResultButton  != null) {
        //if (animatedResultButton.mouseClicked(mouseX, mouseY, button)) {


            if (click.button() == 2) {
                ItemStack stack = animatedResultButton.getDisplayStack();
                //String itemName = stack.getItem().asItem().toString();
                String itemName = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
                String searchText = "~" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) recipeBookWidget).getSearchField().setText(searchText);
                ((RecipeBookWidgetAccessor) recipeBookWidget).setSelectedTab((RecipeGroupButtonWidget) ((RecipeBookWidgetAccessor) recipeBookWidget).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) recipeBookWidget).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }

            if (click.button() == 1) {
                ItemStack stack = animatedResultButton.getDisplayStack();
                String itemName = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT); // Локализованное имя (например, "Булыжник")
                String searchText = "#" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) recipeBookWidget).getSearchField().setText(searchText);
                ((RecipeBookWidgetAccessor) recipeBookWidget).setSelectedTab((RecipeGroupButtonWidget) ((RecipeBookWidgetAccessor) recipeBookWidget).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) recipeBookWidget).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }


            if (click.button() == 0) {


                if (!(MinecraftClient.getInstance().player.currentScreenHandler instanceof AbstractCraftingScreenHandler)) {
                    // Не наш контейнер — не трогаем, пусть работает обычный код!
                    return;
                }

                //System.out.println(animatedResultButton.getCurrentId().toString());

                MinecraftClient client = MinecraftClient.getInstance();
                ClientRecipeBook recipeBook = client.player.getRecipeBook();

                Map<NetworkRecipeId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

                RecipeDisplayEntry entry = recipes.get(animatedResultButton.getCurrentId());

                if(entry != null) {

                    RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(entry));

                    if(!canDisplay(entry.display())) {
                        alternatesWidget.showAlternativesForResult(myCustomRecipeResultCollection, contextParameterMap, false, animatedResultButton.getX(), animatedResultButton.getY(), left + width / 2, top + 13 + height / 2, (float) animatedResultButton.getWidth());
                    }
                    else
                    {
                        this.lastClickedRecipe = animatedResultButton.getCurrentId();
                        this.resultCollection = animatedResultButton.getResultCollection();
                        recipeBook.unmarkHighlighted(animatedResultButton.getCurrentId());
                        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                        if(!(animatedResultButton.getCurrentId().index() == 9999)) {
                            networkHandler.sendPacket(new RecipeBookDataC2SPacket(animatedResultButton.getCurrentId()));
                        }
                    }
                }

                cir.setReturnValue(true);
                cir.cancel();
            }
        }

    }

    private boolean canDisplay(RecipeDisplay display) {
        RecipeBookWidget<?> widget = this.recipeBookWidget;

        AbstractCraftingScreenHandler handler = (AbstractCraftingScreenHandler) MinecraftClient.getInstance().player.currentScreenHandler;

        //AbstractCraftingScreenHandler handler = ((RecipeBookWidgetAccessor) widget).getCraftingScreenHandler();
        int i = handler.getWidth();
        int j = handler.getHeight();

        return switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> i >= shaped.width() && j >= shaped.height();
            case ShapelessCraftingRecipeDisplay shapeless -> i * j >= shapeless.ingredients().size();
            default -> false;
        };
    }

}
