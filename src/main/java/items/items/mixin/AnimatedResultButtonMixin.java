package items.items.mixin;

import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.util.context.ContextParameterMap;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Mixin(AnimatedResultButton.class)
public class AnimatedResultButtonMixin {

    @Inject(
            method = "showResultCollection",
            at = @At("HEAD"),
            cancellable = true
    )
    private void injectAllRecipes(
            RecipeResultCollection resultCollection,
            boolean filteringCraftable,
            RecipeBookResults resultsObject,
            ContextParameterMap context,
            CallbackInfo ci
    ) {
        AnimatedResultButton self = (AnimatedResultButton)(Object)this;

        // Получаем все рецепты
        List<RecipeDisplayEntry> list = resultCollection.getAllRecipes();

        try {
            // Достаём приватный вложенный класс Result
            Class<?> outer = AnimatedResultButton.class;
            Class<?> resultClass = null;

            for (Class<?> nested : outer.getDeclaredClasses()) {
                if (nested.getSimpleName().equals("Result")) {
                    resultClass = nested;
                    break;
                }
            }

            if (resultClass == null) throw new RuntimeException("Result class not found");

            Constructor<?> constructor = resultClass.getDeclaredConstructor(NetworkRecipeId.class, List.class);
            constructor.setAccessible(true);

            List<Object> resultList = new ArrayList<>();
            for (RecipeDisplayEntry entry : list) {
                Object resultInstance = constructor.newInstance(entry.id(), entry.getStacks(context));
                resultList.add(resultInstance);
            }

            // Установка приватного поля 'results'
            Field resultsField = outer.getDeclaredField("results");
            resultsField.setAccessible(true);
            resultsField.set(self, resultList);

            // Установка allResultsEqual
            Method equalMethod = outer.getDeclaredMethod("areAllResultsEqual", List.class);
            equalMethod.setAccessible(true);
            boolean allEqual = (boolean) equalMethod.invoke(null, resultList);

            Field allEqualField = outer.getDeclaredField("allResultsEqual");
            allEqualField.setAccessible(true);
            allEqualField.setBoolean(self, allEqual);

        } catch (Exception e) {
            e.printStackTrace();
        }

        ci.cancel(); // Мы всё обработали вручную — оригинальный метод можно не вызывать
    }
}
