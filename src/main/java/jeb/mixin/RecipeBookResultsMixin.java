package jeb.mixin;

import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.MinecraftClient;
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

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/AnimatedResultButton;mouseClicked(DDI)Z",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true
    )
    private void onRightClickInject(
            double mouseX, double mouseY, int button,
            int areaLeft, int areaTop, int areaWidth, int areaHeight,
            CallbackInfoReturnable<Boolean> cir,
            ContextParameterMap contextParameterMap,
            Iterator<AnimatedResultButton> iterator,
            AnimatedResultButton animatedResultButton
    ) {

        if (animatedResultButton.mouseClicked(mouseX, mouseY, button)) {

            if (button == 1) {
                ItemStack stack = animatedResultButton.getDisplayStack();
                String itemName = stack.getItem().getName().getString(); // Локализованное имя (например, "Булыжник")
                String searchText = "#" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) recipeBookWidget).getSearchField().setText(searchText);
                ((RecipeBookWidgetAccessor) recipeBookWidget).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }


            if (button == 0) {


                //System.out.println(animatedResultButton.getCurrentId().toString());

                MinecraftClient client = MinecraftClient.getInstance();
                ClientRecipeBook recipeBook = client.player.getRecipeBook();

                Map<NetworkRecipeId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

                RecipeDisplayEntry entry = recipes.get(animatedResultButton.getCurrentId());

                if(entry != null) {

                    RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(entry));

                    if(!canDisplay(entry.display())) {
                        alternatesWidget.showAlternativesForResult(myCustomRecipeResultCollection, contextParameterMap, false, animatedResultButton.getX(), animatedResultButton.getY(), areaLeft + areaWidth / 2, areaTop + 13 + areaHeight / 2, (float) animatedResultButton.getWidth());
                    }
                    else
                    {
                        this.lastClickedRecipe = animatedResultButton.getCurrentId();
                        this.resultCollection = animatedResultButton.getResultCollection();
                        recipeBook.unmarkHighlighted(animatedResultButton.getCurrentId());
                        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                        networkHandler.sendPacket(new RecipeBookDataC2SPacket(animatedResultButton.getCurrentId()));
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
