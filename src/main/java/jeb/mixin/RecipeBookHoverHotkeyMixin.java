package jeb.mixin;

import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.JEBClient;
import jeb.client.RecipeSearchQueries;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookScreen.class)
public abstract class RecipeBookHoverHotkeyMixin {

    @Shadow
    @Final
    private RecipeBookWidget<?> recipeBook;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void jeb$onHoverHotkey(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        boolean viewRecipe = JEBClient.keyViewRecipe != null && JEBClient.keyViewRecipe.matchesKey(input);
        boolean viewUses = JEBClient.keyViewUses != null && JEBClient.keyViewUses.matchesKey(input);

        if (!viewRecipe && !viewUses) {
            return;
        }

        // focusedSlot объявлен в HandledScreen — берём через аксессор,
        // так как @Shadow не видит поля из суперкласса цели.
        Slot hovered = ((HandledScreenAccessor) this).jeb$getFocusedSlot();
        if (hovered == null || !hovered.hasStack()) {
            return;
        }

        ItemStack stack = hovered.getStack();
        String searchText = viewRecipe
                ? RecipeSearchQueries.forResult(stack)
                : RecipeSearchQueries.forIngredient(stack);

        RecipeBookWidget<?> widget = this.recipeBook;
        RecipeBookWidgetAccessor accessor = (RecipeBookWidgetAccessor) widget;
        RecipeBookWidgetBridge bridge = (RecipeBookWidgetBridge) widget;

        if (!widget.isOpen()) {
            widget.toggleOpen();
        }

        bridge.jeb$pushHistory(accessor.getSearchField().getText(), accessor.getSelectedTab());
        accessor.getSearchField().setText(searchText);
        accessor.setSelectedTab((RecipeGroupButtonWidget) accessor.getTabButtons().get(0));
        accessor.invokeReset();

        cir.setReturnValue(true);
    }
}
