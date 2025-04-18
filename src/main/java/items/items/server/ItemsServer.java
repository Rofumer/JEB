package items.items.server;

import net.fabricmc.api.ModInitializer;

public class ItemsServer implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("Серверная часть мода инициализирована!");
        // Здесь можно регистрировать команды, события и т.п.
    }
}
