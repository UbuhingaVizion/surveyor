package io.rapidpro.surveyor.work;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.activity.OrgChooseActivity;

/**
 * Minimal notifications for background submission sync: a quiet progress notification while
 * sending, and result notifications on success/failure.
 */
public final class SyncNotifier {

    private static final String CHANNEL_ID = "surveyor_sync";

    public static final int PROGRESS_ID = 1001;
    public static final int RESULT_ID = 1002;

    private SyncNotifier() {
    }

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.sync_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(ctx.getString(R.string.sync_channel_desc));

            NotificationManager manager = ctx.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * A quiet notification used while the sync worker runs in the foreground
     */
    public static Notification progress(Context ctx) {
        return base(ctx)
                .setContentTitle(ctx.getString(R.string.sync_in_progress))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build();
    }

    public static void notifyResult(Context ctx, int sent) {
        if (!canNotify(ctx)) {
            return;
        }
        String text = ctx.getResources().getQuantityString(R.plurals.submissions_sent, sent, sent);
        NotificationManagerCompat.from(ctx).notify(RESULT_ID, base(ctx)
                .setContentTitle(ctx.getString(R.string.sync_complete))
                .setContentText(text)
                .setAutoCancel(true)
                .build());
    }

    public static void notifyFailed(Context ctx, String error) {
        if (!canNotify(ctx)) {
            return;
        }
        NotificationManagerCompat.from(ctx).notify(RESULT_ID, base(ctx)
                .setContentTitle(ctx.getString(R.string.sync_failed))
                .setContentText(error != null ? error : ctx.getString(R.string.error_submissions_send))
                .setAutoCancel(true)
                .build());
    }

    private static NotificationCompat.Builder base(Context ctx) {
        Intent intent = new Intent(ctx, OrgChooseActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(ctx, 0, intent, flags);

        return new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_sync)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private static boolean canNotify(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }
}
