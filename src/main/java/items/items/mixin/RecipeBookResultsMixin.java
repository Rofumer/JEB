package items.items.mixin;

import items.items.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.*;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
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
        if (button == 1) {
            ItemStack stack = animatedResultButton.getDisplayStack();
            String itemName = stack.getItem().getName().getString(); // Локализованное имя (например, "Булыжник")
            String searchText = "#" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
            ((RecipeBookWidgetAccessor) recipeBookWidget).getSearchField().setText(searchText);
                    ((RecipeBookWidgetAccessor) recipeBookWidget).invokeReset();

                    cir.setReturnValue(true);
                }






        if (button == 0) {


            System.out.println(animatedResultButton.getCurrentId().toString());

            MinecraftClient client = MinecraftClient.getInstance();
            ClientRecipeBook recipeBook = client.player.getRecipeBook();

            Map<NetworkRecipeId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

            RecipeDisplayEntry entry = recipes.get(animatedResultButton.getCurrentId());

            RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(entry));

            alternatesWidget.showAlternativesForResult(myCustomRecipeResultCollection, contextParameterMap, false, animatedResultButton.getX()+20, animatedResultButton.getY()+20, areaLeft + areaWidth / 2, areaTop + 13 + areaHeight / 2, (float) animatedResultButton.getWidth());
        }
    }

}
