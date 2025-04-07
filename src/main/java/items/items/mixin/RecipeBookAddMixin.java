package items.items.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class RecipeBookAddMixin {

    @Inject(method = "onRecipeBookAdd", at = @At("HEAD"))
    private void onRecipeBookAdd(RecipeBookAddS2CPacket packet, CallbackInfo ci) {
        System.out.println("Received RecipeBookAddS2CPacket: " + packet);
    }
}

