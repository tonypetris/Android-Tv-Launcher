package it.tony.litelauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Servizio di accessibilità usato solo per i tasti Spegni e Riavvia del launcher.
 * Android non consente a una normale app di spegnere o riavviare il TV: questo è l'unico modo permesso.
 * Non legge né raccoglie nulla: agisce solo quando premi Spegni o Riavvia.
 */
public class PowerService extends AccessibilityService {

    static PowerService instance;

    private static final String[] RESTART_LABELS = {"Riavvia", "Riavvio", "Restart", "Reboot"};

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    /** Standby, come il tasto di accensione del telecomando. */
    boolean standby() {
        if (Build.VERSION.SDK_INT >= 28) return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    /** Apre il menu di spegnimento di Android e preme "Riavvia". */
    boolean reboot() {
        if (!performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) return false;
        tryClick(0);
        return true;
    }

    private void tryClick(final int attempt) {
        handler.postDelayed(() -> {
            if (clickRestart()) return;
            if (attempt < 10) tryClick(attempt + 1);
        }, 300);
    }

    private boolean clickRestart() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null) roots.add(r);
            }
        } catch (Exception ignored) {
        }
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) roots.add(active);

        for (AccessibilityNodeInfo root : roots) {
            for (String label : RESTART_LABELS) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(label);
                if (found == null) continue;
                for (AccessibilityNodeInfo n : found) {
                    AccessibilityNodeInfo c = n;
                    while (c != null && !c.isClickable()) c = c.getParent();
                    if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                }
            }
        }
        return false;
    }
}
