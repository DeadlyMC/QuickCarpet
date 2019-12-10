package quickcarpet.mixin.client;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import quickcarpet.annotation.Feature;
import quickcarpet.utils.extensions.ExtendedInGameHud;

@Feature("logger.hud")
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin implements ExtendedInGameHud {
    @Shadow private Text footer;
    @Shadow private Text header;

    public boolean hasFooterOrHeader() {
        return footer != null || header != null;
    }
}
