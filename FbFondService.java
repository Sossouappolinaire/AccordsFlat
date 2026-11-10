package bj.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Garde « Télécharger Facebook » en vie quand l'application passe en arrière-plan : l'analyse du lien
 * (jusqu'à l'affichage des cartes SD / HD) et le téléchargement continuent, avec une notification.
 */
public class FbFondService extends Service {

    private static final String CANAL = "fb_fond";
    private static final String CANAL_FIN = "fb_fin";
    private static final int ID = 4101;
    private static final int ID_FIN = 4102;

    /** Vrai quand l'écran « Télécharger Facebook » est visible (pas de notification de fin dans ce cas). */
    static volatile boolean visible = false;
    private static volatile boolean actif = false;

    private PowerManager.WakeLock verrou;

    // ------------------------------------------------------------ API pour l'écran

    static void demarrer(Context c, String titre, String texte) {
        actif = true;
        Intent i = new Intent(c, FbFondService.class).putExtra("titre", titre).putExtra("texte", texte);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) {
            actif = false;     // démarrage refusé : l'écran continue comme avant
        }
    }

    /** Met à jour la notification (pct < 0 : progression indéterminée). */
    static void maj(Context c, String titre, String texte, int pct) {
        if (!actif) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.notify(ID, construire(c, CANAL, titre, texte, pct, true));
    }

    static void arreter(Context c) {
        if (!actif) return;
        actif = false;
        try {
            c.stopService(new Intent(c, FbFondService.class));
        } catch (Exception ignore) {
            // déjà arrêté
        }
    }

    /** Notification « terminé » quand la personne n'est plus sur l'écran. */
    static void notifierFin(Context c, String titre, String texte) {
        if (visible) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.notify(ID_FIN, construire(c, CANAL_FIN, titre, texte, -1, false));
    }

    private static Notification construire(Context c, String canal, String titre, String texte, int pct, boolean enCours) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            if (CANAL.equals(canal)) {
                nm.createNotificationChannel(new NotificationChannel(CANAL, "Facebook en arrière-plan", NotificationManager.IMPORTANCE_LOW));
            } else {
                nm.createNotificationChannel(new NotificationChannel(CANAL_FIN, "Facebook : terminé", NotificationManager.IMPORTANCE_DEFAULT));
            }
        }
        Intent ouvrir = new Intent(c, FacebookActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 41, ouvrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, canal) : new Notification.Builder(c);
        b.setSmallIcon(enCours ? android.R.drawable.stat_sys_download : android.R.drawable.stat_sys_download_done)
                .setContentTitle(titre)
                .setContentText(texte)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setOngoing(enCours)
                .setAutoCancel(!enCours);
        if (enCours) {
            if (pct >= 0) b.setProgress(100, Math.min(100, pct), false); else b.setProgress(0, 0, true);
        }
        return b.build();
    }

    // ------------------------------------------------------------ le service

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String titre = i == null ? null : i.getStringExtra("titre");
        String texte = i == null ? null : i.getStringExtra("texte");
        Notification n = construire(this, CANAL, titre == null ? "Facebook" : titre, texte == null ? "" : texte, -1, true);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(ID, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (verrou == null) {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    verrou = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bj.radio:fbfond");
                    verrou.acquire(3 * 60 * 60 * 1000L);       // 3 h au maximum
                }
            } catch (Exception ignore) {
                // sans verrou : le service tourne quand même
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (verrou != null && verrou.isHeld()) verrou.release();
        } catch (Exception ignore) {
            // déjà libéré
        }
        verrou = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
