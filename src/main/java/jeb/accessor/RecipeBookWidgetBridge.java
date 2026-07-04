package jeb.accessor;

import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;

public interface RecipeBookWidgetBridge {
    void jeb$refresh();

    void jeb$pushHistory(String query, RecipeBookTabButton tab);

    boolean jeb$goBack();

    boolean jeb$hasHistory();
}
