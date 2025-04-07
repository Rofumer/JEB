package items.items.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class SynchronizeRecipesMixin {

    @Inject(method = "onSynchronizeRecipes", at = @At("HEAD"))
    private void onSynchronizeRecipes(SynchronizeRecipesS2CPacket packet, CallbackInfo ci) {
        System.out.println("[Mixin] Received SynchronizeRecipesS2CPacket:");
        System.out.println("Item Sets: " + packet.itemSets());
        System.out.println("Stonecutter Recipes: " + packet.stonecutterRecipes());
    }
}
