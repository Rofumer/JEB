package jeb.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {

    @Shadow
    @Final
    private ServerRecipeBook.DisplayResolver displayResolver;

    @Inject(
            method = "sendInitialRecipeBook",
            at = @At("HEAD"),
            cancellable = true
    )
    private void injectAllRecipes(ServerPlayer player, CallbackInfo ci) {
        // Отправляем настройки книги рецептов
        player.connection.send(new ClientboundRecipeBookSettingsPacket(((RecipeBook)(Object)this).getBookSettings()));

        // Собираем все рецепты сервера
        List<ClientboundRecipeBookAddPacket.Entry> allEntries = new ArrayList<>();

        // Получаем все рецепты через RecipeManager
        Collection<RecipeHolder<?>> allRecipes = player.level().getServer().getRecipeManager().getRecipes();

        for (RecipeHolder<?> recipeEntry : allRecipes) {
            ResourceKey<Recipe<?>> recipeKey = recipeEntry.id(); // ключ рецепта

            displayResolver.displaysForRecipe(recipeKey, display -> {
                // Можно фильтровать, например, по display или recipeEntry
                if (BuiltInRegistries.RECIPE_DISPLAY.getKey(display.display().type()) == null) {
                    System.out.println("[JEB Debug] Skipping unknown recipe display: " + display.getClass().getName());
                    return;
                }
                allEntries.add(new ClientboundRecipeBookAddPacket.Entry(display, false, false));
            });
        }

        // Отправляем весь список рецептов игроку
        player.connection.send(new ClientboundRecipeBookAddPacket(allEntries, true));

        // Отменяем оригинальный метод
        ci.cancel();
    }

}

/*

@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {

    @Shadow
    @Final
    private ServerRecipeBook.DisplayCollector collector;

    @Shadow
    @VisibleForTesting
    protected Set<RegistryKey<Recipe<?>>> unlocked;

    @Shadow
    @VisibleForTesting
    protected Set<RegistryKey<Recipe<?>>> highlighted;

    @Shadow
    public abstract RecipeBookOptions getOptions();

    @Inject(method = "sendInitRecipesPacket", at = @At("HEAD"), cancellable = true)
    private void injectMissingRecipesOnly(ServerPlayerEntity player, CallbackInfo ci) {
        player.networkHandler.sendPacket(new RecipeBookSettingsS2CPacket(this.getOptions()));

        List<RecipeBookAddS2CPacket.Entry> list = new ArrayList<>();

        // Получаем все рецепты на сервере
        for (RegistryKey<Recipe<?>> recipeKey : player.getServer()
                .getRegistryManager()
                .get(RegistryKeys.RECIPE)
                .getKeys()) {

            // Только те, которых ещё нет в книге игрока
            if (!unlocked.contains(recipeKey)) {
                collector.displaysForRecipe(recipeKey, (display) -> {
                    boolean isHighlighted = highlighted.contains(recipeKey);
                    list.add(new RecipeBookAddS2CPacket.Entry(display, false, isHighlighted));
                });
            }
        }

        // Отправка недостающих рецептов
        if (!list.isEmpty()) {
            player.networkHandler.sendPacket(new RecipeBookAddS2CPacket(list, true));
        }

        ci.cancel(); // Отключаем оригинальный метод
    }
}


*/