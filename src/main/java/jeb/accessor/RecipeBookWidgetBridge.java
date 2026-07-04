package jeb.accessor;

import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;

public interface RecipeBookWidgetBridge {
    void jeb$refresh();

    void jeb$pushHistory(String query, RecipeGroupButtonWidget tab);

    boolean jeb$goBack();

    boolean jeb$hasHistory();
}
