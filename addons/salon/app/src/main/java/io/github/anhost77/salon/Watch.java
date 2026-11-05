package io.github.anhost77.salon;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

/**
 * "Regarder sur…": a guide channel, live, in the app that carries it. Free TV (OQEE) opens a channel by its number
 * (https://oq.ee/channel/{n}/play, its own deep link); the CANAL+ channels Free TV does not carry open myCANAL.
 * OQEE numbers from the community list (homebridge-plugin-androidtv wiki), checked on this TV for TF1.
 */
final class Watch {
    private static final String OQEE = "net.oqee.androidtv.store";
    private static final String MYCANAL = "com.canal.android.canal";
    private static final Map<String, Integer> OQEE_IDS = new HashMap<>();
    private static final Map<String, Integer> CANAL_IDS = new HashMap<>();

    static {
        // Each number opened on the TV on 05/11/2026 and its name read in OQEE's log (ChannelData name=…).
        // France 3 is the Paris Île-de-France edition (271 lands on OQEE's home). Canal+, Canal+ Séries and Ciné+
        // are not in this Free offer (their old numbers open OCS or nothing): they go to myCANAL.
        String[][] ids = {
                {"TF1.fr", "536"}, {"France2.fr", "270"}, {"France3.fr", "321"},
                {"France5.fr", "272"}, {"M6.fr", "537"}, {"Arte.fr", "273"}, {"W9.fr", "409"}, {"TMC.fr", "517"},
                {"NT1.fr", "410"}, {"France4.fr", "412"}, {"CStar.fr", "558"}, {"Gulli.fr", "557"},
                {"TF1SeriesFilms.fr", "843"}, {"LEquipe21.fr", "844"}, {"6ter.fr", "845"}, {"Numero23.fr", "846"},
                {"RMCDecouverte.fr", "847"}, {"Cherie25.fr", "848"}, {"ParisPremiere.fr", "282"},
                {"PlanetePlus.fr", "128"}};
        for (String[] i : ids) OQEE_IDS.put(i[0], Integer.parseInt(i[1]));
        // myCANAL plays a channel live from https://www.canalplus.com/live/?channel={epg id} (ids from
        // secure-webtv-static.canal-plus.com/metadata/cpfra/all/v2.2/globalchannels.json). Each one played on this TV
        // on 05/11/2026. Canal+ Sport, Cinéma, Foot, OCS and the Ciné+ channels answer UPES_122 (not in this
        // subscription): they keep opening myCANAL's home rather than an error screen.
        String[][] canal = {
                {"CanalPlus.fr", "301"}, {"CanalPlusSport360.fr", "83"}, {"CanalPlusDocs.fr", "899"},
                {"CanalPlusGrandEcran.fr", "900"}, {"CanalPlusKIDS.fr", "259"}, {"CanalPlusBoxOffice.fr", "1074"}};
        for (String[] i : canal) CANAL_IDS.put(i[0], Integer.parseInt(i[1]));
    }

    private Watch() { }

    private static Integer oqee(int channel) {
        return OQEE_IDS.get(TvTonight.CHANNELS[channel][0]);
    }

    static String appName(Context ctx, int channel) {
        return ctx.getString(oqee(channel) != null ? R.string.watch_freetv : R.string.watch_mycanal);
    }

    static void open(Context ctx, int channel) {
        Integer n = oqee(channel);
        Integer live = CANAL_IDS.get(TvTonight.CHANNELS[channel][0]);
        Intent i = n != null
                ? new Intent(Intent.ACTION_VIEW, Uri.parse("https://oq.ee/channel/" + n + "/play")).setPackage(OQEE)
                : live != null
                ? new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.canalplus.com/live/?channel=" + live))
                        .setPackage(MYCANAL)
                : ctx.getPackageManager().getLeanbackLaunchIntentForPackage(MYCANAL);
        if (i == null) return;
        try {
            // A fresh task: an app already open would otherwise come back as it was and ignore the link.
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (RuntimeException missing) {
            Intent app = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(n != null ? OQEE : MYCANAL);
            if (app != null) ctx.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
