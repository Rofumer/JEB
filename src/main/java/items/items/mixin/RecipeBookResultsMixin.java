package items.items.mixin;

import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.client.gui.screen.recipebook.RecipeAlternativesWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.context.ContextParameterMap;
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

@Mixin(RecipeBookResults.class)
public class RecipeBookResultsMixin {

    @Shadow private RecipeBookWidget<?> recipeBookWidget;
    @Shadow private RecipeAlternativesWidget alternatesWidget;


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
            // Изменяем текст поиска
            ((RecipeBookWidgetAccessor) recipeBookWidget).getSearchField().setText("cobblestone");

            // Вызываем reset()
            ((RecipeBookWidgetAccessor) recipeBookWidget).invokeReset();

            cir.setReturnValue(true); // чтобы остановить дальнейшую обработку
        }

        if (button == 0) {
            alternatesWidget.showAlternativesForResult(animatedResultButton.getResultCollection(), contextParameterMap, false, animatedResultButton.getX(), animatedResultButton.getY(), areaLeft + areaWidth / 2, areaTop + 13 + areaHeight / 2, (float)animatedResultButton.getWidth());
        }
    }
}
