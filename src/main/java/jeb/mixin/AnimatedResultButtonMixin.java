package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public class AnimatedResultButtonMixin implements AnimatedResultButtonExtension {

    @Unique
    private long jeb$flashUntil = 0L;

    @Unique
    @Override
    public void jeb$flash() {
        this.jeb$flashUntil = System.currentTimeMillis() + 300L;
    }

    @Unique
    private boolean jeb$isFlashing() {
        return System.currentTimeMillis() < this.jeb$flashUntil;
    }

    @Unique
    private static void jeb$drawBorderCompat(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        try {
            Method drawBorder = GuiGraphicsExtractor.class.getMethod(
                    "drawBorder",
                    int.class, int.class, int.class, int.class, int.class
            );
            drawBorder.invoke(ctx, x, y, w, h, color);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method renderOutline = GuiGraphicsExtractor.class.getMethod(
                    "renderOutline",
                    int.class, int.class, int.class, int.class, int.class
            );
            renderOutline.invoke(ctx, x, y, w, h, color);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method fill = GuiGraphicsExtractor.class.getMethod(
                    "fill",
                    int.class, int.class, int.class, int.class, int.class
            );

            // верх
            fill.invoke(ctx, x, y, x + w, y + 1, color);
            // низ
            fill.invoke(ctx, x, y + h - 1, x + w, y + h, color);
            // лево
            fill.invoke(ctx, x, y, x + 1, y + h, color);
            // право
            fill.invoke(ctx, x + w - 1, y, x + w, y + h, color);
        } catch (ReflectiveOperationException ignored) {
            // Если и этого нет — просто молча пропускаем подсветку.
        }
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void jeb$renderFlash(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!jeb$isFlashing()) {
            return;
        }

        RecipeButton self = (RecipeButton) (Object) this;
        jeb$drawBorderCompat(
                graphics,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                0xFFFFFF00
        );
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getSelectedRecipes(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection$CraftableStatus;)Ljava/util/List;"
            )
    )
    private List<RecipeDisplayEntry> redirectFilter(RecipeCollection instance, RecipeCollection.CraftableStatus filterMode) {
        return instance.getRecipes();
    }

    @Unique
    private static final Component MORE_RECIPES_TEXT = Component.translatable("items.craftsfromitem");

    @Inject(method = "getTooltipText", at = @At("HEAD"), cancellable = true)
    private void onGetTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        try {
            List<Component> list = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            list.add(MORE_RECIPES_TEXT);
            cir.setReturnValue(list);
        } catch (Exception e) {
            e.printStackTrace();
            cir.setReturnValue(List.of(Component.literal("§c[Error rendering tooltip]")));
        }
    }
}