package wtf.yoraudev.ruby.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.yoraudev.ruby.auth.McTokenAuth;
import wtf.yoraudev.ruby.auth.SessionManager;

import java.util.concurrent.CompletableFuture;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    @Unique
    private String statusMessage = null;
    @Unique
    private int statusColor = 0xFFFFFFFF;
    @Unique
    private long statusMessageTime = 0;

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addSchubiAuthButton(CallbackInfo ci) {
        int buttonWidth = 220;
        int buttonHeight = 20;
        int padding = 5;

        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("TokenLogin - Login From Clipboard"), button -> {
                if (this.client != null) {
                    loginFromClipboard();
                }
            })
            .dimensions(this.width - buttonWidth - padding, padding, buttonWidth, buttonHeight)
            .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (statusMessage != null) {
            // Auto-hide message after 5 seconds
            if (System.currentTimeMillis() - statusMessageTime > 5000) {
                statusMessage = null;
                return;
            }

            // Center text exactly in the middle of the screen
            int x = this.width / 2;
            int y = this.height / 2;

            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(statusMessage),
                x,
                y,
                statusColor
            );
        }
    }

    private void loginFromClipboard() {
        if (this.client == null || this.client.keyboard == null) {
            setStatusMessage("Failed to read clipboard", 0xFFFF0000);
            return;
        }

        String clipboardText = this.client.keyboard.getClipboard();
        if (clipboardText == null || clipboardText.trim().isEmpty()) {
            setStatusMessage("Clipboard is empty!", 0xFFFF0000);
            return;
        }

        String token = clipboardText.trim();
        setStatusMessage("Authenticating...", 0xFFFFFFFF);

        CompletableFuture.supplyAsync(() -> {
            return McTokenAuth.INSTANCE.authenticate(token);
        }).thenAccept(result -> {
            if (this.client != null) {
                this.client.execute(() -> {
                    if (result instanceof McTokenAuth.AuthResult.Success) {
                        boolean success = SessionManager.INSTANCE.setSession(token);
                        if (success) {
                            setStatusMessage("Login successful!", 0xFF00FF00);
                        } else {
                            setStatusMessage("Failed to set session", 0xFFFF0000);
                        }
                    } else if (result instanceof McTokenAuth.AuthResult.Failure) {
                        String message = ((McTokenAuth.AuthResult.Failure) result).getMessage();
                        setStatusMessage("Login failed: " + message, 0xFFFF0000);
                    }
                });
            }
        }).exceptionally(throwable -> {
            if (this.client != null) {
                this.client.execute(() -> {
                    setStatusMessage("Error: " + throwable.getMessage(), 0xFFFF0000);
                });
            }
            return null;
        });
    }

    @Unique
    private void setStatusMessage(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusMessageTime = System.currentTimeMillis();
    }
}