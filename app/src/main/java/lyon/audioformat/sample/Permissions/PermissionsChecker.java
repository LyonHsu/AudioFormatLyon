package lyon.audioformat.sample.Permissions;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class PermissionsChecker {
    private final Context mContext;
    String TAG = PermissionsChecker.class.getSimpleName();
    public PermissionsChecker(Context context) {
        mContext = context;
    }

    // 判断权限集合
    public boolean lacksPermissions(String... permissions) {
        for (String permission : permissions) {
            if (lacksPermission(permission)) {
                Log.d(TAG,"permission:"+permission+" no get Permissions!");
                return true;
            }
        }
        return false;
    }

    // 判断是否缺少权限
    private boolean lacksPermission(String permission) {
        return ContextCompat.checkSelfPermission(mContext, permission) ==
                PackageManager.PERMISSION_DENIED;
    }
}
