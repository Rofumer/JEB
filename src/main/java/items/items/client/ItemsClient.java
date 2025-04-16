package items.items.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;

public class ItemsClient implements ClientModInitializer {

    private boolean waitingForR = false; // Добавляем флаг для проверки, нужно ли устанавливать экран

    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            /*RecipeListScreen recipeListScreen = new RecipeListScreen();

            if (client.player != null && !RecipeListScreen.sent) {
                try {
                    RecipeListScreen.sent = true;
                    recipeListScreen.loadAllRecipes();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }*/

            if (waitingForR) {
                // Добавляем небольшую задержку, чтобы избежать чрезмерной нагрузки
                if (org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                    if (client.currentScreen == null) {
                        client.setScreen(new RecipeListScreen());
                    }
                    waitingForR = false; // Закрываем флаг, чтобы экран не переключался снова
                }
            } else {
                // Проверяем нажатие клавиши R
                if (org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                    waitingForR = true; // Устанавливаем флаг, чтобы экран переключился
                }
            }
        });
    }
}
