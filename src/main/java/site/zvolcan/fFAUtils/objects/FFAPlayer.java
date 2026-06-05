package site.zvolcan.fFAUtils.objects;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public final class FFAPlayer {

    @Getter
    private final UUID uuid;
    @Getter
    @Setter
    private String lastKit = null;
    @Getter
    @Setter
    private int kills = 0;
    @Getter
    @Setter
    private int deaths = 0;

    public FFAPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public double getKDR() {
        if (deaths == 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

}
