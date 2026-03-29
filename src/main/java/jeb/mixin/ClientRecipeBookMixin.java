package jeb.mixin;

import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin implements ClientRecipeBookAccessor {

    @Accessor("known")
    @Override
    public abstract Map<RecipeDisplayId, RecipeDisplayEntry> getRecipes();


    @Inject(method = "categorizeAndGroupRecipes", at = @At("HEAD"), cancellable = true)
    private static void injectToGroupedMap(Iterable<RecipeDisplayEntry> recipes, CallbackInfoReturnable<Map<RecipeBookCategory, List<List<RecipeDisplayEntry>>>> cir) {
        Map<RecipeBookCategory, List<List<RecipeDisplayEntry>>> map = new HashMap();

        for (RecipeDisplayEntry recipeDisplayEntry : recipes) {
            RecipeBookCategory recipeBookCategory = recipeDisplayEntry.category();

            // Игнорируем группу, всегда делаем пустую
            OptionalInt optionalInt = OptionalInt.empty();

            // Т.к. optionalInt всегда пустой, всё будет сюда
            ((List) map.computeIfAbsent(recipeBookCategory, (group) -> new ArrayList()))
                    .add(List.of(recipeDisplayEntry));
        }

        cir.setReturnValue(map);
    }
}


