package meteordevelopment.meteorclient.utils.render;

import com.google.gson.Gson;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.accounts.TexturesJson;
import meteordevelopment.meteorclient.systems.accounts.UuidToProfileResponse;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.network.Http;

import java.util.Base64;
import java.util.UUID;

public class PlayerHeadUtils {
    public static PlayerHeadTexture STEVE_HEAD;

    private PlayerHeadUtils() {
    }

    @PostInit
    public static void init() {
        try {
            // Use NameMC’s static Steve PNG instead of broken no-arg constructor
            String steveSkinUrl = "https://s.namemc.com/i/e05011e55833b73e.png";
            STEVE_HEAD = new PlayerHeadTexture(steveSkinUrl);
            MeteorClient.LOG.info("Loaded Steve head texture from NameMC.");
        } catch (Exception e) {
            MeteorClient.LOG.error("Failed to initialize Steve head texture, using null fallback.", e);
            STEVE_HEAD = null;
        }
    }

    public static PlayerHeadTexture fetchHead(UUID id) {
        if (id == null) return null;

        try {
            String url = getSkinUrl(id);
            return url != null ? new PlayerHeadTexture(url) : null;
        } catch (Exception e) {
            MeteorClient.LOG.error("Failed to fetch player head for UUID " + id, e);
            return null;
        }
    }

    public static String getSkinUrl(UUID id) {
        UuidToProfileResponse res2 = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
            .exceptionHandler(e -> MeteorClient.LOG.error("Could not contact Mojang session servers.", e))
            .sendJson(UuidToProfileResponse.class);

        if (res2 == null) return null;

        String base64Textures = res2.getPropertyValue("textures");
        if (base64Textures == null) return null;

        TexturesJson textures = new Gson().fromJson(
            new String(Base64.getDecoder().decode(base64Textures)),
            TexturesJson.class
        );

        if (textures.textures.SKIN == null) return null;

        return textures.textures.SKIN.url;
    }
}
