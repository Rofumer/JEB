package jeb.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
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

@Mixin(RecipeBookPage.class)
public class RecipeBookResultsMixin {

    @Shadow
    private RecipeBookComponent<?> parent;
    @Shadow
    private OverlayRecipeComponent overlay;

    @Shadow
    private RecipeDisplayId lastClickedRecipe;



    @Shadow
    @Nullable
    private RecipeCollection lastClickedRecipeCollection;

    @Shadow
    private RecipeButton hoveredButton;

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onRightClickInject(
            MouseButtonEvent click, int left, int top, int width, int height, boolean bl, CallbackInfoReturnable<Boolean> cir, @Local ContextMap contextParameterMap, @Local RecipeButton animatedResultButton
    ) {

        //if (!(MinecraftClient.getInstance().player.currentScreenHandler instanceof AbstractCraftingScreenHandler)) {
            // Не наш контейнер — не трогаем, пусть работает обычный код!
        //    return;
        //}

        animatedResultButton = hoveredButton;
        if (animatedResultButton  != null) {
        //if (animatedResultButton.mouseClicked(mouseX, mouseY, button)) {


            if (click.button() == 2) {
                ItemStack stack = animatedResultButton.getDisplayStack();
                //String itemName = stack.getItem().asItem().toString();
                String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
                String searchText = "~" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) parent).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) parent).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) parent).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) parent).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }

            if (click.button() == 1) {
                ItemStack stack = animatedResultButton.getDisplayStack();
                String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT); // Локализованное имя (например, "Булыжник")
                String searchText = "#" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) parent).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) parent).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) parent).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) parent).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }


            if (click.button() == 0) {


                if (!(Minecraft.getInstance().player.containerMenu instanceof AbstractCraftingMenu)) {
                    // Не наш контейнер — не трогаем, пусть работает обычный код!
                    return;
                }

                //System.out.println(animatedResultButton.getCurrentId().toString());

                Minecraft client = Minecraft.getInstance();
                ClientRecipeBook recipeBook = client.player.getRecipeBook();

                Map<RecipeDisplayId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

                RecipeDisplayEntry entry = recipes.get(animatedResultButton.getCurrentRecipe());

                if(entry != null) {

                    RecipeCollection myCustomRecipeResultCollection = new RecipeCollection(List.of(entry));

                    if(!canDisplay(entry.display())) {
                        overlay.init(myCustomRecipeResultCollection, contextParameterMap, false, animatedResultButton.getX(), animatedResultButton.getY(), left + width / 2, top + 13 + height / 2, (float) animatedResultButton.getWidth());
                    }
                    else
                    {
                        this.lastClickedRecipe = animatedResultButton.getCurrentRecipe();
                        this.lastClickedRecipeCollection = animatedResultButton.getCollection();
                        recipeBook.removeHighlight(animatedResultButton.getCurrentRecipe());
                        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
                        if(!(animatedResultButton.getCurrentRecipe().index() == 9999)) {
                            networkHandler.send(new ServerboundRecipeBookSeenRecipePacket(animatedResultButton.getCurrentRecipe()));
                        }
                    }
                }

                cir.setReturnValue(true);
                cir.cancel();
            }
        }

    }

    private boolean canDisplay(RecipeDisplay display) {
        RecipeBookComponent<?> widget = this.parent;

        AbstractCraftingMenu handler = (AbstractCraftingMenu) Minecraft.getInstance().player.containerMenu;

        //AbstractCraftingScreenHandler handler = ((RecipeBookWidgetAccessor) widget).getCraftingScreenHandler();
        int i = handler.getGridWidth();
        int j = handler.getGridHeight();

        return switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> i >= shaped.width() && j >= shaped.height();
            case ShapelessCraftingRecipeDisplay shapeless -> i * j >= shapeless.ingredients().size();
            default -> false;
        };
    }

}
