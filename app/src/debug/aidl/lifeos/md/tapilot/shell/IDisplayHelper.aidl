package lifeos.md.tapilot.shell;
import android.view.Surface;
interface IDisplayHelper {
    int identity();
    void watchClient(IBinder client);
    int create(in Surface surface, int width, int height, int density);
    boolean gesture(int displayId, in float[] points, long duration);
    void key(int displayId, int keyCode);
    void text(int displayId, String value);
    void launch(int displayId, String component);
    void release(int displayId);
    void shutdown();
}
