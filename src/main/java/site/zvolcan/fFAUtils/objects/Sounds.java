package site.zvolcan.fFAUtils.objects;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

public class Sounds {

    public static final Sound SUCCESS_SOUND =
            Sound.sound(
                    Key.key("block.note_block.bit"),
                    Sound.Source.PLAYER,
                    1.0f,
                    2.0f
            );

    public static final Sound ERROR_SOUND =
            Sound.sound(
                    Key.key("block.note_block.bass"),
                    Sound.Source.PLAYER,
                    1.0f,
                    1.0f
            );

}
