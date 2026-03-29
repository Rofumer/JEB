package jeb.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractWidget.class)
public interface ClickableWidgetAccessor {
    @Accessor("visible")
    boolean isVisible();

    @Accessor("visible")
    void setVisible(boolean visible);
}
