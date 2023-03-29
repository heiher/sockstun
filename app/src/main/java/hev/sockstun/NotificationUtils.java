package hev.sockstun;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public class NotificationUtils {
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    private static final String notificationChannelId = NotificationUtils.class.getName();
    private static final String notificationName = "Vpn前台服务";
    private static final int NOTIFICATION_ID = 1;
    private static NotificationManager notificationManager;
    private static Notification.Builder notificationBuilder;


    /**
     * 申请通知权限
     * @param activity
     */
    public static void requestNotificationPermission(Activity activity) {

        if (Build.VERSION.SDK_INT >= 33) {
            if (activity.checkSelfPermission(POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
                if (!activity.shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)) {
                    enableNotification(activity);
                } else {
                    activity.requestPermissions(new String[]{POST_NOTIFICATIONS}, 100);
                }
            }
        } else {
            boolean enabled = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                enabled = getNotificationManager(activity).areNotificationsEnabled();
                if (!enabled) {
                    enableNotification(activity);
                }
            }

        }
    }

    private static NotificationManager getNotificationManager(Context context) {
        if (notificationManager == null) {
            notificationManager = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return notificationManager;
    }

    private static void enableNotification(Context context) {
        Intent intent = new Intent();
        try {


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, context.getApplicationInfo().uid);
                intent.putExtra("app_package", context.getPackageName());
                intent.putExtra("app_uid", context.getApplicationInfo().uid);
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", context.getPackageName(), null);
                intent.setData(uri);
            }
            context.startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            context.startActivity(intent);
        }
    }

    /**
     * 开启前台服务
     */
    public static void startForegroundService(Service service) {
        notificationManager = getNotificationManager(service);

        //创建NotificationChannel

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId, notificationName, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(notificationChannel);

        }

        service.startForeground(NOTIFICATION_ID, getNotification(service));

    }

    private static Notification getNotification(Service service) {
        Intent i = new Intent(service, MainActivity.class);
        PendingIntent pi = PendingIntent.getService(service, 0, i, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        notificationBuilder = new Notification.Builder(service)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setLights(0, 0, 0)
                .setContentTitle(service.getString(R.string.app_name))
                .setContentText("服务正在运行...")
                .setContentIntent(pi);


        //设置Notification的ChannelID,否则不能正常显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            notificationBuilder.setChannelId(notificationChannelId);

        }
        return notificationBuilder.build();

    }

    /**
     * 更新通知内容
     *
     * @param title
     * @param content
     */

    public static void updateNotification(String title, String content) {

        if (content == null || content.isEmpty()||notificationBuilder==null) {
            return;
        }
        notificationBuilder.setContentTitle(title);
        notificationBuilder.setContentText(content);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

}
