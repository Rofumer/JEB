package items.items.mixin;

import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;

import net.minecraft.recipe.book.RecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin implements items.items.accessor.ClientRecipeBookAccessor {

    @Accessor("recipes")
    @Override
    public abstract Map<NetworkRecipeId, RecipeDisplayEntry> getRecipes();


    @Inject(method = "toGroupedMap", at = @At("HEAD"), cancellable = true)
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


