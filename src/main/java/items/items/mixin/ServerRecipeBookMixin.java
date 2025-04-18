package items.items.mixin;

import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.network.packet.s2c.play.RecipeBookSettingsS2CPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerRecipeBook;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {

    @Shadow
    @Final
    private ServerRecipeBook.DisplayCollector collector;

    @Inject(
            method = "sendInitRecipesPacket",
            at = @At("HEAD"),
            cancellable = true
    )
    private void injectAllRecipes(ServerPlayerEntity player, CallbackInfo ci) {
        // Отправляем настройки книги рецептов
        player.networkHandler.sendPacket(new RecipeBookSettingsS2CPacket(((RecipeBook)(Object)this).getOptions()));

        // Собираем все рецепты сервера
        List<RecipeBookAddS2CPacket.Entry> allEntries = new ArrayList<>();

        // Получаем все рецепты через RecipeManager
        Collection<RecipeEntry<?>> allRecipes = player.getServer().getRecipeManager().values();

        for (RecipeEntry<?> recipeEntry : allRecipes) {
            RegistryKey<Recipe<?>> recipeKey = recipeEntry.id(); // ключ рецепта

            collector.displaysForRecipe(recipeKey, display -> {
                // Можно фильтровать, например, по display или recipeEntry
                allEntries.add(new RecipeBookAddS2CPacket.Entry(display, false, false));
            });
        }

        // Отправляем весь список рецептов игроку
        player.networkHandler.sendPacket(new RecipeBookAddS2CPacket(allEntries, true));

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