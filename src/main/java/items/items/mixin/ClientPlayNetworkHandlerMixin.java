package items.items.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

import static items.items.client.ItemsClient.existingResultItems;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(
            method = "onRecipeBookAdd",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/recipebook/ClientRecipeBook;add(Lnet/minecraft/recipe/RecipeDisplayEntry;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectOnRecipeBookAdd(RecipeBookAddS2CPacket packet, CallbackInfo ci, ClientRecipeBook clientRecipeBook, Iterator var3, RecipeBookAddS2CPacket.Entry entry) {

        // Получаем display
        RecipeDisplay display = entry.contents().display();

        // Пример: добавим результат рецепта в Set
        if (display.result() instanceof SlotDisplay.StackSlotDisplay result) {
            ItemStack stack = result.stack();
            Item item = stack.getItem();
            existingResultItems.add(item);
        }

        // Можно сделать что угодно с display — лог, анализ, модификация
        // System.out.println("Получен display: " + display);
    }
}

