package mx.xperience.launcher.providers;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.Keep;

import com.android.launcher3.IconProvider;
import mx.xperience.launcher.model.IconPack;

@Keep
public class NightFallIconProvider extends IconProvider {

    private Context mContext;

    public NightFallIconProvider(Context context) {
        mContext = context;
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        Drawable icon = super.getIcon(info, iconDpi, flattenDrawable);
        IconPack iconPack = IconPackProvider.loadAndGetIconPack(mContext);
        if (iconPack != null) {
            Drawable iconMask = iconPack.getIcon(info, null, info.getLabel());
            if (iconMask != null) {
                return iconMask;
            } else {
                return icon;
            }
        } else {
            return icon;
        }
    }
}