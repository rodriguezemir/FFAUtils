package site.zvolcan.fFAUtils.objects;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

public class Sounds {

    public static final Sound SUCCESS_SOUND =
            Sound.sound(
                    Key.key("entity.experience_orb.pickup"),
                    Sound.Source.PLAYER,
                    1.0f,
                    1.0f
            );

}
