package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

import java.util.ArrayList;
import java.util.List;


@Mixin(RecipeButton.class)
public class AnimatedResultButtonMixin implements AnimatedResultButtonExtension {


    private static void drawBorderCompat(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        try {
            GuiGraphics.class.getMethod("drawBorder", int.class,int.class,int.class,int.class,int.class)
                    .invoke(ctx, x,y,w,h,color);
        } catch (ReflectiveOperationException e1) {
            try {
                GuiGraphics.class.getMethod("renderOutline", int.class,int.class,int.class,int.class,int.class)
                        .invoke(ctx, x,y,w,h,color);
            } catch (ReflectiveOperationException e2) {
                // Фоллбэк через fill — 1px рамка
                ctx.fill(x, y, x + w, y + 1, color);
                ctx.fill(x, y + h - 1, x + w, y + h, color);
                ctx.fill(x, y, x + 1, y + h, color);
                ctx.fill(x + w - 1, y, x + w, y + h, color);
            }
        }
    }


    @Unique
    private long jeb$flashUntil = 0L; // время до которого будет подсветка

    @Unique
    public void jeb$flash() {
        this.jeb$flashUntil = System.currentTimeMillis() + 300; // подсветка 300 мс
    }

    @Unique
    private boolean jeb$isFlashing() {
        return System.currentTimeMillis() < this.jeb$flashUntil;
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void jeb$renderFlash(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (jeb$isFlashing()) {
            RecipeButton self = (RecipeButton) (Object) this;
            drawBorderCompat(context,self.getX(), self.getY(), self.getWidth(), self.getHeight(), 0xFFFFFF00); // Жёлтая рамка
        }
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getSelectedRecipes(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection$CraftableStatus;)Ljava/util/List;"
            )
    )
    private List<RecipeDisplayEntry> redirectFilter(RecipeCollection instance, RecipeCollection.CraftableStatus filterMode) {
        // Возвращаем все рецепты, без фильтрации
        return instance.getRecipes();
    }

    @Unique
    private static final Component MORE_RECIPES_TEXT = Component.translatable("items.craftsfromitem");

    /*@Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void injectAlwaysAddTooltip(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        List<Text> list = cir.getReturnValue();
        try{
        list.add(MORE_RECIPES_TEXT); // всегда добавляем
        } catch (Exception e) {
            e.printStackTrace();
            // Можно также записать лог или безопасно проигнорировать ошибку
        }
        cir.setReturnValue(list);    // возвращаем изменённый список
    }*/

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


