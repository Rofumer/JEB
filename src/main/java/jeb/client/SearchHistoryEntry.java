package jeb.client;

import net.minecraft.recipe.book.RecipeBookGroup;

public record SearchHistoryEntry(String query, RecipeBookGroup category) {}
